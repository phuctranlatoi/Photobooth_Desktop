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
fun FrameScreen(
    frames: List<FramePack>,
    layout: LayoutMode,
    selectedMoments: List<CapturedMoment>,
    selectedFrame: FramePack,
    onFrameSelected: (String) -> Unit,
    onAddFrame: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrintPreview(layout, selectedMoments, selectedFrame, Modifier.weight(0.8f).fillMaxHeight())
        PanelBox(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("Bước 6", "Chọn khung ảnh", "Bấm vào khung để thay đổi.")
                OutlinedButton(onClick = onAddFrame) { Text("Thêm PNG") }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                frames.forEach { frame ->
                    FrameChoice(frame, frame.id == selectedFrame.id) { onFrameSelected(frame.id) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Quay lại") }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(backgroundColor = NeutralPanel, contentColor = NeutralText),
                    modifier = Modifier.weight(1f)
                ) { Text("Xem bản in") }
            }
        }
    }
}
