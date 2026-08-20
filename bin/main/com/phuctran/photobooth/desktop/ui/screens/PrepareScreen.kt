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
fun PrepareScreen(layout: LayoutMode, effect: EffectMode) {
    PanelBox(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            CameraPoseGuide(Modifier.weight(0.8f).fillMaxHeight(0.8f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Chuẩn bị", color = AccentNude, fontWeight = FontWeight.Bold)
                Text("Mỗi tấm có 3 giây", style = MaterialTheme.typography.h3, fontWeight = FontWeight.Black)
                InfoPill("Layout ${layout.title} • chọn ${layout.selectCount}")
                InfoPill("Màu ${effect.title}")
            }
        }
    }
}
