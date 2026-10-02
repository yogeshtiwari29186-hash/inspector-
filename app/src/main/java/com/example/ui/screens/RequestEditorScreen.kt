package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.example.data.model.CapturedRequest
import com.example.data.model.TrafficState
import com.example.ui.components.HttpMethodBadge
import com.example.ui.components.JsonEditor
import com.example.ui.components.KeyValueEditor
import com.example.ui.components.RequestDiffViewer
import com.example.util.ValidationUtils
import com.example.viewmodel.TrafficViewModel
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestEditorScreen(
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
    val originalRequest = record?.request

    // Editor States
    var method by remember { mutableStateOf("GET") }
    var url by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val headers = remember { mutableStateListOf<Pair<String, String>>() }
    val queryParams = remember { mutableStateListOf<Pair<String, String>>() }

    var isInitialized by remember { mutableStateOf(false) }
    var showDiffPreview by remember { mutableStateOf(false) }
    var showConfirmBackDialog by remember { mutableStateOf(false) }
    var methodDropdownExpanded by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // Initialize from active request
    LaunchedEffect(record) {
        if (record != null && !isInitialized) {
            val reqToEdit = record.activeRequest
            method = reqToEdit.method
            url = reqToEdit.url
            body = reqToEdit.body ?: ""
            headers.clear()
            reqToEdit.headers.forEach { (k, v) -> headers.add(k to v) }
            queryParams.clear()
            reqToEdit.queryParameters.forEach { (k, v) -> queryParams.add(k to v) }
            isInitialized = true
        }
    }

    // Check if dirty
    val hasUnsavedChanges = remember(method, url, body, headers.size, queryParams.size, originalRequest) {
        if (originalRequest == null) false
        else {
            method != originalRequest.method ||
                    url != originalRequest.url ||
                    body != (originalRequest.body ?: "") ||
                    headers.toMap() != originalRequest.headers ||
                    queryParams.toMap() != originalRequest.queryParameters
        }
    }

    BackHandler(enabled = hasUnsavedChanges) {
        showConfirmBackDialog = true
    }

    if (showConfirmBackDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmBackDialog = false },
            title = { Text("Discard Changes?", fontWeight = FontWeight.Bold) },
            text = { Text("You have unsaved edits to this request. Are you sure you want to discard them?") },
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

    fun buildModifiedRequest(): CapturedRequest? {
        if (!ValidationUtils.isValidMethod(method)) {
            validationError = "Invalid HTTP Method: $method"
            return null
        }
        if (!ValidationUtils.isValidUrl(url)) {
            validationError = "Invalid URL format: Must start with http:// or https://"
            return null
        }
        validationError = null

        val uri = try { URI(url) } catch (_: Exception) { null }
        val headersMap = headers.filter { it.first.isNotBlank() }.toMap()
        val queryParamsMap = queryParams.filter { it.first.isNotBlank() }.toMap()

        val orig = originalRequest ?: return null
        return orig.copy(
            method = method.uppercase(),
            url = url,
            scheme = uri?.scheme ?: "http",
            host = uri?.host ?: orig.host,
            port = if (uri?.port != null && uri.port != -1) uri.port else orig.port,
            path = uri?.path ?: orig.path,
            headers = headersMap,
            queryParameters = queryParamsMap,
            body = if (body.isBlank()) null else body,
            contentLength = if (body.isNotBlank()) body.toByteArray(Charsets.UTF_8).size.toLong() else null,
            state = TrafficState.MODIFIED
        )
    }

    Scaffold(
        containerColor = Color(0xFF020617),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "EDIT REQUEST #${requestId.uppercase()}",
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
                    // Reset to original
                    IconButton(
                        onClick = {
                            originalRequest?.let { orig ->
                                method = orig.method
                                url = orig.url
                                body = orig.body ?: ""
                                headers.clear()
                                orig.headers.forEach { (k, v) -> headers.add(k to v) }
                                queryParams.clear()
                                orig.queryParameters.forEach { (k, v) -> queryParams.add(k to v) }
                                Toast.makeText(context, "Reset to original request", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("reset_request_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset to Original", tint = Color(0xFF94A3B8))
                    }

                    // Diff Preview Toggle
                    IconButton(
                        onClick = { showDiffPreview = !showDiffPreview },
                        modifier = Modifier.testTag("toggle_diff_preview")
                    ) {
                        Icon(
                            Icons.Default.Difference,
                            contentDescription = "Preview Diff",
                            tint = if (showDiffPreview) Color(0xFFA855F7) else Color(0xFF94A3B8)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        bottomBar = {
            // Action bar: CANCEL, VALIDATE, SAVE, SAVE & FORWARD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF1E293B))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    validationError?.let { err ->
                        Text(
                            text = err,
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Validate button
                        OutlinedButton(
                            onClick = {
                                val req = buildModifiedRequest()
                                if (req != null) {
                                    Toast.makeText(context, "Request validated successfully!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("VALIDATE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Save Changes
                        OutlinedButton(
                            onClick = {
                                val modReq = buildModifiedRequest()
                                if (modReq != null) {
                                    viewModel.saveAndForwardRequest(requestId, modReq)
                                    Toast.makeText(context, "Modifications saved!", Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_changes_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA855F7))
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SAVE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Save & Forward
                        Button(
                            onClick = {
                                val modReq = buildModifiedRequest()
                                if (modReq != null) {
                                    viewModel.saveAndForwardRequest(requestId, modReq)
                                    Toast.makeText(context, "Saved and forwarded to server!", Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                }
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("save_and_forward_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SAVE & FORWARD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
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
                // Diff Preview if toggled
                if (showDiffPreview && originalRequest != null) {
                    val currentMod = buildModifiedRequest()
                    if (currentMod != null) {
                        RequestDiffViewer(original = originalRequest, modified = currentMod)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }

            // HTTP Method & URL Editor
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "METHOD & TARGET URL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF64748B)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Method selector
                            Box {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFF475569), RoundedCornerShape(8.dp))
                                        .clickable { methodDropdownExpanded = true }
                                        .padding(horizontal = 12.dp, vertical = 14.dp)
                                        .testTag("method_selector")
                                ) {
                                    HttpMethodBadge(method = method)
                                }

                                DropdownMenu(
                                    expanded = methodDropdownExpanded,
                                    onDismissRequest = { methodDropdownExpanded = false },
                                    modifier = Modifier.background(Color(0xFF1E293B))
                                ) {
                                    listOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS").forEach { m ->
                                        DropdownMenuItem(
                                            text = { HttpMethodBadge(method = m) },
                                            onClick = {
                                                method = m
                                                methodDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // URL input
                            OutlinedTextField(
                                value = url,
                                onValueChange = { url = it },
                                placeholder = { Text("https://example.com/api/...") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("request_url_input"),
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
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

            // Query Parameters Editor
            item {
                KeyValueEditor(
                    title = "QUERY PARAMETERS",
                    items = queryParams,
                    onItemChange = { idx, k, v ->
                        queryParams[idx] = k to v
                    },
                    onItemDelete = { idx ->
                        queryParams.removeAt(idx)
                    },
                    onItemAdd = {
                        queryParams.add("" to "")
                    }
                )
            }

            // Headers Editor
            item {
                KeyValueEditor(
                    title = "REQUEST HEADERS",
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
                    title = "REQUEST BODY (JSON / TEXT)",
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
