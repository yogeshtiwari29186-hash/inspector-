package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.DevTrafficInspectorApp
import com.example.R
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

/**
 * User-consented local VPN router. TUN traffic is sent to the existing local HTTP
 * inspection proxy, so Wi-Fi proxy settings do not need to be changed manually.
 */
class InspectorVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tun: android.os.ParcelFileDescriptor? = null
    private var engine: TunnelEngine? = null

    private val protector = object : com.example.vpn.proxy.SocketProtector {
        override fun protect(socket: java.net.Socket): Boolean = this@InspectorVpnService.protect(socket)
        override fun protect(socket: java.net.DatagramSocket): Boolean = this@InspectorVpnService.protect(socket)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification())
        startTunnel()
        return START_STICKY
    }

    private fun startTunnel() {
        if (engine != null) return
        scope.launch {
            try {
                val app = DevTrafficInspectorApp.instance
                if (!app.proxyServer.isRunning()) app.proxyServer.start()

                val proxyAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
                val profile = ProxyProfile(
                    id = "devtraffic-local",
                    name = "DevTraffic Local Inspector",
                    type = ProxyType.HTTP,
                    host = "127.0.0.1",
                    port = 8080
                )

                val builder = Builder()
                    .setSession("DevTraffic Inspector")
                    .setMtu(1500)
                    .addAddress("10.215.173.1", 24)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")
                    .setBlocking(true)

                // Keep the inspector's own sockets outside the tunnel to avoid a routing loop.
                runCatching { builder.addDisallowedApplication(packageName) }

                tun = builder.establish() ?: error("Android did not establish the VPN interface")

                VpnStateHolder.onConnecting(profile.id, profile.name)
                engine = TunnelEngine(
                    tunInterface = tun!!,
                    profile = profile,
                    proxyAddress = proxyAddress,
                    mtu = 1500,
                    protector = protector,
                    appResolver = AppResolver(this@InspectorVpnService),
                    dnsBlocker = null
                ).also { it.start() }
                VpnStateHolder.onConnected(android.os.SystemClock.elapsedRealtime())
            } catch (t: Throwable) {
                VpnStateHolder.onError(t.message ?: "VPN start failed")
                stopTunnel()
            }
        }
    }

    private fun stopTunnel() {
        engine?.stop()
        engine = null
        runCatching { tun?.close() }
        tun = null
        VpnStateHolder.onDisconnected()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        stopTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopTunnel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "DevTraffic VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(): Notification {
        ensureNotificationChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("DevTraffic Inspector")
            .setContentText("VPN routing + request inspection active")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "devtraffic_vpn"
        private const val NOTIFICATION_ID = 22081
        const val ACTION_START = "com.example.devtraffic.vpn.START"
        const val ACTION_STOP = "com.example.devtraffic.vpn.STOP"

        fun start(context: Context) {
            val intent = Intent(context, InspectorVpnService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, InspectorVpnService::class.java).setAction(ACTION_STOP))
        }

        fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)
    }
}