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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phuctran.photobooth.desktop.domain.SessionState
import com.phuctran.photobooth.desktop.model.*
import com.phuctran.photobooth.desktop.ui.components.*
import com.phuctran.photobooth.desktop.ui.theme.*


import java.nio.file.Path
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.StateFlow

@Composable
fun StudioModeScreen(
    layouts: List<LayoutMode>,
    effects: List<EffectMode>,
    selectedLayout: LayoutMode,
    selectedEffect: EffectMode,
    liveViewStream: StateFlow<ImageBitmap?>,
    onLayoutSelected: (String) -> Unit,
    onEffectSelected: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    var step by remember { mutableStateOf(1) }

    if (step == 1) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            KioskBackButton(onClick = { if (step == 1) onBack() else step = 1 })
            Spacer(Modifier.width(16.dp))
            Column {
                Text(if (step == 1) "Chọn bố cục" else "Chọn màu ảnh", style = MaterialTheme.typography.h6, color = NeutralText)
                Text("Bước $step / 2", style = MaterialTheme.typography.caption, color = NeutralSecondary)
            }
            Spacer(Modifier.weight(1f))
            StepIndicator(step = 1, currentStep = step, label = "Bố cục")
            Spacer(Modifier.width(32.dp))
            Divider(Modifier.width(100.dp), color = if (step >= 2) MaterialTheme.colors.primary else NeutralBorder)
            Spacer(Modifier.width(32.dp))
            StepIndicator(step = 2, currentStep = step, label = "Màu ảnh")
        }

        PanelBox(Modifier.fillMaxSize()) {
            Step1Layout(
                layouts = layouts,
                selectedLayout = selectedLayout,
                onLayoutSelect = {
                    onLayoutSelected(it.id)
                    step = 2
                },
                onBack = onBack
            )
        }
        }
    } else {
        Step2Effect(
            effects = effects,
            selectedEffect = selectedEffect,
            selectedLayout = selectedLayout,
            liveViewStream = liveViewStream,
            onEffectSelect = onEffectSelected,
            onBack = { step = 1 },
            onConfirm = onConfirm
        )
    }
}

@Composable
fun StepIndicator(step: Int, currentStep: Int, label: String) {
    val isActive = currentStep >= step
    val color = if (isActive) AccentNudeDark else NeutralMuted
    val alpha = if (isActive) 1f else 0.5f
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally, 
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.graphicsLayer(alpha = alpha)
    ) {
        Text(
            "STEP 0$step", 
            color = color, 
            style = MaterialTheme.typography.overline,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Text(
            label.uppercase(), 
            color = color, 
            fontWeight = if (isActive) FontWeight.Black else FontWeight.Medium, 
            style = MaterialTheme.typography.subtitle1,
            letterSpacing = 1.sp
        )
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
            SectionHeader("Bước 1", "Chọn bố cục", "Lướt để xem các bố cục và nhấp vào bố cục ở giữa để đi tiếp.")
        }

        Spacer(Modifier.height(32.dp))

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val pageWidth = maxWidth * 0.3f
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
                modifier = Modifier.fillMaxSize()
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
        val safeLayoutCount = layouts.size.coerceAtLeast(1)
        val currentLayout = layouts.getOrNull(pagerState.currentPage % safeLayoutCount)
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
    liveViewStream: StateFlow<ImageBitmap?>,
    onEffectSelect: (String) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 1024.dp
        val dock: @Composable () -> Unit = {
            Row(
                modifier = Modifier
                    .widthIn(max = 540.dp)
                    .shadow(12.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xE6262528))
                    .padding(8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                effects.forEach { effect ->
                    val isSelected = effect.id == selectedEffect.id
                    Row(
                        modifier = Modifier
                            .height(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected) AccentNude else Color.Transparent)
                            .clickable { onEffectSelect(effect.id) }
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(14.dp).clip(CircleShape)
                                .background(Color(effect.accentColor))
                                .border(1.dp, Color.White.copy(alpha = 0.65f), CircleShape)
                        )
                        Text(
                            effect.title,
                            style = MaterialTheme.typography.subtitle1,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))) {
            StudioLiveView(liveViewStream, selectedEffect)
            Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent, Color.Black.copy(alpha = 0.5f))
                    )
                )
            )
        }

        Row(
            Modifier.align(Alignment.TopStart).padding(24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xD9262528))
                .padding(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KioskBackButton(onClick = onBack, light = true)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Bước 2 / 2", style = MaterialTheme.typography.caption, color = Color.White.copy(alpha = 0.8f))
                Text("Màu ảnh", style = MaterialTheme.typography.h5, color = Color.White)
            }
        }

        if (compact) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                dock()
                KioskPrimaryButton("TIẾP TỤC", onClick = onConfirm, modifier = Modifier.align(Alignment.End).width(180.dp).height(64.dp))
            }
        } else {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) { dock() }
            KioskPrimaryButton(
                "TIẾP TỤC",
                onClick = onConfirm,
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).width(180.dp).height(64.dp)
            )
        }
    }
}

@Composable
private fun StudioLiveView(liveViewStream: StateFlow<ImageBitmap?>, selectedEffect: EffectMode) {
    val liveViewBitmap by liveViewStream.collectAsState()
    if (liveViewBitmap != null) {
        val colorFilter = remember(selectedEffect) {
            androidx.compose.ui.graphics.ColorFilter.colorMatrix(selectedEffect.toComposeColorMatrix())
        }
        Image(
            bitmap = liveViewBitmap!!,
            contentDescription = "Xem trực tiếp từ máy ảnh",
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = -1f),
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter
        )
    } else {
        Box(Modifier.fillMaxSize().background(CameraBlack), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                androidx.compose.foundation.Canvas(Modifier.size(56.dp)) {
                    val ink = Color.White.copy(alpha = 0.48f)
                    drawRoundRect(
                        color = ink,
                        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.25f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.84f, size.height * 0.58f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.1f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
                    )
                    drawCircle(ink, radius = size.width * 0.14f, center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx()))
                    drawRoundRect(
                        color = ink,
                        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height * 0.16f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.28f, size.height * 0.1f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                    )
                }
                Text("Chưa có tín hiệu máy ảnh", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.h6)
            }
        }
    }
}
