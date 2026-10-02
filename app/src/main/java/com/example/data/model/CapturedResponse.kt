package com.example.data.model

data class CapturedResponse(
    val requestId: String,
    val timestamp: Long,
    val statusCode: Int,
    val statusMessage: String?,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val contentType: String? = null,
    val durationMs: Long = 0,
    val sizeBytes: Long = 0
)
