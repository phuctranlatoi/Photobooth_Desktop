package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.model.CapturedMoment
import com.phuctran.photobooth.desktop.model.FramePack
import com.phuctran.photobooth.desktop.model.LayoutMode
import com.phuctran.photobooth.desktop.ui.components.*
import com.phuctran.photobooth.desktop.ui.theme.*

@Composable
fun SelectPhotosScreen(
    layout: LayoutMode,
    capturedMoments: List<CapturedMoment>,
    selectedMoments: List<CapturedMoment>,
    onMomentSelect: (CapturedMoment) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(NeutralBg).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            KioskBackButton(onClick = onBack)
            Text("Chọn ảnh", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold, color = NeutralText)
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1.35f).fillMaxHeight()) {
                SectionHeader("", "Chọn ảnh để in", "Đã chọn ${selectedMoments.size}/${layout.selectCount} ảnh · album giữ toàn bộ ảnh.")
                Spacer(Modifier.height(24.dp))
                if (capturedMoments.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Chưa có ảnh để chọn", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold, color = NeutralText)
                            Text("Vui lòng chụp lại hoặc gọi nhân viên kiểm tra camera.", color = NeutralSecondary, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        capturedMoments.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                row.forEach { moment ->
                                    MomentTile(
                                        moment = moment,
                                        selectedOrder = selectedMoments.indexOf(moment).takeIf { it >= 0 }?.plus(1),
                                        aspectRatio = layout.photoAspectRatio,
                                        isDimmed = selectedMoments.size >= layout.selectCount && !selectedMoments.contains(moment),
                                        onClick = { onMomentSelect(moment) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            Column(Modifier.weight(0.75f).fillMaxHeight()) {
                SectionHeader("", "Bản in đang chọn", "${selectedMoments.size}/${layout.selectCount} ảnh")
                Spacer(Modifier.height(18.dp))
                PrintPreview(
                    layout = layout,
                    moments = selectedMoments,
                    frame = FramePack("selection_preview", "Le Souvenir", "", layout.accentColor),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                Spacer(Modifier.height(18.dp))
                OutputRow("Cần chọn", "${layout.selectCount} ảnh")
                Spacer(Modifier.height(10.dp))
                OutputRow("Đã chọn", "${selectedMoments.size} ảnh")
                Spacer(Modifier.height(18.dp))
                KioskPrimaryButton(
                    text = "Tiếp tục",
                    onClick = onConfirm,
                    enabled = selectedMoments.size == layout.selectCount,
                    modifier = Modifier.fillMaxWidth().height(KioskControlTokens.buttonMinHeight)
                )
            }
        }
    }
}
