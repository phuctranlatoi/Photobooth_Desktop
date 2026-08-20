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
fun StudioModeScreen(
    layouts: List<LayoutMode>,
    effects: List<EffectMode>,
    selectedLayout: LayoutMode,
    selectedEffect: EffectMode,
    onLayoutSelected: (String) -> Unit,
    onEffectSelected: (String) -> Unit,
    onConfirm: () -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var selectedSize by remember { mutableStateOf(selectedLayout.printSizeLabel) }
    
    val availableSizes = layouts.map { it.printSizeLabel }.distinct()
    val layoutsForSize = layouts.filter { it.printSizeLabel == selectedSize }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // Top Progress Bar
        Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            StepIndicator(step = 1, currentStep = step, label = "Kích cỡ khung")
            Spacer(Modifier.width(32.dp))
            Divider(Modifier.width(100.dp), color = if (step >= 2) MaterialTheme.colors.primary else NeutralBorder)
            Spacer(Modifier.width(32.dp))
            StepIndicator(step = 2, currentStep = step, label = "Bố cục")
            Spacer(Modifier.width(32.dp))
            Divider(Modifier.width(100.dp), color = if (step >= 3) MaterialTheme.colors.primary else NeutralBorder)
            Spacer(Modifier.width(32.dp))
            StepIndicator(step = 3, currentStep = step, label = "Màu ảnh")
        }

        PanelBox(Modifier.fillMaxSize()) {
            androidx.compose.animation.AnimatedContent(
                targetState = step
            ) { targetStep ->
                when (targetStep) {
                    1 -> Step1PrintSize(
                        availableSizes = availableSizes,
                        selectedSize = selectedSize,
                        onSizeSelect = { size ->
                            selectedSize = size
                            val firstLayout = layouts.first { it.printSizeLabel == size }
                            onLayoutSelected(firstLayout.id)
                            step = 2
                        }
                    )
                    2 -> Step2Layout(
                        layouts = layoutsForSize,
                        selectedLayout = selectedLayout,
                        onLayoutSelect = {
                            onLayoutSelected(it.id)
                            step = 3
                        },
                        onBack = { step = 1 }
                    )
                    3 -> Step3Effect(
                        effects = effects,
                        selectedEffect = selectedEffect,
                        selectedLayout = selectedLayout,
                        onEffectSelect = onEffectSelected,
                        onBack = { step = 2 },
                        onConfirm = onConfirm
                    )
                }
            }
        }
    }
}

@Composable
fun StepIndicator(step: Int, currentStep: Int, label: String) {
    val isActive = currentStep >= step
    val color = if (isActive) MaterialTheme.colors.primary else NeutralMuted
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isActive) color else NeutralPanel),
            contentAlignment = Alignment.Center
        ) {
            Text(step.toString(), color = if (isActive) Color.White else NeutralMuted, fontWeight = FontWeight.Bold)
        }
        Text(label, color = color, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.h6)
    }
}

@Composable
fun Step1PrintSize(
    availableSizes: List<String>,
    selectedSize: String,
    onSizeSelect: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("CHỌN KÍCH CỠ KHUNG", style = MaterialTheme.typography.h3, fontWeight = FontWeight.Black, color = NeutralText)
        Spacer(Modifier.height(8.dp))
        Text("Bước đầu tiên để có một bức ảnh đẹp.", style = MaterialTheme.typography.h6, color = NeutralMuted)
        
        Spacer(Modifier.height(32.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            availableSizes.forEach { size ->
                val isSelected = size == selectedSize
                Box(
                    modifier = Modifier
                        .size(240.dp, 320.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.1f) else NeutralPanel)
                        .border(
                            width = if (isSelected) 4.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colors.primary else NeutralBorder,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clickable { onSizeSelect(size) }
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            Modifier.size(160.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, NeutralBorder, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            // Dummy visual for size
                            val isLandscape = size.startsWith("15")
                            Box(Modifier.size(if (isLandscape) 120.dp else 80.dp, if (isLandscape) 80.dp else 120.dp).border(4.dp, NeutralBorder))
                        }
                        Text(size, style = MaterialTheme.typography.h4, fontWeight = FontWeight.Bold, color = NeutralText)
                    }
                }
            }
        }
    }
}

@Composable
fun Step2Layout(
    layouts: List<LayoutMode>,
    selectedLayout: LayoutMode,
    onLayoutSelect: (LayoutMode) -> Unit,
    onBack: () -> Unit
) {
    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.width(400.dp).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Text("←", fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                SectionHeader("Bước 2", "Chọn bố cục", "Nhấn vào bố cục để chọn và đi tiếp.")
            }
            Spacer(Modifier.height(24.dp))
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                layouts.forEach { layout ->
                    LayoutChoice(
                        layout = layout,
                        selected = layout.id == selectedLayout.id,
                        onClick = { onLayoutSelect(layout) }
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            PrintPreview(
                layout = selectedLayout,
                moments = emptyList(),
                frame = FramePack("preview", "Preview", "", selectedLayout.accentColor),
                modifier = Modifier.fillMaxHeight(0.9f)
            )
        }
    }
}

@Composable
fun Step3Effect(
    effects: List<EffectMode>,
    selectedEffect: EffectMode,
    selectedLayout: LayoutMode,
    onEffectSelect: (String) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.width(400.dp).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Text("←", fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                SectionHeader("Bước 3", "Màu ảnh", "Chọn bộ lọc màu yêu thích.")
            }
            Spacer(Modifier.height(24.dp))
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                effects.forEach { effect ->
                    ChoiceRow(
                        title = effect.title,
                        subtitle = effect.description,
                        selected = effect.id == selectedEffect.id,
                        accent = Color(effect.accentColor),
                        onClick = { onEffectSelect(effect.id) }
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            BouncyButton(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(80.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary, contentColor = Color.White)
            ) {
                Text("XÁC NHẬN CHỤP", style = MaterialTheme.typography.h4, fontWeight = FontWeight.Black)
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            PrintPreview(
                layout = selectedLayout,
                moments = emptyList(),
                frame = FramePack("preview", "Preview", "", selectedLayout.accentColor),
                modifier = Modifier.fillMaxHeight(0.9f)
            )
        }
    }
}
