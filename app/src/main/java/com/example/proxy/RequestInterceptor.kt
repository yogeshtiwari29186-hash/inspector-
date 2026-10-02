package com.example.proxy

import com.example.data.model.CapturedRequest
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

sealed class RequestDecision {
    data class Forward(val request: CapturedRequest) : RequestDecision()
    object Block : RequestDecision()
}

class RequestInterceptor {
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<RequestDecision>>()

    fun register(requestId: String): CompletableDeferred<RequestDecision> {
        val deferred = CompletableDeferred<RequestDecision>()
        pendingRequests[requestId] = deferred
        return deferred
    }

    fun forward(requestId: String, request: CapturedRequest): Boolean {
        val deferred = pendingRequests.remove(requestId) ?: return false
        return deferred.complete(RequestDecision.Forward(request))
    }

    fun block(requestId: String): Boolean {
        val deferred = pendingRequests.remove(requestId) ?: return false
        return deferred.complete(RequestDecision.Block)
    }

    fun cancelAll() {
        val iterator = pendingRequests.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.value.complete(RequestDecision.Block)
            iterator.remove()
        }
    }

    fun isPending(requestId: String): Boolean = pendingRequests.containsKey(requestId)
}
