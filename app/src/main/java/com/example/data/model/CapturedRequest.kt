package com.example.data.model

data class CapturedRequest(
    val id: String,
    val timestamp: Long,
    val method: String,
    val url: String,
    val scheme: String = "http",
    val host: String = "",
    val port: Int = 80,
    val path: String = "/",
    val queryParameters: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val contentType: String? = null,
    val contentLength: Long? = null,
    val protocol: String = "HTTP/1.1",
    val state: TrafficState = TrafficState.WAITING
)
