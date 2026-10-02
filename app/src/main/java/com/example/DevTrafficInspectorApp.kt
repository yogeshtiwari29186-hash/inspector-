package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.db.AppDatabase
import com.example.data.model.CapturedRequest
import com.example.data.model.CapturedResponse
import com.example.data.model.TrafficState
import com.example.data.repository.SettingsRepository
import com.example.data.repository.TrafficRepository
import com.example.proxy.HttpForwarder
import com.example.proxy.ProxyListener
import com.example.proxy.ProxyServer

class DevTrafficInspectorApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var trafficRepository: TrafficRepository
        private set

    lateinit var proxyServer: ProxyServer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getDatabase(this)
        settingsRepository = SettingsRepository(this)
        trafficRepository = TrafficRepository(database.trafficDao(), settingsRepository)
        proxyServer = ProxyServer(settingsRepository, HttpForwarder())

        proxyServer.setListener(object : ProxyListener {
            override fun onRequestCaptured(request: CapturedRequest) {
                trafficRepository.recordRequest(request)
            }

            override fun onResponseCaptured(response: CapturedResponse) {
                trafficRepository.recordResponse(response.requestId, response)
            }

            override fun onRequestBlocked(request: CapturedRequest) {
                trafficRepository.updateState(request.id, TrafficState.BLOCKED)
            }

            override fun onError(requestId: String, error: String) {
                trafficRepository.updateState(requestId, TrafficState.ERROR, error)
            }
        })

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "devtraffic_inspector_channel"
        const val NOTIFICATION_ID = 1001

        lateinit var instance: DevTrafficInspectorApp
            private set
    }
}
