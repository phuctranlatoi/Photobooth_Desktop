package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.model.*
import com.phuctran.photobooth.desktop.ui.components.*
import com.phuctran.photobooth.desktop.ui.theme.*

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
    Row(Modifier.fillMaxSize().background(NeutralBg).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        PrintPreview(layout, selectedMoments, frame, Modifier.weight(0.8f).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight()) {
            SectionHeader("", "Kiểm tra lần cuối", "Xác nhận các thông số trước khi in ảnh.")
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NeutralPanel).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SummaryItem("Bản in", "${exportSummary.printPhotoCount} ảnh đã chọn  ·  $printCopies bản")
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
                KioskSecondaryButton("Quay lại", onBack, modifier = Modifier.weight(1f).height(KioskControlTokens.buttonMinHeight))
                KioskPrimaryButton("In và tạo QR", onPrint, modifier = Modifier.weight(1f).height(KioskControlTokens.buttonMinHeight))
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = NeutralSecondary, style = MaterialTheme.typography.body1, fontWeight = FontWeight.Medium)
        Text(value, color = NeutralText, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
    }
}
