package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import com.phuctran.photobooth.desktop.domain.SessionState
import com.phuctran.photobooth.desktop.model.*
import com.phuctran.photobooth.desktop.ui.components.*
import com.phuctran.photobooth.desktop.ui.theme.*
import java.nio.file.Path

@Composable
fun PrintingScreen(layout: LayoutMode, frame: FramePack, statusMessage: String) {
    val currentStep = when {
        statusMessage.contains("video", ignoreCase = true) -> 4
        statusMessage.contains("album", ignoreCase = true) || statusMessage.contains("link", ignoreCase = true) || statusMessage.contains("Cloudinary", ignoreCase = true) -> 3
        statusMessage.contains("in", ignoreCase = true) -> 2
        statusMessage.contains("ghép", ignoreCase = true) || statusMessage.contains("lưu", ignoreCase = true) -> 1
        else -> 0
    }

    PanelBox(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "PrettyBooth", 
                style = MaterialTheme.typography.h3, 
                fontWeight = FontWeight.Black, 
                color = AccentNudeDark
            )
            Spacer(Modifier.height(48.dp))
            
            // Progress Pipeline
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PipelineStep("Ghép ảnh", isActive = currentStep >= 1, isDone = currentStep > 1)
                PipelineDivider(isActive = currentStep >= 2)
                PipelineStep("In ảnh", isActive = currentStep >= 2, isDone = currentStep > 2)
                PipelineDivider(isActive = currentStep >= 3)
                PipelineStep("Album & QR", isActive = currentStep >= 3, isDone = currentStep > 3)
                PipelineDivider(isActive = currentStep >= 4)
                PipelineStep("Video", isActive = currentStep >= 4, isDone = currentStep > 4)
            }
            
            Spacer(Modifier.height(48.dp))
            
            CircularProgressIndicator(
                color = AccentNudeDark,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )
            
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

@Composable
fun PipelineStep(label: String, isActive: Boolean, isDone: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isActive) AccentNude else NeutralBorder),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            } else if (isActive) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            label, 
            style = MaterialTheme.typography.subtitle2, 
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) NeutralText else NeutralSecondary
        )
    }
}

@Composable
fun PipelineDivider(isActive: Boolean) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(2.dp)
            .background(if (isActive) AccentNude else NeutralBorder)
    )
}
