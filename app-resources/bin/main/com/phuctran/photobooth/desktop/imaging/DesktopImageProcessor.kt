package com.phuctran.photobooth.desktop.imaging

import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt

class DesktopImageProcessor {
    fun saveBoothCapture(
        source: Path,
        outputDir: Path,
        shotIndex: Int,
        photoAspectRatio: Float,
        effectId: String = "normal"
    ): Path? {
        val original = ImageIO.read(source.toFile()) ?: return null
        return saveBoothCapture(original, outputDir, shotIndex, photoAspectRatio, effectId)
    }

    fun saveBoothCapture(
        image: BufferedImage,
        outputDir: Path,
        shotIndex: Int,
        photoAspectRatio: Float,
        effectId: String = "normal"
    ): Path? {
        Files.createDirectories(outputDir)
        val cropped = image.centerCropToAspect(photoAspectRatio)
        val filtered = applyEffect(cropped, effectId)
        val rgb = BufferedImage(filtered.width, filtered.height, BufferedImage.TYPE_INT_RGB)
        val graphics = rgb.createGraphics()
        try {
            graphics.configure()
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, rgb.width, rgb.height)
            graphics.drawImage(filtered, 0, 0, null)
        } finally {
            graphics.dispose()
        }

        val destination = outputDir.resolve("Photo_$shotIndex.jpg")
        ImageIO.write(rgb, "jpg", destination.toFile())
        return destination
    }

    fun applyEffectForLiveView(image: BufferedImage, effectId: String): BufferedImage {
        return applyEffect(image, effectId)
    }

    var availableEffects: List<com.phuctran.photobooth.desktop.model.EffectMode> = com.phuctran.photobooth.desktop.model.DefaultEffectModes

    private fun applyEffect(image: BufferedImage, effectId: String): BufferedImage {
        val effect = availableEffects.find { it.id == effectId } ?: availableEffects.firstOrNull()
        if (effect == null || (effect.saturation == 1.0f && effect.contrast == 1.0f && effect.brightness == 0.0f && effect.warmth == 0.0f && effect.tint == 0.0f)) {
            return image
        }

        val width = image.width
        val height = image.height
        val srcPixels = image.getRGB(0, 0, width, height, null, 0, width)
        val destPixels = IntArray(width * height)

        val sat = effect.saturation
        val con = effect.contrast
        val bri = effect.brightness
        val wrm = effect.warmth
        val tnt = effect.tint
        
        val cOffset = (1f - con) * 127.5f + (bri * 255f)
        val wOffsetR = wrm * 127.5f
        val wOffsetG = wrm * 25.5f
        val wOffsetB = -wrm * 127.5f
        val tOffsetR = tnt * 127.5f
        val tOffsetG = -tnt * 127.5f
        val tOffsetB = tnt * 127.5f

        for (i in 0 until srcPixels.size) {
            val p = srcPixels[i]
            val sr = (p shr 16) and 0xff
            val sg = (p shr 8) and 0xff
            val sb = p and 0xff

            // Contrast & Brightness
            var r = con * sr + cOffset
            var g = con * sg + cOffset
            var b = con * sb + cOffset

            // Saturation
            if (sat != 1.0f) {
                val luma = 0.299f * r + 0.587f * g + 0.114f * b
                r = luma + sat * (r - luma)
                g = luma + sat * (g - luma)
                b = luma + sat * (b - luma)
            }

            // Warmth & Tint
            r += wOffsetR + tOffsetR
            g += wOffsetG + tOffsetG
            b += wOffsetB + tOffsetB

            // Clamp and compose
            val fr = r.toInt().coerceIn(0, 255)
            val fg = g.toInt().coerceIn(0, 255)
            val fb = b.toInt().coerceIn(0, 255)

            destPixels[i] = (0xFF shl 24) or (fr shl 16) or (fg shl 8) or fb
        }

        val outImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        outImage.setRGB(0, 0, width, height, destPixels, 0, width)
        return outImage
    }

    fun ensureJpeg(source: Path, outputDir: Path, prefix: String): Path? {
        val extension = source.fileName.toString().substringAfterLast('.', "").lowercase()
        if (extension == "jpg" || extension == "jpeg") return source.takeIf { Files.isRegularFile(it) }

        val original = ImageIO.read(source.toFile()) ?: return null
        Files.createDirectories(outputDir)
        val rgb = BufferedImage(original.width, original.height, BufferedImage.TYPE_INT_RGB)
        val graphics = rgb.createGraphics()
        try {
            graphics.configure()
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, rgb.width, rgb.height)
            graphics.drawImage(original, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        val destination = outputDir.resolve("${prefix}_${System.currentTimeMillis()}.jpg")
        ImageIO.write(rgb, "jpg", destination.toFile())
        return destination
    }

    private fun BufferedImage.centerCropToAspect(targetAspectRatio: Float): BufferedImage {
        val sourceAspectRatio = width.toFloat() / height.toFloat()
        if (abs(sourceAspectRatio - targetAspectRatio) < 0.01f) return this

        val cropWidth: Int
        val cropHeight: Int
        if (sourceAspectRatio > targetAspectRatio) {
            cropHeight = height
            cropWidth = (height * targetAspectRatio).roundToInt().coerceAtMost(width)
        } else {
            cropWidth = width
            cropHeight = (width / targetAspectRatio).roundToInt().coerceAtMost(height)
        }

        val left = ((width - cropWidth) / 2).coerceAtLeast(0)
        val top = ((height - cropHeight) / 2).coerceAtLeast(0)
        return getSubimage(left, top, cropWidth.coerceAtLeast(1), cropHeight.coerceAtLeast(1))
    }
}

private fun Graphics2D.configure() {
    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
}
