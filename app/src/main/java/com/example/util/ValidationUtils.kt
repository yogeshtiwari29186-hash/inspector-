package com.example.util

import java.net.URI

object ValidationUtils {

    private val VALID_METHODS = setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")

    fun isValidMethod(method: String): Boolean {
        return VALID_METHODS.contains(method.trim().uppercase())
    }

    fun isValidUrl(urlString: String): Boolean {
        if (urlString.isBlank()) return false
        return try {
            val uri = URI(urlString.trim())
            val scheme = uri.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    fun validateHeaderKey(key: String): Boolean {
        return key.isNotBlank() && !key.contains(":") && !key.contains("\r") && !key.contains("\n")
    }
}
