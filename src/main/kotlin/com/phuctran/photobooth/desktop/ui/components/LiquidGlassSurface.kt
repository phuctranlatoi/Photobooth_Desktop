package com.phuctran.photobooth.desktop.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.LocalContentColor
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.ui.theme.*
import kotlin.math.min

/**
 * Draws the multi-layer 3D optical Liquid Glass surface using [drawWithCache]:
 * - Exterior soft drop shadow
 * - Base body refractive gradient
 * - Glossy crown highlight (top 36%)
 * - Lower caustic internal reflection glow
 * - Meniscus outer rim (bright top-left, reflective bottom-right)
 * - Inset inner specular rim
 */
fun Modifier.drawLiquidGlass(
    palette: LiquidGlassPalette,
    cornerRadius: Dp = LiquidGlassMetrics.standardCornerRadius,
    isPressed: Boolean = false,
    enabled: Boolean = true
): Modifier = this.drawWithCache {
    val activePalette = if (enabled) palette else DisabledGlassPalette
    val w = size.width
    val h = size.height
    val maxRadius = min(w, h) / 2f
    val rPx = min(cornerRadius.toPx(), maxRadius)

    val outerRimPx = LiquidGlassMetrics.outerRimWidth.toPx()
    val innerInsetPx = LiquidGlassMetrics.innerRimInset.toPx()
    val innerRimPx = LiquidGlassMetrics.innerRimWidth.toPx()

    // 1. Exterior shadow geometry
    val shadowOffsetY = (if (isPressed) LiquidGlassMetrics.shadowOffsetYPressed else LiquidGlassMetrics.shadowOffsetYRest).toPx()
    val shadowSpread = (if (isPressed) 1.5.dp else 3.5.dp).toPx()

    // 2. Base body gradient
    val stops = (if (isPressed && enabled) activePalette.pressedBodyStops else activePalette.bodyStops).toTypedArray()
    val bodyBrush = Brush.verticalGradient(
        colorStops = stops,
        startY = 0f,
        endY = h
    )

    // Clip path for interior optical layers
    val shapePath = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(0f, 0f, w, h),
                cornerRadius = CornerRadius(rPx, rPx)
            )
        )
    }

    // 3. Glossy crown highlight (top 36%)
    val crownHeight = (h * LiquidGlassMetrics.crownHeightFraction).coerceAtLeast(1f)
    val crownInset = outerRimPx * 0.9f
    val crownCorner = (rPx - crownInset).coerceAtLeast(0f)
    val crownBrush = Brush.verticalGradient(
        colors = listOf(
            activePalette.crownHighlightTop,
            activePalette.crownHighlightBottom
        ),
        startY = crownInset,
        endY = crownHeight
    )

    // 4. Lower caustic glow
    val causticBrush = Brush.radialGradient(
        colors = listOf(
            activePalette.causticGlow,
            Color.Transparent
        ),
        center = Offset(w * 0.5f, h * 0.92f),
        radius = (min(w, h) * 0.78f).coerceAtLeast(1f)
    )

    // 5. Meniscus outer rim (bright top-left, mid transition, darker reflective bottom-right)
    val outerRimBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to activePalette.rimTopLeft,
            0.52f to activePalette.rimMid,
            1.00f to activePalette.rimBottomRight
        ),
        start = Offset.Zero,
        end = Offset(w, h)
    )

    // 6. Inset inner rim & specular highlight
    val innerRectLeft = innerInsetPx
    val innerRectTop = innerInsetPx
    val innerRectW = (w - innerInsetPx * 2f).coerceAtLeast(0f)
    val innerRectH = (h - innerInsetPx * 2f).coerceAtLeast(0f)
    val innerRadius = (rPx - innerInsetPx).coerceAtLeast(0f)
    val innerRimBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to activePalette.innerRimTop,
            0.45f to Color.Transparent,
            1.00f to activePalette.innerRimBottom
        ),
        startY = innerRectTop,
        endY = innerRectTop + innerRectH
    )

    onDrawBehind {
        // Exterior soft multi-step shadow
        if (enabled && activePalette.shadowSpot.alpha > 0f) {
            drawRoundRect(
                color = activePalette.shadowAmbient,
                topLeft = Offset(-shadowSpread * 0.4f, shadowOffsetY * 0.7f),
                size = Size(w + shadowSpread * 0.8f, h + shadowSpread * 0.6f),
                cornerRadius = CornerRadius(rPx + shadowSpread * 0.5f, rPx + shadowSpread * 0.5f)
            )
            drawRoundRect(
                color = activePalette.shadowSpot,
                topLeft = Offset(0f, shadowOffsetY),
                size = Size(w, h),
                cornerRadius = CornerRadius(rPx, rPx)
            )
        }

        // Base body fill
        drawRoundRect(
            brush = bodyBrush,
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = CornerRadius(rPx, rPx)
        )

        // Clipped internal optical highlights (caustic + crown)
        clipPath(shapePath) {
            if (activePalette.causticGlow.alpha > 0f) {
                drawRect(
                    brush = causticBrush,
                    topLeft = Offset(0f, h * 0.35f),
                    size = Size(w, h * 0.65f)
                )
            }

            if (w > crownInset * 2f) {
                drawRoundRect(
                    brush = crownBrush,
                    topLeft = Offset(crownInset, crownInset),
                    size = Size(w - crownInset * 2f, crownHeight),
                    cornerRadius = CornerRadius(crownCorner, crownCorner)
                )
            }
        }

        // Inset inner specular rim
        if (innerRectW > 0f && innerRectH > 0f) {
            drawRoundRect(
                brush = innerRimBrush,
                topLeft = Offset(innerRectLeft, innerRectTop),
                size = Size(innerRectW, innerRectH),
                cornerRadius = CornerRadius(innerRadius, innerRadius),
                style = Stroke(width = innerRimPx)
            )
        }

        // Meniscus outer rim
        drawRoundRect(
            brush = outerRimBrush,
            topLeft = Offset(outerRimPx * 0.5f, outerRimPx * 0.5f),
            size = Size((w - outerRimPx).coerceAtLeast(0f), (h - outerRimPx).coerceAtLeast(0f)),
            cornerRadius = CornerRadius((rPx - outerRimPx * 0.5f).coerceAtLeast(0f)),
            style = Stroke(width = outerRimPx)
        )
    }
}

