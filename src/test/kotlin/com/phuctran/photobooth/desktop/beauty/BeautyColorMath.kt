package com.phuctran.photobooth.desktop.beauty

import kotlin.math.roundToInt

object BeautyColorMath {
    data class YCbCr(val y: Float, val cb: Float, val cr: Float)
    fun toYCbCr(rgb: Int): YCbCr {
        val r = (rgb shr 16 and 255) / 255f
        val g = (rgb shr 8 and 255) / 255f
        val b = (rgb and 255) / 255f
        val y = .299f * r + .587f * g + .114f * b
        return YCbCr(y, (b - y) / 1.772f, (r - y) / 1.402f)
    }
    fun toRGB(y: Float, cb: Float, cr: Float): Int {
        fun channel(value: Float) = (value.coerceIn(0f, 1f) * 255f).roundToInt()
        return (channel(y + 1.402f * cr) shl 16) or
            (channel(y - .344136f * cb - .714136f * cr) shl 8) or channel(y + 1.772f * cb)
    }
}
