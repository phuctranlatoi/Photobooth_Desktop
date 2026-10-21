package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import com.phuctran.photobooth.desktop.services.NativeEosCaptureService
import com.phuctran.photobooth.desktop.ui.theme.*
import kotlinx.coroutines.delay
import javax.swing.JFileChooser

@Composable
fun SettingsTabContent(
    config: DesktopBoothConfig,
    nativeCamera: NativeEosCaptureService?,
    enablePrint: Boolean, onEnablePrintChange: (Boolean) -> Unit,
    useHotFolder: Boolean, onUseHotFolderChange: (Boolean) -> Unit,
    hotFolderPath: String, onHotFolderPathChange: (String) -> Unit,
    postCaptureTimeoutSeconds: Int, onPostCaptureTimeoutChange: (Int) -> Unit,
    photoSelectionTimeoutSeconds: Int, onPhotoSelectionTimeoutChange: (Int) -> Unit,
    frameSelectionTimeoutSeconds: Int, onFrameSelectionTimeoutChange: (Int) -> Unit,
    onSaveSettings: () -> Unit
) {
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) { delay(3000); saved = false } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("VẬN HÀNH THIẾT BỊ", style = MaterialTheme.typography.overline, color = AccentNudeDark)
            Text("Cài đặt hệ thống Kiosk", style = MaterialTheme.typography.h5, color = NeutralText)
            Text("Điều chỉnh thiết bị và thời gian tương tác cho từng lượt chụp.", style = MaterialTheme.typography.body2, color = NeutralSecondary)
            Text("Khởi động lại ứng dụng sau khi lưu để áp dụng cấu hình.", style = MaterialTheme.typography.caption, color = AccentNudeDark,
                modifier = Modifier.background(AccentNudeLight, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 9.dp))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusCard("Nguồn Camera", if (useHotFolder) "Hot Folder" else if (nativeCamera != null) "Canon EDSDK" else "Webcam / DroidCam")
            StatusCard("Máy in Windows", if (enablePrint) "Đang bật" else "Đang tắt")
            StatusCard("Đồng bộ Đám mây", if (config.canUploadAlbum) "Sẵn sàng" else "Ngoại tuyến")
            StatusCard("Kiosk", "${config.boothId} · Port ${config.localServerPort}")
        }
        SettingsSection("In ấn & Xuất ảnh") {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Máy in Windows", style = MaterialTheme.typography.subtitle1, color = NeutralText)
                    Text("Gửi ảnh tới hộp thoại in Windows khi bật.", style = MaterialTheme.typography.body2, color = NeutralSecondary)
                }
                Switch(checked = enablePrint, onCheckedChange = onEnablePrintChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentNudeDark, checkedTrackAlpha = 1f))
            }
            if (config.printerNameStrip.isNotBlank() || config.printerNameFull.isNotBlank()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (config.printerNameStrip.isNotBlank()) InfoChip("Ảnh dải · ${config.printerNameStrip}")
                    if (config.printerNameFull.isNotBlank()) InfoChip("Ảnh đầy đủ · ${config.printerNameFull}")
                }
            }
        }
        SettingsSection("Nguồn Camera & Thu nhận ảnh") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CameraOption("Webcam / DroidCam", "Camera kết nối trực tiếp", !useHotFolder, { onUseHotFolderChange(false) }, Modifier.weight(1f))
                CameraOption("Thư mục ảnh (Hot Folder)", "Ảnh do phần mềm camera xuất ra", useHotFolder, { onUseHotFolderChange(true) }, Modifier.weight(1f))
            }
            AnimatedVisibility(useHotFolder, enter = expandVertically(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Spacer(Modifier.height(4.dp))
                    Text("Đường dẫn Hot Folder", style = MaterialTheme.typography.subtitle2)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = hotFolderPath, onValueChange = onHotFolderPathChange,
                            modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Chọn thư mục ảnh") })
                        OutlinedButton(onClick = {
                            val chooser = JFileChooser().apply {
                                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                dialogTitle = "Chọn Hot Folder"
                            }
                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) onHotFolderPathChange(chooser.selectedFile.absolutePath)
                        }, modifier = Modifier.height(56.dp)) { Text("Chọn thư mục") }
                    }
                    Text("Mẹo: Dùng digiCamControl chụp để ảnh rớt vào đây.", style = MaterialTheme.typography.caption, color = NeutralSecondary)
                }
            }
        }
        if (nativeCamera != null) SettingsSection("Thông số Canon EDSDK") {
            Text("Nếu thông số hiện N/A, hãy đảm bảo máy ảnh ở chế độ M (Manual) và không bị kẹt báo bận.", style = MaterialTheme.typography.body2, color = NeutralSecondary)
            Text("Phơi sáng (Exposure)", style = MaterialTheme.typography.subtitle2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NativeCameraParamDropdown("ISO", nativeCamera.getCurrentIso(), { nativeCamera.getAvailableIsoSpeeds() }, { nativeCamera.setIso(it) }, Modifier.weight(1f))
                NativeCameraParamDropdown("Khẩu độ (Av)", nativeCamera.getCurrentAperture(), { nativeCamera.getAvailableApertures() }, { nativeCamera.setAperture(it) }, Modifier.weight(1f))
                NativeCameraParamDropdown("Tốc độ (Tv)", nativeCamera.getCurrentShutterSpeed(), { nativeCamera.getAvailableShutterSpeeds() }, { nativeCamera.setShutterSpeed(it) }, Modifier.weight(1f))
            }
            Text("Màu sắc (Color) · Thiết lập 1 chiều", style = MaterialTheme.typography.subtitle2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HardcodedCameraParamDropdown("Cân bằng trắng (WB)", listOf(
                    "Tự động (Auto)" to "kEdsWhiteBalance_Auto", "Ánh sáng mặt trời (Daylight)" to "kEdsWhiteBalance_Daylight",
                    "Nhiều mây (Cloudy)" to "kEdsWhiteBalance_Cloudy", "Đèn tròn (Tungsten)" to "kEdsWhiteBalance_Tangsten",
                    "Đèn huỳnh quang (Fluorescent)" to "kEdsWhiteBalance_Fluorescent"
                ), { nativeCamera.setWhiteBalanceSafe(it) }, Modifier.weight(1f))
                HardcodedCameraParamDropdown("Kiểu màu (Picture Style)", listOf(
                    "Tiêu chuẩn (Standard)" to "kEdsPictureStyle_Standard", "Chân dung (Portrait)" to "kEdsPictureStyle_Portrait",
                    "Phong cảnh (Landscape)" to "kEdsPictureStyle_Landscape", "Trung tính (Neutral)" to "kEdsPictureStyle_Neutral",
                    "Chân thật (Faithful)" to "kEdsPictureStyle_Faithful", "Trắng đen (Monochrome)" to "kEdsPictureStyle_Monochrome",
                    "Tự động (Auto)" to "kEdsPictureStyle_Auto"
                ), { nativeCamera.setPictureStyleSafe(it) }, Modifier.weight(1f))
            }
        }
        SettingsSection("Thời gian tương tác tại Kiosk") {
            Text("Điều chỉnh thời gian để khách chọn ảnh và khung. 0 giây sẽ tự động tiếp tục.", style = MaterialTheme.typography.body2, color = NeutralSecondary)
            TimeoutStepper("Chọn ảnh", "Thời gian khách chọn ảnh", photoSelectionTimeoutSeconds, onPhotoSelectionTimeoutChange)
            Divider(color = NeutralBorder)
            TimeoutStepper("Chọn khung", "Thời gian khách chọn khung", frameSelectionTimeoutSeconds, onFrameSelectionTimeoutChange)
            Divider(color = NeutralBorder)
            TimeoutStepper("Sau khi chụp (dự phòng)", "Thời gian chờ sau lượt chụp", postCaptureTimeoutSeconds, onPostCaptureTimeoutChange)
        }
        Surface(shape = RoundedCornerShape(16.dp), color = NeutralPanelAlt, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Cấu hình bổ sung", style = MaterialTheme.typography.subtitle2)
                    Text("Tên máy in, Booth ID và Port được cấu hình trong tệp .env.", style = MaterialTheme.typography.body2, color = NeutralSecondary)
                    if (saved) Text("Đã lưu cài đặt", style = MaterialTheme.typography.caption, color = AccentMint)
                }
                Button(onClick = { onSaveSettings(); saved = true }, modifier = Modifier.height(56.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = AccentNudeDark, contentColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                    Text("Lưu cài đặt", style = MaterialTheme.typography.button)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, NeutralBorder)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.subtitle1, color = NeutralText)
            content()
        }
    }
}

