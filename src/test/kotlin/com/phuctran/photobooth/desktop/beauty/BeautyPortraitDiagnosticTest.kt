package com.phuctran.photobooth.desktop.beauty

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BeautyPortraitDiagnosticTest {
    @Test fun portraitBenchmark() {
        val sessions = Path.of("C:/Users/ASUS/AppData/Local/LeSouvenirDesktop/data/sessions/")
        val candidates = if (Files.isDirectory(sessions)) Files.walk(sessions).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().matches(Regex("(?i)(shot_|Photo_).*\\.jpg")) && Files.size(it) > 100_000 }
                .sorted().toList().groupBy { it.parent }.values.mapNotNull { it.firstOrNull() }.take(14)
        } else emptyList()
        assertEquals(14, candidates.size, "Full 15-portrait benchmark requires fourteen session portraits")
        val samples = listOf(Path.of("src/main/resources/preview/base.png")) + candidates
        val writer = BeautyDiagnosticWriter()
        BeautyMaskPipeline().use { pipeline ->
            samples.forEachIndexed { index, path ->
                val source = requireNotNull(ImageIO.read(path.toFile())) { "Cannot read $path" }
                val image = if (maxOf(source.width, source.height) <= 1600) source else {
                    val scale = 1600.0 / maxOf(source.width, source.height)
                    BufferedImage((source.width * scale).toInt(), (source.height * scale).toInt(), BufferedImage.TYPE_INT_RGB).also {
                        it.createGraphics().apply {
                            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                            drawImage(source, 0, 0, it.width, it.height, null); dispose()
                        }
                    }
                }
                val output = pipeline.run(image)
                val id = if (index == 0) "base_frontal" else "session_${(index - 1).toString().padStart(2, '0')}"
                writer.write(id, image, output)
                assertEquals(0, PixelSafeCompositor.outsideMaskMaxDiff(image, output.result.image, output.mask), id)
                assertTrue(output.result.status in setOf("APPLIED", "TRUE_NEGATIVE", "VALID_SAFE_FALLBACK", "FALLBACK_NO_FACE"), id)
                if (output.result.status == "APPLIED") {
                    val before = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
                    val after = output.result.image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
                    assertTrue(after.indices.none { output.mask[it] > 0f && (before[it] and 0xffffff) != 0 && (after[it] and 0xffffff) == 0 }, "Black patch: $id")
                }
            }
        }
        writer.finish()
    }
}
