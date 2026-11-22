package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.config.BoothOperatingMode
import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import com.phuctran.photobooth.desktop.services.NativeEosCaptureService
import com.phuctran.photobooth.desktop.ui.components.KioskIconButton
import com.phuctran.photobooth.desktop.ui.components.KioskPrimaryButton
import com.phuctran.photobooth.desktop.ui.components.KioskSecondaryButton
import com.phuctran.photobooth.desktop.ui.components.kioskHorizontalDrag
import com.phuctran.photobooth.desktop.ui.components.kioskVerticalDrag
import com.phuctran.photobooth.desktop.ui.theme.*
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser

private enum class TelegramTestStatus {
    IDLE,
    SENDING,
    SUCCESS,
    FAILED
}

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
    operatingMode: BoothOperatingMode = config.operatingMode,
    onOperatingModeChange: (BoothOperatingMode) -> Unit = {},
    maxVisibleFilters: Int = config.maxVisibleFilters,
    onMaxVisibleFiltersChange: (Int) -> Unit = {},
    standaloneInactivityTimeoutSeconds: Int = config.standaloneInactivityTimeoutSeconds,
    onStandaloneInactivityTimeoutChange: (Int) -> Unit = {},
    enableTelegramAlerts: Boolean = config.enableTelegramAlerts,
    onEnableTelegramAlertsChange: (Boolean) -> Unit = {},
    telegramBotToken: String = config.telegramBotToken,
    onTelegramBotTokenChange: (String) -> Unit = {},
    telegramChatId: String = config.telegramChatId,
    onTelegramChatIdChange: (String) -> Unit = {},
    onTestTelegramAlert: (String, String) -> CompletableFuture<Boolean> = { _, _ -> CompletableFuture.completedFuture(false) },
    onSaveSettings: () -> Unit
) {
    var saved by remember { mutableStateOf(false) }
    var showTokenPlaintext by remember { mutableStateOf(false) }
    var telegramTestStatus by remember { mutableStateOf(TelegramTestStatus.IDLE) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(saved) { if (saved) { delay(3000); saved = false } }

    val modeDisplayLabel = when (operatingMode) {
        BoothOperatingMode.COMMERCIAL -> "Thương mại (QR PayOS)"
        BoothOperatingMode.RENTAL -> "Sự kiện (Gói thuê miễn phí)"
        BoothOperatingMode.STANDALONE_KIOSK -> "Kiosk cố định (Tự vận hành)"
    }

    val scrollState = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scrollState).kioskVerticalDrag(scrollState), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("VẬN HÀNH THIẾT BỊ", style = MaterialTheme.typography.overline, color = AccentNudeDark)
            Text("Cài đặt hệ thống Kiosk", style = MaterialTheme.typography.h5, color = NeutralText)
            Text("Điều chỉnh thiết bị và thời gian tương tác cho từng lượt chụp.", style = MaterialTheme.typography.body2, color = NeutralSecondary)
            Text("Khởi động lại ứng dụng sau khi lưu để áp dụng cấu hình.", style = MaterialTheme.typography.caption, color = AccentNudeDark,
                modifier = Modifier.background(AccentNudeLight, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 9.dp))
        }
        val statusScroll = rememberScrollState()
        Row(Modifier.fillMaxWidth().horizontalScroll(statusScroll).kioskHorizontalDrag(statusScroll), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusCard("Chế độ Kiosk", modeDisplayLabel)
            StatusCard("Nguồn Camera", if (useHotFolder) "Hot Folder" else if (nativeCamera != null) "Canon EDSDK" else "Webcam / DroidCam")
            StatusCard("Máy in Windows", if (enablePrint) "Đang bật" else "Đang tắt")
            StatusCard("Đồng bộ Đám mây", if (config.canUploadAlbum) "Sẵn sàng" else "Ngoại tuyến")
            StatusCard("Kiosk", "${config.boothId} · Port ${config.localServerPort}")
        }
        SettingsSection("Chế độ vận hành Kiosk") {
            Text(
                "Chọn mô hình vận hành phù hợp với địa điểm đặt máy. Chế độ Kiosk cố định sẽ đẩy bước chọn khung trang trí lên đầu quy trình và bật bộ đếm tự động thu hồi phiên.",
                style = MaterialTheme.typography.body2,
                color = NeutralSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OperatingModeOption(
                    title = "Thương mại (QR PayOS)",
                    detail = "Chọn Bố cục & Màu -> Thanh toán PayOS -> Chụp -> Chọn ảnh & Khung.",
                    selected = operatingMode == BoothOperatingMode.COMMERCIAL,
                    onClick = { onOperatingModeChange(BoothOperatingMode.COMMERCIAL) },
                    modifier = Modifier.weight(1f)
                )
                OperatingModeOption(
                    title = "Sự kiện (Gói thuê miễn phí)",
                    detail = "Bỏ qua thanh toán PayOS. Khách chụp & in ảnh miễn phí tại sự kiện.",
                    selected = operatingMode == BoothOperatingMode.RENTAL,
                    onClick = { onOperatingModeChange(BoothOperatingMode.RENTAL) },
                    modifier = Modifier.weight(1f)
                )
                OperatingModeOption(
                    title = "Kiosk cố định (Tự vận hành)",
                    detail = "Chọn Bố cục -> Khung -> Màu ngay từ đầu, cảnh báo Telegram & tự reset khi vắng khách.",
                    selected = operatingMode == BoothOperatingMode.STANDALONE_KIOSK,
                    onClick = { onOperatingModeChange(BoothOperatingMode.STANDALONE_KIOSK) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (operatingMode == BoothOperatingMode.RENTAL) {
                Text(
                    "✨ Đang bật chế độ sự kiện: Màn hình chọn số lượng sẽ hiển thị 'Miễn phí' và tự động bỏ qua bước thanh toán.",
                    style = MaterialTheme.typography.caption,
                    color = AccentNudeDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentNudeLight, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            AnimatedVisibility(
                visible = operatingMode == BoothOperatingMode.STANDALONE_KIOSK,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "✨ Đang bật chế độ Kiosk cố định: Khách xem & chọn mẫu khung trang trí trước khi thanh toán; sau khi chọn ảnh sẽ chuyển thẳng sang màn hình xác nhận in.",
                        style = MaterialTheme.typography.caption,
                        color = AccentNudeDark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AccentNudeLight, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                    Divider(color = NeutralBorder)
                    StandaloneTimeoutStepper(
                        label = "Tự động thu hồi phiên khi không tương tác",
                        detail = "Nếu khách rời đi hoặc không chạm màn hình trong khoảng thời gian này (30–300 giây), Kiosk tự quay về màn hình chờ và xóa dữ liệu tạm.",
                        value = standaloneInactivityTimeoutSeconds,
                        onValueChange = { onStandaloneInactivityTimeoutChange(it.coerceIn(30, 300)) }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = operatingMode == BoothOperatingMode.STANDALONE_KIOSK,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            SettingsSection("Cảnh báo Telegram Bot (Kiosk cố định)") {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bật cảnh báo Telegram", style = MaterialTheme.typography.subtitle1, color = NeutralText)
                        Text(
                            "Tự động gửi thông báo đơn hàng đã thanh toán (mã đơn, bố cục, số bản in, doanh thu) về nhóm Telegram quản lý.",
                            style = MaterialTheme.typography.body2,
                            color = NeutralSecondary
                        )
                    }
                    Switch(
                        checked = enableTelegramAlerts,
                        onCheckedChange = onEnableTelegramAlertsChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentNudeDark,
                            checkedTrackAlpha = 1f,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = NeutralBorder,
                            uncheckedTrackAlpha = 1f
                        )
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = telegramBotToken,
                        onValueChange = {
                            onTelegramBotTokenChange(it)
                            telegramTestStatus = TelegramTestStatus.IDLE
                        },
                        modifier = Modifier.weight(1.3f),
                        singleLine = true,
                        label = { Text("Bot Token") },
                        placeholder = { Text("123456789:ABCdefGHIjklMNOpqrSTUvwxYZ") },
                        visualTransformation = if (showTokenPlaintext) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showTokenPlaintext = !showTokenPlaintext }) {
                                Text(if (showTokenPlaintext) "Ẩn" else "Hiện", style = MaterialTheme.typography.caption, color = AccentNudeDark)
                            }
                        }
                    )
                    OutlinedTextField(
                        value = telegramChatId,
                        onValueChange = {
                            onTelegramChatIdChange(it)
                            telegramTestStatus = TelegramTestStatus.IDLE
                        },
                        modifier = Modifier.weight(0.9f),
                        singleLine = true,
                        label = { Text("Chat ID") },
                        placeholder = { Text("-1001234567890") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    KioskSecondaryButton(
                        text = "Gửi tin nhắn thử nghiệm",
                        enabled = telegramTestStatus != TelegramTestStatus.SENDING &&
                            telegramBotToken.isNotBlank() &&
                            telegramChatId.isNotBlank(),
                        onClick = {
                            telegramTestStatus = TelegramTestStatus.SENDING
                            coroutineScope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching { onTestTelegramAlert(telegramBotToken, telegramChatId).get() }.getOrDefault(false)
                                }
                                telegramTestStatus = if (ok) TelegramTestStatus.SUCCESS else TelegramTestStatus.FAILED
                            }
                        }
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        when (telegramTestStatus) {
                            TelegramTestStatus.IDLE -> Unit
                            TelegramTestStatus.SENDING -> Text(
                                text = "Đang gửi tin nhắn thử nghiệm...",
                                style = MaterialTheme.typography.body2,
                                color = NeutralSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            TelegramTestStatus.SUCCESS -> Text(
                                text = "✓ Đã gửi tin nhắn thử nghiệm thành công!",
                                style = MaterialTheme.typography.body2,
                                color = AccentMint,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            TelegramTestStatus.FAILED -> Text(
                                text = "✗ Gửi thất bại. Vui lòng kiểm tra Bot Token và Chat ID.",
                                style = MaterialTheme.typography.body2,
                                color = AccentRed,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        SettingsSection("Giới hạn bộ lọc màu hiển thị") {
            FilterCountStepper(
                label = "Số lượng bộ lọc hiển thị cho khách",
                detail = "Giới hạn số lượng bộ lọc màu đang bật được hiển thị trên thanh chọn màu (tối đa từ 1 đến 10 bộ lọc, mặc định 3).",
                value = maxVisibleFilters.coerceIn(1, 10),
                onValueChange = { onMaxVisibleFiltersChange(it.coerceIn(1, 10)) }
            )
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
                val chipsScroll = rememberScrollState()
                Row(Modifier.horizontalScroll(chipsScroll).kioskHorizontalDrag(chipsScroll), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (config.printerNameStrip.isNotBlank()) InfoChip("Ảnh dải · ${config.printerNameStrip}")
                    if (config.printerNameFull.isNotBlank()) InfoChip("Ảnh đầy đủ · ${config.printerNameFull}")
                }
            }
        }
        SettingsSection("Nguồn Camera & Thu nhận ảnh") {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                        KioskSecondaryButton(
                            text = "Chọn thư mục",
                            onClick = {
                                val chooser = JFileChooser().apply {
                                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                    dialogTitle = "Chọn Hot Folder"
                                }
                                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) onHotFolderPathChange(chooser.selectedFile.absolutePath)
                            }
                        )
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Cấu hình bổ sung", style = MaterialTheme.typography.subtitle2)
                    Text("Tên máy in, Booth ID và Port được cấu hình trong tệp .env.", style = MaterialTheme.typography.body2, color = NeutralSecondary)
                    if (saved) Text("Đã lưu cài đặt", style = MaterialTheme.typography.caption, color = AccentMint)
                }
                KioskPrimaryButton(
                    text = "Lưu cài đặt",
                    onClick = { onSaveSettings(); saved = true }
                )
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
            Text(label, style = MaterialTheme.typography.caption, color = NeutralSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.subtitle2, color = NeutralText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption,
        color = AccentNudeDark,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.background(AccentNudeLight, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun OperatingModeOption(title: String, detail: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(if (selected) AccentNudeLight else NeutralPanelAlt, shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) AccentNude else NeutralBorder,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = if (selected) "●  $title" else "○  $title",
            style = MaterialTheme.typography.subtitle2,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) AccentNudeDark else NeutralText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(detail, style = MaterialTheme.typography.body2, color = NeutralSecondary)
    }
}

@Composable
private fun CameraOption(title: String, detail: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(if (selected) AccentNudeLight else NeutralPanelAlt, shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) AccentNude else NeutralBorder,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = if (selected) "●  $title" else "○  $title",
            style = MaterialTheme.typography.subtitle2,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) AccentNudeDark else NeutralText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(detail, style = MaterialTheme.typography.body2, color = NeutralSecondary)
    }
}

@Composable
private fun StandaloneTimeoutStepper(label: String, detail: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.subtitle2)
            Text(detail, style = MaterialTheme.typography.body2, color = NeutralSecondary)
        }
        Spacer(Modifier.width(12.dp))
        StepControl("−", value > 30) { onValueChange((value - 15).coerceAtLeast(30)) }
        Text(
            text = "$value giây",
            style = MaterialTheme.typography.subtitle1,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .width(120.dp)
                .padding(horizontal = 6.dp)
                .background(NeutralPanelAlt, RoundedCornerShape(10.dp))
                .padding(vertical = 14.dp)
        )
        StepControl("+", value < 300) { onValueChange((value + 15).coerceAtMost(300)) }
    }
}

@Composable
private fun FilterCountStepper(label: String, detail: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.subtitle2)
            Text(detail, style = MaterialTheme.typography.body2, color = NeutralSecondary)
        }
        Spacer(Modifier.width(12.dp))
        StepControl("−", value > 1) { onValueChange((value - 1).coerceAtLeast(1)) }
        Text(
            text = "$value bộ lọc",
            style = MaterialTheme.typography.subtitle1,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .width(120.dp)
                .padding(horizontal = 6.dp)
                .background(NeutralPanelAlt, RoundedCornerShape(10.dp))
                .padding(vertical = 14.dp)
        )
        StepControl("+", value < 10) { onValueChange((value + 1).coerceAtMost(10)) }
    }
}

@Composable
private fun TimeoutStepper(label: String, detail: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.subtitle2)
            Text(detail, style = MaterialTheme.typography.body2, color = NeutralSecondary)
        }
        Spacer(Modifier.width(12.dp))
        StepControl("−", value > 0) { onValueChange((value - 10).coerceAtLeast(0)) }
        Text(
            text = "$value giây",
            style = MaterialTheme.typography.subtitle1,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .width(120.dp)
                .padding(horizontal = 6.dp)
                .background(NeutralPanelAlt, RoundedCornerShape(10.dp))
                .padding(vertical = 14.dp)
        )
        StepControl("+", value < 3600) { onValueChange((value + 10).coerceAtMost(3600)) }
    }
}

@Composable
private fun StepControl(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    KioskIconButton(
        onClick = onClick,
        enabled = enabled,
        size = 52.dp,
        palette = SecondaryGlassPalette,
        onClickLabel = symbol
    ) {
        Text(symbol, style = MaterialTheme.typography.h6, color = if (enabled) NeutralText else NeutralMuted)
    }
}
