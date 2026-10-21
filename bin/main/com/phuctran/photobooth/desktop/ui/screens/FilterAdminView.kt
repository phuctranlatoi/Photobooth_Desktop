package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import com.phuctran.photobooth.desktop.model.EffectMode
import com.phuctran.photobooth.desktop.ui.components.InfoPill
import com.phuctran.photobooth.desktop.ui.components.KioskPrimaryButton
import com.phuctran.photobooth.desktop.ui.components.KioskSecondaryButton
import com.phuctran.photobooth.desktop.ui.components.SectionHeader
import com.phuctran.photobooth.desktop.ui.theme.*
import java.awt.FileDialog
import java.io.File
import java.awt.RenderingHints
import javax.imageio.ImageIO
import java.awt.Frame
import java.nio.file.Path
import java.awt.image.BufferedImage
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.awt.Color as AwtColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun FilterAdminView(
    effects: List<EffectMode>,
    onSaveEffects: (List<EffectMode>) -> Unit,
    onImportLut: (Path) -> String,
    onPreviewEffect: (EffectMode, BufferedImage) -> BufferedImage
) {
    var mutableEffects by remember(effects) { mutableStateOf(effects.toMutableList()) }
    fun commitEffects(updated: List<EffectMode>) {
        mutableEffects = updated.toMutableList()
        onSaveEffects(updated)
    }
    var savedFeedback by remember { mutableStateOf(false) }
    LaunchedEffect(savedFeedback) { if (savedFeedback) { delay(2500); savedFeedback = false } }
    var selectedEffectId by remember { mutableStateOf(effects.firstOrNull()?.id) }
    LaunchedEffect(effects) {
        if (effects.none { it.id == selectedEffectId }) {
            selectedEffectId = effects.firstOrNull()?.id
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selectedEffect = mutableEffects.find { it.id == selectedEffectId }
    if (confirmDelete && selectedEffect != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Xóa bộ lọc?") },
        text = { Text("Bộ lọc này sẽ bị xóa khi bạn lưu thay đổi.") },
        confirmButton = { TextButton(onClick = {
            commitEffects(mutableEffects.filter { it.id != selectedEffectId })
            selectedEffectId = mutableEffects.firstOrNull()?.id
            confirmDelete = false
        }) { Text("Xóa") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Hủy") } }
    )

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFFFBF9F5)).padding(top = 16.dp)) {
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
                text = "+ Nhập LUT (.cube)",
                onClick = {
                    val dialog = FileDialog(null as Frame?, "Chọn LUT 3D", FileDialog.LOAD)
                    dialog.setFilenameFilter { _, name -> name.endsWith(".cube", true) }
                    dialog.isVisible = true
                    dialog.file?.let { filename ->
                        try {
                            val file = Path.of(dialog.directory, filename)
                            val stored = onImportLut(file)
                            val newId = "lut_${System.currentTimeMillis()}"
                            commitEffects(mutableEffects + EffectMode(newId, file.fileName.toString().removeSuffix(".cube"), "Màu ảnh tùy chỉnh", 0xFFC27357, isLut = true, lutPath = stored, enabled = true))
                            selectedEffectId = newId
                            error = null
                        } catch (e: Exception) {
                            System.err.println("LUT import failed: ${e.message}")
                            error = "Không thể nhập bộ lọc màu. Vui lòng kiểm tra tệp .cube và thử lại."
                        }
                    }
                    dialog.dispose()
                },
                modifier = Modifier.fillMaxWidth()
            )
            KioskSecondaryButton(
                text = "+ Thêm bộ lọc thông số",
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
            error?.let { Text(it, color = AccentRed) }

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
                        modifier = Modifier.fillMaxWidth().border(if (isSelected) 2.dp else 1.dp, if (isSelected) Color(0xFFB85838) else Color(0xFFEAE4DC), RoundedCornerShape(12.dp)),
                        backgroundColor = if (effect.enabled) Color.White else Color(0xFFF9F7F5),
                        elevation = 0.dp,
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
                            Box(Modifier.size(40.dp).border(5.dp, Color(effect.accentColor).copy(alpha = if (effect.enabled) .18f else .08f), androidx.compose.foundation.shape.CircleShape).background(Color(effect.accentColor).copy(alpha = if (effect.enabled) 1f else .38f), androidx.compose.foundation.shape.CircleShape))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    effect.title,
                                    color = if (effect.enabled) NeutralText else NeutralMuted,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    effect.description,
                                    color = NeutralSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    InfoPill(if (effect.isLut) "LUT 3D" else "Thông số", bgColor = AccentNudeLight, textColor = AccentNudeDark)
                                    InfoPill(if (effect.enabled) "BẬT · Khách thấy" else "TẮT · Đang ẩn", bgColor = if (effect.enabled) Color(0xFFE8F5E9) else NeutralPanelAlt, textColor = if (effect.enabled) Color(0xFF2E7D32) else NeutralMuted)
                                }
                            }
                            Switch(
                                checked = effect.enabled,
                                onCheckedChange = { checked ->
                                    commitEffects(mutableEffects.map { if (it.id == effect.id) it.copy(enabled = checked) else it })
                                    selectedEffectId = effect.id
                                },
                                colors = filterSwitchColors()
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
        ) {
            if (selectedEffect != null) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionHeader("Chỉnh sửa bộ lọc", selectedEffect.title, "Thay đổi thông số màu và lưu để áp dụng cho kiosk.")
                
                var title by remember(selectedEffect) { mutableStateOf(selectedEffect.title) }
                var description by remember(selectedEffect) { mutableStateOf(selectedEffect.description) }
                var saturation by remember(selectedEffect) { mutableStateOf(selectedEffect.saturation) }
                var contrast by remember(selectedEffect) { mutableStateOf(selectedEffect.contrast) }
                var brightness by remember(selectedEffect) { mutableStateOf(selectedEffect.brightness) }
                var warmth by remember(selectedEffect) { mutableStateOf(selectedEffect.warmth) }
                var tint by remember(selectedEffect) { mutableStateOf(selectedEffect.tint) }
                var intensity by remember(selectedEffect) { mutableStateOf(selectedEffect.lutIntensity) }

                fun updateCurrentEffect(newEnabled: Boolean = selectedEffect.enabled) {
                    val updated = selectedEffect.copy(
                        title = title,
                        description = description,
                        saturation = saturation,
                        contrast = contrast,
                        brightness = brightness,
                        warmth = warmth,
                        tint = tint,
                        lutIntensity = intensity,
                        enabled = newEnabled
                    )
                    mutableEffects = mutableEffects.map { if (it.id == updated.id) updated else it }.toMutableList()
                }

                Surface(
                    color = if (selectedEffect.enabled) Color(0xFFF5FBF6) else Color(0xFFF9F7F5),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedEffect.enabled) AccentMint else Color(0xFFEAE4DC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.size(42.dp).background(if (selectedEffect.enabled) Color(0xFFE8F5E9) else Color(0xFFEAE4DC), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(14.dp).background(if (selectedEffect.enabled) AccentMint else NeutralMuted, androidx.compose.foundation.shape.CircleShape))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("TRẠNG THÁI TRÊN KIOSK", color = NeutralSecondary, style = MaterialTheme.typography.caption)
                            InfoPill(if (selectedEffect.enabled) "ĐANG BẬT (Khách nhìn thấy)" else "ĐANG TẮT (Ẩn khỏi khách chụp)", bgColor = if (selectedEffect.enabled) Color(0xFFE8F5E9) else NeutralPanelAlt, textColor = if (selectedEffect.enabled) Color(0xFF2E7D32) else NeutralMuted)
                            Text(if (selectedEffect.enabled) "Bộ lọc đang kích hoạt cho khách chụp" else "Bộ lọc đang tạm ẩn khỏi Kiosk", color = NeutralText, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
                            Text(if (selectedEffect.enabled) "Bộ lọc này sẽ hiển thị trực tiếp trong danh sách chọn hiệu ứng tại buồng chụp." else "Bộ lọc này sẽ được ẩn hoàn toàn khỏi màn hình chụp của khách. Mọi thông số màu vẫn được lưu an toàn.", color = NeutralSecondary, style = MaterialTheme.typography.body2)
                        }
                        Switch(checked = selectedEffect.enabled, onCheckedChange = { checked ->
                            updateCurrentEffect(newEnabled = checked)
                            commitEffects(mutableEffects)
                        }, colors = filterSwitchColors())
                    }
                }

                Surface(color = Color.White, shape = RoundedCornerShape(18.dp), elevation = 0.dp, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEAE4DC)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.size(64.dp).background(Color(selectedEffect.accentColor), RoundedCornerShape(14.dp)))
                        Column(Modifier.weight(1f)) {
                            Text(selectedEffect.title, color = NeutralText, style = MaterialTheme.typography.h5)
                            Text(selectedEffect.description, color = NeutralSecondary, style = MaterialTheme.typography.body2)
                        }
                        InfoPill(if (selectedEffect.isLut) "LUT 3D" else "Thông số", bgColor = AccentNudeLight, textColor = AccentNudeDark)
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; updateCurrentEffect() },
                    label = { Text("Tên bộ lọc") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(textColor = NeutralText, focusedBorderColor = AccentNude)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it; updateCurrentEffect() },
                    label = { Text("Mô tả") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(textColor = NeutralText, focusedBorderColor = AccentNude)
                )

                Spacer(Modifier.height(8.dp))

                FilterBeforeAfter(selectedEffect, onPreviewEffect)

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
                    InfoPill(selectedEffect.title, bgColor = NeutralPanel, textColor = NeutralSecondary)
                }

                if (selectedEffect.isLut) {
                    Text("Tệp LUT: ${selectedEffect.title}", color = NeutralSecondary)
                    SliderControl("Cường độ ${(intensity * 100).toInt()}%", intensity, 0f..1f) { intensity = it; updateCurrentEffect() }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(.25f, .5f, .75f, 1f).forEach { amount ->
                            OutlinedButton(onClick = { intensity = amount; updateCurrentEffect() }) { Text("${(amount * 100).toInt()}%") }
                        }
                    }
                } else {
                    SliderControl("Độ bão hòa (Saturation)", saturation, 0f..2f) { saturation = it; updateCurrentEffect() }
                    SliderControl("Độ tương phản (Contrast)", contrast, 0f..2f) { contrast = it; updateCurrentEffect() }
                    SliderControl("Độ sáng (Brightness)", brightness, -1f..1f) { brightness = it; updateCurrentEffect() }
                    SliderControl("Độ ấm (Warmth)", warmth, -1f..1f) { warmth = it; updateCurrentEffect() }
                    SliderControl("Phủ màu (Tint)", tint, -1f..1f) { tint = it; updateCurrentEffect() }
                    OutlinedButton(onClick = {
                        saturation = 1f; contrast = 1f; brightness = 0f; warmth = 0f; tint = 0f
                        updateCurrentEffect()
                    }) { Text("Đặt lại thông số") }
                }

                Spacer(Modifier.height(16.dp))
                }
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8E2DC)),
                    elevation = 4.dp,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!selectedEffect.isSystemDefault) KioskSecondaryButton(
                            text = "Xóa bộ lọc", onClick = { confirmDelete = true }, modifier = Modifier.width(150.dp)
                        ) else InfoPill("Mặc định hệ thống")
                        Text(
                            if (savedFeedback) "Đã lưu tất cả thay đổi vào hệ thống!" else "Đã lưu tự động khi bật/tắt & nhập LUT",
                            modifier = Modifier.weight(1f),
                            color = if (savedFeedback) AccentMint else NeutralSecondary,
                            style = MaterialTheme.typography.caption
                        )
                        KioskPrimaryButton("Lưu tất cả thay đổi", {
                            onSaveEffects(mutableEffects)
                            savedFeedback = true
                        }, modifier = Modifier.width(220.dp).height(52.dp))
                    }
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
private fun filterSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = AccentMint,
    checkedTrackAlpha = 1f,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = Color(0xFFD6CFC7),
    uncheckedTrackAlpha = 1f
)

