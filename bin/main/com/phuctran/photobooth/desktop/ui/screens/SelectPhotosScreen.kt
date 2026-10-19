package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.model.CapturedMoment
import com.phuctran.photobooth.desktop.model.FramePack
import com.phuctran.photobooth.desktop.model.LayoutMode
import com.phuctran.photobooth.desktop.ui.components.*
import com.phuctran.photobooth.desktop.ui.theme.*
import com.phuctran.photobooth.desktop.controller.DesktopBoothController

@Composable
fun SelectPhotosScreen(
    controller: DesktopBoothController,
    layout: LayoutMode,
    capturedMoments: List<CapturedMoment>,
    selectedMoments: List<CapturedMoment>,
    onMomentSelect: (CapturedMoment) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    val timeLeft by controller.inactivityTimeLeft.collectAsState()
    var previewingMoment by remember { mutableStateOf<CapturedMoment?>(null) }
    val gridState = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CompactLocalHeader("Bước 6", "Chọn ảnh")
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1.4f).fillMaxHeight()) {
                SectionHeader("", "Chọn ảnh để in", "Đã chọn ${selectedMoments.size}/${layout.selectCount} ảnh · Album giữ toàn bộ ảnh.")
                Text("Chạm để chọn · Giữ để xem lớn", style = MaterialTheme.typography.body2, color = NeutralSecondary)
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
                        state = gridState,
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.weight(1f).fillMaxWidth().kioskVerticalDrag(gridState),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        itemsIndexed(capturedMoments, key = { index, moment -> "${moment.photoPath}:$index" }, span = { _, _ -> GridItemSpan(if (layout.photoAspectRatio >= 1.1f) 3 else 2) }) { _, moment ->
                            MomentTile(
                                moment = moment,
                                selectedOrder = selectedMoments.indexOf(moment).takeIf { it >= 0 }?.plus(1),
                                aspectRatio = layout.photoAspectRatio,
                                isDimmed = selectedMoments.size >= layout.selectCount && !selectedMoments.contains(moment),
                                onClick = { onMomentSelect(moment) },
                                onPreview = { previewingMoment = if (it) moment else null }
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
        Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
            DecisionTimerBadge(timeLeft)
            SessionCancellationMenu(onCancel = { controller.cancelSession() })
        }
        AnimatedVisibility(
            visible = previewingMoment != null,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(),
            exit = ExitTransition.None
        ) {
            previewingMoment?.let { moment ->
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f))) {
                    val preview = rememberPreviewImageBitmap(moment.photoPath)
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            contentDescription = moment.photoLabel,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(24.dp)
                        )
                    }
                }
            }
        }
    }
}
