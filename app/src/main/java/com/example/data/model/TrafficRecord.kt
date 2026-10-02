package com.example.data.model

data class TrafficRecord(
    val request: CapturedRequest,
    val response: CapturedResponse? = null,
    val modifiedRequest: CapturedRequest? = null,
    val modifiedResponse: CapturedResponse? = null,
    val state: TrafficState = TrafficState.WAITING,
    val errorMessage: String? = null
) {
    val id: String get() = request.id
    val timestamp: Long get() = request.timestamp
    val activeRequest: CapturedRequest get() = modifiedRequest ?: request
    val activeResponse: CapturedResponse? get() = modifiedResponse ?: response
    val isModified: Boolean get() = modifiedRequest != null || modifiedResponse != null
}
