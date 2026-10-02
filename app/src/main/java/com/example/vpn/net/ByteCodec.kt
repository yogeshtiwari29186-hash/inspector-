package com.example.vpn.net

import java.net.InetAddress

/** 网络字节序（大端）读写辅助。 */

internal fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

internal fun ByteArray.u16(index: Int): Int = (u8(index) shl 8) or u8(index + 1)

/** 读 32 位无符号量；用 Long 承载以避开 Kotlin Int 的符号问题。 */
internal fun ByteArray.u32(index: Int): Long =
    (u16(index).toLong() shl 16) or u16(index + 2).toLong()

/** IPv4 地址按 32 位整数读出，用作会话表的键既快又省内存。 */
internal fun ByteArray.ipv4(index: Int): Int =
    (u8(index) shl 24) or (u8(index + 1) shl 16) or (u8(index + 2) shl 8) or u8(index + 3)

internal fun ByteArray.putU8(index: Int, value: Int) {
    this[index] = (value and 0xFF).toByte()
}

internal fun ByteArray.putU16(index: Int, value: Int) {
    this[index] = ((value ushr 8) and 0xFF).toByte()
    this[index + 1] = (value and 0xFF).toByte()
}

internal fun ByteArray.putU32(index: Int, value: Long) {
    this[index] = ((value ushr 24) and 0xFF).toByte()
    this[index + 1] = ((value ushr 16) and 0xFF).toByte()
    this[index + 2] = ((value ushr 8) and 0xFF).toByte()
    this[index + 3] = (value and 0xFF).toByte()
}

internal fun ByteArray.putIpv4(index: Int, value: Int) {
    putU32(index, value.toLong() and 0xFFFFFFFFL)
}

internal fun Int.toIpv4Bytes(): ByteArray = byteArrayOf(
    ((this ushr 24) and 0xFF).toByte(),
    ((this ushr 16) and 0xFF).toByte(),
    ((this ushr 8) and 0xFF).toByte(),
    (this and 0xFF).toByte(),
)

internal fun Int.toInetAddress(): InetAddress = InetAddress.getByAddress(toIpv4Bytes())

internal fun Int.toIpv4String(): String =
    "${(this ushr 24) and 0xFF}.${(this ushr 16) and 0xFF}.${(this ushr 8) and 0xFF}.${this and 0xFF}"
