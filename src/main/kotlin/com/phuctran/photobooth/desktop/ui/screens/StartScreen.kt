package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.ui.components.*
import com.phuctran.photobooth.desktop.ui.theme.*

@Composable
fun StartScreen(
    layouts: List<com.phuctran.photobooth.desktop.model.LayoutMode>,
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
                        Text("Bundle Special", color = Color.White, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Chọn dịp để xem các khung độc quyền", color = NeutralMuted, style = MaterialTheme.typography.body2)
                        Spacer(Modifier.height(24.dp))
                        
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 240.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(groupedFrames.keys.toList()) { eventName ->
                                val frames = groupedFrames[eventName] ?: emptyList()
                                SpecialEventItem(
                                    eventName = eventName,
                                    frames = frames,
                                    onClick = { selectedEvent = eventName }
                                )
                            }
                        }
                    }
                } else {
                    val frames = groupedFrames[selectedEvent] ?: emptyList()
                    val layoutIds = remember(frames) { frames.mapNotNull { it.targetLayoutId }.distinct().sorted() }
                    var selectedLayoutId by remember(selectedEvent) { mutableStateOf(layoutIds.firstOrNull()) }
                    
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material.IconButton(onClick = { selectedEvent = null }) {
                                Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(selectedEvent!!.uppercase(), color = Color.White, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text("${layoutIds.size} bố cục • ${frames.size} mẫu khung", color = AccentNude, style = MaterialTheme.typography.caption)
                            }
                        }
                        
                        Spacer(Modifier.height(24.dp))
                        Text("Chọn bố cục", color = Color.White, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                            layoutIds.forEach { layoutId ->
                                val isSelected = selectedLayoutId == layoutId
                                val targetLayout = layouts.find { it.id == layoutId }
                                
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) AccentNude else Color(0xFF2A2631),
                                    modifier = Modifier
                                        .height(280.dp)
                                        .aspectRatio(targetLayout?.printAspectRatio ?: 0.66f)
                                        .clickable { selectedLayoutId = layoutId }
                                ) {
                                    Box(Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
                                        if (targetLayout != null) {
                                            LayoutThumbnail(
                                                layout = targetLayout,
                                                modifier = Modifier.fillMaxSize(),
                                                accentColor = Color(targetLayout.accentColor)
                                            )
                                        } else {
                                            Text(
                                                text = layoutId,
                                                color = if (isSelected) Color.Black else Color.White,
                                                modifier = Modifier.padding(16.dp),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(24.dp))
                        Text("Chọn mẫu khung", color = Color.White, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        
                        val framesForLayout = frames.filter { it.targetLayoutId == selectedLayoutId }
                        val selectedTargetLayout = layouts.find { it.id == selectedLayoutId }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                            framesForLayout.forEach { frame ->
                                SpecialBundleItem(frame = frame, targetLayout = selectedTargetLayout, onClick = { onSpecialSelected(frame) })
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
fun SpecialEventItem(eventName: String, frames: List<com.phuctran.photobooth.desktop.model.FramePack>, onClick: () -> Unit) {
    var isHovered by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isHovered) 1.02f else 1f)
    
    val coverPath = remember(eventName) {
        com.phuctran.photobooth.desktop.config.DesktopAppPaths.appDataDir().resolve("data").resolve("covers").resolve("$eventName.png")
    }
    val coverBitmap = remember(coverPath) {
        if (java.nio.file.Files.exists(coverPath)) loadImageBitmap(coverPath) else null
    }
    val previewBitmap = remember(frames, coverBitmap) {
        coverBitmap ?: frames.firstOrNull()?.customImagePath?.let(::loadImageBitmap)
    }
    
    val layouts = remember(frames) { frames.mapNotNull { it.targetLayoutId }.distinct() }
    
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
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap,
                        contentDescription = eventName,
                        contentScale = if (coverBitmap != null) ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(if (coverBitmap != null) 0.dp else 16.dp)
                    )
                } else {
                    Icon(androidx.compose.material.icons.Icons.Default.Favorite, contentDescription = null, tint = AccentNude, modifier = Modifier.size(48.dp))
                }
            }
            Column(Modifier.fillMaxWidth().background(Color(0xFF2A2631)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = eventName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.subtitle1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "SPECIAL BUNDLE",
                        color = AccentNude,
                        style = MaterialTheme.typography.overline,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${layouts.size} bố cục · ${frames.size} mẫu khung",
                    color = NeutralMuted,
                    style = MaterialTheme.typography.caption
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Xem bundle", color = Color.White, style = MaterialTheme.typography.body2, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(4.dp))
                    Icon(androidx.compose.material.icons.Icons.Default.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun SpecialBundleItem(frame: com.phuctran.photobooth.desktop.model.FramePack, targetLayout: com.phuctran.photobooth.desktop.model.LayoutMode?, onClick: () -> Unit) {
    var isHovered by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isHovered) 1.02f else 1f)
    
    val bitmap = remember(frame.customImagePath) { frame.customImagePath?.let(::loadImageBitmap) }
    val aspectRatio = targetLayout?.printAspectRatio ?: 0.66f
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isHovered) AccentNude else Color(0xFF2A2631),
        modifier = Modifier
            .height(280.dp)
            .aspectRatio(aspectRatio)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(4.dp)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = frame.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("No Image", color = Color.Gray)
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
@Composable
fun LayoutThumbnail(layout: com.phuctran.photobooth.desktop.model.LayoutMode, modifier: Modifier = Modifier, accentColor: Color) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White)
            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
    ) {
        val columns = layout.gridColumns.coerceAtLeast(1)
        val rows = kotlin.math.ceil(layout.selectCount / columns.toFloat()).toInt().coerceAtLeast(1)
        
        val top = size.width * layout.paddingTopRatio
        val left = size.width * layout.paddingLeftRatio
        val right = size.width * layout.paddingRightRatio
        val gapX = size.width * layout.gapHorizontalRatio
        val gapY = size.width * layout.gapVerticalRatio
        
        val slotWidth = (size.width - left - right - gapX * (columns - 1).toFloat()) / columns.toFloat()
        val slotHeight = slotWidth / layout.photoAspectRatio

        val slotColor = Color(0xFFE0E0E0) // Light gray for slots

        if (layout.absoluteSlots.isNotEmpty()) {
            layout.absoluteSlots.forEach { slot ->
                val x = size.width * slot.x
                val y = size.height * slot.y
                val slotW = size.width * slot.width
                val slotH = size.height * slot.height
                
                drawRect(
                    color = slotColor,
                    topLeft = androidx.compose.ui.geometry.Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(slotW, slotH)
                )
            }
        } else {
            repeat(layout.selectCount) { index ->
                val row = index / columns
                val column = index % columns
                val x = left + (slotWidth + gapX) * column.toFloat()
                val y = top + (slotHeight + gapY) * row.toFloat()
                
                drawRect(
                    color = slotColor,
                    topLeft = androidx.compose.ui.geometry.Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(slotWidth, slotHeight)
                )
            }
        }
    }
}
