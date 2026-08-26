package com.phuctran.photobooth.desktop.services

import androidx.compose.ui.graphics.ImageBitmap
import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import com.phuctran.photobooth.desktop.imaging.DesktopImageProcessor
import androidx.compose.ui.graphics.toComposeImageBitmap
import edsdk.api.CanonCamera
import edsdk.utils.CanonConstants.EdsSaveTo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.deleteIfExists

class NativeEosCaptureService(
    private val config: DesktopBoothConfig,
    private val imageProcessor: DesktopImageProcessor = DesktopImageProcessor()
) : StillCaptureService {

    private val _liveViewStream = MutableStateFlow<ImageBitmap?>(null)
    override val liveViewStream: StateFlow<ImageBitmap?> = _liveViewStream
    override val lastProcessedFrame: BufferedImage? = null

    private val camera: CanonCamera = CanonCamera()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var liveViewJob: Job? = null
    private var sessionOpen = false

    init {
        try {
            sessionOpen = camera.openSession()
            if (sessionOpen) {
                println("Native EDSDK: Camera session opened successfully.")
            } else {
                println("Native EDSDK: Failed to open camera session (Camera not connected or turned off?).")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getAvailableIsoSpeeds(): List<String> = runCatching { camera.availableISOSpeeds?.map { it.description() } ?: emptyList() }.getOrDefault(emptyList())
    fun getCurrentIso(): String = runCatching { camera.isoSpeed?.description() ?: "" }.getOrDefault("")
    fun setIso(desc: String) { runCatching { camera.setISOSpeed(edsdk.utils.CanonConstants.EdsISOSpeed.enumOfDescription(desc)) } }

    fun getAvailableApertures(): List<String> = runCatching { camera.availableApertureValues?.map { it.description() } ?: emptyList() }.getOrDefault(emptyList())
    fun getCurrentAperture(): String = runCatching { camera.apertureValue?.description() ?: "" }.getOrDefault("")
    fun setAperture(desc: String) { runCatching { camera.setApertureValue(edsdk.utils.CanonConstants.EdsAv.enumOfDescription(desc)) } }

    fun getAvailableShutterSpeeds(): List<String> = runCatching { camera.availableShutterSpeeds?.map { it.description() } ?: emptyList() }.getOrDefault(emptyList())
    fun getCurrentShutterSpeed(): String = runCatching { camera.shutterSpeed?.description() ?: "" }.getOrDefault("")
    fun setShutterSpeed(desc: String) { runCatching { camera.setShutterSpeed(edsdk.utils.CanonConstants.EdsTv.enumOfDescription(desc)) } }

    fun setWhiteBalanceSafe(wbEnumName: String) {
        runCatching {
            val enumVal = edsdk.utils.CanonConstants.EdsWhiteBalance.valueOf(wbEnumName)
            camera.setWhiteBalance(enumVal)
        }
    }

    fun setPictureStyleSafe(psEnumName: String) {
        runCatching {
            val enumVal = edsdk.utils.CanonConstants.EdsPictureStyle.valueOf(psEnumName)
            camera.setPictureStyle(enumVal)
        }
    }

    override fun cameraNames(): List<String> {
        return listOf("Canon EDSDK (Native)")
    }

    override fun startLiveView(effectId: String) {
        if (!sessionOpen || liveViewJob?.isActive == true) return

        liveViewJob = scope.launch {
            var liveViewStarted = false
            var retryCount = 0
            while (!liveViewStarted && retryCount < 10 && isActive) {
                if (camera.beginLiveView()) {
                    liveViewStarted = true
                    println("Native EDSDK: Live view started successfully.")
                } else {
                    println("Native EDSDK: Camera busy, retrying live view... ($retryCount)")
                    retryCount++
                    delay(500)
                }
            }

            if (!liveViewStarted) {
                println("Native EDSDK: Failed to start live view after multiple attempts.")
                return@launch
            }

            while (isActive) {
                try {
                    val image = camera.downloadLiveView()
                    if (image != null) {
                        val processed = imageProcessor.applyEffectForLiveView(image, effectId)
                        _liveViewStream.value = processed.toComposeImageBitmap()
                        image.flush()
                    }
                } catch (e: Exception) {
                    // Suppress intermittent download errors
                }
                delay(30) // ~30 fps request rate
            }
        }
    }

    override fun stopLiveView() {
        liveViewJob?.cancel()
        liveViewJob = null
        if (sessionOpen) {
            camera.endLiveView()
        }
    }

    override fun captureJpeg(outputDir: Path, shotIndex: Int, photoAspectRatio: Float, effectId: String): Path? {
        if (!sessionOpen) return null

        Files.createDirectories(outputDir)
        
        println("Native EDSDK: Executing capture...")
        var resultPath: Path? = null
        try {
            // Sử dụng SafeShootCommand để tránh lỗi ArrayIndexOutOfBoundsException của edsdk4j
            val photos = camera.execute(edsdk.api.commands.SafeShootCommand(edsdk.utils.CanonConstants.EdsSaveTo.kEdsSaveTo_Host, 3)).get(5)
            if (photos != null && photos.isNotEmpty()) {
                val photoFile = photos.last()
                val tempFile = photoFile.toPath()
                
                resultPath = imageProcessor.saveBoothCapture(
                    source = tempFile,
                    outputDir = outputDir,
                    shotIndex = shotIndex,
                    photoAspectRatio = photoAspectRatio,
                    effectId = effectId
                )
                
                runCatching { tempFile.deleteIfExists() }
            } else {
                println("Native EDSDK: Capture failed (AF NG or SaveTo error).")
                // Quan trọng: Nếu shoot() thất bại, edsdk4j sẽ không tự bật lại Live View.
                // Chúng ta phải tự bật lại nếu liveViewJob đang chạy.
                if (liveViewJob?.isActive == true) {
                    println("Native EDSDK: Attempting to resume Live View after failed capture...")
                    camera.beginLiveView()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (liveViewJob?.isActive == true) camera.beginLiveView()
        }

        return resultPath
    }
}

