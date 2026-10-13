package com.phuctran.photobooth.desktop.data

data class SaleTransaction(
    val id: Long = 0,
    val sessionId: String,
    val boothId: String,
    val layoutId: String,
    val layoutTitle: String,
    val printSize: String,
    val quantity: Int,
    val includedQuantity: Int,
    val extraQuantity: Int,
    val basePrice: Long,
    val extraUnitPrice: Long,
    val extraAmount: Long,
    val totalAmount: Long,
    val paymentMethod: String,
    val createdAt: Long,
    val status: String = "completed"
)

data class SalesSummary(val revenue: Long, val sessions: Long, val prints: Long, val extraRevenue: Long, val extraPrints: Long)
data class DailyRevenuePoint(val date: String, val revenue: Long, val sessions: Long)
data class SizeBreakdown(val printSize: String, val sessions: Long, val prints: Long, val revenue: Long, val extraRevenue: Long)
