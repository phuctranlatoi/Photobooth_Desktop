package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phuctran.photobooth.desktop.model.EffectMode
import com.phuctran.photobooth.desktop.model.LayoutMode
import com.phuctran.photobooth.desktop.ui.components.CameraPoseGuide
import com.phuctran.photobooth.desktop.ui.components.InfoPill
import com.phuctran.photobooth.desktop.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrepareScreen(layout: LayoutMode, effect: EffectMode) {
    Row(
        modifier = Modifier.fillMaxSize().background(NeutralSurface).padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CameraPoseGuide(Modifier.weight(0.54f).fillMaxHeight(0.82f).shadow(14.dp, RoundedCornerShape(18.dp)))
        Box(Modifier.weight(0.46f)) {
                Column(verticalArrangement = Arrangement.Center) {
                    Text("Bước 5", modifier = Modifier.background(AccentNudeLight, RoundedCornerShape(50)).border(1.dp, NeutralBorder, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 7.dp), style = MaterialTheme.typography.caption, color = AccentNudeDark, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    Text("Chuẩn bị tạo dáng", style = MaterialTheme.typography.h5.copy(fontSize = 24.sp), color = NeutralText, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Đứng vào khung,\nnhìn camera\nvà giữ dáng khi số đếm hiện lên.",
                        style = MaterialTheme.typography.h6.copy(lineHeight = 28.sp),
                        color = NeutralText,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Khi nhấn chụp, mỗi ảnh sẽ có 3 giây đếm ngược.",
                        style = MaterialTheme.typography.body2,
                        color = NeutralSecondary
                    )
                    Spacer(Modifier.height(24.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().background(NeutralPanel.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                            .border(1.dp, NeutralBorder.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InfoPill("${layout.shotCount} lần chụp", bgColor = AccentNudeLight, textColor = AccentNudeDark)
                        InfoPill("Chọn ${layout.selectCount} ảnh", bgColor = NeutralPanelAlt, textColor = NeutralText)
                        InfoPill(effect.title, bgColor = NeutralPanelAlt, textColor = NeutralSecondary)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("${layout.title} · ${layout.printSizeLabel}", style = MaterialTheme.typography.caption, color = NeutralMuted, fontWeight = FontWeight.Normal)
                }
        }
    }
}
