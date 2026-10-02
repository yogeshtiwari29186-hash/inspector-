package com.example.vpn.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogKind {
    SERVICE,
    DNS,
    CONNECT,
}

/** 决定日志行的底色，与参考实现保持一致的语义。 */
enum class LogLevel {
    NEUTRAL,
    SUCCESS,
    NOTICE,
    FAILURE,
}

data class LogEntry(
    val id: Long,
    val timestampMillis: Long,
    val kind: LogKind,
    val title: String,
    val status: String? = null,
    val packageName: String? = null,
    /** 这条记录涉及的域名（可加入 DNS 拦截）；纯 IP 或系统行没有。 */
    val domain: String? = null,
    val level: LogLevel = LogLevel.NEUTRAL,
)

/**
 * 隧道活动日志。
 *
 * 定量环形缓冲，写满即丢最旧的一条 —— 日志只用于观察当下发生了什么，
 * 不需要也不应该无限增长。
 */
object TunnelLog {

    private const val CAPACITY = 600

    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>()
    private var nextId = 1L

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    /** 暂停后新日志直接丢弃，缓冲里的历史保留。 */
    fun setPaused(value: Boolean) {
        _paused.value = value
    }

    fun service(title: String, level: LogLevel = LogLevel.NOTICE) {
        append(LogKind.SERVICE, title, null, null, null, level)
    }

    fun dns(query: String, packageName: String?, domain: String?) {
        append(LogKind.DNS, query, null, packageName, domain, LogLevel.NEUTRAL)
    }

    fun connect(
        target: String,
        packageName: String?,
        status: String?,
        level: LogLevel,
        domain: String? = null,
    ) {
        append(LogKind.CONNECT, target, status, packageName, domain, level)
    }

    fun clear() {
        synchronized(lock) { buffer.clear() }
        _entries.value = emptyList()
    }

    /** 导出为 CSV，所有字段统一加引号并转义内部引号。 */
    fun toCsv(entries: List<LogEntry>): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val sb = StringBuilder("timestamp,kind,package,title,status,level\n")
        entries.forEach { entry ->
            sb.appendCsvField(formatter.format(Date(entry.timestampMillis)))
            sb.appendCsvField(entry.kind.name)
            sb.appendCsvField(entry.packageName ?: "")
            sb.appendCsvField(entry.title)
            sb.appendCsvField(entry.status ?: "")
            sb.append(csvField(entry.level.name)).append('\n')
        }
        return sb.toString()
    }

    private fun StringBuilder.appendCsvField(value: String): StringBuilder =
        append(csvField(value)).append(',')

    private fun csvField(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""

    private fun append(
        kind: LogKind,
        title: String,
        status: String?,
        packageName: String?,
        domain: String?,
        level: LogLevel,
    ) {
        if (_paused.value) return
        val snapshot = synchronized(lock) {
            buffer.addLast(
                LogEntry(
                    id = nextId++,
                    timestampMillis = System.currentTimeMillis(),
                    kind = kind,
                    title = title,
                    status = status,
                    packageName = packageName,
                    domain = domain,
                    level = level,
                ),
            )
            while (buffer.size > CAPACITY) buffer.removeFirst()
            buffer.toList()
        }
        _entries.value = snapshot
    }
}
