package com.example.proxy

import com.example.data.model.CapturedRequest
import com.example.data.model.CapturedResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpForwarder(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
) {

    @Throws(IOException::class)
    suspend fun forward(capturedRequest: CapturedRequest): CapturedResponse {
        val startTime = System.currentTimeMillis()
        val builder = Request.Builder()

        // Set URL
        builder.url(capturedRequest.url)

        // Set Headers (filter out connection-specific headers)
        capturedRequest.headers.forEach { (key, value) ->
            if (!isHopByHopHeader(key)) {
                try {
                    builder.addHeader(key, value)
                } catch (_: Exception) {
                    // Skip invalid header characters
                }
            }
        }

        // Set Method and Body
        val method = capturedRequest.method.uppercase()
        if (methodRequiresBody(method)) {
            val contentType = capturedRequest.contentType ?: "application/json"
            val mediaType = contentType.toMediaTypeOrNull()
            val bodyBytes = capturedRequest.body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
            builder.method(method, bodyBytes.toRequestBody(mediaType))
        } else if (methodPermitsBody(method) && capturedRequest.body != null) {
            val contentType = capturedRequest.contentType ?: "application/json"
            val mediaType = contentType.toMediaTypeOrNull()
            val bodyBytes = capturedRequest.body.toByteArray(Charsets.UTF_8)
            builder.method(method, bodyBytes.toRequestBody(mediaType))
        } else {
            builder.method(method, null)
        }

        val okHttpRequest = builder.build()
        val call = client.newCall(okHttpRequest)
        val okHttpResponse = call.execute()

        val duration = System.currentTimeMillis() - startTime
        val responseBodyBytes = okHttpResponse.body?.bytes() ?: ByteArray(0)
        val responseBodyString = if (responseBodyBytes.isNotEmpty()) {
            String(responseBodyBytes, Charsets.UTF_8)
        } else {
            null
        }

        val resHeaders = mutableMapOf<String, String>()
        for (i in 0 until okHttpResponse.headers.size) {
            val name = okHttpResponse.headers.name(i)
            val value = okHttpResponse.headers.value(i)
            resHeaders[name] = value
        }

        val resContentType = okHttpResponse.header("Content-Type")

        return CapturedResponse(
            requestId = capturedRequest.id,
            timestamp = System.currentTimeMillis(),
            statusCode = okHttpResponse.code,
            statusMessage = okHttpResponse.message,
            headers = resHeaders,
            body = responseBodyString,
            contentType = resContentType,
            durationMs = duration,
            sizeBytes = responseBodyBytes.size.toLong()
        )
    }

    private fun isHopByHopHeader(headerName: String): Boolean {
        return when (headerName.lowercase()) {
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade" -> true
            else -> false
        }
    }

    private fun methodRequiresBody(method: String): Boolean {
        return method == "POST" || method == "PUT" || method == "PATCH"
    }

    private fun methodPermitsBody(method: String): Boolean {
        return method != "GET" && method != "HEAD"
    }
}
