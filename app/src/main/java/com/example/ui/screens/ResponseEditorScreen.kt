package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.example.data.model.CapturedResponse
import com.example.ui.components.JsonEditor
import com.example.ui.components.KeyValueEditor
import com.example.ui.components.StatusCodeBadge
import com.example.viewmodel.TrafficViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponseEditorScreen(
    requestId: String,
    viewModel: TrafficViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val selectedRecord by viewModel.selectedRecord.collectAsState()

    LaunchedEffect(requestId) {
        viewModel.selectRecord(requestId)
    }

    val record = selectedRecord
    val originalResponse = record?.response

    var statusCodeText by remember { mutableStateOf("200") }
    var statusMessage by remember { mutableStateOf("OK") }
    var body by remember { mutableStateOf("") }
    val headers = remember { mutableStateListOf<Pair<String, String>>() }

    var isInitialized by remember { mutableStateOf(false) }
    var showConfirmBackDialog by remember { mutableStateOf(false) }

    LaunchedEffect(originalResponse) {
        if (originalResponse != null && !isInitialized) {
            val res = record?.activeResponse ?: originalResponse
            statusCodeText = res.statusCode.toString()
            statusMessage = res.statusMessage ?: "OK"
            body = res.body ?: ""
            headers.clear()
            res.headers.forEach { (k, v) -> headers.add(k to v) }
            isInitialized = true
        }
    }

    val hasUnsavedChanges = remember(statusCodeText, statusMessage, body, headers.size, originalResponse) {
        if (originalResponse == null) false
        else {
            statusCodeText != originalResponse.statusCode.toString() ||
                    statusMessage != (originalResponse.statusMessage ?: "") ||
                    body != (originalResponse.body ?: "") ||
                    headers.toMap() != originalResponse.headers
        }
    }

    BackHandler(enabled = hasUnsavedChanges) {
        showConfirmBackDialog = true
    }

    if (showConfirmBackDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmBackDialog = false },
            title = { Text("Discard Response Changes?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to discard unsaved response modifications?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmBackDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Text("DISCARD")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmBackDialog = false }) {
                    Text("KEEP EDITING")
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    fun buildModifiedResponse(): CapturedResponse? {
        val code = statusCodeText.toIntOrNull()
        if (code == null || code < 100 || code > 599) {
            Toast.makeText(context, "Invalid status code (100-599)", Toast.LENGTH_SHORT).show()
            return null
        }
        val orig = originalResponse ?: return null
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        return orig.copy(
            statusCode = code,
            statusMessage = statusMessage,
            headers = headers.filter { it.first.isNotBlank() }.toMap(),
            body = if (body.isBlank()) null else body,
            sizeBytes = bodyBytes.size.toLong()
        )
    }

    Scaffold(
        containerColor = Color(0xFF020617),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "EDIT RESPONSE #${requestId.uppercase()}",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (hasUnsavedChanges) "Modified (unsaved)" else "Ready to edit",
                            fontSize = 11.sp,
                            color = if (hasUnsavedChanges) Color(0xFFA855F7) else Color(0xFF94A3B8)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (hasUnsavedChanges) showConfirmBackDialog = true else onNavigateBack()
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            originalResponse?.let { orig ->
                                statusCodeText = orig.statusCode.toString()
                                statusMessage = orig.statusMessage ?: "OK"
                                body = orig.body ?: ""
                                headers.clear()
                                orig.headers.forEach { (k, v) -> headers.add(k to v) }
                                Toast.makeText(context, "Reset to original response", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color(0xFF94A3B8))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        bottomBar = {
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
                        onClick = {
                            if (buildModifiedResponse() != null) {
                                Toast.makeText(context, "Response format valid!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("VALIDATE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val modRes = buildModifiedResponse()
                            if (modRes != null) {
                                viewModel.saveAndReturnResponse(requestId, modRes)
                                Toast.makeText(context, "Modified response returned to client!", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("save_and_return_response_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SAVE & RETURN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Status Code & Message Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "STATUS CODE & MESSAGE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF64748B)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = statusCodeText,
                                onValueChange = { statusCodeText = it },
                                label = { Text("Code") },
                                modifier = Modifier
                                    .width(90.dp)
                                    .testTag("status_code_input"),
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34D399)
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF475569),
                                    focusedContainerColor = Color(0xFF0F172A),
                                    unfocusedContainerColor = Color(0xFF0F172A)
                                )
                            )

                            OutlinedTextField(
                                value = statusMessage,
                                onValueChange = { statusMessage = it },
                                label = { Text("Status Message") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("status_message_input"),
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    color = Color.White
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF475569),
                                    focusedContainerColor = Color(0xFF0F172A),
                                    unfocusedContainerColor = Color(0xFF0F172A)
                                )
                            )
                        }
                    }
                }
            }

            // Headers Editor
            item {
                KeyValueEditor(
                    title = "RESPONSE HEADERS",
                    items = headers,
                    onItemChange = { idx, k, v ->
                        headers[idx] = k to v
                    },
                    onItemDelete = { idx ->
                        headers.removeAt(idx)
                    },
                    onItemAdd = {
                        headers.add("" to "")
                    }
                )
            }

            // Body Editor
            item {
                JsonEditor(
                    title = "RESPONSE BODY (JSON / TEXT)",
                    content = body,
                    onContentChange = { body = it },
                    readOnly = false,
                    minLines = 8,
                    maxLines = 18
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}
