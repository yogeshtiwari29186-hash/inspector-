package com.example.vpn

import android.os.SystemClock
import android.system.OsConstants
import android.util.Log
import com.example.vpn.DnsMode
import com.example.vpn.ProxyProfile
import com.example.vpn.ProxyType
import com.example.vpn.dns.DnsBlocker
import com.example.vpn.log.TunnelLog
import com.example.vpn.net.DnsMessage
import com.example.vpn.net.DnsResponse
import com.example.vpn.net.HostRegistry
import com.example.vpn.net.PacketBuilder
import com.example.vpn.net.SessionKey
import com.example.vpn.net.toInetAddress
import com.example.vpn.net.toIpv4Bytes
import com.example.vpn.net.u8
import com.example.vpn.proxy.ProxyClient
import com.example.vpn.proxy.SocketProtector
import com.example.vpn.proxy.UdpAssociation
import com.example.vpn.proxy.readExactly
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 一条 UDP "流"（四元组）的转发通道。
 *
 * 转发方式取决于配置：
 *  - SOCKS5 且开启 UDP：走 UDP ASSOCIATE，全协议支持；
 *  - 其余情况：只放行 DNS，并自动降级为 DNS over TCP（RFC 7766）经代理查询，
 *    这样即使上游只有 HTTP CONNECT，域名解析依然可用。
 *
 * 每个四元组独占一条转发通道 —— 共享一个中继 socket 会让回程包无法区分本地源端口。
 */
