package com.phuctran.photobooth.desktop

import com.phuctran.photobooth.desktop.model.PricingCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PricingCalculatorTest {
    @Test
    fun stripPrices() {
        mapOf(2 to 70_000L, 4 to 110_000L, 6 to 150_000L, 8 to 190_000L,
            10 to 230_000L, 20 to 430_000L).forEach { (quantity, expected) ->
            val price = PricingCalculator.calculatePrice("5x15", quantity)
            assertEquals("5x15", price.printSize)
            assertEquals(expected, price.totalAmount, "5x15 x $quantity")
            assertEquals(70_000L, price.basePrice)
            assertEquals(20_000L, price.extraUnitPrice)
        }
    }

    @Test
    fun postcardPrices() {
        mapOf(2 to 90_000L, 3 to 120_000L, 4 to 150_000L, 5 to 180_000L,
            6 to 210_000L, 10 to 330_000L).forEach { (quantity, expected) ->
            val price = PricingCalculator.calculatePrice("10x15", quantity)
            assertEquals("10x15", price.printSize)
            assertEquals(expected, price.totalAmount, "10x15 x $quantity")
            assertEquals(90_000L, price.basePrice)
            assertEquals(30_000L, price.extraUnitPrice)
        }
    }

    @Test
    fun landscapePostcardPricesAndQuantityRules() {
        listOf("15x10", "15 x 10 cm").forEach { size ->
            listOf(2, 3, 10).forEach { quantity ->
                val price = PricingCalculator.calculatePrice(size, quantity)
                assertEquals(PricingCalculator.calculatePrice("10x15", quantity), price)
                assertEquals(90_000L, price.basePrice)
                assertEquals(30_000L, price.extraUnitPrice)
                assertEquals(2, price.includedQuantity)
            }
            assertEquals((2..50).toList(), PricingCalculator.allowedOptions(size))
            for (quantity in -5..50) {
                assertEquals(maxOf(2, quantity), PricingCalculator.sanitizeQuantity(size, quantity))
            }
            assertFailsWith<IllegalArgumentException> { PricingCalculator.calculatePrice(size, 1) }
        }
        assertFailsWith<IllegalArgumentException> { PricingCalculator.calculatePrice("20x30", 2) }
        assertFailsWith<IllegalArgumentException> { PricingCalculator.allowedOptions("20x30") }
    }

    @Test
    fun quantityRulesAndExpandedOptions() {
        assertEquals((2..50 step 2).toList(), PricingCalculator.allowedOptions("5x15"))
        assertEquals((2..50).toList(), PricingCalculator.allowedOptions("10x15"))
        for (quantity in -5..50) {
            val strip = PricingCalculator.sanitizeQuantity("5x15", quantity)
            val postcard = PricingCalculator.sanitizeQuantity("10x15", quantity)
            assertTrue(strip >= 2 && strip % 2 == 0)
            assertEquals(maxOf(2, quantity), postcard)
            assertEquals(if (maxOf(2, quantity) % 2 == 0) maxOf(2, quantity) else maxOf(2, quantity) + 1, strip)
        }
        assertEquals(50, PricingCalculator.sanitizeQuantity("5x15", 49))
        assertEquals(50, PricingCalculator.sanitizeQuantity("10x15", 50))
        assertEquals(1_030_000L, PricingCalculator.calculatePrice("5x15", 50).totalAmount)
        assertEquals(1_530_000L, PricingCalculator.calculatePrice("10x15", 50).totalAmount)
        assertFailsWith<IllegalArgumentException> { PricingCalculator.calculatePrice("5x15", 3) }
        assertFailsWith<IllegalArgumentException> { PricingCalculator.calculatePrice("10x15", 1) }
    }

    @Test
    fun extrasExcludeTwoIncludedPrints() {
        listOf("5x15" to 20_000L, "10x15" to 30_000L).forEach { (size, unit) ->
            listOf(2, 4, 10).forEach { quantity ->
                val snapshot = PricingCalculator.calculatePrice(size, quantity)
                assertEquals(2, snapshot.includedQuantity)
                assertEquals(quantity - 2, snapshot.extraQuantity)
                assertEquals((quantity - 2) * unit, snapshot.extraAmount)
                assertEquals(snapshot.basePrice + snapshot.extraAmount, snapshot.totalAmount)
            }
        }
    }
}
