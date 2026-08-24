package com.phuctran.photobooth.desktop.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

val NeutralBg = Color(0xFFF7F8FA)
val NeutralPanel = Color.White
val NeutralText = Color(0xFF1A1A24)
val NeutralMuted = Color(0xFFA1A5AB)
val NeutralBorder = Color(0xFFE5E7EB)

val AccentNude = Color(0xFFDAB39A)
val AccentNudeLight = Color(0xFFFAF5F0)

val KioskColors = androidx.compose.material.lightColors(
    primary = AccentNude,
    primaryVariant = AccentNudeLight,
    secondary = AccentNude,
    secondaryVariant = AccentNudeLight,
    background = NeutralBg,
    surface = NeutralPanel,
    error = Color(0xFFE57373),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = NeutralText,
    onSurface = NeutralText,
    onError = Color.White
)

@Composable
fun PhotoboothTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = KioskColors,
        typography = Typography(defaultFontFamily = FontFamily.SansSerif),
        content = content
    )
}
