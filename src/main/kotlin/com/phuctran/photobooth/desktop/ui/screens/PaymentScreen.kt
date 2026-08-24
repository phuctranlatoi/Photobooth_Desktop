package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.model.*
import com.phuctran.photobooth.desktop.ui.components.*
import com.phuctran.photobooth.desktop.ui.theme.*

@Composable
fun PaymentScreen(
    totalAmount: Long,
    layout: LayoutMode,
    effect: EffectMode,
    printCopies: Int,
    paymentQrData: String?,
    isPaymentConfigured: Boolean,
    onPaid: () -> Unit,
    onBack: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color.White)) {
        // Top Left Back Button
        Row(Modifier.align(Alignment.TopStart).padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFF3F4F6))
            ) {
                Text("←", fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Bold)
            }
        }

        // Center Digital Ticket
        Column(
            Modifier
                .align(Alignment.Center)
                .width(420.dp)
                .shadow(24.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFF9FAFB)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Section: Info
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PHOTOBOOTH STUDIO", style = MaterialTheme.typography.overline, color = NeutralMuted, letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp))
                Spacer(Modifier.height(16.dp))
                Text(formatVnd(totalAmount), style = MaterialTheme.typography.h3, fontWeight = FontWeight.Black, color = NeutralText)
                Spacer(Modifier.height(8.dp))
                Text("${layout.title} • ${effect.title} • $printCopies bản in", style = MaterialTheme.typography.subtitle1, color = NeutralMuted)
            }
            
            // Dashed Divider (Simulated)
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White)) {
                // Actually, just drawing a simple solid line for now
                Divider(color = NeutralBorder, thickness = 2.dp)
            }
            
            // Bottom Section: QR Code
            Column(Modifier.fillMaxWidth().background(Color.White).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (isPaymentConfigured) {
                    if (paymentQrData == null) {
                        CircularProgressIndicator(color = AccentNude)
                        Spacer(Modifier.height(16.dp))
                        Text("Đang tạo mã thanh toán...", color = NeutralMuted)
                    } else {
                        QrCodeView(paymentQrData, Modifier.size(240.dp))
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Quét mã qua ứng dụng ngân hàng\nHệ thống sẽ tự động chuyển trang",
                            style = MaterialTheme.typography.body2,
                            color = NeutralMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text("Hệ thống thanh toán lỗi", color = AccentNude)
                }
            }
        }

        // Bottom Staff Override (Cash)
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .clip(RoundedCornerShape(50))
                .clickable { onPaid() }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(AccentNude))
            Text("Nhân viên: Thu tiền mặt", color = NeutralMuted, fontWeight = FontWeight.Bold)
        }
    }
}
