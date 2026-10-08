package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var staffPanelVisible by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.TopStart).padding(24.dp)) {
            // Keep the staff-only long press on the title rather than exposing the override.
            CompactLocalHeader("Bước 3", "Thanh toán", onBack,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onLongPress = { staffPanelVisible = true })
                })
            Text("Quét QR bằng ứng dụng ngân hàng hoặc nhờ nhân viên xác nhận tiền mặt.", style = MaterialTheme.typography.body2, color = NeutralSecondary)
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(start = 68.dp, end = 68.dp, top = 116.dp, bottom = 76.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(0.9f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Text("Số tiền cần thanh toán", style = MaterialTheme.typography.h5, color = NeutralText)
                Spacer(Modifier.height(12.dp))
                Text(formatVnd(totalAmount), style = MaterialTheme.typography.h1.copy(fontSize = 44.sp), fontWeight = FontWeight.Black, color = AccentNudeDark)
                Spacer(Modifier.height(32.dp))
                OutputRow("Bố cục", layout.title)
                Spacer(Modifier.height(10.dp))
                OutputRow("Màu ảnh", effect.title)
                Spacer(Modifier.height(10.dp))
                OutputRow("Bản in", "$printCopies bản")
                Spacer(Modifier.height(24.dp))
                StatusChip(
                    label = "Thanh toán QR",
                    value = when {
                        !isPaymentConfigured -> "Chưa sẵn sàng"
                        paymentQrData == "ERROR" -> "Không thể tạo mã"
                        paymentQrData == null -> "Đang tạo mã"
                        else -> "Sẵn sàng"
                    },
                    color = if (isPaymentConfigured && paymentQrData != "ERROR") AccentMint else AccentAmber
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NeutralPanel.copy(alpha = 0.94f))
                    .border(1.dp, NeutralBorder, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isPaymentConfigured) {
                    if (paymentQrData == null) {
                        CircularProgressIndicator(color = AccentNude, strokeWidth = 5.dp, modifier = Modifier.size(72.dp))
                        Spacer(Modifier.height(20.dp))
                        Text("Đang tạo mã thanh toán...", color = NeutralSecondary, style = MaterialTheme.typography.subtitle1)
                    } else if (paymentQrData == "ERROR") {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = AccentAmber,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Không tạo được mã QR", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold, color = AccentAmber)
                        Spacer(Modifier.height(8.dp))
                        Text("Vui lòng kiểm tra mạng hoặc thanh toán bằng tiền mặt.", color = NeutralSecondary, textAlign = TextAlign.Center)
                    } else {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White)
                                .border(1.dp, NeutralBorder, RoundedCornerShape(20.dp))
                                .padding(18.dp)
                        ) {
                            QrCodeView(paymentQrData, Modifier.size(270.dp))
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("Quét mã bằng ứng dụng ngân hàng", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black, color = NeutralText)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Sau khi thanh toán thành công, hệ thống sẽ tự chuyển sang bước chụp.",
                            style = MaterialTheme.typography.body2,
                            color = NeutralSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text("Chưa bật thanh toán QR", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black, color = NeutralText)
                    Spacer(Modifier.height(8.dp))
                    Text("Nhân viên có thể xác nhận tiền mặt để tiếp tục phiên chụp.", color = NeutralSecondary, textAlign = TextAlign.Center)
                }
            }
        }

        if (staffPanelVisible || !isPaymentConfigured || paymentQrData == "ERROR") {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 26.dp)
                    .clip(RoundedCornerShape(KioskControlTokens.floatingCornerRadius))
                    .background(NeutralPanel)
                    .border(1.dp, NeutralBorder, RoundedCornerShape(KioskControlTokens.floatingCornerRadius))
                    .clickable { onPaid() }
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(AccentAmber))
                Text("Nhân viên xác nhận tiền mặt", color = NeutralSecondary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
