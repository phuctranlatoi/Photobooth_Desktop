package com.phuctran.photobooth.desktop.beauty

import java.awt.image.BufferedImage
import com.phuctran.photobooth.desktop.facemask.FaceOvalMaskBuilder
import org.junit.jupiter.api.Test
import kotlin.test.*

class BeautyProcessorTest {
    @Test fun rectangularMultiFaceUnionUsesMaximumAndPreservesOutsidePixels() {
        val src = image()
        val width = src.width
        val height = src.height
        val first = FloatArray(width * height)
        val second = FloatArray(width * height)
        for (y in 4 until 17) for (x in 3 until 15) first[y * width + x] = .8f
        for (y in 10 until 21) for (x in 12 until 26) second[y * width + x] = .65f

        val union = FaceOvalMaskBuilder().composite(listOf(first, second), width, height)
        for (i in union.indices) {
            assertEquals(maxOf(first[i], second[i]), union[i], "pixel $i")
            assertTrue(union[i] in 0f..1f, "pixel $i")
        }
        val output = BeautyProcessor().process(src, union)
        assertEquals("APPLIED", output.status)
        assertEquals(0, PixelSafeCompositor.outsideMaskMaxDiff(src, output.image, union))
        val before = pixels(src)
        val after = pixels(output.image)
        for (i in union.indices) if (union[i] == 0f) assertEquals(before[i], after[i], "outside pixel $i")
        assertTrue(union.indices.any { union[it] > 0f && before[it] != after[it] })
    }

    @Test fun multiFaceMaskUnionBoundedAndNoDoubleProcessing() {
        val src = image()
        val first = FloatArray(768) { .8f }
        val second = FloatArray(768) { if (it % 2 == 0) .7f else 0f }
        val union = FaceOvalMaskBuilder().composite(listOf(first, second), src.width, src.height)
        assertTrue(union.all { it == .8f && it <= 1f })
        val single = BeautyProcessor().process(src, first).image
        val combined = BeautyProcessor().process(src, union).image
        assertContentEquals(pixels(single), pixels(combined))
        assertContentEquals(pixels(src), pixels(src))
    }
    @Test fun channelWiseOutsideMaskDifferenceIsStrictlyZero() {
        val src = image()
        val result = PixelSafeCompositor.copy(src)
        val mask = FloatArray(768)
        assertEquals(0, PixelSafeCompositor.outsideMaskMaxDiff(src, result, mask))
        result.setRGB(0, 0, result.getRGB(0, 0) xor 1)
        assertEquals(1, PixelSafeCompositor.outsideMaskMaxDiff(src, result, mask))
        mask[0] = 1f
        assertEquals(0, PixelSafeCompositor.outsideMaskMaxDiff(src, result, mask))
    }
    @Test fun pipelineFallbackProcessingErrorOnException() {
        val src = image()
        val before = pixels(src)
        BeautyMaskPipeline { throw IllegalStateException("detector failed") }.use { pipeline ->
            val output = pipeline.run(src)
            assertEquals("FALLBACK_PROCESSING_ERROR", output.result.status)
            assertTrue(output.mask.all { it == 0f })
            assertContentEquals(before, pixels(output.result.image))
            assertNotSame(src, output.result.image)
            assertContentEquals(before, pixels(src))
        }
    }
    private fun image() = BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB).also { image ->
        for (y in 0 until image.height) for (x in 0 until image.width) image.setRGB(x, y, ((x * 7) shl 16) or ((y * 10) shl 8) or 100)
    }
    private fun pixels(image: BufferedImage) = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    @Test fun beautyDisabledReturnsExactOriginal() { val src = image(); val out = BeautyProcessor().process(src, FloatArray(768) { 1f }, BeautyConfig(enabled = false)); assertEquals("UNCHANGED_DISABLED", out.status); assertContentEquals(pixels(src), pixels(out.image)); assertNotSame(src, out.image) }
    @Test fun zeroStrengthReturnsExactOriginal() { val src = image(); val out = BeautyProcessor().process(src, FloatArray(768) { 1f }, BeautyConfig(0, 0, 0)); assertEquals("UNCHANGED_ZERO_STRENGTH", out.status); assertContentEquals(pixels(src), pixels(out.image)) }
    @Test fun emptyMaskReturnsExactOriginal() { val src = image(); val out = BeautyProcessor().process(src, FloatArray(768)); assertEquals("UNCHANGED_EMPTY_MASK", out.status); assertContentEquals(pixels(src), pixels(out.image)) }
    @Test fun outsideMaskPixelsAre100PercentIdentical() { val src = image(); val mask = FloatArray(768) { if (it % 3 == 0) .5f else 0f }; assertEquals(0, PixelSafeCompositor.outsideMaskMaxDiff(src, BeautyProcessor().process(src, mask).image, mask)) }
    @Test fun sourceImageIsNeverMutated() { val src = image(); val before = pixels(src); BeautyProcessor().process(src, FloatArray(768) { 1f }); assertContentEquals(before, pixels(src)) }
    @Test fun alphaBlendingIsMathematicallyAccurate() { for (alpha in listOf(0f, .25f, .5f, .75f, 1f)) assertEquals((255 shl 24) or ((100 * alpha).toInt() shl 16), PixelSafeCompositor.blend(0xff000000.toInt(), 0xff640000.toInt(), alpha)) }
    @Test fun noBlackPatchesGenerated() { val src = BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB); src.createGraphics().apply { color = java.awt.Color(150, 110, 90); fillRect(0, 0, 32, 24); dispose() }; val out = BeautyProcessor().process(src, FloatArray(768) { 1f }).image; assertTrue(pixels(out).all { (it shr 16 and 255) > 80 && (it shr 8 and 255) > 70 }) }
    @Test fun invalidMaskTriggersSafeFallback() { val src = image(); for (mask in listOf(FloatArray(5), FloatArray(768) { Float.NaN }, FloatArray(768) { 2f })) { val out = BeautyProcessor().process(src, mask); assertEquals("FALLBACK_INVALID_MASK", out.status); assertContentEquals(pixels(src), pixels(out.image)) } }
    @Test fun guidedFilterPreservesEdges() { val input = FloatArray(400) { if (it % 20 < 10) .15f else .85f }; val out = GuidedLuminanceFilter().filter(input, 20, 20); assertTrue(out[9] < .35f); assertTrue(out[10] > .65f) }
    @Test fun toneCurveIsMonotonic() { var previous = -1f; for (i in 0..1000) { val y = i / 1000f; val value = y + .35f * y * (1f - y) * (1f - y); assertTrue(value >= previous); previous = value } }
    @Test fun detailPreservationMaintainsHighFrequency() { val src = image(); val mask = FloatArray(768) { 1f }; val low = BeautyProcessor().process(src, mask, BeautyConfig(detailPreservation = 0)).image; val high = BeautyProcessor().process(src, mask, BeautyConfig(detailPreservation = 100)).image; val before = pixels(src); assertTrue(pixels(high).indices.sumOf { kotlin.math.abs((pixels(high)[it] and 255) - (before[it] and 255)) } <= pixels(low).indices.sumOf { kotlin.math.abs((pixels(low)[it] and 255) - (before[it] and 255)) } + 768) }
}

