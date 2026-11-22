package com.phuctran.photobooth.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.ui.theme.*
import kotlin.math.min

/**
 * Draws a 3D Liquid Glass content plate ("plate để đồ") with zero per-frame allocations using [drawWithCache].
 *
 * Render pipeline:
 * 1. External soft shadow (scaled by [elevation]; 0.dp for nested rows)
 * 2. Translucent body gradient
 * 3. Crown reflection (top 28%) and lower caustic glow
 * 4. `drawContent()`
 * 5. Inset inner specular reflection and outer meniscus rim (drawn over or around content edge)
 *
 * Supports an optional [visualOutset] to paint an optical glass lip around the target without
 * changing measured child constraints or layout geometry.
 */
fun Modifier.liquidGlassPlate(
    variant: LiquidGlassPlateVariant = LiquidGlassPlateVariant.Standard,
    cornerRadius: Dp = LiquidGlassPlateMetrics.panelCornerRadius,
    elevation: Dp = when (variant) {
        LiquidGlassPlateVariant.Elevated -> LiquidGlassPlateMetrics.elevatedElevation
        LiquidGlassPlateVariant.Selected -> LiquidGlassPlateMetrics.defaultElevation
        LiquidGlassPlateVariant.Standard -> LiquidGlassPlateMetrics.defaultElevation
        LiquidGlassPlateVariant.Media -> LiquidGlassPlateMetrics.defaultElevation
    },
    rimWidth: Dp = if (variant == LiquidGlassPlateVariant.Selected) {
        LiquidGlassPlateMetrics.selectedRimWidth
    } else {
        LiquidGlassPlateMetrics.outerRimWidth
    },
    visualOutset: Dp = 0.dp,
    plateAlpha: Float = 1f
): Modifier = this.liquidGlassPlate(
    palette = variant.toPalette(),
    cornerRadius = cornerRadius,
    elevation = elevation,
    rimWidth = rimWidth,
    visualOutset = visualOutset,
    plateAlpha = plateAlpha
)

/**
 * Palette-driven overload for custom or interpolated optical plates.
 */
