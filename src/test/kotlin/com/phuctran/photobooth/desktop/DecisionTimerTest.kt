package com.phuctran.photobooth.desktop

import com.phuctran.photobooth.desktop.config.BoothOperatingMode
import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import com.phuctran.photobooth.desktop.controller.DesktopBoothController
import com.phuctran.photobooth.desktop.data.SalesRepository
import com.phuctran.photobooth.desktop.domain.SessionState
import com.phuctran.photobooth.desktop.model.CapturedMoment
import com.phuctran.photobooth.desktop.model.DefaultLayoutModes
import com.phuctran.photobooth.desktop.model.LayoutMode
import com.phuctran.photobooth.desktop.services.PrinterService
import com.phuctran.photobooth.desktop.services.StillCaptureService
import com.phuctran.photobooth.desktop.services.TelegramNotificationService
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DecisionTimerTest {
    @TempDir
    lateinit var tempDir: Path

    private val testScopes = mutableListOf<TestScope>()
    private val controllers = mutableListOf<DesktopBoothController>()

    @AfterEach
    fun tearDown() {
        controllers.forEach { it.close() }
        testScopes.forEach { it.cancel() }
        controllers.clear()
        testScopes.clear()
    }

    private class StubCamera : StillCaptureService {
        override val liveViewStream = MutableStateFlow<androidx.compose.ui.graphics.ImageBitmap?>(null)
        override val lastProcessedFrame: BufferedImage? = null
        override fun cameraNames() = emptyList<String>()
        override fun startLiveView(effectId: String) = Unit
        override fun stopLiveView() = Unit
        override fun captureJpeg(outputDir: Path, shotIndex: Int, photoAspectRatio: Float, effectId: String): Path? = null
    }

    private class StubPrinter : PrinterService {
        var printCalls = 0
        override fun printImage(path: Path, copies: Int, printerName: String?): Result<String> {
            printCalls++
            return Result.success("ok")
        }
    }

    private class StubTelegramService : TelegramNotificationService() {
        override fun sendOrderPaid(
            config: DesktopBoothConfig,
            orderCode: String,
            layoutTitle: String,
            copies: Int,
            amountVnd: Long
        ): CompletableFuture<Boolean> = CompletableFuture.completedFuture(true)

        override fun sendOrderCompleted(
            config: DesktopBoothConfig,
            sessionId: String,
            orderCode: String,
            layoutTitle: String,
            copies: Int,
            amountVnd: Long,
            qrUrl: String?,
            paymentMethod: String,
            printStatus: String
        ): CompletableFuture<Boolean> = CompletableFuture.completedFuture(true)
    }

    private data class TestHarness(
        val controller: DesktopBoothController,
        val scope: TestScope,
        val printer: StubPrinter
    )

    private fun createController(
        operatingMode: BoothOperatingMode = BoothOperatingMode.STANDALONE_KIOSK,
        photoSelectionTimeoutSeconds: Int = 30,
        frameSelectionTimeoutSeconds: Int = 20,
        postCaptureTimeoutSeconds: Int = 15,
        standaloneInactivityTimeoutSeconds: Int = 120,
        enableSystemPrint: Boolean = false
    ): TestHarness {
        val scope = TestScope(StandardTestDispatcher())
        testScopes += scope
        val printer = StubPrinter()
        val config = DesktopBoothConfig(
            isRentalMode = operatingMode == BoothOperatingMode.RENTAL,
            isStandaloneKioskMode = operatingMode == BoothOperatingMode.STANDALONE_KIOSK,
            photoSelectionTimeoutSeconds = photoSelectionTimeoutSeconds,
            frameSelectionTimeoutSeconds = frameSelectionTimeoutSeconds,
            postCaptureTimeoutSeconds = postCaptureTimeoutSeconds,
            standaloneInactivityTimeoutSeconds = standaloneInactivityTimeoutSeconds,
            enableSystemPrint = enableSystemPrint,
            enableLocalServer = false,
            useNativeSdk = false
        )
        val controller = DesktopBoothController(
            projectDir = tempDir,
            config = config,
            cameraService = StubCamera(),
            printerService = printer,
            salesRepository = SalesRepository(tempDir),
            telegramNotificationService = StubTelegramService(),
            scope = scope
        )
        controllers += controller
        scope.runCurrent()
        assertEquals(operatingMode, controller.activeConfig.value.operatingMode)
        return TestHarness(controller, scope, printer)
    }

    private fun seedCapturedSession(
        controller: DesktopBoothController,
        sessionId: String = "decision-timer-session"
    ) {
        val layout = DefaultLayoutModes.first().copy(selectCount = 1, shotCount = 1, basePrice = 70_000L)
        val selectedLayout = controller.javaClass.getDeclaredField("_selectedLayout").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (selectedLayout.get(controller) as MutableStateFlow<LayoutMode>).value = layout

        val photo = tempDir.resolve("data/sessions/$sessionId/originals/photo.jpg")
        Files.createDirectories(photo.parent)
        ImageIO.write(
            BufferedImage(16, 24, BufferedImage.TYPE_INT_RGB).apply {
                createGraphics().apply {
                    color = Color.RED
                    fillRect(0, 0, 16, 24)
                    dispose()
                }
            },
            "jpg",
            photo.toFile()
        )

        val currentSessionIdField = controller.javaClass.getDeclaredField("currentSessionId").apply { isAccessible = true }
        currentSessionIdField.set(controller, sessionId)
        val momentsField = controller.javaClass.getDeclaredField("_capturedMoments").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (momentsField.get(controller) as MutableStateFlow<List<CapturedMoment>>).value =
            listOf(CapturedMoment(1, "photo", "video", photo))
        val selectedMomentsField = controller.javaClass.getDeclaredField("_selectedMoments").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (selectedMomentsField.get(controller) as MutableStateFlow<List<CapturedMoment>>).value = emptyList()

        controller.transitionTo(SessionState.SELECTING_PHOTOS)
    }

    private fun TestScope.advanceSeconds(seconds: Int) {
        repeat(seconds) {
            advanceTimeBy(1_000L)
            runCurrent()
        }
    }

    private fun getPrivateJob(controller: DesktopBoothController, fieldName: String): Job? {
        val field = controller.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }
        return field.get(controller) as? Job
    }

    @ParameterizedTest
    @EnumSource(BoothOperatingMode::class)
    fun `recordUserInteraction in SELECTING_PHOTOS and EDITING preserves decision countdown and manages watchdog by mode`(
        mode: BoothOperatingMode
    ) {
        val (controller, scope, _) = createController(
            operatingMode = mode,
            photoSelectionTimeoutSeconds = 30,
            frameSelectionTimeoutSeconds = 20,
            postCaptureTimeoutSeconds = 15,
            standaloneInactivityTimeoutSeconds = 120
        )

        seedCapturedSession(controller)
        scope.runCurrent()
        assertEquals(SessionState.SELECTING_PHOTOS, controller.sessionState.value)
        assertEquals(30, controller.inactivityTimeLeft.value)

        scope.advanceSeconds(1)
        assertEquals(29, controller.inactivityTimeLeft.value)

        val photoInactivityJobBefore = getPrivateJob(controller, "inactivityJob")
        val photoWatchdogBefore = getPrivateJob(controller, "standaloneWatchdogJob")
        assertNotNull(photoInactivityJobBefore)
        if (mode == BoothOperatingMode.STANDALONE_KIOSK) {
            assertNotNull(photoWatchdogBefore)
            assertTrue(photoWatchdogBefore.isActive)
        } else {
            assertNull(photoWatchdogBefore)
        }

        controller.recordUserInteraction()
        scope.runCurrent()

        assertEquals(29, controller.inactivityTimeLeft.value)
        assertSame(photoInactivityJobBefore, getPrivateJob(controller, "inactivityJob"))
        val photoWatchdogAfter = getPrivateJob(controller, "standaloneWatchdogJob")
        if (mode == BoothOperatingMode.STANDALONE_KIOSK) {
            assertNotNull(photoWatchdogAfter)
            assertNotSame(photoWatchdogBefore, photoWatchdogAfter)
            assertTrue(photoWatchdogBefore!!.isCancelled)
            assertTrue(photoWatchdogAfter.isActive)
        } else {
            assertNull(photoWatchdogAfter)
        }

        scope.advanceSeconds(1)
        assertEquals(28, controller.inactivityTimeLeft.value)

        // Move to EDITING and verify the same behavior
        controller.toggleMoment(controller.capturedMoments.value.single())
        controller.confirmSelection()
        scope.runCurrent()
        assertEquals(SessionState.EDITING, controller.sessionState.value)
        assertEquals(20, controller.inactivityTimeLeft.value)

        scope.advanceSeconds(1)
        assertEquals(19, controller.inactivityTimeLeft.value)

        val frameInactivityJobBefore = getPrivateJob(controller, "inactivityJob")
        val frameWatchdogBefore = getPrivateJob(controller, "standaloneWatchdogJob")
        assertNotNull(frameInactivityJobBefore)
        if (mode == BoothOperatingMode.STANDALONE_KIOSK) {
            assertNotNull(frameWatchdogBefore)
            assertTrue(frameWatchdogBefore.isActive)
        } else {
            assertNull(frameWatchdogBefore)
        }

        controller.recordUserInteraction()
        scope.runCurrent()

        assertEquals(19, controller.inactivityTimeLeft.value)
        assertSame(frameInactivityJobBefore, getPrivateJob(controller, "inactivityJob"))
        val frameWatchdogAfter = getPrivateJob(controller, "standaloneWatchdogJob")
        if (mode == BoothOperatingMode.STANDALONE_KIOSK) {
            assertNotNull(frameWatchdogAfter)
            assertNotSame(frameWatchdogBefore, frameWatchdogAfter)
            assertTrue(frameWatchdogBefore!!.isCancelled)
            assertTrue(frameWatchdogAfter.isActive)
        } else {
            assertNull(frameWatchdogAfter)
        }

        scope.advanceSeconds(1)
        assertEquals(18, controller.inactivityTimeLeft.value)
    }

    @ParameterizedTest
    @EnumSource(BoothOperatingMode::class)
    fun `navigating SELECTING_PHOTOS to EDITING and back preserves remaining photo time across all modes`(
        mode: BoothOperatingMode
    ) {
        val (controller, scope, _) = createController(
            operatingMode = mode,
            photoSelectionTimeoutSeconds = 30,
            frameSelectionTimeoutSeconds = 20,
            postCaptureTimeoutSeconds = 15
        )

        seedCapturedSession(controller)
        scope.runCurrent()
        assertEquals(30, controller.inactivityTimeLeft.value)

        scope.advanceSeconds(4)
        assertEquals(26, controller.inactivityTimeLeft.value)

        controller.recordUserInteraction()
        controller.toggleMoment(controller.capturedMoments.value.single())
        controller.confirmSelection()
        scope.runCurrent()
        assertEquals(SessionState.EDITING, controller.sessionState.value)
        assertEquals(20, controller.inactivityTimeLeft.value)

        scope.advanceSeconds(3)
        assertEquals(17, controller.inactivityTimeLeft.value)

        controller.recordUserInteraction()
        controller.goBack()
        scope.runCurrent()
        assertEquals(SessionState.SELECTING_PHOTOS, controller.sessionState.value)
        assertEquals(26, controller.inactivityTimeLeft.value)

        scope.advanceSeconds(2)
        assertEquals(24, controller.inactivityTimeLeft.value)
    }

    @ParameterizedTest
    @EnumSource(BoothOperatingMode::class)
    fun `navigating EDITING to SELECTING_PHOTOS and forward to EDITING preserves remaining frame time across all modes`(
        mode: BoothOperatingMode
    ) {
        val (controller, scope, _) = createController(
            operatingMode = mode,
            photoSelectionTimeoutSeconds = 30,
            frameSelectionTimeoutSeconds = 20,
            postCaptureTimeoutSeconds = 15
        )

        seedCapturedSession(controller)
        scope.runCurrent()
        controller.toggleMoment(controller.capturedMoments.value.single())
        controller.confirmSelection()
        scope.runCurrent()
        assertEquals(SessionState.EDITING, controller.sessionState.value)
        assertEquals(20, controller.inactivityTimeLeft.value)

        scope.advanceSeconds(5)
        assertEquals(15, controller.inactivityTimeLeft.value)

        controller.recordUserInteraction()
        controller.goBack()
        scope.runCurrent()
        assertEquals(SessionState.SELECTING_PHOTOS, controller.sessionState.value)
        assertEquals(30, controller.inactivityTimeLeft.value)

        scope.advanceSeconds(3)
        assertEquals(27, controller.inactivityTimeLeft.value)

        controller.recordUserInteraction()
        controller.confirmSelection()
        scope.runCurrent()
        assertEquals(SessionState.EDITING, controller.sessionState.value)
        assertEquals(15, controller.inactivityTimeLeft.value)

        scope.advanceSeconds(2)
        assertEquals(13, controller.inactivityTimeLeft.value)
    }

    @ParameterizedTest
    @EnumSource(BoothOperatingMode::class)
    fun `EDITING to PRINT_PENDING and back resets only PRINT_PENDING and preserves remaining frame time across all modes`(
        mode: BoothOperatingMode
    ) {
        val (controller, scope, _) = createController(
            operatingMode = mode,
            photoSelectionTimeoutSeconds = 30,
            frameSelectionTimeoutSeconds = 20,
            postCaptureTimeoutSeconds = 15
        )

        seedCapturedSession(controller)
        scope.runCurrent()
        controller.toggleMoment(controller.capturedMoments.value.single())
        controller.confirmSelection()
        scope.runCurrent()
        assertEquals(SessionState.EDITING, controller.sessionState.value)
        assertEquals(20, controller.inactivityTimeLeft.value)

        scope.advanceSeconds(3)
        assertEquals(17, controller.inactivityTimeLeft.value)

        controller.recordUserInteraction()
        controller.confirmFrameSelection()
        scope.runCurrent()
        assertEquals(SessionState.PRINT_PENDING, controller.sessionState.value)
        assertEquals(15, controller.inactivityTimeLeft.value)
        assertNull(getPrivateJob(controller, "standaloneWatchdogJob"))

        scope.advanceSeconds(4)
        assertEquals(11, controller.inactivityTimeLeft.value)

        controller.recordUserInteraction()
        scope.runCurrent()
        assertEquals(15, controller.inactivityTimeLeft.value)
        assertNull(getPrivateJob(controller, "standaloneWatchdogJob"))

        scope.advanceSeconds(2)
        assertEquals(13, controller.inactivityTimeLeft.value)

        controller.recordUserInteraction()
        controller.goBack()
        scope.runCurrent()
        assertEquals(SessionState.EDITING, controller.sessionState.value)
        assertEquals(17, controller.inactivityTimeLeft.value)

        scope.advanceSeconds(1)
        assertEquals(16, controller.inactivityTimeLeft.value)
    }

    @Test
    fun `resetSession clears remainingPhotoTime and remainingFrameTime so next session starts with full durations`() {
        val (controller, scope, _) = createController(
            operatingMode = BoothOperatingMode.STANDALONE_KIOSK,
            photoSelectionTimeoutSeconds = 30,
            frameSelectionTimeoutSeconds = 20,
            postCaptureTimeoutSeconds = 15
        )

        seedCapturedSession(controller, sessionId = "session-1")
        scope.runCurrent()
        assertEquals(30, controller.inactivityTimeLeft.value)
        scope.advanceSeconds(7)
        assertEquals(23, controller.inactivityTimeLeft.value)

        controller.toggleMoment(controller.capturedMoments.value.single())
        controller.confirmSelection()
        scope.runCurrent()
        assertEquals(20, controller.inactivityTimeLeft.value)
        scope.advanceSeconds(6)
        assertEquals(14, controller.inactivityTimeLeft.value)

        controller.resetSession()
        scope.runCurrent()
        assertEquals(SessionState.IDLE, controller.sessionState.value)
        assertNull(controller.inactivityTimeLeft.value)

        seedCapturedSession(controller, sessionId = "session-2")
        scope.runCurrent()
        assertEquals(SessionState.SELECTING_PHOTOS, controller.sessionState.value)
        assertEquals(30, controller.inactivityTimeLeft.value)

        controller.toggleMoment(controller.capturedMoments.value.single())
        controller.confirmSelection()
        scope.runCurrent()
        assertEquals(SessionState.EDITING, controller.sessionState.value)
        assertEquals(20, controller.inactivityTimeLeft.value)
    }

    @Test
    fun `transition occurring right as timer is about to expire does not fire handleInactivityTimeout for old state`() {
        val (controller, scope, printer) = createController(
            operatingMode = BoothOperatingMode.COMMERCIAL,
            photoSelectionTimeoutSeconds = 1,
            frameSelectionTimeoutSeconds = 10,
            postCaptureTimeoutSeconds = 1,
            enableSystemPrint = true
        )

        // Case 1: SELECTING_PHOTOS timer is at its expiry instant when user transitions to EDITING
        seedCapturedSession(controller, sessionId = "stale-tick-session-1")
        scope.runCurrent()
        assertEquals(SessionState.SELECTING_PHOTOS, controller.sessionState.value)
        assertEquals(1, controller.inactivityTimeLeft.value)

        // Advance virtual clock to the exact 1s expiry boundary without executing pending coroutines yet
        scope.advanceTimeBy(1_000L)
        controller.toggleMoment(controller.capturedMoments.value.single())
        controller.confirmSelection()

        // Flush scheduled tasks: the stale SELECTING_PHOTOS tick must exit cleanly without triggering auto-print
        scope.runCurrent()
        assertEquals(SessionState.EDITING, controller.sessionState.value)
        assertEquals(10, controller.inactivityTimeLeft.value)
        assertEquals(0, printer.printCalls)

        scope.advanceSeconds(1)
        assertEquals(SessionState.EDITING, controller.sessionState.value)
        assertEquals(9, controller.inactivityTimeLeft.value)
        assertEquals(0, printer.printCalls)

        // Case 2: PRINT_PENDING timer is at its expiry instant when user presses Back to EDITING
        controller.confirmFrameSelection()
        scope.runCurrent()
        assertEquals(SessionState.PRINT_PENDING, controller.sessionState.value)
        assertEquals(1, controller.inactivityTimeLeft.value)

        scope.advanceTimeBy(1_000L)
        controller.goBack()
        scope.runCurrent()

        assertEquals(SessionState.EDITING, controller.sessionState.value)
        assertEquals(9, controller.inactivityTimeLeft.value)
        assertEquals(0, printer.printCalls)

        scope.advanceSeconds(1)
        assertEquals(SessionState.EDITING, controller.sessionState.value)
        assertEquals(8, controller.inactivityTimeLeft.value)
        assertEquals(0, printer.printCalls)
    }
}
