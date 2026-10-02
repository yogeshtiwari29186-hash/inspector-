package com.example.vpn.net

/** RFC 1071 定义的 16 位反码和。 */
object Checksum {

    /** 对 [length] 字节做反码求和，[initial] 用于把伪头部的和接续进来。 */
    fun compute(data: ByteArray, offset: Int, length: Int, initial: Long = 0L): Int {
        var sum = initial
        var index = offset
        val end = offset + length
        while (index + 1 < end) {
            sum += data.u16(index)
            index += 2
        }
        // 奇数长度时最后一字节按高位对齐补零
        if (index < end) sum += data.u8(index) shl 8
        while ((sum ushr 16) != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    /** TCP/UDP 校验和覆盖的伪头部：源地址、目的地址、协议号与传输层长度。 */
    fun pseudoHeaderSum(sourceIp: Int, destIp: Int, protocol: Int, transportLength: Int): Long {
        var sum = 0L
        sum += ((sourceIp ushr 16) and 0xFFFF).toLong()
        sum += (sourceIp and 0xFFFF).toLong()
        sum += ((destIp ushr 16) and 0xFFFF).toLong()
        sum += (destIp and 0xFFFF).toLong()
        sum += protocol.toLong()
        sum += transportLength.toLong()
        return sum
    }
}
