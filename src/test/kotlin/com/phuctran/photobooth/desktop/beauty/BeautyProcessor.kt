package com.phuctran.photobooth.desktop.beauty

import java.awt.image.BufferedImage

class BeautyProcessor(private val filter: GuidedLuminanceFilter = GuidedLuminanceFilter()) {
    enum class Stage { COMBINED, SMOOTHING_ONLY, WHITENING_ONLY, ROSY_ONLY }

    fun process(image: BufferedImage?, mask: FloatArray, config: BeautyConfig = BeautyConfig(), stage: Stage = Stage.COMBINED): BeautyResult {
        requireNotNull(image) { "Image must not be null" }
        val start = System.nanoTime()
        fun fallback(status: String) = BeautyResult(PixelSafeCompositor.copy(image), status, (System.nanoTime() - start) / 1e6,
            mapOf("meanYBefore" to 0.0, "meanYAfter" to 0.0, "deltaY" to 0.0, "activePixels" to 0, "outsideMaskMaxDiff" to 0))
        if (mask.size != image.width * image.height || mask.any { !it.isFinite() || it !in 0f..1f }) return fallback("FALLBACK_INVALID_MASK")
        if (config.isZeroEffect) return fallback(if (config.enabled) "UNCHANGED_ZERO_STRENGTH" else "UNCHANGED_DISABLED")
        if (mask.all { it == 0f }) return fallback("UNCHANGED_EMPTY_MASK")
        val width = image.width; val height = image.height
        val original = image.getRGB(0, 0, width, height, null, 0, width)
        val luma = FloatArray(original.size); val cb = FloatArray(original.size); val cr = FloatArray(original.size)
        for (i in original.indices) {
            val color = BeautyColorMath.toYCbCr(original[i])
            luma[i] = color.y; cb[i] = color.cb; cr[i] = color.cr
        }
        val s = if (stage == Stage.COMBINED || stage == Stage.SMOOTHING_ONLY) config.smoothingStrength / 100f else 0f
        val w = if (stage == Stage.COMBINED || stage == Stage.WHITENING_ONLY) config.whiteningStrength / 100f else 0f
        val r = if (stage == Stage.COMBINED || stage == Stage.ROSY_ONLY) config.rosyStrength / 100f else 0f
        val d = config.detailPreservation / 100f
        val base = if (s > 0f) filter.filter(luma, width, height) else null
        val out = original.copyOf()
        var beforeSum = 0.0; var afterSum = 0.0; var active = 0
        for (i in out.indices) {
            val alpha = mask[i]
            if (alpha == 0f) continue
            val ySmooth = luma[i] - s * (1f - .70f * d) * (luma[i] - (base?.get(i) ?: luma[i]))
            val h = if (ySmooth < .75f) 1f else ((.98f - ySmooth) / (.98f - .75f)).coerceIn(0f, 1f)
            val y = (ySmooth + .28f * w * ySmooth * (1f - ySmooth) * h).coerceIn(0f, 1f)
            val weight = 4f * y * (1f - y)
            val enhanced = BeautyColorMath.toRGB(y, cb[i] - .012f * r * weight, cr[i] + .025f * r * weight)
            out[i] = PixelSafeCompositor.blend(original[i], enhanced, alpha)
            if (alpha >= .5f) {
                beforeSum += luma[i].toDouble()
                afterSum += BeautyColorMath.toYCbCr(out[i]).y.toDouble()
                active++
            }
        }
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        output.setRGB(0, 0, width, height, out, 0, width)
        val before = if (active > 0) beforeSum / active else 0.0
        val after = if (active > 0) afterSum / active else 0.0
        return BeautyResult(output, "APPLIED", (System.nanoTime() - start) / 1e6,
            mapOf("meanYBefore" to before, "meanYAfter" to after, "deltaY" to (after - before),
                "activePixels" to active, "outsideMaskMaxDiff" to PixelSafeCompositor.outsideMaskMaxDiff(image, output, mask), "stage" to stage.name))
    }
}
