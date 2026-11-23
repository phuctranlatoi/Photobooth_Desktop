package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phuctran.photobooth.desktop.config.BoothOperatingMode
import com.phuctran.photobooth.desktop.model.*
import com.phuctran.photobooth.desktop.ui.components.*
import com.phuctran.photobooth.desktop.ui.theme.*

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.StateFlow

private enum class StudioStep {
    LAYOUT,
    FRAME_PREVIEW,
    EFFECT
}

internal fun filterPreviewFrames(frames: List<FramePack>): List<FramePack> =
    frames.filterNot { it.id == "empty" }

internal fun resolveActivePreviewFrame(
    previewFrames: List<FramePack>,
    currentPreview: FramePack?,
    layout: LayoutMode
): FramePack =
    currentPreview?.takeIf { candidate -> previewFrames.any { it.id == candidate.id } }
        ?: previewFrames.firstOrNull()
        ?: FramePack(
            id = "preview_default_${layout.id}",
            title = "Khung mặc định",
            description = "",
            accentColor = layout.accentColor
        )

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
    onBack: () -> Unit,
    operatingMode: BoothOperatingMode = BoothOperatingMode.COMMERCIAL,
    frames: List<FramePack> = emptyList(),
    maxVisibleFilters: Int = 3
) {
    val isStandaloneKiosk = operatingMode == BoothOperatingMode.STANDALONE_KIOSK
    val totalSteps = if (isStandaloneKiosk) 3 else 2
    var step by remember(isStandaloneKiosk) { mutableStateOf(StudioStep.LAYOUT) }

    val previewFrames = remember(frames) { filterPreviewFrames(frames) }
    var previewFrame by remember(selectedLayout.id, previewFrames) {
        mutableStateOf(previewFrames.firstOrNull())
    }
    val activePreviewFrame = remember(previewFrames, previewFrame, selectedLayout.id, selectedLayout.accentColor) {
        resolveActivePreviewFrame(
            previewFrames = previewFrames,
            currentPreview = previewFrame,
            layout = selectedLayout
        )
    }

    when (step) {
        StudioStep.LAYOUT -> {
            PremiumPageShell(
                title = "Chọn bố cục",
                subtitle = "Lướt để xem các bố cục và nhấp vào bố cục ở giữa để đi tiếp.",
                currentStep = 1,
                totalSteps = totalSteps,
                onBack = onBack
            ) {
                Step1Layout(
                    layouts = layouts,
                    selectedLayout = selectedLayout,
                    onLayoutSelect = {
                        onLayoutSelected(it.id)
                        step = if (isStandaloneKiosk) StudioStep.FRAME_PREVIEW else StudioStep.EFFECT
                    },
                    onBack = onBack
                )
            }
        }
        StudioStep.FRAME_PREVIEW -> {
            Step2FramePreview(
                layout = selectedLayout,
                previewFrames = previewFrames,
                activePreviewFrame = activePreviewFrame,
                onFramePreview = { previewFrame = it },
                onBack = { step = StudioStep.LAYOUT },
                onContinue = { step = StudioStep.EFFECT }
            )
        }
        StudioStep.EFFECT -> {
            Step2Effect(
                effects = effects,
                selectedEffect = selectedEffect,
                selectedLayout = selectedLayout,
                liveViewStream = liveViewStream,
                onEffectSelect = onEffectSelected,
                onBack = { step = if (isStandaloneKiosk) StudioStep.FRAME_PREVIEW else StudioStep.LAYOUT },
                onConfirm = onConfirm,
                maxVisibleFilters = maxVisibleFilters,
                stepLabel = if (isStandaloneKiosk) "Bước 3 / 3" else "Bước 2 / 2"
            )
        }
    }
}

