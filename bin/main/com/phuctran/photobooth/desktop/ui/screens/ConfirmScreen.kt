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
            SectionHeader("Bước 8", "Kiểm tra lần cuối", "Xác nhận các thông số trước khi in ấn.")
            Spacer(Modifier.height(24.dp))
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SummaryItem("Bản in", "${exportSummary.printPhotoCount} ảnh đã chọn  •  $printCopies bản")
                SummaryItem("Ảnh kỹ thuật số", "${exportSummary.uploadedPhotoCount} ảnh gốc")
                
                if (exportSummary.uploadedVideoCount > 0) {
                    SummaryItem("Video", "${exportSummary.uploadedVideoCount} clip")
                }
                
                Divider(color = NeutralBg, modifier = Modifier.padding(vertical = 4.dp))
                
                SummaryItem("Bộ lọc màu", effect.title)
                SummaryItem("Thiết kế khung", frame.title)
            }
            
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KioskSecondaryButton("Quay lại", onBack, modifier = Modifier.weight(1f))
                KioskPrimaryButton("In và tạo QR", onPrint, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = NeutralSecondary, style = MaterialTheme.typography.body1, fontWeight = FontWeight.Medium)
        Text(value, color = AccentNudeDark, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Black)
    }
}
