package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phuctran.photobooth.desktop.model.*
import com.phuctran.photobooth.desktop.ui.components.*
import com.phuctran.photobooth.desktop.ui.theme.*

@Composable
fun QuantityScreen(
    layout: LayoutMode,
    effect: EffectMode,
    onQuantitySelected: (Int) -> Unit,
    onBack: () -> Unit
) {
    var quantity by remember(layout.id) { mutableStateOf(1) }
    val isStrip = layout.printSizeLabel.contains("5x15", ignoreCase = true) ||
        layout.printSizeLabel.contains("5 x 15", ignoreCase = true)
    // Preserve the existing allowed quantities, including the two-copy minimum.
    val quantityOptions = if (isStrip) listOf(2, 4, 6, 8) else listOf(2, 3, 4, 5)
    LaunchedEffect(quantityOptions) {
        if (quantity !in quantityOptions) quantity = quantityOptions.first()
    }
    val selectedIndex = quantityOptions.indexOf(quantity)

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        CompactLocalHeader("Bước 2", "Chọn số lượng", onBack)
        Spacer(Modifier.height(28.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Text("Bạn muốn in bao nhiêu bản?", style = MaterialTheme.typography.h5, color = NeutralText)
                Spacer(Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    QuantityStepButton("−", enabled = selectedIndex > 0) {
                        quantity = quantityOptions[selectedIndex - 1]
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$quantity", style = MaterialTheme.typography.h1.copy(fontSize = 72.sp), color = NeutralText)
                        Text("bản in", style = MaterialTheme.typography.body1, color = NeutralSecondary)
                    }
                    QuantityStepButton("+", enabled = selectedIndex >= 0 && selectedIndex < quantityOptions.lastIndex) {
                        quantity = quantityOptions[selectedIndex + 1]
                    }
                }
                Spacer(Modifier.height(32.dp))
                OutputRow("Màu ảnh", effect.title)
                Spacer(Modifier.height(10.dp))
                OutputRow("Tổng thanh toán", formatVnd(layout.basePrice * quantity))
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text("Bản in dự kiến", style = MaterialTheme.typography.h5, color = NeutralText)
                Text("${layout.printSizeLabel} · ${layout.shotCount} lần chụp", style = MaterialTheme.typography.body2, color = NeutralSecondary)
                Spacer(Modifier.height(18.dp))
                PrintPreview(
                    layout = layout,
                    moments = emptyList(),
                    frame = FramePack("preview", "Le Souvenir", "", layout.accentColor),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        BottomActionBar("Tiếp tục · ${formatVnd(layout.basePrice * quantity)}", { onQuantitySelected(quantity) }, "Quay lại", onBack)
    }
}

@Composable
private fun QuantityStepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = NeutralPanelAlt,
            contentColor = NeutralText,
            disabledBackgroundColor = NeutralPanelAlt,
            disabledContentColor = NeutralMuted
        ),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, style = MaterialTheme.typography.h3)
    }
}
