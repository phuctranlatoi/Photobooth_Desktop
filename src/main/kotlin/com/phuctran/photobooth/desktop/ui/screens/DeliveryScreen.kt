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
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            // Header
            Text(
                "HOÀN TẤT!",
                style = MaterialTheme.typography.h3,
                fontWeight = FontWeight.Black,
                color = AccentNude
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Ảnh đang được in. Vui lòng nhận ảnh ở khe bên dưới nhé!\nĐừng quên tải file mềm về điện thoại của bạn.",
                style = MaterialTheme.typography.h6,
                color = NeutralText,
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(40.dp))
            
            // QR Card
            Box(
                modifier = Modifier
                    .shadow(16.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFF9FAFB))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (summary.qrUrl != null) {
                        RealQr(summary.qrUrl, Modifier.size(240.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Quét mã để tải ảnh gốc", fontWeight = FontWeight.Bold, color = NeutralText)
                        if (summary.outputPath != null) {
                            TextButton(onClick = onOpenOutput) {
                                Text("Mở thư mục lưu file in", color = AccentNude)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)).background(NeutralPanel),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Không có QR", color = NeutralMuted)
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(60.dp))
            
            BouncyButton(
                onClick = onFinish,
                colors = ButtonDefaults.buttonColors(backgroundColor = AccentNude, contentColor = Color.White),
                modifier = Modifier.width(300.dp).height(64.dp)
            ) {
                Text("VỀ TRANG CHỦ", fontWeight = FontWeight.Black, style = MaterialTheme.typography.h5)
            }
        }
    }
}
