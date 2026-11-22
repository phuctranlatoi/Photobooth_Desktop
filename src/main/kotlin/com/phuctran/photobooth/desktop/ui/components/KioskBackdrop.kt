package com.phuctran.photobooth.desktop.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.max

enum class KioskBackdropMode {
    Guest,
    Admin
}

/**
 * Multi-layer warm optical kiosk backdrop rendered via [drawWithCache] so gradient brushes
 * and caustic paths are cached per size change with zero per-frame allocations.
 */
@Composable
fun KioskBackdrop(
    mode: KioskBackdropMode = KioskBackdropMode.Guest,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isGuest = mode == KioskBackdropMode.Guest
    val intensity = if (isGuest) 1.0f else 0.58f

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val maxDim = max(w, h).coerceAtLeast(1f)

                // 1. Foundation gradient (warm cream to soft beige)
                val foundationBrush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFFFCF9F5),
                        0.48f to Color(0xFFF9F4EE),
                        1.00f to Color(0xFFF2EAE0)
                    ),
                    start = Offset.Zero,
                    end = Offset(w, h)
                )

                // 2. Upper-left photographic key light (soft luminous glow)
                val keyLightBrush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF).copy(alpha = 0.72f * intensity),
                        Color(0xFFFFF5EC).copy(alpha = 0.38f * intensity),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.14f, h * 0.12f),
                    radius = maxDim * 0.72f
                )

                // 3. Upper amber refraction
                val amberRefractionBrush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFE5D4).copy(alpha = 0.34f * intensity),
                        Color(0xFFFBE8DC).copy(alpha = 0.14f * intensity),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.78f, h * 0.16f),
                    radius = maxDim * 0.58f
                )

                // 4. Lower-right terracotta bounce
                val terracottaBounceBrush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFEAC7B6).copy(alpha = 0.30f * intensity),
                        Color(0xFFE6D9CE).copy(alpha = 0.15f * intensity),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.88f, h * 0.88f),
                    radius = maxDim * 0.75f
                )

                // 5. Subtle caustic light ribbons (optical lens curvature lines)
                val upperRibbonPath = Path().apply {
                    moveTo(-w * 0.05f, h * 0.24f)
                    cubicTo(
                        w * 0.28f, h * 0.06f,
                        w * 0.64f, h * 0.32f,
                        w * 1.05f, h * 0.14f
                    )
                }
                val lowerRibbonPath = Path().apply {
                    moveTo(-w * 0.04f, h * 0.82f)
                    cubicTo(
                        w * 0.34f, h * 0.66f,
                        w * 0.70f, h * 0.92f,
                        w * 1.04f, h * 0.74f
                    )
                }

                val upperRibbonBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.42f * intensity),
                        Color(0xFFFFE8D8).copy(alpha = 0.28f * intensity),
                        Color.Transparent
                    ),
                    startX = 0f,
                    endX = w
                )

                val lowerRibbonBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFFFEFE5).copy(alpha = 0.26f * intensity),
                        Color.White.copy(alpha = 0.35f * intensity),
                        Color.Transparent
                    ),
                    startX = 0f,
                    endX = w
                )

                val ribbonStrokeWidth = 2.dp.toPx()

                // 6. Soft lens vignette
                val vignetteBrush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1.00f to Color(0xFF594538).copy(alpha = 0.055f * intensity)
                    ),
                    center = Offset(w * 0.5f, h * 0.5f),
                    radius = maxDim * 0.78f
                )

                onDrawBehind {
                    drawRect(brush = foundationBrush)
                    drawRect(brush = keyLightBrush)
                    drawRect(brush = amberRefractionBrush)
                    drawRect(brush = terracottaBounceBrush)

                    drawPath(
                        path = upperRibbonPath,
                        brush = upperRibbonBrush,
                        style = Stroke(width = ribbonStrokeWidth)
                    )
                    drawPath(
                        path = lowerRibbonPath,
                        brush = lowerRibbonBrush,
                        style = Stroke(width = ribbonStrokeWidth)
                    )

                    drawRect(brush = vignetteBrush)
                }
            },
        content = content
    )
}
