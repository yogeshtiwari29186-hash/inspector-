package com.example.ui.navigation

object Routes {
    const val DASHBOARD = "dashboard"
    const val LIVE = "live"
    const val REQUEST_DETAILS = "request/{id}"
    const val REQUEST_EDITOR = "requestEditor/{id}"
    const val RESPONSE_DETAILS = "response/{id}"
    const val RESPONSE_EDITOR = "responseEditor/{id}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val OVERLAY_SETUP = "overlaySetup"
    const val ABOUT = "about"

    fun requestDetails(id: String) = "request/$id"
    fun requestEditor(id: String) = "requestEditor/$id"
    fun responseDetails(id: String) = "response/$id"
    fun responseEditor(id: String) = "responseEditor/$id"
}
