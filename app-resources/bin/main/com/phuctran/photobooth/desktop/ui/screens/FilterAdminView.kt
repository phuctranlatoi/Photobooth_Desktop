package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phuctran.photobooth.desktop.model.EffectMode
import com.phuctran.photobooth.desktop.ui.components.InfoPill
import com.phuctran.photobooth.desktop.ui.components.KioskPrimaryButton
import com.phuctran.photobooth.desktop.ui.components.KioskSecondaryButton
import com.phuctran.photobooth.desktop.ui.components.SectionHeader
import com.phuctran.photobooth.desktop.ui.theme.*

@Composable
fun FilterAdminView(
    effects: List<EffectMode>,
    onSaveEffects: (List<EffectMode>) -> Unit
) {
    var mutableEffects by remember(effects) { mutableStateOf(effects.toMutableList()) }
    var selectedEffectId by remember(effects) { mutableStateOf(effects.firstOrNull()?.id) }
    val selectedEffect = mutableEffects.find { it.id == selectedEffectId }

    Row(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
        // Left Column: List of Filters
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader("Admin", "Bộ lọc màu", "Danh sách các bộ lọc hiện có.")
            
            KioskPrimaryButton(
                text = "Thêm bộ lọc mới",
                onClick = {
                    val newId = "filter_${System.currentTimeMillis()}"
                    val newEffect = EffectMode(
                        id = newId,
                        title = "Bộ lọc mới",
                        description = "Mô tả bộ lọc",
                        accentColor = 0xFF5F6B7A,
                        saturation = 1.0f,
                        contrast = 1.0f,
                        brightness = 0.0f,
                        warmth = 0.0f,
                        tint = 0.0f
                    )
                    mutableEffects = mutableEffects.toMutableList().apply { add(newEffect) }
                    selectedEffectId = newId
                },
                modifier = Modifier.fillMaxWidth()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                mutableEffects.forEach { effect ->
                    val isSelected = effect.id == selectedEffectId
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = if (isSelected) AccentNudeLight else NeutralPanel,
                        elevation = if (isSelected) 6.dp else 0.dp,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedEffectId = effect.id }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(Modifier.size(34.dp).background(Color(effect.accentColor), androidx.compose.foundation.shape.CircleShape))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    effect.title,
                                    color = NeutralText,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    effect.description,
                                    color = NeutralSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        Divider(modifier = Modifier.width(1.dp).fillMaxHeight(), color = NeutralBorder)

        // Right Column: Editor
        Column(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight()
                .padding(start = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedEffect != null) {
                SectionHeader("Chỉnh sửa", selectedEffect.title, "Thay đổi thông số màu và lưu để áp dụng cho kiosk.")
                
                var title by remember(selectedEffect) { mutableStateOf(selectedEffect.title) }
                var description by remember(selectedEffect) { mutableStateOf(selectedEffect.description) }
                var saturation by remember(selectedEffect) { mutableStateOf(selectedEffect.saturation) }
                var contrast by remember(selectedEffect) { mutableStateOf(selectedEffect.contrast) }
                var brightness by remember(selectedEffect) { mutableStateOf(selectedEffect.brightness) }
                var warmth by remember(selectedEffect) { mutableStateOf(selectedEffect.warmth) }
                var tint by remember(selectedEffect) { mutableStateOf(selectedEffect.tint) }

                fun updateCurrentEffect() {
                    val updated = selectedEffect.copy(
                        title = title,
                        description = description,
                        saturation = saturation,
                        contrast = contrast,
                        brightness = brightness,
                        warmth = warmth,
                        tint = tint
                    )
                    mutableEffects = mutableEffects.map { if (it.id == updated.id) updated else it }.toMutableList()
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; updateCurrentEffect() },
                    label = { Text("Tên bộ lọc") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it; updateCurrentEffect() },
                    label = { Text("Mô tả") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(NeutralPanelAlt, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(52.dp).background(Color(selectedEffect.accentColor), androidx.compose.foundation.shape.CircleShape))
                    Column(Modifier.weight(1f)) {
                        Text("Preview màu", color = NeutralText, fontWeight = FontWeight.Bold)
                        Text("Thay đổi slider sẽ cập nhật bộ lọc đang chọn.", color = NeutralSecondary, style = MaterialTheme.typography.body2)
                    }
                    InfoPill(selectedEffect.id, bgColor = NeutralPanel, textColor = NeutralSecondary)
                }

                SliderControl("Độ bão hòa (Saturation)", saturation, 0f..2f) { saturation = it; updateCurrentEffect() }
                SliderControl("Độ tương phản (Contrast)", contrast, 0f..2f) { contrast = it; updateCurrentEffect() }
                SliderControl("Độ sáng (Brightness)", brightness, -1f..1f) { brightness = it; updateCurrentEffect() }
                SliderControl("Độ ấm (Warmth)", warmth, -1f..1f) { warmth = it; updateCurrentEffect() }
                SliderControl("Phủ màu (Tint)", tint, -1f..1f) { tint = it; updateCurrentEffect() }

                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    KioskSecondaryButton(
                        text = "Xóa bộ lọc này",
                        onClick = {
                            mutableEffects = mutableEffects.filter { it.id != selectedEffectId }.toMutableList()
                            selectedEffectId = mutableEffects.firstOrNull()?.id
                        },
                        modifier = Modifier.width(190.dp)
                    )

                    KioskPrimaryButton("Lưu tất cả thay đổi", { onSaveEffects(mutableEffects) }, modifier = Modifier.width(220.dp))
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Vui lòng chọn một bộ lọc để chỉnh sửa.", color = NeutralSecondary)
                }
            }
        }
    }
}

@Composable
fun SliderControl(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = NeutralText, fontWeight = FontWeight.Bold)
            Text(String.format("%.2f", value), color = NeutralSecondary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = AccentNude,
                activeTrackColor = AccentNude,
                inactiveTrackColor = NeutralBorder
            )
        )
    }
}
