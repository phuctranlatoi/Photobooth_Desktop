package com.phuctran.photobooth.desktop.services

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.github.sarxos.webcam.Webcam
import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import com.phuctran.photobooth.desktop.imaging.DesktopImageProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.Dimension
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

class EosUtilityCaptureService(
    private val hotFolderPath: Path,
    private val imageProcessor: DesktopImageProcessor = DesktopImageProcessor()
) : StillCaptureService {

    private val _liveViewStream = MutableStateFlow<ImageBitmap?>(null)
    override val liveViewStream: StateFlow<ImageBitmap?> = _liveViewStream
    override val lastProcessedFrame: java.awt.image.BufferedImage? = null

    private var webcam: Webcam? = null
    private var liveViewJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun cameraNames(): List<String> {
        return try {
            val process = ProcessBuilder("tasklist", "/FI", "IMAGENAME eq EOS Utility 3.exe").start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (output.contains("EOS Utility 3.exe")) {
                listOf("Canon EOS R50 (Utility)")
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun startLiveView(effectId: String) {
        liveViewJob?.cancel()
        liveViewJob = scope.launch(Dispatchers.IO) {
            var webcamStarted = false
            while (isActive) {
                // Ưu tiên chụp màn hình cửa sổ EOS Utility Live View Shoot
                var image = WindowCaptureHelper.captureWindow("Live View")

                if (image == null) {
                    // Fallback to sarxos webcam if window is not found
                    if (!webcamStarted) {
                        try {
                            webcam = Webcam.getDefault()
                            if (webcam != null) {
                                val isObs = webcam!!.name.contains("OBS", ignoreCase = true)
                                if (isObs) {
                                    val sizes = arrayOf(Dimension(1920, 1080), Dimension(1280, 720), Dimension(640, 480))
                                    webcam!!.setCustomViewSizes(*sizes)
                                    webcam!!.viewSize = Dimension(1920, 1080)
                                } else {
                                    val sizes = webcam!!.viewSizes
                                    webcam!!.viewSize = sizes.maxByOrNull { it.width * it.height } ?: Dimension(640, 480)
                                }
                                webcam!!.open(true)
                            }
                            webcamStarted = true
                        } catch (e: Exception) {
                            println("Lỗi mở Webcam cho Live View: ${e.message}")
                        }
                    }
                    try {
                        image = webcam?.image
                    } catch (e: Exception) {
                        println("Lỗi đọc frame Webcam: ${e.message}")
                    }
                } else {
                    // Nếu tìm thấy cửa sổ EOS, đóng webcam nếu đang mở
                    if (webcamStarted) {
                        try { webcam?.close() } catch (e: Exception) {}
                        webcamStarted = false
                    }
                }

                image?.let { img ->
                    try {
                        val processed = if (effectId != "none") {
                            imageProcessor.applyEffectForLiveView(img, effectId)
                        } else img
                        _liveViewStream.value = processed.toComposeImageBitmap()
                    } catch (e: Exception) {
                        // Bỏ qua lỗi render frame
                    }
                }
                delay(1000L / 30L) // ~30fps
            }
        }
    }

    override fun stopLiveView() {
        liveViewJob?.cancel()
        liveViewJob = null
        try {
            webcam?.close()
        } catch (e: Exception) {}
        webcam = null
    }

    fun shutdown() {
        stopLiveView()
        scope.cancel()
    }

    override fun captureJpeg(outputDir: Path, shotIndex: Int, photoAspectRatio: Float, effectId: String): Path? {
        Files.createDirectories(hotFolderPath)
        Files.createDirectories(outputDir)
        
        val existingFiles = Files.walk(hotFolderPath).use { stream -> 
            stream.filter { Files.isRegularFile(it) && it.name.lowercase().endsWith(".jpg") }
                  .map { it.toAbsolutePath().toString() }
                  .toList() 
        }

        // Bắn tín hiệu sang EOS Utility để chụp ngầm (Giả lập bấm phím Spacebar gửi trực tiếp vào cửa sổ)
        // Cách này không làm mất focus của ứng dụng Photobooth
        val triggered = WindowCaptureHelper.sendSpaceToWindow("EOS Utility") || WindowCaptureHelper.sendSpaceToWindow("Live View")
        
        if (!triggered) {
            println("Cảnh báo: Không tìm thấy cửa sổ EOS Utility để gửi lệnh chụp")
        }
        
        // Fail fast if EOS window was not found
        val maxRetries = if (triggered) 150 else 10 // 15s timeout if triggered, 1s if not (mock mode)
        
        var newFile: Path? = null
        runBlocking {
            for (i in 1..maxRetries) { 
                val currentFiles = Files.walk(hotFolderPath).use { stream -> 
                    stream.filter { Files.isRegularFile(it) && it.name.lowercase().endsWith(".jpg") }.toList() 
                }
                val newlyAdded = currentFiles.firstOrNull { 
                    it.toAbsolutePath().toString() !in existingFiles 
                }
                
                if (newlyAdded != null && Files.size(newlyAdded) > 0) {
                    delay(500) // Đợi file ghi xong
                    newFile = newlyAdded
                    break
                }
                delay(100)
            }
        }
        
        if (newFile == null) return null
        
        val result = imageProcessor.saveBoothCapture(
            source = newFile!!,
            outputDir = outputDir,
            shotIndex = shotIndex,
            photoAspectRatio = photoAspectRatio,
            effectId = effectId
        )
        
        runCatching { Files.deleteIfExists(newFile!!) }
        
        return result
    }
}
