package com.phuctran.photobooth.desktop.beauty

class GuidedLuminanceFilter {
    private fun box(input: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
        val horizontal = FloatArray(input.size)
        val output = FloatArray(input.size)
        for (y in 0 until height) {
            var sum = 0f
            for (x in 0 until minOf(width, radius + 1)) sum += input[y * width + x]
            for (x in 0 until width) {
                horizontal[y * width + x] = sum / (minOf(width - 1, x + radius) - maxOf(0, x - radius) + 1)
                if (x - radius >= 0) sum -= input[y * width + x - radius]
                if (x + radius + 1 < width) sum += input[y * width + x + radius + 1]
            }
        }
        for (x in 0 until width) {
            var sum = 0f
            for (y in 0 until minOf(height, radius + 1)) sum += horizontal[y * width + x]
            for (y in 0 until height) {
                output[y * width + x] = sum / (minOf(height - 1, y + radius) - maxOf(0, y - radius) + 1)
                if (y - radius >= 0) sum -= horizontal[(y - radius) * width + x]
                if (y + radius + 1 < height) sum += horizontal[(y + radius + 1) * width + x]
            }
        }
        return output
    }
    fun filter(luma: FloatArray, width: Int, height: Int, radius: Int = (0.012 * maxOf(width, height)).toInt().coerceIn(3, 16), epsilon: Float = .002f): FloatArray {
        require(width > 0 && height > 0 && luma.size == width * height && radius >= 0 && epsilon > 0f)
        val mean = box(luma, width, height, radius)
        val squareMean = box(FloatArray(luma.size) { luma[it] * luma[it] }, width, height, radius)
        val a = FloatArray(luma.size) { i ->
            val variance = (squareMean[i] - mean[i] * mean[i]).coerceAtLeast(0f)
            variance / (variance + epsilon)
        }
        val b = FloatArray(luma.size) { i -> mean[i] * (1f - a[i]) }
        val meanA = box(a, width, height, radius)
        val meanB = box(b, width, height, radius)
        return FloatArray(luma.size) { i -> meanA[i] * luma[i] + meanB[i] }
    }
}
