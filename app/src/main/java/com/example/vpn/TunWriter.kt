package com.example.vpn

/** 把构造好的 IP 包送回 TUN 设备。实现方负责拷贝数据，调用后缓冲区即可复用。 */
interface TunWriter {
    fun enqueue(packet: ByteArray, length: Int)
}
