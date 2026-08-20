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
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeutralText)
            }
            Spacer(Modifier.width(8.dp))
            SectionHeader("Bước 3", "Thanh toán", "${layout.title} • ${effect.title} • $printCopies bản in")
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // Cột Tiền mặt
            PanelBox(Modifier.weight(1f).fillMaxHeight()) {
                Text("Thanh toán tiền mặt", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold, color = NeutralText)
                Spacer(Modifier.height(20.dp))
                Text(formatVnd(totalAmount), style = MaterialTheme.typography.h3, fontWeight = FontWeight.Black, color = NeutralText)
                Spacer(Modifier.height(12.dp))
                InfoPill("Trực tiếp tại quầy")
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onPaid,
                    colors = ButtonDefaults.buttonColors(backgroundColor = NeutralPanel, contentColor = NeutralText),
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    border = BorderStroke(1.dp, NeutralBorder)
                ) { Text("Xác nhận đã thu tiền", style = MaterialTheme.typography.button) }
            }
            
            // Cột Chuyển khoản
            PanelBox(Modifier.weight(1f).fillMaxHeight()) {
                Text("Chuyển khoản QR", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold, color = NeutralText)
                Spacer(Modifier.height(20.dp))
                if (isPaymentConfigured) {
                    if (paymentQrData == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = NeutralText)
                                Spacer(Modifier.height(16.dp))
                                Text("Đang tạo mã thanh toán...", color = NeutralMuted)
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .padding(16.dp)
                            ) {
                                QrCodeView(paymentQrData, Modifier.size(300.dp))
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Hệ thống sẽ tự động chuyển màn hình khi nhận được thanh toán thành công qua PayOS.",
                            style = MaterialTheme.typography.body1,
                            color = NeutralMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Chưa cấu hình PayOS trong .env", color = NeutralMuted)
                    }
                }
            }
        }
    }
}
