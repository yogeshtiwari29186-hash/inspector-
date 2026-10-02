package com.example.vpn.net

import java.util.concurrent.atomic.AtomicInteger

/**
 * 构造写回 TUN 的 IPv4 数据包。
 *
 * 所有方法都把结果写进调用方提供的缓冲区并返回包长度，热路径上不额外分配。
 */
object PacketBuilder {

    private const val DEFAULT_TTL = 64
    private const val FLAG_DONT_FRAGMENT = 0x4000
    private val identification = AtomicInteger(1)

    /**
     * 写入一个 IPv4 + TCP 包。
     *
     * [mss] 大于 0 时附加 MSS 选项 —— 只在 SYN-ACK 里需要，用于告诉本机内核
     * 单个报文段的上限，避免它发出超过隧道 MTU 的数据。
     */
    fun writeTcp(
        output: ByteArray,
        sourceIp: Int,
        sourcePort: Int,
        destIp: Int,
        destPort: Int,
        sequence: Long,
        acknowledgment: Long,
        flags: Int,
        window: Int,
        payload: ByteArray? = null,
        payloadOffset: Int = 0,
        payloadLength: Int = 0,
        mss: Int = 0,
    ): Int {
        val optionsLength = if (mss > 0) 4 else 0
        val tcpLength = TcpHeader.MIN_SIZE + optionsLength + payloadLength
        val totalLength = Ipv4Header.MIN_SIZE + tcpLength

        writeIpv4Header(output, totalLength, PROTO_TCP, sourceIp, destIp)

        val tcp = Ipv4Header.MIN_SIZE
        output.putU16(tcp, sourcePort)
        output.putU16(tcp + 2, destPort)
        output.putU32(tcp + 4, sequence and 0xFFFFFFFFL)
        output.putU32(tcp + 8, acknowledgment and 0xFFFFFFFFL)
        output.putU8(tcp + 12, ((TcpHeader.MIN_SIZE + optionsLength) / 4) shl 4)
        output.putU8(tcp + 13, flags)
        output.putU16(tcp + 14, window)
        output.putU16(tcp + 16, 0) // 校验和占位
        output.putU16(tcp + 18, 0) // 紧急指针

        if (optionsLength > 0) {
            output.putU8(tcp + 20, 2) // kind = MSS
            output.putU8(tcp + 21, 4) // length
            output.putU16(tcp + 22, mss)
        }

        if (payload != null && payloadLength > 0) {
            System.arraycopy(
                payload,
                payloadOffset,
                output,
                tcp + TcpHeader.MIN_SIZE + optionsLength,
                payloadLength,
            )
        }

        val pseudo = Checksum.pseudoHeaderSum(sourceIp, destIp, PROTO_TCP, tcpLength)
        output.putU16(tcp + 16, Checksum.compute(output, tcp, tcpLength, pseudo))
        return totalLength
    }

    /** 写入一个 IPv4 + UDP 包。 */
    fun writeUdp(
        output: ByteArray,
        sourceIp: Int,
        sourcePort: Int,
        destIp: Int,
        destPort: Int,
        payload: ByteArray,
        payloadOffset: Int,
        payloadLength: Int,
    ): Int {
        val udpLength = UdpHeader.SIZE + payloadLength
        val totalLength = Ipv4Header.MIN_SIZE + udpLength

        writeIpv4Header(output, totalLength, PROTO_UDP, sourceIp, destIp)

        val udp = Ipv4Header.MIN_SIZE
        output.putU16(udp, sourcePort)
        output.putU16(udp + 2, destPort)
        output.putU16(udp + 4, udpLength)
        output.putU16(udp + 6, 0) // 校验和占位

        System.arraycopy(payload, payloadOffset, output, udp + UdpHeader.SIZE, payloadLength)

        val pseudo = Checksum.pseudoHeaderSum(sourceIp, destIp, PROTO_UDP, udpLength)
        val checksum = Checksum.compute(output, udp, udpLength, pseudo)
        // UDP 校验和为 0 表示"未计算"，真值为 0 时按 RFC 768 写全 1
        output.putU16(udp + 6, if (checksum == 0) 0xFFFF else checksum)
        return totalLength
    }

    private fun writeIpv4Header(
        output: ByteArray,
        totalLength: Int,
        protocol: Int,
        sourceIp: Int,
        destIp: Int,
    ) {
        output.putU8(0, 0x45) // 版本 4，首部 5 个 32 位字
        output.putU8(1, 0) // DSCP / ECN
        output.putU16(2, totalLength)
        output.putU16(4, identification.getAndIncrement() and 0xFFFF)
        output.putU16(6, FLAG_DONT_FRAGMENT)
        output.putU8(8, DEFAULT_TTL)
        output.putU8(9, protocol)
        output.putU16(10, 0) // 校验和占位
        output.putIpv4(12, sourceIp)
        output.putIpv4(16, destIp)
        output.putU16(10, Checksum.compute(output, 0, Ipv4Header.MIN_SIZE))
    }
}
