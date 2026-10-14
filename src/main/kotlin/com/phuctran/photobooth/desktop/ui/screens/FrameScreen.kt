package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.domain.SessionState
import com.phuctran.photobooth.desktop.controller.DesktopBoothController
import com.phuctran.photobooth.desktop.model.*
import com.phuctran.photobooth.desktop.ui.components.*
import com.phuctran.photobooth.desktop.ui.theme.*
import java.nio.file.Path
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState

@Composable
fun FrameScreen(
    controller: DesktopBoothController,
    frames: List<FramePack>,
    layout: LayoutMode,
    selectedMoments: List<CapturedMoment>,
    selectedFrame: FramePack,
    onFrameSelected: (String) -> Unit,
    onAddFrame: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    val timeLeft by controller.inactivityTimeLeft.collectAsState()
    Box(Modifier.fillMaxSize()) {
    PremiumPageShell(title = "Chọn khung trang trí", onBack = onBack) {
      Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(0.85f).fillMaxHeight()) {
                SectionHeader("", "Bản in xem trước", "${selectedFrame.title} · ${layout.printSizeLabel}")
                Spacer(Modifier.height(18.dp))
                PrintPreview(layout, selectedMoments, selectedFrame, Modifier.weight(1f).fillMaxWidth())
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
            
                if (frames.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Chưa có khung ảnh", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black, color = NeutralText)
                            Text("Bạn vẫn có thể dùng khung mặc định của layout.", color = NeutralSecondary)
                        }
                    }
                } else {
                    val state = rememberLazyGridState()
                    LazyVerticalGrid(
                        state = state,
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(frames, key = { it.id }) { frame ->
                            FrameChoice(frame, frame.id == selectedFrame.id) { onFrameSelected(frame.id) }
                        }
                    }
                }
            }
        }
        BottomActionBar("Xem bản in", onConfirm, "Quay lại", onBack)
      }
    }
    Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
        DecisionTimerBadge(timeLeft)
        SessionCancellationMenu(onCancel = { controller.cancelSession() })
    }
    }
}
