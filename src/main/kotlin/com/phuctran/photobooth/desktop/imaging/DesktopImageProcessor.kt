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

    // Precompute Look-Up Tables (LUTs) for cinematic curves
    private val normalLut = IntArray(256) { i ->
        val x = i / 255f
        val curve = (x * x * (3 - 2 * x)) // Smoothstep S-curve
        // Blend 70% linear, 30% curve for a subtle pop
        (i * 0.7f + curve * 255 * 0.3f).toInt().coerceIn(0, 255)
    }

    private val bwLut = IntArray(256) { i ->
        val x = i / 255f
        // Stronger S-curve for deep blacks and crisp whites
        val curve = if (x < 0.5f) 2 * x * x else 1 - 2 * (1 - x) * (1 - x)
        // Lift shadows slightly to 10 for a classic film look
        (10 + curve * 245).toInt().coerceIn(0, 255)
    }

    private val vintageLut = IntArray(256) { i ->
        val x = i / 255f
        val curve = (x * x * (3 - 2 * x))
        // Fade shadows strongly (lift to 35), compress highlights to 245
        (35 + curve * 210).toInt().coerceIn(0, 255)
    }

    private fun applyEffect(image: BufferedImage, effectId: String): BufferedImage {
        val width = image.width
        val height = image.height
        val srcPixels = image.getRGB(0, 0, width, height, null, 0, width)
        val destPixels = IntArray(width * height)

        val cx = width / 2f
        val cy = height / 2f
        val maxDistSq = cx * cx + cy * cy

        when (effectId) {
            "black_white" -> {
                for (y in 0 until height) {
                    val dySq = (y - cy) * (y - cy)
                    val rowOffset = y * width
                    for (x in 0 until width) {
                        val distSq = (x - cx) * (x - cx) + dySq
                        val vignette = 1.0f - (distSq / maxDistSq) * 0.4f // 40% corner darkening
                        
                        val p = srcPixels[rowOffset + x]
                        val r = (p shr 16) and 0xff
                        val g = (p shr 8) and 0xff
                        val b = p and 0xff
                        
                        // Portrait B&W: favor red/green for bright skin tones
                        val luma = (0.4f * r + 0.5f * g + 0.1f * b) * vignette
                        val l = bwLut[luma.toInt().coerceIn(0, 255)]
                        destPixels[rowOffset + x] = (0xFF shl 24) or (l shl 16) or (l shl 8) or l
                    }
                }
            }
            "vintage" -> {
                for (y in 0 until height) {
                    val dySq = (y - cy) * (y - cy)
                    val rowOffset = y * width
                    for (x in 0 until width) {
                        val distSq = (x - cx) * (x - cx) + dySq
                        val vignette = 1.0f - (distSq / maxDistSq) * 0.4f
                        
                        val p = srcPixels[rowOffset + x]
                        val r = (p shr 16) and 0xff
                        val g = (p shr 8) and 0xff
                        val b = p and 0xff
                        
                        val luma = 0.299f * r + 0.587f * g + 0.114f * b
                        // Warm sepia target
                        val tr = luma * 1.2f
                        val tg = luma * 1.05f
                        val tb = luma * 0.8f

                        // Blend 40% sepia, 60% original, then apply vignette
                        val br = (r * 0.6f + tr * 0.4f) * vignette
                        val bg = (g * 0.6f + tg * 0.4f) * vignette
                        val bb = (b * 0.6f + tb * 0.4f) * vignette

                        // Apply vintage fade curve
                        val fR = vintageLut[br.toInt().coerceIn(0, 255)]
                        val fG = vintageLut[bg.toInt().coerceIn(0, 255)]
                        val fB = vintageLut[bb.toInt().coerceIn(0, 255)]

                        destPixels[rowOffset + x] = (0xFF shl 24) or (fR shl 16) or (fG shl 8) or fB
                    }
                }
            }
            "normal" -> {
                for (y in 0 until height) {
                    val rowOffset = y * width
                    for (x in 0 until width) {
                        val p = srcPixels[rowOffset + x]
                        val r = (p shr 16) and 0xff
                        val g = (p shr 8) and 0xff
                        val b = p and 0xff
                        
                        val luma = 0.299f * r + 0.587f * g + 0.114f * b
                        // 15% Saturation boost
                        val sr = (luma + (r - luma) * 1.15f).toInt().coerceIn(0, 255)
                        val sg = (luma + (g - luma) * 1.15f).toInt().coerceIn(0, 255)
                        val sb = (luma + (b - luma) * 1.15f).toInt().coerceIn(0, 255)

                        // Contrast curve
                        val fR = normalLut[sr]
                        val fG = normalLut[sg]
                        val fB = normalLut[sb]

                        destPixels[rowOffset + x] = (0xFF shl 24) or (fR shl 16) or (fG shl 8) or fB
                    }
                }
            }
            else -> {
                System.arraycopy(srcPixels, 0, destPixels, 0, srcPixels.size)
            }
        }
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        result.setRGB(0, 0, width, height, destPixels, 0, width)
        return result
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
