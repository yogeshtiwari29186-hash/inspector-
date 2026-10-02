package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ResponseCard
import com.example.ui.navigation.Routes
import com.example.util.JsonUtils
import com.example.viewmodel.TrafficViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponseDetailsScreen(
    requestId: String,
    viewModel: TrafficViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (String) -> Unit
) {
    val context = LocalContext.current
    val selectedRecord by viewModel.selectedRecord.collectAsState()

    LaunchedEffect(requestId) {
        viewModel.selectRecord(requestId)
    }

    val record = selectedRecord
    val response = record?.activeResponse

    Scaffold(
        containerColor = Color(0xFF020617),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "RESPONSE #${requestId.uppercase()}",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        record?.let {
                            Text(it.request.url, fontSize = 11.sp, color = Color(0xFF94A3B8), maxLines = 1)
                        }
                    }
                },
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
        },
        bottomBar = {
            if (record != null && response != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF1E293B))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onNavigateToEditor(requestId) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("edit_response_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA855F7))
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("EDIT RESPONSE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.returnOriginalResponse(record)
                                Toast.makeText(context, "Response returned to client app", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("forward_response_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RETURN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.blockResponse(requestId)
                                Toast.makeText(context, "Response blocked", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("block_response_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("BLOCK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (response == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No response captured yet for this request.", color = Color(0xFF94A3B8))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    ResponseCard(
                        response = response,
                        isModified = record.modifiedResponse != null
                    )
                }

                // Headers
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RESPONSE HEADERS (${response.headers.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF64748B)
                                )
                                IconButton(
                                    onClick = {
                                        val headersText = response.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                                        copyToClipboard(context, "Headers", headersText)
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy Headers",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            response.headers.forEach { (key, value) ->
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        text = key,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Text(
                                        text = value,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                            }
                        }
                    }
                }

                // Response Body
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RESPONSE BODY",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF64748B)
                                )

                                if (!response.body.isNullOrBlank()) {
                                    IconButton(
                                        onClick = {
                                            copyToClipboard(context, "Response Body", response.body)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy Body",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (response.body.isNullOrBlank()) {
                                Text(
                                    text = "No body payload in response.",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF64748B)
                                )
                            } else {
                                val formatted = JsonUtils.formatJson(response.body) ?: response.body
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF020617), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = formatted,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = Color(0xFFE2E8F0)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}
