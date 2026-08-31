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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Surface
import androidx.compose.ui.text.style.TextOverflow
import com.phuctran.photobooth.desktop.ui.components.loadImageBitmap

@Composable
fun StartScreen(
    specialFrames: List<com.phuctran.photobooth.desktop.model.FramePack>,
    onSpecialSelected: (com.phuctran.photobooth.desktop.model.FramePack) -> Unit,
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
            if (specialFrames.isNotEmpty()) {
                val groupedFrames = remember(specialFrames) {
                    specialFrames.groupBy { it.specialEventName ?: "Sự Kiện Khác" }
                }
                var selectedEvent by remember { mutableStateOf<String?>(null) }
                
                if (selectedEvent == null) {
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        Text("Sự Kiện Đặc Biệt", color = Color.White, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Chọn một sự kiện để xem các mẫu khung", color = NeutralMuted, style = MaterialTheme.typography.body2)
                        Spacer(Modifier.height(24.dp))
                        
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(groupedFrames.keys.toList()) { eventName ->
                                val firstFrame = groupedFrames[eventName]?.firstOrNull()
                                SpecialEventItem(
                                    eventName = eventName,
                                    previewFrame = firstFrame,
                                    onClick = { selectedEvent = eventName }
                                )
                            }
                        }
                    }
                } else {
                    val frames = groupedFrames[selectedEvent] ?: emptyList()
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material.IconButton(onClick = { selectedEvent = null }) {
                                Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(selectedEvent!!, color = Color.White, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Chọn ngay một mẫu khung bên dưới để bắt đầu", color = NeutralMuted, style = MaterialTheme.typography.body2)
                        Spacer(Modifier.height(24.dp))
                        
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(frames) { frame ->
                                SpecialBundleItem(frame = frame, onClick = { onSpecialSelected(frame) })
                            }
                        }
                    }
                }
            } else {
                // Hiển thị đồ họa mặc định nếu không có sự kiện đặc biệt
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
                    Text("Photobooth", color = Color.White, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black)
                    Text("Vui lòng thiết lập Khung Sự Kiện (Special) trong Admin.", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.body2)
                }
            }
        }
    }
}

@Composable
fun SpecialEventItem(eventName: String, previewFrame: com.phuctran.photobooth.desktop.model.FramePack?, onClick: () -> Unit) {
    var isHovered by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isHovered) 1.02f else 1f)
    
    val bitmap = remember(previewFrame?.customImagePath) { previewFrame?.customImagePath?.let(::loadImageBitmap) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = Color(0xFF2A2631),
        elevation = if (isHovered) 12.dp else 4.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = eventName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    )
                } else {
                    Icon(androidx.compose.material.icons.Icons.Default.Favorite, contentDescription = null, tint = AccentNude, modifier = Modifier.size(48.dp))
                }
            }
            Column(Modifier.fillMaxWidth().background(Color(0xFF2A2631)).padding(16.dp)) {
                Text(
                    text = eventName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.subtitle1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Bấm để xem bộ khung",
                    color = AccentNude,
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SpecialBundleItem(frame: com.phuctran.photobooth.desktop.model.FramePack, onClick: () -> Unit) {
    var isHovered by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isHovered) 1.02f else 1f)
    
    val bitmap = remember(frame.customImagePath) { frame.customImagePath?.let(::loadImageBitmap) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = Color(0xFF2A2631),
        elevation = if (isHovered) 12.dp else 4.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = frame.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(12.dp)
                    )
                } else {
                    Text("No Image", color = Color.Gray)
                }
            }
            Column(Modifier.fillMaxWidth().background(Color(0xFF2A2631)).padding(16.dp)) {
                Text(
                    text = frame.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.subtitle1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Bấm để chụp",
                    color = AccentNude,
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Medium
                )
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
