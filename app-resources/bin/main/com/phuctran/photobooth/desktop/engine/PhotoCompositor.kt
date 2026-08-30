package com.phuctran.photobooth.desktop.engine

import java.awt.AlphaComposite
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.max

class PhotoCompositor {

    fun renderComposition(
        frameImage: BufferedImage,
        targetWidth: Int,
        targetHeight: Int,
        slots: List<FrameSlot>,
        photos: List<BufferedImage>
    ): BufferedImage {
        val finalImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
        val ctx = finalImage.createGraphics()

        // 1. Setup high quality rendering
        ctx.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        ctx.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        ctx.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // 2. Render each slot
        for (slot in slots) {
            val photoIndex = slot.index
            if (photoIndex >= photos.size) continue
            val photo = photos[photoIndex]

            // Calculate denormalized bounds
            val rectX = (slot.x * targetWidth).toInt()
            val rectY = (slot.y * targetHeight).toInt()
            val rectW = (slot.width * targetWidth).toInt()
            val rectH = (slot.height * targetHeight).toInt()

            // Compute cover transform
            val scaleW = rectW.toFloat() / photo.width
            val scaleH = rectH.toFloat() / photo.height
            val coverScale = max(scaleW, scaleH)

            val drawW = (photo.width * coverScale).toInt()
            val drawH = (photo.height * coverScale).toInt()
            val drawX = rectX + (rectW - drawW) / 2
            val drawY = rectY + (rectH - drawH) / 2

            // Apply clip and draw photo
            if (slot.shape == "RECT") {
                val oldClip = ctx.clip
                ctx.setClip(rectX, rectY, rectW, rectH)
                ctx.drawImage(photo, drawX, drawY, drawW, drawH, null)
                ctx.clip = oldClip
            } else {
                // For non-RECT, draw normally if we assume clipping is handled by alpha mask frame
                // Since frame overlay MUST be last, the frame's solid parts will hide the overflow
                // But clip prevents bleeding into other slots!
                val oldClip = ctx.clip
                ctx.setClip(rectX, rectY, rectW, rectH)
                ctx.drawImage(photo, drawX, drawY, drawW, drawH, null)
                ctx.clip = oldClip
            }
        }

        // 3. Draw frame PNG LAST, full canvas
        ctx.drawImage(frameImage, 0, 0, targetWidth, targetHeight, null)

        ctx.dispose()
        return finalImage
    }
}
