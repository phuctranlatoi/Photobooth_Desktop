package com.phuctran.photobooth.desktop.engine

import java.awt.image.BufferedImage
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Slot detection engine for photobooth frames.
 *
 * Main rules of this implementation:
 * 1) Slot detection is based on LARGE UNIFORM INTERIORS, not on decorations.
 * 2) Background punching is allowed ONLY on pixels connected to the slot core.
 * 3) Decorative pixels overlapping a slot must stay EXACTLY like the original image.
 * 4) We support common slot fill colors: transparent / white / off-white / black / dark gray.
 */
class SlotDetectionEngine {

    private data class IntRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val width: Int get() = right - left + 1
        val height: Int get() = bottom - top + 1
    }

    private data class Component(
        val id: Int,
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int,
        val area: Int
    )

    private data class SlotBounds(
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int,
        val pixelArea: Int,
        val id: Int
    ) {
        val width: Int get() = maxX - minX + 1
        val height: Int get() = maxY - minY + 1
        val centerX: Float get() = (minX + maxX) / 2f
        val centerY: Float get() = (minY + maxY) / 2f
    }

    private data class Rgb(val r: Int, val g: Int, val b: Int)

    fun detect(
        originalImage: BufferedImage,
        config: DetectionConfig = DetectionConfig()
    ): DetectionResult {
        @Suppress("UNUSED_VARIABLE")
        val ignoredConfig = config

        val frameBounds = findFrameBounds(originalImage)
            ?: return DetectionResult(
                originalImage.width,
                originalImage.height,
                emptyList(),
                listOf("NO_FRAME_CONTENT"),
                originalImage
            )

        val width = frameBounds.width
        val height = frameBounds.height
        val totalPixels = width * height

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.drawImage(
            originalImage,
            0,
            0,
            width,
            height,
            frameBounds.left,
            frameBounds.top,
            frameBounds.right + 1,
            frameBounds.bottom + 1,
            null
        )
        g.dispose()

        val sourcePixels = IntArray(totalPixels)
        image.getRGB(0, 0, width, height, sourcePixels, 0, width)

        val detectPixels = sourcePixels.copyOf()
        compositeTransparentOnWhite(detectPixels)

        val passes = listOf(SeedType.TRANSPARENT, SeedType.DARK, SeedType.LIGHT)
        var bestSlots = emptyList<SlotBounds>()
        var bestScore = -1f
        var bestComponentIdArray: IntArray? = null
        var bestSlotSeed: BooleanArray? = null
        var bestRadius = 2

        for (pass in passes) {
            val slotSeed = buildSeedMask(sourcePixels, detectPixels, pass)

            val radius = max(2, (min(width, height) * 0.0035f).roundToInt()).coerceAtMost(14)
            val erodedSeed = erodeSquareWithIntegralImage(slotSeed, width, height, radius)

            val componentIdArray = IntArray(totalPixels) { -1 }
            val components = findComponents(erodedSeed, componentIdArray, width, height)

            val candidates = components
                .filter { isPlausibleSlotComponent(it, width, height, radius) }
                .mapNotNull { component ->
                    val fitted = fitTrueRectangle(
                        component = component,
                        componentId = componentIdArray,
                        slotSeed = slotSeed,
                        width = width,
                        height = height,
                        radius = radius
                    )
                    if (fitted == null) null else {
                        val coverage = seedCoverage(fitted, slotSeed, width)
                        if (coverage < 0.42f) null else fitted
                    }
                }
                .let { removeNearDuplicateRects(it) }

            val score = candidates.size * 1000f + candidates.sumOf { it.pixelArea.toDouble() / totalPixels.toDouble() }.toFloat()
            if (score > bestScore && candidates.isNotEmpty()) {
                bestScore = score
                bestSlots = candidates
                bestComponentIdArray = componentIdArray
                bestSlotSeed = slotSeed
                bestRadius = radius
            }
        }

        val warnings = mutableListOf<String>()
        if (bestSlots.isEmpty()) {
            warnings += "NO_SLOTS_FOUND"
            return DetectionResult(width, height, emptyList(), warnings, image)
        }
        // DEBUG: Log raw detection results
        println("=== SLOT DETECTION DEBUG ===")
        println("Frame: ${width}x${height}")
        for ((i, s) in bestSlots.withIndex()) {
            println("  RAW slot[$i]: (${s.minX},${s.minY})-(${s.maxX},${s.maxY}) = ${s.width}x${s.height}")
        }

        val sortedBounds = regularizeSlotBounds(sortSlotsReadingOrder(bestSlots), width, height)

        // DEBUG: Log regularized results
        for ((i, s) in sortedBounds.withIndex()) {
            println("  REG slot[$i]: (${s.minX},${s.minY})-(${s.maxX},${s.maxY}) = ${s.width}x${s.height}")
        }
        println("=== END DEBUG ===")
        val slots = sortedBounds.mapIndexed { index, b ->
            FrameSlot(
                id = UUID.randomUUID().toString(),
                index = index,
                x = b.minX.toFloat() / width,
                y = b.minY.toFloat() / height,
                width = b.width.toFloat() / width,
                height = b.height.toFloat() / height,
                centerX = b.centerX / width,
                centerY = b.centerY / height,
                areaRatio = (b.width * b.height).toFloat() / totalPixels,
                shape = "RECT"
            )
        }

        val punchedPixels = sourcePixels.copyOf()
        val componentId = bestComponentIdArray!!
        val slotSeed = bestSlotSeed!!

        for (b in sortedBounds) {
            val background = estimateSlotBackground(
                slot = b,
                componentId = componentId,
                detectPixels = detectPixels,
                width = width
            )

            val noise95 = estimateBackgroundNoise95(
                slot = b,
                componentId = componentId,
                detectPixels = detectPixels,
                width = width,
                background = background
            )

            val clearThreshold = if (isDarkRgb(background)) {
                max(2.0f, noise95 + 0.8f).coerceAtMost(8f)
            } else {
                max(2.0f, noise95 + 0.8f).coerceAtMost(7f)
            }
            val featherThreshold = if (isDarkRgb(background)) {
                max(clearThreshold + 4.0f, noise95 + 5.0f).coerceAtMost(16f)
            } else {
                max(clearThreshold + 3.5f, noise95 + 4.5f).coerceAtMost(14f)
            }
            val floodThreshold = if (isDarkRgb(background)) {
                max(featherThreshold + 2.0f, noise95 + 8.0f).coerceAtMost(22f)
            } else {
                max(featherThreshold + 1.5f, noise95 + 6.5f).coerceAtMost(18f)
            }

            val backgroundMask = buildConnectedBackgroundMask(
                slot = b,
                componentId = componentId,
                detectPixels = detectPixels,
                width = width,
                height = height,
                background = background,
                definiteThreshold = clearThreshold,
                floodThreshold = floodThreshold
            )

            for (y in b.minY..b.maxY) {
                val row = y * width
                for (x in b.minX..b.maxX) {
                    val i = row + x
                    if (!backgroundMask[i]) {
                        // Decorative/detail pixel. Keep EXACTLY the original ARGB pixel.
                        continue
                    }

                    val src = sourcePixels[i]
                    val srcA = (src ushr 24) and 0xFF
                    if (srcA == 0) {
                        punchedPixels[i] = 0x00000000
                        continue
                    }

                    val dp = detectPixels[i]
                    val r = (dp ushr 16) and 0xFF
                    val gCh = (dp ushr 8) and 0xFF
                    val bCh = dp and 0xFF
                    val distance = colorDistance(r, gCh, bCh, background.r, background.g, background.b)

                    when {
                        distance <= clearThreshold -> {
                            punchedPixels[i] = 0x00000000
                        }

                        distance >= featherThreshold -> {
                            // Connected to background core but visually no longer close to the
                            // slot fill. Be conservative and keep the original pixel.
                            continue
                        }

                        else -> {
                            // Soft transition ONLY for connected background edge pixels.
                            // This avoids jagged edges but never alters decorative pixels.
                            if (srcA < 245) {
                                continue
                            }

                            val keepAlpha = smoothstep(clearThreshold, featherThreshold, distance)
                            val newA = (srcA * keepAlpha).roundToInt().coerceIn(0, 255)
                            if (newA <= 1) {
                                punchedPixels[i] = 0x00000000
                                continue
                            }

                            val srcR = (src ushr 16) and 0xFF
                            val srcG = (src ushr 8) and 0xFF
                            val srcB = src and 0xFF
                            val outR = unmatteChannel(srcR, background.r, keepAlpha)
                            val outG = unmatteChannel(srcG, background.g, keepAlpha)
                            val outB = unmatteChannel(srcB, background.b, keepAlpha)
                            punchedPixels[i] =
                                (newA shl 24) or (outR shl 16) or (outG shl 8) or outB
                        }
                    }
                }
            }
        }

        val punched = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        punched.setRGB(0, 0, width, height, punchedPixels, 0, width)
        return DetectionResult(width, height, slots, warnings, punched)
    }

    private fun findFrameBounds(image: BufferedImage): IntRect? {
        val w = image.width
        val h = image.height
        val rowSupport = IntArray(h)
        val colSupport = IntArray(w)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = image.getRGB(x, y)
                val a = (p ushr 24) and 0xFF
                if (a < 8) continue

                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                if (distanceFromWhite(r, g, b) > 5.0f) {
                    rowSupport[y]++
                    colSupport[x]++
                }
            }
        }

        val minRowSupport = max(2, (w * 0.003f).roundToInt())
        val minColSupport = max(2, (h * 0.003f).roundToInt())

        var top = rowSupport.indexOfFirst { it >= minRowSupport }
        var bottom = rowSupport.indexOfLast { it >= minRowSupport }
        var left = colSupport.indexOfFirst { it >= minColSupport }
        var right = colSupport.indexOfLast { it >= minColSupport }

        if (top < 0 || bottom < 0 || left < 0 || right < 0) {
            top = h
            bottom = -1
            left = w
            right = -1

            for (y in 0 until h) {
                for (x in 0 until w) {
                    val p = image.getRGB(x, y)
                    val a = (p ushr 24) and 0xFF
                    if (a < 8) continue

                    val r = (p ushr 16) and 0xFF
                    val g = (p ushr 8) and 0xFF
                    val b = p and 0xFF
                    if (distanceFromWhite(r, g, b) > 5.0f) {
                        if (x < left) left = x
                        if (x > right) right = x
                        if (y < top) top = y
                        if (y > bottom) bottom = y
                    }
                }
            }
        }

        if (right < left || bottom < top) return null
        return IntRect(left, top, right, bottom)
    }

    private fun compositeTransparentOnWhite(pixels: IntArray) {
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            if (a == 255) continue

            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            val af = a / 255f
            val nr = (r * af + 255f * (1f - af)).roundToInt().coerceIn(0, 255)
            val ng = (g * af + 255f * (1f - af)).roundToInt().coerceIn(0, 255)
            val nb = (b * af + 255f * (1f - af)).roundToInt().coerceIn(0, 255)
            pixels[i] = (255 shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
    }

    enum class SeedType {
        TRANSPARENT, LIGHT, DARK
    }

    private fun buildSeedMask(sourcePixels: IntArray, detectPixels: IntArray, type: SeedType): BooleanArray {
        val totalPixels = sourcePixels.size
        val mask = BooleanArray(totalPixels)
        for (i in 0 until totalPixels) {
            val src = sourcePixels[i]
            val srcA = (src ushr 24) and 0xFF
            val dp = detectPixels[i]
            val r = (dp ushr 16) and 0xFF
            val gCh = (dp ushr 8) and 0xFF
            val b = dp and 0xFF

            if (type == SeedType.TRANSPARENT) {
                if (srcA < 20) mask[i] = true
            } else if (srcA >= 20) {
                val maxC = max(r, max(gCh, b))
                val minC = min(r, min(gCh, b))
                val range = maxC - minC
                val avg = (r + gCh + b) / 3f

                if (type == SeedType.LIGHT) {
                    if (avg >= 252f && range <= 10 && distanceFromWhite(r, gCh, b) <= 6f) mask[i] = true
                } else if (type == SeedType.DARK) {
                    val darkNeutral = avg <= 28f && range <= 20
                    val darkGrayNeutral = avg <= 42f && range <= 10
                    if (darkNeutral || darkGrayNeutral) mask[i] = true
                }
            }
        }
        return mask
    }

    private fun erodeSquareWithIntegralImage(
        source: BooleanArray,
        width: Int,
        height: Int,
        radius: Int
    ): BooleanArray {
        val stride = width + 1
        val integral = IntArray((width + 1) * (height + 1))

        for (y in 0 until height) {
            var rowSum = 0
            for (x in 0 until width) {
                if (!source[y * width + x]) rowSum++
                integral[(y + 1) * stride + (x + 1)] = integral[y * stride + (x + 1)] + rowSum
            }
        }

        val result = BooleanArray(width * height)
        for (y in radius until height - radius) {
            val y0 = y - radius
            val y1 = y + radius
            for (x in radius until width - radius) {
                if (!source[y * width + x]) continue
                val x0 = x - radius
                val x1 = x + radius
                val nonSeedCount = rectSum(integral, stride, x0, y0, x1, y1)
                result[y * width + x] = nonSeedCount == 0
            }
        }
        return result
    }

    private fun rectSum(
        integral: IntArray,
        stride: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int
    ): Int {
        val ax = x0
        val ay = y0
        val bx = x1 + 1
        val by = y1 + 1
        return integral[by * stride + bx] -
            integral[ay * stride + bx] -
            integral[by * stride + ax] +
            integral[ay * stride + ax]
    }

    private fun findComponents(
        mask: BooleanArray,
        componentId: IntArray,
        width: Int,
        height: Int
    ): List<Component> {
        val total = width * height
        val queue = IntArray(total)
        val result = mutableListOf<Component>()
        var nextId = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val start = y * width + x
                if (!mask[start] || componentId[start] != -1) continue

                var head = 0
                var tail = 0
                queue[tail++] = start
                componentId[start] = nextId

                var minX = x
                var maxX = x
                var minY = y
                var maxY = y
                var area = 0

                while (head < tail) {
                    val curr = queue[head++]
                    val cx = curr % width
                    val cy = curr / width

                    area++
                    if (cx < minX) minX = cx
                    if (cx > maxX) maxX = cx
                    if (cy < minY) minY = cy
                    if (cy > maxY) maxY = cy

                    if (cx > 0) {
                        val n = curr - 1
                        if (mask[n] && componentId[n] == -1) {
                            componentId[n] = nextId
                            queue[tail++] = n
                        }
                    }
                    if (cx + 1 < width) {
                        val n = curr + 1
                        if (mask[n] && componentId[n] == -1) {
                            componentId[n] = nextId
                            queue[tail++] = n
                        }
                    }
                    if (cy > 0) {
                        val n = curr - width
                        if (mask[n] && componentId[n] == -1) {
                            componentId[n] = nextId
                            queue[tail++] = n
                        }
                    }
                    if (cy + 1 < height) {
                        val n = curr + width
                        if (mask[n] && componentId[n] == -1) {
                            componentId[n] = nextId
                            queue[tail++] = n
                        }
                    }
                }

                result += Component(nextId, minX, maxX, minY, maxY, area)
                nextId++
            }
        }

        return result
    }

    private fun isPlausibleSlotComponent(
        c: Component,
        width: Int,
        height: Int,
        radius: Int
    ): Boolean {
        val total = width.toLong() * height.toLong()
        val boxW = c.maxX - c.minX + 1
        val boxH = c.maxY - c.minY + 1
        val boxArea = boxW.toLong() * boxH.toLong()
        if (boxArea <= 0L) return false

        val areaRatio = c.area.toDouble() / total.toDouble()
        val fillRatio = c.area.toDouble() / boxArea.toDouble()

        if (areaRatio < 0.010 || areaRatio > 0.78) return false
        // INCREASED threshold to reject tiny phantom slots at frame intersections (like stars/bows)
        if (boxW < width * 0.12f || boxH < height * 0.12f) return false
        // INCREASED fill ratio to ensure we only pick dense rectangles (slots) instead of sparse decorations
        if (fillRatio < 0.65) return false

        val edgeGuard = max(1, radius / 2)
        if (c.minX <= edgeGuard || c.minY <= edgeGuard ||
            c.maxX >= width - 1 - edgeGuard || c.maxY >= height - 1 - edgeGuard
        ) {
            return false
        }

        return true
    }

    private fun fitTrueRectangle(
        component: Component,
        componentId: IntArray,
        slotSeed: BooleanArray,
        width: Int,
        height: Int,
        radius: Int
    ): SlotBounds? {
        val rowLeft = mutableListOf<Int>()
        val rowRight = mutableListOf<Int>()
        val colTop = mutableListOf<Int>()
        val colBottom = mutableListOf<Int>()

        for (y in component.minY..component.maxY) {
            var first = -1
            var last = -1
            val row = y * width
            for (x in component.minX..component.maxX) {
                if (componentId[row + x] == component.id) {
                    if (first < 0) first = x
                    last = x
                }
            }
            if (first >= 0) {
                rowLeft += first
                rowRight += last
            }
        }

        for (x in component.minX..component.maxX) {
            var first = -1
            var last = -1
            for (y in component.minY..component.maxY) {
                if (componentId[y * width + x] == component.id) {
                    if (first < 0) first = y
                    last = y
                }
            }
            if (first >= 0) {
                colTop += first
                colBottom += last
            }
        }

        if (rowLeft.isEmpty() || colTop.isEmpty()) return null

        var left = percentile(rowLeft, 0.10f) - radius
        var right = percentile(rowRight, 0.90f) + radius
        var top = percentile(colTop, 0.10f) - radius
        var bottom = percentile(colBottom, 0.90f) + radius

        left = left.coerceIn(0, width - 1)
        right = right.coerceIn(0, width - 1)
        top = top.coerceIn(0, height - 1)
        bottom = bottom.coerceIn(0, height - 1)

        if (right <= left || bottom <= top) return null

        val search = radius * 2 + 4
        left = refineVerticalEdge(
            mask = slotSeed,
            width = width,
            height = height,
            minCandidate = max(0, left - search),
            maxCandidate = min(right - 1, left + search),
            y0 = top,
            y1 = bottom,
            searchRight = true
        )
        right = refineVerticalEdge(
            mask = slotSeed,
            width = width,
            height = height,
            minCandidate = min(width - 1, right + search),
            maxCandidate = max(left + 1, right - search),
            y0 = top,
            y1 = bottom,
            searchRight = false
        )
        top = refineHorizontalEdge(
            mask = slotSeed,
            width = width,
            height = height,
            minCandidate = max(0, top - search),
            maxCandidate = min(bottom - 1, top + search),
            x0 = left,
            x1 = right,
            searchDown = true
        )
        bottom = refineHorizontalEdge(
            mask = slotSeed,
            width = width,
            height = height,
            minCandidate = min(height - 1, bottom + search),
            maxCandidate = max(top + 1, bottom - search),
            x0 = left,
            x1 = right,
            searchDown = false
        )

        val rectW = right - left + 1
        val rectH = bottom - top + 1
        if (rectW < width * 0.10f || rectH < height * 0.10f) return null

        return SlotBounds(left, right, top, bottom, component.area, component.id)
    }

    private fun refineVerticalEdge(
        mask: BooleanArray,
        width: Int,
        height: Int,
        minCandidate: Int,
        maxCandidate: Int,
        y0: Int,
        y1: Int,
        searchRight: Boolean
    ): Int {
        val range = if (searchRight) minCandidate..maxCandidate else minCandidate downTo maxCandidate
        val span = max(1, y1 - y0 + 1)
        // INCREASED threshold from 0.32f to 0.60f so it ignores sparse thin borders (like text) and only hits dense slot edges
        val threshold = 0.60f
        for (x in range) {
            var count = 0
            for (y in y0..y1) {
                if (mask[y * width + x]) count++
            }
            if (count.toFloat() / span >= threshold) return x
        }
        return maxCandidate
    }

    private fun refineHorizontalEdge(
        mask: BooleanArray,
        width: Int,
        height: Int,
        minCandidate: Int,
        maxCandidate: Int,
        x0: Int,
        x1: Int,
        searchDown: Boolean
    ): Int {
        val range = if (searchDown) minCandidate..maxCandidate else minCandidate downTo maxCandidate
        val span = max(1, x1 - x0 + 1)
        // INCREASED threshold from 0.32f to 0.60f so it ignores sparse thin borders (like text) and only hits dense slot edges
        val threshold = 0.60f
        for (y in range) {
            var count = 0
            val row = y * width
            for (x in x0..x1) {
                if (mask[row + x]) count++
            }
            if (count.toFloat() / span >= threshold) return y
        }
        return maxCandidate
    }

    private fun seedCoverage(rect: SlotBounds, mask: BooleanArray, width: Int): Float {
        var count = 0
        var total = 0
        for (y in rect.minY..rect.maxY) {
            val row = y * width
            for (x in rect.minX..rect.maxX) {
                total++
                if (mask[row + x]) count++
            }
        }
        return if (total == 0) 0f else count.toFloat() / total
    }

    private fun removeNearDuplicateRects(input: List<SlotBounds>): List<SlotBounds> {
        if (input.size < 2) return input
        val kept = mutableListOf<SlotBounds>()

        for (candidate in input.sortedByDescending { it.width * it.height }) {
            val duplicate = kept.any { existing -> intersectionOverUnion(candidate, existing) > 0.82f }
            if (!duplicate) kept += candidate
        }
        return kept
    }

    private fun intersectionOverUnion(a: SlotBounds, b: SlotBounds): Float {
        val left = max(a.minX, b.minX)
        val top = max(a.minY, b.minY)
        val right = min(a.maxX, b.maxX)
        val bottom = min(a.maxY, b.maxY)
        if (right < left || bottom < top) return 0f

        val intersection = (right - left + 1) * (bottom - top + 1)
        val areaA = a.width * a.height
        val areaB = b.width * b.height
        return intersection.toFloat() / (areaA + areaB - intersection).toFloat()
    }

    private fun sortSlotsReadingOrder(input: List<SlotBounds>): List<SlotBounds> {
        if (input.size <= 1) return input
        val medianHeight = input.map { it.height }.sorted().let { it[it.size / 2] }
        val rowTolerance = max(6f, medianHeight * 0.18f)

        return input.sortedWith(Comparator { a, b ->
            val dy = a.centerY - b.centerY
            if (abs(dy) > rowTolerance) {
                a.centerY.compareTo(b.centerY)
            } else {
                a.centerX.compareTo(b.centerX)
            }
        })
    }

    private fun regularizeSlotBounds(input: List<SlotBounds>, width: Int, height: Int): List<SlotBounds> {
        if (input.size <= 1) return input

        // Pick the LARGER median (upper) to use the true slot size, not the corrupted smaller one
        val medianW = input.map { it.width }.sorted().let { it[it.size / 2] }
        val medianH = input.map { it.height }.sorted().let { it[it.size / 2] }
        val similarCount = input.count {
            abs(it.width - medianW) <= max(6, (medianW * 0.16f).roundToInt()) &&
                abs(it.height - medianH) <= max(6, (medianH * 0.16f).roundToInt())
        }
        
        val sorted = sortSlotsReadingOrder(input)

        val rowCenters = clusterCenters(sorted.map { it.centerY.toFloat() }).sorted()
        val colCenters = clusterCenters(sorted.map { it.centerX.toFloat() }).sorted()
        
        val rows = rowCenters.size
        val cols = colCenters.size

        if (rows * cols != sorted.size || rows == 0 || cols == 0) {
            // Not a perfect grid, fallback to basic clustering
            return sorted.map { b ->
                val snappedCx = nearestClusterCenter(b.centerX.toFloat(), colCenters)
                val snappedCy = nearestClusterCenter(b.centerY.toFloat(), rowCenters)
                val left = (snappedCx - medianW / 2f).roundToInt().coerceIn(0, width - medianW)
                val top = (snappedCy - medianH / 2f).roundToInt().coerceIn(0, height - medianH)
                SlotBounds(
                    minX = left,
                    maxX = left + medianW - 1,
                    minY = top,
                    maxY = top + medianH - 1,
                    pixelArea = b.pixelArea,
                    id = b.id
                )
            }
        }

        // --- GRID RECONSTRUCTION ---
        // Find the most reliable starting coordinates for each row/col
        val xStarts = mutableListOf<Int>()
        for (c in 0 until cols) {
            val colSlots = sorted.filter { nearestClusterCenter(it.centerX.toFloat(), colCenters) == colCenters[c] }
            val minX = colSlots.map { it.minX }.sorted().let { if (it.isNotEmpty()) it[(it.size - 1) / 2] else 0 }
            xStarts.add(minX)
        }

        val yStarts = mutableListOf<Int>()
        for (r in 0 until rows) {
            val rowSlots = sorted.filter { nearestClusterCenter(it.centerY.toFloat(), rowCenters) == rowCenters[r] }
            val minY = rowSlots.map { it.minY }.sorted().let { if (it.isNotEmpty()) it[(it.size - 1) / 2] else 0 }
            yStarts.add(minY)
        }
        
        // Enforce constant gaps (pick median gap)
        val refinedXStarts = if (cols >= 2) {
            val gaps = mutableListOf<Int>()
            for (c in 0 until cols - 1) gaps.add(xStarts[c+1] - (xStarts[c] + medianW))
            val medianGap = max(0, gaps.sorted().let { it[it.size / 2] })
            // If xStarts[0] is suspiciously at edge (0), back-calculate from xStarts[1]
            val anchorX = if (xStarts[0] < 4 && xStarts.size >= 2) {
                max(0, xStarts[1] - medianW - medianGap)
            } else xStarts[0]
            List(cols) { c -> anchorX + c * (medianW + medianGap) }
        } else xStarts

        val refinedYStarts = if (rows >= 2) {
            val gaps = mutableListOf<Int>()
            for (r in 0 until rows - 1) gaps.add(yStarts[r+1] - (yStarts[r] + medianH))
            var medianGap = max(0, gaps.sorted().let { it[it.size / 2] })

            var anchorY = yStarts[0]

            // If yStarts[0] is suspiciously at edge (0-3px), it likely bloated.
            if (anchorY < 4) {
                val padL = refinedXStarts.firstOrNull() ?: 0
                
                if (cols == 1 && padL > 0) {
                    // For 1-col layouts with corrupted top, assume symmetric padding: padTop = gap = padLeft
                    anchorY = padL
                    medianGap = padL
                } else if (yStarts.size >= 2) {
                    val reliableGap = if (rows > 2) {
                        yStarts[2] - (yStarts[1] + medianH)
                    } else medianGap
                    
                    medianGap = max(0, reliableGap)
                    anchorY = max(0, yStarts[1] - medianH - medianGap)
                }
            }

            List(rows) { r -> anchorY + r * (medianH + medianGap) }
        } else yStarts

        // Enforce symmetry: if there's only 1 column, the slot width should be symmetric
        // padLeft and padRight should be equal (or nearly equal)
        val effectiveW = if (cols == 1) {
            val padL = refinedXStarts[0]
            val padR = width - (refinedXStarts[0] + medianW)
            if (padR <= 0 && padL > 0) {
                // Slot bleeds to right edge - enforce symmetric width
                width - 2 * padL
            } else {
                medianW
            }
        } else medianW

        // Same for single row
        val effectiveH = if (rows == 1) {
            val padT = refinedYStarts[0]
            val padB = height - (refinedYStarts[0] + medianH)
            if (padB <= 0 && padT > 0) {
                height - 2 * padT
            } else {
                medianH
            }
        } else medianH

        return sorted.map { b ->
            val cIndex = colCenters.indexOf(nearestClusterCenter(b.centerX.toFloat(), colCenters))
            val rIndex = rowCenters.indexOf(nearestClusterCenter(b.centerY.toFloat(), rowCenters))

            val safeCIndex = max(0, cIndex)
            val safeRIndex = max(0, rIndex)

            val left = refinedXStarts[safeCIndex].coerceIn(0, width - effectiveW)
            val top = refinedYStarts[safeRIndex].coerceIn(0, height - effectiveH)
            
            SlotBounds(
                minX = left,
                maxX = left + effectiveW - 1,
                minY = top,
                maxY = top + effectiveH - 1,
                pixelArea = b.pixelArea,
                id = b.id
            )
        }.let { removeNearDuplicateRects(it) }
            .sortedWith(compareBy<SlotBounds> { it.centerY }.thenBy { it.centerX })
    }

    private fun clusterCenters(values: List<Float>, tolerance: Float = 16f): List<Float> {
        if (values.isEmpty()) return emptyList()
        val sorted = values.sorted()
        val clusters = mutableListOf<MutableList<Float>>()
        for (v in sorted) {
            val last = clusters.lastOrNull()
            if (last == null) {
                clusters += mutableListOf(v)
            } else {
                val center = last.average().toFloat()
                if (abs(v - center) <= tolerance) {
                    last += v
                } else {
                    clusters += mutableListOf(v)
                }
            }
        }
        return clusters.map { it.average().toFloat() }
    }

    private fun nearestClusterCenter(value: Float, centers: List<Float>): Float {
        if (centers.isEmpty()) return value
        return centers.minByOrNull { abs(it - value) } ?: value
    }

    private fun estimateSlotBackground(
        slot: SlotBounds,
        componentId: IntArray,
        detectPixels: IntArray,
        width: Int
    ): Rgb {
        val hr = IntArray(256)
        val hg = IntArray(256)
        val hb = IntArray(256)
        var count = 0

        for (y in slot.minY..slot.maxY) {
            val row = y * width
            for (x in slot.minX..slot.maxX) {
                val i = row + x
                if (componentId[i] != slot.id) continue
                val p = detectPixels[i]
                hr[(p ushr 16) and 0xFF]++
                hg[(p ushr 8) and 0xFF]++
                hb[p and 0xFF]++
                count++
            }
        }

        if (count == 0) return Rgb(255, 255, 255)
        return Rgb(
            histogramMedian(hr, count),
            histogramMedian(hg, count),
            histogramMedian(hb, count)
        )
    }

    private fun estimateBackgroundNoise95(
        slot: SlotBounds,
        componentId: IntArray,
        detectPixels: IntArray,
        width: Int,
        background: Rgb
    ): Float {
        val histogram = IntArray(256)
        var count = 0

        for (y in slot.minY..slot.maxY) {
            val row = y * width
            for (x in slot.minX..slot.maxX) {
                val i = row + x
                if (componentId[i] != slot.id) continue

                val p = detectPixels[i]
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                val d = colorDistance(r, g, b, background.r, background.g, background.b)
                    .roundToInt()
                    .coerceIn(0, 255)
                histogram[d]++
                count++
            }
        }

        if (count == 0) return 2f
        val target = max(1, (count * 0.95f).roundToInt())
        var seen = 0
        for (i in histogram.indices) {
            seen += histogram[i]
            if (seen >= target) return i.toFloat()
        }
        return 2f
    }

    private fun buildConnectedBackgroundMask(
        slot: SlotBounds,
        componentId: IntArray,
        detectPixels: IntArray,
        width: Int,
        height: Int,
        background: Rgb,
        definiteThreshold: Float,
        floodThreshold: Float
    ): BooleanArray {
        val total = width * height
        val visited = BooleanArray(total)
        val queue = IntArray(slot.width * slot.height)
        var head = 0
        var tail = 0

        for (y in slot.minY..slot.maxY) {
            val row = y * width
            for (x in slot.minX..slot.maxX) {
                val i = row + x
                if (componentId[i] == slot.id) {
                    visited[i] = true
                    queue[tail++] = i
                }
            }
        }

        while (head < tail) {
            val curr = queue[head++]
            val cx = curr % width
            val cy = curr / width

            if (cx > slot.minX) {
                val ni = curr - 1
                if (!visited[ni] && shouldFloodBackground(ni, detectPixels, background, definiteThreshold, floodThreshold)) {
                    visited[ni] = true
                    queue[tail++] = ni
                }
            }
            if (cx < slot.maxX) {
                val ni = curr + 1
                if (!visited[ni] && shouldFloodBackground(ni, detectPixels, background, definiteThreshold, floodThreshold)) {
                    visited[ni] = true
                    queue[tail++] = ni
                }
            }
            if (cy > slot.minY) {
                val ni = curr - width
                if (!visited[ni] && shouldFloodBackground(ni, detectPixels, background, definiteThreshold, floodThreshold)) {
                    visited[ni] = true
                    queue[tail++] = ni
                }
            }
            if (cy < slot.maxY) {
                val ni = curr + width
                if (!visited[ni] && shouldFloodBackground(ni, detectPixels, background, definiteThreshold, floodThreshold)) {
                    visited[ni] = true
                    queue[tail++] = ni
                }
            }
        }

        return visited
    }

    private fun shouldFloodBackground(
        index: Int,
        detectPixels: IntArray,
        background: Rgb,
        definiteThreshold: Float,
        floodThreshold: Float
    ): Boolean {
        val p = detectPixels[index]
        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF
        val distance = colorDistance(r, g, b, background.r, background.g, background.b)

        return distance <= floodThreshold
    }

    private fun histogramMedian(hist: IntArray, count: Int): Int {
        val target = (count + 1) / 2
        var seen = 0
        for (i in hist.indices) {
            seen += hist[i]
            if (seen >= target) return i
        }
        return 255
    }

    private fun percentile(values: List<Int>, fraction: Float): Int {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        val index = ((sorted.size - 1) * fraction.coerceIn(0f, 1f)).roundToInt()
        return sorted[index]
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun unmatteChannel(observed: Int, background: Int, alpha: Float): Int {
        if (alpha <= 0.02f) return 0
        val foreground = (observed - background * (1f - alpha)) / alpha
        return foreground.roundToInt().coerceIn(0, 255)
    }

    private fun isDarkRgb(rgb: Rgb): Boolean = ((rgb.r + rgb.g + rgb.b) / 3f) <= 64f

    private fun distanceFromWhite(r: Int, g: Int, b: Int): Float =
        colorDistance(r, g, b, 255, 255, 255)

    private fun colorDistance(
        r1: Int,
        g1: Int,
        b1: Int,
        r2: Int,
        g2: Int,
        b2: Int
    ): Float {
        val dr = (r1 - r2).toFloat()
        val dg = (g1 - g2).toFloat()
        val db = (b1 - b2).toFloat()
        return sqrt((dr * dr + dg * dg + db * db) / 3f)
    }
}
