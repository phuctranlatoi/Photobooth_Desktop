package com.phuctran.photobooth.desktop.services

import com.phuctran.photobooth.desktop.imaging.DesktopImageProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.awt.Robot
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

class HotFolderCaptureService(
    private val hotFolderPath: Path,
    private val imageProcessor: DesktopImageProcessor = DesktopImageProcessor()
) : StillCaptureService {

    private val robot by lazy { runCatching { Robot() }.getOrNull() }

    override val liveViewStream: kotlinx.coroutines.flow.StateFlow<androidx.compose.ui.graphics.ImageBitmap?> = 
        kotlinx.coroutines.flow.MutableStateFlow(null)
        
    override val lastProcessedFrame: java.awt.image.BufferedImage? = null

    override fun startLiveView(effectId: String) {
        // No-op for HotFolder
    }

    override fun stopLiveView() {
        // No-op for HotFolder
    }

    override fun cameraNames(): List<String> {
        return listOf("Sony Camera (Hot Folder)")
    }

    override fun captureJpeg(outputDir: Path, shotIndex: Int, photoAspectRatio: Float, effectId: String): Path? {
        Files.createDirectories(hotFolderPath)
        
        val existingFiles = Files.list(hotFolderPath).use { stream -> 
            stream.map { it.name }.toList() 
        }
        
        // Đã gỡ bỏ trigger ngầm vì máy ảnh kén phần mềm.
        // Khách hàng sẽ tự dùng Clicker Bluetooth để chụp. 
        // App chỉ nằm chờ file rớt vào Hot Folder.
        
        var newFile: Path? = null
        runBlocking {
            for (i in 1..150) { // 15s timeout
                val currentFiles = Files.list(hotFolderPath).use { stream -> stream.toList() }
                val newlyAdded = currentFiles.firstOrNull { 
                    it.name !in existingFiles && it.name.lowercase().endsWith(".jpg") 
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
