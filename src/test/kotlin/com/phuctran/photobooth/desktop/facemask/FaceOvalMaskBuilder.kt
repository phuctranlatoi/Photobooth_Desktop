package com.phuctran.photobooth.desktop.facemask

import kotlin.math.*

/** Crop-space semantic mask, projected back without allowing interpolation outside the crop. */
class FaceOvalMaskBuilder {
    fun composite(masks: List<FloatArray>, width: Int, height: Int): FloatArray {
        val total = FloatArray(width * height)
        for (mask in masks) {
            require(mask.size == total.size) { "Mask dimensions do not match image" }
            for (i in total.indices) total[i] = maxOf(total[i], mask[i].coerceIn(0f, 1f))
        }
        return total
    }
    data class Masks(val geometry: FloatArray, val protected: FloatArray, val candidate: FloatArray, val final: FloatArray, val cropFinal: FloatArray, val skinProb: FloatArray, val repaired: BooleanArray)
    fun build(width: Int, height: Int, parsing: OnnxFaceParsingService.Parsing, face: DetectedFace, alignment: FaceAlignment): Masks {
        val n = parsing.size; val plane = n * n
        val geometry = FloatArray(plane); val protected = FloatArray(plane); val candidate = FloatArray(plane); val crop = FloatArray(plane)
        val l = face.landmarks
        val left = alignment.toCrop(l.lx.toDouble(), l.ly.toDouble()); val right = alignment.toCrop(l.rx.toDouble(), l.ry.toDouble())
        val mouth = alignment.toCrop(l.mx.toDouble(), l.my.toDouble()); val d = n / 3.6f
        fun inside(x: Double, y: Double, cx: Double, cy: Double, rx: Float, ry: Float) = ((x - cx) / rx).pow(2) + ((y - cy) / ry).pow(2) <= 1.0
        val eligible = BooleanArray(plane); val seeds = BooleanArray(plane); val skinProb = FloatArray(plane); val repaired = BooleanArray(plane)
        for (y in 0 until n) for (x in 0 until n) {
            val i = y * n + x; val label = parsing.label(x, y)
            val distUpper = sqrt(((x - n * .5) / (1.45 * d)).pow(2) + ((y - (n * .38 + .15 * d)) / (1.40 * d)).pow(2))
            val u = (x - mouth.x) / d
            val pNeck = parsing.neckProb[i]
            val pSkin = parsing.skinNoseProb[i]
            val jawBoundary = mouth.y + d * (.60 - .30 * (abs(u) / 1.15).pow(1.6).coerceAtMost(1.2))
            val jawFade = if (y <= jawBoundary) 1f else ((jawBoundary + .12 * d - y) / (.12 * d)).toFloat().coerceIn(0f, 1f)
            val jawCutoff = jawBoundary + .14 * d
            val lowerAlpha = jawFade
            val upperAlpha = ((1.0 - distUpper) / .12).toFloat().coerceIn(0f, 1f)
            val blend = ((y - (mouth.y - .05 * d)) / (.10 * d)).toFloat().coerceIn(0f, 1f)
            geometry[i] = if (y > jawCutoff || (y > jawBoundary && pNeck > .22f)) 0f else (upperAlpha * (1f - blend) + lowerAlpha * blend)
            skinProb[i] = pSkin
            val inEyes = inside(x.toDouble(), y.toDouble(), left.x, left.y, .15f * d, .10f * d) || inside(x.toDouble(), y.toDouble(), right.x, right.y, .15f * d, .10f * d)
            val mouthCore = inside(x.toDouble(), y.toDouble(), mouth.x, mouth.y, .12f * d, .06f * d)
            val semanticMouth = (label in 11..13 && parsing.lipsProb[i] > .35f)
            val inMouth = semanticMouth || mouthCore
            val isProtected = inEyes || inMouth ||
                (label in 2..3 && parsing.browsProb[i] > .45f && parsing.browsProb[i] > pSkin) ||
                label in 4..5 ||
                label in 11..13 ||
                (label == 6 && !(pSkin >= .25f && !inEyes) && (parsing.glassesProb[i] > .60f || pSkin < .18f)) ||
                (label == 17 && parsing.hairProb[i] > .50f && parsing.hairProb[i] > pSkin) ||
                label in 14..15 || (pNeck > .28f && pNeck > pSkin * .7f)
            protected[i] = if (isProtected) 1f else 0f
            eligible[i] = !isProtected && geometry[i] > .05f && pSkin >= .18f &&
                parsing.bgProb[i] <= .50f && parsing.clothProb[i] <= .40f && pNeck <= .28f
            seeds[i] = eligible[i] && pSkin >= .50f
        }
        // Multi-source eight-connected geodesic growth from confident skin.
        val visited = BooleanArray(plane); val queue = IntArray(plane)
        var head = 0; var tail = 0
        for (i in 0 until plane) if (seeds[i]) { visited[i] = true; queue[tail++] = i }
        while (head < tail) {
            val i = queue[head++]; val x = i % n; val y = i / n
            candidate[i] = minOf(1f, skinProb[i] * 1.2f)
            crop[i] = candidate[i] * geometry[i]
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until n || ny !in 0 until n) continue
                val j = ny * n + nx
                if (eligible[j] && !visited[j]) { visited[j] = true; queue[tail++] = j }
            }
        }
        val visitedVoid = BooleanArray(plane)
        fun inCore(px: Int, py: Int): Boolean =
            inside(px.toDouble(), py.toDouble(), left.x, left.y, .15f * d, .10f * d) ||
                inside(px.toDouble(), py.toDouble(), right.x, right.y, .15f * d, .10f * d) ||
                inside(px.toDouble(), py.toDouble(), mouth.x, mouth.y, .12f * d, .06f * d)
        for (start in 0 until plane) if (crop[start] == 0f && protected[start] == 0f && !visitedVoid[start] && geometry[start] > .1f) {
            head = 0; tail = 0; queue[tail++] = start; visitedVoid[start] = true
            while (head < tail) {
                val i = queue[head++]; val x = i % n; val y = i / n
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until n || ny !in 0 until n) continue
                    val j = ny * n + nx
                    if (crop[j] == 0f && protected[j] == 0f && geometry[j] > .1f && !visitedVoid[j]) {
                        visitedVoid[j] = true; queue[tail++] = j
                    }
                }
            }
            if (tail <= 1500) for (k in 0 until tail) {
                val i = queue[k]; val x = i % n; val y = i / n
                val touchesCoreFeature = inCore(x, y) || (parsing.label(x, y) in 11..13)
                if (parsing.neckProb[i] <= .28f && parsing.bgProb[i] <= .50f && !touchesCoreFeature && parsing.clothProb[i] <= .40f) {
                    crop[i] = geometry[i] * .95f; repaired[i] = true
                }
            }
        }
        // Feather only the retained boundary; never paint excluded features or rejected islands.
        val feathered = crop.copyOf()
        for (y in 0 until n) for (x in 0 until n) {
            val i = y * n + x
            if (crop[i] == 0f || protected[i] > .5f || geometry[i] == 0f) continue
            var sum = 0f; var count = 0
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx in 0 until n && ny in 0 until n) { if (crop[ny * n + nx] > 0f) { sum += crop[ny * n + nx]; count++ } }
            }
            feathered[i] = if (geometry[i] == 0f || protected[i] > 0.5f) 0f else sum / count
        }
        val full = FloatArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val point = alignment.toCrop(x.toDouble(), y.toDouble()); val px = point.x; val py = point.y
            if (px < 0 || py < 0 || px >= n - 1 || py >= n - 1) continue
            val x0 = px.toInt(); val y0 = py.toInt(); val fx = (px - x0).toFloat(); val fy = (py - y0).toFloat()
            // Keep landmark cores excluded even when a subpixel-sized core misses crop pixel centers.
            if (inside(px, py, left.x, left.y, .12f * d, .08f * d) ||
                inside(px, py, right.x, right.y, .12f * d, .08f * d) ||
                inside(px, py, mouth.x, mouth.y, .12f * d, .06f * d)) continue
            val index = y0 * n + x0
            val value = feathered[index] * (1 - fx) * (1 - fy) + feathered[index + 1] * fx * (1 - fy) + feathered[index + n] * (1 - fx) * fy + feathered[index + n + 1] * fx * fy
            val protectedWeight = protected[index] * (1 - fx) * (1 - fy) + protected[index + 1] * fx * (1 - fy) +
                protected[index + n] * (1 - fx) * fy + protected[index + n + 1] * fx * fy
            if (protectedWeight >= 0.15f) {
                full[y * width + x] = 0f
                continue
            }
            val attenuation = if (protectedWeight > 0.02f) ((0.15f - protectedWeight) / 0.13f).coerceIn(0f, 1f) else 1f
            full[y * width + x] = (value * attenuation).coerceIn(0f, 1f)
        }
        return Masks(geometry, protected, candidate, full, feathered, skinProb, repaired)
    }
}

