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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalLayoutApi::class)
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
    var selectedEvent by remember { mutableStateOf<String?>(null) }
    var selectedLayoutId by remember { mutableStateOf<String?>(null) }

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
                val eventNames = groupedFrames.keys.toList()
                
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = selectedLayoutId != null
                    ) { showFrames ->
                        if (!showFrames) {
                            Column(Modifier.fillMaxSize()) {
                                // TOP ROW: Chọn bundle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text("Chọn bundle", color = Color.White, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.height(4.dp))
                                            Text("Nhiều khung ảnh theo chủ đề đặc biệt cho bạn lựa chọn", color = NeutralMuted, style = MaterialTheme.typography.body2)
                                        }
                                    }
                                }
                                
                                Spacer(Modifier.height(24.dp))
                                
                                // Bundle Slider
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    eventNames.forEach { eventName ->
                                        val isSelected = selectedEvent == eventName
                                        SpecialEventItem(
                                            eventName = eventName,
                                            frames = groupedFrames[eventName] ?: emptyList(),
                                            isSelected = isSelected,
                                            onClick = { 
                                                selectedEvent = eventName 
                                                selectedLayoutId = null // Reset layout selection when changing bundle
                                            }
                                        )
                                    }
                                }
                                
                                Spacer(Modifier.height(24.dp))
                                
                                // BOTTOM ROW: Chọn bố cục
                                if (selectedEvent != null) {
                                    val frames = groupedFrames[selectedEvent] ?: emptyList()
                                    // Extract ALL possible identifiers the user might have used (targetLayoutId or targetPrintSize)
                                    val possibleLayoutIds = remember(frames) { 
                                        frames.flatMap { listOfNotNull(it.targetLayoutId, it.targetPrintSize) }.distinct() 
                                    }
                                    
                                    // Find all layouts that match any of the identifiers
                                    val matchedLayouts = remember(possibleLayoutIds, layouts) {
                                        layouts.filter { it.id in possibleLayoutIds }.sortedBy { it.id }
                                    }
                                    
                                    Text("Chọn bố cục", color = Color.White, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(12.dp))
                                    
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        matchedLayouts.forEach { targetLayout ->
                                            val isLayoutSelected = selectedLayoutId == targetLayout.id
                                            LayoutSelectionCard(
                                                layout = targetLayout,
                                                isSelected = isLayoutSelected,
                                                onClick = { selectedLayoutId = targetLayout.id },
                                                modifier = if (matchedLayouts.size <= 2) Modifier.width(220.dp).height(80.dp) else Modifier.width(180.dp).height(80.dp)
                                            )
                                        }
                                    }
                                    
                                    Spacer(Modifier.weight(1f))
                                    // Bottom Banner
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF2A2631))
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("✨ Mỗi bundle có nhiều mẫu khung độc quyền, cùng bố cục để bạn tha hồ lựa chọn!", color = AccentNude, style = MaterialTheme.typography.body2)
                                    }
                                }
                            }
                        } else {
                            // SHOW FRAMES PAGE
                            Column(Modifier.fillMaxSize()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { selectedLayoutId = null },
                                        modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF2A2631))
                                    ) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text("Chọn mẫu khung", color = Color.White, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(4.dp))
                                        Text("Lướt để xem các khung ảnh và chọn mẫu ưng ý nhất", color = NeutralMuted, style = MaterialTheme.typography.body2)
                                    }
                                }
                                
                                Spacer(Modifier.height(32.dp))
                                
                                val frames = groupedFrames[selectedEvent] ?: emptyList()
                                val filteredFrames = frames.filter { it.targetLayoutId == selectedLayoutId || it.targetPrintSize == selectedLayoutId }
                                val targetLayout = layouts.find { it.id == selectedLayoutId }
                                
                                // Apply size scaling if there are only 1 or 2 frames
                                val itemModifier = if (filteredFrames.size <= 2) {
                                    Modifier.height(520.dp) // Make them even larger if only 2 left
                                } else {
                                    Modifier.height(380.dp) // Normal large size
                                }
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                                    modifier = Modifier.fillMaxWidth().weight(1f).horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    filteredFrames.forEach { frame ->
                                        SpecialBundleItem(
                                            frame = frame,
                                            targetLayout = targetLayout,
                                            onClick = { onSpecialSelected(frame) },
                                            modifier = itemModifier
                                        )
                                    }
                                }
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
fun SpecialEventItem(eventName: String, frames: List<com.phuctran.photobooth.desktop.model.FramePack>, isSelected: Boolean, onClick: () -> Unit) {
    var isHovered by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isHovered && !isSelected) 1.02f else 1f)
    
    val coverPath = remember(eventName) {
        com.phuctran.photobooth.desktop.config.DesktopAppPaths.appDataDir().resolve("data").resolve("covers").resolve("$eventName.png")
    }
    val coverBitmap = remember(coverPath) {
        if (java.nio.file.Files.exists(coverPath)) loadImageBitmap(coverPath) else null
    }
    val previewBitmap = remember(frames, coverBitmap) {
        coverBitmap ?: frames.firstOrNull()?.customImagePath?.let(::loadImageBitmap)
    }
    
    Surface(
        modifier = Modifier
            .width(220.dp)
            .height(330.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp, 
                color = if (isSelected) AccentNude else Color.Transparent, 
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        color = Color(0xFF2A2631),
        elevation = if (isSelected || isHovered) 8.dp else 0.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap,
                    contentDescription = eventName,
                    contentScale = if (coverBitmap != null) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF333333)), contentAlignment = Alignment.Center) {
                    Icon(androidx.compose.material.icons.Icons.Default.Favorite, contentDescription = null, tint = AccentNude, modifier = Modifier.size(48.dp))
                }
            }
            
            // Text overlay at the bottom
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha=0.8f))
                        )
                    )
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = eventName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.subtitle2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Icon(androidx.compose.material.icons.Icons.Default.Favorite, contentDescription = null, tint = AccentNude, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun LayoutSelectionCard(
    layout: com.phuctran.photobooth.desktop.model.LayoutMode, 
    isSelected: Boolean = false, 
    modifier: Modifier = Modifier.height(80.dp).width(180.dp),
    onClick: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) AccentNude else Color(0xFF2A2631),
        border = if (isSelected) BorderStroke(1.dp, Color.White) else null,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.fillMaxHeight().aspectRatio(layout.printAspectRatio), contentAlignment = Alignment.Center) {
                LayoutThumbnail(
                    layout = layout,
                    modifier = Modifier.fillMaxSize(),
                    accentColor = Color(layout.accentColor)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.Center, modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bố cục ${layout.shotCount} ảnh",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.subtitle2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val cleanSize = layout.printSizeLabel.split("_").firstOrNull() ?: layout.printSizeLabel
                Text(
                    text = "Khổ $cleanSize",
                    color = NeutralMuted,
                    style = MaterialTheme.typography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SpecialBundleItem(frame: com.phuctran.photobooth.desktop.model.FramePack, targetLayout: com.phuctran.photobooth.desktop.model.LayoutMode?, modifier: Modifier = Modifier.height(240.dp), onClick: () -> Unit) {
    var isHovered by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isHovered) 1.02f else 1f)
    
    val bitmap = remember(frame.customImagePath) { frame.customImagePath?.let(::loadImageBitmap) }
    val aspectRatio = targetLayout?.printAspectRatio ?: 0.66f
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isHovered) AccentNude else Color(0xFF2A2631),
        modifier = modifier
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
