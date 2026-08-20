package com.phuctran.photobooth.desktop.services

import androidx.compose.ui.graphics.ImageBitmap
import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import com.phuctran.photobooth.desktop.imaging.DesktopImageProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.isActive

class CliCaptureService(
    private val config: DesktopBoothConfig,
    private val imageProcessor: DesktopImageProcessor = DesktopImageProcessor()
) : StillCaptureService {

    private val _liveViewStream = MutableStateFlow<ImageBitmap?>(null)
    override val liveViewStream: StateFlow<ImageBitmap?> = _liveViewStream
    override val lastProcessedFrame: java.awt.image.BufferedImage? = null

    override fun cameraNames(): List<String> {
        return listOf("Canon R100 (CLI)")
    }

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private var liveViewJob: kotlinx.coroutines.Job? = null

    override fun startLiveView(effectId: String) {
        if (liveViewJob?.isActive == true) return
        
        liveViewJob = scope.launch {
            while (isActive) {
                // Thử tìm cửa sổ của digiCamControl (Live view) hoặc EOS Utility
                var image = com.phuctran.photobooth.desktop.services.WindowCaptureHelper.captureWindow("Live view")
                if (image == null) {
                    image = com.phuctran.photobooth.desktop.services.WindowCaptureHelper.captureWindow("EOS Utility")
                }
                
                if (image != null) {
                    val processed = imageProcessor.applyEffectForLiveView(image, effectId)
                    _liveViewStream.value = processed.toComposeImageBitmap()
                }
                kotlinx.coroutines.delay(16) // Thử poll nhanh hơn (16ms = ~60fps logic)
            }
        }
    }

    override fun stopLiveView() {
        liveViewJob?.cancel()
        liveViewJob = null
    }



    override fun captureJpeg(outputDir: Path, shotIndex: Int, photoAspectRatio: Float, effectId: String): Path? {
        Files.createDirectories(outputDir)
        
        val tempRawFile = outputDir.resolve("raw_capture_${System.currentTimeMillis()}_$shotIndex.jpg")
        val command = config.cliCaptureCommand.replace("%s", tempRawFile.absolutePathString())
        
        try {
            println("Executing CLI camera command: $command")
            val process = Runtime.getRuntime().exec(command)
            val exitCode = process.waitFor()
            println("CLI command exited with code: $exitCode")

            var fileExists = false
            for (i in 0 until 50) { // 5s timeout
                if (Files.exists(tempRawFile) && Files.size(tempRawFile) > 0) {
                    fileExists = true
                    Thread.sleep(200)
                    break
                }
                Thread.sleep(100)
            }

            if (!fileExists) {
                println("Failed to find raw camera file at ${tempRawFile.absolutePathString()}")
                return null
            }

            val resultPath = imageProcessor.saveBoothCapture(
                source = tempRawFile,
                outputDir = outputDir,
                shotIndex = shotIndex,
                photoAspectRatio = photoAspectRatio,
                effectId = effectId
            )
            
            runCatching { Files.deleteIfExists(tempRawFile) }
            
            return resultPath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
