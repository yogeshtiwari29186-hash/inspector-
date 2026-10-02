package com.example.vpn.net

/** DNS 查询的问题段。 */
data class DnsQuestion(val name: String, val type: Int) {
    val typeName: String
        get() = when (type) {
            TYPE_A -> "A"
            TYPE_AAAA -> "AAAA"
            TYPE_CNAME -> "CNAME"
            TYPE_HTTPS -> "HTTPS"
            TYPE_TXT -> "TXT"
            TYPE_PTR -> "PTR"
            else -> "TYPE$type"
        }

    companion object {
        const val TYPE_A = 1
        const val TYPE_CNAME = 5
        const val TYPE_PTR = 12
        const val TYPE_TXT = 16
        const val TYPE_AAAA = 28
        const val TYPE_HTTPS = 65
    }
}

/**
 * 极简 DNS 报文读取器。
 *
 * 只取两样东西：查询里问的域名，以及应答里的 A 记录。
 * 后者用来建立 IP → 域名的反查表，好让连接日志显示域名而不是一串裸 IP。
 */
object DnsMessage {

    private const val HEADER_SIZE = 12
    private const val MAX_POINTER_JUMPS = 16

    /** 读取第一个 Question；不是合法查询时返回 null。 */
    fun readQuestion(data: ByteArray, offset: Int, length: Int): DnsQuestion? {
        if (length < HEADER_SIZE + 5) return null
        val end = offset + length
        val questionCount = data.u16(offset + 4)
        if (questionCount < 1) return null

        val (name, afterName) = readName(data, offset + HEADER_SIZE, offset, end) ?: return null
        if (afterName + 4 > end) return null
        return DnsQuestion(name, data.u16(afterName))
    }

    /** 问题段的结束位置（QCLASS 之后）；伪造应答时在此截断并续写 Answer。 */
    internal fun questionEnd(data: ByteArray, offset: Int, length: Int): Int? {
        if (length < HEADER_SIZE + 5) return null
        val end = offset + length
        if (data.u16(offset + 4) < 1) return null
        val (_, afterName) = readName(data, offset + HEADER_SIZE, offset, end) ?: return null
        val afterQuestion = afterName + 4 // QTYPE + QCLASS
        return if (afterQuestion <= end) afterQuestion else null
    }

    /** 读取应答里的全部 A 记录，返回 域名 → IPv4 的配对。 */
    fun readAnswers(data: ByteArray, offset: Int, length: Int): List<Pair<String, Int>> {
        if (length < HEADER_SIZE) return emptyList()
        val end = offset + length
        val questionCount = data.u16(offset + 4)
        val answerCount = data.u16(offset + 6)
        if (answerCount < 1) return emptyList()

        var cursor = offset + HEADER_SIZE
        repeat(questionCount) {
            val (_, next) = readName(data, cursor, offset, end) ?: return emptyList()
            cursor = next + 4 // QTYPE + QCLASS
            if (cursor > end) return emptyList()
        }

        val results = mutableListOf<Pair<String, Int>>()
        repeat(answerCount) {
            val (name, afterName) = readName(data, cursor, offset, end) ?: return results
            if (afterName + 10 > end) return results
            val type = data.u16(afterName)
            val dataLength = data.u16(afterName + 8)
            val recordStart = afterName + 10
            if (recordStart + dataLength > end) return results
            if (type == DnsQuestion.TYPE_A && dataLength == 4) {
                results += name to data.ipv4(recordStart)
            }
            cursor = recordStart + dataLength
        }
        return results
    }

    /**
     * 读取一个可能被压缩的域名。
     *
     * @return 域名与"名字之后的位置"；遇到压缩指针时，后者指向指针本身之后而不是跳转目标。
     */
    private fun readName(
        data: ByteArray,
        start: Int,
        messageStart: Int,
        end: Int,
    ): Pair<String, Int>? {
        val builder = StringBuilder()
        var cursor = start
        var afterName = -1
        var jumps = 0

        while (cursor < end) {
            val labelLength = data.u8(cursor)
            when {
                labelLength == 0 -> {
                    if (afterName < 0) afterName = cursor + 1
                    return builder.toString() to afterName
                }
                // 高两位为 11 表示这是一个指向报文别处的压缩指针
                (labelLength and 0xC0) == 0xC0 -> {
                    if (cursor + 1 >= end) return null
                    if (++jumps > MAX_POINTER_JUMPS) return null // 防御环形指针
                    if (afterName < 0) afterName = cursor + 2
                    cursor = messageStart + (((labelLength and 0x3F) shl 8) or data.u8(cursor + 1))
                    if (cursor < messageStart || cursor >= end) return null
                }

                else -> {
                    val labelStart = cursor + 1
                    if (labelStart + labelLength > end) return null
                    if (builder.isNotEmpty()) builder.append('.')
                    builder.append(String(data, labelStart, labelLength, Charsets.US_ASCII))
                    cursor = labelStart + labelLength
                }
            }
        }
        return null
    }
}
