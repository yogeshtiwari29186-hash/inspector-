package com.example.vpn

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class VpnStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

data class VpnState(
    val status: VpnStatus = VpnStatus.DISCONNECTED,
    val profileId: String? = null,
    val profileName: String? = null,
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    /** 最近一个采样周期内的瞬时速率，单位字节每秒。 */
    val uploadBytesPerSecond: Long = 0L,
    val downloadBytesPerSecond: Long = 0L,
    val activeSessions: Int = 0,
    /** [android.os.SystemClock.elapsedRealtime] 时间基准，0 表示未连接。 */
    val connectedSince: Long = 0L,
    val errorMessage: String? = null,
) {
    val isActive: Boolean get() = status == VpnStatus.CONNECTING || status == VpnStatus.CONNECTED
}

/**
 * 服务与界面之间共享的运行时状态。
 *
 * VpnService 与 UI 同进程，因此一个内存单例就够了 —— 不必走 Binder 或广播。
 */
object VpnStateHolder {

    private val _state = MutableStateFlow(VpnState())
    val state: StateFlow<VpnState> = _state.asStateFlow()

    /** 由转发线程高频写入，因此用原子计数器而不是每包都去改 StateFlow。 */
    val uploadCounter = AtomicLong(0)
    val downloadCounter = AtomicLong(0)
    val sessionCounter = AtomicInteger(0)

    // 速率采样的上一帧
    private var lastSampleAt = 0L
    private var lastUpload = 0L
    private var lastDownload = 0L

    fun onConnecting(profileId: String, profileName: String) {
        uploadCounter.set(0)
        downloadCounter.set(0)
        sessionCounter.set(0)
        lastSampleAt = 0L
        lastUpload = 0L
        lastDownload = 0L
        _state.value = VpnState(
            status = VpnStatus.CONNECTING,
            profileId = profileId,
            profileName = profileName,
        )
    }

    fun onConnected(connectedSince: Long) {
        _state.update { it.copy(status = VpnStatus.CONNECTED, connectedSince = connectedSince) }
    }

    fun onError(message: String) {
        _state.update {
            it.copy(status = VpnStatus.ERROR, errorMessage = message, connectedSince = 0L)
        }
    }

    fun onDisconnected() {
        _state.value = VpnState()
    }

    /** 由服务里的定时器周期性调用，把原子计数器同步到界面状态并折算瞬时速率。 */
    fun publishCounters() {
        val now = SystemClock.elapsedRealtime()
        val upload = uploadCounter.get()
        val download = downloadCounter.get()
        val elapsed = now - lastSampleAt

        // 第一次采样没有基准，速率按 0 处理，避免把累计值当成瞬时值
        val uploadRate = if (lastSampleAt > 0 && elapsed > 0) {
            (upload - lastUpload) * 1000 / elapsed
        } else {
            0L
        }
        val downloadRate = if (lastSampleAt > 0 && elapsed > 0) {
            (download - lastDownload) * 1000 / elapsed
        } else {
            0L
        }
        lastSampleAt = now
        lastUpload = upload
        lastDownload = download

        _state.update {
            if (!it.isActive) {
                it
            } else {
                it.copy(
                    uploadBytes = upload,
                    downloadBytes = download,
                    uploadBytesPerSecond = uploadRate.coerceAtLeast(0),
                    downloadBytesPerSecond = downloadRate.coerceAtLeast(0),
                    activeSessions = sessionCounter.get(),
                )
            }
        }
    }
}
