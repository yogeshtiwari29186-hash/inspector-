package com.example.ui.screens

import android.widget.Toast
import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.vpn.InspectorVpnService
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.ui.navigation.Routes
import com.example.proxy.CertificateAuthority
import com.example.util.OverlayUtils
import com.example.viewmodel.SettingsViewModel
import com.example.viewmodel.TrafficViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlaySetupScreen(
    settingsViewModel: SettingsViewModel,
    trafficViewModel: TrafficViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(OverlayUtils.canDrawOverlays(context)) }
    var vpnPrepared by remember { mutableStateOf(VpnService.prepare(context) == null) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        vpnPrepared = result.resultCode == Activity.RESULT_OK && VpnService.prepare(context) == null
        if (vpnPrepared) {
            trafficViewModel.startProxy(context)
            settingsViewModel.setFloatingInspector(true, context)
            InspectorVpnService.start(context)
            Toast.makeText(context, "Inspector VPN started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "VPN permission is required for automatic traffic capture", Toast.LENGTH_LONG).show()
        }
    }

    fun startInspection() {
        val intent = InspectorVpnService.prepareIntent(context)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            vpnPrepared = true
            trafficViewModel.startProxy(context)
            settingsViewModel.setFloatingInspector(true, context)
            InspectorVpnService.start(context)
            Toast.makeText(context, "Inspector VPN started", Toast.LENGTH_SHORT).show()
        }
    }

    // Re-check whenever screen resumes from system settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = OverlayUtils.canDrawOverlays(context)
        if (hasPermission) {
            context.getSharedPreferences("devtraffic_setup", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("overlay_setup_completed", true).apply()
            settingsViewModel.setFloatingInspector(true, context)
        }
    }

    Scaffold(
        containerColor = Color(0xFF020617),
        topBar = {
            TopAppBar(
                title = { Text("Floating Inspector", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Hero Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (hasPermission) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (hasPermission) Icons.Default.CheckCircle else Icons.Default.Layers,
                                contentDescription = null,
                                tint = if (hasPermission) Color(0xFF10B981) else Color(0xFFF59E0B),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (hasPermission) "Floating Inspector Enabled" else "Enable Floating Inspector",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "A compact, movable widget that floats above other apps during development testing so you can see live intercepted requests with a badge counter.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 18.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Permission status & Action
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SYSTEM OVERLAY PERMISSION",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )

                            Text(
                                text = if (hasPermission) "GRANTED" else "REQUIRED",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (hasPermission) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (!hasPermission) {
                            Text(
                                text = "Android requires explicit user approval to display floating windows over other apps. Tap below to open official Android system settings.",
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1),
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    OverlayUtils.openOverlaySettings(context)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("allow_overlay_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                            ) {
                                Text("ALLOW OVERLAY", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(
                                text = "Permission is active. The floating bubble will appear whenever the inspector is running.",
                                fontSize = 12.sp,
                                color = Color(0xFF34D399)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = {
                                    context.startActivity(CertificateAuthority.installIntent(context))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("INSTALL HTTPS INSPECTION CA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "For your own/test device only. Android will ask you to explicitly install the local CA. This does not bypass certificate pinning.",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                lineHeight = 15.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        trafficViewModel.startProxy(context)
                                        settingsViewModel.setFloatingInspector(true, context)
                                        Toast.makeText(context, "Inspector started with overlay", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("START NOW", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        settingsViewModel.setFloatingInspector(false, context)
                                        Toast.makeText(context, "Floating overlay disabled", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                                ) {
                                    Text("HIDE OVERLAY", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // How it works
            item {
                Text(
                    text = "HOW FLOATING INSPECTOR WORKS",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GuideStep(1, "Allow the Inspector VPN once. It routes device traffic to the local inspector automatically.")
                    GuideStep(2, "Install the DevTraffic Local CA on your own/test device for HTTPS inspection.")
                    GuideStep(3, "Login normally. The request will pause in the Inspector when interception is enabled.")
                    GuideStep(4, "Edit the request, tap SAVE & FORWARD, then inspect/edit the response before Return.")
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun GuideStep(step: Int, text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2563EB)),
                contentAlignment = Alignment.Center
            ) {
                Text(step.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(text, fontSize = 12.sp, color = Color(0xFFE2E8F0))
        }
    }
}
