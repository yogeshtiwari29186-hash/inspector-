package com.example.vpn.proxy

import android.util.Base64
import com.example.vpn.ProxyProfile
import com.example.vpn.ProxyType
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 与上游 HTTP / SOCKS5 代理对话的客户端。
 *
 * [proxyAddress] 必须是**已解析**的地址：隧道一旦建立，域名解析会被路由进隧道本身，
 * 因此代理服务器的主机名必须在 establish() 之前解析好并在整个会话期间复用。
 */
class ProxyClient(
    private val profile: ProxyProfile,
    private val proxyAddress: InetSocketAddress,
    private val protector: SocketProtector,
) {

    /** 与目标建立一条 TCP 隧道，返回握手完成、可直接收发数据的 socket。 */
    @Throws(IOException::class)
    fun connectTcp(destination: InetAddress, destinationPort: Int): Socket {
        val socket = openProtectedSocket()
        try {
            socket.connect(proxyAddress, CONNECT_TIMEOUT_MS)
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            socket.tcpNoDelay = true
            when (profile.type) {
                ProxyType.SOCKS5 -> socks5Connect(socket, destination, destinationPort)
                ProxyType.HTTP -> httpConnect(socket, destination, destinationPort)
            }
            // 握手完毕后回到阻塞读，转发循环靠 close() 来中断
            socket.soTimeout = 0
            return socket
        } catch (t: Throwable) {
            runCatching { socket.close() }
            throw t
        }
    }

    /**
     * 申请一条 SOCKS5 UDP 转发通道。
     *
     * 控制用的 TCP 连接必须在整个 UDP 会话期间保持打开 —— 一旦关闭，代理就会回收该转发端口。
     */
    @Throws(IOException::class)
    fun openUdpAssociate(): UdpAssociation {
        require(profile.type == ProxyType.SOCKS5) { "UDP ASSOCIATE 仅 SOCKS5 支持" }
        val socket = openProtectedSocket()
        try {
            socket.connect(proxyAddress, CONNECT_TIMEOUT_MS)
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            socket.keepAlive = true
            negotiateAuth(socket.getInputStream(), socket.getOutputStream())

            // 客户端不预先知道自己的出口地址，按 RFC 1928 用全零地址表示"任意"
            val request = ByteArrayOutputStream().apply {
                write(SOCKS_VERSION)
                write(CMD_UDP_ASSOCIATE)
                write(0x00)
                write(ATYP_IPV4)
                write(byteArrayOf(0, 0, 0, 0))
                write(0)
                write(0)
            }
            socket.getOutputStream().apply {
                write(request.toByteArray())
                flush()
            }

            val bound = readSocks5Reply(socket.getInputStream())
            // 部分代理在 BND.ADDR 里回 0.0.0.0，表示"用你连我的那个地址"
            val relayHost = if (bound.address.isAnyLocalAddress) proxyAddress.address else bound.address
            socket.soTimeout = 0
            return UdpAssociation(socket, InetSocketAddress(relayHost, bound.port))
        } catch (t: Throwable) {
            runCatching { socket.close() }
            throw t
        }
    }

    private fun openProtectedSocket(): Socket {
        val socket = Socket()
        // 先 bind 才会真正创建底层 fd，protect() 需要拿到这个 fd
        socket.bind(InetSocketAddress(0))
        if (!protector.protect(socket)) {
            runCatching { socket.close() }
            throw IOException("无法保护 socket，隧道可能已关闭")
        }
        return socket
    }

    // ---------------------------------------------------------------- SOCKS5

    private fun socks5Connect(socket: Socket, destination: InetAddress, port: Int) {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        negotiateAuth(input, output)

        val request = ByteArrayOutputStream().apply {
            write(SOCKS_VERSION)
            write(CMD_CONNECT)
            write(0x00)
            writeAddress(destination, port)
        }
        output.write(request.toByteArray())
        output.flush()
        readSocks5Reply(input)
    }

    private fun negotiateAuth(input: InputStream, output: OutputStream) {
        val methods = if (profile.requiresAuth) {
            byteArrayOf(SOCKS_VERSION.toByte(), 2, METHOD_NO_AUTH.toByte(), METHOD_USER_PASS.toByte())
        } else {
            byteArrayOf(SOCKS_VERSION.toByte(), 1, METHOD_NO_AUTH.toByte())
        }
        output.write(methods)
        output.flush()

        val greeting = input.readExactly(2)
        if (greeting[0].toInt() and 0xFF != SOCKS_VERSION) {
            throw IOException("代理返回了非 SOCKS5 响应")
        }
        when (val method = greeting[1].toInt() and 0xFF) {
            METHOD_NO_AUTH -> Unit
            METHOD_USER_PASS -> performUserPassAuth(input, output)
            0xFF -> throw IOException("代理拒绝了所有认证方式")
            else -> throw IOException("代理要求不支持的认证方式：$method")
        }
    }

    private fun performUserPassAuth(input: InputStream, output: OutputStream) {
        val user = profile.username.toByteArray(Charsets.UTF_8)
        val pass = profile.password.toByteArray(Charsets.UTF_8)
        if (user.size > 255 || pass.size > 255) throw IOException("用户名或密码超出 255 字节")

        val payload = ByteArrayOutputStream().apply {
            write(0x01) // 用户名/密码认证子协商版本
            write(user.size)
            write(user)
            write(pass.size)
            write(pass)
        }
        output.write(payload.toByteArray())
        output.flush()

        val reply = input.readExactly(2)
        if (reply[1].toInt() != 0) throw IOException("代理认证失败：用户名或密码错误")
    }

    /** 读取 SOCKS5 应答并返回其中的 BND.ADDR / BND.PORT。 */
    private fun readSocks5Reply(input: InputStream): InetSocketAddress {
        val header = input.readExactly(4)
        if (header[0].toInt() and 0xFF != SOCKS_VERSION) throw IOException("SOCKS5 应答格式错误")
        val reply = header[1].toInt() and 0xFF
        if (reply != 0) throw IOException(socksErrorMessage(reply))

        val address = when (val atyp = header[3].toInt() and 0xFF) {
            ATYP_IPV4 -> InetAddress.getByAddress(input.readExactly(4))
            ATYP_IPV6 -> InetAddress.getByAddress(input.readExactly(16))
            ATYP_DOMAIN -> {
                val length = input.readExactly(1)[0].toInt() and 0xFF
                val host = String(input.readExactly(length), Charsets.UTF_8)
                // 这里的域名由代理返回，解析它是安全的（不经过隧道的用户流量路径）
                InetAddress.getByName(host)
            }

            else -> throw IOException("SOCKS5 应答含未知地址类型：$atyp")
        }
        val portBytes = input.readExactly(2)
        val port = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)
        return InetSocketAddress(address, port)
    }

    // ------------------------------------------------------------ HTTP CONNECT

    private fun httpConnect(socket: Socket, destination: InetAddress, port: Int) {
        val hostLiteral = destination.toLiteral(port)
        val request = buildString {
            append("CONNECT ").append(hostLiteral).append(" HTTP/1.1\r\n")
            append("Host: ").append(hostLiteral).append("\r\n")
            if (profile.requiresAuth) {
                val token = Base64.encodeToString(
                    "${profile.username}:${profile.password}".toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP,
                )
                append("Proxy-Authorization: Basic ").append(token).append("\r\n")
            }
            append("Proxy-Connection: Keep-Alive\r\n")
            append("\r\n")
        }
        socket.getOutputStream().apply {
            write(request.toByteArray(Charsets.ISO_8859_1))
            flush()
        }

        val statusLine = socket.getInputStream().readHttpHeaders()
        val code = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
            ?: throw IOException("代理返回了无法解析的响应：$statusLine")
        if (code !in 200..299) {
            throw IOException(
                when (code) {
                    407 -> "代理要求身份验证（407）"
                    403 -> "代理拒绝了该目标（403）"
                    else -> "代理 CONNECT 失败：$statusLine"
                },
            )
        }
    }

    /** 读到空行为止，返回状态行。 */
    private fun InputStream.readHttpHeaders(): String {
        val statusLine = readLineCrLf()
        while (true) {
            val line = readLineCrLf()
            if (line.isEmpty()) break
        }
        return statusLine
    }

    private fun InputStream.readLineCrLf(): String {
        val buffer = StringBuilder()
        while (true) {
            val b = read()
            if (b < 0) {
                if (buffer.isEmpty()) throw IOException("代理提前关闭了连接")
                break
            }
            if (b == '\n'.code) break
            if (b != '\r'.code) buffer.append(b.toChar())
            if (buffer.length > MAX_HEADER_LINE) throw IOException("代理响应头过长")
        }
        return buffer.toString()
    }

    private fun ByteArrayOutputStream.writeAddress(address: InetAddress, port: Int) {
        when (address) {
            is Inet4Address -> {
                write(ATYP_IPV4)
                write(address.address)
            }

            is Inet6Address -> {
                write(ATYP_IPV6)
                write(address.address)
            }

            else -> throw IOException("不支持的地址类型：$address")
        }
        write((port shr 8) and 0xFF)
        write(port and 0xFF)
    }

    companion object {
        const val SOCKS_VERSION = 0x05
        const val CMD_CONNECT = 0x01
        const val CMD_UDP_ASSOCIATE = 0x03
        const val ATYP_IPV4 = 0x01
        const val ATYP_DOMAIN = 0x03
        const val ATYP_IPV6 = 0x04

        private const val METHOD_NO_AUTH = 0x00
        private const val METHOD_USER_PASS = 0x02
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val HANDSHAKE_TIMEOUT_MS = 15_000
        private const val MAX_HEADER_LINE = 8192

        private fun socksErrorMessage(code: Int): String = when (code) {
            1 -> "SOCKS5：代理内部错误"
            2 -> "SOCKS5：规则不允许该连接"
            3 -> "SOCKS5：网络不可达"
            4 -> "SOCKS5：主机不可达"
            5 -> "SOCKS5：目标拒绝连接"
            6 -> "SOCKS5：TTL 超时"
            7 -> "SOCKS5：不支持的命令"
            8 -> "SOCKS5：不支持的地址类型"
            else -> "SOCKS5：未知错误（$code）"
        }
    }
}

/** SOCKS5 UDP 转发通道：控制连接 + 转发端点。 */
class UdpAssociation(
    private val controlSocket: Socket,
    val relayAddress: InetSocketAddress,
) : AutoCloseable {

    val isAlive: Boolean get() = !controlSocket.isClosed && controlSocket.isConnected

    override fun close() {
        runCatching { controlSocket.close() }
    }
}

/** 严格读满 [count] 字节，不足即视为连接中断。 */
@Throws(IOException::class)
internal fun InputStream.readExactly(count: Int): ByteArray {
    val buffer = ByteArray(count)
    var offset = 0
    while (offset < count) {
        val read = read(buffer, offset, count - offset)
        if (read < 0) throw IOException("连接被提前关闭（还需 ${count - offset} 字节）")
        offset += read
    }
    return buffer
}

internal fun InetAddress.toLiteral(port: Int): String =
    if (this is Inet6Address) "[${hostAddress}]:$port" else "${hostAddress}:$port"
