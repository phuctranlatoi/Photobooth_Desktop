package com.phuctran.photobooth.desktop.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

val NeutralBg = Color(0xFFF4F6F8)
val NeutralPanel = Color.White
val NeutralPanelAlt = Color(0xFFEEF2F6)
val NeutralText = Color(0xFF17171F)
val NeutralSecondary = Color(0xFF596271)
val NeutralMuted = Color(0xFF858D99)
val NeutralBorder = Color(0xFFDCE2EA)

val AccentNude = Color(0xFFC9876A)
val AccentNudeDark = Color(0xFF9C5D46)
val AccentNudeLight = Color(0xFFFFF0E8)
val AccentMint = Color(0xFF0E9F86)
val AccentBlue = Color(0xFF2563EB)
val AccentAmber = Color(0xFFD97706)
val AccentRed = Color(0xFFDC2626)
val CameraBlack = Color(0xFF050507)
val InkSoft = Color(0xFF252228)

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
    h1 = TextStyle(fontWeight = FontWeight.Black, fontSize = 72.sp, letterSpacing = 0.sp),
    h2 = TextStyle(fontWeight = FontWeight.Black, fontSize = 48.sp, letterSpacing = 0.sp),
    h3 = TextStyle(fontWeight = FontWeight.Black, fontSize = 36.sp, letterSpacing = 0.sp),
    h4 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 0.sp),
    h5 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 0.sp),
    h6 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 0.sp),
    subtitle1 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.sp),
    subtitle2 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp),
    body1 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, letterSpacing = 0.sp),
    body2 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, letterSpacing = 0.sp),
    button = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.sp),
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
