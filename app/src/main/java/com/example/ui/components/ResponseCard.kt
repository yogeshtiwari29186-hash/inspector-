package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CapturedResponse
import java.util.Locale

@Composable
fun ResponseCard(
    response: CapturedResponse,
    isModified: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (isModified) Color(0xFFA855F7).copy(alpha = 0.6f) else Color(0xFF334155),
                RoundedCornerShape(12.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusCodeBadge(code = response.statusCode)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = response.statusMessage ?: "HTTP Status",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            if (isModified) {
                Text(
                    text = "RESPONSE MODIFIED",
                    color = Color(0xFFA855F7),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text("Duration", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Text(
                    "${response.durationMs} ms",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF38BDF8)
                )
            }
            Column {
                Text("Size", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Text(
                    formatBytes(response.sizeBytes),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF38BDF8)
                )
            }
            response.contentType?.let { ct ->
                Column {
                    Text("Type", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Text(
                        ct.substringBefore(";"),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
}