fun Modifier.liquidGlassPlate(
    palette: LiquidGlassPlatePalette,
    cornerRadius: Dp = LiquidGlassPlateMetrics.panelCornerRadius,
    elevation: Dp = LiquidGlassPlateMetrics.defaultElevation,
    rimWidth: Dp = LiquidGlassPlateMetrics.outerRimWidth,
    visualOutset: Dp = 0.dp,
    plateAlpha: Float = 1f
): Modifier = this.drawWithCache {
    val alphaFactor = plateAlpha.coerceIn(0f, 1f)
    val outsetPx = visualOutset.toPx()
    val left = -outsetPx
    val top = -outsetPx
    val w = (size.width + outsetPx * 2f).coerceAtLeast(1f)
    val h = (size.height + outsetPx * 2f).coerceAtLeast(1f)

    val maxRadius = min(w, h) / 2f
    val rPx = min((cornerRadius + visualOutset).toPx(), maxRadius)

    val outerRimPx = rimWidth.toPx()
    val innerInsetPx = LiquidGlassPlateMetrics.innerRimInset.toPx()
    val innerRimPx = LiquidGlassPlateMetrics.innerRimWidth.toPx()

    // 1. External soft shadow geometry
    val elevPx = elevation.toPx()
    val hasShadow = elevPx > 0.5f && alphaFactor > 0.05f && palette.shadowSpot.alpha > 0f
    val shadowOffsetY = (elevPx * 0.34f).coerceAtLeast(1f)
    val shadowSpread = (elevPx * 0.42f).coerceAtLeast(1f)
    val ambientShadowColor = palette.shadowAmbient.copy(alpha = palette.shadowAmbient.alpha * alphaFactor)
    val spotShadowColor = palette.shadowSpot.copy(alpha = palette.shadowSpot.alpha * alphaFactor)

    // 2. Translucent body gradient
    val bodyStops = palette.bodyStops.map { (stop, color) ->
        stop to color.copy(alpha = color.alpha * alphaFactor)
    }.toTypedArray()
    val bodyBrush = Brush.verticalGradient(
        colorStops = bodyStops,
        startY = top,
        endY = top + h
    )

    val shapePath = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(left, top, left + w, top + h),
                cornerRadius = CornerRadius(rPx, rPx)
            )
        )
    }

    // 3. Crown reflection (top 28%) & lower caustic glow
    val crownHeight = (h * LiquidGlassPlateMetrics.crownHeightFraction).coerceAtLeast(1f)
    val crownInset = outerRimPx * 0.85f
    val crownCorner = (rPx - crownInset).coerceAtLeast(0f)
    val crownBrush = Brush.verticalGradient(
        colors = listOf(
            palette.crownHighlightTop.copy(alpha = palette.crownHighlightTop.alpha * alphaFactor),
            palette.crownHighlightBottom.copy(alpha = palette.crownHighlightBottom.alpha * alphaFactor)
        ),
        startY = top + crownInset,
        endY = top + crownHeight
    )

    val causticColor = palette.causticGlow.copy(alpha = palette.causticGlow.alpha * alphaFactor)
    val causticBrush = Brush.radialGradient(
        colors = listOf(causticColor, Color.Transparent),
        center = Offset(left + w * 0.5f, top + h * 0.90f),
        radius = (min(w, h) * 0.82f).coerceAtLeast(1f)
    )

    // 5. Inset reflection & meniscus rim
    val innerRectLeft = left + innerInsetPx
    val innerRectTop = top + innerInsetPx
    val innerRectW = (w - innerInsetPx * 2f).coerceAtLeast(0f)
    val innerRectH = (h - innerInsetPx * 2f).coerceAtLeast(0f)
    val innerRadius = (rPx - innerInsetPx).coerceAtLeast(0f)
    val innerRimBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to palette.innerRimTop.copy(alpha = palette.innerRimTop.alpha * alphaFactor),
            0.48f to Color.Transparent,
            1.00f to palette.innerRimBottom.copy(alpha = palette.innerRimBottom.alpha * alphaFactor)
        ),
        startY = innerRectTop,
        endY = innerRectTop + innerRectH
    )

    val outerRimBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to palette.rimTopLeft.copy(alpha = palette.rimTopLeft.alpha * alphaFactor),
            0.52f to palette.rimMid.copy(alpha = palette.rimMid.alpha * alphaFactor),
            1.00f to palette.rimBottomRight.copy(alpha = palette.rimBottomRight.alpha * alphaFactor)
        ),
        start = Offset(left, top),
        end = Offset(left + w, top + h)
    )

    onDrawWithContent {
        // 1. External soft shadow
        if (hasShadow) {
            drawRoundRect(
                color = ambientShadowColor,
                topLeft = Offset(left - shadowSpread * 0.45f, top + shadowOffsetY * 0.65f),
                size = Size(w + shadowSpread * 0.9f, h + shadowSpread * 0.75f),
                cornerRadius = CornerRadius(rPx + shadowSpread * 0.5f, rPx + shadowSpread * 0.5f)
            )
            drawRoundRect(
                color = spotShadowColor,
                topLeft = Offset(left, top + shadowOffsetY),
                size = Size(w, h),
                cornerRadius = CornerRadius(rPx, rPx)
            )
        }

        // 2. Translucent body gradient
        drawRoundRect(
            brush = bodyBrush,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = CornerRadius(rPx, rPx)
        )

        // 3. Crown reflection (top 28%) & lower caustic
        clipPath(shapePath) {
            if (causticColor.alpha > 0f) {
                drawRect(
                    brush = causticBrush,
                    topLeft = Offset(left, top + h * 0.32f),
                    size = Size(w, h * 0.68f)
                )
            }
            if (w > crownInset * 2f) {
                drawRoundRect(
                    brush = crownBrush,
                    topLeft = Offset(left + crownInset, top + crownInset),
                    size = Size(w - crownInset * 2f, crownHeight),
                    cornerRadius = CornerRadius(crownCorner, crownCorner)
                )
            }
        }

        // 4. Child content
        drawContent()

        // 5. Inset inner reflection & meniscus rim
        if (innerRectW > 0f && innerRectH > 0f && innerRimPx > 0f) {
            drawRoundRect(
                brush = innerRimBrush,
                topLeft = Offset(innerRectLeft, innerRectTop),
                size = Size(innerRectW, innerRectH),
                cornerRadius = CornerRadius(innerRadius, innerRadius),
                style = Stroke(width = innerRimPx)
            )
        }

        if (outerRimPx > 0f) {
            drawRoundRect(
                brush = outerRimBrush,
                topLeft = Offset(left + outerRimPx * 0.5f, top + outerRimPx * 0.5f),
                size = Size((w - outerRimPx).coerceAtLeast(0f), (h - outerRimPx).coerceAtLeast(0f)),
                cornerRadius = CornerRadius((rPx - outerRimPx * 0.5f).coerceAtLeast(0f)),
                style = Stroke(width = outerRimPx)
            )
        }
    }
}

/**
 * Reusable 3D Liquid Glass content container composable.
 */
@Composable
fun LiquidGlassPlate(
    modifier: Modifier = Modifier,
    variant: LiquidGlassPlateVariant = LiquidGlassPlateVariant.Standard,
    cornerRadius: Dp = LiquidGlassPlateMetrics.panelCornerRadius,
    elevation: Dp = when (variant) {
        LiquidGlassPlateVariant.Elevated -> LiquidGlassPlateMetrics.elevatedElevation
        LiquidGlassPlateVariant.Selected -> LiquidGlassPlateMetrics.defaultElevation
        LiquidGlassPlateVariant.Standard -> LiquidGlassPlateMetrics.defaultElevation
        LiquidGlassPlateVariant.Media -> LiquidGlassPlateMetrics.defaultElevation
    },
    rimWidth: Dp = if (variant == LiquidGlassPlateVariant.Selected) {
        LiquidGlassPlateMetrics.selectedRimWidth
    } else {
        LiquidGlassPlateMetrics.outerRimWidth
    },
    visualOutset: Dp = 0.dp,
    plateAlpha: Float = 1f,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = variant.toPalette()
    Box(
        modifier = modifier
            .liquidGlassPlate(
                palette = palette,
                cornerRadius = cornerRadius,
                elevation = elevation,
                rimWidth = rimWidth,
                visualOutset = visualOutset,
                plateAlpha = plateAlpha
            )
            .padding(contentPadding),
        contentAlignment = contentAlignment
    ) {
        CompositionLocalProvider(LocalContentColor provides palette.contentColor) {
            content()
        }
    }
}
