package com.example.vpn.net

/**
 * IP → 域名的反查表，数据来自流经隧道的 DNS 应答。
 *
 * 隧道里看到的目标只有 IP，有了这张表，连接日志才能显示 `github.com:443`
 * 而不是让人无从判断的 `140.82.121.4:443`。
 */
object HostRegistry {

    private const val CAPACITY = 512

    private val lock = Any()

    // accessOrder = true 让 LinkedHashMap 按访问顺序淘汰，即最近用过的域名留得更久
    private val names = object : LinkedHashMap<Int, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>): Boolean =
            size > CAPACITY
    }

    fun remember(address: Int, name: String) {
        if (name.isEmpty()) return
        synchronized(lock) { names[address] = name }
    }

    fun lookup(address: Int): String? = synchronized(lock) { names[address] }

    /** 有域名就用域名，没有就退回点分十进制。 */
    fun describe(address: Int, port: Int): String = "${lookup(address) ?: address.toIpv4String()}:$port"

    fun clear() {
        synchronized(lock) { names.clear() }
    }
}
