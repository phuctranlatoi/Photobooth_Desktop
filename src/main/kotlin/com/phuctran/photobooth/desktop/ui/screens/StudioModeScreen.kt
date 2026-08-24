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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.launch

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.scrollBy
import com.phuctran.photobooth.desktop.domain.SessionState
import com.phuctran.photobooth.desktop.model.*
import com.phuctran.photobooth.desktop.ui.components.*
import com.phuctran.photobooth.desktop.ui.theme.*


import java.nio.file.Path

@Composable
fun StudioModeScreen(
    layouts: List<LayoutMode>,
    effects: List<EffectMode>,
    selectedLayout: LayoutMode,
    selectedEffect: EffectMode,
    liveViewBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    onLayoutSelected: (String) -> Unit,
    onEffectSelected: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    var step by remember { mutableStateOf(1) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // Top Progress Bar
        Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            StepIndicator(step = 1, currentStep = step, label = "Bố cục")
            Spacer(Modifier.width(32.dp))
            Divider(Modifier.width(100.dp), color = if (step >= 2) MaterialTheme.colors.primary else NeutralBorder)
            Spacer(Modifier.width(32.dp))
            StepIndicator(step = 2, currentStep = step, label = "Màu ảnh")
        }

        PanelBox(Modifier.fillMaxSize()) {
            androidx.compose.animation.AnimatedContent(
                targetState = step
            ) { targetStep ->
                when (targetStep) {
                    1 -> Step1Layout(
                        layouts = layouts,
                        selectedLayout = selectedLayout,
                        onLayoutSelect = {
                            onLayoutSelected(it.id)
                            step = 2
                        },
                        onBack = onBack
                    )
                    2 -> Step2Effect(
                        effects = effects,
                        selectedEffect = selectedEffect,
                        selectedLayout = selectedLayout,
                        liveViewBitmap = liveViewBitmap,
                        onEffectSelect = onEffectSelected,
                        onBack = { step = 1 },
                        onConfirm = onConfirm
                    )
                }
            }
        }
    }
}

@Composable
fun StepIndicator(step: Int, currentStep: Int, label: String) {
    val isActive = currentStep >= step
    val color = if (isActive) MaterialTheme.colors.primary else NeutralMuted
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isActive) color else NeutralPanel),
            contentAlignment = Alignment.Center
        ) {
            Text(step.toString(), color = if (isActive) Color.White else NeutralMuted, fontWeight = FontWeight.Bold)
        }
        Text(label, color = color, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.h6)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Step1Layout(
    layouts: List<LayoutMode>,
    selectedLayout: LayoutMode,
    onLayoutSelect: (LayoutMode) -> Unit,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val actualInitialIndex = layouts.indexOfFirst { it.id == selectedLayout.id }.coerceAtLeast(0)
    val safeSize = layouts.size.coerceAtLeast(1)
    val middle = 50_000
    val initialPage = middle - (middle % safeSize) + actualInitialIndex
    
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = initialPage,
        pageCount = { 100_000 }
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Text("←", fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            SectionHeader("Bước 1", "Chọn bố cục", "Lướt để xem các bố cục và nhấp vào bố cục ở giữa để đi tiếp.")
        }

        Spacer(Modifier.height(32.dp))

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val pageWidth = 400.dp
            val horizontalPadding = if (maxWidth > pageWidth) (maxWidth - pageWidth) / 2 else 0.dp
            
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                pageSpacing = 0.dp,
                beyondBoundsPageCount = 2,
                flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = androidx.compose.foundation.pager.PagerSnapDistance.atMost(20)
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            coroutineScope.launch {
                                pagerState.scrollBy(-delta)
                            }
                        },
                        onDragStopped = { velocity: Float ->
                            coroutineScope.launch {
                                val pagesToSpin = (velocity / 500f).toInt().coerceIn(-10, 10)
                                val targetPage = pagerState.currentPage - pagesToSpin
                                pagerState.animateScrollToPage(targetPage)
                            }
                        }
                    )
            ) { page ->
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val absoluteOffset = kotlin.math.abs(pageOffset)
                val scale = androidx.compose.ui.util.lerp(
                    start = 0.8f,
                    stop = 1f,
                    fraction = 1f - absoluteOffset.coerceIn(0f, 1f)
                )
                val alpha = androidx.compose.ui.util.lerp(
                    start = 0.3f,
                    stop = 1f,
                    fraction = 1f - absoluteOffset.coerceIn(0f, 1f)
                )
                
                val actualPage = page % layouts.size.coerceAtLeast(1)
                val layout = layouts[actualPage]
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (pagerState.currentPage == page) {
                                onLayoutSelect(layout)
                            } else {
                                coroutineScope.launch { pagerState.animateScrollToPage(page) }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    PrintPreview(
                        layout = layout,
                        moments = emptyList(),
                        frame = FramePack("preview", "Preview", "", layout.accentColor),
                        modifier = Modifier.fillMaxHeight(0.9f)
                    )
                }
            }
        }
        
        // Bottom indicator / title
        val currentLayout = layouts.getOrNull(pagerState.currentPage)
        if (currentLayout != null) {
            Column(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(currentLayout.title, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold, color = NeutralText)
                Text(currentLayout.subtitle, style = MaterialTheme.typography.subtitle1, color = NeutralMuted)
            }
        }
    }
}

