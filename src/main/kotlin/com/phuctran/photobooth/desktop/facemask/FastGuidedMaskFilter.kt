package com.phuctran.photobooth.desktop.facemask

/** Scalar guided filter with reusable scratch storage. Workspace is not thread-safe. */
class FastGuidedMaskFilter(
    val radius: Int = 8,
    val epsilon: Float = 1e-3f,
    val subsampling: Int = 2,
) {
    init { require(radius >= 0 && epsilon > 0f && subsampling in listOf(1, 2, 4)) }

    class Workspace(width: Int, height: Int, subsampling: Int = 2) {
        val smallWidth = (width + subsampling - 1) / subsampling
        val smallHeight = (height + subsampling - 1) / subsampling
        private val size = width * height
        private val smallSize = smallWidth * smallHeight
        val meanI = FloatArray(size); val meanP = FloatArray(size)
        val corrI = FloatArray(size); val corrIp = FloatArray(size)
        val varI = FloatArray(size); val covIp = FloatArray(size)
        val a = FloatArray(size); val b = FloatArray(size)
        val meanA = FloatArray(size); val meanB = FloatArray(size)
        val smallI = FloatArray(smallSize); val smallP = FloatArray(smallSize)
        val smallMeanI = FloatArray(smallSize); val smallMeanP = FloatArray(smallSize)
        val smallCorrI = FloatArray(smallSize); val smallCorrIp = FloatArray(smallSize)
        val smallA = FloatArray(smallSize); val smallB = FloatArray(smallSize)
        val smallMeanA = FloatArray(smallSize); val smallMeanB = FloatArray(smallSize)
        internal val horizontal = FloatArray(size)
        internal val smallHorizontal = FloatArray(smallSize)
        internal val smallVar = FloatArray(smallSize)
        internal val smallCov = FloatArray(smallSize)
    }

    private fun box(input: FloatArray, output: FloatArray, tmp: FloatArray, w: Int, h: Int, r: Int) {
        for (y in 0 until h) {
            var sum = 0f
            val initR = minOf(w - 1, r)
            for (x in 0..initR) sum += input[y * w + x]
            tmp[y * w] = sum
            for (x in 1 until w) {
                if (x + r < w) sum += input[y * w + x + r]
                if (x - r - 1 >= 0) sum -= input[y * w + x - r - 1]
                tmp[y * w + x] = sum
            }
        }
        for (x in 0 until w) {
            var sum = 0f
            val initR = minOf(h - 1, r)
            for (y in 0..initR) sum += tmp[y * w + x]
            val countX = minOf(w - 1, x + r) - maxOf(0, x - r) + 1
            output[x] = sum / (countX * (initR + 1))
            for (y in 1 until h) {
                if (y + r < h) sum += tmp[(y + r) * w + x]
                if (y - r - 1 >= 0) sum -= tmp[(y - r - 1) * w + x]
                val countY = minOf(h - 1, y + r) - maxOf(0, y - r) + 1
                output[y * w + x] = sum / (countX * countY)
            }
        }
    }

    fun filter(guidance: FloatArray, mask: FloatArray, width: Int, height: Int,
               workspace: Workspace = Workspace(width, height, subsampling), output: FloatArray = FloatArray(width * height)): FloatArray {
        require(width > 0 && height > 0 && guidance.size == width * height && mask.size == guidance.size && output.size == mask.size)
        val s = subsampling
        val w = if (s == 1) width else workspace.smallWidth
        val h = if (s == 1) height else workspace.smallHeight
        val i = if (s == 1) guidance else workspace.smallI
        val p = if (s == 1) mask else workspace.smallP
        if (s > 1) for (sy in 0 until h) for (sx in 0 until w) {
            var si = 0f; var sp = 0f; var count = 0
            for (y in sy * s until minOf(height, (sy + 1) * s)) for (x in sx * s until minOf(width, (sx + 1) * s)) {
                val index = y * width + x; si += guidance[index]; sp += mask[index]; count++
            }
            i[sy * w + sx] = si / count; p[sy * w + sx] = sp / count
        }
        val mi = if (s == 1) workspace.meanI else workspace.smallMeanI
        val mp = if (s == 1) workspace.meanP else workspace.smallMeanP
        val ci = if (s == 1) workspace.corrI else workspace.smallCorrI
        val cp = if (s == 1) workspace.corrIp else workspace.smallCorrIp
        val variance = if (s == 1) workspace.varI else workspace.smallVar
        val covariance = if (s == 1) workspace.covIp else workspace.smallCov
        val a = if (s == 1) workspace.a else workspace.smallA
        val b = if (s == 1) workspace.b else workspace.smallB
        val ma = if (s == 1) workspace.meanA else workspace.smallMeanA
        val mb = if (s == 1) workspace.meanB else workspace.smallMeanB
        val tmp = if (s == 1) workspace.horizontal else workspace.smallHorizontal
        val r = if (s == 1) radius else maxOf(1, radius / s)
        box(i, mi, tmp, w, h, r); box(p, mp, tmp, w, h, r)
        for (index in 0 until w * h) { ci[index] = i[index] * i[index]; cp[index] = i[index] * p[index] }
        box(ci, variance, tmp, w, h, r); box(cp, covariance, tmp, w, h, r)
        for (index in 0 until w * h) {
            ci[index] = variance[index]; cp[index] = covariance[index]
            variance[index] = ci[index] - mi[index] * mi[index]
            covariance[index] = cp[index] - mi[index] * mp[index]
            a[index] = covariance[index] / (variance[index] + epsilon)
            b[index] = mp[index] - a[index] * mi[index]
        }
        box(a, ma, tmp, w, h, r); box(b, mb, tmp, w, h, r)
        for (y in 0 until height) for (x in 0 until width) {
            val index = y * width + x
            if (s == 1) output[index] = (ma[index] * guidance[index] + mb[index]).coerceIn(0f, 1f)
            else {
                val px = ((x + .5f) / s - .5f).coerceIn(0f, (w - 1).toFloat())
                val py = ((y + .5f) / s - .5f).coerceIn(0f, (h - 1).toFloat())
                val x0 = px.toInt(); val y0 = py.toInt(); val x1 = minOf(x0 + 1, w - 1); val y1 = minOf(y0 + 1, h - 1)
                val fx = px - x0; val fy = py - y0
                val aa = (ma[y0 * w + x0] * (1 - fx) + ma[y0 * w + x1] * fx) * (1 - fy) + (ma[y1 * w + x0] * (1 - fx) + ma[y1 * w + x1] * fx) * fy
                val bb = (mb[y0 * w + x0] * (1 - fx) + mb[y0 * w + x1] * fx) * (1 - fy) + (mb[y1 * w + x0] * (1 - fx) + mb[y1 * w + x1] * fx) * fy
                output[index] = (aa * guidance[index] + bb).coerceIn(0f, 1f)
            }
        }
        return output
    }
}
