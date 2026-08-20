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
fun PrintingScreen(layout: LayoutMode, frame: FramePack) {
    PanelBox(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Đang in ảnh", style = MaterialTheme.typography.h3, fontWeight = FontWeight.Black)
                CircularProgressIndicator(color = AccentNude, strokeWidth = 6.dp, modifier = Modifier.size(84.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoPill(layout.mediaLabel)
                    InfoPill(frame.title)
                    InfoPill("Đang tạo album")
                }
            }
        }
    }
}
