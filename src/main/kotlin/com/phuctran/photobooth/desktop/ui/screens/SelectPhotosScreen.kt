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
fun SelectPhotosScreen(
    layout: LayoutMode,
    capturedMoments: List<CapturedMoment>,
    selectedMoments: List<CapturedMoment>,
    onMomentSelect: (CapturedMoment) -> Unit,
    onConfirm: () -> Unit
) {
    PanelBox(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Bước 5", "Chọn ảnh để in", "Đã chọn ${selectedMoments.size}/${layout.selectCount} • album giữ toàn bộ ảnh.")
            Button(
                onClick = onConfirm,
                enabled = selectedMoments.size == layout.selectCount,
                colors = ButtonDefaults.buttonColors(backgroundColor = NeutralPanel, contentColor = NeutralText)
            ) { Text("Chọn frame") }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            capturedMoments.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { moment ->
                        MomentTile(
                            moment = moment,
                            selectedOrder = selectedMoments.indexOf(moment).takeIf { it >= 0 }?.plus(1),
                            aspectRatio = layout.photoAspectRatio,
                            onClick = { onMomentSelect(moment) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
