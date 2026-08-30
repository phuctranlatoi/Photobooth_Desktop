package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.shadow
import com.phuctran.photobooth.desktop.ui.components.BouncyButton
import com.phuctran.photobooth.desktop.ui.components.InfoPill
import com.phuctran.photobooth.desktop.ui.components.KioskPrimaryButton
import com.phuctran.photobooth.desktop.ui.theme.*

@Composable
fun StartScreen(
    liveViewBitmap: ImageBitmap?,
    onStart: () -> Unit, 
    onAdmin: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )
    val floatOffsetY by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )
    
    var tapCount by remember { mutableStateOf(0) }

    Row(
        Modifier
            .fillMaxSize()
            .background(NeutralBg)
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier.weight(0.9f).fillMaxHeight().padding(start = 28.dp, end = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Le Souvenir", color = AccentNudeDark, fontWeight = FontWeight.Black, style = MaterialTheme.typography.h4, modifier = Modifier.clickable(
                indication = null, 
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) {
                tapCount++
                if (tapCount >= 5) {
                    tapCount = 0
                    onAdmin()
                }
            })
            Spacer(Modifier.height(14.dp))
            Text("Chụp ảnh lấy ngay", style = MaterialTheme.typography.h2, fontWeight = FontWeight.Black, color = NeutralText)
            Spacer(Modifier.height(16.dp))
            Text(
                "Chọn layout, tạo dáng, in ảnh và quét QR để tải album về điện thoại.",
                color = NeutralSecondary,
                style = MaterialTheme.typography.h6
            )
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoPill("In lấy ngay", bgColor = NeutralPanel, textColor = NeutralText)
                InfoPill("Tải ảnh bằng QR", bgColor = AccentNudeLight, textColor = AccentNudeDark)
            }
            Spacer(Modifier.height(42.dp))
            
            KioskPrimaryButton(
                text = "Bắt đầu chụp",
                onClick = onStart,
                modifier = Modifier.width(300.dp).height(72.dp).graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
            )
            
            Spacer(Modifier.height(28.dp))
            Text("Sẵn sàng lưu lại khoảnh khắc đẹp của bạn.", color = NeutralMuted, style = MaterialTheme.typography.caption)
        }
        
        Box(
            Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(CameraBlack)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (liveViewBitmap != null) {
                Image(
                    bitmap = liveViewBitmap,
                    contentDescription = "Live camera preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = -1f)
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.08f), Color.Transparent, Color.Black.copy(alpha = 0.58f))
                            )
                        )
                )
                Column(
                    Modifier.align(Alignment.BottomStart).padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoPill("Live preview", bgColor = Color.White.copy(alpha = 0.18f), textColor = Color.White)
                    Text("Sẵn sàng cho phiên chụp mới", color = Color.White, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black)
                    Text("Đứng vào khung, bấm bắt đầu và làm phần còn lại thật vui.", color = Color.White.copy(alpha = 0.76f), style = MaterialTheme.typography.body2)
                }
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Color(0xFF18151B), Color(0xFF3C2D2A))))
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PhotostripGraphic(
                        modifier = Modifier
                            .offset(x = (-50).dp, y = floatOffsetY.dp)
                            .graphicsLayer(rotationZ = -10f)
                    )
                    PhotostripGraphic(
                        modifier = Modifier
                            .offset(x = 52.dp, y = (-floatOffsetY).dp)
                            .graphicsLayer(rotationZ = 7f)
                    )
                }
                Column(
                    Modifier.align(Alignment.BottomStart).padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Studio preview", color = Color.White, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black)
                    Text("Camera sẽ hiển thị tại đây khi sẵn sàng.", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.body2)
                }
            }
        }
    }
}

@Composable
fun PhotostripGraphic(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight(0.7f)
            .aspectRatio(0.4f)
            .shadow(24.dp, RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) { index ->
            val colors = when(index) {
                0 -> listOf(Color(0xFFFFA5A5), Color(0xFFFF7171)) // Reddish
                1 -> listOf(Color(0xFFFFD34D), Color(0xFFFFBF24)) // Yellowish
                else -> listOf(Color(0xFF6EE7B7), Color(0xFF34D399)) // Greenish
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.linearGradient(colors = colors)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Box(Modifier.weight(0.3f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "LE SOUVENIR",
                fontWeight = FontWeight.Black,
                color = Color(0xFF1A1A24),
                style = MaterialTheme.typography.overline,
                maxLines = 1
            )
        }
    }
}

@Composable
fun FeatureItem(icon: String, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(NeutralPanel),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp))
        }
        Column {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.subtitle1, color = NeutralText)
            Text(subtitle, color = NeutralMuted, style = MaterialTheme.typography.body2)
        }
    }
}
