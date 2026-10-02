package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.DevTrafficInspectorApp
import com.example.data.model.ProxySettings
import com.example.service.OverlayService
import com.example.util.OverlayUtils
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel : ViewModel() {

    private val settingsRepo = DevTrafficInspectorApp.instance.settingsRepository

    val settings: StateFlow<ProxySettings> = settingsRepo.settingsFlow

    fun updateHost(host: String) {
        settingsRepo.updateSettings(settings.value.copy(host = host))
    }

    fun updatePort(port: Int) {
        settingsRepo.updateSettings(settings.value.copy(port = port))
    }

    fun setProxyEnabled(enabled: Boolean) {
        settingsRepo.setProxyEnabled(enabled)
    }

    fun setCaptureRequests(enabled: Boolean) {
        settingsRepo.updateSettings(settings.value.copy(captureRequests = enabled))
    }

    fun setCaptureResponses(enabled: Boolean) {
        settingsRepo.updateSettings(settings.value.copy(captureResponses = enabled))
    }

    fun setSaveHistory(enabled: Boolean) {
        settingsRepo.updateSettings(settings.value.copy(saveHistory = enabled))
    }

    fun setMaskSensitiveData(enabled: Boolean) {
        settingsRepo.setMaskSensitiveData(enabled)
    }

    fun setFloatingInspector(enabled: Boolean, context: Context): Boolean {
        if (enabled) {
            if (!OverlayUtils.canDrawOverlays(context)) {
                return false // Permission required
            }
            settingsRepo.setFloatingInspectorEnabled(true)
            OverlayService.start(context)
        } else {
            settingsRepo.setFloatingInspectorEnabled(false)
            OverlayService.stop(context)
        }
        return true
    }

    fun setVerboseLogging(enabled: Boolean) {
        settingsRepo.updateSettings(settings.value.copy(verboseLogging = enabled))
    }

    fun setInterceptRequests(enabled: Boolean) {
        settingsRepo.setInterceptRequests(enabled)
    }

    fun setInterceptResponses(enabled: Boolean) {
        settingsRepo.setInterceptResponses(enabled)
    }

    fun resetSettings() {
        settingsRepo.resetSettings()
    }
}
