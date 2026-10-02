package com.example.ui.screens

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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.navigation.Routes
import com.example.viewmodel.SettingsViewModel
import com.example.viewmodel.TestScenario
import com.example.viewmodel.TrafficMetrics
import com.example.viewmodel.TrafficViewModel

@Composable
fun DashboardScreen(
    trafficViewModel: TrafficViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val isRunning by trafficViewModel.isProxyRunning.collectAsState()
    val isPaused by trafficViewModel.isPaused.collectAsState()
    val metrics by trafficViewModel.metrics.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()

    Scaffold(
        containerColor = Color(0xFF020617)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // App Title & Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DevTraffic Inspector",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Developer HTTP/HTTPS Interception & Debugging",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF38BDF8), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Router,
                            contentDescription = "Inspector Icon",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "PROXY ENGINE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF64748B)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isPaused) Color(0xFFF59E0B)
                                                else if (isRunning) Color(0xFF10B981)
                                                else Color(0xFFEF4444)
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isPaused) "PAUSED" else if (isRunning) "RUNNING" else "STOPPED",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPaused) Color(0xFFFBBF24) else if (isRunning) Color(0xFF34D399) else Color(0xFFF87171)
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "LISTENING ADDRESS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = "${settings.host}:${settings.port}",
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isRunning) {
                                Button(
                                    onClick = { trafficViewModel.startProxy(context) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("start_proxy_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("START", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        if (isPaused) trafficViewModel.resumeProxy(context)
                                        else trafficViewModel.pauseProxy(context)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pause_resume_button"),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (isPaused) Color(0xFF34D399) else Color(0xFFFBBF24)
                                    )
                                ) {
                                    Icon(
                                        if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isPaused) "RESUME" else "PAUSE", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { trafficViewModel.stopProxy(context) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("stop_proxy_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("STOP", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Metrics Section
            item {
                Text(
                    text = "TRAFFIC METRICS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard("Total", metrics.totalRequests.toString(), Color(0xFF38BDF8), Modifier.weight(1f))
                    MetricCard("Waiting", metrics.waitingCount.toString(), Color(0xFFFBBF24), Modifier.weight(1f))
                    MetricCard("Forwarded", metrics.forwardedCount.toString(), Color(0xFF34D399), Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard("Modified", metrics.modifiedCount.toString(), Color(0xFFA855F7), Modifier.weight(1f))
                    MetricCard("Blocked", metrics.blockedCount.toString(), Color(0xFFF87171), Modifier.weight(1f))
                    MetricCard("Errors", metrics.errorCount.toString(), Color(0xFFFB7185), Modifier.weight(1f))
                }
            }

            // Quick Nav Actions
            item {
                Text(
                    text = "NAVIGATION & TOOLS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NavCard(
                            title = "Live Traffic",
                            subtitle = "${metrics.waitingCount} waiting",
                            icon = Icons.Default.Sync,
                            accentColor = Color(0xFF38BDF8),
                            onClick = { onNavigate(Routes.LIVE) },
                            modifier = Modifier.weight(1f)
                        )

                        NavCard(
                            title = "History",
                            subtitle = "Local Room DB",
                            icon = Icons.Default.History,
                            accentColor = Color(0xFFA855F7),
                            onClick = { onNavigate(Routes.HISTORY) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NavCard(
                            title = "Overlay Setup",
                            subtitle = if (settings.floatingInspectorEnabled) "Active" else "Off",
                            icon = Icons.Default.Layers,
                            accentColor = Color(0xFFF59E0B),
                            onClick = { onNavigate(Routes.OVERLAY_SETUP) },
                            modifier = Modifier.weight(1f)
                        )

                        NavCard(
                            title = "Settings",
                            subtitle = "Proxy & Masking",
                            icon = Icons.Default.Settings,
                            accentColor = Color(0xFF10B981),
                            onClick = { onNavigate(Routes.SETTINGS) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Developer Testing Sandbox
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BugReport, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Dev Test Sandbox",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Text(
                                text = "1-TAP SIMULATOR",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Simulate requests directly from your development application to verify interception, request editing, and response editing.",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    trafficViewModel.sendTestRequest(TestScenario.LOGIN_POST)
                                    onNavigate(Routes.LIVE)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_post_login"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                            ) {
                                Text("POST Login", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    trafficViewModel.sendTestRequest(TestScenario.PROFILE_GET)
                                    onNavigate(Routes.LIVE)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_get_profile"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF34D399))
                            ) {
                                Text("GET Profile", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    trafficViewModel.sendTestRequest(TestScenario.UPDATE_PUT)
                                    onNavigate(Routes.LIVE)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFBBF24))
                            ) {
                                Text("PUT Update", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    trafficViewModel.sendTestRequest(TestScenario.DELETE_API)
                                    onNavigate(Routes.LIVE)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171))
                            ) {
                                Text("DELETE Res", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // About Shortcut
            item {
                OutlinedButton(
                    onClick = { onNavigate(Routes.ABOUT) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF64748B))
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("About & Security Guidelines", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
    }
}

@Composable
private fun NavCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = subtitle, fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
        }
    }
}
