package com.phuctran.photobooth.desktop.beauty

import com.google.gson.Gson
import com.phuctran.photobooth.desktop.facemask.*
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Random
import javax.imageio.ImageIO
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RealArchiveBeautyAuditTest {
    @Test fun audit() {
        val archive = Path.of("build/Cloudinary_Archive_2026-10-19_12_38_162_Originals")
        val files = Files.walk(archive).use { paths -> paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jpg", true) }
            .sorted(compareBy<Path>({ it.fileName.toString() }, { archive.relativize(it).toString() })).toList() }
        assertEquals(125, files.size)
        // Python random.sample uses MT19937's getrandbits; consume Python's frozen manifest to avoid divergent RNGs.
        val manifest = Path.of("build/reports/beauty-phase432-random20-beauty/selection_manifest.json")
        val manifestCases = if (Files.exists(manifest)) {
            com.google.gson.JsonParser.parseString(Files.readString(manifest)).asJsonObject.getAsJsonArray("cases")
                .map { it.asJsonObject }
        } else null
        val selected = if (manifestCases != null) {
            manifestCases.map { entry -> files.single { it.fileName.toString() == entry.get("original_filename").asString } }
        } else {
            // Deterministic fallback when the Python selection manifest is unavailable.
            files.shuffled(Random(20261024L)).take(20)
        }
        assertEquals(20, selected.size)
        val root = Path.of("build/reports/beauty-real-archive-audit"); Files.createDirectories(root)
        val records = mutableListOf<Map<String, Any>>()
        YuNetFaceDetector().use { detector -> OnnxFaceParsingService().use { parser ->
            val builder = FaceOvalMaskBuilder(); val selector = CustomerFaceSelector(); val processor = BeautyProcessor()
            selected.forEachIndexed { index, path ->
                val sha256 = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                manifestCases?.get(index)?.let { entry ->
                    assertEquals(entry.get("sha256").asString, sha256, "SHA-256 mismatch: ${path.fileName}")
                }
                val started = System.nanoTime(); val image = requireNotNull(ImageIO.read(path.toFile()))
                val roles = selector.classify(image.width, image.height, detector.detectAll(image))
                val masks = roles.filter { it.role == FaceRole.FOREGROUND_CUSTOMER }.map { item ->
                    val alignment = FaceAlignment(item.face.landmarks, parser.defaultSize)
                    val crop = alignment.crop(image)
                    builder.build(image.width, image.height, parser.parse(crop), item.face, alignment, alignedCrop = crop).final
                }
                val composite = builder.composite(masks, image.width, image.height)
                val outputs = listOf(BeautyPreset.BRIGHT, BeautyPreset.STUDIO, BeautyPreset.STRONG).associateWith {
                    processor.process(image, composite, BeautyConfig.fromPreset(it))
                }
                val measuredOutsideMaskMaxDiff = outputs.mapValues { (preset, result) ->
                    PixelSafeCompositor.outsideMaskMaxDiff(image, result.image, composite).also { maxDiff ->
                        assertEquals(0, maxDiff, "${path.fileName} $preset")
                    }
                }
                val dir = root.resolve("case_${(index + 1).toString().padStart(2, '0')}"); Files.createDirectories(dir)
                fun save(name: String, bitmap: BufferedImage) { check(ImageIO.write(bitmap, "png", dir.resolve(name).toFile())) }
                save("01_original.png", image)
                val marked = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
                marked.createGraphics().apply {
                    drawImage(image, 0, 0, null); font = font.deriveFont(24f)
                    roles.forEach { item ->
                        color = if (item.role == FaceRole.FOREGROUND_CUSTOMER) Color.GREEN else Color.ORANGE
                        val b = item.face.bounds; drawRect(b.x, b.y, b.width, b.height)
                        drawString(item.role.name, b.x, (b.y - 5).coerceAtLeast(25))
                    }; dispose()
                }
                save("02_detected_faces.png", marked)
                val maskImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
                val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
                val overlay = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
                val tinted = pixels.copyOf()
                for (i in composite.indices) {
                    maskImage.raster.setSample(i % image.width, i / image.width, 0, (composite[i] * 255).toInt())
                    val a = composite[i] * .4f; val rgb = pixels[i]
                    val r = (((rgb shr 16 and 255) * (1-a))).toInt(); val g = (((rgb shr 8 and 255) * (1-a) + 255*a)).toInt()
                    val b = ((rgb and 255) * (1-a)).toInt(); tinted[i] = (r shl 16) or (g shl 8) or b
                }
                overlay.setRGB(0, 0, image.width, image.height, tinted, 0, image.width)
                save("03_composite_mask.png", maskImage); save("04_green_overlay.png", overlay)
                val bright = outputs.getValue(BeautyPreset.BRIGHT).image
                save("05_beauty_bright.png", bright)
                save("05b_beauty_studio.png", outputs.getValue(BeautyPreset.STUDIO).image)
                save("05c_beauty_strong.png", outputs.getValue(BeautyPreset.STRONG).image)
                val triptych = BufferedImage(image.width * 3, image.height, BufferedImage.TYPE_INT_RGB)
                triptych.createGraphics().apply { drawImage(image, 0, 0, null); drawImage(overlay, image.width, 0, null)
                    drawImage(bright, image.width * 2, 0, null); dispose() }
                save("06_before_after_bright.png", triptych)
                val face = roles.firstOrNull { it.role == FaceRole.FOREGROUND_CUSTOMER }?.face?.bounds
                val x = (face?.x ?: image.width / 4).coerceIn(0, image.width - 1)
                val y = (face?.y ?: image.height / 4).coerceIn(0, image.height - 1)
                val w = (face?.width ?: image.width / 2).coerceIn(1, image.width - x)
                val h = (face?.height ?: image.height / 2).coerceIn(1, image.height - y)
                val zoom = BufferedImage(w * 2, h, BufferedImage.TYPE_INT_RGB)
                zoom.createGraphics().apply { drawImage(image.getSubimage(x,y,w,h),0,0,null)
                    drawImage(bright.getSubimage(x,y,w,h),w,0,null); dispose() }
                save("07_zoomed_face_comparison.png", zoom)
                val record = mapOf<String, Any>("id" to "R20_${(index+1).toString().padStart(2,'0')}", "filename" to path.fileName.toString(), "sha256" to sha256,
                    "faces" to roles.size, "foreground" to masks.size, "bystanders" to roles.count { it.role == FaceRole.BACKGROUND_BYSTANDER },
                    "maskPixels" to composite.count { it >= .5f }, "latencyMs" to (System.nanoTime()-started)/1e6,
                    "brightMs" to outputs.getValue(BeautyPreset.BRIGHT).latencyMs,
                    "measuredOutsideMaskMaxDiff" to measuredOutsideMaskMaxDiff.mapKeys { it.key.name })
                Files.writeString(dir.resolve("case_metadata.json"), Gson().toJson(record)); records += record
                println("Archive beauty ${index+1}/20 ${path.fileName}: ${masks.size} customers")
            }
        } }
        val columns = listOf("id","filename","sha256","faces","foreground","bystanders","maskPixels","latencyMs","brightMs")
        val presets = listOf(BeautyPreset.BRIGHT, BeautyPreset.STUDIO, BeautyPreset.STRONG)
        Files.writeString(root.resolve("audit_summary.csv"), (columns + presets.map { "measuredOutsideMaskMaxDiff_${it.name.lowercase()}" }).joinToString(",") + "\n" +
            records.joinToString("\n") { record ->
                (columns.map { record.getValue(it).toString() } + presets.map {
                    @Suppress("UNCHECKED_CAST")
                    (record.getValue("measuredOutsideMaskMaxDiff") as Map<String, Int>).getValue(it.name).toString()
                }).joinToString(",")
            })
        Files.writeString(root.resolve("performance_summary.json"), Gson().toJson(mapOf("seed" to 20261024, "cases" to records)))
    }
}

