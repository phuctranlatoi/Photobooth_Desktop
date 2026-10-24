package com.phuctran.photobooth.desktop.beauty

import com.google.gson.Gson
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import javax.imageio.ImageIO
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Phase5RebuildDiagnosticTest {
    @Test fun portraitBenchmark() {
        val sessions = Path.of("C:/Users/ASUS/AppData/Local/LeSouvenirDesktop/data/sessions/")
        val candidates = if (Files.isDirectory(sessions)) Files.walk(sessions).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().matches(Regex("(?i)(shot_|Photo_).*\\.jpg")) && Files.size(it) > 100_000 }
                .sorted().toList().groupBy { it.parent }.values.mapNotNull { it.firstOrNull() }.take(14)
        } else emptyList()
        assertEquals(14, candidates.size, "Full 15-portrait benchmark requires fourteen session portraits")
        val root = Path.of("build/reports/beauty-phase5-rebuild")
        val run = root.resolve("run_${Instant.now().toEpochMilli()}")
        Files.createDirectories(run)
        val records = mutableListOf<Map<String, Any>>()
        val samples = listOf(Path.of("src/main/resources/preview/base.png")) + candidates
        BeautyMaskPipeline().use { pipeline ->
            samples.forEachIndexed { index, path ->
                val source = requireNotNull(ImageIO.read(path.toFile()))
                val image = if (maxOf(source.width, source.height) <= 1600) source else {
                    val scale = 1600.0 / maxOf(source.width, source.height)
                    BufferedImage((source.width * scale).toInt(), (source.height * scale).toInt(), BufferedImage.TYPE_INT_RGB).also {
                        it.createGraphics().apply {
                            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                            drawImage(source, 0, 0, it.width, it.height, null); dispose()
                        }
                    }
                }
                val id = if (index == 0) "base_frontal" else "session_${(index - 1).toString().padStart(2, '0')}"
                val dir = run.resolve(id); Files.createDirectories(dir)
                fun save(name: String, bitmap: BufferedImage) { ImageIO.write(bitmap, "png", dir.resolve(name).toFile()) }
                val output = pipeline.run(image)
                val mask = output.mask
                val processor = BeautyProcessor()
                val results = BeautyPreset.entries.associateWith { processor.process(image, mask, BeautyConfig.fromPreset(it)) }
                val studio = results.getValue(BeautyPreset.STUDIO).image
                val maskImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
                for (y in 0 until image.height) for (x in 0 until image.width) maskImage.raster.setSample(x, y, 0, (mask[y * image.width + x] * 255).toInt())
                save("01_original.png", image); save("02_final_improved_skin_mask.png", maskImage)
                for ((i, preset) in BeautyPreset.entries.withIndex()) save("0${i + 3}_${preset.name.lowercase()}_beauty.png", results.getValue(preset).image)
                for ((i, stage) in listOf(BeautyProcessor.Stage.SMOOTHING_ONLY, BeautyProcessor.Stage.WHITENING_ONLY, BeautyProcessor.Stage.ROSY_ONLY).withIndex())
                    save("0${i + 7}_${stage.name.lowercase()}.png", processor.process(image, mask, stage = stage).image)
                fun compare(before: BufferedImage, after: BufferedImage): BufferedImage = BufferedImage(before.width * 2, before.height, BufferedImage.TYPE_INT_RGB).also {
                    it.createGraphics().apply { drawImage(before, 0, 0, null); drawImage(after, before.width, 0, null); dispose() }
                }
                for ((i, preset) in listOf(BeautyPreset.STUDIO, BeautyPreset.BRIGHT, BeautyPreset.STRONG).withIndex())
                    save("${(i + 10)}_before_after_${preset.name.lowercase()}.png", compare(image, results.getValue(preset).image))
                val faceX = image.width / 4; val faceY = image.height / 4
                val faceW = image.width / 2; val faceH = image.height / 2
                save("13_full_resolution_face_detail.png", compare(image.getSubimage(faceX, faceY, faceW, faceH), studio.getSubimage(faceX, faceY, faceW, faceH)))
                save("14_absolute_pixel_difference.png", PixelSafeCompositor.absoluteDifference(image, studio))
                save("15_outside_mask_difference.png", PixelSafeCompositor.outsideMaskDifference(image, studio, mask))
                val deltas = BeautyPreset.entries.map { (results.getValue(it).diagnostics["deltaY"] as? Double) ?: 0.0 }
                val maxDiff = BeautyPreset.entries.maxOf { PixelSafeCompositor.outsideMaskMaxDiff(image, results.getValue(it).image, mask) }
                val pixels = studio.getRGB(0, 0, image.width, image.height, null, 0, image.width)
                val active = mask.indices.count { mask[it] >= .5f }
                val clipped = if (active > 0) mask.indices.count { mask[it] >= .5f && BeautyColorMath.toYCbCr(pixels[it]).y >= .995f } * 100.0 / active else 0.0
                val card = BufferedImage(850, 330, BufferedImage.TYPE_INT_RGB)
                card.createGraphics().apply {
                    color = Color(24, 30, 40); fillRect(0, 0, card.width, card.height)
                    color = Color.WHITE; font = font.deriveFont(18f)
                    (listOf("LE SOUVENIR / PHASE 5 / $id", "Status: ${output.result.status}  Faces: ${output.faceCount}  Active: $active") +
                        BeautyPreset.entries.map { "${it.name}: mean Y before/after ${results.getValue(it).diagnostics["meanYBefore"]} / ${results.getValue(it).diagnostics["meanYAfter"]}   delta Y ${results.getValue(it).diagnostics["deltaY"]}" } +
                        listOf("Studio latency: ${results.getValue(BeautyPreset.STUDIO).latencyMs} ms  Highlight clipping: $clipped%", "Outside-mask max diff: $maxDiff   Exact zero: ${maxDiff == 0}"))
                        .forEachIndexed { n, line -> drawString(line, 20, 35 + n * 39) }
                    dispose()
                }
                save("16_quality_metrics.png", card)
                records += mapOf("id" to id, "status" to output.result.status, "activePixels" to active, "deltas" to deltas,
                    "highlightClippingPercent" to clipped, "outsideMaskMaxDiff" to maxDiff, "latencyMs" to results.getValue(BeautyPreset.STUDIO).latencyMs)
                assertEquals(0, maxDiff, id)
                if (active > 0) {
                    assertTrue(deltas.zipWithNext().all { (a, b) -> a < b }, "Nonmonotonic delta Y: $id $deltas")
                    assertTrue(clipped < .5, "Highlight clipping: $id $clipped%")
                }
            }
        }
        Files.writeString(run.resolve("summary.json"), Gson().toJson(records))
        Files.writeString(run.resolve("summary.csv"), "id,status,activePixels,naturalDeltaY,studioDeltaY,brightDeltaY,strongDeltaY,highlightClippingPercent,outsideMaskMaxDiff,latencyMs\n" + records.joinToString("\n") {
            "${it["id"]},${it["status"]},${it["activePixels"]},${(it["deltas"] as List<*>).joinToString(",")},${it["highlightClippingPercent"]},${it["outsideMaskMaxDiff"]},${it["latencyMs"]}"
        })
        val latest = root.resolve("latest")
        if (Files.exists(latest)) Files.walk(latest).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        Files.walk(run).use { files -> files.forEach { from ->
            val to = latest.resolve(run.relativize(from))
            if (Files.isDirectory(from)) Files.createDirectories(to) else Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING)
        } }
        println("Phase 5 benchmark: $run (${records.size} samples)")
    }
}
