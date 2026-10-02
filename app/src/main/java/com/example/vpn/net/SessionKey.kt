package com.example.vpn.net

/** 四元组，用作会话表的键。 */
data class SessionKey(
    val sourceIp: Int,
    val sourcePort: Int,
    val destIp: Int,
    val destPort: Int,
) {
    override fun toString(): String =
        "${sourceIp.toIpv4String()}:$sourcePort → ${destIp.toIpv4String()}:$destPort"
}

/**
 * 序列号是模 2^32 的循环量，不能直接比大小。
 * 这里用 32 位有符号差判断先后，正确处理回绕。
 */
internal fun seqLessThan(a: Long, b: Long): Boolean = (a - b).toInt() < 0

internal fun seqLessOrEqual(a: Long, b: Long): Boolean = (a - b).toInt() <= 0

/** 序列号前进 [delta] 字节，保持在 32 位范围内。 */
internal fun seqAdvance(seq: Long, delta: Int): Long = (seq + delta) and 0xFFFFFFFFL
