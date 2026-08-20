package com.phuctran.photobooth.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.Color
import java.awt.image.BufferedImage

@Composable
fun QrCodeView(data: String, modifier: Modifier = Modifier) {
    val bitmap = remember(data) { generateQrCode(data, 300) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "QR Code",
            modifier = modifier
        )
    }
}

private fun generateQrCode(data: String, size: Int): ImageBitmap? {
    try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size)
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until size) {
            for (y in 0 until size) {
                image.setRGB(x, y, if (bitMatrix.get(x, y)) Color.BLACK.rgb else Color.WHITE.rgb)
            }
        }
        return image.toComposeImageBitmap()
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
