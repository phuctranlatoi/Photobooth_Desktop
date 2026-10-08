package com.phuctran.photobooth.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.domain.SessionState
import com.phuctran.photobooth.desktop.ui.theme.NeutralBg
import com.phuctran.photobooth.desktop.ui.theme.NeutralBorder
import com.phuctran.photobooth.desktop.ui.theme.NeutralMuted
import com.phuctran.photobooth.desktop.ui.theme.NeutralText
import java.nio.file.Path

@Composable
@Suppress("UNUSED_PARAMETER") // Keep the existing shell call contract; operational indicators belong to local screens.
fun AppShell(
    state: SessionState,
    albumEnabled: Boolean = false,
    printerEnabled: Boolean = false,
    paymentConfigured: Boolean = false,
    inactivityTimeLeft: Int? = null,
    inactivityTimeout: Int = 90,
    content: @Composable BoxScope.() -> Unit
) {
    val isFullscreen = state == SessionState.LIVE_VIEW || state == SessionState.COUNTDOWN || state == SessionState.CAPTURING

    if (isFullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            content()
        }
    } else {
        Box(Modifier.fillMaxSize().background(NeutralBg).padding(24.dp)) {
            content()
        }
    }
}

@Composable
fun StatusBar(status: String, outputPath: Path?) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)).background(NeutralBg).border(1.dp, NeutralBorder, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(status, color = NeutralText, style = MaterialTheme.typography.body2, fontWeight = FontWeight.Bold)
        Text(outputPath?.toString() ?: "No output path", color = NeutralMuted, style = MaterialTheme.typography.caption)
    }
}
