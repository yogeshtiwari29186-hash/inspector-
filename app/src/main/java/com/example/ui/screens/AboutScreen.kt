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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        containerColor = Color(0xFF020617),
        topBar = {
            TopAppBar(
                title = { Text("About & Security", fontWeight = FontWeight.Bold, color = Color.White) },
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
                // App Branding Card
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
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF38BDF8), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Router,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "DevTraffic Inspector",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = "Version 1.0.0 (Build 2026)",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF38BDF8)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Developer network debugging and request/response inspection tool.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Privacy Statement
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PRIVACY STATEMENT",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "• 100% Local: All captured HTTP/HTTPS traffic is stored strictly on your local device.\n" +
                                    "• Zero Remote Telemetry: No analytics, tracking, or cloud crash reporters.\n" +
                                    "• No Automatic Uploads: Captured requests and responses are never exfiltrated or transmitted to external third parties.\n" +
                                    "• Sensitive Data Protection: Automated masking hides auth tokens, passwords, and private API keys from screen viewing.",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Authorized Use & Security Policy
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AUTHORIZED USE & COMPLIANCE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "This application is designed specifically for software developers and QA testers to inspect, test, and debug their own applications and servers.\n\n" +
                                    "Strict Ethical Engineering Policy:\n" +
                                    "• No third-party app interception\n" +
                                    "• No certificate pinning bypass or security control tampering\n" +
                                    "• No spyware, covert monitoring, or password theft behavior\n" +
                                    "• For HTTPS traffic from your own debug apps, you must configure network_security_config.xml in your debug build to trust user certificates.",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Developer Guide for Own App
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "CONNECTING YOUR DEBUG APP (OkHttp / Retrofit)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFF59E0B)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "In your debug build's OkHttpClient.Builder:\n\n" +
                                    "val proxy = Proxy(Proxy.Type.HTTP,\n" +
                                    "    InetSocketAddress(\"127.0.0.1\", 8080))\n" +
                                    "val client = OkHttpClient.Builder()\n" +
                                    "    .proxy(proxy)\n" +
                                    "    .build()",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF38BDF8),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}
