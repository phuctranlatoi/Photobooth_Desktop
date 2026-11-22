package com.phuctran.photobooth.desktop.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Color and optical refraction profile for a 3D Liquid Glass surface.
 */
data class LiquidGlassPalette(
    val bodyStops: List<Pair<Float, Color>>,
    val pressedBodyStops: List<Pair<Float, Color>>,
    val crownHighlightTop: Color,
    val crownHighlightBottom: Color,
    val causticGlow: Color,
    val rimTopLeft: Color,
    val rimMid: Color,
    val rimBottomRight: Color,
    val innerRimTop: Color,
    val innerRimBottom: Color,
    val shadowAmbient: Color,
    val shadowSpot: Color,
    val contentColor: Color
)

/**
 * Primary warm terracotta/nude optical lens glass.
 * High-contrast pure white typography for clarity.
 */
val PrimaryGlassPalette = LiquidGlassPalette(
    bodyStops = listOf(
        0.00f to Color(0xFFD98A6C),
        0.48f to Color(0xFFC27357),
        1.00f to Color(0xFFA3583E)
    ),
    pressedBodyStops = listOf(
        0.00f to Color(0xFFC87B5E),
        0.50f to Color(0xFFB3654A),
        1.00f to Color(0xFF944D34)
    ),
    crownHighlightTop = Color.White.copy(alpha = 0.42f),
    crownHighlightBottom = Color.White.copy(alpha = 0.03f),
    causticGlow = Color(0xFFFFD6C4).copy(alpha = 0.34f),
    rimTopLeft = Color.White.copy(alpha = 0.72f),
    rimMid = Color(0xFFF3C6B6).copy(alpha = 0.36f),
    rimBottomRight = Color(0xFF6E3522).copy(alpha = 0.55f),
    innerRimTop = Color.White.copy(alpha = 0.35f),
    innerRimBottom = Color(0xFFFFE5D9).copy(alpha = 0.14f),
    shadowAmbient = Color(0xFF7A3E28).copy(alpha = 0.18f),
    shadowSpot = Color(0xFF8C462D).copy(alpha = 0.28f),
    contentColor = Color.White
)

/**
 * Frosted pearl crystal secondary glass.
 * Crisp dark neutral text (#242424) for maximum legibility.
 */
val SecondaryGlassPalette = LiquidGlassPalette(
    bodyStops = listOf(
        0.00f to Color(0xFFFFFFFF).copy(alpha = 0.96f),
        0.55f to Color(0xFFF9F5F0).copy(alpha = 0.92f),
        1.00f to Color(0xFFEDE6DE).copy(alpha = 0.90f)
    ),
    pressedBodyStops = listOf(
        0.00f to Color(0xFFF7F2EC).copy(alpha = 0.95f),
        0.55f to Color(0xFFEEE8E0).copy(alpha = 0.92f),
        1.00f to Color(0xFFE3DBD1).copy(alpha = 0.92f)
    ),
    crownHighlightTop = Color.White.copy(alpha = 0.85f),
    crownHighlightBottom = Color.White.copy(alpha = 0.10f),
    causticGlow = Color(0xFFFFE8DA).copy(alpha = 0.40f),
    rimTopLeft = Color.White.copy(alpha = 0.95f),
    rimMid = Color(0xFFE2D9CE).copy(alpha = 0.75f),
    rimBottomRight = Color(0xFFC4B8AA).copy(alpha = 0.65f),
    innerRimTop = Color.White.copy(alpha = 0.80f),
    innerRimBottom = Color.White.copy(alpha = 0.35f),
    shadowAmbient = Color(0xFF3D352E).copy(alpha = 0.07f),
    shadowSpot = Color(0xFF3D352E).copy(alpha = 0.13f),
    contentColor = NeutralText
)

/**
 * Warm neutral stone glass for steppers, utility pills, and subtle controls.
 */
