package com.example.proxy

import com.example.data.model.CapturedResponse
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

sealed class ResponseDecision {
    data class Return(val response: CapturedResponse) : ResponseDecision()
    object Block : ResponseDecision()
}

class ResponseInterceptor {
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<ResponseDecision>>()

    fun register(requestId: String): CompletableDeferred<ResponseDecision> {
        val deferred = CompletableDeferred<ResponseDecision>()
        pendingResponses[requestId] = deferred
        return deferred
    }

    fun returnResponse(requestId: String, response: CapturedResponse): Boolean {
        val deferred = pendingResponses.remove(requestId) ?: return false
        return deferred.complete(ResponseDecision.Return(response))
    }

    fun block(requestId: String): Boolean {
        val deferred = pendingResponses.remove(requestId) ?: return false
        return deferred.complete(ResponseDecision.Block)
    }

    fun cancelAll() {
        val iterator = pendingResponses.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.value.complete(ResponseDecision.Block)
            iterator.remove()
        }
    }

    fun isPending(requestId: String): Boolean = pendingResponses.containsKey(requestId)
}
