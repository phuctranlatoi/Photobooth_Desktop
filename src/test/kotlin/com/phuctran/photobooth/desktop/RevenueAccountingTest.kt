package com.phuctran.photobooth.desktop

import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import com.phuctran.photobooth.desktop.controller.DesktopBoothController
import com.phuctran.photobooth.desktop.data.SalesRepository
import com.phuctran.photobooth.desktop.domain.SessionState
import com.phuctran.photobooth.desktop.model.CapturedMoment
import com.phuctran.photobooth.desktop.model.DefaultLayoutModes
import com.phuctran.photobooth.desktop.model.FramePack
import com.phuctran.photobooth.desktop.model.LayoutMode
import com.phuctran.photobooth.desktop.model.PricingCalculator
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
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RevenueAccountingTest {
    @TempDir lateinit var folder: Path
    private val scopes = mutableListOf<CoroutineScope>()

    @AfterEach fun tearDown() { scopes.forEach { it.cancel() } }

    private class FakeCamera : StillCaptureService {
        override val liveViewStream = MutableStateFlow<androidx.compose.ui.graphics.ImageBitmap?>(null)
        override val lastProcessedFrame: BufferedImage? = null
        override fun cameraNames() = emptyList<String>()
        override fun startLiveView(effectId: String) = Unit
        override fun stopLiveView() = Unit
        override fun captureJpeg(outputDir: Path, shotIndex: Int, photoAspectRatio: Float, effectId: String): Path? = null
    }

    private class FakePrinter(private val succeeds: Boolean = true) : PrinterService {
        var calls = 0
        override fun printImage(path: Path, copies: Int, printerName: String?): Result<String> {
            calls++
            return if (succeeds) Result.success("accepted") else Result.failure(IllegalStateException("offline"))
        }
    }

    private data class Fixture(val controller: DesktopBoothController, val sales: SalesRepository, val printer: FakePrinter)

    private fun fixture(printEnabled: Boolean = false, printerSuccess: Boolean = true): Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scopes += scope
        val sales = SalesRepository(folder)
        val printer = FakePrinter(printerSuccess)
        val controller = DesktopBoothController(
            projectDir = folder, config = DesktopBoothConfig(enableSystemPrint = printEnabled,
                enableLocalServer = false, useNativeSdk = false), cameraService = FakeCamera(),
            printerService = printer, salesRepository = sales, scope = scope
        )
        return Fixture(controller, sales, printer)
    }

    private fun seedSession(controller: DesktopBoothController, state: SessionState = SessionState.SELECTING_PHOTOS): Path {
        // Seed an already captured photo, avoiding real camera hardware and countdown delays.
        val layout = DefaultLayoutModes.first().copy(selectCount = 1, shotCount = 1, basePrice = 90_000L)
        val selectedLayout = controller.javaClass.getDeclaredField("_selectedLayout").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (selectedLayout.get(controller) as MutableStateFlow<LayoutMode>).value = layout
        val copies = controller.javaClass.getDeclaredField("_printCopies").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (copies.get(controller) as MutableStateFlow<Int>).value = 2
        val photo = folder.resolve("data/sessions/test-session/originals/photo.jpg")
        Files.createDirectories(photo.parent)
        ImageIO.write(BufferedImage(16, 24, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().apply { color = Color.RED; fillRect(0, 0, 16, 24); dispose() }
        }, "jpg", photo.toFile())
        val field = controller.javaClass.getDeclaredField("currentSessionId").apply { isAccessible = true }
        field.set(controller, "test-session")
        val moments = controller.javaClass.getDeclaredField("_capturedMoments").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (moments.get(controller) as MutableStateFlow<List<CapturedMoment>>).value =
            listOf(CapturedMoment(1, "photo", "video", photo))
        controller.transitionTo(state)
        return photo
    }

    private fun selectAndConfirm(controller: DesktopBoothController) {
        controller.toggleMoment(controller.capturedMoments.value.single())
        controller.confirmSelection()
        assertEquals(SessionState.EDITING, controller.sessionState.value)
        controller.chooseFrame(controller.availableFrames.value.first().id)
        controller.confirmFrameSelection()
        assertEquals(SessionState.PRINT_PENDING, controller.sessionState.value)
    }

    private suspend fun awaitDelivery(controller: DesktopBoothController) = withTimeout(30_000) {
        while (controller.sessionState.value != SessionState.DELIVERY) delay(20)
    }

    @Test fun selectionAndFrameConfirmationAreNotSales() {
        val (controller, sales) = fixture()
        seedSession(controller)
        selectAndConfirm(controller)
        assertEquals(0L, sales.getSummary(0, Long.MAX_VALUE).revenue)
        assertTrue(sales.getRecentTransactions().isEmpty())
    }

    @Test fun cancellationOnAllowedScreensResetsAndCleansFilesWithoutSale() = runBlocking {
        for (state in listOf(SessionState.SELECTING_PHOTOS, SessionState.EDITING)) {
            val (controller, sales) = fixture()
            val photo = seedSession(controller, state)
            assertTrue(controller.cancelSession())
            assertEquals(SessionState.IDLE, controller.sessionState.value)
            assertTrue(controller.capturedMoments.value.isEmpty())
            withTimeout(5_000) { while (Files.exists(photo)) delay(20) }
            assertEquals(0L, sales.getSummary(0, Long.MAX_VALUE).revenue)
        }
    }

    @Test fun cancellationIsForbiddenOutsidePhotoAndFrameSelection() {
        val (controller, sales) = fixture()
        for (state in SessionState.entries - setOf(SessionState.SELECTING_PHOTOS, SessionState.EDITING)) {
            controller.transitionTo(state)
            assertFalse(controller.cancelSession(), "Cancellation should be rejected in $state")
            assertEquals(state, controller.sessionState.value)
        }
        assertEquals(0L, sales.getSummary(0, Long.MAX_VALUE).revenue)
    }

    @Test fun renderedFileWithoutSystemPrintingIsNotRevenue() = runBlocking {
        val (controller, sales, printer) = fixture()
        seedSession(controller)
        selectAndConfirm(controller)
        controller.startPrinting(userConfirmed = true)
        awaitDelivery(controller)
        assertTrue(Files.isRegularFile(controller.exportSummary.value.outputPath))
        assertEquals("Đã render file in local (chế độ không in). Bật ENABLE_SYSTEM_PRINT=true để in.", controller.exportSummary.value.printStatus)
        assertEquals(0, printer.calls)
        assertEquals(0L, sales.getSummary(0, Long.MAX_VALUE).revenue)
    }

    @Test fun autoPrintWithoutUserConfirmationIsNotRevenue() = runBlocking {
        val (controller, sales, printer) = fixture(printEnabled = true)
        seedSession(controller)
        selectAndConfirm(controller)
        // Invoke the timer's timeout callback rather than bypassing its auto-print path.
        controller.javaClass.getDeclaredMethod("handleInactivityTimeout").apply { isAccessible = true }.invoke(controller)
        awaitDelivery(controller)
        assertEquals(1, printer.calls)
        assertTrue(sales.getRecentTransactions().isEmpty())
    }

    @Test fun confirmedSuccessfulPrintRecordsExactlyOneSale() = runBlocking {
        val (controller, sales, printer) = fixture(printEnabled = true)
        seedSession(controller)
        selectAndConfirm(controller)
        controller.startPrinting(userConfirmed = true)
        awaitDelivery(controller)
        assertEquals(1, printer.calls)
        assertEquals(1, sales.getRecentTransactions().size)
        assertEquals(90_000L, sales.getRecentTransactions().single().totalAmount)
    }

    @Test fun failedPrinterDoesNotRecordSale() = runBlocking {
        val (controller, sales, printer) = fixture(printEnabled = true, printerSuccess = false)
        seedSession(controller)
        selectAndConfirm(controller)
        controller.startPrinting(userConfirmed = true)
        awaitDelivery(controller)
        assertEquals(1, printer.calls)
        assertTrue(sales.getRecentTransactions().isEmpty())
    }

    @Test fun repeatedPrintOfSameSessionHasAtMostOneSale() = runBlocking {
        val (controller, sales) = fixture(printEnabled = true)
        seedSession(controller)
        selectAndConfirm(controller)
        controller.startPrinting(userConfirmed = true)
        awaitDelivery(controller)
        controller.startPrinting(userConfirmed = true)
        withTimeout(30_000) {
            while (controller.sessionState.value != SessionState.PRINTING) delay(20)
            while (controller.sessionState.value != SessionState.DELIVERY) delay(20)
        }
        assertEquals(1, sales.getRecentTransactions().size)
    }

    @Test fun priceSnapshotDoesNotChangeWhenLayoutPricingChanges() = runBlocking {
        val (controller, sales) = fixture(printEnabled = true)
        seedSession(controller)
        selectAndConfirm(controller)
        controller.startPrinting(userConfirmed = true)
        awaitDelivery(controller)
        val original = sales.getRecentTransactions().single()
        // Pricing/display config can change later; historical snapshots remain in SQLite.
        val selectedLayout = controller.javaClass.getDeclaredField("_selectedLayout").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (selectedLayout.get(controller) as MutableStateFlow<LayoutMode>).value = controller.selectedLayout.value.copy(basePrice = 999_999L)
        assertEquals(original, SalesRepository(folder).getRecentTransactions().single())
        assertEquals(90_000L, original.basePrice)
    }

    @Test fun saleUsesPriceFrozenAtQuantitySelectionEvenIfLayoutChangesBeforePrinting() = runBlocking {
        val (controller, sales) = fixture(printEnabled = true)
        seedSession(controller)
        controller.setQuantityAndStartPayment(3)
        val frozen = PricingCalculator.calculatePrice("10x15", 3)
        assertEquals(frozen.totalAmount, controller.totalPrice.value)

        val selectedLayout = controller.javaClass.getDeclaredField("_selectedLayout").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (selectedLayout.get(controller) as MutableStateFlow<LayoutMode>).value =
            controller.selectedLayout.value.copy(printSizeLabel = "5 x 15 cm", basePrice = 999_999L)
        selectAndConfirm(controller)
        controller.startPrinting(userConfirmed = true)
        awaitDelivery(controller)

        val sale = sales.getRecentTransactions().single()
        assertEquals(frozen.printSize, sale.printSize)
        assertEquals(frozen.quantity, sale.quantity)
        assertEquals(frozen.includedQuantity, sale.includedQuantity)
        assertEquals(frozen.extraQuantity, sale.extraQuantity)
        assertEquals(frozen.basePrice, sale.basePrice)
        assertEquals(frozen.extraUnitPrice, sale.extraUnitPrice)
        assertEquals(frozen.extraAmount, sale.extraAmount)
        assertEquals(frozen.totalAmount, sale.totalAmount)
    }

    @Test fun vndValuesAreStoredAsIntegerLongWithoutFloatingPointLoss() {
        val sales = SalesRepository(folder)
        val pricing = PricingCalculator.calculatePrice("10x15", 3)
        val largeVnd = 9_007_199_254_740_993L // Above the exact Double integer range.
        sales.recordSale(com.phuctran.photobooth.desktop.data.SaleTransaction(
            sessionId = "large-vnd", boothId = "booth_1", layoutId = "layout", layoutTitle = "Layout",
            printSize = pricing.printSize, quantity = pricing.quantity, includedQuantity = pricing.includedQuantity,
            extraQuantity = pricing.extraQuantity, basePrice = largeVnd, extraUnitPrice = pricing.extraUnitPrice,
            extraAmount = pricing.extraAmount, totalAmount = largeVnd, paymentMethod = "cash", createdAt = 1L
        ))
        assertEquals(largeVnd, SalesRepository(folder).getRecentTransactions().single().totalAmount)
        assertEquals(largeVnd, sales.getSummary(0, Long.MAX_VALUE).revenue)
    }
}
