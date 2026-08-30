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
    Box(Modifier.fillMaxSize().background(NeutralBg)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PanelBox(Modifier.weight(1.35f).fillMaxHeight()) {
                SectionHeader("Bước 6", "Chọn ảnh để in", "Đã chọn ${selectedMoments.size}/${layout.selectCount} • album giữ toàn bộ ảnh.")
                Spacer(Modifier.height(22.dp))

                if (capturedMoments.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Chưa có ảnh để chọn", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black, color = NeutralText)
                            Text("Vui lòng chụp lại hoặc gọi nhân viên kiểm tra camera.", color = NeutralSecondary, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        capturedMoments.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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

            PanelBox(Modifier.weight(0.75f).fillMaxHeight()) {
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
                    text = "Tiếp tục chọn khung",
                    onClick = onConfirm,
                    enabled = selectedMoments.size == layout.selectCount,
                    modifier = Modifier.fillMaxWidth().height(62.dp)
                )
            }
        }
    }
}
