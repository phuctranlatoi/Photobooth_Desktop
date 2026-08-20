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
import androidx.compose.foundation.layout.IntrinsicSize
import com.phuctran.photobooth.desktop.services.NativeEosCaptureService
import com.phuctran.photobooth.desktop.ui.components.*
import com.phuctran.photobooth.desktop.ui.theme.*
import com.phuctran.photobooth.desktop.model.DefaultLayoutModes
import com.phuctran.photobooth.desktop.model.LayoutMode
import com.phuctran.photobooth.desktop.model.FramePack
import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset

@Composable
fun AdminScreen(
    frames: List<FramePack>,
    config: DesktopBoothConfig,
    nativeCamera: NativeEosCaptureService?,
    onAddFrame: (String) -> Unit,
    onSaveSettings: (Boolean, Boolean, String) -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var selectedLayoutForFrame by remember { mutableStateOf<LayoutMode>(DefaultLayoutModes.first()) }
    var expanded by remember { mutableStateOf(false) }

    var enablePrint by remember { mutableStateOf(config.enableSystemPrint) }
    var useHotFolder by remember { mutableStateOf(config.useHotFolder) }
    var hotFolderPath by remember { mutableStateOf(config.hotFolderPath) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralBg)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onBack) { Text("Quay lại") }
            Text("Quản trị hệ thống", style = MaterialTheme.typography.h4, fontWeight = FontWeight.Bold, color = NeutralText)
        }

        TabRow(
            selectedTabIndex = selectedTab,
            backgroundColor = Color.Transparent,
            contentColor = MaterialTheme.colors.primary,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colors.primary
                )
            }
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("Khung ảnh", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("Cài đặt", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (selectedTab == 0) {
                // Frames
                Column(Modifier.fillMaxSize()) {
                    SectionHeader("Quản lý Khung ảnh (Frame)", "Thêm frame PNG tương ứng với layout để khách hàng chọn.", "")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Chọn Layout:", fontWeight = FontWeight.Medium)
                            Box {
                                OutlinedButton(onClick = { expanded = true }) {
                                    Text(selectedLayoutForFrame.mediaLabel)
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    DefaultLayoutModes.forEach { layout ->
                                        DropdownMenuItem(onClick = {
                                            selectedLayoutForFrame = layout
                                            expanded = false
                                        }) {
                                            Text(layout.mediaLabel)
                                        }
                                    }
                                }
                            }
                            OutlinedButton(onClick = { onAddFrame(selectedLayoutForFrame.id) }) { Text("Thêm frame PNG") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        frames.forEach { frame -> FrameChoice(frame, false) {} }
                    }
                }
            } else {
                // Settings
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    SectionHeader("Cài đặt", "Thiết bị ngoại vi", "Lưu ý: Bạn cần khởi động lại ứng dụng sau khi lưu để áp dụng.")
                    
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
                    Button(
                        onClick = { onSaveSettings(enablePrint, useHotFolder, hotFolderPath) },
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary, contentColor = Color.White),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Lưu cài đặt", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
                    }
                }
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
