package com.example.vpn.proxy

import java.net.DatagramSocket
import java.net.Socket

/**
 * 把 socket 排除出隧道。
 *
 * 隧道建立后，本应用发往代理服务器的连接如果不加保护，会被系统重新路由回 TUN，
 * 形成自我循环。[android.net.VpnService.protect] 就是用来打破这个循环的。
 */
interface SocketProtector {
    fun protect(socket: Socket): Boolean
    fun protect(socket: DatagramSocket): Boolean
}
