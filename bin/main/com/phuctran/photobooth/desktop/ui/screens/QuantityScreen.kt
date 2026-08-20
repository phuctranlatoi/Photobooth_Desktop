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
fun QuantityScreen(
    layout: LayoutMode,
    effect: EffectMode,
    onQuantitySelected: (Int) -> Unit,
    onBack: () -> Unit
) {
    var quantity by remember(layout.id) { mutableStateOf(1) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelBox(Modifier.weight(1f).fillMaxHeight()) {
            SectionHeader("Bước 2", "Chọn số tấm in", "Ảnh số vẫn giữ đầy đủ, bản in chỉ lấy ảnh đã chọn.")
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(1, 2, 3, 4).forEach { count ->
                    QuantityCard(count, quantity == count) { quantity = count }
                }
            }
            Spacer(Modifier.height(20.dp))
            OutputRow("Bố cục", layout.mediaLabel)
            OutputRow("Màu", effect.title)
            OutputRow("Tổng thanh toán", formatVnd(layout.basePrice * quantity))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Quay lại") }
                Button(
                    onClick = { onQuantitySelected(quantity) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = NeutralPanel, contentColor = NeutralText),
                    modifier = Modifier.weight(1f)
                ) { Text("Thanh toán") }
            }
        }
        PrintPreview(
            layout = layout,
            moments = emptyList(),
            frame = FramePack("preview", "Preview", "", layout.accentColor),
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}
