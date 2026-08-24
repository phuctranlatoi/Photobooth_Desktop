package com.phuctran.photobooth.desktop.controller

import com.phuctran.photobooth.desktop.config.DesktopAppPaths
import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import com.phuctran.photobooth.desktop.domain.SessionState
import com.phuctran.photobooth.desktop.domain.SessionStateMachine
import com.phuctran.photobooth.desktop.imaging.DesktopCompositor
import com.phuctran.photobooth.desktop.imaging.DesktopImageProcessor
import com.phuctran.photobooth.desktop.model.BoothSession
import com.phuctran.photobooth.desktop.model.CapturedMoment
import com.phuctran.photobooth.desktop.model.DefaultEffectModes
import com.phuctran.photobooth.desktop.model.DefaultLayoutModes
import com.phuctran.photobooth.desktop.model.EffectMode
import com.phuctran.photobooth.desktop.model.ExportSummary
import com.phuctran.photobooth.desktop.model.FramePack
import com.phuctran.photobooth.desktop.model.LayoutMode
import com.phuctran.photobooth.desktop.services.DesktopAlbumUploader
import com.phuctran.photobooth.desktop.services.LocalFileServer
import com.phuctran.photobooth.desktop.services.PrinterService
import com.phuctran.photobooth.desktop.services.StillCaptureService
import com.phuctran.photobooth.desktop.services.SystemPrinterService
import com.phuctran.photobooth.desktop.services.WebcamStillCaptureService
import com.phuctran.photobooth.desktop.storage.DesktopSessionStore
import com.phuctran.photobooth.desktop.storage.FrameStore
import com.phuctran.photobooth.desktop.services.PaymentService
import com.phuctran.photobooth.desktop.utils.NetworkUtility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class DesktopBoothController(
    private val projectDir: Path,
    private val config: DesktopBoothConfig,
    private val stateMachine: SessionStateMachine = SessionStateMachine(),
    private val frameStore: FrameStore = FrameStore(projectDir),
    private val sessionStore: DesktopSessionStore = DesktopSessionStore(projectDir),
    private val compositor: DesktopCompositor = DesktopCompositor(),
    private val imageProcessor: DesktopImageProcessor = DesktopImageProcessor(),
    private val albumUploader: DesktopAlbumUploader = DesktopAlbumUploader(projectDir, config),
    private val printerService: PrinterService = SystemPrinterService(),
    private val cameraService: StillCaptureService = when {
        config.useNativeSdk -> com.phuctran.photobooth.desktop.services.NativeEosCaptureService(config, imageProcessor)
        config.useCliCamera -> com.phuctran.photobooth.desktop.services.CliCaptureService(config, imageProcessor)
        config.useHotFolder -> com.phuctran.photobooth.desktop.services.HotFolderCaptureService(Path.of(config.hotFolderPath), imageProcessor)
        else -> com.phuctran.photobooth.desktop.services.WebcamStillCaptureService(imageProcessor)
    },
    private val paymentService: PaymentService = PaymentService(config),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    val nativeCamera: com.phuctran.photobooth.desktop.services.NativeEosCaptureService?
        get() = cameraService as? com.phuctran.photobooth.desktop.services.NativeEosCaptureService

    private val localServer: LocalFileServer? = if (config.enableLocalServer) {
        LocalFileServer(projectDir.resolve("data").resolve("output"), config.localServerPort)
    } else null

    val sessionState: StateFlow<SessionState> = stateMachine.currentState

    private val _isAppReady = MutableStateFlow(false)
    val isAppReady = _isAppReady.asStateFlow()

    private val _availableLayouts = MutableStateFlow(DefaultLayoutModes)
    val availableLayouts = _availableLayouts.asStateFlow()

    private val _selectedLayout = MutableStateFlow(DefaultLayoutModes.first())
    val selectedLayout = _selectedLayout.asStateFlow()

    private val _selectedEffect = MutableStateFlow(DefaultEffectModes.first())
    val selectedEffect = _selectedEffect.asStateFlow()

    private val _availableFrames = MutableStateFlow<List<com.phuctran.photobooth.desktop.model.FramePack>>(emptyList())
    val availableFrames = _availableFrames.asStateFlow()

    private val _selectedFrame = MutableStateFlow(com.phuctran.photobooth.desktop.model.FramePack(
        id = "empty",
        title = "Chưa có khung ảnh",
        description = "Vui lòng vào Cài Đặt -> Tạo Layout/Frame để lưu khung ảnh cho bố cục này.",
        accentColor = 0xFF5F6B7A
    ))
    val selectedFrame = _selectedFrame.asStateFlow()

    private val _printCopies = MutableStateFlow(1)
    val printCopies = _printCopies.asStateFlow()

    private val _capturedMoments = MutableStateFlow<List<CapturedMoment>>(emptyList())
    val capturedMoments = _capturedMoments.asStateFlow()

    private val _selectedMoments = MutableStateFlow<List<CapturedMoment>>(emptyList())
    val selectedMoments = _selectedMoments.asStateFlow()

    private val _captureSources = MutableStateFlow<List<Path>>(emptyList())
    val captureSources = _captureSources.asStateFlow()

    private val _cameraDevices = MutableStateFlow<List<String>>(emptyList())
    val cameraDevices = _cameraDevices.asStateFlow()

    private val _countdown = MutableStateFlow(0)
    val countdown = _countdown.asStateFlow()

    private val _totalPrice = MutableStateFlow(0L)
    val totalPrice = _totalPrice.asStateFlow()

    private val _exportSummary = MutableStateFlow(ExportSummary(0, 0, 0))
    val exportSummary = _exportSummary.asStateFlow()

    private val _statusMessage = MutableStateFlow("Sẵn sàng.")
    val statusMessage = _statusMessage.asStateFlow()

    private var captureJob: Job? = null
    private var deliveryJob: Job? = null
    private var paymentPollingJob: Job? = null
    private var currentSessionId: String? = null

    private val _paymentQrData = MutableStateFlow<String?>(null)
    val paymentQrData = _paymentQrData.asStateFlow()

    val isPaymentConfigured: Boolean get() = paymentService.isConfigured

    companion object {
        const val CAPTURE_PREP_SECONDS = 3
        const val RECORDING_SECONDS = 4
    }

    init {
        scope.launch {
            try {
                val layoutsFromFirebase = com.phuctran.photobooth.desktop.remote.FirebaseManager.fetchLayouts()
                if (layoutsFromFirebase.isNotEmpty()) {
                    _availableLayouts.value = layoutsFromFirebase
                    _selectedLayout.value = layoutsFromFirebase.first()
                }
            } catch (e: Exception) {
                println("Failed to fetch layouts from Firebase: ${e.message}")
            }
            refreshFramesForLayout(_selectedLayout.value, preserveSelection = false)
            refreshCameraDevices()
            localServer?.start()
            
            // Start live view early to attract customers on StartScreen
            cameraService.startLiveView(_selectedEffect.value.id)
            
            _isAppReady.value = true
        }
    }

    fun transitionTo(newState: SessionState) {
        stateMachine.transitionTo(newState)
        if (newState == SessionState.IDLE) {
            // Reset to normal effect when returning to idle
            chooseEffect(com.phuctran.photobooth.desktop.model.DefaultEffectModes.first().id)
        }
    }

    fun chooseLayout(layoutId: String) {
        val layout = _availableLayouts.value.firstOrNull { it.id == layoutId } ?: return
        _selectedLayout.value = layout
        _selectedMoments.value = emptyList()
        _capturedMoments.value = emptyList()
        _exportSummary.value = ExportSummary(0, 0, 0)
        refreshFramesForLayout(layout, preserveSelection = false)
        _statusMessage.value = "Đã chọn ${layout.mediaLabel}."
    }

    val liveViewStream: StateFlow<androidx.compose.ui.graphics.ImageBitmap?> = cameraService.liveViewStream

    fun chooseEffect(effectId: String) {
        com.phuctran.photobooth.desktop.model.DefaultEffectModes.firstOrNull { it.id == effectId }?.let {
            _selectedEffect.value = it
            _statusMessage.value = "Đã chọn màu ${it.title}."
            // Always update live view with the new effect so they can preview it in SELECTING screen
            cameraService.startLiveView(it.id)
        }
    }

    fun confirmStudioSetup() {
        _printCopies.value = 1
        _totalPrice.value = _selectedLayout.value.basePrice
        transitionTo(SessionState.SELECTING_QUANTITY)
    }

    fun setQuantityAndStartPayment(quantity: Int) {
        val safeQuantity = quantity.coerceIn(1, 4)
        _printCopies.value = safeQuantity
        _totalPrice.value = _selectedLayout.value.basePrice * safeQuantity
        transitionTo(SessionState.PAYMENT_PENDING)
        
        paymentPollingJob?.cancel()
        _paymentQrData.value = null
        if (paymentService.isConfigured) {
            val orderCode = System.currentTimeMillis()
            val amount = _totalPrice.value.toInt()
            scope.launch(Dispatchers.IO) {
                val qr = paymentService.createPaymentLink(orderCode, amount, "Photobooth $orderCode")
                _paymentQrData.value = qr
                if (qr != null) {
                    paymentPollingJob = scope.launch(Dispatchers.IO) {
                        while (isActive) {
                            if (paymentService.checkPaymentStatus(orderCode)) {
                                completePayment()
                                break
                            }
                            delay(3000)
                        }
                    }
                }
            }
        }
    }

    fun completePayment() {
        paymentPollingJob?.cancel()
        paymentPollingJob = null
        scope.launch {
            transitionTo(SessionState.PREPARING)
            cameraService.startLiveView(_selectedEffect.value.id)
            delay(1600)
            transitionTo(SessionState.LIVE_VIEW)
        }
    }

    fun addCaptureSources(paths: List<Path>) {
        val valid = paths.filter { Files.isRegularFile(it) }
        _captureSources.value = _captureSources.value + valid
        _statusMessage.value = "Đã thêm ${valid.size} ảnh nguồn capture."
    }

    fun clearCaptureSources() {
        _captureSources.value = emptyList()
        _statusMessage.value = "Đã xoá ảnh nguồn capture."
    }

    fun refreshCameraDevices() {
        scope.launch(Dispatchers.IO) {
            val devices = cameraService.cameraNames()
            _cameraDevices.value = devices
            if (devices.isNotEmpty()) {
                _statusMessage.value = "Tìm thấy camera: ${devices.joinToString(", ")}."
            }
        }
    }

    private val _isRecordingVideo = MutableStateFlow(false)
    val isRecordingVideo = _isRecordingVideo.asStateFlow()

    fun startCaptureFlow() {
        if (captureJob?.isActive == true) return

        captureJob = scope.launch {
            currentSessionId = currentSessionId ?: UUID.randomUUID().toString()
            _capturedMoments.value = emptyList()
            _selectedMoments.value = emptyList()
            _exportSummary.value = ExportSummary(0, 0, 0)

            val layout = _selectedLayout.value
            for (index in 1..layout.shotCount) {
                val videoFramesDir = projectDir
                    .resolve("data")
                    .resolve("sessions")
                    .resolve(currentSessionId!!)
                    .resolve("videos")
                    .resolve("shot_$index")
                Files.createDirectories(videoFramesDir)
                
                var videoJob: kotlinx.coroutines.Job? = null

                transitionTo(SessionState.COUNTDOWN)
                for (seconds in CAPTURE_PREP_SECONDS downTo 1) {
                    _countdown.value = seconds
                    _statusMessage.value = "Chuẩn bị ảnh $index sau $seconds giây."
                    
                    if (seconds <= RECORDING_SECONDS && videoJob == null) {
                        _isRecordingVideo.value = true
                        videoJob = scope.launch(Dispatchers.IO) {
                            var frameIdx = 1
                            while (_isRecordingVideo.value && isActive) {
                                val frame = cameraService.lastProcessedFrame
                                if (frame != null) {
                                    val frameFile = videoFramesDir.resolve("frame_%03d.jpg".format(frameIdx++))
                                    try {
                                        javax.imageio.ImageIO.write(frame, "jpg", frameFile.toFile())
                                    } catch (e: Exception) {}
                                }
                                delay(100) // 10 fps
                            }
                        }
                    }
                    delay(1000)
                }
                _countdown.value = 0 // Báo hiệu đang chụp/đang lưu ảnh

                transitionTo(SessionState.CAPTURING)
                val photoPath = withContext(kotlinx.coroutines.Dispatchers.IO) { captureStill(index, layout) }
                    ?: withContext(Dispatchers.IO) {
                        // Dummy image for testing without real camera
                        val dummyPath = projectDir.resolve("data").resolve("sessions").resolve(currentSessionId!!).resolve("originals").resolve("dummy_$index.jpg")
                        java.nio.file.Files.createDirectories(dummyPath.parent)
                        val img = java.awt.image.BufferedImage(1200, 1800, java.awt.image.BufferedImage.TYPE_INT_RGB)
                        val g = img.createGraphics()
                        val colors = arrayOf(
                            java.awt.Color(220, 53, 69),   // Red
                            java.awt.Color(40, 167, 69),   // Green
                            java.awt.Color(0, 123, 255),   // Blue
                            java.awt.Color(255, 193, 7),   // Yellow
                            java.awt.Color(23, 162, 184),  // Cyan
                            java.awt.Color(111, 66, 193),  // Purple
                            java.awt.Color(253, 126, 20),  // Orange
                            java.awt.Color(32, 201, 151)   // Teal
                        )
                        g.color = colors[(index - 1) % colors.size]
                        g.fillRect(0, 0, 1200, 1800)
                        g.color = java.awt.Color.WHITE
                        g.font = java.awt.Font("Arial", java.awt.Font.BOLD, 150)
                        g.drawString("MOCK $index", 300, 900)
                        g.dispose()
                        javax.imageio.ImageIO.write(img, "jpg", dummyPath.toFile())
                        dummyPath
                    }
                
                // Encode video concurrently in the background so it doesn't block the next shot
                val videoPathFuture = scope.async(Dispatchers.IO) { encodeVideo(videoFramesDir, index) }
                
                _capturedMoments.value = _capturedMoments.value + CapturedMoment(
                    index = index,
                    photoLabel = "Ảnh $index",
                    videoLabel = "Video $index",
                    photoPath = photoPath,
                    videoPath = null // Will be updated when async encode finishes
                )
                
                scope.launch {
                    val finalVideoPath = videoPathFuture.await()
                    if (finalVideoPath != null) {
                        _capturedMoments.value = _capturedMoments.value.map {
                            if (it.index == index) it.copy(videoPath = finalVideoPath) else it
                        }
                    }
                }

                transitionTo(SessionState.LIVE_VIEW)
                delay(700)
            }

            _exportSummary.value = ExportSummary(
                printPhotoCount = 0,
                uploadedPhotoCount = _capturedMoments.value.size,
                uploadedVideoCount = 0 // Will be updated during delivery
            )
            cameraService.stopLiveView()
            _statusMessage.value = "Đã chụp ${_capturedMoments.value.size} ảnh. Chọn ảnh để in."
            transitionTo(SessionState.SELECTING_PHOTOS)
        }
    }

    private fun encodeVideo(framesDir: Path, shotIndex: Int): Path? {
        val outputFile = framesDir.parent.resolve("video_$shotIndex.mp4")
        return runCatching {
            val process = ProcessBuilder(
                "ffmpeg", "-y", 
                "-framerate", "10", 
                "-i", "${framesDir.toAbsolutePath()}\\frame_%03d.jpg", 
                "-c:v", "libx264", 
                "-preset", "fast",
                "-crf", "28",
                "-pix_fmt", "yuv420p",
                outputFile.toAbsolutePath().toString()
            ).redirectErrorStream(true).start()
            
            val exited = process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
            if (exited && process.exitValue() == 0 && Files.exists(outputFile)) {
                outputFile
            } else {
                process.destroyForcibly()
                null
            }
        }.getOrNull()
    }

    fun toggleMoment(moment: CapturedMoment) {
        val current = _selectedMoments.value.toMutableList()
        if (current.contains(moment)) {
            current.remove(moment)
        } else if (current.size < _selectedLayout.value.selectCount && moment.photoPath != null) {
            current.add(moment)
        }
        _selectedMoments.value = current
    }

    fun confirmSelection() {
        if (_selectedMoments.value.size == _selectedLayout.value.selectCount) {
            refreshFramesForLayout(_selectedLayout.value, preserveSelection = true)
            transitionTo(SessionState.EDITING)
        } else {
            _statusMessage.value = "Cần chọn đủ ${_selectedLayout.value.selectCount} ảnh."
        }
    }

    fun chooseFrame(frameId: String) {
        _availableFrames.value.firstOrNull { it.id == frameId }?.let {
            _selectedFrame.value = it
            _statusMessage.value = "Đã chọn frame ${it.title}."
        }
    }

    fun addCustomFrame(path: Path, layout: LayoutMode, isSpecial: Boolean = false) {
        runCatching { frameStore.addCustomFrame(path, layout.printSizeLabel, layout.id, isSpecial) }
            .onSuccess { frame ->
                refreshFramesForLayout(_selectedLayout.value, preserveSelection = false)
                _selectedFrame.value = frame
                _statusMessage.value = "Đã thêm frame ${frame.title}."
            }
            .onFailure { _statusMessage.value = it.message ?: "Không thêm được frame." }
    }

    fun confirmFrameSelection() {
        _exportSummary.value = ExportSummary(
            printPhotoCount = _selectedMoments.value.size,
            uploadedPhotoCount = _capturedMoments.value.size,
            uploadedVideoCount = 0
        )
        transitionTo(SessionState.PRINT_PENDING)
    }

    fun startPrinting() {
        deliveryJob?.cancel()
        deliveryJob = scope.launch {
            transitionTo(SessionState.PRINTING)
            val sessionId = currentSessionId ?: UUID.randomUUID().toString().also { currentSessionId = it }
            val selectedPhotoPaths = _selectedMoments.value.mapNotNull { it.photoPath }
            val masterFile = runCatching {
                compositor.renderFinal(
                    layout = _selectedLayout.value,
                    frame = _selectedFrame.value,
                    photoPaths = selectedPhotoPaths,
                    outputDir = projectDir.resolve("data").resolve("output")
                ).finalImagePath
            }.getOrNull()

            _statusMessage.value = "Đã render ảnh in. Đang upload album..."

            val printStatus = if (masterFile != null && config.enableSystemPrint) {
                _statusMessage.value = "Đã render ảnh in. Đang gửi sang Windows Print..."
                withContext(Dispatchers.IO) {
                    printerService.printImage(masterFile)
                        .getOrElse { error -> "Không gửi được sang Windows Print: ${error.message}" }
                }
            } else if (masterFile != null) {
                "Đã render file in local. Bật ENABLE_SYSTEM_PRINT=true để gửi sang Windows Print."
            } else {
                "Chưa render được file in."
            }

            _statusMessage.value = "$printStatus Đang upload album..."

            val albumResult = if (config.canUploadAlbum) {
                withContext(Dispatchers.IO) {
                    albumUploader.uploadSessionAlbum(
                        sessionId = sessionId,
                        layout = _selectedLayout.value,
                        frame = _selectedFrame.value,
                        capturedMoments = _capturedMoments.value,
                        selectedMoments = _selectedMoments.value,
                        masterPrint = masterFile
                    )
                }
            } else null

            val summary = if (albumResult != null && albumResult.albumUrl != null) {
                ExportSummary(
                    printPhotoCount = _selectedMoments.value.size,
                    uploadedPhotoCount = albumResult.uploadedCount,
                    uploadedVideoCount = 0,
                    qrUrl = albumResult.albumUrl,
                    masterUrl = albumResult.finalPhotoUrl,
                    albumId = albumResult.albumId,
                    outputPath = masterFile,
                    printStatus = printStatus
                )
            } else if (config.enableLocalServer && masterFile != null) {
                val ip = NetworkUtility.getLocalIpAddress()
                val filename = masterFile.fileName.toString()
                ExportSummary(
                    printPhotoCount = _selectedMoments.value.size,
                    uploadedPhotoCount = 0,
                    uploadedVideoCount = 0,
                    qrUrl = "http://$ip:${config.localServerPort}/download/$filename",
                    masterUrl = "http://$ip:${config.localServerPort}/download/$filename",
                    albumId = null,
                    outputPath = masterFile,
                    printStatus = printStatus
                )
            } else {
                ExportSummary(
                    printPhotoCount = _selectedMoments.value.size,
                    uploadedPhotoCount = 0,
                    uploadedVideoCount = 0,
                    qrUrl = null,
                    masterUrl = null,
                    albumId = null,
                    outputPath = masterFile,
                    printStatus = printStatus
                )
            }

            _exportSummary.value = summary
            
            if (summary.qrUrl != null) {
                _statusMessage.value = "Hoàn tất! Mời quét mã QR để lấy ảnh."
            } else {
                _statusMessage.value = "Hoàn tất! Chờ máy in nhả ảnh nhé."
            }
            
            transitionTo(SessionState.DELIVERY)

            val session = BoothSession(
                id = sessionId,
                boothId = config.boothId,
                productId = _selectedLayout.value.id,
                state = "COMPLETED",
                qrCodeUrl = summary.qrUrl,
                photoUrls = albumResult?.originalPhotoUrls ?: emptyList(),
                videoUrls = emptyList(),
                masterUrl = summary.masterUrl,
                startedAt = System.currentTimeMillis(),
                paidAt = System.currentTimeMillis(),
                completedAt = System.currentTimeMillis()
            )
            withContext(Dispatchers.IO) { sessionStore.saveSession(session) }

            delay(1000)
            transitionTo(SessionState.DELIVERY)
        }
    }

    fun finishSession() {
        deliveryJob?.cancel()
        deliveryJob = null
        resetSession()
    }

    fun goBack() {
        when (sessionState.value) {
            SessionState.SELECTING_QUANTITY -> transitionTo(SessionState.SELECTING)
            SessionState.EDITING -> transitionTo(SessionState.SELECTING_PHOTOS)
            SessionState.PRINT_PENDING -> transitionTo(SessionState.EDITING)
            else -> Unit
        }
    }

    fun resetSession() {
        captureJob?.cancel()
        deliveryJob?.cancel()
        paymentPollingJob?.cancel()
        currentSessionId = null
        cameraService.stopLiveView()
        _capturedMoments.value = emptyList()
        _selectedMoments.value = emptyList()
        _countdown.value = 0
        _printCopies.value = 1
        _selectedFrame.value = _availableFrames.value.firstOrNull() ?: com.phuctran.photobooth.desktop.model.FramePack(
            id = "empty",
            title = "Chưa có khung ảnh",
            description = "Vui lòng vào Cài Đặt -> Tạo Layout/Frame để lưu khung ảnh cho bố cục này.",
            accentColor = 0xFF5F6B7A
        )
        _exportSummary.value = ExportSummary(0, 0, 0)
        _statusMessage.value = "Sẵn sàng."
        stateMachine.reset()
    }

    val activeConfig = MutableStateFlow(config)

    fun saveDeviceSettings(enablePrint: Boolean, useHotFolder: Boolean, hotFolderPath: String) {
        val envFile = config.envSource ?: DesktopAppPaths.workingDir().resolve(".env")
        val updates = mapOf(
            "ENABLE_SYSTEM_PRINT" to enablePrint.toString(),
            "USE_HOT_FOLDER" to useHotFolder.toString(),
            "HOT_FOLDER_PATH" to hotFolderPath
        )
        runCatching {
            com.phuctran.photobooth.desktop.config.SettingsManager.updateSettings(envFile, updates)
        }.onSuccess {
            _statusMessage.value = "Đã lưu cài đặt. Khởi động lại ứng dụng để áp dụng."
            activeConfig.value = config.copy(
                enableSystemPrint = enablePrint,
                useHotFolder = useHotFolder,
                hotFolderPath = hotFolderPath
            )
        }.onFailure { e ->
            _statusMessage.value = "Lỗi lưu cài đặt: ${e.message}"
        }
    }

    private fun refreshFramesForLayout(layout: LayoutMode, preserveSelection: Boolean) {
        val customPacks = frameStore.loadFrames()
            .filter { it.isCustom && it.targetLayoutId == layout.id }
            .sortedWith(
                compareBy(
                    { if (it.targetPrintSize == layout.printSizeLabel || it.targetPrintSize == null) 0 else 1 },
                    { it.targetPrintSize.orEmpty() },
                    { it.title }
                )
            )
        val allFrames = customPacks.ifEmpty { 
            listOf(com.phuctran.photobooth.desktop.model.FramePack(
                id = "empty",
                title = "Chưa có khung ảnh",
                description = "Vui lòng vào Cài Đặt -> Tạo Layout/Frame để lưu khung ảnh cho bố cục này.",
                accentColor = 0xFF5F6B7A
            ))
        }
        val previousFrameId = _selectedFrame.value.id
        _availableFrames.value = allFrames
        _selectedFrame.value = if (preserveSelection) {
            allFrames.firstOrNull { it.id == previousFrameId } ?: allFrames.first()
        } else {
            allFrames.first()
        }
    }

    private fun captureStill(index: Int, layout: LayoutMode): Path? {
        val sources = _captureSources.value
        val sessionId = currentSessionId ?: java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("'Session_'yyyyMMdd_HHmm")).also { currentSessionId = it }
        val outputDir = projectDir
            .resolve("data")
            .resolve("sessions")
            .resolve(sessionId)
        val source = sources.getOrNull(index - 1) ?: sources.lastOrNull()
        val currentEffect = _selectedEffect.value.id
        if (source != null) {
            return imageProcessor.saveBoothCapture(
                source = source,
                outputDir = outputDir,
                shotIndex = index,
                photoAspectRatio = layout.photoAspectRatio,
                effectId = currentEffect
            )
        }
        return cameraService.captureJpeg(
            outputDir = outputDir,
            shotIndex = index,
            photoAspectRatio = layout.photoAspectRatio,
            effectId = currentEffect
        )
    }

    fun deleteLayout(layoutId: String) {
        scope.launch {
            com.phuctran.photobooth.desktop.remote.FirebaseManager.deleteLayout(layoutId)
            val updatedLayouts = com.phuctran.photobooth.desktop.remote.FirebaseManager.fetchLayouts()
            if (updatedLayouts.isNotEmpty()) {
                _availableLayouts.value = updatedLayouts
            }
        }
    }
}
