package com.phuctran.photobooth.desktop.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import com.phuctran.photobooth.desktop.data.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Money is transferred as decimal strings; never parse it through Double. Null means cloud unavailable. */
class DesktopRevenueClient(private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
    private val neon = NeonDirectRevenueClient()
    private fun request(config: DesktopBoothConfig, path: String, body: String? = null): JsonObject? {
        if (config.boothApiKey.isBlank() || config.webAlbumBaseUrl.isBlank()) return null
        return runCatching {
            val builder = HttpRequest.newBuilder(URI.create("${config.webAlbumBaseUrl.trimEnd('/')}/api/v1/revenue/$path"))
                .timeout(Duration.ofSeconds(10)).header("Authorization", config.authHeader)
            if (body == null) builder.GET() else builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
            val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) null else JsonParser.parseString(response.body()).asJsonObject
        }.getOrNull()
    }

    fun recordTransaction(config: DesktopBoothConfig, transaction: SaleTransaction): Boolean {
        if (config.neonDatabaseUrl.isNotBlank()) return neon.recordTransaction(config, transaction)
        val body = SimpleJson.obj(
            "externalSessionId" to transaction.sessionId, "layoutId" to transaction.layoutId,
            "layoutTitle" to transaction.layoutTitle, "printSize" to transaction.printSize,
            "quantity" to transaction.quantity, "includedQuantity" to transaction.includedQuantity,
            "extraQuantity" to transaction.extraQuantity, "basePrice" to transaction.basePrice.toString(),
            "extraUnitPrice" to transaction.extraUnitPrice.toString(), "extraAmount" to transaction.extraAmount.toString(),
            "totalAmount" to transaction.totalAmount.toString(), "paymentMethod" to transaction.paymentMethod,
            "createdAt" to transaction.createdAt
        )
        return request(config, "transactions", body) != null
    }

    fun adjustTransaction(config: DesktopBoothConfig, sessionId: String, actualQuantity: Int, reason: String?): Boolean {
        require(actualQuantity >= 2)
        if (config.neonDatabaseUrl.isNotBlank()) return neon.adjustTransaction(config, sessionId, actualQuantity, reason)
        return request(config, "transactions/${java.net.URLEncoder.encode(sessionId, Charsets.UTF_8)}/adjust",
            SimpleJson.obj("actualQuantity" to actualQuantity, "reason" to reason)) != null
    }

    fun voidTransaction(config: DesktopBoothConfig, sessionId: String, reason: String?): Boolean {
        if (config.neonDatabaseUrl.isNotBlank()) return neon.voidTransaction(config, sessionId, reason)
        return request(config, "transactions/${java.net.URLEncoder.encode(sessionId, Charsets.UTF_8)}/void",
            SimpleJson.obj("reason" to reason)) != null
    }

    private fun range(startTime: Long, endTime: Long) = "startTime=$startTime&endTime=$endTime"
    private fun summaryJson(config: DesktopBoothConfig, startTime: Long, endTime: Long) =
        request(config, "summary?${range(startTime, endTime)}&timeZone=${java.net.URLEncoder.encode(java.time.ZoneId.systemDefault().id, Charsets.UTF_8)}")

    fun getSummary(config: DesktopBoothConfig, startTime: Long, endTime: Long): SalesSummary? =
        if (config.neonDatabaseUrl.isNotBlank()) neon.getDashboard(config, startTime, endTime)?.first else
        runCatching { summaryJson(config, startTime, endTime)?.getAsJsonObject("summary")?.let {
            SalesSummary(it.money("revenue"), it.money("sessions"), it.money("prints"),
                it.money("extraRevenue"), it.money("extraPrints"))
        } }.getOrNull()

    fun getDailyPoints(config: DesktopBoothConfig, startTime: Long, endTime: Long): List<DailyRevenuePoint>? =
        if (config.neonDatabaseUrl.isNotBlank()) neon.getDashboard(config, startTime, endTime)?.second else
        runCatching { summaryJson(config, startTime, endTime)?.getAsJsonArray("dailyPoints")?.map {
            it.asJsonObject.let { p -> DailyRevenuePoint(p.get("date").asString, p.money("revenue"), p.money("sessions")) }
        } }.getOrNull()

    fun getSizeBreakdowns(config: DesktopBoothConfig, startTime: Long, endTime: Long): List<SizeBreakdown>? =
        if (config.neonDatabaseUrl.isNotBlank()) neon.getDashboard(config, startTime, endTime)?.third else
        runCatching { summaryJson(config, startTime, endTime)?.getAsJsonArray("sizeBreakdowns")?.map {
            it.asJsonObject.let { p -> SizeBreakdown(p.get("printSize").asString, p.money("sessions"),
                p.money("prints"), p.money("revenue"), p.money("extraRevenue")) }
        } }.getOrNull()

    /** Fetch all dashboard aggregates in one round trip. */
    fun getDashboard(config: DesktopBoothConfig, startTime: Long, endTime: Long): Triple<SalesSummary, List<DailyRevenuePoint>, List<SizeBreakdown>>? =
        if (config.neonDatabaseUrl.isNotBlank()) neon.getDashboard(config, startTime, endTime) else
        runCatching { summaryJson(config, startTime, endTime)?.let { root ->
            val summary = root.getAsJsonObject("summary")
            Triple(SalesSummary(summary.money("revenue"), summary.money("sessions"), summary.money("prints"),
                summary.money("extraRevenue"), summary.money("extraPrints")),
                root.getAsJsonArray("dailyPoints").map { p -> p.asJsonObject.let { DailyRevenuePoint(it.get("date").asString, it.money("revenue"), it.money("sessions")) } },
                root.getAsJsonArray("sizeBreakdowns").map { p -> p.asJsonObject.let { SizeBreakdown(it.get("printSize").asString, it.money("sessions"), it.money("prints"), it.money("revenue"), it.money("extraRevenue")) } })
        } }.getOrNull()

    fun getRecentTransactions(config: DesktopBoothConfig, startTime: Long, endTime: Long, limit: Int = 20): List<SaleTransaction>? =
        if (config.neonDatabaseUrl.isNotBlank()) neon.getRecentTransactions(config, startTime, endTime, limit) else
        runCatching { request(config, "transactions?${range(startTime, endTime)}&limit=${limit.coerceIn(1, 100)}")
            ?.getAsJsonArray("transactions")?.map { item -> item.asJsonObject.let { s ->
                SaleTransaction(sessionId = s.get("sessionId").asString, boothId = s.get("boothId").asString,
                    layoutId = s.get("layoutId").asString, layoutTitle = s.get("layoutTitle").asString,
                    printSize = s.get("printSize").asString, quantity = s.get("quantity").asInt,
                    includedQuantity = s.get("includedQuantity").asInt, extraQuantity = s.get("extraQuantity").asInt,
                    basePrice = s.money("basePrice"), extraUnitPrice = s.money("extraUnitPrice"),
                    extraAmount = s.money("extraAmount"), totalAmount = s.money("totalAmount"),
                    paymentMethod = s.get("paymentMethod").asString, createdAt = s.money("createdAt"),
                    status = s.get("status").asString,
                    originalQuantity = s.get("originalQuantity")?.takeUnless { it.isJsonNull }?.asInt,
                    originalTotalAmount = s.get("originalTotalAmount")?.takeUnless { it.isJsonNull }?.asString?.toLong(),
                    adjustReason = s.get("adjustReason")?.takeUnless { it.isJsonNull }?.asString,
                    adjustedAt = s.get("adjustedAt")?.takeUnless { it.isJsonNull }?.asString?.let { value ->
                        value.toLongOrNull() ?: java.time.Instant.parse(value).toEpochMilli()
                    })
            } } }.getOrNull()

    private fun JsonObject.money(key: String): Long = get(key).asString.toLong()
}
