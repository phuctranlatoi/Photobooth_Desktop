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
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            CameraPoseGuide(Modifier.weight(0.9f).fillMaxHeight(0.86f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                SectionHeader("Bước 5", "Chuẩn bị tạo dáng", "Khi nhấn chụp, mỗi ảnh sẽ có 3 giây đếm ngược.")
                Spacer(Modifier.height(28.dp))
                Text("Đứng vào khung, nhìn camera và giữ dáng khi số đếm hiện lên.", style = MaterialTheme.typography.h4, fontWeight = FontWeight.Black, color = NeutralText)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoPill("${layout.shotCount} lần chụp", bgColor = AccentNudeLight, textColor = AccentNudeDark)
                    InfoPill("Chọn ${layout.selectCount} ảnh in")
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoPill(layout.title)
                    InfoPill(effect.title, bgColor = NeutralPanelAlt, textColor = NeutralText)
                }
            }
        }
    }
}
