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
import com.phuctran.photobooth.desktop.ui.components.SectionHeader
import com.phuctran.photobooth.desktop.ui.theme.NeutralBorder

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
            SectionHeader("Bộ Lọc (Filters)", "Danh sách các bộ lọc hiện có", "")
            
            Button(
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
            ) {
                Text("+ Thêm bộ lọc mới", fontWeight = FontWeight.Bold)
            }

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
                        backgroundColor = if (isSelected) MaterialTheme.colors.primary else Color(0xFF27273A),
                        elevation = if (isSelected) 8.dp else 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedEffectId = effect.id }
                                .padding(16.dp)
                        ) {
                            Text(
                                effect.title,
                                color = if (isSelected) Color.White else Color.LightGray,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                effect.description,
                                color = if (isSelected) Color.White.copy(alpha = 0.8f) else Color.Gray,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
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
                SectionHeader("Chỉnh sửa", "Thay đổi thông số cho ${selectedEffect.title}", "")
                
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

                SliderControl("Độ bão hòa (Saturation)", saturation, 0f..2f) { saturation = it; updateCurrentEffect() }
                SliderControl("Độ tương phản (Contrast)", contrast, 0f..2f) { contrast = it; updateCurrentEffect() }
                SliderControl("Độ sáng (Brightness)", brightness, -1f..1f) { brightness = it; updateCurrentEffect() }
                SliderControl("Độ ấm (Warmth)", warmth, -1f..1f) { warmth = it; updateCurrentEffect() }
                SliderControl("Phủ màu (Tint)", tint, -1f..1f) { tint = it; updateCurrentEffect() }

                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(
                        onClick = {
                            mutableEffects = mutableEffects.filter { it.id != selectedEffectId }.toMutableList()
                            selectedEffectId = mutableEffects.firstOrNull()?.id
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red, backgroundColor = Color.Transparent)
                    ) {
                        Text("Xóa bộ lọc này", fontWeight = FontWeight.Bold)
                    }

                    Button(onClick = { onSaveEffects(mutableEffects) }) {
                        Text("Lưu tất cả thay đổi", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Vui lòng chọn một bộ lọc để chỉnh sửa.", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun SliderControl(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold)
            Text(String.format("%.2f", value), color = Color.Gray)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