val NeutralGlassPalette = LiquidGlassPalette(
    bodyStops = listOf(
        0.00f to Color(0xFFFAF7F3).copy(alpha = 0.95f),
        0.52f to Color(0xFFF2ECE5).copy(alpha = 0.92f),
        1.00f to Color(0xFFE6DEC3).copy(alpha = 0.25f).compositeOver(Color(0xFFE7DFD6))
    ),
    pressedBodyStops = listOf(
        0.00f to Color(0xFFF0EAE3).copy(alpha = 0.95f),
        0.50f to Color(0xFFE7E0D8).copy(alpha = 0.94f),
        1.00f to Color(0xFFDDD4CA).copy(alpha = 0.94f)
    ),
    crownHighlightTop = Color.White.copy(alpha = 0.75f),
    crownHighlightBottom = Color.White.copy(alpha = 0.06f),
    causticGlow = Color.White.copy(alpha = 0.32f),
    rimTopLeft = Color.White.copy(alpha = 0.90f),
    rimMid = Color(0xFFDDD4CA).copy(alpha = 0.75f),
    rimBottomRight = Color(0xFFBDB2A6).copy(alpha = 0.62f),
    innerRimTop = Color.White.copy(alpha = 0.65f),
    innerRimBottom = Color.White.copy(alpha = 0.25f),
    shadowAmbient = Color(0xFF3D352E).copy(alpha = 0.06f),
    shadowSpot = Color(0xFF3D352E).copy(alpha = 0.11f),
    contentColor = NeutralText
)

/**
 * Ruby optical glass for destructive or alert actions.
 * High-contrast white text.
 */
val DangerGlassPalette = LiquidGlassPalette(
    bodyStops = listOf(
        0.00f to Color(0xFFE54848),
        0.50f to Color(0xFFD32F2F),
        1.00f to Color(0xFFB22020)
    ),
    pressedBodyStops = listOf(
        0.00f to Color(0xFFD13B3B),
        0.50f to Color(0xFFBF2626),
        1.00f to Color(0xFF9C1919)
    ),
    crownHighlightTop = Color.White.copy(alpha = 0.40f),
    crownHighlightBottom = Color.White.copy(alpha = 0.03f),
    causticGlow = Color(0xFFFFC7C7).copy(alpha = 0.30f),
    rimTopLeft = Color.White.copy(alpha = 0.68f),
    rimMid = Color(0xFFF5B5B5).copy(alpha = 0.35f),
    rimBottomRight = Color(0xFF6B1111).copy(alpha = 0.60f),
    innerRimTop = Color.White.copy(alpha = 0.34f),
    innerRimBottom = Color(0xFFFFD6D6).copy(alpha = 0.14f),
    shadowAmbient = Color(0xFF7F1D1D).copy(alpha = 0.18f),
    shadowSpot = Color(0xFF991B1B).copy(alpha = 0.28f),
    contentColor = Color.White
)

/**
 * Translucent dark-media crystal glass for floating over live camera or dark imagery.
 */
val CrystalOnMediaGlassPalette = LiquidGlassPalette(
    bodyStops = listOf(
        0.00f to Color.White.copy(alpha = 0.30f),
        0.50f to Color.White.copy(alpha = 0.18f),
        1.00f to Color(0xFFE8DFD8).copy(alpha = 0.14f)
    ),
    pressedBodyStops = listOf(
        0.00f to Color.White.copy(alpha = 0.38f),
        0.50f to Color.White.copy(alpha = 0.24f),
        1.00f to Color.White.copy(alpha = 0.18f)
    ),
    crownHighlightTop = Color.White.copy(alpha = 0.45f),
    crownHighlightBottom = Color.White.copy(alpha = 0.04f),
    causticGlow = Color(0xFFFFE5D6).copy(alpha = 0.24f),
    rimTopLeft = Color.White.copy(alpha = 0.72f),
    rimMid = Color.White.copy(alpha = 0.32f),
    rimBottomRight = Color.Black.copy(alpha = 0.35f),
    innerRimTop = Color.White.copy(alpha = 0.38f),
    innerRimBottom = Color.White.copy(alpha = 0.12f),
    shadowAmbient = Color.Black.copy(alpha = 0.22f),
    shadowSpot = Color.Black.copy(alpha = 0.34f),
    contentColor = Color.White
)

/**
 * Muted matte frosted glass for disabled states.
 */
