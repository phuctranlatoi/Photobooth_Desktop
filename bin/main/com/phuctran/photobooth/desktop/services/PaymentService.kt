package com.phuctran.photobooth.desktop.services

import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import vn.payos.PayOS
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest
import vn.payos.model.v2.paymentRequests.PaymentLinkItem
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus

class PaymentService(private val config: DesktopBoothConfig) {

    private val payOS: PayOS? = if (config.payosClientId.isNotBlank() && config.payosApiKey.isNotBlank() && config.payosChecksumKey.isNotBlank()) {
        PayOS(config.payosClientId, config.payosApiKey, config.payosChecksumKey)
    } else {
        null
    }

    val isConfigured: Boolean
        get() = payOS != null

    fun createPaymentLink(orderCode: Long, amount: Int, description: String): String? {
        if (payOS == null) return null
        return try {
            val item = PaymentLinkItem.builder().name("Photobooth").quantity(1).price(amount.toLong()).build()
            val paymentRequest = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(amount.toLong())
                .description(description)
                .items(listOf(item))
                .cancelUrl("https://localhost/cancel")
                .returnUrl("https://localhost/success")
                .build()
            
            val response = payOS.paymentRequests().create(paymentRequest)
            response.qrCode
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun checkPaymentStatus(orderCode: Long): Boolean {
        if (payOS == null) return false
        return try {
            val link = payOS.paymentRequests().get(orderCode)
            link.status == PaymentLinkStatus.PAID
        } catch (e: Exception) {
            false
        }
    }
}
