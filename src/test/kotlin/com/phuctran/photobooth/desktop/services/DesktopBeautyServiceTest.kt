package com.phuctran.photobooth.desktop.services

import com.phuctran.photobooth.desktop.beauty.BeautyConfig
import com.phuctran.photobooth.desktop.beauty.BeautyPreset
import com.phuctran.photobooth.desktop.beauty.BeautyProcessor
import com.phuctran.photobooth.desktop.beauty.PixelSafeCompositor
import com.phuctran.photobooth.desktop.facemask.FaceAlignment
import com.phuctran.photobooth.desktop.facemask.FaceOvalMaskBuilder
import com.phuctran.photobooth.desktop.facemask.FaceRole
import com.phuctran.photobooth.desktop.facemask.CustomerFaceSelector
import com.phuctran.photobooth.desktop.facemask.OnnxFaceParsingService
import com.phuctran.photobooth.desktop.facemask.YuNetFaceDetector
import java.nio.file.Path
import javax.imageio.ImageIO
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopBeautyServiceTest {
    @TempDir lateinit var tempDir: Path

    @Test fun testDisabledBeautyReturnsExactSourceFile() {
        val source = tempDir.resolve("Photo_1.jpg").toFile().also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        val originalBytes = source.readBytes()
        val output = tempDir.resolve("beauty.png").toFile()
        DesktopBeautyService().use { service ->
            assertSame(source, service.processCapture(source, output, BeautyConfig(enabled = false)))
            assertSame(source, service.processCapture(source, output, BeautyConfig(0, 0, 0)))
        }
        assertFalse(output.exists())
        assertTrue(source.readBytes().contentEquals(originalBytes))
    }

    @Test fun testMissingOrCorruptFileSafelyFallsBack() {
        val missing = tempDir.resolve("missing.jpg").toFile()
        val empty = tempDir.resolve("corrupt.jpg").toFile().also { it.writeText("not an image") }
        val bytes = empty.readBytes()
        val output = tempDir.resolve("beauty.png").toFile()
        DesktopBeautyService().use { service ->
            for (source in listOf(missing, empty)) {
                assertSame(source, service.processCapture(source, output, BeautyConfig()))
            }
        }
        assertFalse(output.exists())
        assertTrue(empty.readBytes().contentEquals(bytes))
    }

    @Test fun testEnabledBeautyOnPortraitProducesValidDerivative() {
        val source = Path.of("src/main/resources/preview/base.png").toFile()
        assertTrue(source.isFile, "Portrait fixture is required")
        val original = requireNotNull(ImageIO.read(source))
        val index = 1
        val sessionDir = tempDir.resolve("session-portrait")
        val output = sessionDir.resolve("beauty").resolve("Photo_${index}_beauty.png").toFile()
        DesktopBeautyService().use { service ->
            assertEquals(output, service.processCapture(source, output, BeautyConfig.fromPreset(BeautyPreset.STUDIO, enabled = true)))
        }
        assertTrue(output.isFile && output.length() > 0L)
        assertEquals(sessionDir.resolve("beauty").resolve("Photo_1_beauty.png"), output.toPath())
        val result = requireNotNull(ImageIO.read(output))
        assertEquals(original.width, result.width)
        assertEquals(original.height, result.height)
        // The corners lie outside the portrait's facial mask and must remain pixel-identical.
        for (x in listOf(0, original.width - 1)) for (y in listOf(0, original.height - 1)) {
            assertEquals(original.getRGB(x, y), result.getRGB(x, y), "Unmasked corner ($x, $y)")
        }
    }

    @Test fun testProductionMaskAndProcessorPreserveUnmaskedPixels() {
        val source = Path.of("src/main/resources/preview/base.png").toFile()
        assertTrue(source.isFile, "Portrait fixture is required")
        val image = requireNotNull(ImageIO.read(source))
        YuNetFaceDetector().use { detector ->
            OnnxFaceParsingService().use { parser ->
                val face = CustomerFaceSelector().classify(image.width, image.height, detector.detectAll(image))
                    .first { it.role == FaceRole.FOREGROUND_CUSTOMER }.face
                val alignment = FaceAlignment(face.landmarks, parser.defaultSize)
                val crop = alignment.crop(image)
                val mask = FaceOvalMaskBuilder().build(
                    image.width, image.height, parser.parse(crop), face, alignment, alignedCrop = crop
                ).final
                assertTrue(mask.any { it > 0f }, "Portrait should have a nonempty face mask")
                assertTrue(mask.any { it == 0f }, "Portrait should have unmasked pixels")
                val result = BeautyProcessor().process(image, mask, BeautyConfig.fromPreset(BeautyPreset.STUDIO))
                assertEquals("APPLIED", result.status)
                assertEquals(0, PixelSafeCompositor.outsideMaskMaxDiff(image, result.image, mask))
                assertEquals(0, result.diagnostics["outsideMaskMaxDiff"])
            }
        }
    }

    @Test fun testCloseIsIdempotent() {
        val service = DesktopBeautyService()
        service.close()
        service.close()
    }
}
