package com.example.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TrafficRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RequestCard(
    record: TrafficRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val req = record.activeRequest
    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("request_card_${record.id}")
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (record.isModified) Color(0xFFA855F7).copy(alpha = 0.5f) else Color(0xFF334155)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Row 1: Time, Method, and Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeStr,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    HttpMethodBadge(method = req.method)
                    if (record.isModified) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Modified",
                            tint = Color(0xFFA855F7),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                TrafficStateBadge(state = record.state)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: URL / Path
            Text(
                text = req.url,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFF1F5F9),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Row 3: Response summary if available
            record.activeResponse?.let { res ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusCodeBadge(code = res.statusCode)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${res.durationMs}ms",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatSize(res.sizeBytes),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "View Details",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
}
