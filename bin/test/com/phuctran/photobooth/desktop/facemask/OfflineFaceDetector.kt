package com.phuctran.photobooth.desktop.facemask

import java.awt.image.BufferedImage
import kotlin.math.*

/** Development-only color/contrast heuristic; no production camera dependencies. */
class OfflineFaceDetector : FaceDetector {
    override val name = "OfflineHeuristic"
    override fun detectAll(image: BufferedImage): List<DetectedFace> = listOfNotNull(detect(image))
    override fun close() {}
    override fun detect(image: BufferedImage): DetectedFace? {
        val w = image.width; val h = image.height
        if (w < 32 || h < 32) return null
        val scale = minOf(1.0, 640.0 / maxOf(w, h))
        val sw = (w * scale).toInt().coerceAtLeast(32)
        val sh = (h * scale).toInt().coerceAtLeast(32)
        fun toSourceX(ax: Float): Float = ax * w / sw
        fun toSourceY(ay: Float): Float = ay * h / sh
        fun toAnalysisX(sx: Float): Int = (sx * sw / w).toInt().coerceIn(0, sw - 1)
        fun toAnalysisY(sy: Float): Int = (sy * sh / h).toInt().coerceIn(0, sh - 1)
        val stride = sw + 1
        val skinInt = LongArray(stride * (sh + 1))
        val lumInt = LongArray(stride * (sh + 1))
        for (y in 0 until sh) for (x in 0 until sw) {
            val c = image.getRGB((x.toLong() * w / sw).toInt(), (y.toLong() * h / sh).toInt())
            val r = c shr 16 and 255; val g = c shr 8 and 255; val b = c and 255
            val skin = if (r > 75 && g > 40 && b > 20 && r > g && r - b > 12 && maxOf(r, g, b) - minOf(r, g, b) > 15) 1L else 0L
            val i = (y + 1) * stride + x + 1
            skinInt[i] = skin + skinInt[i - 1] + skinInt[i - stride] - skinInt[i - stride - 1]
            lumInt[i] = (299L * r + 587L * g + 114L * b) / 1000 + lumInt[i - 1] + lumInt[i - stride] - lumInt[i - stride - 1]
        }
        fun sum(table: LongArray, x0: Int, y0: Int, x1: Int, y1: Int): Long {
            val a = x0.coerceIn(0, sw); val b = y0.coerceIn(0, sh)
            val c = x1.coerceIn(a, sw); val d = y1.coerceIn(b, sh)
            return table[d * stride + c] - table[b * stride + c] - table[d * stride + a] + table[b * stride + a]
        }
        fun sumSkin(x0: Int, y0: Int, x1: Int, y1: Int) = sum(skinInt, x0, y0, x1, y1)
        fun meanLum(x0: Int, y0: Int, x1: Int, y1: Int): Double {
            val a = x0.coerceIn(0, sw); val b = y0.coerceIn(0, sh)
            val c = x1.coerceIn(a, sw); val d = y1.coerceIn(b, sh)
            return sum(lumInt, a, b, c, d).toDouble() / max(1, (c - a) * (d - b))
        }
        data class Box(val x: Int, val y: Int, val f: Int, val height: Int, val score: Double)
        val boxes = ArrayList<Box>()
        var f = max(20, (sw * .22).toInt())
        while (f <= (sw * .65).toInt()) {
            val height = (f * 1.35).toInt()
            if (height <= sh) {
                val step = max(2, f / 12)
                for (y in 0..sh - height step step) for (x in 0..sw - f step step) {
                    val x0 = x + (f * .15).toInt(); val x1 = x + (f * .85).toInt()
                    val y0 = y + (height * .15).toInt(); val y1 = y + (height * .85).toInt()
                    val density = sumSkin(x0, y0, x1, y1).toDouble() / max(1, (x1 - x0) * (y1 - y0))
                    if (density < .35) continue
                    val middle = (x0 + x1) / 2
                    val left = sumSkin(x0, y0 + height / 4, middle, y1).toDouble()
                    val right = sumSkin(middle, y0 + height / 4, x1, y1).toDouble()
                    val balance = abs(left - right) / max(1.0, left + right)
                    if (balance >= .40) continue
                    boxes.add(Box(x, y, f, height, density * (1.0 - balance)))
                }
            }
            f += max(1, f / 8)
        }
        data class Eye(val x: Int, val y: Int, val contrast: Double)
        data class Result(val box: Box, val left: Eye, val right: Eye, val score: Double)
        var best: Result? = null
        // Evaluate strong proposals at different scales, rather than letting a uniformly skin-colored
        // patch win without any evidence of two eyes.
        for (box in boxes.sortedByDescending { it.score }.take(250)) {
            val radius = max(2, box.f / 25)
            fun eyes(from: Double, to: Double): List<Eye> {
                val found = ArrayList<Eye>()
                for (y in box.y + (box.height * .28).toInt()..box.y + (box.height * .50).toInt() step 2)
                    for (x in box.x + (box.f * from).toInt()..box.x + (box.f * to).toInt() step 2) {
                        val contrast = meanLum(x - 2 * radius, y + radius, x + 2 * radius + 1, y + 3 * radius + 1) -
                            meanLum(x - radius, y - radius / 2, x + radius + 1, y + radius / 2 + 1)
                        if (contrast > 0) found.add(Eye(x, y, contrast))
                    }
                return found.sortedByDescending { it.contrast }.take(35)
            }
            val left = eyes(.12, .45); val right = eyes(.55, .88)
            for (l in left) for (r in right) {
                val d = hypot((r.x - l.x).toDouble(), (r.y - l.y).toDouble())
                if (d < .35 * box.f || d > .68 * box.f || abs(r.y - l.y) > .15 * d) continue
                val mid = (l.x + r.x) / 2.0
                if (abs(mid - box.x - box.f / 2.0) > .15 * box.f) continue
                val bridgeX = mid.toInt(); val bridgeY = (l.y + r.y) / 2
                if (sumSkin(bridgeX - radius, bridgeY, bridgeX + radius + 1, bridgeY + 2 * radius + 1) < radius.toLong() * radius / 2) continue
                val score = l.contrast + r.contrast - .5 * abs(r.y - l.y) / d - .2 * abs(mid - box.x - box.f / 2.0) / box.f + box.score * 10
                if (best == null || score > best.score) best = Result(box, l, r, score)
            }
        }
        val result = best ?: return null
        val l = result.left; val r = result.right; val box = result.box
        val eyeX = (l.x + r.x) / 2f; val eyeY = (l.y + r.y) / 2f
        val d = hypot((r.x - l.x).toDouble(), (r.y - l.y).toDouble()).toFloat()
        var mouthY = (eyeY + d).toInt().coerceIn(0, sh - 1)
        var darkest = Double.POSITIVE_INFINITY
        val mx = toAnalysisX(toSourceX(eyeX))
        for (y in toAnalysisY(toSourceY(eyeY + .80f * d))..toAnalysisY(toSourceY(eyeY + 1.30f * d))) {
            val dark = meanLum(mx - max(1, (.08f * d).toInt()), y, mx + max(1, (.08f * d).toInt()) + 1, y + 1)
            if (dark < darkest) { darkest = dark; mouthY = y }
        }
        return DetectedFace(
            FaceBounds(toSourceX(box.x.toFloat()).toInt(), toSourceY(box.y.toFloat()).toInt(),
                toSourceX(box.f.toFloat()).toInt(), toSourceY(box.height.toFloat()).toInt()),
            FaceLandmarks(toSourceX(l.x.toFloat()), toSourceY(l.y.toFloat()),
                toSourceX(r.x.toFloat()), toSourceY(r.y.toFloat()), toSourceX(eyeX), toSourceY(mouthY.toFloat()))
        )
    }
}
