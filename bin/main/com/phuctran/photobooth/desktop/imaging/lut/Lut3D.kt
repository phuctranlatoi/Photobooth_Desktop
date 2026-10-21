package com.phuctran.photobooth.desktop.imaging.lut

import java.awt.image.BufferedImage
import kotlin.math.floor
import kotlin.math.roundToInt

class Lut3D internal constructor(
    val size: Int,
    val title: String?,
    private val domainMin: FloatArray,
    private val domainMax: FloatArray,
    private val data: FloatArray
) {
    fun apply(source: BufferedImage, intensity: Float = 1f): BufferedImage {
        val width = source.width
        val height = source.height
        val pixels = source.getRGB(0, 0, width, height, null, 0, width)
        val amount = intensity.coerceIn(0f, 1f)
        val scale = FloatArray(3) { (size - 1) / (domainMax[it] - domainMin[it]) }
        for (i in pixels.indices) {
            val pixel = pixels[i]
            if ((pixel ushr 24) == 0) continue
            val input = floatArrayOf(((pixel ushr 16) and 255) / 255f, ((pixel ushr 8) and 255) / 255f, (pixel and 255) / 255f)
            val pos = FloatArray(3) { ((input[it] - domainMin[it]) * scale[it]).coerceIn(0f, (size - 1).toFloat()) }
            val low = IntArray(3) { floor(pos[it]).toInt() }
            val high = IntArray(3) { (low[it] + 1).coerceAtMost(size - 1) }
            val frac = FloatArray(3) { pos[it] - low[it] }
            val result = IntArray(3)
            for (channel in 0..2) {
                var value = 0f
                for (b in 0..1) for (g in 0..1) for (r in 0..1) {
                    val weight = (if (r == 0) 1f - frac[0] else frac[0]) *
                        (if (g == 0) 1f - frac[1] else frac[1]) * (if (b == 0) 1f - frac[2] else frac[2])
                    val offset = 3 * ((if (r == 0) low[0] else high[0]) + size *
                        ((if (g == 0) low[1] else high[1]) + size * (if (b == 0) low[2] else high[2])))
                    value += weight * data[offset + channel]
                }
                result[channel] = ((input[channel] + amount * (value - input[channel])) * 255f).roundToInt().coerceIn(0, 255)
            }
            pixels[i] = (pixel and -0x1000000) or (result[0] shl 16) or (result[1] shl 8) or result[2]
        }
        val output = BufferedImage(width, height, if (source.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        output.setRGB(0, 0, width, height, pixels, 0, width)
        return output
    }
}
