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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items

@Composable
fun FrameScreen(
    frames: List<FramePack>,
    layout: LayoutMode,
    selectedMoments: List<CapturedMoment>,
    selectedFrame: FramePack,
    onFrameSelected: (String) -> Unit,
    onAddFrame: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PanelBox(Modifier.weight(0.85f).fillMaxHeight()) {
            SectionHeader("", "Preview bản in", "${selectedFrame.title} · ${layout.printSizeLabel}")
            Spacer(Modifier.height(18.dp))
            PrintPreview(layout, selectedMoments, selectedFrame, Modifier.weight(1f).fillMaxWidth())
        }
        PanelBox(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("Bước 7", "Chọn khung ảnh", "Chạm vào khung để xem ngay trên bản in.")
                KioskSecondaryButton("Thêm PNG", { onAddFrame(false) }, modifier = Modifier.width(132.dp).height(50.dp))
            }
            Spacer(Modifier.height(16.dp))
            
            if (frames.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Chưa có khung ảnh", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black, color = NeutralText)
                        Text("Bạn vẫn có thể dùng khung mặc định của layout.", color = NeutralSecondary)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(190.dp),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(frames, key = { it.id }) { frame ->
                        FrameChoice(frame, frame.id == selectedFrame.id) { onFrameSelected(frame.id) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KioskSecondaryButton("Quay lại", onBack, modifier = Modifier.weight(1f))
                KioskPrimaryButton("Xem bản in", onConfirm, modifier = Modifier.weight(1f))
            }
        }
    }
}
