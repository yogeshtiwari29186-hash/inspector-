package com.example.proxy

import com.example.data.model.CapturedRequest
import com.example.data.model.CapturedResponse

interface ProxyListener {
    fun onRequestCaptured(request: CapturedRequest)
    fun onResponseCaptured(response: CapturedResponse)
    fun onRequestBlocked(request: CapturedRequest)
    fun onError(requestId: String, error: String)
}
