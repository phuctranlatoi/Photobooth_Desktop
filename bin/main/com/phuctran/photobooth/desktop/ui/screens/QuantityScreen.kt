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

    Column(Modifier.fillMaxSize().background(NeutralBg).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            KioskBackButton(onBack, modifier = Modifier.size(KioskControlTokens.buttonMinHeight))
            SectionHeader("Bước 2", "Chọn số lượng", "Chọn số bản in bạn muốn nhận.")
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text("Số bản in", style = MaterialTheme.typography.h5, color = NeutralText)
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    QuantityStepButton("−", enabled = selectedIndex > 0) {
                        quantity = quantityOptions[selectedIndex - 1]
                    }
                    Text("$quantity", style = MaterialTheme.typography.h1, color = NeutralText)
                    QuantityStepButton("+", enabled = selectedIndex >= 0 && selectedIndex < quantityOptions.lastIndex) {
                        quantity = quantityOptions[selectedIndex + 1]
                    }
                    Text("bản in", style = MaterialTheme.typography.body1, color = NeutralSecondary)
                }
                Spacer(Modifier.height(32.dp))
                OutputRow("Màu ảnh", effect.title)
                Spacer(Modifier.height(10.dp))
                OutputRow("Tổng thanh toán", formatVnd(layout.basePrice * quantity))
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KioskSecondaryButton("Quay lại", onBack, modifier = Modifier.weight(1f))
                    KioskPrimaryButton("Tiếp tục", { onQuantitySelected(quantity) }, modifier = Modifier.weight(1f))
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
}

@Composable
private fun QuantityStepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(56.dp),
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
