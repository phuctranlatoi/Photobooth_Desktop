package com.phuctran.photobooth.desktop

import com.phuctran.photobooth.desktop.config.BoothOperatingMode
import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import com.phuctran.photobooth.desktop.config.DesktopConfigLoader
import com.phuctran.photobooth.desktop.controller.DesktopBoothController
import com.phuctran.photobooth.desktop.data.SalesRepository
import com.phuctran.photobooth.desktop.domain.SessionState
import com.phuctran.photobooth.desktop.model.CapturedMoment
import com.phuctran.photobooth.desktop.model.DefaultLayoutModes
import com.phuctran.photobooth.desktop.model.FramePack
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StandaloneKioskModeTest {
    @TempDir(cleanup = CleanupMode.NEVER)
    lateinit var tempDir: Path
    private val scopes = mutableListOf<CoroutineScope>()
    private val controllers = mutableListOf<DesktopBoothController>()

    @AfterEach
    fun tearDown() {
        scopes.forEach { it.cancel() }
        controllers.forEach { it.close() }
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

    private class SpyTelegramService : TelegramNotificationService() {
        var paidCalls = 0
        var lastOrderCode: String? = null
        var lastLayoutTitle: String? = null
        var lastCopies: Int = 0
        var lastAmountVnd: Long = 0L

        override fun sendOrderPaid(
            config: DesktopBoothConfig,
            orderCode: String,
            layoutTitle: String,
            copies: Int,
            amountVnd: Long
        ) {
            paidCalls++
            lastOrderCode = orderCode
            lastLayoutTitle = layoutTitle
            lastCopies = copies
            lastAmountVnd = amountVnd
        }

        override fun sendTestMessage(token: String, chatId: String): CompletableFuture<Boolean> {
            return CompletableFuture.completedFuture(token.isNotBlank() && chatId.isNotBlank())
        }
    }

    private fun createController(
        config: DesktopBoothConfig,
        telegram: SpyTelegramService = SpyTelegramService()
    ): Pair<DesktopBoothController, SpyTelegramService> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scopes += scope
        val sales = SalesRepository(tempDir)
        val controller = DesktopBoothController(
            projectDir = tempDir,
            config = config,
            cameraService = StubCamera(),
            printerService = StubPrinter(),
            salesRepository = sales,
            telegramNotificationService = telegram,
            scope = scope
        )
        controllers += controller
        return Pair(controller, telegram)
    }

    private fun seedCapturedSession(controller: DesktopBoothController, sessionId: String = "standalone-session"): Path {
        val layout = DefaultLayoutModes.first().copy(selectCount = 1, shotCount = 1, basePrice = 70_000L)
        val selectedLayout = controller.javaClass.getDeclaredField("_selectedLayout").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (selectedLayout.get(controller) as MutableStateFlow<LayoutMode>).value = layout

        val photo = tempDir.resolve("data/sessions/$sessionId/originals/photo.jpg")
        Files.createDirectories(photo.parent)
        ImageIO.write(BufferedImage(16, 24, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().apply { color = Color.RED; fillRect(0, 0, 16, 24); dispose() }
        }, "jpg", photo.toFile())

        val field = controller.javaClass.getDeclaredField("currentSessionId").apply { isAccessible = true }
        field.set(controller, sessionId)
        val moments = controller.javaClass.getDeclaredField("_capturedMoments").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (moments.get(controller) as MutableStateFlow<List<CapturedMoment>>).value =
            listOf(CapturedMoment(1, "photo", "video", photo))
        controller.transitionTo(SessionState.SELECTING_PHOTOS)
        return photo
    }

    @Test
    fun testEnvironmentParsingAndClamping() {
        val loaded = DesktopBoothConfig.fromEnvironment(
            mapOf(
                "BOOTH_OPERATING_MODE" to "STANDALONE_KIOSK",
                "MAX_VISIBLE_FILTERS" to "25",
                "STANDALONE_INACTIVITY_TIMEOUT_SECONDS" to "10",
                "ENABLE_TELEGRAM_ALERTS" to "true",
                "TELEGRAM_BOT_TOKEN" to "123456:ABCDEF",
                "TELEGRAM_CHAT_ID" to "-100999"
            )
        )
        assertEquals(BoothOperatingMode.STANDALONE_KIOSK, loaded.operatingMode)
        assertTrue(loaded.isStandaloneKioskMode)
        assertFalse(loaded.isRentalMode)
        assertEquals(10, loaded.maxVisibleFilters, "MAX_VISIBLE_FILTERS must be clamped to 1..10")
        assertEquals(30, loaded.standaloneInactivityTimeoutSeconds, "STANDALONE_INACTIVITY_TIMEOUT_SECONDS must be clamped to 30..300")
        assertTrue(loaded.telegramAlertsConfigured)
    }

    @Test
    fun testSaveDeviceSettingsPersistsStandaloneAndFilterSettings() {
        val envFile = tempDir.resolve(".env")
        Files.writeString(envFile, "BOOTH_ID=kiosk_fixed\n")
        val initial = DesktopBoothConfig(
            enableSystemPrint = false,
            enableLocalServer = false,
            useNativeSdk = false,
            envSource = envFile
        )
        val (controller, _) = createController(initial)

        controller.saveDeviceSettings(
            enablePrint = true,
            useHotFolder = false,
            hotFolderPath = "C:\\hot",
            postCaptureTimeoutSeconds = 90,
            photoSelectionTimeoutSeconds = 90,
            frameSelectionTimeoutSeconds = 60,
            operatingMode = BoothOperatingMode.STANDALONE_KIOSK,
            maxVisibleFilters = 3,
            standaloneInactivityTimeoutSeconds = 150,
            enableTelegramAlerts = true,
            telegramBotToken = "bot_token_123",
            telegramChatId = "-100888777"
        )

        val updated = controller.activeConfig.value
        assertEquals(BoothOperatingMode.STANDALONE_KIOSK, updated.operatingMode)
        assertTrue(updated.isStandaloneKioskMode)
        assertFalse(updated.isRentalMode)
        assertEquals(3, updated.maxVisibleFilters)
        assertEquals(150, updated.standaloneInactivityTimeoutSeconds)
        assertTrue(updated.telegramAlertsConfigured)

        val reloaded = DesktopConfigLoader.load(tempDir)
        assertEquals(BoothOperatingMode.STANDALONE_KIOSK, reloaded.operatingMode)
        assertEquals(3, reloaded.maxVisibleFilters)
        assertEquals(150, reloaded.standaloneInactivityTimeoutSeconds)
        assertTrue(reloaded.enableTelegramAlerts)
        assertEquals("bot_token_123", reloaded.telegramBotToken)
        assertEquals("-100888777", reloaded.telegramChatId)
    }

    @Test
    fun testStandaloneModeSkipsPostCaptureFrameEditingAndGoesBackToSelectingPhotos() {
        val config = DesktopBoothConfig(
            isStandaloneKioskMode = true,
            enableSystemPrint = false,
            enableLocalServer = false,
            useNativeSdk = false
        )
        val (controller, _) = createController(config)

        seedCapturedSession(controller)
        controller.toggleMoment(controller.capturedMoments.value.single())

        // In STANDALONE_KIOSK mode, confirmSelection() must skip EDITING and go straight to PRINT_PENDING
        controller.confirmSelection()
        assertEquals(SessionState.PRINT_PENDING, controller.sessionState.value)

        // And goBack() from PRINT_PENDING must return to SELECTING_PHOTOS (not EDITING)
        controller.goBack()
        assertEquals(SessionState.SELECTING_PHOTOS, controller.sessionState.value)
    }

    @Test
    fun testTelegramNotificationSentOnlyInStandaloneModeWhenConfigured() {
        val standaloneConfig = DesktopBoothConfig(
            isStandaloneKioskMode = true,
            enableTelegramAlerts = true,
            telegramBotToken = "token_abc",
            telegramChatId = "chat_123",
            enableSystemPrint = false,
            enableLocalServer = false,
            useNativeSdk = false
        )
        val (standaloneController, standaloneTelegram) = createController(standaloneConfig)
        standaloneController.setQuantityAndStartPayment(2)
        standaloneController.completePayment("QR PayOS")
        assertEquals(1, standaloneTelegram.paidCalls, "Telegram alert must be sent in STANDALONE_KIOSK mode")
        assertEquals(2, standaloneTelegram.lastCopies)
        assertTrue(standaloneTelegram.lastAmountVnd > 0L)

        val commercialConfig = standaloneConfig.copy(isStandaloneKioskMode = false)
        val (commercialController, commercialTelegram) = createController(commercialConfig)
        commercialController.setQuantityAndStartPayment(2)
        commercialController.completePayment("QR PayOS")
        assertEquals(1, commercialTelegram.paidCalls, "Telegram alert must be sent in COMMERCIAL mode when configured")

        val rentalConfig = standaloneConfig.copy(isStandaloneKioskMode = false, isRentalMode = true)
        val (rentalController, rentalTelegram) = createController(rentalConfig)
        rentalController.setQuantityAndStartPayment(2)
        rentalController.completePayment("Miễn phí (Event)")
        assertEquals(0, rentalTelegram.paidCalls, "Telegram alert must NOT be sent in RENTAL mode")
    }

    @Test
    fun testStandaloneInactivityWatchdogResetsSessionAndCleansDirectory() = runBlocking {
        val config = DesktopBoothConfig(
            isStandaloneKioskMode = true,
            standaloneInactivityTimeoutSeconds = 120,
            enableSystemPrint = false,
            enableLocalServer = false,
            useNativeSdk = false
        )
        val (controller, _) = createController(config)
        val photo = seedCapturedSession(controller, sessionId = "expired-standalone-session")
        val sessionDir = photo.parent.parent
        assertTrue(Files.isDirectory(sessionDir))
        assertEquals(SessionState.SELECTING_PHOTOS, controller.sessionState.value)

        controller.handleStandaloneInactivityTimeout()
        assertEquals(SessionState.IDLE, controller.sessionState.value)

        withTimeout(5_000) {
            while (Files.exists(sessionDir)) {
                delay(30)
            }
        }
        assertFalse(Files.exists(sessionDir), "Temporary session directory must be cleaned up on standalone timeout")
    }

    @Test
    fun testStandaloneInactivityWatchdogExcludesFulfillmentAndActiveCaptureStates() {
        val config = DesktopBoothConfig(
            isStandaloneKioskMode = true,
            standaloneInactivityTimeoutSeconds = 120,
            enableSystemPrint = false,
            enableLocalServer = false,
            useNativeSdk = false
        )
        val (controller, _) = createController(config)
        val watchdogField = controller.javaClass.getDeclaredField("standaloneWatchdogJob").apply { isAccessible = true }
        val captureJobField = controller.javaClass.getDeclaredField("captureJob").apply { isAccessible = true }

        fun isWatchdogArmed(): Boolean {
            val job = watchdogField.get(controller) as? kotlinx.coroutines.Job
            return job?.isActive == true
        }

        val eligibleStates = listOf(
            SessionState.SELECTING,
            SessionState.SELECTING_QUANTITY,
            SessionState.PAYMENT_PENDING,
            SessionState.LIVE_VIEW,
            SessionState.SELECTING_PHOTOS,
            SessionState.EDITING
        )
        for (state in eligibleStates) {
            controller.transitionTo(state)
            assertTrue(isWatchdogArmed(), "Watchdog should be armed in $state")
        }

        // LIVE_VIEW while captureJob is active must NOT arm the watchdog
        val activeCaptureJob = kotlinx.coroutines.Job()
        captureJobField.set(controller, activeCaptureJob)
        controller.transitionTo(SessionState.LIVE_VIEW)
        assertFalse(isWatchdogArmed(), "Watchdog must NOT be armed in LIVE_VIEW while captureJob is active")
        activeCaptureJob.cancel()
        captureJobField.set(controller, null)

        val excludedStates = listOf(
            SessionState.PRINT_PENDING,
            SessionState.PRINTING,
            SessionState.DELIVERY,
            SessionState.PREPARING,
            SessionState.COUNTDOWN,
            SessionState.CAPTURING,
            SessionState.IDLE,
            SessionState.ADMIN
        )
        for (state in excludedStates) {
            controller.transitionTo(state)
            assertFalse(isWatchdogArmed(), "Watchdog must NOT be armed in $state")
        }

        // handleStandaloneInactivityTimeout must not abort or delete session in PRINT_PENDING, PRINTING, or DELIVERY
        val photo = seedCapturedSession(controller, sessionId = "fulfillment-standalone-session")
        val sessionDir = photo.parent.parent
        for (fulfillmentState in listOf(SessionState.PRINT_PENDING, SessionState.PRINTING, SessionState.DELIVERY)) {
            controller.transitionTo(fulfillmentState)
            controller.handleStandaloneInactivityTimeout()
            assertEquals(fulfillmentState, controller.sessionState.value, "Should not abort in $fulfillmentState")
            assertTrue(Files.isDirectory(sessionDir), "Session directory must remain intact in $fulfillmentState")
        }
    }
}
