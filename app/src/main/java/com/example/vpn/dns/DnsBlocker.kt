package com.example.vpn.dns

import com.example.vpn.net.DnsQuestion

/** DNS blocker is disabled for the Inspector VPN; DNS is forwarded normally. */
class DnsBlocker {
    fun resolve(question: DnsQuestion, packageName: String?): String? = null
    fun isAppBlocked(packageName: String?): Boolean = false
}