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
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PanelBox(Modifier.weight(1f).fillMaxHeight()) {
            SectionHeader("Bước 3", "Chọn số bản in", "Album số vẫn giữ toàn bộ ảnh, bản in chỉ dùng ảnh bạn chọn.")
            Spacer(Modifier.height(24.dp))
            val isStrip = layout.printSizeLabel.contains("5x15", ignoreCase = true) || layout.printSizeLabel.contains("5 x 15", ignoreCase = true)
            // For 5x15, min 2 copies (1 sheet of 10x15). Options: 2, 4, 6, 8.
            // For 10x15, min 2 copies. Options: 2, 3, 4, 5.
            val quantityOptions = if (isStrip) listOf(2, 4, 6, 8) else listOf(2, 3, 4, 5)
            
            // Adjust current quantity if it's not in the options
            LaunchedEffect(quantityOptions) {
                if (quantity !in quantityOptions) {
                    quantity = quantityOptions.first()
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                quantityOptions.forEach { count ->
                    QuantityCard(count, quantity == count) { quantity = count }
                }
            }
            Spacer(Modifier.height(24.dp))
            OutputRow("Bố cục", layout.mediaLabel)
            Spacer(Modifier.height(10.dp))
            OutputRow("Màu", effect.title)
            Spacer(Modifier.height(10.dp))
            OutputRow("Tổng thanh toán", formatVnd(layout.basePrice * quantity))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KioskSecondaryButton("Quay lại", onBack, modifier = Modifier.weight(1f))
                KioskPrimaryButton("Thanh toán", { onQuantitySelected(quantity) }, modifier = Modifier.weight(1f))
            }
        }
        PanelBox(Modifier.weight(1f).fillMaxHeight()) {
            SectionHeader("", "Bản in dự kiến", "${layout.printSizeLabel} · ${layout.shotCount} lần chụp")
            Spacer(Modifier.height(18.dp))
            PrintPreview(
                layout = layout,
                moments = emptyList(),
                frame = FramePack("preview", "Le Souvenir", "", layout.accentColor),
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}
