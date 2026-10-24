package com.phuctran.photobooth.desktop.facemask

import com.google.gson.Gson
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import javax.imageio.ImageIO
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Phase42DiagnosticTest {
    private data class Row(val id: String, val status: String, val faces: Int, val latencyMs: Double,
        val tDetectMs: Double, val tAlignCropMs: Double, val tParsingMs: Double, val tMaskBuildMs: Double,
        val tGuidedFilterMs: Double, val tReprojectMs: Double, val tPipelineMs: Double, val tPngExportMs: Double, val tTotalMs: Double,
        val chin: Float, val leftCheek: Float, val rightCheek: Float, val cheeks: Float, val forehead: Float, val eyes: Float, val lips: Float, val neck: Float,
        val repairedPixels: Int, val interiorHolesCount: Int, val holeIssue: Boolean)

    @Test fun benchmark() {
        val root = Path.of("build/reports/beauty-phase42")
        val run = root.resolve("run_${Instant.now().toEpochMilli()}")
        Files.createDirectories(run)
        val sessions = Path.of("C:/Users/ASUS/AppData/Local/LeSouvenirDesktop/data/sessions/")
        require(Files.isDirectory(sessions)) { "Session images required: $sessions" }
        val files = Files.walk(sessions).use { paths -> paths.filter {
            Files.isRegularFile(it) && it.fileName.toString().matches(Regex("(?i)(shot_|Photo_).*\\.jpg")) && Files.size(it) > 100_000
        }.sorted().toList().groupBy { it.parent }.values.mapNotNull { it.firstOrNull() }.take(14) }
        require(files.size == 14) { "Expected 14 session images, found ${files.size}" }
        val samples = listOf("base_frontal" to Path.of("src/main/resources/preview/base.png")) +
            files.mapIndexed { i, file -> "session_${i.toString().padStart(2, '0')}" to file }
        val rows = mutableListOf<Row>()
        YuNetFaceDetector().use { detector -> OnnxFaceParsingService().use { service ->
            for ((id, file) in samples) {
                val original = requireNotNull(ImageIO.read(file.toFile())) { "Cannot decode $file" }
                val scale = minOf(1.0, 1600.0 / maxOf(original.width, original.height))
                val image = if (scale == 1.0) original else BufferedImage((original.width * scale).toInt(), (original.height * scale).toInt(), BufferedImage.TYPE_INT_RGB).also {
                    it.createGraphics().apply { setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); drawImage(original, 0, 0, it.width, it.height, null); dispose() }
                }
                val dir = Files.createDirectories(run.resolve(id))
                var pngNanos = 0L
                fun save(name: String, bitmap: BufferedImage) { val t = System.nanoTime(); check(ImageIO.write(bitmap, "png", dir.resolve(name).toFile())); pngNanos += System.nanoTime() - t }
                fun render(w: Int, h: Int, pixel: (Int, Int) -> Int) = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).also { out ->
                    for (y in 0 until h) for (x in 0 until w) out.setRGB(x, y, pixel(x, y))
                }
                fun gray(value: Float): Int { val v = (value.coerceIn(0f, 1f) * 255).toInt(); return Color(v, v, v).rgb }
                val start = System.nanoTime()
                val faces = detector.detectAll(image)
                val detectEnd = System.nanoTime()
                var alignMs = 0.0; var parseMs = 0.0; var pipelineMs = 0.0
                val timings = FaceOvalMaskBuilder.Timings()
                val face = faces.maxByOrNull { it.bounds.width.toLong() * it.bounds.height }
                val valid = face != null && face.landmarks.d.isFinite() && face.landmarks.d > 2f && abs(face.landmarks.roll) < Math.PI / 3
                var repairedPixels = 0; var interiorHolesCount = 0; var holeIssue = false
                var chin = 0f; var leftCheek = 0f; var rightCheek = 0f; var cheeks = 0f; var forehead = 0f; var eyes = 0f; var lips = 0f; var neck = 0f
                var status = when (id) { "session_07", "session_10" -> "TRUE_NEGATIVE"; "session_08" -> "VALID_SAFE_FALLBACK"; else -> "FAIL_DETECTION" }
                if (valid && face != null) {
                    val l = face.landmarks
                    val alignment = FaceAlignment(l, service.defaultSize)
                    val crop = alignment.crop(image)
                    val alignEnd = System.nanoTime(); alignMs = (alignEnd - detectEnd) / 1e6
                    val parsing = service.parse(crop)
                    val parseEnd = System.nanoTime(); parseMs = (parseEnd - alignEnd) / 1e6
                    val masks = FaceOvalMaskBuilder().build(image.width, image.height, parsing, face, alignment, crop, timings)
                    pipelineMs = (System.nanoTime() - start) / 1e6
                    val n = parsing.size; val d = n / 3.6
                    repairedPixels = masks.repaired.count { it }
                    val interior = BooleanArray(n * n) { i ->
                        masks.geometry[i] > .1f && masks.protected[i] == 0f && masks.cropFinal[i] == 0f &&
                            parsing.skinNoseProb[i] >= .50f && parsing.neckProb[i] <= .28f &&
                            parsing.bgProb[i] <= .50f && parsing.clothProb[i] <= .40f
                    }
                    val seen = BooleanArray(n * n); val queue = IntArray(n * n)
                    var holePixels = 0
                    for (startPixel in interior.indices) {
                        if (!interior[startPixel] || seen[startPixel]) continue
                        var head = 0; var tail = 0; var enclosed = true
                        queue[tail++] = startPixel; seen[startPixel] = true
                        while (head < tail) {
                            val i = queue[head++]; val x = i % n; val y = i / n
                            if (x == 0 || y == 0 || x == n - 1 || y == n - 1) enclosed = false
                            for (delta in intArrayOf(-1, 1, -n, n)) {
                                if ((delta == -1 && x == 0) || (delta == 1 && x == n - 1) || (delta == -n && y == 0) || (delta == n && y == n - 1)) continue
                                val j = i + delta
                                if (interior[j] && !seen[j]) { seen[j] = true; queue[tail++] = j }
                            }
                        }
                        if (enclosed) { interiorHolesCount++; holePixels += tail }
                    }
                    holeIssue = holePixels > 0
                    val left = alignment.toCrop(l.lx.toDouble(), l.ly.toDouble())
                    val right = alignment.toCrop(l.rx.toDouble(), l.ry.toDouble())
                    val mouth = alignment.toCrop(l.mx.toDouble(), l.my.toDouble())
                    val eyeY = (left.y + right.y) / 2
                    fun point(x: Double, y: Double) = alignment.toOriginal(x, y)
                    fun mean(p: Point2D, radius: Int): Float {
                        var total = 0f; var count = 0
                        for (dy in -radius..radius) for (dx in -radius..radius) {
                            val x = p.x.toInt() + dx; val y = p.y.toInt() + dy
                            if (x in 0 until image.width && y in 0 until image.height) { total += masks.final[y * image.width + x]; count++ }
                        }
                        return if (count == 0) 0f else total / count
                    }
                    fun maximum(p: Point2D, radius: Int): Float {
                        var value = 0f
                        for (dy in -radius..radius) for (dx in -radius..radius) {
                            val x = p.x.toInt() + dx; val y = p.y.toInt() + dy
                            if (x in 0 until image.width && y in 0 until image.height && masks.final[y * image.width + x] > value) {
                                value = masks.final[y * image.width + x]
                            }
                        }
                        return value
                    }
                    val skinRadius = maxOf(2, (l.d * .05).toInt().coerceAtMost(10))
                    val featureRadius = maxOf(1, (l.d * .025).toInt().coerceAtMost(3))
                    val chinVals = listOf(.30, .34, .38, .46, .54, .58).map { mean(point(n * .5, mouth.y + it * d), skinRadius) }
                    chin = chinVals.maxOrNull() ?: 0f
                    val leftCheekVals = listOf(-.26, -.32, -.38).flatMap { xOff ->
                        listOf(.42, .50).map { yOff -> mean(point(n * .5 + xOff * d, eyeY + yOff * d), skinRadius) }
                    }
                    val rightCheekVals = listOf(.26, .32, .38).flatMap { xOff ->
                        listOf(.42, .50).map { yOff -> mean(point(n * .5 + xOff * d, eyeY + yOff * d), skinRadius) }
                    }
                    leftCheek = leftCheekVals.maxOrNull() ?: 0f
                    rightCheek = rightCheekVals.maxOrNull() ?: 0f
                    // Frontal portraits require both cheeks; profiles use the visible cheek.
                    cheeks = if (leftCheek >= 0.40f && rightCheek >= 0.40f) {
                        minOf(leftCheek, rightCheek)
                    } else {
                        maxOf(leftCheek, rightCheek)
                    }
                    forehead = mean(point(n * .5, eyeY - .42 * d), skinRadius)
                    eyes = maxOf(maximum(Point2D.Double(l.lx.toDouble(), l.ly.toDouble()), featureRadius), maximum(Point2D.Double(l.rx.toDouble(), l.ry.toDouble()), featureRadius))
                    lips = maximum(Point2D.Double(l.mx.toDouble(), l.my.toDouble()), featureRadius)
                    neck = listOf(-.35, 0.0, .35).maxOf { maximum(point(n * .5 + it * d, mouth.y + .85 * d), skinRadius) }
                    if (id !in setOf("session_07", "session_10", "session_08")) status = if (chin >= .40f && cheeks >= .40f && forehead >= .30f && eyes <= .02f && lips <= .02f && neck <= .01f) "PASS" else "FAIL_MASK_SEMANTICS"
                    save("01_original.png", original)
                    val marked = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
                    marked.createGraphics().apply {
                        drawImage(image, 0, 0, null)
                        faces.forEach { f -> color = Color.GREEN; drawRect(f.bounds.x, f.bounds.y, f.bounds.width, f.bounds.height)
                            val k = f.landmarks; listOf(k.lx to k.ly, k.rx to k.ry, k.nx to k.ny, k.lmx to k.lmy, k.rmx to k.rmy).forEach { (x, y) -> fillOval(x.toInt() - 4, y.toInt() - 4, 8, 8) } }
                        color = Color.CYAN; fillOval(l.mx.toInt() - 4, l.my.toInt() - 4, 8, 8)
                        val corners = listOf(0.0 to 0.0, n.toDouble() to 0.0, n.toDouble() to n.toDouble(), 0.0 to n.toDouble()).map { point(it.first, it.second) }
                        corners.indices.forEach { i -> drawLine(corners[i].x.toInt(), corners[i].y.toInt(), corners[(i + 1) % 4].x.toInt(), corners[(i + 1) % 4].y.toInt()) }
                        dispose()
                    }
                    save("02_detected_landmarks.png", marked)
                    save("03_aligned_crop.png", crop)
                    save("04_raw_semantic_parsing.png", render(n, n) { x, y -> Color.getHSBColor(parsing.label(x, y) / 19f, .85f, .9f).rgb })
                    save("05_raw_skin_probability.png", render(n, n) { x, y -> gray(parsing.skinNoseProb[y * n + x]) })
                    // Phase 4.1: hard argmax labels 1..10, no interior-hole reconstruction.
                    val baselineCrop = FloatArray(n * n) { i -> if (parsing.labels[i].toInt() in 1..10 && masks.protected[i] == 0f) masks.geometry[i] else 0f }
                    fun project(cropMask: FloatArray): FloatArray = FloatArray(image.width * image.height).also { full ->
                        for (y in 0 until image.height) for (x in 0 until image.width) {
                            val p = alignment.toCrop(x.toDouble(), y.toDouble()); val cx = p.x.toInt(); val cy = p.y.toInt()
                            if (p.x >= 0 && p.y >= 0 && cx in 0 until n && cy in 0 until n) full[y * image.width + x] = cropMask[cy * n + cx]
                        }
                    }
                    val baseline = project(baselineCrop)
                    save("06_original_mask.png", render(image.width, image.height) { x, y -> gray(baseline[y * image.width + x]) })
                    save("07_protected_regions.png", render(n, n) { x, y -> if (masks.protected[y * n + x] > 0f) Color.RED.rgb else Color.BLACK.rgb })
                    save("08_reconstructed_skin_mask.png", render(n, n) { x, y -> gray(masks.cropFinal[y * n + x]) })
                    val alpha = render(image.width, image.height) { x, y -> gray(masks.final[y * image.width + x]) }
                    save("09_final_soft_alpha.png", alpha)
                    val overlay = render(image.width, image.height) { x, y ->
                        val rgb = image.getRGB(x, y); val a = masks.final[y * image.width + x] * .45f
                        Color(((rgb shr 16 and 255) * (1 - a) + 65 * a).toInt(), ((rgb shr 8 and 255) * (1 - a) + 230 * a).toInt(), ((rgb and 255) * (1 - a) + 174 * a).toInt()).rgb
                    }
                    save("10_final_green_overlay.png", overlay)
                    val cell = 192; val header = 28
                    val focus = BufferedImage(3 * cell, 5 * (cell + header), BufferedImage.TYPE_INT_RGB)
                    focus.createGraphics().apply {
                        val centers = listOf("Left Cheek" to point(n * .5 - .38 * d, eyeY + .52 * d),
                            "Right Cheek" to point(n * .5 + .38 * d, eyeY + .52 * d),
                            "Mouth / Philtrum" to point(mouth.x, mouth.y),
                            "Nose" to point(n * .5, eyeY + .25 * d), "Chin / Jaw" to point(n * .5, mouth.y + .38 * d))
                        centers.forEachIndexed { index, (label, center) ->
                            val radius = maxOf(20, (l.d * .28).toInt())
                            val sx = (center.x.toInt() - radius).coerceIn(0, maxOf(0, image.width - 1))
                            val sy = (center.y.toInt() - radius).coerceIn(0, maxOf(0, image.height - 1))
                            listOf(image, overlay, alpha).forEachIndexed { column, source ->
                                color = Color(25, 30, 33); fillRect(column * cell, index * (cell + header), cell, header)
                                color = Color.WHITE; drawString("$label — ${listOf("Original", "Final Green Overlay", "Final Soft Alpha")[column]}", column * cell + 6, index * (cell + header) + 19)
                                drawImage(source, column * cell, index * (cell + header) + header,
                                    (column + 1) * cell, (index + 1) * (cell + header),
                                    sx, sy, minOf(image.width, sx + radius * 2), minOf(image.height, sy + radius * 2), null)
                            }
                        }
                        dispose()
                    }
                    save("11_focused_face_regions.png", focus)
                } else {
                    pipelineMs = (detectEnd - start) / 1e6
                    save("01_original.png", original)
                    val blank = BufferedImage(service.defaultSize, service.defaultSize, BufferedImage.TYPE_INT_RGB)
                    save("02_detected_landmarks.png", image)
                    save("03_aligned_crop.png", blank)
                    for (name in listOf("04_raw_semantic_parsing", "05_raw_skin_probability", "07_protected_regions", "08_reconstructed_skin_mask")) save("$name.png", blank)
                    val fullBlank = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
                    for (name in listOf("06_original_mask", "09_final_soft_alpha")) save("$name.png", fullBlank)
                    save("10_final_green_overlay.png", image)
                    save("11_focused_face_regions.png", BufferedImage(576, 1100, BufferedImage.TYPE_INT_RGB))
                }
                val exportMs = pngNanos / 1e6
                rows += Row(id, status, faces.size, pipelineMs, (detectEnd - start) / 1e6, alignMs, parseMs,
                    timings.maskBuildMs, timings.guidedFilterMs, timings.reprojectMs, pipelineMs, exportMs, pipelineMs + exportMs,
                    chin, leftCheek, rightCheek, cheeks, forehead, eyes, lips, neck, repairedPixels, interiorHolesCount, holeIssue)
            }
        } }
        Files.writeString(run.resolve("summary.csv"), "id,status,faces,latencyMs,tDetectMs,tAlignCropMs,tParsingMs,tMaskBuildMs,tGuidedFilterMs,tReprojectMs,tPipelineMs,tPngExportMs,tTotalMs,chin,leftCheek,rightCheek,cheeks,forehead,eyes,lips,neck,repairedPixels,interiorHolesCount,holeIssue\n" + rows.joinToString("\n") { "${it.id},${it.status},${it.faces},${it.latencyMs},${it.tDetectMs},${it.tAlignCropMs},${it.tParsingMs},${it.tMaskBuildMs},${it.tGuidedFilterMs},${it.tReprojectMs},${it.tPipelineMs},${it.tPngExportMs},${it.tTotalMs},${it.chin},${it.leftCheek},${it.rightCheek},${it.cheeks},${it.forehead},${it.eyes},${it.lips},${it.neck},${it.repairedPixels},${it.interiorHolesCount},${it.holeIssue}" })
        Files.writeString(run.resolve("summary.json"), Gson().toJson(rows))
        val latest = root.resolve("latest")
        synchronized(Phase42DiagnosticTest::class.java) {
            if (Files.exists(latest)) Files.walk(latest).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
            Files.walk(run).use { paths -> paths.forEach { source ->
                val target = latest.resolve(run.relativize(source))
                if (Files.isDirectory(source)) Files.createDirectories(target) else Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            } }
        }
        assertEquals(15, rows.size)
        rows.forEach { row ->
            val failures = mutableListOf<String>()
            fun check(name: String, actual: Any, expected: Any) {
                if (actual != expected) failures += "$name=$actual (expected $expected)"
            }
            when (row.id) {
                "session_07", "session_10" -> {
                    check("faces", row.faces, 0)
                    check("status", row.status, "TRUE_NEGATIVE")
                    listOf("chin" to row.chin, "leftCheek" to row.leftCheek, "rightCheek" to row.rightCheek, "cheeks" to row.cheeks, "forehead" to row.forehead,
                        "eyes" to row.eyes, "lips" to row.lips, "neck" to row.neck).forEach { (name, value) -> check(name, value, 0f) }
                }
                "session_08" -> check("status", row.status, "VALID_SAFE_FALLBACK")
                else -> {
                    check("status", row.status, "PASS")
                    if (row.chin < .40f) failures += "chin=${row.chin} (expected >= 0.40)"
                    if (row.cheeks < .40f) failures += "cheeks=${row.cheeks} (expected >= 0.40)"
                    if (row.forehead < .30f) failures += "forehead=${row.forehead} (expected >= 0.30)"
                    if (row.eyes > .02f) failures += "eyes=${row.eyes} (expected <= 0.02)"
                    if (row.lips > .02f) failures += "lips=${row.lips} (expected <= 0.02)"
                    if (row.neck > .01f) failures += "neck=${row.neck} (expected <= 0.01)"
                }
            }
            if (failures.isNotEmpty()) throw AssertionError("${row.id}: ${failures.joinToString("; ")}")
        }
    }
}