class UdpSession(
    val key: SessionKey,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val proxyClient: ProxyClient,
    private val profile: ProxyProfile,
    private val protector: SocketProtector,
    private val tun: TunWriter,
    private val appResolver: AppResolver?,
    private val dnsBlocker: DnsBlocker?,
    private val onFinished: (SessionKey) -> Unit,
) {

    private val closed = AtomicBoolean(false)
    private val useSocksUdp = profile.type == ProxyType.SOCKS5 && profile.udpOverSocks
    private val isDns = key.destPort == DNS_PORT

    private var packageResolved = false
    private var cachedPackage: String? = null

    @Volatile
    private var association: UdpAssociation? = null

    @Volatile
    private var relaySocket: DatagramSocket? = null

    @Volatile
    private var started = false

    @Volatile
    var lastActivity: Long = SystemClock.elapsedRealtime()
        private set

    val idleTimeoutMs: Long get() = if (isDns) DNS_IDLE_MS else UDP_IDLE_MS

    /** 转发一个从 TUN 收到的 UDP 载荷。 */
    fun send(payload: ByteArray) {
        lastActivity = SystemClock.elapsedRealtime()
        if (isDns) {
            logDnsQuery(payload)
            if (dnsBlocker != null && tryBlockDns(payload)) return
        }
        when {
            // 直连解析优先判断：这条路径完全不碰代理
            isDns && profile.dnsMode == DnsMode.DIRECT -> sendDnsDirect(payload)
            useSocksUdp -> sendOverSocks(payload)
            isDns -> sendDnsOverTcp(payload)
            else -> {
                // 代理不支持 UDP：静默丢弃。应用侧通常会自行回退到 TCP。
                Log.d(TAG, "丢弃 UDP（代理未启用 UDP 转发）：$key")
                finish()
            }
        }
    }

    // ------------------------------------------------------- SOCKS5 UDP

    private fun sendOverSocks(payload: ByteArray) {
        if (!started) {
            started = true
            scope.launch(ioDispatcher) {
                if (!openAssociation()) {
                    finish()
                    return@launch
                }
                forward(payload)
                receiveLoop()
            }
        } else {
            scope.launch(ioDispatcher) { forward(payload) }
        }
    }

    private fun openAssociation(): Boolean = try {
        val assoc = proxyClient.openUdpAssociate()
        val socket = DatagramSocket()
        if (!protector.protect(socket)) {
            socket.close()
            assoc.close()
            false
        } else {
            association = assoc
            relaySocket = socket
            true
        }
    } catch (e: Exception) {
        Log.d(TAG, "UDP ASSOCIATE 失败 $key：${e.message}")
        false
    }

    private fun forward(payload: ByteArray) {
        val socket = relaySocket ?: return
        val relay = association?.relayAddress ?: return
        try {
            // SOCKS5 UDP 请求头：RSV(2) FRAG(1) ATYP ADDR PORT
            val framed = ByteArrayOutputStream(payload.size + 10).apply {
                write(0)
                write(0)
                write(0)
                write(ProxyClient.ATYP_IPV4)
                write(key.destIp.toIpv4Bytes())
                write((key.destPort ushr 8) and 0xFF)
                write(key.destPort and 0xFF)
                write(payload)
            }.toByteArray()
            socket.send(DatagramPacket(framed, framed.size, relay))
            VpnStateHolder.uploadCounter.addAndGet(payload.size.toLong())
        } catch (e: Exception) {
            Log.d(TAG, "UDP 发送失败 $key：${e.message}")
            finish()
        }
    }

    private fun receiveLoop() {
        val socket = relaySocket ?: return
        val buffer = ByteArray(MAX_DATAGRAM)
        val packet = DatagramPacket(buffer, buffer.size)
        try {
            while (!closed.get()) {
                packet.setData(buffer, 0, buffer.size)
                socket.receive(packet)
                lastActivity = SystemClock.elapsedRealtime()
                val payloadOffset = socksPayloadOffset(buffer, packet.length) ?: continue
                val payloadLength = packet.length - payloadOffset
                if (payloadLength <= 0) continue
                VpnStateHolder.downloadCounter.addAndGet(payloadLength.toLong())
                writeBackToTun(buffer, payloadOffset, payloadLength)
            }
        } catch (e: Exception) {
            if (!closed.get()) Log.d(TAG, "UDP 接收结束 $key：${e.message}")
        } finally {
            finish()
        }
    }

    /** 跳过 SOCKS5 UDP 应答头，返回真实载荷的起始下标。 */
    private fun socksPayloadOffset(buffer: ByteArray, length: Int): Int? {
        if (length < 10) return null
        var offset = 3 // RSV(2) + FRAG(1)
        val addressType = buffer.u8(offset)
        offset += 1
        offset += when (addressType) {
            ProxyClient.ATYP_IPV4 -> 4
            ProxyClient.ATYP_IPV6 -> 16
            ProxyClient.ATYP_DOMAIN -> {
                if (offset >= length) return null
                1 + buffer.u8(offset)
            }

            else -> return null
        }
        offset += 2 // 端口
        return if (offset < length) offset else null
    }

    // -------------------------------------------------------- DNS / TCP

    /**
     * DNS 是一问一答，直接为每次查询开一条隧道：
     * TCP 承载的 DNS 报文前面多两个字节的长度前缀。
     */
    private fun sendDnsOverTcp(payload: ByteArray) {
        scope.launch(ioDispatcher) {
            try {
                proxyClient.connectTcp(key.destIp.toInetAddress(), key.destPort).use { socket ->
                    socket.soTimeout = DNS_TIMEOUT_MS
                    socket.getOutputStream().apply {
                        write((payload.size ushr 8) and 0xFF)
                        write(payload.size and 0xFF)
                        write(payload)
                        flush()
                    }
                    VpnStateHolder.uploadCounter.addAndGet(payload.size.toLong())

                    val input = socket.getInputStream()
                    val header = input.readExactly(2)
                    val length = ((header[0].toInt() and 0xFF) shl 8) or (header[1].toInt() and 0xFF)
                    if (length in 1..MAX_DATAGRAM) {
                        val response = input.readExactly(length)
                        VpnStateHolder.downloadCounter.addAndGet(length.toLong())
                        writeBackToTun(response, 0, length)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "DNS over TCP 失败 $key：${e.message}")
            } finally {
                finish()
            }
        }
    }

    /**
     * 本地直连解析：用一个 protect 过的 socket 直接问 DNS 服务器。
     * 快，但查询内容对所在网络可见 —— 这是用户在配置里明确选择的取舍。
     */
    private fun sendDnsDirect(payload: ByteArray) {
        scope.launch(ioDispatcher) {
            try {
                DatagramSocket().use { socket ->
                    if (!protector.protect(socket)) return@launch
                    socket.soTimeout = DNS_TIMEOUT_MS
                    socket.send(
                        DatagramPacket(
                            payload,
                            payload.size,
                            key.destIp.toInetAddress(),
                            key.destPort,
                        ),
                    )
                    VpnStateHolder.uploadCounter.addAndGet(payload.size.toLong())

                    val buffer = ByteArray(MAX_DATAGRAM)
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    VpnStateHolder.downloadCounter.addAndGet(response.length.toLong())
                    writeBackToTun(buffer, 0, response.length)
                }
            } catch (e: Exception) {
                Log.d(TAG, "直连 DNS 失败 $key：${e.message}")
            } finally {
                finish()
            }
        }
    }

    // ------------------------------------------------------------ 日志

    /**
     * 命中拦截规则的查询不再转发，直接伪造一个应答回给应用。
     *
     * @return true 表示已拦截并作答，本次会话到此结束。
     */
    private fun tryBlockDns(payload: ByteArray): Boolean {
        val blocker = dnsBlocker ?: return false
        val question = DnsMessage.readQuestion(payload, 0, payload.size) ?: return false
        val packageName = resolvePackage()
        val ip = blocker.resolve(question, packageName) ?: return false
        val response = DnsResponse.buildBlockedResponse(payload, payload.size, ip) ?: return false
        writeBackToTun(response, 0, response.size)
        val rule = if (blocker.isAppBlocked(packageName)) "应用" else "域名"
        TunnelLog.dns(
            "DNS ${question.typeName} ${question.name} → $ip（按$rule 拦截）",
            packageName,
            question.name,
        )
        finish()
        return true
    }

    private fun logDnsQuery(payload: ByteArray) {
        val question = DnsMessage.readQuestion(payload, 0, payload.size) ?: return
        TunnelLog.dns(
            "DNS ${question.typeName} ${question.name}",
            resolvePackage(),
            question.name,
        )
    }

    /** UID 反查要跨进程，一条会话只做一次。 */
    private fun resolvePackage(): String? {
        if (!packageResolved) {
            cachedPackage = appResolver?.resolve(OsConstants.IPPROTO_UDP, key)
            packageResolved = true
        }
        return cachedPackage
    }

    /** 把 DNS 应答里的 A 记录喂给反查表，好让后续的连接日志显示域名。 */
    private fun rememberDnsAnswers(payload: ByteArray, offset: Int, length: Int) {
        if (!isDns) return
        DnsMessage.readAnswers(payload, offset, length).forEach { (name, address) ->
            HostRegistry.remember(address, name)
        }
    }

    // ------------------------------------------------------------ 回写

    private fun writeBackToTun(payload: ByteArray, offset: Int, length: Int) {
        rememberDnsAnswers(payload, offset, length)
        val output = ByteArray(28 + length)
        val size = PacketBuilder.writeUdp(
            output = output,
            sourceIp = key.destIp,
            sourcePort = key.destPort,
            destIp = key.sourceIp,
            destPort = key.sourcePort,
            payload = payload,
            payloadOffset = offset,
            payloadLength = length,
        )
        tun.enqueue(output, size)
    }

    fun finish() {
        if (!closed.compareAndSet(false, true)) return
        // 关闭 socket 即可让阻塞中的 receive() 抛异常退出，无需再取消协程
        runCatching { relaySocket?.close() }
        runCatching { association?.close() }
        onFinished(key)
    }

    private companion object {
        const val TAG = "UdpSession"
        const val DNS_PORT = 53
        const val DNS_TIMEOUT_MS = 10_000
        const val MAX_DATAGRAM = 65507
        const val DNS_IDLE_MS = 20_000L
        const val UDP_IDLE_MS = 120_000L
    }
}