@Composable
private fun Step2FramePreview(
    layout: LayoutMode,
    previewFrames: List<FramePack>,
    activePreviewFrame: FramePack,
    onFramePreview: (FramePack) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KioskBackButton(onClick = onBack)
                Spacer(Modifier.width(20.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "BƯỚC 2 · XEM TRƯỚC KHUNG",
                        style = MaterialTheme.typography.overline,
                        fontWeight = FontWeight.Bold,
                        color = AccentNudeDark
                    )
                    Text(
                        text = "Xem trước khung trang trí",
                        style = MaterialTheme.typography.h4,
                        color = NeutralText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Chạm vào từng mẫu để xem trước. Bạn sẽ chọn khung chính thức sau khi chụp và chọn ảnh.",
                        style = MaterialTheme.typography.body2,
                        color = NeutralSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                MinimalStepIndicator(currentStep = 2, totalSteps = 3)
                KioskPrimaryButton(
                    text = "Tiếp tục chọn màu",
                    onClick = onContinue,
                    modifier = Modifier.widthIn(min = 208.dp)
                )
            }
        }

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Column(
                modifier = Modifier.weight(1.1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionHeader(
                    badge = "XEM TRƯỚC",
                    title = "Bản in minh họa",
                    subtitle = "${activePreviewFrame.title} · ${layout.printSizeLabel}"
                )
                PrintPreview(
                    layout = layout,
                    moments = emptyList(),
                    frame = activePreviewFrame,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            Column(
                modifier = Modifier.weight(0.9f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionHeader(
                    badge = "",
                    title = "Các mẫu khung",
                    subtitle = "Chạm một mẫu để đổi bản xem trước bên trái."
                )
                if (previewFrames.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Chưa có mẫu khung để xem trước",
                                style = MaterialTheme.typography.h6,
                                fontWeight = FontWeight.Bold,
                                color = NeutralText,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Bố cục này vẫn dùng được. Bạn có thể tiếp tục với khung mặc định.",
                                style = MaterialTheme.typography.body2,
                                color = NeutralSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    val gridState = rememberLazyGridState()
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f).fillMaxWidth().kioskVerticalDrag(gridState),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(previewFrames, key = { it.id }) { frame ->
                            FramePreviewTile(
                                frame = frame,
                                isPreviewed = frame.id == activePreviewFrame.id,
                                onClick = { onFramePreview(frame) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FramePreviewTile(
    frame: FramePack,
    isPreviewed: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember(frame.id) { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 100)
    )
    val borderColor by animateColorAsState(
        targetValue = if (isPreviewed) AccentNude else NeutralBorder,
        animationSpec = tween(durationMillis = 150)
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isPreviewed) AccentNudeLight.copy(alpha = 0.35f) else Color.White,
        animationSpec = tween(durationMillis = 150)
    )
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, frame.customImagePath) {
        value = frame.customImagePath?.let { path ->
            withContext(Dispatchers.IO) { loadFrameThumbnail(path) }
        }
    }

    val tileShape = RoundedCornerShape(LiquidGlassPlateMetrics.cardCornerRadius)
    val borderWidth = if (isPreviewed) 2.dp else 1.dp
    val accessibilityText = if (isPreviewed) "Đang xem trước ${frame.title}" else "Xem trước ${frame.title}"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(4.dp, tileShape, clip = false)
            .clip(tileShape)
            .background(backgroundColor)
            .border(borderWidth, borderColor, tileShape)
            .semantics {
                role = Role.Button
                contentDescription = accessibilityText
            }
            .bouncyClickable(interactionSource = interactionSource, onClick = onClick)
    ) {
        // Inner photographic stage
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .background(NeutralPanelAlt.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val customPath = frame.customImagePath
            val bmp = imageBitmap
            if (customPath != null) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(NeutralPanelAlt))
                        Text(
                            text = "Đang tải khung",
                            color = NeutralMuted,
                            style = MaterialTheme.typography.caption,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = frame.title,
                        color = NeutralText,
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (frame.description.isNotBlank()) {
                        Text(
                            text = frame.description,
                            color = NeutralSecondary,
                            style = MaterialTheme.typography.body2,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (isPreviewed) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AccentNudeLight)
                    .border(1.dp, AccentNude.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(AccentNude)
                )
                Text(
                    text = "ĐANG XEM",
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Bold,
                    color = AccentNudeDark
                )
            }
        }
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
                modifier = Modifier.fillMaxSize().kioskPagerDrag(pagerState)
            ) { page ->
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val proximity = 1f - kotlin.math.abs(pageOffset).coerceIn(0f, 1f)
                val focus = proximity * proximity * (3f - 2f * proximity) // smoothstep
                val scale = androidx.compose.ui.util.lerp(0.84f, 1f, focus)
                val contentAlpha = androidx.compose.ui.util.lerp(0.55f, 1f, focus)
                
                val actualPage = page % layouts.size.coerceAtLeast(1)
                val layout = layouts[actualPage]
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(focus)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            alpha = contentAlpha
                            translationY = androidx.compose.ui.util.lerp(10.dp.toPx(), 0f, focus)
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
    onConfirm: () -> Unit,
    maxVisibleFilters: Int = 3,
    stepLabel: String = "Bước 2 / 2"
) {
    val visibleEffects = remember(effects, maxVisibleFilters) {
        effects.filter { it.enabled }.take(maxVisibleFilters.coerceIn(1, 10))
    }
    LaunchedEffect(visibleEffects, selectedEffect.id) {
        if (visibleEffects.isNotEmpty() && visibleEffects.none { it.id == selectedEffect.id }) {
            onEffectSelect(visibleEffects.first().id)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 1024.dp
        val maxDockWidth = if (compact) maxWidth - 48.dp else minOf(720.dp, maxWidth - 440.dp)
        val isBalancedShortList = visibleEffects.size in 1..3
        val dock: @Composable () -> Unit = {
            val dockScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .widthIn(max = maxDockWidth)
                    .liquidGlassPlate(
                        variant = LiquidGlassPlateVariant.Media,
                        cornerRadius = 24.dp,
                        elevation = 14.dp
                    )
                    .padding(8.dp)
                    .horizontalScroll(dockScrollState)
                    .kioskHorizontalDrag(dockScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                visibleEffects.forEach { effect ->
                    val isSelected = effect.id == selectedEffect.id
                    val cleanTitle = remember(effect.title) { formatEffectDisplayName(effect.title) }
                    val interactionSource = remember(effect.id) { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    Row(
                        modifier = Modifier
                            .height(64.dp)
                            .then(if (isBalancedShortList) Modifier.widthIn(min = 148.dp) else Modifier)
                            .graphicsLayer {
                                scaleX = if (isPressed) 0.97f else 1f
                                scaleY = if (isPressed) 0.97f else 1f
                            }
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected) AccentNude else Color.Transparent)
                            .clickable(interactionSource = interactionSource, indication = null) { onEffectSelect(effect.id) }
                            .padding(horizontal = if (isBalancedShortList) 22.dp else 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(14.dp).clip(CircleShape)
                                .background(Color(effect.accentColor))
                                .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.45f), CircleShape)
                        )
                        Text(
                            cleanTitle,
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
                .liquidGlassPlate(
                    variant = LiquidGlassPlateVariant.Media,
                    cornerRadius = 24.dp,
                    elevation = 12.dp
                )
                .padding(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KioskBackButton(onClick = onBack, light = true)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stepLabel, style = MaterialTheme.typography.caption, color = Color.White.copy(alpha = 0.8f))
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

internal fun formatEffectDisplayName(title: String): String {
    val cleaned = title.trim()
        .replace(Regex("(?i)\\.cube\\b"), "")
        .replace(Regex("(?i)(?:[\\s_-]*\\d+x\\d+)(?=$|[\\s_-])"), "")
        .replace(Regex("(?i)(?:[\\s_-]*v\\d+)(?=$|[\\s_-])"), "")
        .replace(Regex("[_-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return cleaned.ifEmpty { title.trim() }
}

@Composable
private fun StudioLiveView(liveViewStream: StateFlow<ImageBitmap?>, selectedEffect: EffectMode) {
    val liveViewBitmap by liveViewStream.collectAsState()
    if (liveViewBitmap != null) {
        val colorFilter = remember(selectedEffect) {
            if (selectedEffect.isLut) null else
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