@Volatile private var cachedOptimizedSample: BufferedImage? = null

private fun loadOptimizedSampleImage(): BufferedImage = synchronized(EffectMode::class.java) {
    cachedOptimizedSample?.let { return@synchronized it }
    val source = try {
        (EffectMode::class.java.getResourceAsStream("/preview/base.png")
            ?: Thread.currentThread().contextClassLoader?.getResourceAsStream("base.png"))?.use { ImageIO.read(it) }
            ?: sequenceOf(File("base.png"), File("src/main/resources/preview/base.png"))
                .firstNotNullOfOrNull { if (it.isFile) ImageIO.read(it) else null }
    } catch (e: Exception) {
        System.err.println("Sample load failed: ${e.message}")
        null
    } ?: createFallbackPortraitSample()
    val scale = minOf(1.0, 600.0 / maxOf(source.width, source.height))
    val width = maxOf(1, (source.width * scale).toInt())
    val height = maxOf(1, (source.height * scale).toInt())
    val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = result.createGraphics()
    try {
        graphics.color = AwtColor.WHITE
        graphics.fillRect(0, 0, width, height)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.drawImage(source, 0, 0, width, height, null)
    } finally { graphics.dispose() }
    cachedOptimizedSample = result
    result
}

private fun createFallbackPortraitSample(): BufferedImage =
        BufferedImage(300, 240, BufferedImage.TYPE_INT_RGB).also { image ->
            val graphics = image.createGraphics()
            graphics.paint = java.awt.GradientPaint(0f, 0f, AwtColor(219, 192, 168), 300f, 240f, AwtColor(106, 86, 82))
            graphics.fillRect(0, 0, 300, 240)
            graphics.color = AwtColor(55, 43, 43)
            graphics.fillOval(90, 12, 120, 170)
            graphics.color = AwtColor(231, 172, 139)
            graphics.fillOval(107, 38, 86, 115)
            graphics.color = AwtColor(56, 45, 52)
            graphics.fillOval(70, 158, 160, 180)
            graphics.dispose()
        }