@Composable
private fun StatusCard(label: String, value: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, NeutralBorder), modifier = Modifier.widthIn(min = 185.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.caption, color = NeutralSecondary)
            Text(value, style = MaterialTheme.typography.subtitle2, color = NeutralText)
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Text(text, style = MaterialTheme.typography.caption, color = AccentNudeDark,
        modifier = Modifier.background(AccentNudeLight, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 8.dp))
}

@Composable
private fun CameraOption(title: String, detail: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.background(if (selected) AccentNudeLight else NeutralPanelAlt, shape)
        .clickable(onClick = onClick).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (selected) "●  $title" else "○  $title", style = MaterialTheme.typography.subtitle2, color = if (selected) AccentNudeDark else NeutralText)
        Text(detail, style = MaterialTheme.typography.body2, color = NeutralSecondary)
    }
}

@Composable
private fun TimeoutStepper(label: String, detail: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.subtitle2)
            Text(detail, style = MaterialTheme.typography.body2, color = NeutralSecondary)
        }
        StepControl("−", value > 0) { onValueChange((value - 10).coerceAtLeast(0)) }
        Text("$value giây", style = MaterialTheme.typography.subtitle1, textAlign = TextAlign.Center,
            modifier = Modifier.width(120.dp).padding(horizontal = 6.dp).background(NeutralPanelAlt, RoundedCornerShape(10.dp)).padding(vertical = 14.dp))
        StepControl("+", value < 3600) { onValueChange((value + 10).coerceAtMost(3600)) }
    }
}

@Composable
private fun StepControl(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(52.dp),
        shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(0.dp), border = BorderStroke(1.dp, NeutralBorder)) {
        Text(symbol, style = MaterialTheme.typography.h6)
    }
}
