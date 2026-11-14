package com.phuctran.photobooth.desktop

import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import com.phuctran.photobooth.desktop.config.DesktopConfigLoader
import com.phuctran.photobooth.desktop.controller.DesktopBoothController
import com.phuctran.photobooth.desktop.data.SalesRepository
import com.phuctran.photobooth.desktop.domain.SessionState
import com.phuctran.photobooth.desktop.model.CapturedMoment
import com.phuctran.photobooth.desktop.model.DefaultLayoutModes
import com.phuctran.photobooth.desktop.model.LayoutMode
import com.phuctran.photobooth.desktop.model.PricingSnapshot
import com.phuctran.photobooth.desktop.services.PrinterService
import com.phuctran.photobooth.desktop.services.StillCaptureService
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RentalModeTest {
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

    private fun createController(config: DesktopBoothConfig, printer: StubPrinter = StubPrinter()): Pair<DesktopBoothController, SalesRepository> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scopes += scope
        val sales = SalesRepository(tempDir)
        val controller = DesktopBoothController(
            projectDir = tempDir,
            config = config,
            cameraService = StubCamera(),
            printerService = printer,
            salesRepository = sales,
            scope = scope
        )
        controllers += controller
        return Pair(controller, sales)
    }

    private fun getPrivatePaymentMethod(controller: DesktopBoothController): String {
        val field = controller.javaClass.getDeclaredField("paymentMethod").apply { isAccessible = true }
        return field.get(controller) as String
    }

    private fun getPrivatePricingSnapshot(controller: DesktopBoothController): PricingSnapshot? {
        val field = controller.javaClass.getDeclaredField("frozenPricingSnapshot").apply { isAccessible = true }
        return field.get(controller) as? PricingSnapshot
    }

    private fun seedSession(controller: DesktopBoothController): Path {
        val layout = DefaultLayoutModes.first().copy(selectCount = 1, shotCount = 1, basePrice = 90_000L)
        val selectedLayout = controller.javaClass.getDeclaredField("_selectedLayout").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (selectedLayout.get(controller) as MutableStateFlow<LayoutMode>).value = layout
        val photo = tempDir.resolve("data/sessions/rental-session/originals/photo.jpg")
        Files.createDirectories(photo.parent)
        ImageIO.write(BufferedImage(16, 24, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().apply { color = Color.BLUE; fillRect(0, 0, 16, 24); dispose() }
        }, "jpg", photo.toFile())
        val field = controller.javaClass.getDeclaredField("currentSessionId").apply { isAccessible = true }
        field.set(controller, "rental-session")
        val moments = controller.javaClass.getDeclaredField("_capturedMoments").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (moments.get(controller) as MutableStateFlow<List<CapturedMoment>>).value =
            listOf(CapturedMoment(1, "photo", "video", photo))
        controller.transitionTo(SessionState.SELECTING_PHOTOS)
        return photo
    }

    @Test
    fun testConfigLoaderDefaultRentalModeIsFalse() {
        val envFile = tempDir.resolve(".env")
        Files.writeString(envFile, "BOOTH_ID=test_booth\n")
        val loaded = DesktopConfigLoader.load(tempDir)
        assertFalse(loaded.isRentalMode, "Default rental mode must be false")
    }

    @Test
    fun testConfigLoaderParsesRentalModeFlags() {
        val envFile = tempDir.resolve(".env")

        for (truthy in listOf("true", "1", "yes", "on", "TRUE", "Yes")) {
            Files.writeString(envFile, "RENTAL_MODE=$truthy\n")
            val loaded = DesktopConfigLoader.load(tempDir)
            assertTrue(loaded.isRentalMode, "Flag '$truthy' should evaluate to true")
        }

        for (falsy in listOf("false", "0", "no", "off", "FALSE", "No")) {
            Files.writeString(envFile, "RENTAL_MODE=$falsy\n")
            val loaded = DesktopConfigLoader.load(tempDir)
            assertFalse(loaded.isRentalMode, "Flag '$falsy' should evaluate to false")
        }
    }

    @Test
    fun testCommercialModeRequiresPayment() {
        val config = DesktopBoothConfig(
            isRentalMode = false,
            enableSystemPrint = false,
            enableLocalServer = false,
            useNativeSdk = false
        )
        val (controller, _) = createController(config)

        controller.setQuantityAndStartPayment(2)

        assertEquals("Tiền mặt", getPrivatePaymentMethod(controller))
        assertEquals(SessionState.PAYMENT_PENDING, controller.sessionState.value)
        assertTrue(controller.totalPrice.value > 0L, "Commercial mode must calculate a positive total price")
        val snapshot = getPrivatePricingSnapshot(controller)
        assertNotNull(snapshot)
        assertTrue(snapshot.totalAmount > 0L)
    }

    @Test
    fun testRentalModeSkipsPaymentAndTransitionsToPreparing() = runBlocking {
        val config = DesktopBoothConfig(
            isRentalMode = true,
            enableSystemPrint = false,
            enableLocalServer = false,
            useNativeSdk = false
        )
        val (controller, _) = createController(config)

        controller.setQuantityAndStartPayment(2)

        assertEquals("Cho thuê", getPrivatePaymentMethod(controller))
        assertEquals(0L, controller.totalPrice.value, "Rental mode total price must be 0")
        val snapshot = getPrivatePricingSnapshot(controller)
        assertNotNull(snapshot)
        assertEquals(0L, snapshot.totalAmount, "Frozen pricing snapshot must be 0")
        assertEquals(0L, snapshot.basePrice)
        assertNull(controller.paymentQrData.value, "QR data must be null in rental mode")

        // In rental mode, state immediately goes to PREPARING then LIVE_VIEW
        assertTrue(
            controller.sessionState.value == SessionState.PREPARING || controller.sessionState.value == SessionState.LIVE_VIEW,
            "Rental mode must skip PAYMENT_PENDING directly to PREPARING or LIVE_VIEW, was: ${controller.sessionState.value}"
        )

        withTimeout(5_000) {
            while (controller.sessionState.value != SessionState.LIVE_VIEW) {
                delay(50)
            }
        }
        assertEquals(SessionState.LIVE_VIEW, controller.sessionState.value)
    }

    @Test
    fun testSaveDeviceSettingsPersistsRentalMode() {
        val envFile = tempDir.resolve(".env")
        Files.writeString(envFile, "RENTAL_MODE=false\n")
        val config = DesktopBoothConfig(
            isRentalMode = false,
            enableSystemPrint = false,
            enableLocalServer = false,
            useNativeSdk = false,
            envSource = envFile
        )
        val (controller, _) = createController(config)
        assertFalse(controller.activeConfig.value.isRentalMode)

        controller.saveDeviceSettings(
            enablePrint = true,
            useHotFolder = false,
            hotFolderPath = "C:\\test\\hotfolder",
            postCaptureTimeoutSeconds = 60,
            photoSelectionTimeoutSeconds = 60,
            frameSelectionTimeoutSeconds = 45,
            isRentalMode = true
        )

        assertTrue(controller.activeConfig.value.isRentalMode, "Active config must reflect updated rental mode")

        val loaded = DesktopConfigLoader.load(tempDir)
        assertTrue(loaded.isRentalMode, "Persisted .env must load with isRentalMode=true")
    }

    @Test
    fun testRentalModePrintRecordsSaleWithZeroAmountAndRentalMethod() = runBlocking {
        val printer = StubPrinter()
        val config = DesktopBoothConfig(
            isRentalMode = true,
            enableSystemPrint = true,
            enableLocalServer = false,
            useNativeSdk = false
        )
        val (controller, sales) = createController(config, printer)

        // Simulate choosing quantity 2 in rental mode
        controller.setQuantityAndStartPayment(2)

        // Seed photo selection
        seedSession(controller)
        controller.toggleMoment(controller.capturedMoments.value.single())
        controller.confirmSelection()
        assertEquals(SessionState.EDITING, controller.sessionState.value)

        controller.chooseFrame(controller.availableFrames.value.first().id)
        controller.confirmFrameSelection()
        assertEquals(SessionState.PRINT_PENDING, controller.sessionState.value)

        controller.startPrinting(userConfirmed = true)

        withTimeout(30_000) {
            while (controller.sessionState.value != SessionState.DELIVERY) delay(20)
        }

        assertEquals(1, printer.printCalls)
        val recordedSales = sales.getRecentTransactions()
        assertEquals(1, recordedSales.size)
        val sale = recordedSales.single()
        assertEquals(0L, sale.totalAmount, "Rental sale total amount must be 0")
        assertEquals(0L, sale.basePrice, "Rental sale base price must be 0")
        assertEquals("Cho thuê", sale.paymentMethod, "Rental sale payment method must be 'Cho thuê'")
    }
}