@Composable
private fun FilterBeforeAfter(effect: EffectMode, onPreviewEffect: (EffectMode, BufferedImage) -> BufferedImage) {
    var sample by remember { mutableStateOf<BufferedImage?>(null) }
    LaunchedEffect(Unit) {
        sample = withContext(Dispatchers.IO) { loadOptimizedSampleImage() }
    }
    var preview by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(effect, sample, onPreviewEffect) {
        val source = sample ?: return@LaunchedEffect
        delay(60)
        loading = true
        preview = null
        preview = withContext(Dispatchers.Default) {
            try {
                onPreviewEffect(effect, source).toComposeImageBitmap()
            } catch (e: Exception) {
                System.err.println("Filter preview failed: ${e.message}")
                source.toComposeImageBitmap()
            }
        }
        loading = false
    }
    val sampleBitmap = remember(sample) { sample?.toComposeImageBitmap() }
    val detail = if (effect.isLut) "LUT ${(effect.lutIntensity * 100).toInt()}%" else effect.title
    Surface(color = Color.White, shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeutralBorder), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Xem trước màu ảnh", style = MaterialTheme.typography.subtitle1, color = NeutralText)
                    Text("So sánh trực tiếp trên ảnh chân dung (base.png)", style = MaterialTheme.typography.body2, color = NeutralSecondary)
                }
                InfoPill("Hiển thị 1:1 (Không crop)", bgColor = NeutralPanelAlt, textColor = NeutralSecondary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(250.dp).background(Color(0xFF191817), RoundedCornerShape(14.dp))) {
                    if (sampleBitmap != null) Image(sampleBitmap, "Ảnh gốc", Modifier.fillMaxSize(), contentScale = ContentScale.Fit, alignment = Alignment.Center)
                    Text("TRƯỚC (GỐC)", Modifier.align(Alignment.TopStart).padding(10.dp)
                        .background(Color(0xCC1A1918), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color.White, style = MaterialTheme.typography.caption)
                }
                Box(Modifier.weight(1f).height(250.dp).background(Color(0xFF191817), RoundedCornerShape(14.dp))) {
                    (preview ?: sampleBitmap)?.let { Image(it, "Ảnh sau lọc", Modifier.fillMaxSize(), contentScale = ContentScale.Fit, alignment = Alignment.Center) }
                    Text("SAU (ĐÃ LỌC)", Modifier.align(Alignment.TopStart).padding(10.dp)
                        .background(AccentNude, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color.White, style = MaterialTheme.typography.caption)
                    Text(detail, Modifier.align(Alignment.TopEnd).padding(10.dp)
                        .background(Color(0xCC1A1918), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color.White, style = MaterialTheme.typography.caption, maxLines = 1)
                    if (loading) Box(Modifier.fillMaxSize().background(Color(0x66191817)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = AccentNude)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ảnh gốc · base.png", Modifier.weight(1f), color = NeutralSecondary, style = MaterialTheme.typography.body2)
                Text("Ảnh sau lọc · $detail", Modifier.weight(1f), color = NeutralSecondary, style = MaterialTheme.typography.body2)
            }
        }
    }
}


@Composable
fun SliderControl(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = NeutralText, fontWeight = FontWeight.Bold)
            Text(String.format("%.2f", value), color = AccentNudeDark, fontWeight = FontWeight.Bold,
                fontSize = 13.sp, modifier = Modifier.background(AccentNudeLight, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFB85838),
                activeTrackColor = Color(0xFFB85838),
                inactiveTrackColor = NeutralBorder
            )
        )
    }
}


