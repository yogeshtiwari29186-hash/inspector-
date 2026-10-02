package com.example.data.model

data class ProxySettings(
    val enableProxy: Boolean = false,
    val host: String = "127.0.0.1",
    val port: Int = 8080,
    val captureRequests: Boolean = true,
    val captureResponses: Boolean = true,
    val saveHistory: Boolean = true,
    val maskSensitiveData: Boolean = true,
    val floatingInspectorEnabled: Boolean = false,
    val verboseLogging: Boolean = false,
    val interceptRequests: Boolean = true,
    val interceptResponses: Boolean = false
)
