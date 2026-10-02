package com.example.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import androidx.core.content.getSystemService
import com.example.vpn.net.SessionKey
import com.example.vpn.net.toInetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * 判断某条连接是哪个应用发起的。
 *
 * 靠 [ConnectivityManager.getConnectionOwnerUid] 反查四元组的属主 UID，
 * 该接口 Android 10 才提供，更早的系统上只能显示"未知应用"。
 */
class AppResolver(context: Context) {

    private val connectivityManager = context.applicationContext.getSystemService<ConnectivityManager>()
    private val packageManager = context.applicationContext.packageManager

    /** UID → 包名。查询要跨进程，缓存下来避免每条连接都付一次代价。 */
    private val packagesByUid = ConcurrentHashMap<Int, String>()

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun resolve(protocol: Int, key: SessionKey): String? {
        if (!isSupported) return null
        val manager = connectivityManager ?: return null

        val uid = try {
            manager.getConnectionOwnerUid(
                protocol,
                InetSocketAddress(key.sourceIp.toInetAddress(), key.sourcePort),
                InetSocketAddress(key.destIp.toInetAddress(), key.destPort),
            )
        } catch (e: Exception) {
            // 部分定制系统会在这里抛 SecurityException，降级为未知即可
            return null
        }
        if (uid == Process.INVALID_UID || uid <= 0) return null

        val cached = packagesByUid[uid]
        if (cached != null) return cached.ifEmpty { null }

        val resolved = runCatching { packageManager.getPackagesForUid(uid)?.firstOrNull() }
            .getOrNull()
            .orEmpty()
        packagesByUid[uid] = resolved
        return resolved.ifEmpty { null }
    }
}
