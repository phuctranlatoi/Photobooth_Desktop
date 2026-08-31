package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

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
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.graphics.toComposeImageBitmap

@Composable
fun AdminScreen(
    layouts: List<LayoutMode>,
    frames: List<FramePack>,
    effects: List<com.phuctran.photobooth.desktop.model.EffectMode>,
    config: DesktopBoothConfig,
    nativeCamera: NativeEosCaptureService?,
    onAddFrame: (String) -> Unit,
    onSaveLayoutConfig: (String, Long, Int, Int) -> Unit = { _, _, _, _ -> },
    onDeleteLayout: (String) -> Unit,
    onSaveSettings: (Boolean, Boolean, String) -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralBg)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                KioskSecondaryButton("Thoát admin", {
                onSaveSettings(enablePrint, useHotFolder, hotFolderPath)
                onBack() 
                }, modifier = Modifier.width(150.dp))
                Column {
                    Text("Quản trị Le Souvenir", style = MaterialTheme.typography.h4, fontWeight = FontWeight.Black, color = NeutralText)
                    Text("Kiểm tra nội dung, thiết bị và công cụ vận hành trước sự kiện.", color = NeutralSecondary, style = MaterialTheme.typography.body2)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusChip("Camera", if (useHotFolder) "Hot folder" else "Webcam", AccentBlue)
                StatusChip("In", if (enablePrint) "Bật" else "Tắt", if (enablePrint) AccentMint else NeutralMuted)
                StatusChip("Album", if (config.canUploadAlbum) "Cloud" else "Local", if (config.canUploadAlbum) AccentMint else AccentAmber)
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
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (selectedTab == 0) {
                // Frames
                Column(Modifier.fillMaxSize()) {
                    SectionHeader("Admin", "Quản lý khung ảnh", "Xem, lọc và xóa các khung ảnh đã lưu trên hệ thống.")
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val standardFrames = adminFrames.filter { !it.isSpecial }
                        val specialFrames = adminFrames.filter { it.isSpecial }
                        
                        if (standardFrames.isNotEmpty()) {
                            Text("Khung Thường", style = MaterialTheme.typography.h5, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
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
                            Text("Khung Sự Kiện Đặc Biệt", style = MaterialTheme.typography.h5, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp, top = 16.dp))
                            val groupedByEvent = specialFrames.groupBy { it.specialEventName ?: "Sự Kiện Khác" }
                            groupedByEvent.forEach { (eventName, framesByEvent) ->
                                Text("Sự kiện: $eventName", style = MaterialTheme.typography.h6, color = AccentNudeDark, fontWeight = FontWeight.Bold)
                                
                                val groupedByLayout = framesByEvent.groupBy { it.targetLayoutId ?: "Bố cục Khác" }
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
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    SectionHeader("Admin", "Cài đặt thiết bị", "Bạn cần khởi động lại ứng dụng sau khi lưu để áp dụng.")
                    
                    // Print Settings
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Máy in Windows", fontWeight = FontWeight.Bold, color = NeutralText)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Switch(
                                checked = enablePrint,
                                onCheckedChange = { enablePrint = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colors.primary)
                            )
                            Text(if (enablePrint) "Bật (Gửi ảnh tới hộp thoại in Windows)" else "Tắt", color = NeutralMuted)
                        }
                    }
                    
                    Divider(color = NeutralBorder)
                    
                    // Camera Settings
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Nguồn Camera", fontWeight = FontWeight.Bold, color = NeutralText)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = !useHotFolder,
                                    onClick = { useHotFolder = false },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary)
                                )
                                Text("Webcam / DroidCam", Modifier.clickable { useHotFolder = false })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = useHotFolder,
                                    onClick = { useHotFolder = true },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary)
                                )
                                Text("Thư mục ảnh (Hot Folder)", Modifier.clickable { useHotFolder = true })
                            }
                        }
                        
                        if (useHotFolder) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Đường dẫn Hot Folder:", color = NeutralMuted)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = hotFolderPath,
                                        onValueChange = { hotFolderPath = it },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        colors = TextFieldDefaults.outlinedTextFieldColors(textColor = NeutralText)
                                    )
                                    OutlinedButton(onClick = {
                                        val chooser = javax.swing.JFileChooser().apply {
                                            fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                                            dialogTitle = "Chọn Hot Folder"
                                        }
                                        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                                            hotFolderPath = chooser.selectedFile.absolutePath
                                        }
                                    }) {
                                        Text("Chọn thư mục")
                                    }
                                }
                                Text("Mẹo: Dùng digiCamControl chụp để ảnh rớt vào đây.", color = NeutralMuted, style = MaterialTheme.typography.caption)
                            }
                        }
                        
                        if (nativeCamera != null) {
                            Divider(color = NeutralBorder)
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Bảng điều khiển Camera Native (Canon EDSDK)", fontWeight = FontWeight.Bold, color = NeutralText)
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
                    
                    Spacer(Modifier.height(16.dp))
                    KioskPrimaryButton(
                        text = "Lưu cài đặt",
                        onClick = { onSaveSettings(enablePrint, useHotFolder, hotFolderPath) },
                        modifier = Modifier.width(180.dp)
                    )
                }
            } else if (selectedTab == 3) {
                // Calculator App (Firebase Tool)
                androidx.compose.material.MaterialTheme(
                    colors = androidx.compose.material.darkColors(
                        primary = androidx.compose.ui.graphics.Color(0xFF6366F1),
                        background = androidx.compose.ui.graphics.Color(0xFF1E1E2E),
                        surface = androidx.compose.ui.graphics.Color(0xFF27273A)
                    )
                ) {
                    androidx.compose.material.Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = androidx.compose.material.MaterialTheme.colors.background
                    ) {
                        com.phuctran.photobooth.desktop.engine.CalculatorApp(config, layouts)
                    }
                }
            } else if (selectedTab == 4) {
                FilterAdminView(effects = effects, onSaveEffects = onSaveEffects)
            }
        }
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
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            if (java.nio.file.Files.exists(frame.customImagePath)) {
                                val stream = java.nio.file.Files.newInputStream(frame.customImagePath)
                                val img = javax.imageio.ImageIO.read(stream)
                                if (img != null) {
                                    val bitmap = img.toComposeImageBitmap()
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        imageBitmap = bitmap
                                    }
                                }
                            }
                        } catch(e: Exception) { e.printStackTrace() }
                    }
                }
                
                if (imageBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = imageBitmap!!,
                        contentDescription = frame.title,
                        modifier = Modifier.height(100.dp).widthIn(min = 72.dp).clip(RoundedCornerShape(10.dp)).background(NeutralPanelAlt),
                    )
                } else {
                    Box(modifier = Modifier.height(100.dp).width(72.dp).background(NeutralPanelAlt, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
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
        title = { Text("Cài đặt Bố cục", fontWeight = FontWeight.Bold, color = NeutralText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Giá tiền (VNĐ)") },
                    colors = TextFieldDefaults.outlinedTextFieldColors(textColor = NeutralText)
                )
                OutlinedTextField(
                    value = shotCountStr,
                    onValueChange = { shotCountStr = it },
                    label = { Text("Số ảnh chụp") },
                    colors = TextFieldDefaults.outlinedTextFieldColors(textColor = NeutralText)
                )
                OutlinedTextField(
                    value = countdownStr,
                    onValueChange = { countdownStr = it },
                    label = { Text("Thời gian đếm ngược (giây)") },
                    colors = TextFieldDefaults.outlinedTextFieldColors(textColor = NeutralText)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
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
                Text("Khổ in: ${layout.printSizeLabel} | Chụp ${layout.shotCount} ảnh | Chọn ${layout.selectCount} ảnh", color = NeutralSecondary, fontSize = 12.sp)
                Text(layout.description, color = NeutralMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (showConfirm) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Xóa layout?", color = AccentRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

