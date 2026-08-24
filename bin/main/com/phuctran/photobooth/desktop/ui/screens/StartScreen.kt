package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import com.phuctran.photobooth.desktop.ui.theme.AccentNude
import com.phuctran.photobooth.desktop.ui.theme.NeutralBg
import com.phuctran.photobooth.desktop.ui.theme.NeutralMuted
import com.phuctran.photobooth.desktop.ui.theme.NeutralPanel
import com.phuctran.photobooth.desktop.ui.theme.NeutralText

@Composable
fun StartScreen(
    liveViewBitmap: ImageBitmap?,
    onStart: () -> Unit, 
    onAdmin: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )
    
    var tapCount by remember { mutableStateOf(0) }

    Row(Modifier.fillMaxSize().background(NeutralBg), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(48.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("PHOTOBOOTH", color = AccentNude, fontWeight = FontWeight.Black, style = MaterialTheme.typography.h3, modifier = Modifier.clickable(
                indication = null, 
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) {
                tapCount++
                if (tapCount >= 5) {
                    tapCount = 0
                    onAdmin()
                }
            })
            Text("STUDIO EDITION", style = MaterialTheme.typography.h2, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            Text(
                "Trải nghiệm chụp ảnh chuyên nghiệp ngay tại đây. Chạm để bắt đầu phiên chụp của bạn.",
                color = NeutralMuted,
                style = MaterialTheme.typography.h6
            )
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoPill("Sony a6400 / Pro Webcam")
                InfoPill("In ảnh lấy ngay")
            }
            Spacer(Modifier.height(48.dp))
            
            BouncyButton(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(backgroundColor = AccentNude, contentColor = Color.White),
                modifier = Modifier.width(320.dp).height(80.dp).graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
            ) {
                Text("CHẠM ĐỂ BẮT ĐẦU", fontWeight = FontWeight.Black, style = MaterialTheme.typography.h5)
            }
            
            Spacer(Modifier.height(48.dp))
        }
        
        Box(Modifier.weight(1.2f).fillMaxHeight().padding(24.dp).clip(RoundedCornerShape(24.dp)).background(NeutralPanel), contentAlignment = Alignment.Center) {
            if (liveViewBitmap != null) {
                Image(
                    bitmap = liveViewBitmap,
                    contentDescription = "Live View Preview",
                    modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = -1f),
                    contentScale = ContentScale.Crop
                )
            } else {
                val color1 by infiniteTransition.animateColor(
                    initialValue = Color(0xFFF7F8FA),
                    targetValue = Color(0xFFE2E8F0),
                    animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse)
                )
                val color2 by infiniteTransition.animateColor(
                    initialValue = Color(0xFFE2E8F0),
                    targetValue = Color(0xFFF7F8FA),
                    animationSpec = infiniteRepeatable(tween(5000), RepeatMode.Reverse)
                )

                val floatOffsetY by infiniteTransition.animateFloat(
                    initialValue = -15f,
                    targetValue = 15f,
                    animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse)
                )
                
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors = listOf(color1, color2)))) {
                    
                    // Floating Orb 1 (Pink)
                    Box(Modifier
                        .offset(x = 80.dp, y = (40 + floatOffsetY * 2).dp)
                        .size(250.dp)
                        .background(Brush.radialGradient(listOf(Color(0x44FFB3BA), Color.Transparent)))
                    )
                    
                    // Floating Orb 2 (Blue)
                    Box(Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-60).dp, y = (-40 - floatOffsetY * 3).dp)
                        .size(350.dp)
                        .background(Brush.radialGradient(listOf(Color(0x44BAE1FF), Color.Transparent)))
                    )

                    // Floating Orb 3 (Yellow)
                    Box(Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-20).dp, y = (20 + floatOffsetY).dp)
                        .size(150.dp)
                        .background(Brush.radialGradient(listOf(Color(0x44FFFFBA), Color.Transparent)))
                    )
                    
                    val textOffsetX by infiniteTransition.animateFloat(
                        initialValue = -150f,
                        targetValue = 150f,
                        animationSpec = infiniteRepeatable(tween(8000, easing = FastOutSlowInEasing), RepeatMode.Reverse)
                    )

                    // Large watermark text to make it feel fuller and dynamic
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "STUDIO",
                            color = Color.Black.copy(alpha = 0.03f),
                            fontWeight = FontWeight.Black,
                            fontSize = androidx.compose.ui.unit.TextUnit(120f, androidx.compose.ui.unit.TextUnitType.Sp),
                            modifier = Modifier
                                .offset(y = (-40).dp, x = textOffsetX.dp)
                                .graphicsLayer(rotationZ = -5f)
                        )
                    }

                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // Back Strip (tilted left, floating up)
                        PhotostripGraphic(
                            modifier = Modifier
                                .offset(x = (-40).dp, y = floatOffsetY.dp)
                                .graphicsLayer(rotationZ = -12f)
                        )
                        
                        // Front Strip (tilted right, floating down)
                        PhotostripGraphic(
                            modifier = Modifier
                                .offset(x = 40.dp, y = (-floatOffsetY).dp)
                                .graphicsLayer(rotationZ = 8f)
                        )
                    }
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
                "PHOTOBOOTH",
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
