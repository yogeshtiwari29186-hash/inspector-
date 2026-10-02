package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ProxySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("devtraffic_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<ProxySettings> = _settings.asStateFlow()

    private fun loadSettings(): ProxySettings {
        return ProxySettings(
            enableProxy = prefs.getBoolean("enable_proxy", false),
            host = prefs.getString("host", "127.0.0.1") ?: "127.0.0.1",
            port = prefs.getInt("port", 8080),
            captureRequests = prefs.getBoolean("capture_requests", true),
            captureResponses = prefs.getBoolean("capture_responses", true),
            saveHistory = prefs.getBoolean("save_history", true),
            maskSensitiveData = prefs.getBoolean("mask_sensitive_data", true),
            floatingInspectorEnabled = prefs.getBoolean("floating_inspector", false),
            verboseLogging = prefs.getBoolean("verbose_logging", false),
            interceptRequests = prefs.getBoolean("intercept_requests", true),
            interceptResponses = prefs.getBoolean("intercept_responses", false)
        )
    }

    fun updateSettings(newSettings: ProxySettings) {
        prefs.edit()
            .putBoolean("enable_proxy", newSettings.enableProxy)
            .putString("host", newSettings.host)
            .putInt("port", newSettings.port)
            .putBoolean("capture_requests", newSettings.captureRequests)
            .putBoolean("capture_responses", newSettings.captureResponses)
            .putBoolean("save_history", newSettings.saveHistory)
            .putBoolean("mask_sensitive_data", newSettings.maskSensitiveData)
            .putBoolean("floating_inspector", newSettings.floatingInspectorEnabled)
            .putBoolean("verbose_logging", newSettings.verboseLogging)
            .putBoolean("intercept_requests", newSettings.interceptRequests)
            .putBoolean("intercept_responses", newSettings.interceptResponses)
            .apply()
        _settings.value = newSettings
    }

    fun setProxyEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(enableProxy = enabled))
    }

    fun setHostAndPort(host: String, port: Int) {
        updateSettings(_settings.value.copy(host = host, port = port))
    }

    fun setFloatingInspectorEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(floatingInspectorEnabled = enabled))
    }

    fun setMaskSensitiveData(enabled: Boolean) {
        updateSettings(_settings.value.copy(maskSensitiveData = enabled))
    }

    fun setInterceptRequests(enabled: Boolean) {
        updateSettings(_settings.value.copy(interceptRequests = enabled))
    }

    fun setInterceptResponses(enabled: Boolean) {
        updateSettings(_settings.value.copy(interceptResponses = enabled))
    }

    fun resetSettings() {
        val default = ProxySettings()
        updateSettings(default)
    }
}
