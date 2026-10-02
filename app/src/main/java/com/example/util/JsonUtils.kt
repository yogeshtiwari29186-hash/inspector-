package com.example.util

import com.example.data.model.CapturedRequest
import com.example.data.model.CapturedResponse
import com.example.data.model.TrafficState
import org.json.JSONArray
import org.json.JSONObject

object JsonUtils {

    fun formatJson(raw: String?): String? {
        if (raw.isNullOrBlank()) return raw
        return try {
            val trimmed = raw.trim()
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed).toString(2)
            } else if (trimmed.startsWith("[")) {
                JSONArray(trimmed).toString(2)
            } else {
                raw
            }
        } catch (_: Exception) {
            raw
        }
    }

    fun isValidJson(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        return try {
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed)
                true
            } else if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun requestToJson(req: CapturedRequest): String {
        val obj = JSONObject()
        obj.put("id", req.id)
        obj.put("timestamp", req.timestamp)
        obj.put("method", req.method)
        obj.put("url", req.url)
        obj.put("scheme", req.scheme)
        obj.put("host", req.host)
        obj.put("port", req.port)
        obj.put("path", req.path)
        obj.put("body", req.body ?: JSONObject.NULL)
        obj.put("contentType", req.contentType ?: JSONObject.NULL)
        obj.put("contentLength", req.contentLength ?: -1L)
        obj.put("protocol", req.protocol)
        obj.put("state", req.state.name)

        val headersObj = JSONObject()
        req.headers.forEach { (k, v) -> headersObj.put(k, v) }
        obj.put("headers", headersObj)

        val paramsObj = JSONObject()
        req.queryParameters.forEach { (k, v) -> paramsObj.put(k, v) }
        obj.put("queryParameters", paramsObj)

        return obj.toString()
    }

    fun jsonToRequest(jsonStr: String): CapturedRequest {
        val obj = JSONObject(jsonStr)
        val headers = mutableMapOf<String, String>()
        if (obj.has("headers")) {
            val hObj = obj.getJSONObject("headers")
            val keys = hObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                headers[k] = hObj.getString(k)
            }
        }
        val params = mutableMapOf<String, String>()
        if (obj.has("queryParameters")) {
            val pObj = obj.getJSONObject("queryParameters")
            val keys = pObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                params[k] = pObj.getString(k)
            }
        }
        val stateStr = obj.optString("state", TrafficState.WAITING.name)
        val state = try {
            TrafficState.valueOf(stateStr)
        } catch (_: Exception) {
            TrafficState.WAITING
        }

        return CapturedRequest(
            id = obj.getString("id"),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            method = obj.getString("method"),
            url = obj.getString("url"),
            scheme = obj.optString("scheme", "http"),
            host = obj.optString("host", ""),
            port = obj.optInt("port", 80),
            path = obj.optString("path", "/"),
            queryParameters = params,
            headers = headers,
            body = if (obj.isNull("body")) null else obj.getString("body"),
            contentType = if (obj.isNull("contentType")) null else obj.getString("contentType"),
            contentLength = if (obj.optLong("contentLength", -1L) >= 0) obj.getLong("contentLength") else null,
            protocol = obj.optString("protocol", "HTTP/1.1"),
            state = state
        )
    }

    fun responseToJson(res: CapturedResponse): String {
        val obj = JSONObject()
        obj.put("requestId", res.requestId)
        obj.put("timestamp", res.timestamp)
        obj.put("statusCode", res.statusCode)
        obj.put("statusMessage", res.statusMessage ?: JSONObject.NULL)
        obj.put("body", res.body ?: JSONObject.NULL)
        obj.put("contentType", res.contentType ?: JSONObject.NULL)
        obj.put("durationMs", res.durationMs)
        obj.put("sizeBytes", res.sizeBytes)

        val headersObj = JSONObject()
        res.headers.forEach { (k, v) -> headersObj.put(k, v) }
        obj.put("headers", headersObj)

        return obj.toString()
    }

    fun jsonToResponse(jsonStr: String): CapturedResponse {
        val obj = JSONObject(jsonStr)
        val headers = mutableMapOf<String, String>()
        if (obj.has("headers")) {
            val hObj = obj.getJSONObject("headers")
            val keys = hObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                headers[k] = hObj.getString(k)
            }
        }

        return CapturedResponse(
            requestId = obj.getString("requestId"),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            statusCode = obj.getInt("statusCode"),
            statusMessage = if (obj.isNull("statusMessage")) null else obj.getString("statusMessage"),
            headers = headers,
            body = if (obj.isNull("body")) null else obj.getString("body"),
            contentType = if (obj.isNull("contentType")) null else obj.getString("contentType"),
            durationMs = obj.optLong("durationMs", 0),
            sizeBytes = obj.optLong("sizeBytes", 0)
        )
    }
}
