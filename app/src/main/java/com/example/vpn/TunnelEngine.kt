package com.example.vpn

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import com.example.vpn.ProxyProfile
import com.example.vpn.dns.DnsBlocker
import com.example.vpn.net.Ipv4Header
import com.example.vpn.net.PROTO_TCP
import com.example.vpn.net.PROTO_UDP
import com.example.vpn.net.PacketBuilder
import com.example.vpn.net.SessionKey
import com.example.vpn.net.TcpHeader
import com.example.vpn.net.UdpHeader
import com.example.vpn.net.seqAdvance
import com.example.vpn.proxy.ProxyClient
import com.example.vpn.proxy.SocketProtector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 隧道主循环：从 TUN 读 IP 包，按协议分发给会话，再把响应写回 TUN。
 *
 * 读、写各占一个专用线程；每条会话的阻塞式代理 IO 跑在一个可伸缩线程池上。
 */
class TunnelEngine(
    private val tunInterface: ParcelFileDescriptor,
    private val profile: ProxyProfile,
    proxyAddress: InetSocketAddress,
    private val mtu: Int,
    private val protector: SocketProtector,
    private val appResolver: AppResolver?,
    private val dnsBlocker: DnsBlocker?,
) : TunWriter {

    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "freeproxy-io").apply { isDaemon = true }
    }
    private val ioDispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val proxyClient = ProxyClient(profile, proxyAddress, protector)

    private val tcpSessions = ConcurrentHashMap<SessionKey, TcpSession>()
    private val udpSessions = ConcurrentHashMap<SessionKey, UdpSession>()

    private val writeQueue = ArrayBlockingQueue<ByteArray>(WRITE_QUEUE_SIZE)

    private var readerThread: Thread? = null
    private var writerThread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        readerThread = Thread(::readLoop, "freeproxy-tun-read").apply { start() }
        writerThread = Thread(::writeLoop, "freeproxy-tun-write").apply { start() }
        scope.launch { housekeepingLoop() }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        tcpSessions.values.toList().forEach { it.finish() }
        udpSessions.values.toList().forEach { it.finish() }
        tcpSessions.clear()
        udpSessions.clear()
        scope.cancel()
        readerThread?.interrupt()
        writerThread?.interrupt()
        executor.shutdownNow()
        runCatching { tunInterface.close() }
    }

    // ------------------------------------------------------------- 读

    private fun readLoop() {
        val input = FileInputStream(tunInterface.fileDescriptor)
        val buffer = ByteArray(mtu + HEADROOM)
        try {
            while (running.get()) {
                val length = input.read(buffer)
                if (length <= 0) continue
                dispatch(buffer, length)
            }
        } catch (e: Exception) {
            if (running.get()) Log.w(TAG, "TUN 读取中断：${e.message}")
        } finally {
            runCatching { input.close() }
        }
    }

    private fun dispatch(buffer: ByteArray, length: Int) {
        // 解析失败的包（含 IPv6、分片包）直接丢弃
        val ip = Ipv4Header.parse(buffer, length) ?: return
        when (ip.protocol) {
            PROTO_TCP -> handleTcp(ip, buffer)
            PROTO_UDP -> handleUdp(ip, buffer)
            else -> Unit // ICMP 等不做处理：代理协议本身也承载不了
        }
    }

    private fun handleTcp(ip: Ipv4Header, buffer: ByteArray) {
        val tcp = TcpHeader.parse(buffer, ip.headerLength, ip.payloadLength) ?: return
        val key = SessionKey(ip.sourceIp, tcp.sourcePort, ip.destIp, tcp.destPort)
        val payloadOffset = ip.headerLength + tcp.dataOffset
        val payloadLength = ip.totalLength - payloadOffset
        if (payloadLength < 0) return

        val existing = tcpSessions[key]
        if (existing != null) {
            existing.onPacket(tcp, buffer, payloadOffset, payloadLength)
            return
        }

        // 新连接只能由 SYN 发起；其余情况说明会话已过期，回 RST 让对端立即放弃
        if (!tcp.isSyn) {
            if (!tcp.isRst) sendReset(key, tcp)
            return
        }
        if (tcpSessions.size >= MAX_TCP_SESSIONS) {
            Log.w(TAG, "TCP 会话数达到上限，拒绝新连接")
            sendReset(key, tcp)
            return
        }

        val session = TcpSession(
            key = key,
            scope = scope,
            ioDispatcher = ioDispatcher,
            proxyClient = proxyClient,
            tun = this,
            mtu = mtu,
            appResolver = appResolver,
            onFinished = { tcpSessions.remove(it) },
        )
        // putIfAbsent 防止 SYN 重传时并发建两条会话
        val raced = tcpSessions.putIfAbsent(key, session)
        if (raced != null) {
            raced.onPacket(tcp, buffer, payloadOffset, payloadLength)
        } else {
            session.open(tcp)
        }
    }

    private fun handleUdp(ip: Ipv4Header, buffer: ByteArray) {
        val udp = UdpHeader.parse(buffer, ip.headerLength, ip.payloadLength) ?: return
        val key = SessionKey(ip.sourceIp, udp.sourcePort, ip.destIp, udp.destPort)
        val payloadOffset = ip.headerLength + UdpHeader.SIZE
        val payloadLength = udp.payloadLength
        if (payloadLength <= 0) return

        val session = udpSessions[key] ?: run {
            if (udpSessions.size >= MAX_UDP_SESSIONS) return
            val created = UdpSession(
                key = key,
                scope = scope,
                ioDispatcher = ioDispatcher,
                proxyClient = proxyClient,
                profile = profile,
                protector = protector,
                tun = this,
                appResolver = appResolver,
                dnsBlocker = dnsBlocker,
                onFinished = { udpSessions.remove(it) },
            )
            udpSessions.putIfAbsent(key, created) ?: created
        }
        session.send(buffer.copyOfRange(payloadOffset, payloadOffset + payloadLength))
    }

    /** 对没有会话的报文回 RST，避免应用一直卡在连接超时上。 */
    private fun sendReset(key: SessionKey, tcp: TcpHeader) {
        val buffer = ByteArray(40)
        val payloadEnd = if (tcp.isSyn) 1 else 0
        val size = PacketBuilder.writeTcp(
            output = buffer,
            sourceIp = key.destIp,
            sourcePort = key.destPort,
            destIp = key.sourceIp,
            destPort = key.sourcePort,
            sequence = tcp.acknowledgment,
            acknowledgment = seqAdvance(tcp.sequence, payloadEnd),
            flags = TcpHeader.RST or TcpHeader.ACK,
            window = 0,
        )
        enqueue(buffer, size)
    }

    // ------------------------------------------------------------- 写

    override fun enqueue(packet: ByteArray, length: Int) {
        if (!running.get()) return
        // 队列满说明内核侧已经跟不上，丢弃比阻塞会话线程更好
        if (!writeQueue.offer(packet.copyOf(length))) {
            Log.w(TAG, "TUN 写队列已满，丢弃 1 个包")
        }
    }

    private fun writeLoop() {
        val output = FileOutputStream(tunInterface.fileDescriptor)
        try {
            while (running.get()) {
                val packet = writeQueue.poll(500, TimeUnit.MILLISECONDS) ?: continue
                output.write(packet)
            }
        } catch (e: Exception) {
            if (running.get()) Log.w(TAG, "TUN 写入中断：${e.message}")
        } finally {
            runCatching { output.close() }
        }
    }

    // --------------------------------------------------------- 定时维护

    private suspend fun housekeepingLoop() {
        while (scope.isActive && running.get()) {
            delay(HOUSEKEEPING_INTERVAL_MS)
            val now = SystemClock.elapsedRealtime()
            tcpSessions.values.toList()
                .filter { now - it.lastActivity > TCP_IDLE_TIMEOUT_MS }
                .forEach { it.finish() }
            udpSessions.values.toList()
                .filter { now - it.lastActivity > it.idleTimeoutMs }
                .forEach { it.finish() }
        }
    }

    private companion object {
        const val TAG = "TunnelEngine"
        const val HEADROOM = 80
        const val WRITE_QUEUE_SIZE = 1024
        const val MAX_TCP_SESSIONS = 512
        const val MAX_UDP_SESSIONS = 256
        const val TCP_IDLE_TIMEOUT_MS = 300_000L
        const val HOUSEKEEPING_INTERVAL_MS = 5_000L
    }
}
