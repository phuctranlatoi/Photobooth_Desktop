package com.phuctran.photobooth.desktop.beauty

import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.roundToInt

object PixelSafeCompositor {
    fun copy(image: BufferedImage): BufferedImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB).also {
        it.setRGB(0, 0, image.width, image.height, image.getRGB(0, 0, image.width, image.height, null, 0, image.width), 0, image.width)
    }
    fun blend(original: Int, enhanced: Int, alpha: Float): Int {
        if (alpha <= 0f) return original
        val a = alpha.coerceIn(0f, 1f)
        var result = original and -0x1000000
        for (shift in listOf(0, 8, 16)) {
            val before = original shr shift and 255
            val after = enhanced shr shift and 255
            result = result or ((before + (after - before) * a).roundToInt().coerceIn(0, 255) shl shift)
        }
        return result
    }
    fun outsideMaskMaxDiff(original: BufferedImage, result: BufferedImage, mask: FloatArray): Int {
        require(original.width == result.width && original.height == result.height && mask.size == original.width * original.height)
        val before = original.getRGB(0, 0, original.width, original.height, null, 0, original.width)
        val after = result.getRGB(0, 0, result.width, result.height, null, 0, result.width)
        var maxDiff = 0
        for (i in before.indices) {
            if (mask[i] == 0f) {
                val dr = abs((before[i] shr 16 and 255) - (after[i] shr 16 and 255))
                val dg = abs((before[i] shr 8 and 255) - (after[i] shr 8 and 255))
                val db = abs((before[i] and 255) - (after[i] and 255))
                val diff = maxOf(dr, dg, db)
                if (diff > maxDiff) maxDiff = diff
            }
        }
        return maxDiff
    }
    fun absoluteDifference(original: BufferedImage, result: BufferedImage, multiplier: Int = 4): BufferedImage = difference(original, result, null, multiplier)
    fun outsideMaskDifference(original: BufferedImage, result: BufferedImage, mask: FloatArray, multiplier: Int = 10): BufferedImage = difference(original, result, mask, multiplier)
    private fun difference(original: BufferedImage, result: BufferedImage, mask: FloatArray?, multiplier: Int): BufferedImage {
        require(original.width == result.width && original.height == result.height && (mask == null || mask.size == original.width * original.height))
        val a = original.getRGB(0, 0, original.width, original.height, null, 0, original.width)
        val b = result.getRGB(0, 0, result.width, result.height, null, 0, result.width)
        val pixels = IntArray(a.size) { i ->
            if (mask != null && mask[i] != 0f) 0xff000000.toInt() else {
                var rgb = 0xff000000.toInt()
                for (shift in listOf(0, 8, 16)) rgb = rgb or (minOf(255, abs((a[i] shr shift and 255) - (b[i] shr shift and 255)) * multiplier) shl shift)
                rgb
            }
        }
        return BufferedImage(original.width, original.height, BufferedImage.TYPE_INT_RGB).also { it.setRGB(0, 0, it.width, it.height, pixels, 0, it.width) }
    }
}
