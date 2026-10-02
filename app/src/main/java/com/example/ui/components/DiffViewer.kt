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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CapturedRequest

@Composable
fun RequestDiffViewer(
    original: CapturedRequest,
    modified: CapturedRequest,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA855F7))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "DIFF PREVIEW: ORIGINAL vs MODIFIED",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFA855F7)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Method diff
            DiffRow(
                label = "METHOD",
                originalVal = original.method,
                modifiedVal = modified.method,
                isChanged = original.method != modified.method
            )

            Spacer(modifier = Modifier.height(8.dp))

            // URL diff
            DiffRow(
                label = "URL",
                originalVal = original.url,
                modifiedVal = modified.url,
                isChanged = original.url != modified.url
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Headers diff
            val headersChanged = original.headers != modified.headers
            DiffRow(
                label = "HEADERS",
                originalVal = "${original.headers.size} headers",
                modifiedVal = "${modified.headers.size} headers",
                isChanged = headersChanged
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Body diff
            val bodyChanged = (original.body ?: "") != (modified.body ?: "")
            DiffRow(
                label = "BODY",
                originalVal = original.body ?: "(empty)",
                modifiedVal = modified.body ?: "(empty)",
                isChanged = bodyChanged,
                isMultiline = true
            )
        }
    }
}

@Composable
private fun DiffRow(
    label: String,
    originalVal: String,
    modifiedVal: String,
    isChanged: Boolean,
    isMultiline: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isChanged) Color(0x22A855F7) else Color(0x11000000),
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (isChanged) Color(0xFFA855F7).copy(alpha = 0.5f) else Color(0xFF334155),
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isChanged) Color(0xFFA855F7) else Color(0xFF94A3B8)
            )
            if (isChanged) {
                Text(
                    text = "CHANGED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFA855F7)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "ORIGINAL: $originalVal",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF94A3B8),
            maxLines = if (isMultiline) 6 else 2
        )

        if (isChanged) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "MODIFIED: $modifiedVal",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF38BDF8),
                maxLines = if (isMultiline) 6 else 2
            )
        }
    }
}
