package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TrafficState

@Composable
fun TrafficStateBadge(
    state: TrafficState,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (state) {
        TrafficState.WAITING -> Triple(Color(0x33F59E0B), Color(0xFFFBBF24), "WAITING")
        TrafficState.EDITING -> Triple(Color(0x3338BDF8), Color(0xFF38BDF8), "EDITING")
        TrafficState.FORWARDED -> Triple(Color(0x330284C7), Color(0xFF7DD3FC), "FORWARDED")
        TrafficState.MODIFIED -> Triple(Color(0x33A855F7), Color(0xFFC084FC), "MODIFIED")
        TrafficState.BLOCKED -> Triple(Color(0x33EF4444), Color(0xFFF87171), "BLOCKED")
        TrafficState.COMPLETED -> Triple(Color(0x3310B981), Color(0xFF34D399), "COMPLETED")
        TrafficState.ERROR -> Triple(Color(0x33F43F5E), Color(0xFFFB7185), "ERROR")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, textColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun HttpMethodBadge(
    method: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (method.uppercase()) {
        "GET" -> Pair(Color(0x3310B981), Color(0xFF34D399))
        "POST" -> Pair(Color(0x3338BDF8), Color(0xFF38BDF8))
        "PUT" -> Pair(Color(0x33F59E0B), Color(0xFFFBBF24))
        "DELETE" -> Pair(Color(0x33EF4444), Color(0xFFF87171))
        "PATCH" -> Pair(Color(0x33A855F7), Color(0xFFC084FC))
        "HEAD", "OPTIONS" -> Pair(Color(0x3364748B), Color(0xFF94A3B8))
        else -> Pair(Color(0x3364748B), Color(0xFFE2E8F0))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = method.uppercase(),
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun StatusCodeBadge(
    code: Int,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (code) {
        in 200..299 -> Pair(Color(0x3310B981), Color(0xFF34D399))
        in 300..399 -> Pair(Color(0x3306B6D4), Color(0xFF22D3EE))
        in 400..499 -> Pair(Color(0x33F59E0B), Color(0xFFFBBF24))
        in 500..599 -> Pair(Color(0x33EF4444), Color(0xFFF87171))
        else -> Pair(Color(0x3364748B), Color(0xFFCBD5E1))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = code.toString(),
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
