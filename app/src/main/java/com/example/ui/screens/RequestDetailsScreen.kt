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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TrafficState
import com.example.ui.components.HttpMethodBadge
import com.example.ui.components.ResponseCard
import com.example.ui.components.TrafficStateBadge
import com.example.ui.navigation.Routes
import com.example.util.JsonUtils
import com.example.util.SensitiveDataMasker
import com.example.viewmodel.TrafficViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestDetailsScreen(
    requestId: String,
    viewModel: TrafficViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (String) -> Unit,
    onNavigateToResponseDetails: (String) -> Unit
) {
    val context = LocalContext.current
    val selectedRecord by viewModel.selectedRecord.collectAsState()
    var showMaskedSecrets by remember { mutableStateOf(false) }

    LaunchedEffect(requestId) {
        viewModel.selectRecord(requestId)
    }

    val record = selectedRecord

    Scaffold(
        containerColor = Color(0xFF020617),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "REQUEST #${requestId.uppercase()}",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        record?.let {
                            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.timestamp))
                            Text(timeStr, fontSize = 11.sp, color = Color(0xFF94A3B8))
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
                actions = {
                    // Mask/Unmask secrets toggle
                    IconButton(
                        onClick = { showMaskedSecrets = !showMaskedSecrets },
                        modifier = Modifier.testTag("toggle_mask_secrets")
                    ) {
                        Icon(
                            imageVector = if (showMaskedSecrets) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showMaskedSecrets) "Mask Secrets" else "Show Secrets",
                            tint = if (showMaskedSecrets) Color(0xFFF59E0B) else Color(0xFF94A3B8)
                        )
                    }

                    // Delete record
                    IconButton(
                        onClick = {
                            viewModel.deleteRecord(requestId)
                            onNavigateBack()
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        bottomBar = {
            if (record != null) {
                val isPending = record.state == TrafficState.WAITING || record.state == TrafficState.EDITING
                SurfaceBottomBar(
                    isPending = isPending,
                    onForward = {
                        viewModel.forwardOriginalRequest(record)
                        Toast.makeText(context, "Request forwarded", Toast.LENGTH_SHORT).show()
                    },
                    onEdit = {
                        onNavigateToEditor(record.id)
                    },
                    onBlock = {
                        viewModel.blockRequest(record.id)
                        Toast.makeText(context, "Request blocked", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    ) { paddingValues ->
        if (record == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading request details...", color = Color(0xFF94A3B8))
            }
        } else {
            val req = record.activeRequest
            val headersToDisplay = SensitiveDataMasker.maskHeaders(req.headers, !showMaskedSecrets)
            val bodyToDisplay = SensitiveDataMasker.maskBody(req.body, req.contentType, !showMaskedSecrets)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    // Overview Card
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    HttpMethodBadge(method = req.method)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = req.scheme.uppercase(),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                TrafficStateBadge(state = record.state)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "TARGET URL",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = req.url,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        copyToClipboard(context, "URL", req.url)
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy URL",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Query Parameters
                if (req.queryParameters.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "QUERY PARAMETERS (${req.queryParameters.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                req.queryParameters.forEach { (key, value) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(key, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF94A3B8))
                                        Text(value, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
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
                                    text = "REQUEST HEADERS (${headersToDisplay.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF64748B)
                                )
                                IconButton(
                                    onClick = {
                                        val headersText = headersToDisplay.entries.joinToString("\n") { "${it.key}: ${it.value}" }
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

                            if (headersToDisplay.isEmpty()) {
                                Text("No headers", fontSize = 12.sp, color = Color(0xFF64748B))
                            } else {
                                headersToDisplay.forEach { (key, value) ->
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
                }

                // Request Body
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
                                Column {
                                    Text(
                                        text = "REQUEST BODY",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF64748B)
                                    )
                                    req.contentType?.let {
                                        Text(it, fontSize = 11.sp, color = Color(0xFF94A3B8))
                                    }
                                }

                                if (!bodyToDisplay.isNullOrBlank()) {
                                    IconButton(
                                        onClick = {
                                            copyToClipboard(context, "Body", bodyToDisplay)
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

                            if (bodyToDisplay.isNullOrBlank()) {
                                Text(
                                    text = "No body payload.",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF64748B)
                                )
                            } else {
                                val formattedBody = JsonUtils.formatJson(bodyToDisplay) ?: bodyToDisplay
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF020617), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = formattedBody,
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

                // Response Section if response exists
                record.activeResponse?.let { res ->
                    item {
                        Text(
                            text = "CAPTURED RESPONSE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ResponseCard(response = res, isModified = record.modifiedResponse != null)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onNavigateToResponseDetails(record.id) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                        ) {
                            Text("VIEW RESPONSE DETAILS", fontWeight = FontWeight.Bold)
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

@Composable
private fun SurfaceBottomBar(
    isPending: Boolean,
    onForward: () -> Unit,
    onEdit: () -> Unit,
    onBlock: () -> Unit
) {
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
            // Edit Request Button (Always available)
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier
                    .weight(1f)
                    .testTag("edit_request_button"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA855F7))
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("EDIT REQUEST", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            if (isPending) {
                // Forward Button
                Button(
                    onClick = onForward,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("forward_request_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("FORWARD", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Block Button
                Button(
                    onClick = onBlock,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("block_request_button"),
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

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}
