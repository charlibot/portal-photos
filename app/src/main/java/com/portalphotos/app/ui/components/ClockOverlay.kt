package com.portalphotos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portalphotos.app.data.prefs.ClockOverlayMode
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ClockOverlay(
    mode: ClockOverlayMode,
    pixelShiftProtection: Boolean,
    modifier: Modifier = Modifier
) {
    if (mode == ClockOverlayMode.HIDDEN) return

    var currentTimeText by remember { mutableStateOf("") }
    var currentDateText by remember { mutableStateOf("") }
    var shiftOffsetX by remember { mutableStateOf(0.dp) }
    var shiftOffsetY by remember { mutableStateOf(0.dp) }

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }

    // Ticking clock effect & Pixel Shift Protection loop keyed on pixelShiftProtection
    LaunchedEffect(pixelShiftProtection) {
        var tickCount = 0
        while (true) {
            val now = Date()
            currentTimeText = timeFormat.format(now)
            currentDateText = dateFormat.format(now)

            if (pixelShiftProtection && tickCount % 300 == 0) { // Every 5 mins
                shiftOffsetX = ((-4..4).random()).dp
                shiftOffsetY = ((-4..4).random()).dp
            } else if (!pixelShiftProtection) {
                shiftOffsetX = 0.dp
                shiftOffsetY = 0.dp
            }

            tickCount++
            delay(1000L)
        }
    }

    val boxAlignment = when (mode) {
        ClockOverlayMode.TOP_RIGHT -> Alignment.TopEnd
        ClockOverlayMode.BOTTOM_LEFT -> Alignment.BottomStart
        ClockOverlayMode.BAR -> Alignment.BottomCenter
        ClockOverlayMode.HIDDEN -> Alignment.TopEnd
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Box(
            modifier = Modifier
                .align(boxAlignment)
                .offset(x = shiftOffsetX, y = shiftOffsetY)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Column {
                Text(
                    text = currentTimeText,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = currentDateText,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
