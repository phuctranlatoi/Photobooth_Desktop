package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CompactLocalHeader("Bước 8", "Kiểm tra lần cuối")
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            PrintPreview(layout, selectedMoments, frame, Modifier.weight(1.15f).fillMaxHeight())
            Column(Modifier.weight(0.85f).fillMaxHeight()) {
                SectionHeader("", "Bản in của bạn", "Xác nhận trước khi in ảnh.")
                Spacer(Modifier.height(28.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    SummaryItem("Bản in", "${exportSummary.printPhotoCount} ảnh đã chọn  ·  $printCopies bản")
                    SummaryItem("Ảnh kỹ thuật số", "${exportSummary.uploadedPhotoCount} ảnh gốc")
                    if (exportSummary.uploadedVideoCount > 0) {
                        SummaryItem("Video", "${exportSummary.uploadedVideoCount} clip")
                    }
                    Divider(color = NeutralBorder, modifier = Modifier.padding(vertical = 4.dp))
                    SummaryItem("Bộ lọc màu", effect.title)
                    SummaryItem("Thiết kế khung", frame.title)
                }
            }
        }
        BottomActionBar("In và tạo QR", onPrint, "Quay lại", onBack)
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = NeutralSecondary, style = MaterialTheme.typography.body1, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(value, color = NeutralText, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
    }
}