@Composable
fun Step2Effect(
    effects: List<EffectMode>,
    selectedEffect: EffectMode,
    selectedLayout: LayoutMode,
    liveViewBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    onEffectSelect: (String) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        // Main Area: Live View / Preview (Full bleed)
        if (liveViewBitmap != null) {
            val colorFilter = remember(selectedEffect.id) {
                when (selectedEffect.id) {
                    "black_white" -> {
                        val matrix = androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
                        androidx.compose.ui.graphics.ColorFilter.colorMatrix(matrix)
                    }
                    "vintage" -> {
                        val matrix = androidx.compose.ui.graphics.ColorMatrix(
                            floatArrayOf(
                                0.393f, 0.769f, 0.189f, 0f, 0f,
                                0.349f, 0.686f, 0.168f, 0f, 0f,
                                0.272f, 0.534f, 0.131f, 0f, 0f,
                                0f,     0f,     0f,     1f, 0f
                            )
                        )
                        androidx.compose.ui.graphics.ColorFilter.colorMatrix(matrix)
                    }
                    else -> null
                }
            }
            
            Image(
                bitmap = liveViewBitmap,
                contentDescription = "Live View Preview",
                modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = -1f),
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter
            )
        } else {
            // Fallback if camera is off - DO NOT show PrintPreview here. Show a clean dark placeholder.
            Box(Modifier.fillMaxSize().background(Color(0xFF1A1A1A)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Camera Not Available", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.h6)
                }
            }
        }
        
        // Gradient overlay for better text/button readability
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f)
                        )
                    )
                )
        )
        
        // Top Left: Back Button & Header
        Row(Modifier.align(Alignment.TopStart).padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
            ) {
                Text("←", fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Bước 2", style = MaterialTheme.typography.overline, color = Color.White.copy(alpha = 0.7f))
                Text("Màu ảnh", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black, color = Color.White)
            }
        }
        
        // Top Right: Confirm Button
        BouncyButton(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(backgroundColor = AccentNude, contentColor = Color.White),
            modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).height(56.dp).width(160.dp)
        ) {
            Text("TIẾP TỤC", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.subtitle1)
        }
        
        // Bottom Area: Floating Effect Selector
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            effects.forEach { effect ->
                val isSelected = effect.id == selectedEffect.id
                val bgColor = if (isSelected) AccentNude else Color.White
                val textColor = if (isSelected) Color.White else NeutralText
                
                Column(
                    modifier = Modifier
                        .width(160.dp)
                        .height(140.dp)
                        .shadow(if (isSelected) 16.dp else 4.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(bgColor)
                        .clickable { onEffectSelect(effect.id) }
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(Color(effect.accentColor)))
                    Spacer(Modifier.height(12.dp))
                    Text(effect.title, fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.subtitle1, maxLines = 1)
                }
            }
        }
    }
}