val DisabledGlassPalette = LiquidGlassPalette(
    bodyStops = listOf(
        0.00f to Color(0xFFEDE8E2).copy(alpha = 0.85f),
        0.50f to Color(0xFFE6E0D9).copy(alpha = 0.85f),
        1.00f to Color(0xFFDFD8D0).copy(alpha = 0.85f)
    ),
    pressedBodyStops = listOf(
        0.00f to Color(0xFFEDE8E2).copy(alpha = 0.85f),
        0.50f to Color(0xFFE6E0D9).copy(alpha = 0.85f),
        1.00f to Color(0xFFDFD8D0).copy(alpha = 0.85f)
    ),
    crownHighlightTop = Color.White.copy(alpha = 0.32f),
    crownHighlightBottom = Color.White.copy(alpha = 0.02f),
    causticGlow = Color.Transparent,
    rimTopLeft = Color.White.copy(alpha = 0.45f),
    rimMid = Color(0xFFD8D0C8).copy(alpha = 0.55f),
    rimBottomRight = Color(0xFFC8BFB6).copy(alpha = 0.45f),
    innerRimTop = Color.White.copy(alpha = 0.22f),
    innerRimBottom = Color.Transparent,
    shadowAmbient = Color.Transparent,
    shadowSpot = Color.Transparent,
    contentColor = NeutralMuted
)

/**
 * Geometric & optical metrics for Liquid Glass controls.
 */
object LiquidGlassMetrics {
    val standardCornerRadius: Dp = 18.dp
    val compactCornerRadius: Dp = 12.dp
    val floatingDockCornerRadius: Dp = 26.dp
    val pillCornerRadius: Dp = 999.dp

    val buttonMinHeight: Dp = KioskControlTokens.buttonMinHeight
    val compactButtonMinHeight: Dp = 42.dp
    val iconButtonSize: Dp = KioskControlTokens.buttonMinHeight
    val stepperButtonSize: Dp = 72.dp

    val outerRimWidth: Dp = 1.35.dp
    val innerRimInset: Dp = 1.6.dp
    val innerRimWidth: Dp = 1.dp

    val shadowRadiusRest: Dp = 10.dp
    val shadowRadiusPressed: Dp = 4.dp
    val shadowOffsetYRest: Dp = 4.dp
    val shadowOffsetYPressed: Dp = 1.5.dp

    const val crownHeightFraction: Float = 0.36f
}

/**
 * Tactile motion tokens for physical lens compression on touch.
 */
object LiquidGlassMotion {
    const val pressedScale: Float = 0.972f
    const val iconPressedScale: Float = 0.94f
    const val shutterPressedScale: Float = 0.90f
    val pressedTranslateY: Dp = 1.5.dp
    const val pressStiffness: Float = 650f
    const val pressDampingRatio: Float = 0.72f
}

/**
 * Optical lens tokens for the 108dp Camera Shutter button.
 */
object ShutterGlassTokens {
    val totalHitSize: Dp = 108.dp
    val outerBezelDiameter: Dp = 104.dp
    val innerPlungerDiameter: Dp = 82.dp
    val outerRingStroke: Dp = 2.5.dp
    val innerAccentStroke: Dp = 2.dp

    val outerHousingTop = Color.White.copy(alpha = 0.28f)
    val outerHousingBottom = Color(0xFFDCC7BC).copy(alpha = 0.14f)
    val outerRimTopLeft = Color.White.copy(alpha = 0.90f)
    val outerRimBottomRight = Color(0xFFC27357).copy(alpha = 0.55f)

    val plungerTop = Color(0xFFFFFFFF)
    val plungerMid = Color(0xFFFAF5F0)
    val plungerBottom = Color(0xFFEADFD6)
    val plungerCaustic = Color(0xFFFFDFCF).copy(alpha = 0.65f)
    val plungerAccentRim = AccentNude.copy(alpha = 0.88f)
}

private fun Color.compositeOver(background: Color): Color {
    val fgA = this.alpha
    val bgA = background.alpha * (1f - fgA)
    val outA = fgA + bgA
    if (outA <= 0f) return Color.Transparent
    return Color(
        red = (this.red * fgA + background.red * bgA) / outA,
        green = (this.green * fgA + background.green * bgA) / outA,
        blue = (this.blue * fgA + background.blue * bgA) / outA,
        alpha = outA
    )
}
