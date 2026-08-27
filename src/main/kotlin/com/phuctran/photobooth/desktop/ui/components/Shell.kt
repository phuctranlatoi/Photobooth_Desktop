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
import androidx.compose.ui.unit.sp
import java.nio.file.Path

@Composable
fun AppShell(
    state: SessionState,
    albumEnabled: Boolean = false,
    printerEnabled: Boolean = false,
    paymentConfigured: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val isFullscreen = state == SessionState.LIVE_VIEW || state == SessionState.COUNTDOWN || state == SessionState.CAPTURING

    if (isFullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            content()
        }
    } else {
        Box(
            Modifier.fillMaxSize().background(NeutralBg).padding(24.dp)
        ) {
            content()
        }
    }
}

@Composable
fun TopBar(
    state: SessionState,
    albumEnabled: Boolean,
    printerEnabled: Boolean,
    paymentConfigured: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                "Le Souvenir", 
                color = AccentNudeDark, 
                style = MaterialTheme.typography.h4, 
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Text(
                stepLabel(state),
                color = NeutralSecondary,
                style = MaterialTheme.typography.subtitle2,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun stepLabel(state: SessionState): String = when (state) {
    SessionState.IDLE -> "Sẵn sàng đón khách"
    SessionState.SELECTING -> "Chọn bố cục và màu ảnh"
    SessionState.SELECTING_QUANTITY -> "Chọn số bản in"
    SessionState.PAYMENT_PENDING -> "Thanh toán"
    SessionState.PREPARING -> "Chuẩn bị tạo dáng"
    SessionState.LIVE_VIEW -> "Live view"
    SessionState.COUNTDOWN -> "Đếm ngược"
    SessionState.CAPTURING -> "Đang chụp"
    SessionState.SELECTING_PHOTOS -> "Chọn ảnh in"
    SessionState.EDITING -> "Chọn khung"
    SessionState.COMPOSING -> "Ghép ảnh"
    SessionState.PRINT_PENDING -> "Kiểm tra lần cuối"
    SessionState.PRINTING -> "Đang in và upload"
    SessionState.DELIVERY -> "Hoàn tất"
    SessionState.RECOVERY -> "Khôi phục phiên"
    SessionState.OUT_OF_SERVICE -> "Tạm ngưng phục vụ"
    SessionState.ADMIN -> "Quản trị"
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
