package com.example.vpn

import android.os.SystemClock
import android.system.OsConstants
import android.util.Log
import com.example.vpn.log.LogLevel
import com.example.vpn.log.TunnelLog
import com.example.vpn.net.HostRegistry
import com.example.vpn.net.PacketBuilder
import com.example.vpn.net.SessionKey
import com.example.vpn.net.TcpHeader
import com.example.vpn.net.seqAdvance
import com.example.vpn.net.seqLessOrEqual
import com.example.vpn.net.seqLessThan
import com.example.vpn.net.toInetAddress
import com.example.vpn.proxy.ProxyClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 一条 TCP 连接的用户态终结点。
 *
 * 对本机内核而言，这个对象扮演目标服务器：它回 SYN-ACK、确认数据、发 FIN；
 * 真实流量则通过 [ProxyClient] 建立的隧道往返。
 *
 * 关于可靠性的一个重要简化：写向 TUN 的数据是交给本机内核的，不经过任何有损链路，
 * 因此不需要拥塞控制。只要严格遵守对端宣告的接收窗口就不会丢包；
 * 超时重传仅作为极端情况下的兜底。
 */
class TcpSession(
    val key: SessionKey,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val proxyClient: ProxyClient,
    private val tun: TunWriter,
    mtu: Int,
    private val appResolver: AppResolver?,
    private val onFinished: (SessionKey) -> Unit,
) {

    private enum class State { CONNECTING, ESTABLISHED, CLOSED }

    private val mss = (mtu - IPV4_TCP_HEADER_SIZE).coerceIn(536, 1460)
    private val lock = Object()
    private val outputBuffer = ByteArray(mtu + 80)
    private val closed = AtomicBoolean(false)

    /** 上行数据队列；有界，队列压力通过 TCP 接收窗口反馈给应用。 */
    private val upstream = Channel<ByteArray>(capacity = UPSTREAM_QUEUE_SIZE)

    @Volatile
    private var state = State.CONNECTING

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var job: Job? = null

    @Volatile
    var lastActivity: Long = SystemClock.elapsedRealtime()
        private set

    // ---- 发送方向（我们 → 内核）
    private val initialSequence = Random.nextLong(0, 0xFFFF_FFFFL)
    private var sendUnacked = initialSequence
    private var sendNext = initialSequence
    private var peerWindow = 65535
    private val retransmitQueue = ArrayDeque<Segment>()
    private var finSent = false

    // ---- 接收方向（内核 → 我们）
    private var receiveNext = 0L
    private var pendingUpstreamBytes = 0
    private var upstreamClosed = false

    private class Segment(val sequence: Long, val data: ByteArray)

    /** 收到 SYN：登记序列号并开始异步连接代理。 */
    fun open(syn: TcpHeader) {
        synchronized(lock) {
            receiveNext = seqAdvance(syn.sequence, 1)
            peerWindow = syn.window
        }
        job = scope.launch(ioDispatcher) {
            // UID 反查要趁 socket 还在，因此放在建立隧道之前
            val packageName = appResolver?.resolve(OsConstants.IPPROTO_TCP, key)
            val target = HostRegistry.describe(key.destIp, key.destPort)
            // 目标是主机名（而非 IP 字面量）时，允许从日志把它加入 DNS 拦截
            val targetHost = target.substringBeforeLast(':')
            val targetDomain = targetHost.takeIf { host -> host.any { it.isLetter() } }

            val connected = try {
                proxyClient.connectTcp(key.destIp.toInetAddress(), key.destPort)
            } catch (e: Exception) {
                Log.d(TAG, "连接失败 $key：${e.message}")
                TunnelLog.connect(
                    target = target,
                    packageName = packageName,
                    status = e.message?.take(48) ?: "失败",
                    level = LogLevel.FAILURE,
                    domain = targetDomain,
                )
                // 立刻回 RST，让应用马上得到"连接被拒绝"而不是干等超时
                sendReset()
                finish()
                return@launch
            }
            TunnelLog.connect(target, packageName, "OK", LogLevel.SUCCESS, targetDomain)

            val accepted = synchronized(lock) {
                if (state != State.CONNECTING) {
                    false
                } else {
                    socket = connected
                    state = State.ESTABLISHED
                    sendSynAck()
                    true
                }
            }
            if (!accepted) {
                runCatching { connected.close() }
                return@launch
            }

            VpnStateHolder.sessionCounter.incrementAndGet()
            launch(ioDispatcher) { pumpUpstream(connected) }
            pumpDownstream(connected)
        }
    }

    /** 处理来自内核的一个 TCP 报文段。 */
    fun onPacket(header: TcpHeader, buffer: ByteArray, payloadOffset: Int, payloadLength: Int) {
        lastActivity = SystemClock.elapsedRealtime()

        if (header.isRst) {
            finish()
            return
        }

        synchronized(lock) {
            peerWindow = header.window
            if (header.isAck) releaseAcknowledged(header.acknowledgment)
            lock.notifyAll()
        }

        // 重复的 SYN 说明我们的 SYN-ACK 丢了（或那时还没连上代理），补发一次
        if (header.isSyn) {
            synchronized(lock) {
                if (state == State.ESTABLISHED) sendSynAck()
            }
            return
        }

        if (state == State.CLOSED) {
            sendReset()
            return
        }

        val accepted = if (payloadLength > 0) {
            acceptData(header.sequence, buffer, payloadOffset, payloadLength)
        } else {
            0
        }

        if (header.isFin) {
            acceptFin(seqAdvance(header.sequence, accepted))
        }
    }

    /** @return 实际被接收的字节数，用于定位随行 FIN 的序列号。 */
    private fun acceptData(sequence: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        val chunk = synchronized(lock) {
            when {
                sequence == receiveNext -> buffer.copyOfRange(offset, offset + length)
                // 重传的老数据，或 TUN 上本不该出现的乱序：都用一个 ACK 应答
                else -> {
                    sendAck()
                    return 0
                }
            }
        }

        // trySend 失败意味着上行积压：不推进 receiveNext，对端会因零窗口暂停，
        // 等队列腾出空间后由 pumpUpstream 主动通告新窗口。
        if (!upstream.trySend(chunk).isSuccess) {
            synchronized(lock) { sendAck() }
            return 0
        }
        synchronized(lock) {
            receiveNext = seqAdvance(receiveNext, length)
            pendingUpstreamBytes += length
            sendAck()
        }
        return length
    }

    private fun acceptFin(finSequence: Long) {
        synchronized(lock) {
            if (upstreamClosed) {
                sendAck()
                return
            }
            if (finSequence != receiveNext) return
            receiveNext = seqAdvance(receiveNext, 1)
            upstreamClosed = true
            sendAck()
        }
        // 关闭上行队列，写协程排空后会 shutdownOutput，让代理知道请求已结束
        upstream.close()
    }

    // ------------------------------------------------------------ 数据泵

    private suspend fun pumpUpstream(socket: Socket) {
        try {
            val output = socket.getOutputStream()
            for (chunk in upstream) {
                output.write(chunk)
                output.flush()
                VpnStateHolder.uploadCounter.addAndGet(chunk.size.toLong())
                synchronized(lock) {
                    val before = advertisedWindow()
                    pendingUpstreamBytes = max(0, pendingUpstreamBytes - chunk.size)
                    // 只在窗口刚从"不足一个 MSS"恢复时通告，避免每块数据都回一个冗余 ACK
                    if (state == State.ESTABLISHED && before < mss && advertisedWindow() >= mss) {
                        sendAck()
                    }
                }
            }
            runCatching { socket.shutdownOutput() }
        } catch (e: Exception) {
            Log.d(TAG, "上行结束 $key：${e.message}")
            finish()
        }
    }

    private fun pumpDownstream(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val buffer = ByteArray(mss)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                VpnStateHolder.downloadCounter.addAndGet(read.toLong())
                sendData(buffer, read)
            }
            sendFin()
        } catch (e: Exception) {
            Log.d(TAG, "下行结束 $key：${e.message}")
            if (state == State.ESTABLISHED) sendReset()
            finish()
        }
    }

    /** 把代理返回的数据切成 MSS 大小写回 TUN，并按对端窗口节流。 */
    private fun sendData(data: ByteArray, length: Int) {
        var offset = 0
        while (offset < length) {
            val chunk = min(mss, length - offset)
            if (!awaitSendWindow(chunk)) throw IOException("会话已关闭")
            synchronized(lock) {
                if (state != State.ESTABLISHED) throw IOException("会话已关闭")
                val sequence = sendNext
                val size = PacketBuilder.writeTcp(
                    output = outputBuffer,
                    sourceIp = key.destIp,
                    sourcePort = key.destPort,
                    destIp = key.sourceIp,
                    destPort = key.sourcePort,
                    sequence = sequence,
                    acknowledgment = receiveNext,
                    flags = TcpHeader.ACK or TcpHeader.PSH,
                    window = advertisedWindow(),
                    payload = data,
                    payloadOffset = offset,
                    payloadLength = chunk,
                )
                tun.enqueue(outputBuffer, size)
                sendNext = seqAdvance(sequence, chunk)
                retransmitQueue.addLast(Segment(sequence, data.copyOfRange(offset, offset + chunk)))
            }
            offset += chunk
        }
    }

    /** 等到窗口能容下 [needed] 字节；久等不到 ACK 就重传队首。@return false 表示会话已关闭。 */
    private fun awaitSendWindow(needed: Int): Boolean {
        synchronized(lock) {
            var lastRetransmit = SystemClock.elapsedRealtime()
            while (state == State.ESTABLISHED) {
                val inflight = (sendNext - sendUnacked).toInt()
                val allowed = min(max(peerWindow, mss), MAX_INFLIGHT)
                if (inflight + needed <= allowed) return true

                lock.wait(WINDOW_POLL_MS)
                val now = SystemClock.elapsedRealtime()
                if (now - lastRetransmit >= RETRANSMIT_TIMEOUT_MS) {
                    retransmitUnacknowledged()
                    lastRetransmit = now
                }
            }
            return false
        }
    }

    // --------------------------------------------------------- 报文发送

    private fun sendSynAck() {
        val size = PacketBuilder.writeTcp(
            output = outputBuffer,
            sourceIp = key.destIp,
            sourcePort = key.destPort,
            destIp = key.sourceIp,
            destPort = key.sourcePort,
            sequence = initialSequence,
            acknowledgment = receiveNext,
            flags = TcpHeader.SYN or TcpHeader.ACK,
            window = advertisedWindow(),
            mss = mss,
        )
        tun.enqueue(outputBuffer, size)
        // SYN 自身占用一个序列号
        if (sendNext == initialSequence) sendNext = seqAdvance(initialSequence, 1)
    }

    private fun sendAck() {
        val size = PacketBuilder.writeTcp(
            output = outputBuffer,
            sourceIp = key.destIp,
            sourcePort = key.destPort,
            destIp = key.sourceIp,
            destPort = key.sourcePort,
            sequence = sendNext,
            acknowledgment = receiveNext,
            flags = TcpHeader.ACK,
            window = advertisedWindow(),
        )
        tun.enqueue(outputBuffer, size)
    }

    private fun sendFin() {
        synchronized(lock) {
            if (finSent || state != State.ESTABLISHED) return
            finSent = true
            val size = PacketBuilder.writeTcp(
                output = outputBuffer,
                sourceIp = key.destIp,
                sourcePort = key.destPort,
                destIp = key.sourceIp,
                destPort = key.sourcePort,
                sequence = sendNext,
                acknowledgment = receiveNext,
                flags = TcpHeader.FIN or TcpHeader.ACK,
                window = advertisedWindow(),
            )
            tun.enqueue(outputBuffer, size)
            sendNext = seqAdvance(sendNext, 1)
        }
    }

    private fun sendReset() {
        // 独立缓冲区：这个方法可能在别的线程正操作 outputBuffer 时被调用
        val buffer = ByteArray(IPV4_TCP_HEADER_SIZE)
        val size = synchronized(lock) {
            PacketBuilder.writeTcp(
                output = buffer,
                sourceIp = key.destIp,
                sourcePort = key.destPort,
                destIp = key.sourceIp,
                destPort = key.sourcePort,
                sequence = sendNext,
                acknowledgment = receiveNext,
                flags = TcpHeader.RST or TcpHeader.ACK,
                window = 0,
            )
        }
        tun.enqueue(buffer, size)
    }

    /** 调用方必须持有 [lock]。 */
    private fun retransmitUnacknowledged() {
        val first = retransmitQueue.firstOrNull() ?: return
        val size = PacketBuilder.writeTcp(
            output = outputBuffer,
            sourceIp = key.destIp,
            sourcePort = key.destPort,
            destIp = key.sourceIp,
            destPort = key.sourcePort,
            sequence = first.sequence,
            acknowledgment = receiveNext,
            flags = TcpHeader.ACK or TcpHeader.PSH,
            window = advertisedWindow(),
            payload = first.data,
            payloadOffset = 0,
            payloadLength = first.data.size,
        )
        tun.enqueue(outputBuffer, size)
    }

    /** 丢弃已被确认的段。调用方必须持有 [lock]。 */
    private fun releaseAcknowledged(acknowledgment: Long) {
        if (!seqLessThan(sendUnacked, acknowledgment)) return
        if (!seqLessOrEqual(acknowledgment, sendNext)) return
        sendUnacked = acknowledgment
        while (true) {
            val segment = retransmitQueue.firstOrNull() ?: break
            val end = seqAdvance(segment.sequence, segment.data.size)
            if (seqLessOrEqual(end, acknowledgment)) retransmitQueue.removeFirst() else break
        }
    }

    /** 剩余可用的接收窗口；上行积压时收缩，必要时通告零窗口让应用暂停发送。 */
    private fun advertisedWindow(): Int =
        (RECEIVE_WINDOW - pendingUpstreamBytes).coerceIn(0, RECEIVE_WINDOW)

    fun finish() {
        if (!closed.compareAndSet(false, true)) return
        val wasEstablished = synchronized(lock) {
            val established = state == State.ESTABLISHED
            state = State.CLOSED
            lock.notifyAll()
            established
        }
        if (wasEstablished) VpnStateHolder.sessionCounter.decrementAndGet()

        upstream.close()
        runCatching { socket?.close() }
        job?.cancel()
        onFinished(key)
    }

    private companion object {
        const val TAG = "TcpSession"
        const val IPV4_TCP_HEADER_SIZE = 40
        const val RECEIVE_WINDOW = 65535
        const val MAX_INFLIGHT = 65535
        const val UPSTREAM_QUEUE_SIZE = 64
        const val RETRANSMIT_TIMEOUT_MS = 400L
        const val WINDOW_POLL_MS = 100L
    }
}
