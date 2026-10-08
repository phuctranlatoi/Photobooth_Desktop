package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.model.ExportSummary
import com.phuctran.photobooth.desktop.ui.components.*
import com.phuctran.photobooth.desktop.ui.theme.*

@Composable
fun DeliveryScreen(
    summary: ExportSummary,
    totalCaptured: Int,
    printCopies: Int,
    onOpenOutput: () -> Unit,
    onOpenAlbum: () -> Unit,
    onFinish: () -> Unit
) {
    val hasQr = !summary.qrUrl.isNullOrBlank()
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
        val compact = maxWidth < 850.dp
        val qrSize = if (compact) 224.dp else 304.dp
        if (compact) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DeliveryHeading(hasQr)
                DeliveryQr(hasQr, summary.qrUrl, qrSize, Modifier.weight(1f).fillMaxWidth())
                DeliveryDetails(totalCaptured, printCopies, summary, onFinish)
            }
        } else {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(0.9f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    DeliveryHeading(hasQr)
                    Spacer(Modifier.height(32.dp))
                    DeliveryDetails(totalCaptured, printCopies, summary, onFinish)
                }
                DeliveryQr(hasQr, summary.qrUrl, qrSize, Modifier.weight(1f).fillMaxHeight(0.9f))
            }
        }
    }
}

@Composable
private fun DeliveryHeading(hasQr: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(AccentMint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentMint, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(if (hasQr) "Khoảnh khắc của bạn đã sẵn sàng!" else "Buổi chụp đã hoàn tất!", style = MaterialTheme.typography.h4, color = NeutralText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasQr) "Quét mã để xem và lưu ảnh của bạn." else "Ảnh đã được xử lý. Vui lòng nhờ nhân viên hỗ trợ nhận file ảnh.",
            style = MaterialTheme.typography.body1,
            color = NeutralSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DeliveryQr(hasQr: Boolean, url: String?, qrSize: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    PanelBox(modifier) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (hasQr && url != null) {
                RealQr(url, Modifier.size(qrSize))
                Spacer(Modifier.height(18.dp))
                Text("Quét mã QR để nhận ảnh", style = MaterialTheme.typography.h6, color = NeutralText, textAlign = TextAlign.Center)
            } else {
                Text("Chưa có mã QR", style = MaterialTheme.typography.h6, color = NeutralText)
                Spacer(Modifier.height(8.dp))
                Text("Vui lòng liên hệ nhân viên để lấy file ảnh.", style = MaterialTheme.typography.body2, color = NeutralSecondary, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun DeliveryDetails(totalCaptured: Int, printCopies: Int, summary: ExportSummary, onFinish: () -> Unit) {
    val printMessage = when {
        summary.printStatus == null -> "Máy có thể vẫn đang in ảnh. Vui lòng kiểm tra khe nhận ảnh."
        summary.printStatus.startsWith("Không", ignoreCase = true) || summary.printStatus.startsWith("Chưa", ignoreCase = true) ->
            "Chưa thể in ảnh. Vui lòng nhờ nhân viên hỗ trợ."
        summary.printStatus.contains("local", ignoreCase = true) ->
            "Ảnh đã được lưu. Vui lòng nhờ nhân viên hỗ trợ nhận bản in."
        else -> "Ảnh đã được gửi đi in. Vui lòng kiểm tra khe nhận ảnh."
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutputRow("Ảnh đã chụp", "$totalCaptured ảnh")
        OutputRow("Bản in yêu cầu", "$printCopies bản")
        Text(printMessage, style = MaterialTheme.typography.body2, color = NeutralSecondary)
        Spacer(Modifier.height(10.dp))
        KioskPrimaryButton("Hoàn thành · Về trang đầu", onFinish, Modifier.fillMaxWidth())
    }
}