/**
 * Interactive 3D Liquid Glass button surface.
 * Touch hitbounds remain strictly fixed on the outer container while tactile scale
 * and vertical depression animate exclusively on the inner visual layer.
 */
@Composable
fun LiquidGlassButtonSurface(
    onClick: () -> Unit,
    palette: LiquidGlassPalette,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = LiquidGlassMetrics.standardCornerRadius,
    pressedScale: Float = LiquidGlassMotion.pressedScale,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClickLabel: String? = null,
    content: @Composable RowScope.() -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = LiquidGlassMotion.pressDampingRatio,
            stiffness = LiquidGlassMotion.pressStiffness
        ),
        label = "LiquidGlassScale"
    )
    val translateYFactor by animateFloatAsState(
        targetValue = if (enabled && isPressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = LiquidGlassMotion.pressDampingRatio,
            stiffness = LiquidGlassMotion.pressStiffness
        ),
        label = "LiquidGlassTranslateY"
    )

    val activeContentColor = if (enabled) palette.contentColor else DisabledGlassPalette.contentColor

    // Outer Box preserves fixed, unscaled hitbounds so touch release never slips off target.
    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        propagateMinConstraints = true
    ) {
        // Inner visual layer applies tactile compression and optical glass rendering.
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .defaultMinSize(minHeight = LiquidGlassMetrics.buttonMinHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = translateYFactor * LiquidGlassMotion.pressedTranslateY.toPx()
                }
                .drawLiquidGlass(
                    palette = palette,
                    cornerRadius = cornerRadius,
                    isPressed = isPressed,
                    enabled = enabled
                )
                .padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalContentColor provides activeContentColor) {
                ProvideTextStyle(value = MaterialTheme.typography.button.copy(color = activeContentColor)) {
                    content()
                }
            }
        }
    }
}
