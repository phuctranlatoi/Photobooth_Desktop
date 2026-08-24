package com.phuctran.photobooth.desktop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.domain.SessionState
import com.phuctran.photobooth.desktop.ui.theme.*
import java.nio.file.Path

@Composable
fun AppShell(
    state: SessionState,
    content: @Composable BoxScope.() -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(NeutralBg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TopBar(state)
        
        // ProgressStepper is removed to simplify UI
        
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun TopBar(state: SessionState) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(NeutralPanel).border(1.dp, NeutralBorder, RoundedCornerShape(12.dp)).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(AccentNude), contentAlignment = Alignment.Center) {
                Text("P", color = Color.White, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Black)
            }
            Column {
                Text("PHOTOBOOTH KIOSK", color = NeutralText, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Black)
                Text("Session: ${state.name}", color = NeutralMuted, style = MaterialTheme.typography.caption)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            InfoPill("Cloud Album ON", bgColor = AccentNudeLight, textColor = AccentNude)
            InfoPill("Printer READY", bgColor = Color(0xFFE8F5E9), textColor = Color(0xFF4CAF50))
        }
    }
}

@Composable
fun ProgressStepper(currentState: SessionState) {
    val steps = listOf(
        "STUDIO_MODE" to "Chọn layout",
        "QUANTITY" to "Số lượng",
        "PAYMENT" to "Thanh toán",
        "PREPARE" to "Chuẩn bị",
        "CAPTURING" to "Chụp ảnh",
        "SELECTION" to "Chọn ảnh",
        "FRAME_CHOICE" to "Chọn khung",
        "CONFIRM" to "Xác nhận",
        "PROCESSING" to "Đang xử lý",
        "COMPLETE" to "Hoàn tất"
    )
    
    val currentIndex = steps.indexOfFirst { it.first == currentState.name }.coerceAtLeast(0)

    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(NeutralPanel).border(1.dp, NeutralBorder, RoundedCornerShape(12.dp)).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, pair ->
            val isActive = index == currentIndex
            val isPassed = index < currentIndex
            val color = if (isActive) AccentNude else if (isPassed) NeutralText else NeutralMuted
            val weight = if (isActive) FontWeight.Bold else FontWeight.Normal
            
            Text(pair.second, color = color, fontWeight = weight, style = MaterialTheme.typography.caption)
            if (index < steps.size - 1) {
                Box(Modifier.width(20.dp).height(1.dp).background(NeutralBorder))
            }
        }
    }
}

@Composable
fun StatusBar(status: String, outputPath: Path?) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)).background(NeutralBg).border(1.dp, NeutralBorder, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(status, color = NeutralText, style = MaterialTheme.typography.body2, fontWeight = FontWeight.Bold)
        Text(outputPath?.toString() ?: "No output path", color = NeutralMuted, style = MaterialTheme.typography.caption)
    }
}
