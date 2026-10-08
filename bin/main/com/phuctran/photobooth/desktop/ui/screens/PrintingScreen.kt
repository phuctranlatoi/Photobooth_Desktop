package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.model.FramePack
import com.phuctran.photobooth.desktop.model.LayoutMode
import com.phuctran.photobooth.desktop.ui.components.PanelBox
import com.phuctran.photobooth.desktop.ui.theme.*

@Composable
fun PrintingScreen(layout: LayoutMode, frame: FramePack, statusMessage: String) {
    val currentStep = when {
        statusMessage.contains("video", ignoreCase = true) -> 4
        statusMessage.contains("album", ignoreCase = true) || statusMessage.contains("link", ignoreCase = true) || statusMessage.contains("Cloudinary", ignoreCase = true) -> 3
        statusMessage.contains("in", ignoreCase = true) -> 2
        statusMessage.contains("ghép", ignoreCase = true) || statusMessage.contains("lưu", ignoreCase = true) -> 1
        else -> 0
    }

    Box(Modifier.fillMaxSize().background(NeutralBg).padding(32.dp)) {
        PanelBox(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Le Souvenir", style = MaterialTheme.typography.h3, fontWeight = FontWeight.Bold, color = NeutralText)
                Spacer(Modifier.height(48.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    PipelineStep("Ghép ảnh", currentStep >= 1, currentStep > 1)
                    PipelineDivider(currentStep >= 2)
                    PipelineStep("In ảnh", currentStep >= 2, currentStep > 2)
                    PipelineDivider(currentStep >= 3)
                    PipelineStep("Album & QR", currentStep >= 3, currentStep > 3)
                    PipelineDivider(currentStep >= 4)
                    PipelineStep("Video", currentStep >= 4, currentStep > 4)
                }
                Spacer(Modifier.height(48.dp))
                CircularProgressIndicator(color = AccentMint, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    statusMessage.ifBlank { "Đang hoàn thiện tác phẩm của bạn..." },
                    style = MaterialTheme.typography.h5,
                    fontWeight = FontWeight.Medium,
                    color = NeutralText,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun PipelineStep(label: String, isActive: Boolean, isDone: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (isActive) AccentMint else NeutralBorder),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            } else if (isActive) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.subtitle2, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) NeutralText else NeutralSecondary)
    }
}

@Composable
fun PipelineDivider(isActive: Boolean) {
    Box(Modifier.width(40.dp).height(2.dp).background(if (isActive) AccentMint else NeutralBorder))
}
