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
fun ConfirmScreen(
    layout: LayoutMode,
    effect: EffectMode,
    frame: FramePack,
    selectedMoments: List<CapturedMoment>,
    printCopies: Int,
    exportSummary: ExportSummary,
    onPrint: () -> Unit,
    onBack: () -> Unit
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrintPreview(layout, selectedMoments, frame, Modifier.weight(0.8f).fillMaxHeight())
        PanelBox(Modifier.weight(1f).fillMaxHeight()) {
            SectionHeader("Bước 7", "Xác nhận output", "Ảnh in dùng ảnh đã chọn, album số giữ ảnh gốc.")
            Spacer(Modifier.height(12.dp))
            OutputRow("In", "${exportSummary.printPhotoCount} ảnh đã chọn • $printCopies bản")
            OutputRow("Upload ảnh", "${exportSummary.uploadedPhotoCount} ảnh gốc")
            OutputRow("Upload video", "${exportSummary.uploadedVideoCount} clip")
            OutputRow("Style", effect.title)
            OutputRow("Frame", frame.title)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Quay lại") }
                Button(
                    onClick = onPrint,
                    colors = ButtonDefaults.buttonColors(backgroundColor = NeutralPanel, contentColor = NeutralText),
                    modifier = Modifier.weight(1f)
                ) { Text("In và tạo QR") }
            }
        }
    }
}
