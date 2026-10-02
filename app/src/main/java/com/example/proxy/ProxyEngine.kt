package com.example.proxy

import com.example.data.model.CapturedRequest
import com.example.data.model.CapturedResponse

interface ProxyEngine {
    suspend fun start()
    suspend fun stop()
    fun isRunning(): Boolean
    fun isPaused(): Boolean
    fun pause()
    fun resume()
    fun setListener(listener: ProxyListener)
    fun forwardRequest(requestId: String, request: CapturedRequest)
    fun blockRequest(requestId: String)
    fun returnResponse(requestId: String, response: CapturedResponse)
    fun blockResponse(requestId: String)
}
