package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.IntrinsicSize
import com.phuctran.photobooth.desktop.services.NativeEosCaptureService
import com.phuctran.photobooth.desktop.ui.components.*
import com.phuctran.photobooth.desktop.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.phuctran.photobooth.desktop.model.DefaultLayoutModes
import com.phuctran.photobooth.desktop.model.LayoutMode
import com.phuctran.photobooth.desktop.model.FramePack
import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import com.phuctran.photobooth.desktop.data.SalesRepository
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.withContext

@Composable
fun AdminScreen(
    layouts: List<LayoutMode>,
    frames: List<FramePack>,
    effects: List<com.phuctran.photobooth.desktop.model.EffectMode>,
    config: DesktopBoothConfig,
    nativeCamera: NativeEosCaptureService?,
    salesRepository: SalesRepository,
    onAddFrame: (String) -> Unit,
    onSaveLayoutConfig: (String, Long, Int, Int) -> Unit = { _, _, _, _ -> },
    onDeleteLayout: (String) -> Unit,
    onSaveSettings: (Boolean, Boolean, String, Int, Int, Int) -> Unit,
    onSaveEffects: (List<com.phuctran.photobooth.desktop.model.EffectMode>) -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }


    val projectDir = com.phuctran.photobooth.desktop.config.DesktopAppPaths.appDataDir()
    val frameStore = remember { com.phuctran.photobooth.desktop.storage.FrameStore(projectDir) }
    var adminFrames by remember { mutableStateOf(emptyList<FramePack>()) }

    fun refreshAdminFrames() {
        adminFrames = frameStore.loadFrames().filter { it.isCustom }
    }

    LaunchedEffect(Unit) {
        refreshAdminFrames()
    }

    var enablePrint by remember { mutableStateOf(config.enableSystemPrint) }
    var useHotFolder by remember { mutableStateOf(config.useHotFolder) }
    var hotFolderPath by remember { mutableStateOf(config.hotFolderPath) }
    var postCaptureTimeoutSeconds by remember { mutableIntStateOf(config.postCaptureTimeoutSeconds) }
    var photoSelectionTimeoutSeconds by remember { mutableIntStateOf(config.photoSelectionTimeoutSeconds) }
    var frameSelectionTimeoutSeconds by remember { mutableIntStateOf(config.frameSelectionTimeoutSeconds) }

    fun saveSettings() {
        onSaveSettings(enablePrint, useHotFolder, hotFolderPath, postCaptureTimeoutSeconds, photoSelectionTimeoutSeconds, frameSelectionTimeoutSeconds)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralBg)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                KioskSecondaryButton("← Thoát Admin", {
                saveSettings()
                onBack() 
                }, modifier = Modifier.width(170.dp))
                Column {
                    Text("Quản trị Le Souvenir", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold, color = NeutralText)
                    Text("Kiểm tra nội dung, thiết bị và cấu hình vận hành.", color = NeutralSecondary, style = MaterialTheme.typography.body2.copy(fontSize = 13.sp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusChip("Camera", if (useHotFolder) "Hot folder" else "Webcam", AccentBlue)
                StatusChip("Print", if (enablePrint) "Bật" else "Tắt", if (enablePrint) AccentMint else NeutralMuted)
                StatusChip("Cloud Sync", if (config.canUploadAlbum) "Sẵn sàng" else "Local", if (config.canUploadAlbum) AccentMint else AccentAmber)
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            backgroundColor = NeutralPanel,
            contentColor = MaterialTheme.colors.primary,
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, NeutralBorder, RoundedCornerShape(14.dp)),
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colors.primary
                )
            }
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("Khung", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("Bố cục", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) { Text("Cài đặt", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }) { Text("Công cụ", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }
            Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }) { Text("Màu ảnh", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }
            Tab(selected = selectedTab == 5, onClick = { selectedTab = 5 }) { Text("Doanh thu", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (selectedTab == 5) {
                RevenueScreen(salesRepository, projectDir)
            } else if (selectedTab == 0) {
                // Frames
                Column(Modifier.fillMaxSize()) {
                    SectionHeader("Admin", "Quản lý khung ảnh", "Xem, lọc và xóa các khung ảnh đã lưu trên hệ thống.")
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val standardFrames = adminFrames.filter { !it.isSpecial }
                        val specialFrames = adminFrames.filter { it.isSpecial }
                        
                        if (standardFrames.isNotEmpty()) {
             Text("Khung Thường", style = MaterialTheme.typography.h5, color = NeutralText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                            val groupedBySize = standardFrames.groupBy { it.targetPrintSize ?: "Kích thước Khác" }
                            groupedBySize.forEach { (size, framesBySize) ->
                                Text("Khổ in: $size", style = MaterialTheme.typography.h6, color = AccentNudeDark, fontWeight = FontWeight.Bold)
                                
                                val groupedByLayout = framesBySize.groupBy { it.targetLayoutId ?: "Bố cục Khác" }
                                groupedByLayout.forEach { (layout, framesByLayout) ->
                                    Text("Bố cục: $layout", style = MaterialTheme.typography.subtitle1, color = NeutralSecondary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                                    
                                    framesByLayout.forEach { frame -> 
                                        Box(modifier = Modifier.padding(start = 32.dp, top = 8.dp, bottom = 8.dp)) {
                                            FrameAdminCard(frame) {
                                                if (frame.customImagePath != null) {
                                                    java.nio.file.Files.deleteIfExists(frame.customImagePath)
                                                    refreshAdminFrames()
                                                }
                                            }
                                        }
                                    }
                                }
                                Divider(color = NeutralBorder, modifier = Modifier.padding(vertical = 16.dp))
                            }
                        }
                        
                        if (specialFrames.isNotEmpty()) {
                             Text("Khung Sự Kiện Đặc Biệt", style = MaterialTheme.typography.h5, color = NeutralText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp, top = 16.dp))
                            val groupedByEvent = specialFrames.groupBy { it.specialEventName ?: "Sự Kiện Khác" }
                            groupedByEvent.forEach { (eventName, framesByEvent) ->
                                EventAdminCard(
                                    eventName = eventName,
                                    frames = framesByEvent,
                                    frameStore = frameStore,
                                    onRefresh = { refreshAdminFrames() }
                                )
                            }
                        }
                        
                        if (standardFrames.isEmpty() && specialFrames.isEmpty()) {
                            Text("Chưa có khung ảnh custom nào.", color = NeutralSecondary, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            } else if (selectedTab == 1) {
                // Layouts
                Column(Modifier.fillMaxSize()) {
                    SectionHeader("Admin", "Quản lý bố cục", "Xem các bố cục ảnh đang khả dụng trên hệ thống.")
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (layouts.isEmpty()) {
                            Text("Chưa có bố cục nào được tải.", color = NeutralSecondary, modifier = Modifier.padding(16.dp))
                        } else {
                            var editingLayout by remember { mutableStateOf<LayoutMode?>(null) }
                            
                            layouts.forEach { layout -> 
                                LayoutAdminCard(
                                    layout = layout, 
                                    onEdit = { editingLayout = layout },
                                    onDelete = { onDeleteLayout(layout.id) }
                                )
                            }
                            
                            editingLayout?.let { layoutToEdit ->
                                LayoutEditDialog(
                                    layout = layoutToEdit,
                                    onDismiss = { editingLayout = null },
                                    onSave = { price, shotCount, countdown ->
                                        onSaveLayoutConfig(layoutToEdit.id, price, shotCount, countdown)
                                        editingLayout = null
                                    }
                                )
                            }
                        }
                    }
                }
            } else if (selectedTab == 2) {

                // Settings
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AdminTokens.sectionGap)) {
                    SectionHeader("Admin", "Cài đặt thiết bị", "Bạn cần khởi động lại ứng dụng sau khi lưu để áp dụng.")

                    SettingsGroup("IN ẢNH") {
                        SettingsRow("Máy in Windows", "Gửi ảnh tới hộp thoại in Windows khi bật.") {
                            Switch(
                                checked = enablePrint,
                                onCheckedChange = { enablePrint = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentNude, checkedTrackAlpha = 1f)
                            )
                        }
                    }

                    SettingsGroup("NGUỒN CAMERA") {
                        ChoiceRow("Webcam / DroidCam", "Dùng camera kết nối trực tiếp.", !useHotFolder, AccentNude, { useHotFolder = false })
                        Spacer(Modifier.height(10.dp))
                        ChoiceRow("Thư mục ảnh (Hot Folder)", "Lấy ảnh từ thư mục do phần mềm camera xuất ra.", useHotFolder, AccentNude, { useHotFolder = true })
                        AnimatedVisibility(visible = useHotFolder, enter = expandVertically(), exit = shrinkVertically()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Spacer(Modifier.height(14.dp))
                                Text("Đường dẫn Hot Folder", style = MaterialTheme.typography.body2, color = NeutralSecondary)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = hotFolderPath,
                                        onValueChange = { hotFolderPath = it },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        placeholder = { Text("Chọn thư mục ảnh") },
                                        colors = TextFieldDefaults.outlinedTextFieldColors(textColor = NeutralText)
                                    )
                                    KioskSecondaryButton("Chọn thư mục", onClick = {
                                        val chooser = javax.swing.JFileChooser().apply {
                                            fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                                            dialogTitle = "Chọn Hot Folder"
                                        }
                                        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                                            hotFolderPath = chooser.selectedFile.absolutePath
                                        }
                                    })
                                }
                                Text("Mẹo: Dùng digiCamControl chụp để ảnh rớt vào đây.", color = NeutralMuted, style = MaterialTheme.typography.caption)
                            }
                        }
                    }

                    if (nativeCamera != null) {
                        SettingsGroup("THÔNG SỐ CANON EDSDK") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Ghi chú: Nếu thông số hiện N/A, hãy đảm bảo máy ảnh đang ở chế độ M (Manual) và không bị kẹt báo bận.", color = NeutralMuted, style = MaterialTheme.typography.caption)
                                
                                // Phơi sáng (Exposure)
                                Text("Phơi sáng (Exposure)", fontWeight = FontWeight.Medium, color = NeutralText, modifier = Modifier.padding(top = 8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                    NativeCameraParamDropdown(
                                        label = "ISO", 
                                        currentValue = nativeCamera.getCurrentIso(),
                                        fetchOptions = { nativeCamera.getAvailableIsoSpeeds() },
                                        onSelect = { nativeCamera.setIso(it) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    NativeCameraParamDropdown(
                                        label = "Khẩu độ (Av)", 
                                        currentValue = nativeCamera.getCurrentAperture(),
                                        fetchOptions = { nativeCamera.getAvailableApertures() },
                                        onSelect = { nativeCamera.setAperture(it) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    NativeCameraParamDropdown(
                                        label = "Tốc độ (Tv)", 
                                        currentValue = nativeCamera.getCurrentShutterSpeed(),
                                        fetchOptions = { nativeCamera.getAvailableShutterSpeeds() },
                                        onSelect = { nativeCamera.setShutterSpeed(it) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // Màu sắc (Color)
                                Text("Màu sắc (Color) - (Thiết lập 1 chiều)", fontWeight = FontWeight.Medium, color = NeutralText, modifier = Modifier.padding(top = 8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                    HardcodedCameraParamDropdown(
                                        label = "Cân bằng trắng (WB)", 
                                        options = listOf(
                                            "Tự động (Auto)" to "kEdsWhiteBalance_Auto",
                                            "Ánh sáng mặt trời (Daylight)" to "kEdsWhiteBalance_Daylight",
                                            "Nhiều mây (Cloudy)" to "kEdsWhiteBalance_Cloudy",
                                            "Đèn tròn (Tungsten)" to "kEdsWhiteBalance_Tangsten",
                                            "Đèn huỳnh quang (Fluorescent)" to "kEdsWhiteBalance_Fluorescent"
                                        ),
                                        onSelect = { nativeCamera.setWhiteBalanceSafe(it) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    HardcodedCameraParamDropdown(
                                        label = "Kiểu màu (Picture Style)", 
                                        options = listOf(
                                            "Tiêu chuẩn (Standard)" to "kEdsPictureStyle_Standard",
                                            "Chân dung (Portrait)" to "kEdsPictureStyle_Portrait",
                                            "Phong cảnh (Landscape)" to "kEdsPictureStyle_Landscape",
                                            "Trung tính (Neutral)" to "kEdsPictureStyle_Neutral",
                                            "Chân thật (Faithful)" to "kEdsPictureStyle_Faithful",
                                            "Trắng đen (Monochrome)" to "kEdsPictureStyle_Monochrome",
                                            "Tự động (Auto)" to "kEdsPictureStyle_Auto"
                                        ),
                                        onSelect = { nativeCamera.setPictureStyleSafe(it) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    SettingsGroup("THỜI GIAN LỰA CHỌN") {
                        Text("Điều chỉnh thời gian để khách chọn ảnh và khung. 0 giây sẽ tự động tiếp tục.", style = MaterialTheme.typography.body2, color = NeutralSecondary)
                        NumberStepper("Chọn ảnh", photoSelectionTimeoutSeconds, { photoSelectionTimeoutSeconds = it }, "Thời gian khách chọn ảnh")
                        SettingsDivider()
                        NumberStepper("Chọn khung", frameSelectionTimeoutSeconds, { frameSelectionTimeoutSeconds = it }, "Thời gian khách chọn khung")
                        SettingsDivider()
                        NumberStepper("Sau khi chụp (dự phòng)", postCaptureTimeoutSeconds, { postCaptureTimeoutSeconds = it }, "Thời gian chờ sau lượt chụp")
                    }

                    SettingsGroup("LƯU CẤU HÌNH") {
                        KioskPrimaryButton("Lưu cài đặt", onClick = { saveSettings() }, modifier = Modifier.width(180.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            } else if (selectedTab == 3) {
                // Calculator App (Firebase Tool)
                com.phuctran.photobooth.desktop.engine.CalculatorApp(config, layouts)
            } else if (selectedTab == 4) {
                FilterAdminView(effects = effects, onSaveEffects = onSaveEffects)
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NeutralPanel,
        shape = RoundedCornerShape(AdminTokens.groupCornerRadius),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeutralBorder.copy(alpha = AdminTokens.dividerAlpha)),
        elevation = 0.dp
    ) {
        Column(Modifier.padding(AdminTokens.groupPadding)) {
            Text(title, style = MaterialTheme.typography.overline, color = AdminGroupLabelColor)
            Spacer(Modifier.height(AdminTokens.groupHeaderBottomGap))
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    Divider(color = NeutralBorder.copy(alpha = AdminTokens.dividerAlpha))
}

@Composable
private fun SettingsRow(label: String, description: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = AdminTokens.rowMinHeight)
            .padding(horizontal = AdminTokens.rowHorizontalPadding, vertical = AdminTokens.rowVerticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.body1, color = NeutralText, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.body2, color = NeutralSecondary)
        }
        Spacer(Modifier.width(16.dp))
        control()
    }
}

@Composable
private fun NumberStepper(label: String, value: Int, onValueChange: (Int) -> Unit, description: String = "") {
    SettingsRow(label, description) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepperButton("−", value > 0) { onValueChange((value - 10).coerceAtLeast(0)) }
            Text("$value giây", style = MaterialTheme.typography.subtitle1, color = NeutralText, textAlign = TextAlign.Center, modifier = Modifier.width(88.dp))
            StepperButton("+", value < 3600) { onValueChange((value + 10).coerceAtMost(3600)) }
        }
    }
}

@Composable
private fun StepperButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(backgroundColor = AccentNudeLight, contentColor = AccentNudeDark, disabledContentColor = NeutralMuted),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeutralBorder),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(symbol, style = MaterialTheme.typography.h6)
    }
}

@Composable
fun NativeCameraParamDropdown(
    label: String,
    currentValue: String,
    fetchOptions: () -> List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf(currentValue) }
    var options by remember { mutableStateOf(emptyList<String>()) }
    
    // Load options initially to avoid empty list on first render
    LaunchedEffect(Unit) {
        options = fetchOptions()
    }

    // Update current value if it changes from outside
    LaunchedEffect(currentValue) {
        current = currentValue
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, NeutralBorder, RoundedCornerShape(8.dp))
                .background(Color.White)
                .clickable { 
                    options = fetchOptions()
                    expanded = true 
                }
                .padding(12.dp)
        ) {
            Text(label, color = NeutralMuted, style = MaterialTheme.typography.caption, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = current.takeIf { it.isNotEmpty() } ?: "N/A", 
                color = if (current.isEmpty()) NeutralMuted else NeutralText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(IntrinsicSize.Max)
        ) {
            if (options.isEmpty()) {
                DropdownMenuItem(onClick = { expanded = false }) {
                    Text("Không có lựa chọn (Lỗi hoặc bị khóa)", color = NeutralMuted)
                }
            } else {
                options.forEach { option ->
                    DropdownMenuItem(onClick = {
                        onSelect(option)
                        current = option
                        expanded = false
                    }) {
                        Text(option)
                    }
                }
            }
        }
    }
}

@Composable
fun HardcodedCameraParamDropdown(
    label: String,
    options: List<Pair<String, String>>, // Label to EnumName
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var currentLabel by remember { mutableStateOf("Chưa đồng bộ (Áp dụng trên máy)") }
    
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, NeutralBorder, RoundedCornerShape(8.dp))
                .background(Color.White)
                .clickable { expanded = true }
                .padding(12.dp)
        ) {
            Text(label, color = NeutralMuted, style = MaterialTheme.typography.caption, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = currentLabel, 
                color = NeutralText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.width(IntrinsicSize.Max)) {
            options.forEach { (optLabel, optValue) ->
                DropdownMenuItem(onClick = {
                    onSelect(optValue)
                    currentLabel = optLabel
                    expanded = false
                }) {
                    Text(optLabel)
                }
            }
        }
    }
}

@Composable
fun FrameAdminCard(frame: com.phuctran.photobooth.desktop.model.FramePack, onDelete: () -> Unit) {
    androidx.compose.material.Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, NeutralBorder, RoundedCornerShape(14.dp)),
        backgroundColor = NeutralPanel,
        elevation = 0.dp,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (frame.customImagePath != null) {
                var imageBitmap by remember(frame.customImagePath) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                
                LaunchedEffect(frame.customImagePath) {
                    imageBitmap = withContext(Dispatchers.IO) { loadFrameThumbnail(frame.customImagePath) }
                }
                
                if (imageBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = imageBitmap!!,
                        contentDescription = frame.title,
                        modifier = Modifier.size(80.dp, 100.dp).clip(RoundedCornerShape(10.dp)).background(NeutralPanelAlt),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Box(modifier = Modifier.height(100.dp).width(80.dp).background(NeutralPanelAlt, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentNude, modifier = Modifier.size(24.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(frame.title, color = NeutralText, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoPill(frame.targetPrintSize ?: "Khổ khác", bgColor = NeutralPanelAlt, textColor = NeutralSecondary)
                    InfoPill(frame.targetLayoutId ?: "Layout khác", bgColor = AccentNudeLight, textColor = AccentNudeDark)
                    if (frame.isSpecial) InfoPill("Sự kiện", bgColor = AccentNudeLight, textColor = AccentNudeDark)
                }
                if (frame.customImagePath != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(frame.customImagePath.toString(), color = NeutralMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (frame.customImagePath != null) {
                androidx.compose.material.OutlinedButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(10.dp),
                    colors = androidx.compose.material.ButtonDefaults.outlinedButtonColors(contentColor = AccentRed, backgroundColor = Color.Transparent)
                ) {
                    Text("Xóa", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LayoutEditDialog(layout: LayoutMode, onDismiss: () -> Unit, onSave: (Long, Int, Int) -> Unit) {
    var priceStr by remember { mutableStateOf(layout.basePrice.toString()) }
    var shotCountStr by remember { mutableStateOf(layout.shotCount.toString()) }
    var countdownStr by remember { mutableStateOf(layout.countdownSeconds.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        backgroundColor = NeutralPanel,
        title = { Text("Cài đặt Bố cục", style = MaterialTheme.typography.h5, color = NeutralText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Giá tiền (VNĐ)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(textColor = NeutralText, focusedBorderColor = AccentNude)
                )
                OutlinedTextField(
                    value = shotCountStr,
                    onValueChange = { shotCountStr = it },
                    label = { Text("Số ảnh chụp") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(textColor = NeutralText, focusedBorderColor = AccentNude)
                )
                OutlinedTextField(
                    value = countdownStr,
                    onValueChange = { countdownStr = it },
                    label = { Text("Thời gian đếm ngược (giây)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(textColor = NeutralText, focusedBorderColor = AccentNude)
                )
            }
        },
        confirmButton = {
            Button(colors = ButtonDefaults.buttonColors(backgroundColor = AccentNude, contentColor = Color.White), onClick = {
                val p = priceStr.toLongOrNull() ?: layout.basePrice
                val s = shotCountStr.toIntOrNull() ?: layout.shotCount
                val c = countdownStr.toIntOrNull() ?: layout.countdownSeconds
                onSave(p, s, c)
            }) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = NeutralSecondary)
            }
        }
    )
}

@Composable
fun LayoutAdminCard(layout: LayoutMode, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    androidx.compose.material.Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, NeutralBorder, RoundedCornerShape(14.dp)),
        backgroundColor = NeutralPanel,
        elevation = 0.dp,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniLayoutPreview(layout = layout, modifier = Modifier.height(96.dp).width(112.dp).background(NeutralPanelAlt, RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(layout.title, color = NeutralText, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Layout ID: ${layout.id}", color = NeutralSecondary, fontSize = 12.sp)
                 Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      InfoPill("Khổ ${layout.printSizeLabel}")
                     InfoPill("Chụp ${layout.shotCount}")
                     InfoPill("Chọn ${layout.selectCount}")
                     InfoPill("Đếm ${layout.countdownSeconds}s")
                      InfoPill("Giá ${layout.basePrice}đ", bgColor = AccentNudeLight, textColor = AccentNudeDark)
                 }
                Text(layout.description, color = NeutralMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (showConfirm) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Xác nhận xóa bố cục này?", color = AccentRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    androidx.compose.material.OutlinedButton(
                        onClick = { showConfirm = false },
                        colors = androidx.compose.material.ButtonDefaults.outlinedButtonColors(contentColor = NeutralSecondary)
                    ) {
                        Text("Hủy")
                    }
                    androidx.compose.material.Button(
                        onClick = { 
                            showConfirm = false
                            onDelete() 
                        },
                        colors = androidx.compose.material.ButtonDefaults.buttonColors(backgroundColor = AccentRed, contentColor = Color.White)
                    ) {
                        Text("Xóa")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material.OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(10.dp),
                        colors = androidx.compose.material.ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                    ) {
                        Text("Sửa")
                    }
                    androidx.compose.material.OutlinedButton(
                        onClick = { showConfirm = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = androidx.compose.material.ButtonDefaults.outlinedButtonColors(contentColor = AccentRed)
                    ) {
                        Text("Xóa")
                    }
                }
            }
        }
    }
}

@Composable
fun EventAdminCard(
    eventName: String, 
    frames: List<com.phuctran.photobooth.desktop.model.FramePack>, 
    frameStore: com.phuctran.photobooth.desktop.storage.FrameStore, 
    onRefresh: () -> Unit
) {
    val coverPath = remember(eventName) { frameStore.getSpecialEventCoverPath(eventName) }
    var coverLastModified by remember { mutableStateOf(if (java.nio.file.Files.exists(coverPath)) java.nio.file.Files.getLastModifiedTime(coverPath).toMillis() else 0L) }
    val coverBitmap = com.phuctran.photobooth.desktop.ui.components.rememberImageBitmap(coverPath, coverLastModified)
    var showConfirm by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        color = NeutralPanel,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeutralBorder)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Cover Preview
                Box(
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    val currentBitmap = coverBitmap
                    if (currentBitmap != null) {
                        Image(
                            bitmap = currentBitmap,
                            contentDescription = "Cover",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text("Trống", color = NeutralSecondary, style = MaterialTheme.typography.caption)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(eventName, style = MaterialTheme.typography.h6, color = NeutralText, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Tổng cộng: ${frames.size} khung", color = NeutralSecondary, style = MaterialTheme.typography.body2)
                }
                Spacer(Modifier.width(16.dp))
                
                // Actions
                if (showConfirm) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Xóa toàn bộ sự kiện này?", color = AccentRed, style = MaterialTheme.typography.body2)
                        androidx.compose.material.OutlinedButton(
                            onClick = { showConfirm = false },
                            shape = RoundedCornerShape(8.dp),
                            colors = androidx.compose.material.ButtonDefaults.outlinedButtonColors(contentColor = NeutralSecondary)
                        ) { Text("Hủy") }
                        androidx.compose.material.Button(
                            onClick = { 
                                showConfirm = false
                                frames.forEach { frame ->
                                    if (frame.customImagePath != null) {
                                        java.nio.file.Files.deleteIfExists(frame.customImagePath)
                                    }
                                }
                                java.nio.file.Files.deleteIfExists(coverPath)
                                onRefresh() 
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = androidx.compose.material.ButtonDefaults.buttonColors(backgroundColor = AccentRed, contentColor = Color.White)
                        ) { Text("Xóa") }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material.OutlinedButton(
                            onClick = {
                                val parentFrame = java.awt.Window.getWindows().firstOrNull { it.isShowing && it is javax.swing.JFrame } as? java.awt.Frame
                                val dialog = java.awt.FileDialog(parentFrame, "Chọn Ảnh Bìa (Cover)", java.awt.FileDialog.LOAD)
                                dialog.isAlwaysOnTop = true
                                dialog.setFilenameFilter { _, name -> name.endsWith(".png", true) || name.endsWith(".jpg", true) }
                                dialog.isVisible = true
                                val file = dialog.file
                                val dir = dialog.directory
                                if (file != null && dir != null) {
                                    val sourcePath = java.io.File(dir, file).toPath()
                                    frameStore.setSpecialEventCover(eventName, sourcePath)
                                    coverLastModified = java.nio.file.Files.getLastModifiedTime(coverPath).toMillis()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = androidx.compose.material.ButtonDefaults.outlinedButtonColors(contentColor = NeutralText)
                        ) { Text("Đổi Ảnh Bìa") }
                        
                        androidx.compose.material.OutlinedButton(
                            onClick = { isExpanded = !isExpanded },
                            shape = RoundedCornerShape(8.dp),
                            colors = androidx.compose.material.ButtonDefaults.outlinedButtonColors(contentColor = NeutralText)
                        ) { Text(if (isExpanded) "Thu gọn" else "Xem khung") }
                        
                        androidx.compose.material.OutlinedButton(
                            onClick = { showConfirm = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = androidx.compose.material.ButtonDefaults.outlinedButtonColors(contentColor = AccentRed)
                        ) { Text("Xóa Sự kiện") }
                    }
                }
            }
            
            if (isExpanded) {
                Spacer(Modifier.height(16.dp))
                Divider(color = NeutralBorder)
                Spacer(Modifier.height(16.dp))
                val groupedByLayout = frames.groupBy { it.targetLayoutId ?: "Bố cục Khác" }
                groupedByLayout.forEach { (layout, framesByLayout) ->
                    Text("Bố cục: $layout", style = MaterialTheme.typography.subtitle2, color = AccentNude, modifier = Modifier.padding(bottom = 8.dp))
                    framesByLayout.forEach { frame -> 
                        Box(modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) {
                            FrameAdminCard(frame) {
                                if (frame.customImagePath != null) {
                                    java.nio.file.Files.deleteIfExists(frame.customImagePath)
                                    onRefresh()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
