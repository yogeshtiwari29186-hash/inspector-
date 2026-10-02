package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.DevTrafficInspectorApp
import com.example.MainActivity
import com.example.data.model.TrafficRecord
import com.example.ui.theme.DevTrafficTheme
import kotlin.math.roundToInt

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val mViewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = mViewModelStore

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var isPanelOpen by mutableStateOf(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(Bundle())
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupOverlay()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlay() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val prefs = getSharedPreferences("devtraffic_overlay", Context.MODE_PRIVATE)
        val initialX = prefs.getInt("pos_x", 40)
        val initialY = prefs.getInt("pos_y", 200)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)

            setContent {
                DevTrafficTheme(darkTheme = true) {
                    val app = DevTrafficInspectorApp.instance
                    val pendingRequests by app.trafficRepository.pendingRequests.collectAsState(initial = emptyList())
                    val isRunning = app.proxyServer.isRunning()
                    val isPaused = app.proxyServer.isPaused()

                    OverlayContent(
                        pendingRequests = pendingRequests,
                        isRunning = isRunning,
                        isPaused = isPaused,
                        isPanelExpanded = isPanelOpen,
                        onTogglePanel = { isPanelOpen = !isPanelOpen },
                        onOpenInspector = { openMainActivity(null) },
                        onRequestClick = { record -> openMainActivity(record.id) },
                        onPauseToggle = {
                            if (isPaused) app.proxyServer.resume() else app.proxyServer.pause()
                        },
                        onClosePanel = { isPanelOpen = false }
                    )
                }
            }
        }

        // Attach dragging listener to the root compose view
        var initialTouchX = 0f
        var initialTouchY = 0f
        var startX = 0
        var startY = 0
        var isDragging = false

        composeView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    startX = layoutParams.x
                    startY = layoutParams.y
                    isDragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).roundToInt()
                    val dy = (event.rawY - initialTouchY).roundToInt()
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        isDragging = true
                        layoutParams.x = startX + dx
                        layoutParams.y = startY + dy
                        try {
                            windowManager?.updateViewLayout(composeView, layoutParams)
                        } catch (_: Exception) {}
                    }
                    isDragging
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        prefs.edit()
                            .putInt("pos_x", layoutParams.x)
                            .putInt("pos_y", layoutParams.y)
                            .apply()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }

        overlayView = composeView
        try {
            windowManager?.addView(composeView, layoutParams)
        } catch (_: Exception) {}
    }

    private fun openMainActivity(requestId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (requestId != null) {
                putExtra("TARGET_REQUEST_ID", requestId)
            }
        }
        startActivity(intent)
        isPanelOpen = false
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        mViewModelStore.clear()

        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.stopService(intent)
        }
    }
}

@Composable
private fun OverlayContent(
    pendingRequests: List<TrafficRecord>,
    isRunning: Boolean,
    isPaused: Boolean,
    isPanelExpanded: Boolean,
    onTogglePanel: () -> Unit,
    onOpenInspector: () -> Unit,
    onRequestClick: (TrafficRecord) -> Unit,
    onPauseToggle: () -> Unit,
    onClosePanel: () -> Unit
) {
    Column(horizontalAlignment = Alignment.Start) {
        // Floating Bubble
        Box(
            modifier = Modifier
                .padding(8.dp)
                .size(56.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xFF0F172A))
                .border(2.dp, if (pendingRequests.isNotEmpty()) Color(0xFFF59E0B) else Color(0xFF38BDF8), CircleShape)
                .clickable { onTogglePanel() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Router,
                contentDescription = "DevTraffic Inspector",
                tint = if (pendingRequests.isNotEmpty()) Color(0xFFF59E0B) else Color(0xFF38BDF8),
                modifier = Modifier.size(28.dp)
            )

            // Numeric badge if pending requests exist
            if (pendingRequests.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (pendingRequests.size > 99) "99+" else pendingRequests.size.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Expanded Floating Panel
        AnimatedVisibility(
            visible = isPanelExpanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .padding(8.dp)
                    .widthIn(min = 280.dp, max = 340.dp)
                    .shadow(12.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "DevTraffic Inspector",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isPaused) Color(0xFFF59E0B) else if (isRunning) Color(0xFF10B981) else Color(0xFF64748B))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPaused) "Paused" else if (isRunning) "Running" else "Stopped",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                        IconButton(onClick = onClosePanel, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Pending requests: ${pendingRequests.size}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (pendingRequests.isNotEmpty()) Color(0xFFFBBF24) else Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (pendingRequests.isEmpty()) {
                        Text(
                            text = "No pending intercepted traffic.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            pendingRequests.take(4).forEach { rec ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onRequestClick(rec) },
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = rec.request.method,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = Color(0xFF38BDF8)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = rec.request.path,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = Color(0xFFE2E8F0),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (pendingRequests.size > 4) {
                                Text(
                                    text = "+${pendingRequests.size - 4} more waiting...",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons
                    Button(
                        onClick = onOpenInspector,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("OPEN INSPECTOR", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPauseToggle,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPaused) "RESUME" else "PAUSE", color = Color.White, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onClosePanel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CLOSE", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
