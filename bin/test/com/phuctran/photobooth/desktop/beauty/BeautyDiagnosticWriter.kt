package com.phuctran.photobooth.desktop.beauty

import com.google.gson.Gson
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import javax.imageio.ImageIO

class BeautyDiagnosticWriter(private val root: Path = Path.of("build/reports/beauty-phase5")) {
    data class Record(val id: String, val status: String, val faces: Int, val latencyMs: Double, val outsideMaskMaxDiff: Int)
    private val records = mutableListOf<Record>()
    val runDir: Path = root.resolve("run_${Instant.now().toEpochMilli()}").also { Files.createDirectories(it) }
    fun write(id: String, image: BufferedImage, output: BeautyMaskPipeline.Output, config: BeautyConfig = BeautyConfig()) {
        val dir = runDir.resolve(id).also { Files.createDirectories(it) }
        fun save(name: String, bitmap: BufferedImage) { ImageIO.write(bitmap, "png", dir.resolve(name).toFile()) }
        val mask = output.mask
        val processor = BeautyProcessor()
        val result = output.result
        val maskImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
        for (y in 0 until image.height) for (x in 0 until image.width) maskImage.raster.setSample(x, y, 0, (mask[y * image.width + x] * 255).toInt())
        save("01_original.png", image)
        save("02_final_facial_skin_mask.png", maskImage)
        save("03_beauty_smoothing_only.png", processor.process(image, mask, config, BeautyProcessor.Stage.SMOOTHING_ONLY).image)
        save("04_beauty_brightening_only.png", processor.process(image, mask, config, BeautyProcessor.Stage.WHITENING_ONLY).image)
        save("05_beauty_rosy_only.png", processor.process(image, mask, config, BeautyProcessor.Stage.ROSY_ONLY).image)
        save("06_beauty_combined.png", result.image)
        val comparison = BufferedImage(image.width * 2, image.height, BufferedImage.TYPE_INT_RGB)
        comparison.createGraphics().apply { drawImage(image, 0, 0, null); drawImage(result.image, image.width, 0, null); dispose() }
        save("07_before_after_comparison.png", comparison)
        save("08_absolute_pixel_diff.png", PixelSafeCompositor.absoluteDifference(image, result.image))
        save("09_outside_mask_diff.png", PixelSafeCompositor.outsideMaskDifference(image, result.image, mask))
        val maxDiff = PixelSafeCompositor.outsideMaskMaxDiff(image, result.image, mask)
        val card = BufferedImage(750, 230, BufferedImage.TYPE_INT_RGB)
        card.createGraphics().apply {
            color = Color(25, 30, 41); fillRect(0, 0, card.width, card.height)
            color = Color.WHITE; font = font.deriveFont(18f)
            listOf("LE SOUVENIR / BEAUTY LAB / $id", "Status: ${result.status}     Faces: ${output.faceCount}",
                "Latency: %.2f ms     Outside-mask max diff: %d".format(result.latencyMs, maxDiff),
                "Smoothing ${config.smoothingStrength} / Brightening ${config.whiteningStrength} / Rosy ${config.rosyStrength}",
                "Detail preservation ${config.detailPreservation} / Exact outside-mask: ${maxDiff == 0}")
                .forEachIndexed { index, line -> drawString(line, 22, 38 + index * 40) }
            dispose()
        }
        save("10_processing_metrics.png", card)
        records += Record(id, result.status, output.faceCount, result.latencyMs, maxDiff)
    }
    fun finish() {
        Files.writeString(runDir.resolve("summary.csv"), "id,status,faces,latencyMs,outsideMaskMaxDiff\n" + records.joinToString("\n") { "${it.id},${it.status},${it.faces},${it.latencyMs},${it.outsideMaskMaxDiff}" })
        Files.writeString(runDir.resolve("summary.json"), Gson().toJson(records))
        val latest = root.resolve("latest")
        try {
            synchronized(BeautyDiagnosticWriter::class.java) {
                if (Files.exists(latest)) Files.walk(latest).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
                Files.walk(runDir).use { paths -> paths.forEach { from ->
                    val to = latest.resolve(runDir.relativize(from))
                    if (Files.isDirectory(from)) Files.createDirectories(to)
                    else Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING)
                } }
            }
        } catch (e: Exception) {
            System.err.println("Could not update latest directory: ${e.message}")
        }
    }
}

