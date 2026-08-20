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
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Photobooth Layout Calculator",
        state = rememberWindowState(width = 1000.dp, height = 800.dp)
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
fun CalculatorApp() {
    val coroutineScope = rememberCoroutineScope()
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var generatedCode by remember { mutableStateOf("Vui lòng chọn một file PNG có nền trong suốt (đục lỗ) để bắt đầu tính toán.") }
    var isProcessing by remember { mutableStateOf(false) }
    var currentResult by remember { mutableStateOf<DetectionResult?>(null) }

    fun processImage(file: File) {
        coroutineScope.launch {
            isProcessing = true
            try {
                val image = withContext(Dispatchers.IO) { ImageIO.read(file) }
                if (image != null) {
                    val engine = SlotDetectionEngine()
                    val result = engine.detect(image)
                    
                    if (result.slots.isEmpty()) {
                        generatedCode = "LỖI: Không tìm thấy lỗ ảnh nào trong file này.\nHãy đảm bảo vùng để ảnh đã được đục lỗ (trong suốt) hoặc là các khối màu đặc hình chữ nhật."
                        currentResult = null
                        previewBitmap = image.toComposeImageBitmap()
                    } else {
                        generatedCode = generateKotlinCode(file.nameWithoutExtension, result)
                        currentResult = result
                        
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
        val chooser = JFileChooser()
        chooser.dialogTitle = "Chọn file Frame PNG"
        val filter = FileNameExtensionFilter("PNG Images", "png")
        chooser.fileFilter = filter
        val userDir = System.getProperty("user.dir")
        chooser.currentDirectory = File(userDir)
        
        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile
            selectedFile = file
            processImage(file)
        }
    }

    fun copyToClipboard() {
        val selection = StringSelection(generatedCode)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }

    Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Left Column (Controls & Preview)
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Layout Calculator Tool", style = MaterialTheme.typography.h5, color = Color.White)
            Text("Công cụ tự động đo đạc tỷ lệ và khoảng cách cho khung ảnh", color = Color.Gray)
            
            Button(
                onClick = { openFileChooser() },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Chọn File Frame PNG...")
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
                        }
                    } else {
                        Text("Chưa có ảnh xem trước", color = Color.Gray)
                    }
                }
            }
        }

        // Right Column (Result Code)
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Kết quả Code", style = MaterialTheme.typography.h6, color = Color.White)
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

