package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
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
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PanelBox(Modifier.weight(0.3f).fillMaxHeight()) {
            SectionHeader("Hoàn tất", "Lấy ảnh của bạn", "Cảm ơn bạn đã sử dụng dịch vụ!")
            Spacer(Modifier.height(24.dp))
            
            Text("Chi tiết", fontWeight = FontWeight.Bold, color = NeutralText)
            Spacer(Modifier.height(8.dp))
            Text("Đã chụp: $totalCaptured ảnh", color = NeutralMuted)
            Text("In: $printCopies bản", color = NeutralMuted)
            
            Spacer(Modifier.weight(1f))
            BouncyButton(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = AccentNude, contentColor = Color.White)
            ) {
                Text("VỀ TRANG CHỦ", fontWeight = FontWeight.Black, style = MaterialTheme.typography.h6)
            }
        }
        
        PanelBox(Modifier.weight(0.7f).fillMaxHeight()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (summary.qrUrl != null) {
                        RealQr(summary.qrUrl, Modifier.size(270.dp))
                        Text(text = summary.qrUrl, color = NeutralMuted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        OutlinedButton(onClick = onOpenAlbum) { Text("Mở album trực tuyến") }
                    } else {
                        Box(
                            modifier = Modifier.size(270.dp).clip(RoundedCornerShape(8.dp)).background(NeutralPanel).border(1.dp, NeutralBorder, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Không có QR", color = NeutralMuted)
                        }
                    }
                    if (summary.outputPath != null) {
                        TextButton(onClick = onOpenOutput) {
                            Text("Mở thư mục lưu file in", color = AccentNude)
                        }
                    }
                }
            }
        }
    }
}
