package com.phuctran.photobooth.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.domain.SessionState
import com.phuctran.photobooth.desktop.ui.theme.NeutralBg
import com.phuctran.photobooth.desktop.ui.theme.NeutralBorder
import com.phuctran.photobooth.desktop.ui.theme.NeutralMuted
import com.phuctran.photobooth.desktop.ui.theme.NeutralText
import com.phuctran.photobooth.desktop.ui.theme.AccentNude
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
    } else if (state == SessionState.ADMIN) {
        Box(Modifier.fillMaxSize().background(NeutralBg).padding(24.dp)) { content() }
    } else {
        Box(Modifier.fillMaxSize().background(NeutralBg)) {
            // Draw once behind the content, rather than layering blurred composables over media.
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFFEDE4).copy(alpha = 0.24f), Color.Transparent),
                        center = Offset.Zero,
                        radius = size.maxDimension * 0.78f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFE9EFF5).copy(alpha = 0.32f), Color.Transparent),
                        center = Offset(size.width, size.height),
                        radius = size.maxDimension * 0.78f
                    )
                )
            }
            Box(Modifier.fillMaxSize().padding(24.dp)) { content() }
        }
    }
}

/** Guest-facing, screen-local navigation; never displayed over the full-screen camera. */
@Composable
fun CompactLocalHeader(step: String, title: String, onBack: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (onBack != null) KioskBackButton(onClick = onBack)
        Column {
            Text(step, style = MaterialTheme.typography.caption, color = AccentNude)
            Text(title, style = MaterialTheme.typography.h4, color = NeutralText, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
