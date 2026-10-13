package com.phuctran.photobooth.desktop.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

val NeutralBg = Color(0xFFFBF9F6)
val NeutralPanel = Color.White
val NeutralPanelAlt = Color(0xFFF4EFEA)
val NeutralText = Color(0xFF242424)
val NeutralSecondary = Color(0xFF5C5854)
val NeutralMuted = Color(0xFF8C8680)
val NeutralBorder = Color(0xFFE8E2DC)

val AccentNude = Color(0xFFC27357)
val AccentNudeDark = Color(0xFFA65D43)
val AccentNudeLight = Color(0xFFFCEEE8)
val AccentMint = Color(0xFF2E8B57)
val AccentBlue = Color(0xFF3B82F6)
val AccentAmber = Color(0xFFD97706)
val AccentRed = Color(0xFFD32F2F)
val CameraBlack = Color(0xFF050507)
val InkSoft = Color(0xFF252228)
val NeutralSurface = Color(0xFFFAF8F6)
val AdminGroupLabelColor = Color(0xFF7A6257)

val KioskColors = androidx.compose.material.lightColors(
    primary = AccentNude,
    primaryVariant = AccentNudeDark,
    secondary = AccentNude,
    secondaryVariant = AccentNudeLight,
    background = NeutralBg,
    surface = NeutralPanel,
    error = AccentRed,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = NeutralText,
    onSurface = NeutralText,
    onError = Color.White
)

private val KioskTypography = Typography(
    defaultFontFamily = FontFamily.SansSerif,
    h1 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = 0.sp),
    h2 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = 0.sp),
    h3 = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, letterSpacing = 0.sp),
    h4 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = 0.sp),
    h5 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 0.sp),
    h6 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 0.sp),
    subtitle1 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.sp),
    subtitle2 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp),
    body1 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, letterSpacing = 0.sp),
    body2 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, letterSpacing = 0.sp),
    button = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.sp),
    caption = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.sp),
    overline = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.sp)
)

@Composable
fun PhotoboothTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = KioskColors,
        typography = KioskTypography,
        content = content
    )
}
