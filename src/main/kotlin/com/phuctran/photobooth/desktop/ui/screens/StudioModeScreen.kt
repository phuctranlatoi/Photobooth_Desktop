package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
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
    onEffectSelect: (String) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.width(400.dp).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Text("←", fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                SectionHeader("Bước 2", "Màu ảnh", "Chọn bộ lọc màu yêu thích.")
            }
            Spacer(Modifier.height(24.dp))
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                effects.forEach { effect ->
                    ChoiceRow(
                        title = effect.title,
                        subtitle = effect.description,
                        selected = effect.id == selectedEffect.id,
                        accent = Color(effect.accentColor),
                        onClick = { onEffectSelect(effect.id) }
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            BouncyButton(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(80.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary, contentColor = Color.White)
            ) {
                Text("XÁC NHẬN CHỤP", style = MaterialTheme.typography.h4, fontWeight = FontWeight.Black)
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            PrintPreview(
                layout = selectedLayout,
                moments = emptyList(),
                frame = FramePack("preview", "Preview", "", selectedLayout.accentColor),
                modifier = Modifier.fillMaxHeight(0.9f)
            )
        }
    }
}
