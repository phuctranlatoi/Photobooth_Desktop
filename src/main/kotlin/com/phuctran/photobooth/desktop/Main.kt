package com.phuctran.photobooth.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.phuctran.photobooth.desktop.config.DesktopAppPaths
import com.phuctran.photobooth.desktop.config.DesktopConfigLoader
import com.phuctran.photobooth.desktop.controller.DesktopBoothController
import com.phuctran.photobooth.desktop.domain.SessionState
import com.phuctran.photobooth.desktop.ui.components.AppShell
import com.phuctran.photobooth.desktop.ui.screens.*
import com.phuctran.photobooth.desktop.ui.theme.PhotoboothTheme

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Photobooth Kiosk"
    ) {
        val config = remember { DesktopConfigLoader.load(DesktopAppPaths.appDataDir()) }
        val controller = remember { DesktopBoothController(projectDir = DesktopAppPaths.appDataDir(), config = config) }

        val state by controller.sessionState.collectAsState()
        val layout by controller.selectedLayout.collectAsState()
        val effect by controller.selectedEffect.collectAsState()
        val frame by controller.selectedFrame.collectAsState()
        val availableFrames by controller.availableFrames.collectAsState()
        val capturedMoments by controller.capturedMoments.collectAsState()
        val selectedMoments by controller.selectedMoments.collectAsState()
        val printCopies by controller.printCopies.collectAsState()
        val countdown by controller.countdown.collectAsState()
        val captureSources by controller.captureSources.collectAsState()
        val cameraDevices by controller.cameraDevices.collectAsState()
        val exportSummary by controller.exportSummary.collectAsState()
        val totalPrice by controller.totalPrice.collectAsState()
        val statusMessage by controller.statusMessage.collectAsState()

        PhotoboothTheme {
            AppShell(state) {
                when (state) {
                    SessionState.IDLE -> StartScreen(
                        onStart = { controller.transitionTo(SessionState.SELECTING) },
                        onAdmin = { controller.transitionTo(SessionState.ADMIN) }
                    )
                    SessionState.SELECTING -> StudioModeScreen(
                        layouts = com.phuctran.photobooth.desktop.model.DefaultLayoutModes,
                        effects = com.phuctran.photobooth.desktop.model.DefaultEffectModes,
                        selectedLayout = layout,
                        selectedEffect = effect,
                        onLayoutSelected = { controller.chooseLayout(it) },
                        onEffectSelected = { controller.chooseEffect(it) },
                        onConfirm = { controller.confirmStudioSetup() }
                    )
                    SessionState.SELECTING_QUANTITY -> QuantityScreen(
                        layout = layout,
                        effect = effect,
                        onQuantitySelected = { controller.setQuantityAndStartPayment(it) },
                        onBack = { controller.goBack() }
                    )
                    SessionState.PAYMENT_PENDING -> {
                        val paymentQrData by controller.paymentQrData.collectAsState()
                        PaymentScreen(
                            totalAmount = totalPrice,
                            layout = layout,
                            effect = effect,
                            printCopies = printCopies,
                            paymentQrData = paymentQrData,
                            isPaymentConfigured = controller.isPaymentConfigured,
                            onPaid = { controller.completePayment() },
                            onBack = { controller.goBack() }
                        )
                    }
                    SessionState.PREPARING -> PrepareScreen(layout, effect)
                    SessionState.LIVE_VIEW, SessionState.COUNTDOWN, SessionState.CAPTURING -> {
                        val liveViewBitmap by controller.liveViewStream.collectAsState()
                        val isRecordingVideo by controller.isRecordingVideo.collectAsState()
                        CaptureScreen(
                            state = state,
                            layout = layout,
                            effect = effect,
                            countdown = countdown,
                            capturedMoments = capturedMoments,
                            captureSources = captureSources,
                            cameraDevices = cameraDevices,
                            liveViewBitmap = liveViewBitmap,
                            isRecordingVideo = isRecordingVideo,
                            onImportSources = {
                                val chooser = javax.swing.JFileChooser().apply { 
                                    fileSelectionMode = javax.swing.JFileChooser.FILES_AND_DIRECTORIES; isMultiSelectionEnabled = true 
                                }
                                if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                                    controller.addCaptureSources(chooser.selectedFiles.map { it.toPath() })
                                }
                            },
                            onClearSources = { controller.clearCaptureSources() },
                            onRefreshCameraDevices = { controller.refreshCameraDevices() },
                            onStartCapture = { controller.startCaptureFlow() }
                        )
                    }
                    SessionState.SELECTING_PHOTOS -> SelectPhotosScreen(
                        layout = layout,
                        capturedMoments = capturedMoments,
                        selectedMoments = selectedMoments,
                        onMomentSelect = { controller.toggleMoment(it) },
                        onConfirm = { controller.confirmSelection() }
                    )
                    SessionState.EDITING -> FrameScreen(
                        frames = availableFrames,
                        layout = layout,
                        selectedMoments = selectedMoments,
                        selectedFrame = frame,
                        onFrameSelected = { controller.chooseFrame(it) },
                        onAddFrame = {
                            val chooser = javax.swing.JFileChooser().apply { 
                                fileFilter = javax.swing.filechooser.FileNameExtensionFilter("PNG Frame", "png")
                            }
                            if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                                controller.addCustomFrame(chooser.selectedFile.toPath(), layout.id)
                            }
                        },
                        onConfirm = { controller.confirmFrameSelection() },
                        onBack = { controller.goBack() }
                    )
                    SessionState.PRINT_PENDING -> ConfirmScreen(
                        layout = layout,
                        effect = effect,
                        frame = frame,
                        selectedMoments = selectedMoments,
                        printCopies = printCopies,
                        exportSummary = exportSummary,
                        onPrint = { controller.startPrinting() },
                        onBack = { controller.goBack() }
                    )
                    SessionState.PRINTING -> PrintingScreen(layout, frame)
                    SessionState.DELIVERY -> DeliveryScreen(
                        summary = exportSummary,
                        totalCaptured = capturedMoments.size,
                        printCopies = printCopies,
                        onOpenOutput = { exportSummary.outputPath?.let { java.awt.Desktop.getDesktop().open(it.parent.toFile()) } },
                        onOpenAlbum = { exportSummary.qrUrl?.let { java.awt.Desktop.getDesktop().browse(java.net.URI(it)) } },
                        onFinish = { controller.finishSession() }
                    )
                    SessionState.ADMIN -> {
                        val currentConfig by controller.activeConfig.collectAsState()
                        AdminScreen(
                            frames = availableFrames,
                            config = currentConfig,
                            nativeCamera = controller.nativeCamera,
                            onAddFrame = { layoutId ->
                                val chooser = javax.swing.JFileChooser().apply { 
                                    fileFilter = javax.swing.filechooser.FileNameExtensionFilter("PNG Frame", "png")
                                }
                                if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                                    controller.addCustomFrame(chooser.selectedFile.toPath(), layoutId)
                                }
                            },
                            onSaveSettings = { enablePrint, useHotFolder, hotFolderPath ->
                                controller.saveDeviceSettings(enablePrint, useHotFolder, hotFolderPath)
                            },
                            onBack = { controller.resetSession() }
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}
