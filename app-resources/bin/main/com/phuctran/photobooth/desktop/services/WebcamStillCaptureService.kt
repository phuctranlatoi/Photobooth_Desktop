package com.phuctran.photobooth.desktop.services

import com.github.sarxos.webcam.Webcam
import com.phuctran.photobooth.desktop.imaging.DesktopImageProcessor
import java.awt.Dimension
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

interface StillCaptureService {
    val liveViewStream: StateFlow<ImageBitmap?>
    val lastProcessedFrame: java.awt.image.BufferedImage?
    fun cameraNames(): List<String>
    fun startLiveView(effectId: String = "normal")
    fun stopLiveView()
    fun captureJpeg(outputDir: Path, shotIndex: Int, photoAspectRatio: Float, effectId: String = "normal"): Path?
}

class WebcamStillCaptureService(
    private val imageProcessor: DesktopImageProcessor = DesktopImageProcessor()
) : StillCaptureService {

    private val _liveViewStream = kotlinx.coroutines.flow.MutableStateFlow<ImageBitmap?>(null)
    override val liveViewStream: StateFlow<ImageBitmap?> = _liveViewStream
    
    @Volatile
    override var lastProcessedFrame: java.awt.image.BufferedImage? = null
        private set

    private var liveViewJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)

    override fun cameraNames(): List<String> = runCatching {
        Webcam.getWebcams().map { it.name }.sortedByDescending { it.contains("DroidCam", ignoreCase = true) }
    }.getOrDefault(emptyList())

    private var cachedWebcam: Webcam? = null

    private fun getWebcam(): Webcam? {
        if (cachedWebcam == null) {
            val webcams = runCatching { Webcam.getWebcams() }.getOrDefault(emptyList())
            cachedWebcam = webcams.firstOrNull { it.name.contains("DroidCam", ignoreCase = true) }
                ?: runCatching { Webcam.getDefault() }.getOrNull()
        }
        return cachedWebcam
    }

    private fun java.awt.image.BufferedImage.centerCropToAspect(targetAspectRatio: Float): java.awt.image.BufferedImage {
        val sourceAspectRatio = width.toFloat() / height.toFloat()
        if (kotlin.math.abs(sourceAspectRatio - targetAspectRatio) < 0.01f) return this

        val cropWidth: Int
        val cropHeight: Int
        if (sourceAspectRatio > targetAspectRatio) {
            cropHeight = height
            cropWidth = (height * targetAspectRatio).toInt().coerceAtMost(width)
        } else {
            cropWidth = width
            cropHeight = (width / targetAspectRatio).toInt().coerceAtMost(height)
        }

        val left = ((width - cropWidth) / 2).coerceAtLeast(0)
        val top = ((height - cropHeight) / 2).coerceAtLeast(0)
        return getSubimage(left, top, cropWidth.coerceAtLeast(1), cropHeight.coerceAtLeast(1))
    }

    override fun startLiveView(effectId: String) {
        if (liveViewJob?.isActive == true) {
            stopLiveView()
        }
        
        val webcam = getWebcam() ?: return
        openAtBestSize(webcam)
        
        liveViewJob = scope.launch {
            while (isActive) {
                try {
                    val frame = webcam.image
                    if (frame != null) {
                        val cropped169 = frame.centerCropToAspect(16f / 9f)
                        val filtered = imageProcessor.applyEffectForLiveView(cropped169, effectId)
                        lastProcessedFrame = filtered
                        _liveViewStream.value = filtered.toComposeImageBitmap()
                    }
                } catch (e: Exception) {
                    // Ignore frame drop
                }
                kotlinx.coroutines.delay(33) // ~30fps
            }
        }
    }

    override fun stopLiveView() {
        liveViewJob?.cancel()
        liveViewJob = null
        _liveViewStream.value = null
    }

    @Synchronized
    override fun captureJpeg(outputDir: Path, shotIndex: Int, photoAspectRatio: Float, effectId: String): Path? {
        val webcam = getWebcam() ?: return null
        openAtBestSize(webcam)
        val image = runCatching { webcam.image }.getOrNull() ?: return null
        val cropped169 = image.centerCropToAspect(16f / 9f)

        return imageProcessor.saveBoothCapture(
            image = cropped169,
            outputDir = outputDir,
            shotIndex = shotIndex,
            photoAspectRatio = photoAspectRatio,
            effectId = effectId
        )
    }

    private fun openAtBestSize(webcam: Webcam) {
        if (webcam.isOpen) return
        val bestSize = webcam.viewSizes
            .filterNotNull()
            .maxByOrNull { it.width * it.height }
            ?: Dimension(1280, 720)
        runCatching { webcam.viewSize = bestSize }
        webcam.open()
    }
}
