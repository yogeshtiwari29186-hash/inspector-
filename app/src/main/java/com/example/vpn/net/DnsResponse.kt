package com.example.vpn.net

/** 伪造 DNS 应答：拦截命中的查询不再转发，直接在隧道内作答。 */
object DnsResponse {

    private const val HEADER_SIZE = 12
    private const val ANSWER_TTL = 60

    /** 应答 NAME 用压缩指针指回问题段开头（偏移 12）。 */
    private const val NAME_POINTER_HI = 0xC0
    private const val NAME_POINTER_LO = 0x0C

    private const val FLAG_QR = 0x8000
    private const val FLAG_RD = 0x0100
    private const val FLAG_RA = 0x0080

    /**
     * 用 [ip] 给 [query] 造一个 NOERROR 应答。
     *
     * A 查询回一条指向 [ip] 的 A 记录；其余类型（AAAA、HTTPS 等）回空应答，
     * 让查询方立即得到"没有记录"，而不是等超时或改走别的解析通道漏出去。
     *
     * @return 报文不合法时返回 null，调用方应回退到正常转发。
     */
    fun buildBlockedResponse(query: ByteArray, queryLen: Int, ip: String): ByteArray? {
        if (queryLen < HEADER_SIZE || queryLen > query.size) return null
        val question = DnsMessage.readQuestion(query, 0, queryLen) ?: return null
        val questionEnd = DnsMessage.questionEnd(query, 0, queryLen) ?: return null
        val address = parseIpv4(ip) ?: return null

        val isA = question.type == DnsQuestion.TYPE_A
        val answerSize = if (isA) 16 else 0 // NAME(2) TYPE CLASS TTL RDLENGTH(各2) RDATA(4)
        // 只保留头 + 问题段：查询可能带 EDNS 等附加记录，直接续写 Answer 会把它们挤出原位
        val response = query.copyOf(questionEnd + answerSize)

        // 标志位：QR=1、OPCODE 与 RD 沿用查询、RA=1、RCODE=0
        val flags = FLAG_QR or (query.u16(2) and (0x7800 or FLAG_RD)) or FLAG_RA
        response.putU16(2, flags)
        response.putU16(4, 1) // QDCOUNT
        response.putU16(6, if (isA) 1 else 0) // ANCOUNT
        response.putU16(8, 0) // NSCOUNT
        response.putU16(10, 0) // ARCOUNT

        if (isA) {
            var cursor = questionEnd
            response.putU8(cursor, NAME_POINTER_HI)
            response.putU8(cursor + 1, NAME_POINTER_LO)
            cursor += 2
            response.putU16(cursor, DnsQuestion.TYPE_A)
            response.putU16(cursor + 2, 1) // CLASS IN
            response.putU32(cursor + 4, ANSWER_TTL.toLong())
            response.putU16(cursor + 8, 4)
            response.putIpv4(cursor + 10, address)
        }
        return response
    }

    /** 解析点分四段 IPv4；任何一段非法都视为不可用。 */
    private fun parseIpv4(ip: String): Int? {
        val parts = ip.trim().split('.')
        if (parts.size != 4) return null
        var address = 0
        parts.forEach { part ->
            val octet = part.toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            address = (address shl 8) or octet
        }
        return address
    }
}
