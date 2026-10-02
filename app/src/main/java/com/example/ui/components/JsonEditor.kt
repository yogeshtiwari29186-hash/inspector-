package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.util.JsonUtils

@Composable
fun JsonEditor(
    title: String,
    content: String,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    minLines: Int = 8,
    maxLines: Int = 18
) {
    val context = LocalContext.current
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var isValidJson by remember { mutableStateOf<Boolean?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF1F5F9)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Copy Button
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(title, content)
                        clipboard.setPrimaryClip(clip)
                    },
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("copy_json_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("COPY", fontSize = 10.sp)
                }

                if (!readOnly) {
                    // Paste Button
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!text.isNullOrBlank()) {
                                onContentChange(text)
                            }
                        },
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PASTE", fontSize = 10.sp)
                    }

                    // Format Button
                    OutlinedButton(
                        onClick = {
                            val formatted = JsonUtils.formatJson(content)
                            if (formatted != null) {
                                onContentChange(formatted)
                                isValidJson = true
                                validationMessage = "Valid JSON formatted"
                            } else {
                                isValidJson = false
                                validationMessage = "Cannot format: Invalid JSON"
                            }
                        },
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("format_json_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = "Format", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("FORMAT", fontSize = 10.sp)
                    }

                    // Clear Button
                    OutlinedButton(
                        onClick = {
                            onContentChange("")
                            validationMessage = null
                            isValidJson = null
                        },
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Editor
        OutlinedTextField(
            value = content,
            onValueChange = {
                onContentChange(it)
                if (it.isBlank()) {
                    validationMessage = null
                    isValidJson = null
                } else {
                    val valid = JsonUtils.isValidJson(it)
                    isValidJson = valid
                    validationMessage = if (valid) "Valid JSON" else "Invalid JSON syntax"
                }
            },
            readOnly = readOnly,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("json_editor_field"),
            minLines = minLines,
            maxLines = maxLines,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = Color(0xFFE2E8F0)
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isValidJson == false) Color(0xFFEF4444) else Color(0xFF38BDF8),
                unfocusedBorderColor = if (isValidJson == false) Color(0xFFEF4444) else Color(0xFF475569),
                focusedContainerColor = Color(0xFF0F172A),
                unfocusedContainerColor = Color(0xFF0F172A)
            ),
            placeholder = {
                Text(
                    text = if (readOnly) "No body payload." else "{\n  \"key\": \"value\"\n}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        )

        // Validation status
        validationMessage?.let { msg ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = msg,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = if (isValidJson == true) Color(0xFF10B981) else Color(0xFFEF4444)
            )
        }
    }
}
