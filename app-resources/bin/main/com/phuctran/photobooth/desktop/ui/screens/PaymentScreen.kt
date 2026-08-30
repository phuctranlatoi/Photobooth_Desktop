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
    Box(Modifier.fillMaxSize().background(NeutralBg)) {
        KioskBackButton(onBack, modifier = Modifier.align(Alignment.TopStart).padding(24.dp))

        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 92.dp, vertical = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PanelBox(Modifier.weight(0.9f).fillMaxHeight(0.78f)) {
                SectionHeader("Bước 4", "Thanh toán", "Quét QR bằng ứng dụng ngân hàng hoặc nhờ nhân viên xác nhận tiền mặt.")
                Spacer(Modifier.height(28.dp))
                Text(formatVnd(totalAmount), style = MaterialTheme.typography.h2, fontWeight = FontWeight.Black, color = NeutralText)
                Spacer(Modifier.height(20.dp))
                OutputRow("Bố cục", layout.title)
                Spacer(Modifier.height(10.dp))
                OutputRow("Màu ảnh", effect.title)
                Spacer(Modifier.height(10.dp))
                OutputRow("Bản in", "$printCopies bản")
                Spacer(Modifier.weight(1f))
                StatusChip(
                    label = "PayOS",
                    value = if (isPaymentConfigured) "Sẵn sàng" else "Chưa cấu hình",
                    color = if (isPaymentConfigured) AccentMint else AccentAmber
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(0.86f)
                    .shadow(18.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f))
                    .clip(RoundedCornerShape(24.dp))
                    .background(NeutralPanel)
                    .border(1.dp, NeutralBorder, RoundedCornerShape(24.dp))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isPaymentConfigured) {
                    if (paymentQrData == null) {
                        CircularProgressIndicator(color = AccentNude, strokeWidth = 5.dp, modifier = Modifier.size(72.dp))
                        Spacer(Modifier.height(20.dp))
                        Text("Đang tạo mã thanh toán...", color = NeutralSecondary, style = MaterialTheme.typography.subtitle1)
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
                        Text("Quét mã để thanh toán", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black, color = NeutralText)
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

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(NeutralPanel)
                .border(1.dp, NeutralBorder, RoundedCornerShape(999.dp))
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
