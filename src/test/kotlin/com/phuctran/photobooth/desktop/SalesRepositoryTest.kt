package com.phuctran.photobooth.desktop

import com.phuctran.photobooth.desktop.data.DailyRevenuePoint
import com.phuctran.photobooth.desktop.data.SaleTransaction
import com.phuctran.photobooth.desktop.data.SalesRepository
import com.phuctran.photobooth.desktop.data.SalesSummary
import com.phuctran.photobooth.desktop.data.SizeBreakdown
import com.phuctran.photobooth.desktop.model.PricingCalculator
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class SalesRepositoryTest {
    @TempDir
    lateinit var folder: Path

    private val dayOne = LocalDate.of(2025, 5, 12)
    private val dayTwo = dayOne.plusDays(1)

    private fun timestamp(day: LocalDate, hour: Int): Long =
        day.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun sale(session: String, size: String, quantity: Int, createdAt: Long): SaleTransaction {
        val price = PricingCalculator.calculatePrice(size, quantity)
        return SaleTransaction(
            sessionId = session, boothId = "booth-1", layoutId = "layout-1",
            layoutTitle = "Classic", printSize = price.printSize, quantity = price.quantity,
            includedQuantity = price.includedQuantity, extraQuantity = price.extraQuantity,
            basePrice = price.basePrice, extraUnitPrice = price.extraUnitPrice,
            extraAmount = price.extraAmount, totalAmount = price.totalAmount,
            paymentMethod = "cash", createdAt = createdAt
        )
    }

    @Test
    fun insertingASalePersistsItsFields() {
        val repository = SalesRepository(folder)
        val original = sale("session-1", "5x15", 4, timestamp(dayOne, 10))
        repository.recordSale(original)

        assertTrue(Files.exists(folder.resolve("data/sales.db")))
        val stored = repository.getRecentTransactions()
        assertEquals(1, stored.size)
        assertTrue(stored.single().id > 0)
        assertEquals(original.copy(id = stored.single().id, originalQuantity = original.quantity, originalTotalAmount = original.totalAmount), stored.single())
    }

    @Test
    fun duplicateSessionIdIsIdempotentEvenWithDifferentPayload() {
        val repository = SalesRepository(folder)
        val original = sale("same-session", "5x15", 2, timestamp(dayOne, 10))
        repository.recordSale(original)
        repository.recordSale(sale("same-session", "10x15", 10, timestamp(dayTwo, 11)))

        assertEquals(1, repository.getRecentTransactions().size)
        assertEquals(original.totalAmount, repository.getRecentTransactions().single().totalAmount)
        assertEquals(SalesSummary(70_000, 1, 2, 0, 0),
            repository.getSummary(timestamp(dayOne, 0), timestamp(dayTwo.plusDays(1), 0)))
    }

    @Test
    fun transactionsSurviveRepositoryReinstantiation() {
        val first = SalesRepository(folder)
        first.recordSale(sale("one", "5x15", 4, timestamp(dayOne, 10)))
        first.recordSale(sale("two", "10x15", 3, timestamp(dayTwo, 11)))
        // Repository closes each connection after every operation; opening a new instance reads the same database.
        val reopened = SalesRepository(folder)
        assertEquals(listOf("two", "one"), reopened.getRecentTransactions().map { it.sessionId })
        assertEquals(230_000L, reopened.getSummary(timestamp(dayOne, 0), timestamp(dayTwo.plusDays(1), 0)).revenue)
        reopened.recordSale(sale("three", "5x15", 2, timestamp(dayTwo, 12)))
        assertEquals(3, first.getRecentTransactions().size)
    }

    @Test
    fun summariesBreakdownsDailyChartAndRecentOrdering() {
        val repository = SalesRepository(folder)
        val start = timestamp(dayOne, 0)
        val end = timestamp(dayTwo.plusDays(1), 0)
        repository.recordSale(sale("strip-base", "5x15", 2, timestamp(dayOne, 10)))
        repository.recordSale(sale("strip-extra", "5x15", 4, timestamp(dayOne, 12)))
        repository.recordSale(sale("postcard", "10x15", 3, timestamp(dayTwo, 11)))
        repository.recordSale(sale("outside", "10x15", 2, end))

        assertEquals(SalesSummary(300_000, 3, 9, 70_000, 3), repository.getSummary(start, end))
        assertEquals(listOf(
            SizeBreakdown("10x15", 1, 3, 120_000, 30_000),
            SizeBreakdown("5x15", 2, 6, 180_000, 40_000)
        ), repository.getSizeBreakdowns(start, end))
        assertEquals(listOf(
            DailyRevenuePoint(dayOne.toString(), 180_000, 2),
            DailyRevenuePoint(dayTwo.toString(), 120_000, 1)
        ), repository.getDailyPoints(start, end))
        assertEquals(listOf("postcard", "strip-extra", "strip-base"),
            repository.getRecentTransactions(start, end).map { it.sessionId })
        assertEquals(listOf("postcard", "strip-extra"),
            repository.getRecentTransactions(start, end, 2).map { it.sessionId })
        assertEquals(0, repository.getRecentTransactions(start, end, 0).size)
        assertEquals(SalesSummary(0, 0, 0, 0, 0), repository.getSummary(end + 1, end + 2))
    }
    @Test
    fun correctionsUseStoredPriceSnapshotAndRetainOriginalValues() {
        val repository = SalesRepository(folder)
        val start = timestamp(dayOne, 0)
        val end = timestamp(dayTwo, 0)
        repository.recordSale(sale("strip", "5x15", 4, timestamp(dayOne, 10)))
        assertTrue(repository.adjustTransaction("strip", 3, "Kh?ch gi?m s? l??ng"))
        assertEquals(SalesSummary(90_000, 1, 3, 20_000, 1), repository.getSummary(start, end))
        assertEquals(4, repository.getRecentTransactions().single().originalQuantity)
        assertEquals(110_000, repository.getRecentTransactions().single().originalTotalAmount)
        assertTrue(repository.adjustTransaction("strip", 5, "Kh?ch mua th?m"))
        assertEquals(SalesSummary(130_000, 1, 5, 60_000, 3), repository.getSummary(start, end))
        assertEquals(110_000, repository.getRecentTransactions().single().originalTotalAmount)
        // No pricing lookup: stored base and unit price alone determine future corrections.
        assertEquals(70_000, repository.getRecentTransactions().single().basePrice)
        assertEquals(20_000, repository.getRecentTransactions().single().extraUnitPrice)
    }

    @Test
    fun postcardCorrectionAndVoidExcludeAllAccountingButRetainHistory() {
        val repository = SalesRepository(folder)
        val start = timestamp(dayOne, 0)
        val end = timestamp(dayTwo, 0)
        repository.recordSale(sale("postcard", "10x15", 4, timestamp(dayOne, 10)))
        assertTrue(repository.adjustTransaction("postcard", 3, null))
        assertEquals(SalesSummary(120_000, 1, 3, 30_000, 1), repository.getSummary(start, end))
        assertEquals(150_000, repository.getRecentTransactions().single().originalTotalAmount)
        assertTrue(repository.voidTransaction("postcard", "Ho?n ti?n"))
        assertEquals(SalesSummary(0, 0, 0, 0, 0), repository.getSummary(start, end))
        assertTrue(repository.getDailyPoints(start, end).isEmpty())
        assertTrue(repository.getSizeBreakdowns(start, end).isEmpty())
        assertEquals("VOIDED", repository.getRecentTransactions(start, end).single().status)
        assertTrue(repository.getRecentTransactions().single().isVoided)
    }

}
