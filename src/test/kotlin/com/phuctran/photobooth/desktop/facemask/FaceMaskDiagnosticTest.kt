package com.phuctran.photobooth.desktop.facemask

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.geom.Point2D
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertTrue

class FaceMaskDiagnosticTest {
    private data class Sample(val id: String, val path: Path?, val condition: String, val generator: (() -> BufferedImage)? = null) {
        fun load(): BufferedImage = generator?.invoke() ?: run {
            val file = requireNotNull(path) { "Missing image path for $id" }
            require(Files.isRegularFile(file)) { "Missing image file: $file" }
            requireNotNull(ImageIO.read(file.toFile())) { "Cannot decode image: $file" }
        }
    }
    private data class Result(val id: String, val detector: String, val condition: String, val status: String,
        val faces: Int, val latencyMs: Double, val eyeDistance: Float, val roll: Double,
        val chin: Float, val cheek: Float, val jaw: Float, val forehead: Float, val nose: Float, val lips: Float,
        val neck: Float, val leftEye: Float, val rightEye: Float, val brows: Float, val hair: Float,
        val glasses: Float, val clothing: Float, val background: Float)

    @Test fun diagnostic() {
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(Instant.now().atZone(ZoneOffset.UTC))
        val root = Path.of("build/reports/beauty-diagnostics")
        var runDir = root.resolve("run_$timestamp")
        var suffix = 1
        while (Files.exists(runDir)) runDir = root.resolve("run_${timestamp}_${suffix++}")
        Files.createDirectories(runDir)
        val basePath = Path.of("src/main/resources/preview/base.png")
        val samples = mutableListOf(Sample("base_frontal", basePath, "frontal"))
        val sessions = Path.of("C:/Users/ASUS/AppData/Local/LeSouvenirDesktop/data/sessions/")
        if (Files.isDirectory(sessions)) Files.walk(sessions).use { paths ->
            val candidates = paths.filter { Files.isRegularFile(it) && it.fileName.toString().matches(Regex("(?i)(shot_|Photo_).*\\.jpg")) && Files.size(it) > 100_000 }
                .sorted().toList().groupBy { it.parent }.values.mapNotNull { it.firstOrNull() }
            for ((i, file) in candidates.take(14).withIndex()) {
                samples.add(Sample("session_${i.toString().padStart(2, '0')}", file, "session:${file.parent.fileName}"))
            }
        }
        var variant = 0
        while (samples.size < 15) {
            val index = variant++
            samples.add(Sample("base_variant_$index", null, if (index % 3 == 0) "low_light_tilt" else "tilt_translate") {
                val base = Sample("base_frontal", basePath, "frontal").load()
                val out = BufferedImage(base.width, base.height, BufferedImage.TYPE_INT_RGB)
                out.createGraphics().apply {
                    color = Color.DARK_GRAY; fillRect(0, 0, out.width, out.height)
                    setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                    rotate(Math.toRadians((if (index % 2 == 0) 10 else -10).toDouble()), out.width / 2.0, out.height / 2.0)
                    drawImage(base, (index % 3 - 1) * (out.width / 18), 0, null)
                    color = Color(0, 0, 0, if (index % 3 == 0) 95 else 0)
                    fillRect(0, 0, out.width, out.height)
                    dispose()
                }
                out
            })
        }
        val records = mutableListOf<Result>()
        val detectors = mutableListOf<FaceDetector>()
        if (YuNetFaceDetector.available) detectors.add(YuNetFaceDetector()) else println("YuNet model unavailable: ${YuNetFaceDetector.modelPath}")
        detectors.add(OfflineFaceDetector())
        try {
            OnnxFaceParsingService().use { service ->
                for (sample in samples) {
                    val loaded = sample.load()
                    val image = if (maxOf(loaded.width, loaded.height) > 1600) {
                        val scale = 1600.0 / maxOf(loaded.width, loaded.height)
                        BufferedImage((loaded.width * scale).toInt(), (loaded.height * scale).toInt(), BufferedImage.TYPE_INT_RGB).also { scaled ->
                            scaled.createGraphics().apply {
                                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                                drawImage(loaded, 0, 0, scaled.width, scaled.height, null)
                                dispose()
                            }
                        }
                    } else loaded
                    val dir = runDir.resolve(sample.id); Files.createDirectories(dir)
                    save(dir, "01_original.png", loaded)
                    var exported = false
                    for (detector in detectors) {
                        val start = System.nanoTime()
                        var detectorError = false
                        val faces = try { detector.detectAll(image) } catch (error: Exception) {
                            System.err.println("${detector.name} failed on ${sample.id}:")
                            error.printStackTrace()
                            detectorError = true
                            emptyList()
                        }
                        val ms = (System.nanoTime() - start) / 1_000_000.0
                        val face = faces.maxByOrNull { it.bounds.width.toLong() * it.bounds.height.toLong() }
                        val l = face?.landmarks
                        val valid = l != null && l.d.isFinite() && l.d > 2f && abs(l.roll) < Math.PI / 3
                        var status = if (detectorError) "ERROR_DETECTOR_MODEL" else if (face == null) {
                            if (sample.id in setOf("session_07", "session_10")) "TRUE_NEGATIVE" else if (sample.id == "session_08") "VALID_SAFE_FALLBACK" else "FAIL_DETECTION"
                        } else if (!valid) "FAIL_MASK_GEOMETRY" else "FAIL_MASK_SEMANTICS"
                        val metrics = FloatArray(13)
                        if (valid && face != null && l != null) {
                            val alignment = FaceAlignment(l, service.defaultSize)
                            val crop = alignment.crop(image)
                            val parsing = service.parse(crop)
                            val builder = FaceOvalMaskBuilder()
                            val masks = builder.build(image.width, image.height, parsing, face, alignment)
                            val primaryMask = masks.final
                            val n = parsing.size.toDouble()
                            val d = n / 3.6
                            val left = alignment.toCrop(l.lx.toDouble(), l.ly.toDouble())
                            val right = alignment.toCrop(l.rx.toDouble(), l.ry.toDouble())
                            val mouth = alignment.toCrop(l.mx.toDouble(), l.my.toDouble())
                            val eyeY = (left.y + right.y) / 2
                            fun point(x: Double, y: Double) = alignment.toOriginal(x, y)
                            val rSkin = maxOf(2, (0.05 * l.d).toInt().coerceAtMost(10))
                            val rFeature = maxOf(1, (0.025 * l.d).toInt().coerceAtMost(3))
                            val chin = point(n * .5, mouth.y + .38 * d)
                            val cheeks = listOf(point(n * .5 + .42 * d, eyeY + .52 * d), point(n * .5 - .42 * d, eyeY + .52 * d))
                            val jaws = listOf(point(n * .5 + .48 * d, mouth.y + .15 * d), point(n * .5 - .48 * d, mouth.y + .15 * d))
                            val forehead = point(n * .5, eyeY - .42 * d)
                            val nose = Point2D.Double(l.nx.toDouble(), l.ny.toDouble())
                            val lips = Point2D.Double(l.mx.toDouble(), l.my.toDouble())
                            val neckPoints = listOf(point(n * .5 - .35 * d, mouth.y + .85 * d), point(n * .5, mouth.y + .85 * d), point(n * .5 + .35 * d, mouth.y + .85 * d))
                            val eyes = listOf(Point2D.Double(l.lx.toDouble(), l.ly.toDouble()), Point2D.Double(l.rx.toDouble(), l.ry.toDouble()))
                            val brows = listOf(point(left.x, left.y - .18 * d), point(right.x, right.y - .18 * d))
                            val hair = point(n * .5, eyeY - 1.40 * d)
                            fun sampleRegion(p: java.awt.geom.Point2D, radius: Int): Float {
                                var sum = 0f
                                var count = 0
                                for (dy in -radius..radius) for (dx in -radius..radius) {
                                    val x = p.x.toInt() + dx; val y = p.y.toInt() + dy
                                    if (x in 0 until image.width && y in 0 until image.height) {
                                        sum += primaryMask[y * image.width + x]
                                        count++
                                    }
                                }
                                return if (count > 0) sum / count else 0f
                            }
                            fun sampleMax(p: java.awt.geom.Point2D, radius: Int): Float {
                                var maxVal = 0f
                                for (dy in -radius..radius) for (dx in -radius..radius) {
                                    val x = p.x.toInt() + dx; val y = p.y.toInt() + dy
                                    if (x in 0 until image.width && y in 0 until image.height) {
                                        maxVal = maxOf(maxVal, primaryMask[y * image.width + x])
                                    }
                                }
                                return maxVal
                            }
                            val cheekValues = cheeks.map { sampleRegion(it, rSkin) }
                            val jawValues = jaws.map { sampleRegion(it, rSkin) }
                            val neckMax = neckPoints.maxOf { sampleMax(it, rSkin) }
                            val neckMean = neckPoints.map { sampleRegion(it, rSkin) }.average().toFloat()
                            val chinVal = sampleRegion(chin, rSkin)
                            val foreheadVal = sampleRegion(forehead, rSkin)
                            val noseVal = sampleRegion(nose, rSkin)
                            val lipsMax = sampleMax(lips, rFeature)
                            val leftEyeMax = sampleMax(eyes[0], rFeature)
                            val rightEyeMax = sampleMax(eyes[1], rFeature)
                            val hairMax = sampleMax(hair, rSkin)
                            metrics[0] = chinVal
                            metrics[1] = cheekValues.max()
                            metrics[2] = jawValues.max()
                            metrics[3] = foreheadVal
                            metrics[4] = noseVal
                            metrics[5] = lipsMax
                            metrics[6] = neckMax
                            metrics[7] = leftEyeMax; metrics[8] = rightEyeMax
                            metrics[9] = brows.map { sampleRegion(it, rSkin) }.average().toFloat()
                            metrics[10] = hairMax
                            metrics[11] = maxOf(leftEyeMax, rightEyeMax)
                            metrics[12] = 0f
                            status = if (chinVal >= .50f && cheekValues.max() >= .50f &&
                                jawValues.max() >= .50f && foreheadVal >= .30f && noseVal >= .50f &&
                                lipsMax <= .05f && leftEyeMax <= .05f && rightEyeMax <= .05f &&
                                neckMax <= .02f && hairMax <= .02f) "PASS" else "FAIL_MASK_SEMANTICS"
                            if (!exported) {
                                exported = true
                                val marked = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
                                marked.createGraphics().apply {
                                    drawImage(image, 0, 0, null)
                                    faces.forEach { detected ->
                                        color = if (detected === face) Color.GREEN else Color.RED
                                        drawRect(detected.bounds.x, detected.bounds.y, detected.bounds.width, detected.bounds.height)
                                        val lm = detected.landmarks
                                        listOf(lm.lx to lm.ly, lm.rx to lm.ry, lm.nx to lm.ny,
                                            lm.rmx to lm.rmy, lm.lmx to lm.lmy).forEach { (x, y) -> fillOval(x.toInt() - 4, y.toInt() - 4, 8, 8) }
                                    }
                                    color = Color.CYAN
                                    fillOval(l.mx.toInt() - 4, l.my.toInt() - 4, 8, 8)
                                    val corners = listOf(0.0 to 0.0, n to 0.0, n to n, 0.0 to n).map { point(it.first, it.second) }
                                    corners.indices.forEach { i -> drawLine(corners[i].x.toInt(), corners[i].y.toInt(), corners[(i + 1) % 4].x.toInt(), corners[(i + 1) % 4].y.toInt()) }
                                    dispose()
                                }
                                save(dir, "02_yunet_landmarks.png", marked)
                                save(dir, "03_aligned_crop.png", crop)
                                save(dir, "04_raw_bisenet_classes.png", render(parsing.size, parsing.size) { x, y -> Color.getHSBColor(parsing.label(x, y) / 19f, .85f, .9f).rgb })
                                fun gray(value: Float): Int { val v = (value.coerceIn(0f, 1f) * 255).toInt(); return Color(v, v, v).rgb }
                                save(dir, "05_raw_skin_probability.png", render(parsing.size, parsing.size) { x, y -> gray(masks.skinProb[y * parsing.size + x]) })
                                save(dir, "06_face_geometry_boundary.png", render(parsing.size, parsing.size) { x, y -> gray(masks.geometry[y * parsing.size + x]) })
                                save(dir, "07_protected_feature_regions.png", render(parsing.size, parsing.size) { x, y -> if (masks.protected[y * parsing.size + x] > 0f) Color.WHITE.rgb else Color.RED.rgb })
                                save(dir, "08_final_alpha_mask.png", render(image.width, image.height) { x, y -> gray(primaryMask[y * image.width + x]) })
                                save(dir, "09_final_green_overlay.png", render(image.width, image.height) { x, y ->
                                    val rgb = image.getRGB(x, y); val a = primaryMask[y * image.width + x] * .4f
                                    Color(((rgb shr 16 and 255) * (1 - a) + 65 * a).toInt(), ((rgb shr 8 and 255) * (1 - a) + 230 * a).toInt(), ((rgb and 255) * (1 - a) + 174 * a).toInt()).rgb
                                })
                                val validation = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
                                validation.createGraphics().apply {
                                    drawImage(image, 0, 0, null)
                                    fun regions(points: List<java.awt.geom.Point2D>, shade: Color) {
                                        color = shade
                                        points.forEach { drawRect(it.x.toInt() - rSkin, it.y.toInt() - rSkin, 2 * rSkin + 1, 2 * rSkin + 1) }
                                    }
                                    regions(listOf(chin, forehead) + cheeks + jaws, Color.GREEN)
                                    regions(neckPoints + listOf(lips) + eyes, Color.RED)
                                    color = Color.YELLOW
                                    var previous: java.awt.geom.Point2D? = null
                                    for (step in 0..80) {
                                        val u = -1.15 + step * 2.30 / 80
                                        val boundary = mouth.y + d * (.56 - .30 * Math.pow(kotlin.math.abs(u) / 1.15, 1.6).coerceAtMost(1.2))
                                        val current = point(mouth.x + u * d, boundary)
                                        previous?.let { drawLine(it.x.toInt(), it.y.toInt(), current.x.toInt(), current.y.toInt()) }
                                        previous = current
                                    }
                                    dispose()
                                }
                                save(dir, "10_jaw_neck_validation.png", validation)
                            }
                        }
                        records.add(Result(sample.id, detector.name, sample.condition, status, faces.size, ms, l?.d ?: 0f, l?.roll ?: 0.0,
                            metrics[0], metrics[1], metrics[2], metrics[3], metrics[4], metrics[5], metrics[6], metrics[7], metrics[8], metrics[9], metrics[10], metrics[11], metrics[12], 0f))
                    }
                }
            }
        } finally { detectors.forEach { it.close() } }
        val fields = Result::class.java.declaredFields.filter { !it.isSynthetic }
        val csv = buildString {
            appendLine(fields.joinToString(",") { it.name })
            records.forEach { record -> appendLine(fields.joinToString(",") { field -> field.isAccessible = true; "\"${field.get(record).toString().replace("\"", "\"\"")}\"" }) }
        }
        Files.writeString(runDir.resolve("summary.csv"), csv)
        fun json(s: String) = "\"${s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
        val report = buildString {
            append("{\"images\":${samples.size},\"results\":[")
            append(records.joinToString(",") { record -> "{" + fields.joinToString(",") { field ->
                field.isAccessible = true; val value = field.get(record)
                json(field.name) + ":" + if (value is Number && value.toDouble().isFinite()) value.toString() else json(value.toString())
            } + "}" })
            append("],\"comparison\":{")
            append(detectors.joinToString(",") { detector ->
                val rows = records.filter { it.detector == detector.name }
                val found = rows.filter { it.faces > 0 }
                json(detector.name) + ":{\"recall\":${found.size.toDouble() / rows.size},\"multiFaceImages\":${rows.count { it.faces > 1 }}," +
                    "\"averageLatencyMs\":${rows.map { it.latencyMs }.average()},\"eyeDistanceMean\":${found.map { it.eyeDistance.toDouble() }.average().takeIf { it.isFinite() } ?: 0.0}," +
                    "\"conditions\":{${rows.groupBy { it.condition }.entries.joinToString(",") { (condition, group) -> json(condition) + ":" + (group.count { it.faces > 0 }.toDouble() / group.size) }}}"
            })
            append("}}")
        }
        Files.writeString(runDir.resolve("summary.json"), report)
        val latest = root.resolve("latest")
        try {
            synchronized(FaceMaskDiagnosticTest::class.java) {
                if (Files.exists(latest)) Files.walk(latest).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
                Files.walk(runDir).use { paths -> paths.forEach { from ->
                    val to = latest.resolve(runDir.relativize(from)); if (Files.isDirectory(from)) Files.createDirectories(to)
                    else Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING)
                } }
            }
        } catch (error: java.io.IOException) {
            System.err.println("Could not update diagnostic latest directory: $latest")
            error.printStackTrace()
        }
        println("Diagnostics: $runDir; comparison: $report")
        assertTrue(samples.size >= 15)
        assertTrue(records.size >= 15)
    }

    private fun save(dir: Path, name: String, image: BufferedImage) { ImageIO.write(image, "png", dir.resolve(name).toFile()) }
    private fun render(w: Int, h: Int, pixel: (Int, Int) -> Int) = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).also { out ->
        for (y in 0 until h) for (x in 0 until w) out.setRGB(x, y, pixel(x, y))
    }
}
