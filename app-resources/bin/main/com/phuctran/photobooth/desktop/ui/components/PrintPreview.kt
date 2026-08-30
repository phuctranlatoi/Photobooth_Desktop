package com.phuctran.photobooth.desktop.ui.components

import androidx.compose.foundation.*
import kotlin.math.ceil
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.model.*
import com.phuctran.photobooth.desktop.ui.theme.*


@Composable
fun PrintPreview(
    layout: LayoutMode,
    moments: List<CapturedMoment>,
    frame: FramePack,
    modifier: Modifier = Modifier
) {
    val accent = Color(frame.accentColor)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(layout.printAspectRatio, matchHeightConstraintsFirst = false)
                .clip(RoundedCornerShape(8.dp))
                .background(NeutralPanel)
                .border(width = 1.dp, color = NeutralBorder, shape = RoundedCornerShape(8.dp))
        ) {
        val columns = layout.gridColumns.coerceAtLeast(1)
        val rows = ceil(layout.selectCount / columns.toFloat()).toInt().coerceAtLeast(1)
        val top = maxWidth * layout.paddingTopRatio
        val left = maxWidth * layout.paddingLeftRatio
        val right = maxWidth * layout.paddingRightRatio
        val gapX = maxWidth * layout.gapHorizontalRatio
        val gapY = maxWidth * layout.gapVerticalRatio
        val slotWidth = (maxWidth - left - right - gapX * (columns - 1).toFloat()) / columns.toFloat()
        val slotHeight = slotWidth / layout.photoAspectRatio

        if (layout.absoluteSlots.isNotEmpty()) {
            layout.absoluteSlots.forEach { slot ->
                val x = maxWidth * slot.x
                val y = maxHeight * slot.y
                val slotW = maxWidth * slot.width
                val slotH = maxHeight * slot.height
                val moment = moments.getOrNull(slot.index)
                val bitmap = remember(moment?.photoPath) { moment?.photoPath?.let(::loadImageBitmap) }
                
                // Increase bleed to 8.dp to ensure no background shows through
                val bleedX = 8.dp
                val bleedY = 8.dp
                Box(
                    modifier = Modifier
                        .offset(x - bleedX / 2, y - bleedY / 2)
                        .width(slotW + bleedX)
                        .height(slotH + bleedY)
                        .clip(RoundedCornerShape(0.dp))
                        .background(NeutralPanelAlt)
                        .border(1.dp, NeutralBorder.copy(alpha = 0.75f), RoundedCornerShape(0.dp))
                ) {
                    if (bitmap != null) {
                        Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Text(
                        text = moment?.photoLabel ?: "Ảnh ${slot.index + 1}",
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(NeutralPanel.copy(alpha = 0.9f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                        color = accent,
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            repeat(layout.selectCount) { index ->
                val row = index / columns
                val column = index % columns
                val x = left + (slotWidth + gapX) * column.toFloat()
                val y = top + (slotHeight + gapY) * row.toFloat()
                val moment = moments.getOrNull(index)
                val bitmap = remember(moment?.photoPath) { moment?.photoPath?.let(::loadImageBitmap) }
                val bleedX = 8.dp
                val bleedY = 8.dp
                Box(
                    modifier = Modifier
                        .offset(x - bleedX / 2, y - bleedY / 2)
                        .width(slotWidth + bleedX)
                        .height(slotHeight + bleedY)
                        .clip(RoundedCornerShape(0.dp))
                        .background(NeutralPanelAlt)
                        .border(1.dp, NeutralBorder.copy(alpha = 0.75f), RoundedCornerShape(0.dp))
                ) {
                    if (bitmap != null) {
                        Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Text(
                        text = moment?.photoLabel ?: "Ảnh ${index + 1}",
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(NeutralPanel.copy(alpha = 0.9f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                        color = accent,
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (frame.isCustom && frame.customImagePath != null) {
            val overlay by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, frame.customImagePath) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        org.jetbrains.skia.Image.makeFromEncoded(java.nio.file.Files.readAllBytes(frame.customImagePath!!)).toComposeImageBitmap()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
            }
            val overlayBitmap = overlay
            if (overlayBitmap != null) {
                Image(
                    bitmap = overlayBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                )
            }
        } else {
            Box(Modifier.fillMaxSize().border(5.dp, accent, RoundedCornerShape(8.dp)))
            val footerHeight = maxHeight - (top + slotHeight * rows.toFloat() + gapY * (rows - 1).toFloat())
            if (footerHeight > 38.dp) {
                Text(
                    text = frame.title.uppercase(),
                    modifier = Modifier.align(Alignment.BottomStart).padding(14.dp).background(NeutralPanel.copy(alpha = 0.88f), RoundedCornerShape(6.dp)).padding(horizontal = 9.dp, vertical = 5.dp),
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.caption
                )
            }
        }
        }
    }
}

@Composable
fun MiniLayoutPreview(layout: LayoutMode, modifier: Modifier) {
    val accent = Color(layout.accentColor)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier
                .fillMaxHeight()
                .aspectRatio(layout.printAspectRatio, matchHeightConstraintsFirst = false)
                .clip(RoundedCornerShape(5.dp))
                .background(NeutralPanel.copy(alpha = 0.9f))
                .border(width = 1.dp, color = NeutralBorder, shape = RoundedCornerShape(5.dp))
        ) {
        val columns = layout.gridColumns.coerceAtLeast(1)
        val top = maxWidth * layout.paddingTopRatio
        val left = maxWidth * layout.paddingLeftRatio
        val right = maxWidth * layout.paddingRightRatio
        val gapX = maxWidth * layout.gapHorizontalRatio
        val gapY = maxWidth * layout.gapVerticalRatio
        val slotWidth = (maxWidth - left - right - gapX * (columns - 1).toFloat()) / columns.toFloat()
        val slotHeight = slotWidth / layout.photoAspectRatio
        repeat(layout.selectCount) { index ->
            val row = index / columns
            val column = index % columns
            Box(
                Modifier.offset(left + (slotWidth + gapX) * column.toFloat(), top + (slotHeight + gapY) * row.toFloat())
                    .width(slotWidth)
                    .height(slotHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(NeutralBg)
                    .border(1.dp, NeutralMuted.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            )
        }
        }
    }
}
