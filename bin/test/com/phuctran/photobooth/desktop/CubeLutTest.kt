package com.phuctran.photobooth.desktop

import com.phuctran.photobooth.desktop.imaging.lut.CubeLutParser
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CubeLutTest {
    private fun cube(invert: Boolean = false): String = buildString {
        appendLine("TITLE \"Test\"")
        appendLine("LUT_3D_SIZE 2")
        for (b in 0..1) for (g in 0..1) for (r in 0..1) {
            appendLine("${if (invert) 1 - r else r} $g $b")
        }
    }

    @Test fun parsesValidAndRejectsInvalid() {
        assertEquals("Test", CubeLutParser.parse(cube()).title)
        assertFailsWith<IllegalArgumentException> { CubeLutParser.parse("LUT_1D_SIZE 2\n0 0 0") }
        assertFailsWith<IllegalArgumentException> { CubeLutParser.parse("LUT_3D_SIZE 2\n0 0 0") }
        assertFailsWith<IllegalArgumentException> { CubeLutParser.parse(cube().replaceFirst("0 0 0", "NaN 0 0")) }
    }

    @Test fun identityIntensityAndAlpha() {
        val source = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        source.setRGB(0, 0, 0x7F336699)
        val original = source.getRGB(0, 0)
        assertEquals(original, CubeLutParser.parse(cube()).apply(source).getRGB(0, 0))
        val inverted = CubeLutParser.parse(cube(true))
        assertEquals(original, inverted.apply(source, 0f).getRGB(0, 0))
        val full = inverted.apply(source, 1f).getRGB(0, 0)
        assertEquals(0x7F, full ushr 24)
        assertEquals(0xCC, (full ushr 16) and 255)
        assertEquals(original, source.getRGB(0, 0))
        assertTrue(full != original)
    }

    @Test fun transparentPixelsRemainUnchanged() {
        val source = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        source.setRGB(0, 0, 0x00112233)
        assertEquals(0x00112233, CubeLutParser.parse(cube(true)).apply(source).getRGB(0, 0))
    }

    @Test fun rejectsDuplicateAndUnknownDirectives() {
        for (directive in listOf("TITLE \"Again\"", "LUT_3D_SIZE 2", "DOMAIN_MIN 0 0 0", "DOMAIN_MAX 1 1 1")) {
            val header = if (directive.startsWith("TITLE") || directive.startsWith("LUT_3D_SIZE"))
                cube() else "$directive\n${cube()}"
            assertFailsWith<IllegalArgumentException> { CubeLutParser.parse("$directive\n$header") }
        }
        assertFailsWith<IllegalArgumentException> { CubeLutParser.parse("UNKNOWN_VALUE 2\n${cube()}") }
        assertFailsWith<IllegalArgumentException> { CubeLutParser.parse("DOMAIN_MIN 1 0 0\n${cube()}") }
    }

    @Test fun customDomainAndInteriorTrilinearInterpolation() {
        val data = buildString {
            appendLine("LUT_3D_SIZE 2")
            appendLine("DOMAIN_MIN 0.2 0.2 0.2")
            appendLine("DOMAIN_MAX 0.8 0.8 0.8")
            for (b in 0..1) for (g in 0..1) for (r in 0..1) appendLine("${r * g * b} 0 0")
        }
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, 0x00808080)
        val result = CubeLutParser.parse(data).apply(image).getRGB(0, 0)
        assertEquals(33, (result ushr 16) and 255)
        assertEquals(0, (result ushr 8) and 255)
    }
}
