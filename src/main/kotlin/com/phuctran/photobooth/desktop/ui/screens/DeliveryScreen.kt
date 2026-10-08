package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Box(Modifier.fillMaxSize().background(NeutralBg), contentAlignment = Alignment.Center) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(0.9f).fillMaxHeight(0.78f)) {
                SectionHeader("", "Ảnh của bạn đã sẵn sàng", "Nhận bản in tại khe máy và quét QR để tải album.")
                Spacer(Modifier.height(26.dp))
                OutputRow("Ảnh đã chụp", "$totalCaptured ảnh")
                Spacer(Modifier.height(10.dp))
                OutputRow("Bản in", "$printCopies bản")
                Spacer(Modifier.weight(1f))
                KioskPrimaryButton("Hoàn thành", onFinish, modifier = Modifier.fillMaxWidth().height(KioskControlTokens.buttonMinHeight))
            }

            PanelBox(Modifier.weight(1f).fillMaxHeight(0.86f)) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (summary.qrUrl != null) {
                        RealQr(summary.qrUrl, Modifier.size(280.dp))
                        Spacer(Modifier.height(22.dp))
                        Text("Quét để tải ảnh", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold, color = NeutralText)
                        Spacer(Modifier.height(8.dp))
                        Text("Album gồm ảnh gốc, ảnh in và video nếu có.", color = NeutralSecondary, textAlign = TextAlign.Center)
                    } else {
                        QrMock(Modifier.size(260.dp))
                        Spacer(Modifier.height(20.dp))
                        Text("Chưa tạo được QR", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold, color = NeutralText)
                        Text("Vui lòng gọi nhân viên để lấy file ảnh.", color = NeutralSecondary, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
