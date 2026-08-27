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
import com.phuctran.photobooth.desktop.domain.SessionState
import com.phuctran.photobooth.desktop.model.*
import com.phuctran.photobooth.desktop.ui.components.*
import com.phuctran.photobooth.desktop.ui.theme.*


import java.nio.file.Path

@Composable
fun PrintingScreen(layout: LayoutMode, frame: FramePack, statusMessage: String) {
    PanelBox(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = AccentNudeDark,
                strokeWidth = 6.dp,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(48.dp))
            
            Text(
                "Le Souvenir", 
                style = MaterialTheme.typography.h3, 
                fontWeight = FontWeight.Black, 
                color = AccentNudeDark
            )
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                statusMessage.ifBlank { "Đang hoàn thiện tác phẩm của bạn..." }, 
                style = MaterialTheme.typography.h5, 
                fontWeight = FontWeight.Medium, 
                color = NeutralText,
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(24.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill(layout.printSizeLabel)
                InfoPill(frame.title)
            }
        }
    }
}
