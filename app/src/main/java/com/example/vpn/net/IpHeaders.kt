package com.example.vpn.net

const val PROTO_ICMP = 1
const val PROTO_TCP = 6
const val PROTO_UDP = 17

/** IPv4 首部。选项字段不解析，但 [headerLength] 已把它算在内。 */
class Ipv4Header(
    val headerLength: Int,
    val totalLength: Int,
    val protocol: Int,
    val sourceIp: Int,
    val destIp: Int,
) {
    val payloadLength: Int get() = totalLength - headerLength

    companion object {
        const val MIN_SIZE = 20

        /** 解析失败返回 null（畸形包直接丢弃，不抛异常 —— 转发热路径上异常代价太高）。 */
        fun parse(buffer: ByteArray, length: Int): Ipv4Header? {
            if (length < MIN_SIZE) return null
            val versionAndIhl = buffer.u8(0)
            if ((versionAndIhl ushr 4) != 4) return null

            val headerLength = (versionAndIhl and 0x0F) * 4
            if (headerLength < MIN_SIZE || headerLength > length) return null

            val totalLength = buffer.u16(2)
            if (totalLength < headerLength || totalLength > length) return null

            // 本栈不做分片重组：MF 置位或分片偏移非零的包一律丢弃。
            // TUN 的 MTU 由我们自己设定，正常流量不会走到这里。
            val fragmentField = buffer.u16(6)
            val moreFragments = (fragmentField and 0x2000) != 0
            val fragmentOffset = fragmentField and 0x1FFF
            if (moreFragments || fragmentOffset != 0) return null

            return Ipv4Header(
                headerLength = headerLength,
                totalLength = totalLength,
                protocol = buffer.u8(9),
                sourceIp = buffer.ipv4(12),
                destIp = buffer.ipv4(16),
            )
        }
    }
}

/** TCP 首部。 */
class TcpHeader(
    val sourcePort: Int,
    val destPort: Int,
    val sequence: Long,
    val acknowledgment: Long,
    val dataOffset: Int,
    val flags: Int,
    val window: Int,
) {
    val isFin: Boolean get() = (flags and FIN) != 0
    val isSyn: Boolean get() = (flags and SYN) != 0
    val isRst: Boolean get() = (flags and RST) != 0
    val isAck: Boolean get() = (flags and ACK) != 0

    override fun toString(): String = buildString {
        if (isSyn) append("SYN ")
        if (isAck) append("ACK ")
        if (isFin) append("FIN ")
        if (isRst) append("RST ")
        append("seq=").append(sequence).append(" ack=").append(acknowledgment)
    }

    companion object {
        const val MIN_SIZE = 20

        const val FIN = 0x01
        const val SYN = 0x02
        const val RST = 0x04
        const val PSH = 0x08
        const val ACK = 0x10
        const val URG = 0x20

        fun parse(buffer: ByteArray, offset: Int, length: Int): TcpHeader? {
            if (length < MIN_SIZE) return null
            val dataOffset = ((buffer.u8(offset + 12) ushr 4) and 0x0F) * 4
            if (dataOffset < MIN_SIZE || dataOffset > length) return null
            return TcpHeader(
                sourcePort = buffer.u16(offset),
                destPort = buffer.u16(offset + 2),
                sequence = buffer.u32(offset + 4),
                acknowledgment = buffer.u32(offset + 8),
                dataOffset = dataOffset,
                flags = buffer.u8(offset + 13),
                window = buffer.u16(offset + 14),
            )
        }
    }
}

/** UDP 首部。 */
class UdpHeader(
    val sourcePort: Int,
    val destPort: Int,
    val length: Int,
) {
    val payloadLength: Int get() = length - SIZE

    companion object {
        const val SIZE = 8

        fun parse(buffer: ByteArray, offset: Int, available: Int): UdpHeader? {
            if (available < SIZE) return null
            val length = buffer.u16(offset + 4)
            if (length < SIZE || length > available) return null
            return UdpHeader(
                sourcePort = buffer.u16(offset),
                destPort = buffer.u16(offset + 2),
                length = length,
            )
        }
    }
}
