package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CompactLocalHeader("Bước 6", "Chọn ảnh")
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1.4f).fillMaxHeight()) {
                SectionHeader("", "Chọn ảnh để in", "Đã chọn ${selectedMoments.size}/${layout.selectCount} ảnh · Album giữ toàn bộ ảnh.")
                Spacer(Modifier.height(18.dp))
                if (capturedMoments.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Chưa có ảnh để chọn", style = MaterialTheme.typography.h5, color = NeutralText)
                            Text("Vui lòng chụp lại hoặc gọi nhân viên kiểm tra camera.", color = NeutralSecondary, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        itemsIndexed(capturedMoments, key = { index, moment -> "${moment.photoPath}:$index" }) { _, moment ->
                            MomentTile(
                                moment = moment,
                                selectedOrder = selectedMoments.indexOf(moment).takeIf { it >= 0 }?.plus(1),
                                aspectRatio = layout.photoAspectRatio,
                                isDimmed = selectedMoments.size >= layout.selectCount && !selectedMoments.contains(moment),
                                onClick = { onMomentSelect(moment) }
                            )
                        }
                    }
                }
            }
            Column(Modifier.weight(0.85f).fillMaxHeight()) {
                SectionHeader("", "Bản in đang chọn", "${selectedMoments.size}/${layout.selectCount} ảnh")
                Spacer(Modifier.height(18.dp))
                PrintPreview(
                    layout = layout,
                    moments = selectedMoments,
                    frame = FramePack("selection_preview", "Le Souvenir", "", layout.accentColor),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
        BottomActionBar(
            primaryText = "Tiếp tục",
            onPrimary = onConfirm,
            secondaryText = "Quay lại",
            onSecondary = onBack,
            primaryEnabled = selectedMoments.size == layout.selectCount
        )
    }
}
