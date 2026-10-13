package com.phuctran.photobooth.desktop.model

data class PricingSnapshot(
    val printSize: String,
    val quantity: Int,
    val includedQuantity: Int,
    val extraQuantity: Int,
    val basePrice: Long,
    val extraUnitPrice: Long,
    val extraAmount: Long,
    val totalAmount: Long
)

object PricingCalculator {
    private fun isStrip(printSize: String): Boolean {
        val normalized = printSize.replace(" ", "").lowercase()
        require(normalized.contains("5x15") || normalized.contains("10x15")) { "Unsupported print size: $printSize" }
        return normalized.contains("5x15") && !normalized.contains("10x15")
    }

    fun allowedOptions(printSizeLabel: String): List<Int> =
        if (isStrip(printSizeLabel)) (2..50 step 2).toList() else (2..50).toList()

    fun sanitizeQuantity(printSizeLabel: String, quantity: Int): Int {
        val minimum = quantity.coerceAtLeast(2)
        return if (isStrip(printSizeLabel) && minimum % 2 != 0) minimum + 1 else minimum
    }

    fun calculatePrice(printSize: String, quantity: Int): PricingSnapshot {
        val strip = isStrip(printSize)
        require(quantity >= 2 && (!strip || quantity % 2 == 0)) {
            "Unsupported quantity $quantity for $printSize"
        }
        val extra = maxOf(0, quantity - 2)
        val base = if (strip) 70000L else 90000L
        val unit = if (strip) 20000L else 30000L
        return PricingSnapshot(if (strip) "5x15" else "10x15", quantity, 2, extra, base, unit, extra * unit, base + extra * unit)
    }
}
