package com.phuctran.photobooth.desktop.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.model.*
import com.phuctran.photobooth.desktop.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PreviewPaperShape = RoundedCornerShape(4.dp)
private val EmptyPhotoSlotShape = RoundedCornerShape(0.dp)

private val PreviewPaperEdge = Color(0x18000000)
private val PreviewPaperSpotShadow = Color(0x2E1E1A18)
private val PreviewPaperAmbientShadow = Color(0x1A1E1A18)
private val EmptyPhotoSlotFill = Color(0xFFFAF8F5)

@Composable
private fun PreviewPhotoSlot(
    moment: CapturedMoment?,
    modifier: Modifier
) {
    val bitmap = rememberImageBitmap(moment?.photoPath)

    Box(modifier = modifier) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EmptyPhotoSlotFill)
                    .border(
                        width = 0.75.dp,
                        color = NeutralBorder.copy(alpha = 0.45f),
                        shape = EmptyPhotoSlotShape
                    )
            )
        }
    }
}

@Composable
fun PrintPreview(
    layout: LayoutMode,
    moments: List<CapturedMoment>,
    frame: FramePack,
    modifier: Modifier = Modifier
) {
    val accent = Color(frame.accentColor)
    val isStrip = layout.printSizeLabel.contains("5x15", ignoreCase = true) || layout.printSizeLabel.contains("5 x 15", ignoreCase = true)
    val is15x10 = !isStrip && (
        layout.printSizeLabel.contains("15x10", ignoreCase = true) ||
            layout.printSizeLabel.contains("15 x 10", ignoreCase = true)
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            modifier = (if (is15x10) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(layout.printAspectRatio, matchHeightConstraintsFirst = false)
            } else {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(layout.printAspectRatio, matchHeightConstraintsFirst = false)
            })
                .shadow(
                    elevation = 14.dp,
                    shape = PreviewPaperShape,
                    clip = false,
                    spotColor = PreviewPaperSpotShadow,
                    ambientColor = PreviewPaperAmbientShadow
                )
                .background(
                    color = Color.White,
                    shape = PreviewPaperShape
                )
                .border(
                    width = 0.75.dp,
                    color = PreviewPaperEdge,
                    shape = PreviewPaperShape
                )
        ) {
            val columns = layout.gridColumns.coerceAtLeast(1)
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

                    PreviewPhotoSlot(
                        moment = moment,
                        modifier = Modifier
                            .offset(x, y)
                            .width(slotW)
                            .height(slotH)
                            .clip(EmptyPhotoSlotShape)
                    )
                }
            } else {
                repeat(layout.selectCount) { index ->
                    val row = index / columns
                    val column = index % columns
                    val x = left + (slotWidth + gapX) * column.toFloat()
                    val y = top + (slotHeight + gapY) * row.toFloat()
                    val moment = moments.getOrNull(index)

                    PreviewPhotoSlot(
                        moment = moment,
                        modifier = Modifier
                            .offset(x, y)
                            .width(slotWidth)
                            .height(slotHeight)
                            .clip(EmptyPhotoSlotShape)
                    )
                }
            }

            if (frame.isCustom && frame.customImagePath != null) {
                val framePath = frame.customImagePath
                val overlay by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, framePath) {
                    // The on-screen preview only needs a thumbnail. The compositor still reads the
                    // original PNG for printing; the shared cache keeps decoded previews bounded.
                    value = withContext(Dispatchers.IO) { loadFrameThumbnail(framePath) }
                }
                val overlayBitmap = overlay
                if (overlayBitmap != null) {
                    Image(
                        bitmap = overlayBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        filterQuality = FilterQuality.High
                    )
                }
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(2.dp, accent.copy(alpha = 0.7f), PreviewPaperShape)
                )
            }
        }
    }
}

@Composable
fun MiniLayoutPreview(layout: LayoutMode, modifier: Modifier) {
    val accent = Color(layout.accentColor)
    val isStrip = layout.printSizeLabel.contains("5x15", ignoreCase = true) || layout.printSizeLabel.contains("5 x 15", ignoreCase = true)
    val is15x10 = !isStrip && (
        layout.printSizeLabel.contains("15x10", ignoreCase = true) ||
            layout.printSizeLabel.contains("15 x 10", ignoreCase = true)
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            (if (is15x10) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(layout.printAspectRatio, matchHeightConstraintsFirst = false)
            } else {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(layout.printAspectRatio, matchHeightConstraintsFirst = false)
            })
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
