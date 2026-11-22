package com.phuctran.photobooth.desktop.services

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

open class TelegramNotificationService(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
    private val baseApiUrl: String = "https://api.telegram.org"
) {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy")
    private val paidAlerts = ConcurrentHashMap.newKeySet<String>()
    private val completedAlerts = ConcurrentHashMap.newKeySet<String>()

    open fun sendOrderPaid(
        config: DesktopBoothConfig,
        orderCode: String,
        layoutTitle: String,
        copies: Int,
        amountVnd: Long
    ): CompletableFuture<Boolean> {
        if (!config.telegramAlertsConfigured) {
            return CompletableFuture.completedFuture(false)
        }
        val token = config.telegramBotToken.trim()
        val chatId = config.telegramChatId.trim()
        if (token.isBlank() || chatId.isBlank()) {
            return CompletableFuture.completedFuture(false)
        }
        if (!paidAlerts.add(orderCode)) {
            return CompletableFuture.completedFuture(true)
        }

        println("INFO [TelegramNotificationService]: Đang gửi cảnh báo thanh toán đơn #$orderCode...")

        val formattedAmount = String.format("%,d VNĐ", amountVnd)
        val timestamp = LocalDateTime.now().format(timeFormatter)
        val message = buildString {
            appendLine("📸 [LE SOUVENIR] ĐƠN HÀNG MỚI ĐÃ THANH TOÁN")
            appendLine("• Kiosk: ${config.boothId}")
            appendLine("• Mã đơn: #$orderCode")
            appendLine("• Bố cục: $layoutTitle")
            appendLine("• Số lượng in: $copies bản")
            appendLine("• Số tiền: $formattedAmount")
            append("• Thời gian: $timestamp")
        }

        return sendMessageAsync(token = token, chatId = chatId, text = message)
            .whenComplete { success, error ->
                if (error != null || success != true) {
                    paidAlerts.remove(orderCode)
                    println("WARNING [TelegramNotificationService]: Không thể gửi thông báo đơn hàng #$orderCode (${error?.message ?: "HTTP failure"})")
                }
            }
    }

    open fun sendOrderCompleted(
        config: DesktopBoothConfig,
        sessionId: String,
        orderCode: String,
        layoutTitle: String,
        copies: Int,
        amountVnd: Long,
        qrUrl: String?,
        paymentMethod: String,
        printStatus: String
    ): CompletableFuture<Boolean> {
        if (!config.telegramAlertsConfigured) {
            return CompletableFuture.completedFuture(false)
        }
        val token = config.telegramBotToken.trim()
        val chatId = config.telegramChatId.trim()
        if (token.isBlank() || chatId.isBlank()) {
            return CompletableFuture.completedFuture(false)
        }
        if (!completedAlerts.add(sessionId)) {
            return CompletableFuture.completedFuture(true)
        }

        println("INFO [TelegramNotificationService]: Đang gửi thông báo hoàn tất lượt chụp & in đơn #$orderCode (session $sessionId)...")

        val formattedAmount = String.format("%,d VNĐ", amountVnd)
        val timestamp = LocalDateTime.now().format(timeFormatter)
        val message = buildString {
            appendLine("🎉 [LE SOUVENIR] HOÀN TẤT LƯỢT CHỤP & IN ẢNH")
            appendLine("• Kiosk: ${config.boothId}")
            appendLine("• Mã đơn: #$orderCode")
            appendLine("• Bố cục: $layoutTitle")
            appendLine("• Số lượng in: $copies bản")
            appendLine("• Doanh thu: $formattedAmount")
            appendLine("• Thanh toán: $paymentMethod")
            if (!qrUrl.isNullOrBlank()) {
                appendLine("• Link Album: $qrUrl")
            }
            appendLine("• Trạng thái in: $printStatus")
            append("• Thời gian: $timestamp")
        }

        return sendMessageAsync(token = token, chatId = chatId, text = message)
            .whenComplete { success, error ->
                if (error != null || success != true) {
                    completedAlerts.remove(sessionId)
                    println("WARNING [TelegramNotificationService]: Không thể gửi thông báo hoàn tất đơn #$orderCode (${error?.message ?: "HTTP failure"})")
                }
            }
    }

    open fun sendTestMessage(token: String, chatId: String): CompletableFuture<Boolean> {
        val cleanToken = token.trim()
        val cleanChatId = chatId.trim()
        if (cleanToken.isBlank() || cleanChatId.isBlank()) {
            return CompletableFuture.completedFuture(false)
        }
        val timestamp = LocalDateTime.now().format(timeFormatter)
        val message = buildString {
            appendLine("✅ [LE SOUVENIR] Kết nối cảnh báo Telegram thành công!")
            append("• Thời gian kiểm tra: $timestamp")
        }
        return sendMessageAsync(token = cleanToken, chatId = cleanChatId, text = message)
    }

    private fun sendMessageAsync(token: String, chatId: String, text: String): CompletableFuture<Boolean> {
        return try {
            val endpoint = "${baseApiUrl.trimEnd('/')}/bot$token/sendMessage"
            val payload = JsonObject().apply {
                addProperty("chat_id", chatId)
                addProperty("text", text)
            }.toString()

            val request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload, Charsets.UTF_8))
                .build()

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply { response ->
                    val statusOk = response.statusCode() in 200..299
                    val body = response.body().orEmpty()
                    val jsonOk = runCatching {
                        val json = JsonParser.parseString(body).asJsonObject
                        json.get("ok")?.takeUnless { it.isJsonNull }?.asBoolean == true
                    }.getOrDefault(false)
                    val ok = statusOk && jsonOk
                    if (ok) {
                        println("INFO [TelegramNotificationService]: Đã gửi thông báo Telegram thành công (HTTP ${response.statusCode()}).")
                    } else {
                        println("WARNING [TelegramNotificationService]: Telegram API trả về mã ${response.statusCode()}: $body")
                    }
                    ok
                }
                .exceptionally { e ->
                    println("WARNING [TelegramNotificationService]: Lỗi mạng khi gửi Telegram: ${e.message}")
                    false
                }
        } catch (e: Exception) {
            println("WARNING [TelegramNotificationService]: Không tạo được yêu cầu Telegram: ${e.message}")
            CompletableFuture.completedFuture(false)
        }
    }
}
