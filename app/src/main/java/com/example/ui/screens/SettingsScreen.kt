package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.navigation.Routes
import com.example.util.OverlayUtils
import com.example.viewmodel.SettingsViewModel
import com.example.viewmodel.TrafficViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    trafficViewModel: TrafficViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToOverlaySetup: () -> Unit
) {
    val context = LocalContext.current
    val settings by settingsViewModel.settings.collectAsState()

    var hostText by remember(settings.host) { mutableStateOf(settings.host) }
    var portText by remember(settings.port) { mutableStateOf(settings.port.toString()) }

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear All Traffic History?", fontWeight = FontWeight.Bold) },
            text = { Text("This will delete all captured requests from Room database storage.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        trafficViewModel.clearAllHistory()
                        showClearHistoryDialog = false
                        Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Text("CLEAR")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("CANCEL")
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Settings to Default?", fontWeight = FontWeight.Bold) },
            text = { Text("This will restore default proxy host (127.0.0.1), port (8080), and interception options.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsViewModel.resetSettings()
                        showResetDialog = false
                        Toast.makeText(context, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Text("RESET")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("CANCEL")
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    Scaffold(
        containerColor = Color(0xFF020617),
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = Color.White) },
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
                // PROXY CONFIGURATION
                SectionHeader("PROXY CONFIGURATION")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = hostText,
                                onValueChange = {
                                    hostText = it
                                    settingsViewModel.updateHost(it)
                                },
                                label = { Text("Host Address") },
                                modifier = Modifier
                                    .weight(1.5f)
                                    .testTag("proxy_host_input"),
                                singleLine = true,
                                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedContainerColor = Color(0xFF1E293B),
                                    unfocusedContainerColor = Color(0xFF1E293B)
                                )
                            )

                            OutlinedTextField(
                                value = portText,
                                onValueChange = {
                                    portText = it
                                    it.toIntOrNull()?.let { p -> settingsViewModel.updatePort(p) }
                                },
                                label = { Text("Port") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("proxy_port_input"),
                                singleLine = true,
                                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedContainerColor = Color(0xFF1E293B),
                                    unfocusedContainerColor = Color(0xFF1E293B)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "For Android Emulator, 10.0.2.2 normally refers to the host machine. For physical devices, use the appropriate development machine address reachable from the device (e.g. 192.168.x.x).",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // INTERCEPTION BEHAVIOR
            item {
                SectionHeader("INTERCEPTION & PAUSE BEHAVIOR")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        ToggleRow(
                            title = "Intercept Outgoing Requests",
                            subtitle = "Pause incoming requests in WAITING state for inspection, edit, or block before forwarding.",
                            checked = settings.interceptRequests,
                            onCheckedChange = { settingsViewModel.setInterceptRequests(it) },
                            testTag = "toggle_intercept_requests"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ToggleRow(
                            title = "Intercept Incoming Responses",
                            subtitle = "Pause server responses before returning them to client application to allow response modification.",
                            checked = settings.interceptResponses,
                            onCheckedChange = { settingsViewModel.setInterceptResponses(it) },
                            testTag = "toggle_intercept_responses"
                        )
                    }
                }
            }

            // TRAFFIC & HISTORY
            item {
                SectionHeader("TRAFFIC & STORAGE")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        ToggleRow(
                            title = "Capture Requests",
                            subtitle = "Record HTTP method, URL, headers, and request body.",
                            checked = settings.captureRequests,
                            onCheckedChange = { settingsViewModel.setCaptureRequests(it) },
                            testTag = "toggle_capture_requests"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ToggleRow(
                            title = "Capture Responses",
                            subtitle = "Record response status code, duration, size, headers, and response payload.",
                            checked = settings.captureResponses,
                            onCheckedChange = { settingsViewModel.setCaptureResponses(it) },
                            testTag = "toggle_capture_responses"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ToggleRow(
                            title = "Save History Locally (Room DB)",
                            subtitle = "Keep captured traffic records in device storage between app launches.",
                            checked = settings.saveHistory,
                            onCheckedChange = { settingsViewModel.setSaveHistory(it) },
                            testTag = "toggle_save_history"
                        )
                    }
                }
            }

            // SENSITIVE DATA
            item {
                SectionHeader("PRIVACY & SENSITIVE DATA")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        ToggleRow(
                            title = "Mask Sensitive Data",
                            subtitle = "Automatically obscure passwords, tokens, API keys, and authorization headers in UI displays.",
                            checked = settings.maskSensitiveData,
                            onCheckedChange = { settingsViewModel.setMaskSensitiveData(it) },
                            testTag = "toggle_mask_sensitive"
                        )
                    }
                }
            }

            // OVERLAY SETTINGS
            item {
                SectionHeader("FLOATING INSPECTOR OVERLAY")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val hasOverlayPermission = OverlayUtils.canDrawOverlays(context)
                        ToggleRow(
                            title = "Enable Floating Inspector",
                            subtitle = if (hasOverlayPermission)
                                "Compact draggable bubble that appears above other apps with pending request count badge."
                            else
                                "Requires Android Overlay Permission. Tap to configure.",
                            checked = settings.floatingInspectorEnabled && hasOverlayPermission,
                            onCheckedChange = { enable ->
                                if (enable && !hasOverlayPermission) {
                                    onNavigateToOverlaySetup()
                                } else {
                                    settingsViewModel.setFloatingInspector(enable, context)
                                }
                            },
                            testTag = "toggle_floating_inspector"
                        )

                        if (!hasOverlayPermission) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onNavigateToOverlaySetup,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                            ) {
                                Text("SETUP OVERLAY PERMISSION", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ACTIONS
            item {
                SectionHeader("ACTIONS & RESET")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            trafficViewModel.clearLiveTraffic()
                            Toast.makeText(context, "Live traffic cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_live_traffic_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFBBF24))
                    ) {
                        Text("CLEAR LIVE TRAFFIC")
                    }

                    OutlinedButton(
                        onClick = { showClearHistoryDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_history_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                    ) {
                        Text("CLEAR ALL HISTORY (ROOM DB)")
                    }

                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                    ) {
                        Text("RESET SETTINGS TO DEFAULT")
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = Color(0xFF64748B),
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF94A3B8), lineHeight = 16.sp)
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF2563EB),
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF334155)
            )
        )
    }
}
