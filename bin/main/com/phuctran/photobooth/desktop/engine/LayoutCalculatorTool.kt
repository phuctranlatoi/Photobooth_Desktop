package com.phuctran.photobooth.desktop.engine

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.phuctran.photobooth.desktop.remote.FirebaseManager
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import java.io.ByteArrayOutputStream

fun main() = application {
    FirebaseManager.initialize()
    Window(
        onCloseRequest = ::exitApplication,
        title = "Photobooth Layout Calculator",
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        MaterialTheme(
            colors = darkColors(
                primary = Color(0xFF6366F1),
                background = Color(0xFF1E1E2E),
                surface = Color(0xFF27273A)
            )
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                CalculatorApp()
            }
        }
    }
}

@Composable
fun CalculatorApp(
    config: com.phuctran.photobooth.desktop.config.DesktopBoothConfig? = null,
    remoteLayouts: List<com.phuctran.photobooth.desktop.model.LayoutMode> = emptyList()
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var generatedCode by remember { mutableStateOf("Vui lòng chọn một file PNG có nền trong suốt (đục lỗ) để bắt đầu tính toán.") }
    var isProcessing by remember { mutableStateOf(false) }
    var currentResult by remember { mutableStateOf<DetectionResult?>(null) }
    
    var layoutIdInput by remember { mutableStateOf("") }
    var frameIdInput by remember { mutableStateOf("") }
    var isSpecialFrame by remember { mutableStateOf(false) }
    var specialEventName by remember { mutableStateOf("") }
    var specialEventDropdownExpanded by remember { mutableStateOf(false) }
    var isAddingNewSpecialEvent by remember { mutableStateOf(false) }
    var existingSpecialEvents by remember { mutableStateOf(emptyList<String>()) }
    

    var detectedSizeInput by remember { mutableStateOf("") }
    
    var qrXInput by remember { mutableStateOf("") }
    var qrYInput by remember { mutableStateOf("") }
    var qrSizeInput by remember { mutableStateOf("") }
    
    val projectDir = com.phuctran.photobooth.desktop.config.DesktopAppPaths.appDataDir()
    val frameStore = remember { com.phuctran.photobooth.desktop.storage.FrameStore(projectDir) }
    var existingLayoutIds by remember { mutableStateOf(emptyList<String>()) }
    var isAddingNewLayout by remember { mutableStateOf(false) }

    LaunchedEffect(remoteLayouts) {
        val frames = frameStore.loadFrames()
        existingLayoutIds = (remoteLayouts.map { it.id } + com.phuctran.photobooth.desktop.model.DefaultLayoutModes.map { it.id } + frames.mapNotNull { it.targetLayoutId }).distinct().sorted()
        
        // Fetch existing special events
        existingSpecialEvents = frames
            .mapNotNull { it.specialEventName }
            .distinct()
            .sorted()
    }

    fun detectPrintSize(w: Int, h: Int): String {
        val ratio = w.toFloat() / h.toFloat()
        return when {
            Math.abs(ratio - (5f/15f)) < 0.05 -> "5x15"
            Math.abs(ratio - (10f/15f)) < 0.05 -> "10x15"
            Math.abs(ratio - (15f/10f)) < 0.05 -> "15x10"
            Math.abs(ratio - (15f/20f)) < 0.05 -> "15x20"
            else -> "${w}x${h}"
        }
    }

    fun processImage(file: File) {
        coroutineScope.launch {
            isProcessing = true
            layoutIdInput = file.nameWithoutExtension
            frameIdInput = file.nameWithoutExtension
            try {
                val image = withContext(Dispatchers.IO) { ImageIO.read(file) }
                if (image != null) {
                    val engine = SlotDetectionEngine()
                    val result = engine.detect(image)
                    
                    if (result.slots.isEmpty()) {
                        generatedCode = "LỖI: Không tìm thấy lỗ ảnh nào trong file này.\nHãy đảm bảo vùng để ảnh đã được đục lỗ (trong suốt) hoặc là các khối màu đặc hình chữ nhật."
                        currentResult = null
                        previewBitmap = image.toComposeImageBitmap()
                        qrXInput = ""
                        qrYInput = ""
                        qrSizeInput = ""
                    } else {
                        currentResult = result
                        val sizeLabel = "${detectPrintSize(result.width, result.height)}_${result.slots.size}_anh"
                        detectedSizeInput = sizeLabel
                        generatedCode = generateKotlinCode(layoutIdInput, result)
                        
                        if (result.qrSlot != null) {
                            qrXInput = (result.qrSlot.x * result.width).toInt().toString()
                            qrYInput = (result.qrSlot.y * result.height).toInt().toString()
                            qrSizeInput = (result.qrSlot.width * result.width).toInt().toString()
                        } else {
                            val jsonPath = java.io.File(file.parentFile, "${file.nameWithoutExtension}.json")
                            if (jsonPath.exists()) {
                                try {
                                    val jsonStr = jsonPath.readText()
                                    val jsonObject = com.google.gson.Gson().fromJson(jsonStr, com.google.gson.JsonObject::class.java)
                                    qrXInput = if (jsonObject.has("qrCodeX")) jsonObject.get("qrCodeX").asInt.toString() else ""
                                    qrYInput = if (jsonObject.has("qrCodeY")) jsonObject.get("qrCodeY").asInt.toString() else ""
                                    qrSizeInput = if (jsonObject.has("qrCodeSize")) jsonObject.get("qrCodeSize").asInt.toString() else ""
                                } catch (e: Exception) {
                                    qrXInput = ""
                                    qrYInput = ""
                                    qrSizeInput = ""
                                }
                            } else {
                                qrXInput = ""
                                qrYInput = ""
                                qrSizeInput = ""
                            }
                        }
                        
                        if (result.punchedImage != null) {
                            previewBitmap = result.punchedImage.toComposeImageBitmap()
                            val outPath = java.io.File(file.parentFile, "${file.nameWithoutExtension}_punched.png")
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                javax.imageio.ImageIO.write(result.punchedImage, "png", outPath)
                            }
                        } else {
                            previewBitmap = image.toComposeImageBitmap()
                        }
                    }
                } else {
                    generatedCode = "LỖI: Không thể đọc được file ảnh này."
                    currentResult = null
                }
            } catch (e: Exception) {
                generatedCode = "LỖI: ${e.message}"
                currentResult = null
            } finally {
                isProcessing = false
            }
        }
    }

    fun openFileChooser() {
        val parentWindow = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? java.awt.Frame
        val dialog = java.awt.FileDialog(parentWindow, "Chọn file Frame PNG", java.awt.FileDialog.LOAD)
        dialog.file = "*.png"
        val userDir = System.getProperty("user.dir")
        dialog.directory = userDir
        dialog.isVisible = true
        
        if (dialog.file != null) {
            val file = File(dialog.directory, dialog.file)
            selectedFile = file
            processImage(file)
        }
    }

    fun openFileChooserDirect() {
        val parentWindow = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? java.awt.Frame
        val dialog = java.awt.FileDialog(parentWindow, "Chọn file Frame PNG (Đã đục lỗ sẵn)", java.awt.FileDialog.LOAD)
        dialog.file = "*.png"
        val userDir = System.getProperty("user.dir")
        dialog.directory = userDir
        dialog.isVisible = true
        
        if (dialog.file != null) {
            val file = File(dialog.directory, dialog.file)
            selectedFile = file
            layoutIdInput = file.nameWithoutExtension
            frameIdInput = file.nameWithoutExtension
            isProcessing = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val image = ImageIO.read(file)
                    previewBitmap = image.toComposeImageBitmap()
                    val sizeLabel = "${detectPrintSize(image.width, image.height)}_custom"
                    detectedSizeInput = sizeLabel
                    currentResult = null // Không chạy phân tích lỗ
                    
                    val jsonPath = java.io.File(file.parentFile, "${file.nameWithoutExtension}.json")
                    if (jsonPath.exists()) {
                        try {
                            val jsonStr = jsonPath.readText()
                            val jsonObject = com.google.gson.Gson().fromJson(jsonStr, com.google.gson.JsonObject::class.java)
                            qrXInput = if (jsonObject.has("qrCodeX")) jsonObject.get("qrCodeX").asInt.toString() else ""
                            qrYInput = if (jsonObject.has("qrCodeY")) jsonObject.get("qrCodeY").asInt.toString() else ""
                            qrSizeInput = if (jsonObject.has("qrCodeSize")) jsonObject.get("qrCodeSize").asInt.toString() else ""
                        } catch (e: Exception) {
                            qrXInput = ""
                            qrYInput = ""
                            qrSizeInput = ""
                        }
                    } else {
                        qrXInput = ""
                        qrYInput = ""
                        qrSizeInput = ""
                    }
                    
                    generatedCode = "Đã tải khung gốc thành công. Vui lòng chọn Mã Bố Cục tương ứng ở cột phải và nhấn 'Lưu Khung Ảnh (Local)'."
                } catch (e: Exception) {
                    generatedCode = "LỖI: ${e.message}"
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    fun copyToClipboard() {
        val selection = StringSelection(generatedCode)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }
    
    fun saveLayoutToFirebase() {
        val id = layoutIdInput.trim()
        if (id.isEmpty()) return
        
        if (currentResult == null) {
            coroutineScope.launch(Dispatchers.IO) {
                FirebaseManager.updateLayoutQRConfig(id, qrXInput.toIntOrNull(), qrYInput.toIntOrNull(), qrSizeInput.toIntOrNull())
                generatedCode = "ĐÃ CẬP NHẬT TỌA ĐỘ QR CHO BỐ CỤC: $id lên Firebase Firestore thành công!\n\n" + generatedCode
            }
            return
        }
        
        val result = currentResult!!
        
        coroutineScope.launch(Dispatchers.IO) {
            val rawSize = detectedSizeInput.trim()
            val formattedSize = if (rawSize.matches(Regex("\\d+\\s*[xX]\\s*\\d+"))) {
                val parts = rawSize.split(Regex("\\s*[xX]\\s*"))
                "${parts[0]} x ${parts[1]} cm"
            } else {
                if (rawSize.isEmpty()) "15 x 10 cm" else rawSize
            }
            
            val pAspectRatio = result.width.toFloat() / result.height.toFloat()
            val firstSlot = result.slots.firstOrNull()
            val photoAspect = if (firstSlot != null) {
                (firstSlot.width / firstSlot.height) * pAspectRatio
            } else 1f
            
            val layoutData = mutableMapOf<String, Any>(
                "id" to id,
                "title" to id.capitalize(),
                "subtitle" to "${result.slots.size} ảnh",
                "description" to "Tự động phân tích từ ảnh gốc.",
                "shotCount" to result.slots.size,
                "selectCount" to result.slots.size,
                "printSizeLabel" to formattedSize,
                "printAspectRatio" to pAspectRatio,
                "photoAspectRatio" to photoAspect,
                "mediaLabel" to "$formattedSize • ${result.slots.size} ảnh",
                "width" to result.width.toInt(),
                "height" to result.height.toInt(),
                "slots" to result.slots.map { slot ->
                    mapOf(
                        "index" to slot.index,
                        "x" to slot.x,
                        "y" to slot.y,
                        "width" to slot.width,
                        "height" to slot.height,
                        "centerX" to slot.centerX,
                        "centerY" to slot.centerY,
                        "areaRatio" to slot.areaRatio
                    )
                },
                "createdAt" to System.currentTimeMillis()
            )
            qrXInput.toIntOrNull()?.let { layoutData["qrCodeX"] = it }
            qrYInput.toIntOrNull()?.let { layoutData["qrCodeY"] = it }
            qrSizeInput.toIntOrNull()?.let { layoutData["qrCodeSize"] = it }
            
            FirebaseManager.uploadLayout(id, layoutData)
            generatedCode = "ĐÃ LƯU BỐ CỤC: $id lên Firebase Firestore thành công!\n\n" + generatedCode
        }
    }

    fun saveFrameToFirebase() {
        val file = selectedFile ?: return
        val lId = layoutIdInput.trim()
        val fId = frameIdInput.trim()
        if (lId.isEmpty() || fId.isEmpty()) return
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Save locally using FrameStore
                // Nếu currentResult != null thì lấy ảnh đã phân tích, nếu null thì lấy thẳng ảnh gốc
                val imageToSave = currentResult?.punchedImage ?: ImageIO.read(file)
                val tempFile = java.io.File(file.parentFile, "$fId.png")
                javax.imageio.ImageIO.write(imageToSave, "png", tempFile)
                
                val projectDir = com.phuctran.photobooth.desktop.config.DesktopAppPaths.appDataDir()
                val frameStore = com.phuctran.photobooth.desktop.storage.FrameStore(projectDir)
                
                val qrX = qrXInput.toIntOrNull()
                val qrY = qrYInput.toIntOrNull()
                val qrSize = qrSizeInput.toIntOrNull()

                val newFrame = frameStore.addCustomFrame(
                    source = tempFile.toPath(), 
                    printSizeLabel = detectedSizeInput.trim(), 
                    layoutId = lId, 
                    isSpecial = isSpecialFrame,
                    qrCodeX = qrX,
                    qrCodeY = qrY,
                    qrCodeSize = qrSize
                )
                
                tempFile.delete() // Clean up temp file
                
                generatedCode = "ĐÃ LƯU KHUNG ẢNH: $fId vào ổ đĩa nội bộ thành công!\nĐường dẫn: ${newFrame.customImagePath}\n\n" + generatedCode
            } catch (e: Exception) {
                generatedCode = "LỖI LƯU KHUNG: ${e.message}\n\n" + generatedCode
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Left Column (Controls & Preview)
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Layout Calculator Tool", style = MaterialTheme.typography.h5, color = Color.White)
            Text("Công cụ tự động đo đạc tỷ lệ và khoảng cách cho khung ảnh", color = Color.Gray)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { openFileChooser() },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("1. Chọn Ảnh để Đo Đạc", fontSize = 13.sp)
                }
                
                Button(
                    onClick = { openFileChooserDirect() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4DB6AC))
                ) {
                    Text("Thêm Khung Đã Đục Lỗ Trực Tiếp", color = Color.White, fontSize = 13.sp)
                }
            }

            if (selectedFile != null) {
                Text("Đang chọn: ${selectedFile?.name}", color = Color.LightGray, fontSize = 12.sp)
            }

            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = MaterialTheme.colors.surface,
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = MaterialTheme.colors.primary)
                    } else if (previewBitmap != null) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                            val imgW = previewBitmap!!.width.toFloat()
                            val imgH = previewBitmap!!.height.toFloat()
                            val scale = minOf(maxWidth.value / imgW, maxHeight.value / imgH)
                            val drawW = imgW * scale
                            val drawH = imgH * scale
                            val offsetX = (maxWidth.value - drawW) / 2f
                            val offsetY = (maxHeight.value - drawH) / 2f

                            // Draw Sample Photos (UNDERNEATH the frame)
                            if (currentResult != null && currentResult!!.slots.isNotEmpty()) {
                                val placeholderColors = listOf(
                                    Color(0xFFE57373), // Red
                                    Color(0xFF81C784), // Green
                                    Color(0xFF64B5F6), // Blue
                                    Color(0xFFFFD54F), // Yellow
                                    Color(0xFFBA68C8), // Purple
                                    Color(0xFF4DB6AC)  // Teal
                                )
                                for (slot in currentResult!!.slots) {
                                    val rectX = offsetX + slot.x * drawW
                                    val rectY = offsetY + slot.y * drawH
                                    val rectW = slot.width * drawW
                                    val rectH = slot.height * drawH
                                    val bgColor = placeholderColors[slot.index % placeholderColors.size]

                                    Box(
                                        modifier = Modifier
                                            .offset(rectX.dp, rectY.dp)
                                            .width(rectW.dp)
                                            .height(rectH.dp)
                                            .background(bgColor)
                                    ) {
                                        Text(
                                            text = "Ảnh ${slot.index + 1}",
                                            color = Color.Black,
                                            style = MaterialTheme.typography.subtitle1,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                            }

                            // Draw the Punched Frame (ON TOP of the photos)
                            Image(
                                bitmap = previewBitmap!!,
                                contentDescription = "Preview",
                                modifier = Modifier
                                    .offset(offsetX.dp, offsetY.dp)
                                    .width(drawW.dp)
                                    .height(drawH.dp)
                            )
                            
                            // Draw the Red Borders and Paddings (ON TOP of everything)
                            if (currentResult != null && currentResult!!.slots.isNotEmpty()) {
                                // Draw padding texts
                                val minX = currentResult!!.slots.minOf { it.x }
                                val minY = currentResult!!.slots.minOf { it.y }
                                val maxR = currentResult!!.slots.maxOf { it.x + it.width }
                                val maxB = currentResult!!.slots.maxOf { it.y + it.height }

                                val padT = (minY * imgH).toInt()
                                val padL = (minX * imgW).toInt()
                                val padB = ((1f - maxB) * imgH).toInt()
                                val padR = ((1f - maxR) * imgW).toInt()

                                // Top Padding Text
                                Text("Pad Top: ${padT}px", color = Color.Yellow, modifier = Modifier.offset((offsetX + drawW/2f - 40f).dp, (offsetY + (minY*drawH)/2f).dp).background(Color.Black.copy(0.5f)).padding(2.dp))
                                // Bottom Padding Text
                                Text("Pad Bottom: ${padB}px", color = Color.Yellow, modifier = Modifier.offset((offsetX + drawW/2f - 40f).dp, (offsetY + drawH - (1f-maxB)*drawH/2f).dp).background(Color.Black.copy(0.5f)).padding(2.dp))
                                // Left Padding Text
                                Text("Pad L: ${padL}px", color = Color.Yellow, modifier = Modifier.offset((offsetX + (minX*drawW)/2f - 20f).dp, (offsetY + drawH/2f).dp).background(Color.Black.copy(0.5f)).padding(2.dp))
                                // Right Padding Text
                                Text("Pad R: ${padR}px", color = Color.Yellow, modifier = Modifier.offset((offsetX + drawW - (1f-maxR)*drawW/2f - 20f).dp, (offsetY + drawH/2f).dp).background(Color.Black.copy(0.5f)).padding(2.dp))

                                for (slot in currentResult!!.slots) {
                                    val rectX = offsetX + slot.x * drawW
                                    val rectY = offsetY + slot.y * drawH
                                    val rectW = slot.width * drawW
                                    val rectH = slot.height * drawH

                                    // The red border box
                                    Box(
                                        modifier = Modifier
                                            .offset(rectX.dp, rectY.dp)
                                            .width(rectW.dp)
                                            .height(rectH.dp)
                                            .border(2.dp, Color.Red)
                                    ) {
                                        // The size text
                                        Text(
                                            text = "${(slot.width * imgW).toInt()} x ${(slot.height * imgH).toInt()}px",
                                            color = Color.Green,
                                            style = MaterialTheme.typography.caption,
                                            modifier = Modifier.align(Alignment.Center)
                                                .background(Color.Black.copy(alpha = 0.5f))
                                                .padding(2.dp)
                                        )
                                    }
                                }
                            }
                            
                            // Draw QR Code Preview (On top of everything)
                            val qrX = qrXInput.toIntOrNull()
                            val qrY = qrYInput.toIntOrNull()
                            val qrSize = qrSizeInput.toIntOrNull()
                            if (qrX != null && qrY != null && qrSize != null) {
                                val rectX = offsetX + (qrX / imgW) * drawW
                                val rectY = offsetY + (qrY / imgH) * drawH
                                val rectW = (qrSize / imgW) * drawW
                                val rectH = (qrSize / imgW) * drawW
                                
                                Box(
                                    modifier = Modifier
                                        .offset(rectX.dp, rectY.dp)
                                        .width(rectW.dp)
                                        .height(rectH.dp)
                                        .background(Color.White)
                                        .border(2.dp, Color.Black)
                                ) {
                                    Text(
                                        text = "QR Preview",
                                        color = Color.Black,
                                        style = MaterialTheme.typography.caption,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }
                        }
                    } else {
                        Text("Chưa có ảnh xem trước", color = Color.Gray)
                    }
                }
            }
        }

        // Right Column (Result Code & Firebase Actions)
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Firebase Actions
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colors.surface,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Xuất Dữ Liệu Firebase", color = Color.White, style = MaterialTheme.typography.subtitle1)
                    
                    OutlinedTextField(
                        value = detectedSizeInput,
                        onValueChange = { detectedSizeInput = it },
                        label = { Text("Kích thước in & Số lượng ảnh") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White)
                    )
                    
                    var layoutDropdownExpanded by remember { mutableStateOf(false) }
                    if (isAddingNewLayout) {
                        OutlinedTextField(
                            value = layoutIdInput,
                            onValueChange = { layoutIdInput = it },
                            label = { Text("Mã Bố cục MỚI (VD: strip_4_doc)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White),
                            trailingIcon = {
                                androidx.compose.material.IconButton(onClick = { isAddingNewLayout = false; layoutIdInput = "" }) {
                                    Text("Hủy", color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                                }
                            }
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(onClick = { layoutDropdownExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (layoutIdInput.isEmpty()) "Chọn Bố cục khung..." else layoutIdInput)
                                }
                                DropdownMenu(
                                    expanded = layoutDropdownExpanded,
                                    onDismissRequest = { layoutDropdownExpanded = false }
                                ) {
                                    existingLayoutIds.forEach { lId ->
                                        DropdownMenuItem(onClick = {
                                            layoutIdInput = lId
                                            layoutDropdownExpanded = false
                                            
                                            val matchedLayout = remoteLayouts.find { it.id == lId }
                                            if (matchedLayout != null) {
                                                detectedSizeInput = matchedLayout.printSizeLabel
                                                qrXInput = matchedLayout.qrCodeX?.toString() ?: ""
                                                qrYInput = matchedLayout.qrCodeY?.toString() ?: ""
                                                qrSizeInput = matchedLayout.qrCodeSize?.toString() ?: ""
                                            } else {
                                                val defaultLayout = com.phuctran.photobooth.desktop.model.DefaultLayoutModes.find { it.id == lId }
                                                if (defaultLayout != null) {
                                                    detectedSizeInput = defaultLayout.printSizeLabel
                                                    qrXInput = defaultLayout.qrCodeX?.toString() ?: ""
                                                    qrYInput = defaultLayout.qrCodeY?.toString() ?: ""
                                                    qrSizeInput = defaultLayout.qrCodeSize?.toString() ?: ""
                                                }
                                            }
                                        }) {
                                            Text(lId)
                                        }
                                    }
                                }
                            }
                            OutlinedButton(onClick = { isAddingNewLayout = true; layoutIdInput = "" }) {
                                Text("+ Thêm Mới")
                            }
                        }
                    }
                    
                    OutlinedTextField(
                        value = frameIdInput,
                        onValueChange = { frameIdInput = it },
                        label = { Text("Mã Khung (Frame ID)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White)
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material.Checkbox(
                            checked = isSpecialFrame,
                            onCheckedChange = { isSpecialFrame = it },
                            colors = androidx.compose.material.CheckboxDefaults.colors(checkmarkColor = Color.Black, checkedColor = Color.White, uncheckedColor = Color.Gray)
                        )
                        Text("Khung sự kiện đặc biệt (Special)", color = Color.White)
                    }
                    
                    if (isSpecialFrame) {
                        if (isAddingNewSpecialEvent || existingSpecialEvents.isEmpty()) {
                            OutlinedTextField(
                                value = specialEventName,
                                onValueChange = { specialEventName = it },
                                label = { Text("Tên sự kiện mới (VD: Quoc_khanh_2_9)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White),
                                trailingIcon = {
                                    if (existingSpecialEvents.isNotEmpty()) {
                                        androidx.compose.material.IconButton(onClick = { isAddingNewSpecialEvent = false; specialEventName = "" }) {
                                            Text("Hủy", color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                                        }
                                    }
                                }
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(onClick = { specialEventDropdownExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (specialEventName.isEmpty()) "Chọn sự kiện..." else specialEventName)
                                    }
                                    DropdownMenu(
                                        expanded = specialEventDropdownExpanded,
                                        onDismissRequest = { specialEventDropdownExpanded = false }
                                    ) {
                                        existingSpecialEvents.forEach { evtName ->
                                            DropdownMenuItem(onClick = {
                                                specialEventName = evtName
                                                specialEventDropdownExpanded = false
                                            }) {
                                                Text(evtName)
                                            }
                                        }
                                    }
                                }
                                OutlinedButton(onClick = { isAddingNewSpecialEvent = true; specialEventName = "" }) {
                                    Text("+ Mới")
                                }
                            }
                        }
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = qrXInput,
                            onValueChange = { qrXInput = it.filter { it.isDigit() } },
                            label = { Text("QR X (px)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White)
                        )
                        OutlinedTextField(
                            value = qrYInput,
                            onValueChange = { qrYInput = it.filter { it.isDigit() } },
                            label = { Text("QR Y (px)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White)
                        )
                        OutlinedTextField(
                            value = qrSizeInput,
                            onValueChange = { qrSizeInput = it.filter { it.isDigit() } },
                            label = { Text("QR Size (px)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White)
                        )
                    }
                    Text("Bỏ trống để dùng cấu hình mặc định (Góc phải dưới, 10%)", color = Color.Gray, fontSize = 11.sp)
                    
                    val pathPreview = if (detectedSizeInput.isNotBlank() && layoutIdInput.isNotBlank()) {
                        val evt = if (isSpecialFrame) "Special/" + specialEventName.trim().replace(Regex("[^a-zA-Z0-9_]+"), "_").ifEmpty { "Event" } else "Standard"
                        "${detectedSizeInput.trim()}/${layoutIdInput.trim()}/$evt"
                    } else {
                        "..."
                    }
                    Text("Thư mục lưu: data/frames/$pathPreview", color = Color(0xFF64B5F6), fontSize = 12.sp)
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { saveLayoutToFirebase() },
                            modifier = Modifier.weight(1f),
                            enabled = layoutIdInput.isNotBlank()
                        ) {
                            Text("1. LƯU BỐ CỤC")
                        }
                        
                        Button(
                            onClick = { saveFrameToFirebase() },
                            modifier = Modifier.weight(1f),
                            enabled = selectedFile != null && layoutIdInput.isNotBlank() && frameIdInput.isNotBlank()
                        ) {
                            Text("2. LƯU KHUNG (Local)")
                        }
                    }
                    Text("Quy trình: Nếu chưa có layout, bấm lưu Bố cục trước. Nếu layout đã tồn tại, chỉ cần lưu Khung trỏ tới Layout ID đó.", color = Color.Gray, fontSize = 12.sp)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Kết quả Code (Legacy)", style = MaterialTheme.typography.h6, color = Color.White)
                Button(onClick = { copyToClipboard() }, enabled = previewBitmap != null) {
                    Text("Copy Code")
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = MaterialTheme.colors.surface,
                shape = RoundedCornerShape(8.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = generatedCode,
                        color = Color(0xFFA6E22E), // Greenish tech color
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

private data class CenterCluster(
    val center: Float,
    val members: List<Float>
)

private data class GridAnalysis(
    val isRegular: Boolean,
    val columns: List<Float>,
    val rows: List<Float>,
    val slotWidth: Float,
    val slotHeight: Float,
    val gapX: Float,
    val gapY: Float,
    val reason: String
)

private fun generateKotlinCode(idName: String, result: DetectionResult): String {
    val width = result.width.toFloat()
    val height = result.height.toFloat()
    val slots = result.slots.sortedBy { it.index }

    if (slots.isEmpty()) return "// Không tìm thấy slot ảnh."

    val rawAspectRatio = width / height
    val printInfo = detectPrintSize(rawAspectRatio)
    val grid = analyzeGrid(slots)

    val absoluteSlotsCode = buildString {
        appendLine("// ============================================================")
        appendLine("// SLOT COORDINATES - NGUỒN CHÍNH XÁC NHẤT")
        appendLine("// Tọa độ normalized 0..1, KHÔNG suy ngược từ grid/gap.")
        appendLine("// Frame sau crop: ${result.width}px x ${result.height}px")
        appendLine("// Khổ nhận diện: ${printInfo.label}")
        appendLine("// ============================================================")
        appendLine("val detectedSlots = listOf(")

        slots.forEachIndexed { index, slot ->
            val pxX = (slot.x * width).toInt()
            val pxY = (slot.y * height).toInt()
            val pxW = (slot.width * width).toInt()
            val pxH = (slot.height * height).toInt()

            appendLine("    // Slot ${index + 1}: x=${pxX}px, y=${pxY}px, w=${pxW}px, h=${pxH}px")
            appendLine("    FrameSlot(")
            appendLine("        id = \"slot_${index + 1}\",")
            appendLine("        index = $index,")
            appendLine("        x = ${f6(slot.x)}f,")
            appendLine("        y = ${f6(slot.y)}f,")
            appendLine("        width = ${f6(slot.width)}f,")
            appendLine("        height = ${f6(slot.height)}f,")
            appendLine("        centerX = ${f6(slot.centerX)}f,")
            appendLine("        centerY = ${f6(slot.centerY)}f,")
            appendLine("        areaRatio = ${f6(slot.areaRatio)}f,")
            appendLine("        shape = \"RECT\"")
            append("    )")
            if (index != slots.lastIndex) append(",")
            appendLine()
        }
        appendLine(")")
    }

    if (!grid.isRegular) {
        return buildString {
            append(absoluteSlotsCode)
            appendLine()
            appendLine("// ============================================================")
            appendLine("// FREEFORM / IRREGULAR LAYOUT")
            appendLine("// ${grid.reason}")
            appendLine("//")
            appendLine("// QUAN TRỌNG:")
            appendLine("// Không chuyển layout này thành gridColumns + gap + padding.")
            appendLine("// Renderer phải dùng detectedSlots trực tiếp để đặt từng ảnh.")
            appendLine("// Mỗi slot có x/y/width/height riêng nên các bố cục lệch nhau vẫn đúng.")
            appendLine("//")
            appendLine("// Photo bleed chỉ dùng KHI VẼ ẢNH, không được cộng vào tọa độ lỗ:")
            val bleed = maxOf(2, (minOf(result.width, result.height) * 0.003f).toInt())
            appendLine("// val photoBleedPx = ${bleed}")
            appendLine("// drawPhoto(slotRect.expand(photoBleedPx))")
            appendLine("// drawFrameOnTop(punchedFrame)")
            appendLine("// ============================================================")
        }.trimEnd()
    }

    val idealAspectRatio = printInfo.aspectRatio
    val slotWidth = grid.slotWidth
    val slotHeight = grid.slotHeight

    val left = (grid.columns.first() - slotWidth / 2f).coerceAtLeast(0f)
    val right = (grid.columns.last() + slotWidth / 2f).coerceAtMost(1f)
    val top = (grid.rows.first() - slotHeight / 2f).coerceAtLeast(0f)
    val bottom = (grid.rows.last() + slotHeight / 2f).coerceAtMost(1f)

    // Giữ đúng quy ước ratio của LayoutMode hiện tại:
    // X normalized theo frame width, Y normalized theo frame height.
    val paddingTopRatio = top / idealAspectRatio
    val paddingBottomRatio = (1f - bottom) / idealAspectRatio
    val paddingLeftRatio = left
    val paddingRightRatio = 1f - right
    val gapHorizontalRatio = grid.gapX.coerceAtLeast(0f)
    val gapVerticalRatio = grid.gapY.coerceAtLeast(0f) / idealAspectRatio
    val photoAspectRatio = (slotWidth * idealAspectRatio) / slotHeight

    val rawSlotW = (slotWidth * width).toInt()
    val rawSlotH = (slotHeight * height).toInt()
    val rawGapX = (grid.gapX * width).toInt().coerceAtLeast(0)
    val rawGapY = (grid.gapY * height).toInt().coerceAtLeast(0)
    val rawPadLeft = (left * width).toInt()
    val rawPadRight = ((1f - right) * width).toInt()
    val rawPadTop = (top * height).toInt()
    val rawPadBottom = ((1f - bottom) * height).toInt()
    val bleed = maxOf(2, (minOf(result.width, result.height) * 0.003f).toInt())

    return buildString {
        append(absoluteSlotsCode)
        appendLine()
        appendLine("// ============================================================")
        appendLine("// REGULAR GRID - có thể dùng LayoutMode legacy")
        appendLine("// Grid: ${grid.columns.size} cột x ${grid.rows.size} hàng")
        appendLine("// Slot thật: ${rawSlotW}px x ${rawSlotH}px")
        appendLine("// Gap X/Y thật: ${rawGapX}px / ${rawGapY}px")
        appendLine("// Padding T/B/L/R: ${rawPadTop}px / ${rawPadBottom}px / ${rawPadLeft}px / ${rawPadRight}px")
        appendLine("// Photo bleed đề xuất khi render: ${bleed}px (KHÔNG cộng vào hole/slot)")
        appendLine("// ============================================================")
        appendLine("LayoutMode(")
        appendLine("    id = \"$idName\",")
        appendLine("    title = \"Tên Khung\",")
        appendLine("    subtitle = \"${printInfo.label}\",")
        appendLine("    description = \"Mô tả khung\",")
        appendLine("    family = LayoutFamily.Grid,")
        appendLine("    shotCount = ${slots.size},")
        appendLine("    selectCount = ${slots.size},")
        appendLine("    basePrice = 50000L,")
        appendLine("    mediaLabel = \"In 1 ảnh ${printInfo.label}\",")
        appendLine("    accentColor = 0xFF475569,")
        appendLine("    gridColumns = ${grid.columns.size},")
        appendLine("    printSizeLabel = \"${printInfo.label}\",")
        appendLine("    printAspectRatio = ${printInfo.kotlinRatio},")
        appendLine("    photoAspectRatio = ${f4(photoAspectRatio)}f,")
        appendLine("    paddingTopRatio = ${f4(paddingTopRatio)}f,")
        appendLine("    paddingBottomRatio = ${f4(paddingBottomRatio)}f,")
        appendLine("    paddingLeftRatio = ${f4(paddingLeftRatio)}f,")
        appendLine("    paddingRightRatio = ${f4(paddingRightRatio)}f,")
        appendLine("    gapHorizontalRatio = ${f4(gapHorizontalRatio)}f,")
        appendLine("    gapVerticalRatio = ${f4(gapVerticalRatio)}f")
        appendLine(")")
    }.trimEnd()
}

private data class PrintInfo(
    val label: String,
    val aspectRatio: Float,
    val kotlinRatio: String
)

private fun detectPrintSize(rawAspectRatio: Float): PrintInfo {
    val standards = listOf(
        PrintInfo("5 x 15 cm", 5f / 15f, "5f / 15f"),
        PrintInfo("10 x 15 cm", 10f / 15f, "10f / 15f"),
        PrintInfo("15 x 10 cm", 15f / 10f, "15f / 10f")
    )

    val closest = standards.minByOrNull { kotlin.math.abs(rawAspectRatio - it.aspectRatio) }!!
    return if (kotlin.math.abs(rawAspectRatio - closest.aspectRatio) <= 0.055f) {
        closest
    } else {
        PrintInfo(
            label = "Tùy chỉnh",
            aspectRatio = rawAspectRatio,
            kotlinRatio = "${f6(rawAspectRatio)}f"
        )
    }
}

/**
 * Chỉ gọi là Grid khi thật sự có đủ mọi giao điểm row x column, kích thước slot gần bằng nhau
 * và khoảng cách giữa các hàng/cột gần đều. Nhờ vậy layout chéo/lệch không còn bị ép sai thành grid.
 */
private fun analyzeGrid(slots: List<FrameSlot>): GridAnalysis {
    val widths = slots.map { it.width }.sorted()
    val heights = slots.map { it.height }.sorted()
    val medianW = widths[widths.size / 2]
    val medianH = heights[heights.size / 2]

    val xTolerance = maxOf(0.012f, medianW * 0.18f)
    val yTolerance = maxOf(0.012f, medianH * 0.18f)

    val columns = clusterCenters(slots.map { it.centerX }, xTolerance).map { it.center }.sorted()
    val rows = clusterCenters(slots.map { it.centerY }, yTolerance).map { it.center }.sorted()

    if (columns.isEmpty() || rows.isEmpty()) {
        return GridAnalysis(false, columns, rows, medianW, medianH, 0f, 0f, "Không tạo được cụm hàng/cột.")
    }

    if (slots.size != columns.size * rows.size) {
        return GridAnalysis(
            false, columns, rows, medianW, medianH, 0f, 0f,
            "Số slot (${slots.size}) không bằng số giao điểm grid (${columns.size} x ${rows.size} = ${columns.size * rows.size})."
        )
    }

    if (relativeSpread(widths) > 0.065f || relativeSpread(heights) > 0.065f) {
        return GridAnalysis(
            false, columns, rows, medianW, medianH, 0f, 0f,
            "Kích thước các slot không đồng đều đủ để biểu diễn bằng một grid duy nhất."
        )
    }

    val occupied = mutableSetOf<Pair<Int, Int>>()
    for (slot in slots) {
        val col = nearestCenterIndex(slot.centerX, columns)
        val row = nearestCenterIndex(slot.centerY, rows)

        if (kotlin.math.abs(slot.centerX - columns[col]) > xTolerance ||
            kotlin.math.abs(slot.centerY - rows[row]) > yTolerance
        ) {
            return GridAnalysis(
                false, columns, rows, medianW, medianH, 0f, 0f,
                "Có slot lệch khỏi tâm hàng/cột vượt tolerance."
            )
        }

        if (!occupied.add(col to row)) {
            return GridAnalysis(
                false, columns, rows, medianW, medianH, 0f, 0f,
                "Có nhiều slot rơi vào cùng một ô grid."
            )
        }
    }

    val xDistances = columns.zipWithNext { a, b -> b - a }
    val yDistances = rows.zipWithNext { a, b -> b - a }

    if (relativeSpread(xDistances) > 0.08f || relativeSpread(yDistances) > 0.08f) {
        return GridAnalysis(
            false, columns, rows, medianW, medianH, 0f, 0f,
            "Khoảng cách giữa các hàng/cột không đều; dùng absolute slots sẽ chính xác hơn."
        )
    }

    val strideX = if (xDistances.isEmpty()) 0f else xDistances.sorted()[xDistances.size / 2]
    val strideY = if (yDistances.isEmpty()) 0f else yDistances.sorted()[yDistances.size / 2]
    val gapX = if (columns.size > 1) (strideX - medianW).coerceAtLeast(0f) else 0f
    val gapY = if (rows.size > 1) (strideY - medianH).coerceAtLeast(0f) else 0f

    return GridAnalysis(
        isRegular = true,
        columns = columns,
        rows = rows,
        slotWidth = medianW,
        slotHeight = medianH,
        gapX = gapX,
        gapY = gapY,
        reason = "REGULAR_GRID"
    )
}

private fun clusterCenters(values: List<Float>, tolerance: Float): List<CenterCluster> {
    if (values.isEmpty()) return emptyList()

    val sorted = values.sorted()
    val clusters = mutableListOf<MutableList<Float>>()

    for (value in sorted) {
        val last = clusters.lastOrNull()
        if (last == null) {
            clusters += mutableListOf(value)
        } else {
            val mean = last.average().toFloat()
            if (kotlin.math.abs(value - mean) <= tolerance) {
                last += value
            } else {
                clusters += mutableListOf(value)
            }
        }
    }

    return clusters.map { group ->
        CenterCluster(group.average().toFloat(), group.toList())
    }
}

private fun nearestCenterIndex(value: Float, centers: List<Float>): Int {
    var bestIndex = 0
    var bestDistance = Float.MAX_VALUE
    centers.forEachIndexed { index, center ->
        val d = kotlin.math.abs(value - center)
        if (d < bestDistance) {
            bestDistance = d
            bestIndex = index
        }
    }
    return bestIndex
}

private fun relativeSpread(values: List<Float>): Float {
    if (values.size <= 1) return 0f
    val sorted = values.sorted()
    val median = sorted[sorted.size / 2]
    if (median <= 0.000001f) return 0f
    return (sorted.last() - sorted.first()) / median
}

private fun f4(value: Float): String = String.format(Locale.US, "%.4f", value)
private fun f6(value: Float): String = String.format(Locale.US, "%.6f", value)
