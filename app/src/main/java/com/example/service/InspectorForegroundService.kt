package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.DevTrafficInspectorApp
import com.example.MainActivity
import com.example.R
import com.example.util.OverlayUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class InspectorForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        val app = DevTrafficInspectorApp.instance
        when (action) {
            ACTION_START -> {
                startForegroundNotification("Network inspector is running")
                if (app.settingsRepository.settingsFlow.value.floatingInspectorEnabled && OverlayUtils.canDrawOverlays(this)) {
                    OverlayService.start(this)
                }
                serviceScope.launch(Dispatchers.IO) { try { app.proxyServer.start() } catch (_: Exception) {} }
                observeTrafficUpdates()
            }
            ACTION_PAUSE -> { app.proxyServer.pause(); updateNotification("Network inspector is PAUSED") }
            ACTION_RESUME -> { app.proxyServer.resume(); updateNotification("Network inspector is running") }
            ACTION_STOP -> {
                serviceScope.launch(Dispatchers.IO) { try { app.proxyServer.stop() } catch (_: Exception) {} }
                OverlayService.stop(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun observeTrafficUpdates() {
        val app = DevTrafficInspectorApp.instance
        serviceScope.launch {
            app.trafficRepository.pendingRequests.collectLatest { pending ->
                val status = if (app.proxyServer.isPaused()) "PAUSED" else "Running"
                val text = if (pending.isNotEmpty()) status + " • " + pending.size + " request(s) waiting" else "Proxy active (" + status + ")"
                updateNotification(text)
            }
        }
    }

    private fun startForegroundNotification(statusText: String) {
        val notification = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(DevTrafficInspectorApp.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(DevTrafficInspectorApp.NOTIFICATION_ID, notification)
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(DevTrafficInspectorApp.NOTIFICATION_ID, buildNotification(statusText))
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val openPendingIntent = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pauseIntent = Intent(this, InspectorForegroundService::class.java).apply {
            action = if (DevTrafficInspectorApp.instance.proxyServer.isPaused()) ACTION_RESUME else ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, InspectorForegroundService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pauseTitle = if (DevTrafficInspectorApp.instance.proxyServer.isPaused()) "RESUME" else "PAUSE"

        return NotificationCompat.Builder(this, DevTrafficInspectorApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("DevTraffic Inspector")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_view, "OPEN", openPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, pauseTitle, pausePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", stopPendingIntent)
            .build()
    }

    override fun onDestroy() { super.onDestroy(); serviceScope.cancel() }

    companion object {
        const val ACTION_START = "com.devtraffic.inspector.action.START"
        const val ACTION_STOP = "com.devtraffic.inspector.action.STOP"
        const val ACTION_PAUSE = "com.devtraffic.inspector.action.PAUSE"
        const val ACTION_RESUME = "com.devtraffic.inspector.action.RESUME"

        fun start(context: Context) {
            val intent = Intent(context, InspectorForegroundService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
        fun stop(context: Context) { context.startService(Intent(context, InspectorForegroundService::class.java).apply { action = ACTION_STOP }) }
        fun pause(context: Context) { context.startService(Intent(context, InspectorForegroundService::class.java).apply { action = ACTION_PAUSE }) }
        fun resume(context: Context) { context.startService(Intent(context, InspectorForegroundService::class.java).apply { action = ACTION_RESUME }) }
    }
}