package com.example.vpn.net

/** 一条 IPv4 路由：网络地址 + 前缀长度。 */
data class Route(val address: Int, val prefixLength: Int) {
    val addressLiteral: String get() = address.toIpv4String()

    override fun toString(): String = "$addressLiteral/$prefixLength"
}

/**
 * 计算需要导入隧道的路由。
 *
 * "绕过内网"不能靠在转发层丢包实现 —— 那样流量根本到不了本地网络。
 * 正确做法是从 0.0.0.0/0 里把私有网段挖掉，只把剩余部分声明给 TUN，
 * 私有地址便会继续走物理网卡。
 */
object RouteCalculator {

    /** RFC 1918 私有网段，外加回环、链路本地、组播与保留段。 */
    private val EXCLUDED = listOf(
        cidr(0, 0, 0, 0, 8), // "本网络"
        cidr(10, 0, 0, 0, 8), // 私有 A 类
        cidr(127, 0, 0, 0, 8), // 回环
        cidr(169, 254, 0, 0, 16), // 链路本地
        cidr(172, 16, 0, 0, 12), // 私有 B 类
        cidr(192, 168, 0, 0, 16), // 私有 C 类
        cidr(224, 0, 0, 0, 4), // 组播
        cidr(240, 0, 0, 0, 4), // 保留
    )

    /** 整条默认路由。 */
    val DEFAULT: List<Route> = listOf(Route(0, 0))

    /** 默认路由减去私有网段后的最小覆盖集合。 */
    val BYPASS_PRIVATE: List<Route> by lazy {
        buildList { subtract(Route(0, 0), this) }
    }

    /**
     * 把 [route] 中不与排除网段重叠的部分收集进 [output]。
     *
     * 完全被排除的直接丢弃；部分重叠的一分为二递归处理，
     * 因此结果自然是覆盖同一地址空间的最少路由数。
     */
    private fun subtract(route: Route, output: MutableList<Route>) {
        if (EXCLUDED.any { route.isContainedIn(it) }) return
        if (EXCLUDED.none { route.overlaps(it) }) {
            output += route
            return
        }
        // 还有重叠但没被完全覆盖：细分一级继续判断
        if (route.prefixLength >= 32) return
        val childPrefix = route.prefixLength + 1
        val step = 1 shl (32 - childPrefix)
        subtract(Route(route.address, childPrefix), output)
        subtract(Route(route.address + step, childPrefix), output)
    }

    private fun Route.mask(): Int =
        if (prefixLength == 0) 0 else (-1 shl (32 - prefixLength))

    private fun Route.isContainedIn(other: Route): Boolean =
        prefixLength >= other.prefixLength && (address and other.mask()) == other.address

    private fun Route.overlaps(other: Route): Boolean {
        val shared = if (prefixLength < other.prefixLength) mask() else other.mask()
        return (address and shared) == (other.address and shared)
    }

    private fun cidr(a: Int, b: Int, c: Int, d: Int, prefix: Int): Route =
        Route((a shl 24) or (b shl 16) or (c shl 8) or d, prefix)
}
