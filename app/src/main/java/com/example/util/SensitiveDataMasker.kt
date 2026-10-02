package com.example.util

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object SensitiveDataMasker {

    private val SENSITIVE_KEYS = setOf(
        "password",
        "passwd",
        "pass",
        "token",
        "access_token",
        "refresh_token",
        "id_token",
        "authorization",
        "api_key",
        "apikey",
        "secret",
        "client_secret",
        "private_key",
        "auth"
    )

    fun isSensitiveKey(key: String): Boolean {
        val lower = key.lowercase(Locale.ROOT).replace("-", "_")
        return SENSITIVE_KEYS.any { lower.contains(it) }
    }

    fun maskHeaders(headers: Map<String, String>, enabled: Boolean): Map<String, String> {
        if (!enabled) return headers
        return headers.mapValues { (key, value) ->
            if (isSensitiveKey(key)) "••••••••" else value
        }
    }

    fun maskBody(body: String?, contentType: String?, enabled: Boolean): String? {
        if (!enabled || body.isNullOrBlank()) return body
        if (contentType?.contains("json", ignoreCase = true) == true || (body.trim().startsWith("{") && body.trim().endsWith("}"))) {
            return try {
                val json = JSONObject(body)
                maskJsonObject(json).toString(2)
            } catch (_: Exception) {
                // Not standard JSON or array
                try {
                    val array = JSONArray(body)
                    maskJsonArray(array).toString(2)
                } catch (_: Exception) {
                    body
                }
            }
        }
        return body
    }

    private fun maskJsonObject(obj: JSONObject): JSONObject {
        val masked = JSONObject()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.get(key)
            if (isSensitiveKey(key)) {
                masked.put(key, "••••••••")
            } else if (value is JSONObject) {
                masked.put(key, maskJsonObject(value))
            } else if (value is JSONArray) {
                masked.put(key, maskJsonArray(value))
            } else {
                masked.put(key, value)
            }
        }
        return masked
    }

    private fun maskJsonArray(array: JSONArray): JSONArray {
        val masked = JSONArray()
        for (i in 0 until array.length()) {
            val item = array.get(i)
            if (item is JSONObject) {
                masked.put(maskJsonObject(item))
            } else if (item is JSONArray) {
                masked.put(maskJsonArray(item))
            } else {
                masked.put(item)
            }
        }
        return masked
    }
}
