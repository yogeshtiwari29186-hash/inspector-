package com.example.vpn

data class ProxyProfile(
    val id: String = "devtraffic-local",
    val name: String = "DevTraffic Local Inspector",
    val type: ProxyType = ProxyType.HTTP,
    val host: String = "127.0.0.1",
    val port: Int = 8080,
    val username: String = "",
    val password: String = "",
    val udpOverSocks: Boolean = false,
    val dnsMode: DnsMode = DnsMode.PROXY
) {
    val requiresAuth: Boolean get() = username.isNotEmpty()
    val summary: String get() = "${type.name} · ${host}:${port}"
}

enum class ProxyType { SOCKS5, HTTP }

enum class DnsMode { PROXY, DIRECT }
