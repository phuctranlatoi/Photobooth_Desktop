package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.ui.components.BouncyButton
import com.phuctran.photobooth.desktop.ui.components.InfoPill
import com.phuctran.photobooth.desktop.ui.theme.AccentNude
import com.phuctran.photobooth.desktop.ui.theme.NeutralBg
import com.phuctran.photobooth.desktop.ui.theme.NeutralMuted
import com.phuctran.photobooth.desktop.ui.theme.NeutralPanel

@Composable
fun StartScreen(onStart: () -> Unit, onAdmin: () -> Unit) {
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
                modifier = Modifier.width(320.dp).height(80.dp)
            ) {
                Text("CHẠM ĐỂ BẮT ĐẦU", fontWeight = FontWeight.Black, style = MaterialTheme.typography.h5)
            }
            
            Spacer(Modifier.height(48.dp))
        }
        
        Box(Modifier.weight(1.2f).fillMaxHeight().padding(24.dp).clip(RoundedCornerShape(24.dp)).background(NeutralPanel)) {
            // Subtle animated gradient background for the preview area
            val color1 by infiniteTransition.animateColor(
                initialValue = Color(0xFFF0F0F0),
                targetValue = Color.White,
                animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse)
            )
            val color2 by infiniteTransition.animateColor(
                initialValue = Color.White,
                targetValue = Color(0xFFE8E8E8),
                animationSpec = infiniteRepeatable(tween(5000), RepeatMode.Reverse)
            )
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors = listOf(color1, color2))))
        }
    }
}
