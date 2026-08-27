package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.model.*
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
    Box(Modifier.fillMaxSize().background(NeutralBg), contentAlignment = Alignment.Center) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PanelBox(Modifier.weight(0.9f).fillMaxHeight(0.78f)) {
                SectionHeader("Hoàn tất", "Ảnh của bạn đã sẵn sàng", "Nhận bản in tại khe máy và quét QR để tải album.")
                Spacer(Modifier.height(26.dp))
                OutputRow("Ảnh đã chụp", "$totalCaptured ảnh")
                Spacer(Modifier.height(10.dp))
                OutputRow("Bản in", "$printCopies bản")
                Spacer(Modifier.height(10.dp))
                OutputRow("Album", summary.albumId ?: "Đang dùng phiên cục bộ")
                Spacer(Modifier.weight(1f))
                if (summary.outputPath != null) {
                    KioskSecondaryButton("Mở thư mục lưu file", onOpenOutput, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                }
                KioskPrimaryButton("Về trang chủ", onFinish, modifier = Modifier.fillMaxWidth().height(64.dp))
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(0.86f)
                    .shadow(18.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f))
                    .clip(RoundedCornerShape(24.dp))
                    .background(NeutralPanel)
                    .border(1.dp, NeutralBorder, RoundedCornerShape(24.dp))
                    .padding(34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (summary.qrUrl != null) {
                    RealQr(summary.qrUrl, Modifier.size(280.dp))
                    Spacer(Modifier.height(22.dp))
                    Text("Quét để tải ảnh", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black, color = NeutralText)
                    Spacer(Modifier.height(8.dp))
                    Text("Album gồm ảnh gốc, ảnh in và video nếu có.", color = NeutralSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = onOpenAlbum) {
                        Text("Mở album", color = AccentNude, fontWeight = FontWeight.Bold)
                    }
                } else {
                    QrMock(Modifier.size(260.dp))
                    Spacer(Modifier.height(20.dp))
                    Text("Chưa tạo được QR", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black, color = NeutralText)
                    Text("Vui lòng gọi nhân viên để lấy file ảnh.", color = NeutralSecondary, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