private fun generateKotlinCode(idName: String, result: DetectionResult): String {
    val width = result.width.toFloat()
    val height = result.height.toFloat()
    val slots = result.slots
    val firstSlot = slots.first()

    val rawAspectRatio = width / height
    
    // Auto-detect standard frame sizes
    var printSizeLabel = "Tùy chỉnh"
    var idealAspectRatio = rawAspectRatio
    var printAspectRatioStr = String.format("%.4f", rawAspectRatio)

    if (Math.abs(rawAspectRatio - (5f / 15f)) < 0.05f) {
        printSizeLabel = "5 x 15 cm"
        idealAspectRatio = 5f / 15f
        printAspectRatioStr = "5f / 15f"
    } else if (Math.abs(rawAspectRatio - (10f / 15f)) < 0.05f) {
        printSizeLabel = "10 x 15 cm"
        idealAspectRatio = 10f / 15f
        printAspectRatioStr = "10f / 15f"
    } else if (Math.abs(rawAspectRatio - (15f / 10f)) < 0.05f) {
        printSizeLabel = "15 x 10 cm"
        idealAspectRatio = 15f / 10f
        printAspectRatioStr = "15f / 10f"
    }
    
    // Tự động nhận diện lưới nhiều cột (Multi-column)
    val centerXs = slots.map { it.centerX }.sorted()
    val distinctColumns = mutableListOf<Float>()
    for (cx in centerXs) {
        if (distinctColumns.isEmpty() || cx - distinctColumns.last() > 0.05f) {
            distinctColumns.add(cx)
        }
    }
    val gridColumns = distinctColumns.size

    val centerYs = slots.map { it.centerY }.sorted()
    val distinctRows = mutableListOf<Float>()
    for (cy in centerYs) {
        if (distinctRows.isEmpty() || cy - distinctRows.last() > 0.05f) {
            distinctRows.add(cy)
        }
    }
    val gridRows = distinctRows.size

    val bleedPixels = 5f
    val bleedX = bleedPixels / width
    val bleedY = bleedPixels / height

    var maxSlotWidthPercent = 0f
    var maxSlotHeightPercent = 0f
    for (slot in slots) {
        if (slot.width > maxSlotWidthPercent) maxSlotWidthPercent = slot.width
        if (slot.height > maxSlotHeightPercent) maxSlotHeightPercent = slot.height
    }

    val trueSlotWidthPercent = maxSlotWidthPercent + 2 * bleedX
    val trueSlotHeightPercent = maxSlotHeightPercent + 2 * bleedY

    var strideYPercent = 0f
    if (gridRows > 1) {
        val distancesY = mutableListOf<Float>()
        for (i in 1 until distinctRows.size) {
            distancesY.add(distinctRows[i] - distinctRows[i-1])
        }
        distancesY.sort()
        strideYPercent = distancesY[distancesY.size / 2]
    }

    var strideXPercent = 0f
    if (gridColumns > 1) {
        val distancesX = mutableListOf<Float>()
        for (i in 1 until distinctColumns.size) {
            distancesX.add(distinctColumns[i] - distinctColumns[i-1])
        }
        distancesX.sort()
        strideXPercent = distancesX[distancesX.size / 2]
    }

    val trueGapYPercent = Math.max(0f, strideYPercent - trueSlotHeightPercent)
    val trueGapXPercent = Math.max(0f, strideXPercent - trueSlotWidthPercent)

    val trueTopPercent = distinctRows.first() - (trueSlotHeightPercent / 2f)
    val trueLeftPercent = distinctColumns.first() - (trueSlotWidthPercent / 2f)

    val trueBottomPercent = trueTopPercent + gridRows * trueSlotHeightPercent + (gridRows - 1) * trueGapYPercent
    val trueRightPercent = trueLeftPercent + gridColumns * trueSlotWidthPercent + (gridColumns - 1) * trueGapXPercent

    val paddingTopRatio = trueTopPercent / idealAspectRatio
    val paddingBottomRatio = (1f - trueBottomPercent) / idealAspectRatio
    val paddingLeftRatio = trueLeftPercent
    val paddingRightRatio = 1f - trueRightPercent
    
    val gapVerticalRatio = if (gridRows > 1) trueGapYPercent / idealAspectRatio else trueGapXPercent
    val gapHorizontalRatio = if (gridColumns > 1) trueGapXPercent else gapVerticalRatio

    val photoAspectRatio = (trueSlotWidthPercent * idealAspectRatio) / trueSlotHeightPercent

    // Raw Pixel Data for user information
    val rawSlotW = (trueSlotWidthPercent * width).toInt()
    val rawSlotH = (trueSlotHeightPercent * height).toInt()
    val rawPadTop = (trueTopPercent * height).toInt()
    val rawPadBottom = ((1f - trueBottomPercent) * height).toInt()
    val rawPadLeft = (trueLeftPercent * width).toInt()
    val rawPadRight = ((1f - trueRightPercent) * width).toInt()
    val rawGapY = (trueGapYPercent * height).toInt()
    val rawGapX = (trueGapXPercent * width).toInt()

    return """
    // --- BẢNG THÔNG SỐ PIXEL THẬT (PRO MODE) ---
    // Khung lưới: $gridColumns cột x $gridRows hàng
    // Kích thước ảnh thật (sau bleed): ${rawSlotW}px x ${rawSlotH}px
    // Khoảng cách (Gap X/Y): ${rawGapX}px / ${rawGapY}px
    // Padding (T/B/L/R): ${rawPadTop}px / ${rawPadBottom}px / ${rawPadLeft}px / ${rawPadRight}px
    // -------------------------------------------
    LayoutMode(
        id = "$idName",
        title = "Tên Khung",
        subtitle = "$printSizeLabel",
        description = "Mô tả khung",
        family = LayoutFamily.Grid,
        shotCount = ${slots.size},
        selectCount = ${slots.size},
        basePrice = 50000L,
        mediaLabel = "In 1 ảnh $printSizeLabel",
        accentColor = 0xFF475569,
        gridColumns = $gridColumns,
        printSizeLabel = "$printSizeLabel",
        printAspectRatio = $printAspectRatioStr,
        photoAspectRatio = ${String.format("%.4f", photoAspectRatio)}f,
        paddingTopRatio = ${String.format("%.4f", paddingTopRatio)}f,
        paddingBottomRatio = ${String.format("%.4f", paddingBottomRatio)}f,
        paddingLeftRatio = ${String.format("%.4f", paddingLeftRatio)}f,
        paddingRightRatio = ${String.format("%.4f", paddingRightRatio)}f,
        gapHorizontalRatio = ${String.format("%.4f", gapHorizontalRatio)}f,
        gapVerticalRatio = ${String.format("%.4f", gapVerticalRatio)}f
    )
    """.trimIndent()
}
