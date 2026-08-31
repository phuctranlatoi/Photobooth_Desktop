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

        // UNIFORM_COLOR is analyzed on a lightly box-filtered copy. This is detection-only:
        // the exported frame still uses the original sourcePixels bit-for-bit. The filter
        // suppresses high-resolution Canva/JPEG/PNG micro-noise that is invisible at preview
        // size but would otherwise make a visually flat slot fail pixel-level uniformity tests.
        val uniformAnalysisPixels = buildUniformAnalysisPixels(detectPixels, width, height)

        val passes = listOf(SeedType.TRANSPARENT, SeedType.DARK, SeedType.LIGHT, SeedType.UNIFORM_COLOR)
        var bestSlots = emptyList<SlotBounds>()
        var bestScore = -1f
        var bestComponentIdArray: IntArray? = null
        var bestSlotSeed: BooleanArray? = null
        var bestRadius = 2
        var bestPass: SeedType? = null

        for (pass in passes) {
            val passPixels = if (pass == SeedType.UNIFORM_COLOR) uniformAnalysisPixels else detectPixels
            val slotSeed = buildSeedMask(sourcePixels, passPixels, width, height, pass)

            val radius = max(2, (min(width, height) * 0.0035f).roundToInt()).coerceAtMost(14)
            val erodedSeed = erodeSquareWithIntegralImage(slotSeed, width, height, radius)

            val componentIdArray = IntArray(totalPixels) { -1 }
            val components = if (pass == SeedType.UNIFORM_COLOR) {
                findUniformColorComponents(
                    mask = erodedSeed,
                    componentId = componentIdArray,
                    pixels = passPixels,
                    width = width,
                    height = height
                )
            } else {
                findComponents(erodedSeed, componentIdArray, width, height)
            }

            var candidates = components
                .filter { isPlausibleSlotComponent(it, width, height, radius) }
                .mapNotNull { component ->
                    val fitted = if (pass == SeedType.UNIFORM_COLOR) {
                        fitUniformColorRectangle(
                            component = component,
                            width = width,
                            height = height,
                            radius = radius
                        )
                    } else {
                        fitTrueRectangle(
                            component = component,
                            componentId = componentIdArray,
                            slotSeed = slotSeed,
                            width = width,
                            height = height,
                            radius = radius
                        )
                    }
                    if (fitted == null) null else {
                        val coverage = seedCoverage(fitted, slotSeed, width)
                        if (coverage < 0.42f) {
                            null
                        } else if (pass == SeedType.UNIFORM_COLOR) {
                            expandUniformRectangleToTrueBoundary(
                                initial = fitted,
                                pixels = detectPixels,
                                width = width,
                                height = height,
                                searchRadius = max(3, radius + 3)
                            )
                        } else {
                            fitted
                        }
                    }
                }
                .let { removeNearDuplicateRects(it) }

            // UNIFORM_COLOR is a generic fallback for any flat/slowly-varying slot color.
            // It can also see other smooth areas in the frame, so keep the dominant
            // same-size rectangle family before scoring. This matches the photobooth
            // convention that slots in one template are usually the same size.
            if (pass == SeedType.UNIFORM_COLOR) {
                candidates = selectDominantUniformSlotFamily(candidates)
            }

            val score = scoreCandidateSet(
                candidates = candidates,
                slotSeed = slotSeed,
                width = width,
                totalPixels = totalPixels,
                uniformPass = pass == SeedType.UNIFORM_COLOR
            )
            if (score > bestScore && candidates.isNotEmpty()) {
                bestScore = score
                bestSlots = candidates
                bestComponentIdArray = componentIdArray
                bestSlotSeed = slotSeed
                bestRadius = radius
                bestPass = pass
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
        val selectedPass = bestPass

        for (b in sortedBounds) {
            // Expand the boundary to ensure the blurry/anti-aliased rim is fully processed and removed.
            // If the boundary is too tight, the algorithm skips the fringing pixels entirely.
            val expandedSlot = SlotBounds(
                minX = max(0, b.minX - 6),
                maxX = min(width - 1, b.maxX + 6),
                minY = max(0, b.minY - 6),
                maxY = min(height - 1, b.maxY + 6),
                pixelArea = b.pixelArea,
                id = b.id
            )

            val background = estimateSlotBackground(
                slot = b, // use original 'b' for background estimation to avoid noise from decorations
                componentId = componentId,
                detectPixels = detectPixels,
                width = width
            )

            val noise95 = estimateBackgroundNoise95(
                slot = b, // use original 'b' for background estimation
                componentId = componentId,
                detectPixels = detectPixels,
                width = width,
                background = background
            )

            if (selectedPass == SeedType.UNIFORM_COLOR) {
                applyUniformColorRobustMatte(
                    slot = expandedSlot,
                    sourcePixels = sourcePixels,
                    detectPixels = detectPixels,
                    punchedPixels = punchedPixels,
                    componentId = componentId,
                    width = width,
                    height = height
                )
                continue
            }

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

            // --- STAGE 1: FOREGROUND (OBJECT) RECOGNITION ---
            // We region-grow from the bright cores of the overlapping objects to capture their soft shadows.
            // This prevents the background flood fill from eating into the dark shadows/reflections of the ribbon.
            val foregroundMask = BooleanArray(width * height)
            val fgQueue = IntArray(width * height)
            var fgHead = 0
            var fgTail = 0

            // Seed True Foreground: distinctly bright/different pixels
            val trueFgThreshold = floodThreshold + 5.0f
            // Stop growing when it reaches the pure background
            val fgStopThreshold = clearThreshold

            for (y in b.minY..b.maxY) {
                val row = y * width
                for (x in b.minX..b.maxX) {
                    val i = row + x
                    val dp = detectPixels[i]
                    val r = (dp ushr 16) and 0xFF
                    val gCh = (dp ushr 8) and 0xFF
                    val bCh = dp and 0xFF
                    val distance = colorDistance(r, gCh, bCh, background.r, background.g, background.b)
                    
                    if (distance > trueFgThreshold) {
                        foregroundMask[i] = true
                        fgQueue[fgTail++] = i
                    }
                }
            }

            // Region growing for the object's shadows
            while (fgHead < fgTail) {
                val curr = fgQueue[fgHead++]
                val cx = curr % width
                val cy = curr / width

                if (cx > b.minX) {
                    val ni = curr - 1
                    if (!foregroundMask[ni]) {
                        val dp = detectPixels[ni]
                        val r = (dp ushr 16) and 0xFF
                        val gCh = (dp ushr 8) and 0xFF
                        val bCh = dp and 0xFF
                        val distance = colorDistance(r, gCh, bCh, background.r, background.g, background.b)
                        if (distance > fgStopThreshold) {
                            foregroundMask[ni] = true
                            fgQueue[fgTail++] = ni
                        }
                    }
                }
                if (cx < b.maxX) {
                    val ni = curr + 1
                    if (!foregroundMask[ni]) {
                        val dp = detectPixels[ni]
                        val r = (dp ushr 16) and 0xFF
                        val gCh = (dp ushr 8) and 0xFF
                        val bCh = dp and 0xFF
                        val distance = colorDistance(r, gCh, bCh, background.r, background.g, background.b)
                        if (distance > fgStopThreshold) {
                            foregroundMask[ni] = true
                            fgQueue[fgTail++] = ni
                        }
                    }
                }
                if (cy > b.minY) {
                    val ni = curr - width
                    if (!foregroundMask[ni]) {
                        val dp = detectPixels[ni]
                        val r = (dp ushr 16) and 0xFF
                        val gCh = (dp ushr 8) and 0xFF
                        val bCh = dp and 0xFF
                        val distance = colorDistance(r, gCh, bCh, background.r, background.g, background.b)
                        if (distance > fgStopThreshold) {
                            foregroundMask[ni] = true
                            fgQueue[fgTail++] = ni
                        }
                    }
                }
                if (cy < b.maxY) {
                    val ni = curr + width
                    if (!foregroundMask[ni]) {
                        val dp = detectPixels[ni]
                        val r = (dp ushr 16) and 0xFF
                        val gCh = (dp ushr 8) and 0xFF
                        val bCh = dp and 0xFF
                        val distance = colorDistance(r, gCh, bCh, background.r, background.g, background.b)
                        if (distance > fgStopThreshold) {
                            foregroundMask[ni] = true
                            fgQueue[fgTail++] = ni
                        }
                    }
                }
            }
            // --- END STAGE 1 ---

            // --- HYBRID REFINEMENT: un-protect tiny near-white foreground islands ---
            // Stage 1 flags a pixel as foreground purely because it's numerically far
            // from the estimated slot background — that's true for a genuine
            // overlapping decoration (silver bow, colored ribbon, etc.), but it's
            // EQUALLY true for a stray white halo/artifact pixel sitting on a
            // non-white background. Both get swept into foregroundMask and both get
            // fully protected by Stage 2, so the artifact survives untouched.
            // The one reliable signal that tells them apart: real decoration is a
            // sizeable connected shape; a stray artifact is a tiny, near-white speck.
            // This pass only strips protection from foreground islands that are BOTH
            // small AND close to white, entirely inside this slot's own box — a real
            // decoration (large, and/or clearly colored) is never affected.
            run {
                val fgComponentId = IntArray(width * height) { -1 }
                val fgQueue2 = IntArray(width * height)
                var nextFgId = 0

                for (y in b.minY..b.maxY) {
                    val row = y * width
                    for (x in b.minX..b.maxX) {
                        val start = row + x
                        if (!foregroundMask[start] || fgComponentId[start] != -1) continue

                        var head2 = 0
                        var tail2 = 0
                        fgQueue2[tail2++] = start
                        fgComponentId[start] = nextFgId
                        val members = mutableListOf<Int>()

                        while (head2 < tail2) {
                            val curr = fgQueue2[head2++]
                            members += curr
                            val cx = curr % width
                            val cy = curr / width

                            if (cx > b.minX) {
                                val ni = curr - 1
                                if (foregroundMask[ni] && fgComponentId[ni] == -1) {
                                    fgComponentId[ni] = nextFgId; fgQueue2[tail2++] = ni
                                }
                            }
                            if (cx < b.maxX) {
                                val ni = curr + 1
                                if (foregroundMask[ni] && fgComponentId[ni] == -1) {
                                    fgComponentId[ni] = nextFgId; fgQueue2[tail2++] = ni
                                }
                            }
                            if (cy > b.minY) {
                                val ni = curr - width
                                if (foregroundMask[ni] && fgComponentId[ni] == -1) {
                                    fgComponentId[ni] = nextFgId; fgQueue2[tail2++] = ni
                                }
                            }
                            if (cy < b.maxY) {
                                val ni = curr + width
                                if (foregroundMask[ni] && fgComponentId[ni] == -1) {
                                    fgComponentId[ni] = nextFgId; fgQueue2[tail2++] = ni
                                }
                            }
                        }

                        // Real decoration shapes are always far bigger than this cap.
                        val tinyIslandCap = max(10, ((b.width.toLong() * b.height.toLong()) * 0.0006).toInt())
                        if (members.size <= tinyIslandCap) {
                            var sumDistToWhite = 0f
                            for (i in members) {
                                val dp = detectPixels[i]
                                sumDistToWhite += colorDistance(
                                    (dp ushr 16) and 0xFF, (dp ushr 8) and 0xFF, dp and 0xFF,
                                    255, 255, 255
                                )
                            }
                            val avgDistToWhite = sumDistToWhite / members.size
                            if (avgDistToWhite <= 22f) {
                                // Close to white and tiny -> stray artifact, not decoration.
                                // Un-protect so Stage 2/3 can evaluate and clean it normally.
                                for (i in members) foregroundMask[i] = false
                            }
                        }

                        nextFgId++
                    }
                }
            }
            // --- END HYBRID REFINEMENT ---

            // --- STAGE 2: BACKGROUND REMOVAL ---
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
                    if (foregroundMask[i]) {
                        // Protect the recognized object and its shadows from any background removal
                        continue
                    }
                    if (!backgroundMask[i]) {
                        // Decorative/detail pixel isolated from the main background.
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
            // --- STAGE 3: TOPOLOGICAL WHITE FRINGE REMOVAL ---
            // Overlapping decorations pasted from other sources may have a white anti-aliasing fringe.
            // We only want to remove white pixels that are ON THE BOUNDARY of the punched background (the true fringe),
            // and PRESERVE white highlights that are safely INSIDE the object.
            
            val fringeQueue = IntArray(width * height)
            var fringeHead = 0
            var fringeTail = 0
            val fringeMask = BooleanArray(width * height)

            // Step 1: Find white pixels directly adjacent to the punched background
            for (y in b.minY..b.maxY) {
                val row = y * width
                for (x in b.minX..b.maxX) {
                    val i = row + x
                    if (punchedPixels[i] == 0x00000000) continue
                    
                    val p = punchedPixels[i]
                    val r = (p ushr 16) and 0xFF
                    val gCh = (p ushr 8) and 0xFF
                    val bCh = p and 0xFF
                    val distToWhite = colorDistance(r, gCh, bCh, 255, 255, 255)
                    
                    if (distToWhite <= 28.0f) {
                        // Check if it touches a punched pixel
                        var touchesPunched = false
                        if (x > b.minX && punchedPixels[i - 1] == 0x00000000) touchesPunched = true
                        if (x < b.maxX && punchedPixels[i + 1] == 0x00000000) touchesPunched = true
                        if (y > b.minY && punchedPixels[i - width] == 0x00000000) touchesPunched = true
                        if (y < b.maxY && punchedPixels[i + width] == 0x00000000) touchesPunched = true
                        
                        if (touchesPunched) {
                            fringeMask[i] = true
                            fringeQueue[fringeTail++] = i
                        }
                    }
                }
            }

            // Step 2: Flood fill to capture the entire width of the contiguous white fringe
            while (fringeHead < fringeTail) {
                val curr = fringeQueue[fringeHead++]
                val cx = curr % width
                val cy = curr / width

                if (cx > b.minX) {
                    val ni = curr - 1
                    if (!fringeMask[ni] && punchedPixels[ni] != 0x00000000) {
                        val p = punchedPixels[ni]
                        val distance = colorDistance((p ushr 16) and 0xFF, (p ushr 8) and 0xFF, p and 0xFF, 255, 255, 255)
                        if (distance <= 28.0f) {
                            fringeMask[ni] = true
                            fringeQueue[fringeTail++] = ni
                        }
                    }
                }
                if (cx < b.maxX) {
                    val ni = curr + 1
                    if (!fringeMask[ni] && punchedPixels[ni] != 0x00000000) {
                        val p = punchedPixels[ni]
                        val distance = colorDistance((p ushr 16) and 0xFF, (p ushr 8) and 0xFF, p and 0xFF, 255, 255, 255)
                        if (distance <= 28.0f) {
                            fringeMask[ni] = true
                            fringeQueue[fringeTail++] = ni
                        }
                    }
                }
                if (cy > b.minY) {
                    val ni = curr - width
                    if (!fringeMask[ni] && punchedPixels[ni] != 0x00000000) {
                        val p = punchedPixels[ni]
                        val distance = colorDistance((p ushr 16) and 0xFF, (p ushr 8) and 0xFF, p and 0xFF, 255, 255, 255)
                        if (distance <= 28.0f) {
                            fringeMask[ni] = true
                            fringeQueue[fringeTail++] = ni
                        }
                    }
                }
                if (cy < b.maxY) {
                    val ni = curr + width
                    if (!fringeMask[ni] && punchedPixels[ni] != 0x00000000) {
                        val p = punchedPixels[ni]
                        val distance = colorDistance((p ushr 16) and 0xFF, (p ushr 8) and 0xFF, p and 0xFF, 255, 255, 255)
                        if (distance <= 28.0f) {
                            fringeMask[ni] = true
                            fringeQueue[fringeTail++] = ni
                        }
                    }
                }
            }

            // Step 3: Punch ONLY the topologically verified fringe pixels
            for (y in b.minY..b.maxY) {
                val row = y * width
                for (x in b.minX..b.maxX) {
                    val i = row + x
                    if (fringeMask[i]) {
                        val p = punchedPixels[i]
                        val r = (p ushr 16) and 0xFF
                        val gCh = (p ushr 8) and 0xFF
                        val bCh = p and 0xFF
                        val distToWhite = colorDistance(r, gCh, bCh, 255, 255, 255)
                        
                        if (distToWhite <= 12.0f) {
                            punchedPixels[i] = 0x00000000
                        } else {
                            val alpha = (p ushr 24) and 0xFF
                            val keepRatio = smoothstep(12.0f, 28.0f, distToWhite)
                            val newA = (alpha * keepRatio).roundToInt().coerceIn(0, 255)
                            if (newA == 0) {
                                punchedPixels[i] = 0x00000000
                            } else {
                                punchedPixels[i] = (newA shl 24) or (p and 0x00FFFFFF)
                            }
                        }
                    }
                }
            }
            // --- END STAGE 3 ---

            // --- STAGE 4: PIXEL-EXACT HYSTERESIS MATTE ---
            // Final authoritative cleanup, strictly INSIDE this detected slot only.
            // Purpose:
            // 1) remove every remaining background pixel, including pure-white / pure-black residue,
            // 2) preserve decoration pixels bit-for-bit from sourcePixels,
            // 3) recover very bright decoration highlights that are numerically almost identical
            //    to the slot background by bridging only SHORT gaps enclosed by foreground.
            applyPixelExactHysteresisMatte(
                slot = expandedSlot,
                sourcePixels = sourcePixels,
                detectPixels = detectPixels,
                punchedPixels = punchedPixels,
                width = width,
                height = height,
                background = background,
                noise95 = noise95
            )
            // --- END STAGE 4 ---
        }

        val punched = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        punched.setRGB(0, 0, width, height, punchedPixels, 0, width)
        
        // Find QR Code Slot
        val qrSlot = slots.firstOrNull { slot ->
            val ratio = slot.width / slot.height
            ratio in 0.9f..1.1f && slot.width < 0.35f
        }
        val photoSlots = if (qrSlot != null) slots.filter { it != qrSlot } else slots

        return DetectionResult(width, height, photoSlots, warnings, punched, qrSlot)
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


    /**
     * Detection-only box filter for the generic uniform-color pass.
     *
     * Native template files can contain fine export noise/texture that disappears when
     * the UI scales the image down. Judging "uniform" on raw pixels therefore gives a
     * false negative. This integral-image box filter removes only high-frequency noise;
     * it never modifies the returned/punched image.
     */
    private fun buildUniformAnalysisPixels(
        pixels: IntArray,
        width: Int,
        height: Int
    ): IntArray {
        val radius = max(1, (min(width, height) * 0.0025f).roundToInt()).coerceAtMost(5)
        if (radius <= 0) return pixels.copyOf()

        val stride = width + 1
        val sumR = LongArray((width + 1) * (height + 1))
        val sumG = LongArray((width + 1) * (height + 1))
        val sumB = LongArray((width + 1) * (height + 1))

        for (y in 0 until height) {
            var rr = 0L
            var gg = 0L
            var bb = 0L
            for (x in 0 until width) {
                val p = pixels[y * width + x]
                rr += ((p ushr 16) and 0xFF).toLong()
                gg += ((p ushr 8) and 0xFF).toLong()
                bb += (p and 0xFF).toLong()
                val dst = (y + 1) * stride + (x + 1)
                sumR[dst] = sumR[y * stride + (x + 1)] + rr
                sumG[dst] = sumG[y * stride + (x + 1)] + gg
                sumB[dst] = sumB[y * stride + (x + 1)] + bb
            }
        }

        fun rectSum(sum: LongArray, x0: Int, y0: Int, x1: Int, y1: Int): Long {
            val ax = x0
            val ay = y0
            val bx = x1 + 1
            val by = y1 + 1
            return sum[by * stride + bx] - sum[ay * stride + bx] -
                sum[by * stride + ax] + sum[ay * stride + ax]
        }

        val out = IntArray(width * height)
        for (y in 0 until height) {
            val y0 = max(0, y - radius)
            val y1 = min(height - 1, y + radius)
            for (x in 0 until width) {
                val x0 = max(0, x - radius)
                val x1 = min(width - 1, x + radius)
                val count = (x1 - x0 + 1) * (y1 - y0 + 1)
                val r = (rectSum(sumR, x0, y0, x1, y1) / count).toInt().coerceIn(0, 255)
                val g = (rectSum(sumG, x0, y0, x1, y1) / count).toInt().coerceIn(0, 255)
                val b = (rectSum(sumB, x0, y0, x1, y1) / count).toInt().coerceIn(0, 255)
                out[y * width + x] = (255 shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }

    enum class SeedType {
        TRANSPARENT, LIGHT, DARK, UNIFORM_COLOR
    }

    /**
     * Build a seed mask for slot discovery.
     *
     * TRANSPARENT / LIGHT / DARK keep the old fast paths exactly as before.
     * UNIFORM_COLOR is color-agnostic: it asks only whether a pixel sits inside a
     * locally smooth, opaque region. It does NOT care if that region is orange,
     * pink, blue, purple, beige, etc.
     */
    private fun buildSeedMask(
        sourcePixels: IntArray,
        detectPixels: IntArray,
        width: Int,
        height: Int,
        type: SeedType
    ): BooleanArray {
        val totalPixels = sourcePixels.size
        val mask = BooleanArray(totalPixels)

        if (type == SeedType.UNIFORM_COLOR) {
            return buildUniformColorSeedMask(sourcePixels, detectPixels, width, height)
        }

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
                    if (avg >= 252f && range <= 10 && distanceFromWhite(r, gCh, b) <= 6f) {
                        mask[i] = true
                    }
                } else if (type == SeedType.DARK) {
                    val darkNeutral = avg <= 28f && range <= 20
                    val darkGrayNeutral = avg <= 42f && range <= 10
                    if (darkNeutral || darkGrayNeutral) mask[i] = true
                }
            }
        }
        return mask
    }

    /**
     * Detect interiors that are locally color-uniform WITHOUT assuming any hue.
     *
     * The metric is deliberately local rather than "same exact RGB everywhere":
     * a Canva/PNG slot may contain a tiny amount of compression, antialiasing, or
     * a very gentle gradient. We therefore measure the color difference to nearby
     * pixels and keep a pixel when most of its neighborhood is very similar.
     *
     * Decorations and frame textures naturally have higher local variation, which
     * cuts holes in this mask; connected-component + rectangle fitting later still
     * recovers the complete rectangular slot.
     */
    private fun buildUniformColorSeedMask(
        sourcePixels: IntArray,
        detectPixels: IntArray,
        width: Int,
        height: Int
    ): BooleanArray {
        val mask = BooleanArray(width * height)
        if (width < 5 || height < 5) return mask

        // A clean PNG is often 0..1 distance. 3.25 still accepts very gentle gradients
        // while rejecting most textured scrapbook/background detail.
        val nearTolerance = 5.50f
        val farTolerance = 9.00f

        for (y in 2 until height - 2) {
            val row = y * width
            for (x in 2 until width - 2) {
                val i = row + x
                val srcA = (sourcePixels[i] ushr 24) and 0xFF
                if (srcA < 245) continue

                val p = detectPixels[i]
                val cr = (p ushr 16) and 0xFF
                val cg = (p ushr 8) and 0xFF
                val cb = p and 0xFF

                // Radius-1: require a clear majority of immediate neighbors to match.
                var nearGood = 0
                var nearSeen = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val np = detectPixels[(y + dy) * width + (x + dx)]
                        val nr = (np ushr 16) and 0xFF
                        val ng = (np ushr 8) and 0xFF
                        val nb = np and 0xFF
                        if (colorDistance(cr, cg, cb, nr, ng, nb) <= nearTolerance) nearGood++
                        nearSeen++
                    }
                }
                if (nearGood < 5 || nearSeen < 8) continue

                // Radius-2 cardinal samples prevent a noisy/finely textured region from
                // passing only because its immediately adjacent pixels happen to match.
                var farGood = 0
                val offsets = intArrayOf(-2, 0, 2, 0, 0, -2, 0, 2)
                var k = 0
                while (k < offsets.size) {
                    val nx = x + offsets[k]
                    val ny = y + offsets[k + 1]
                    val np = detectPixels[ny * width + nx]
                    val nr = (np ushr 16) and 0xFF
                    val ng = (np ushr 8) and 0xFF
                    val nb = np and 0xFF
                    if (colorDistance(cr, cg, cb, nr, ng, nb) <= farTolerance) farGood++
                    k += 2
                }
                if (farGood < 3) continue

                mask[i] = true
            }
        }

        return mask
    }

    /**
     * Uniform-color discovery may find several smooth rectangles. Photobooth layouts
     * overwhelmingly use same-size photo windows, so retain the largest mutually
     * consistent width/height family. If there is no family of at least 2, keep all
     * candidates so a one-slot freeform template still works.
     */
    private fun selectDominantUniformSlotFamily(input: List<SlotBounds>): List<SlotBounds> {
        if (input.size <= 1) return input

        var best: List<SlotBounds> = emptyList()
        var bestArea = -1L

        for (anchor in input) {
            val wTol = max(8f, anchor.width * 0.22f)
            val hTol = max(8f, anchor.height * 0.22f)
            val family = input.filter {
                abs(it.width - anchor.width) <= wTol &&
                    abs(it.height - anchor.height) <= hTol
            }
            val area = family.sumOf { it.width.toLong() * it.height.toLong() }
            if (family.size > best.size || (family.size == best.size && area > bestArea)) {
                best = family
                bestArea = area
            }
        }

        return if (best.size >= 2) best else input
    }

    /**
     * Score a pass by slot count, rectangular seed coverage and same-size consistency.
     * A small penalty makes the legacy TRANSPARENT/LIGHT/DARK passes win ties, so
     * existing templates keep their old behavior and UNIFORM_COLOR acts as a safe
     * generic fallback.
     */
    private fun scoreCandidateSet(
        candidates: List<SlotBounds>,
        slotSeed: BooleanArray,
        width: Int,
        totalPixels: Int,
        uniformPass: Boolean
    ): Float {
        if (candidates.isEmpty()) return -1f

        val countScore = candidates.size * 1000f
        val coverage = candidates.map { seedCoverage(it, slotSeed, width) }.average().toFloat()
        val coverageScore = coverage * 220f
        val areaScore = candidates.sumOf {
            (it.width.toDouble() * it.height.toDouble()) / totalPixels.toDouble()
        }.toFloat() * 120f

        val consistencyScore = if (candidates.size >= 2) {
            val ws = candidates.map { it.width }.sorted()
            val hs = candidates.map { it.height }.sorted()
            val mw = ws[ws.size / 2].toFloat().coerceAtLeast(1f)
            val mh = hs[hs.size / 2].toFloat().coerceAtLeast(1f)
            val meanDeviation = candidates.map {
                abs(it.width - mw) / mw + abs(it.height - mh) / mh
            }.average().toFloat() / 2f
            (1f - meanDeviation.coerceIn(0f, 1f)) * 260f
        } else 0f

        val uniformPenalty = if (uniformPass) 40f else 0f
        return countScore + coverageScore + areaScore + consistencyScore - uniformPenalty
    }

    /**
     * Connected components for the generic uniform-color pass.
     *
     * A plain Boolean "smooth pixel" mask is not enough: two different flat colors
     * can be connected by a gentle antialiased transition. This region-grower adds
     * a SECOND condition: every accepted pixel must remain close to the component's
     * seed color. Therefore an orange slot cannot leak into a mint frame even when
     * both are individually smooth.
     */
    private fun findUniformColorComponents(
        mask: BooleanArray,
        componentId: IntArray,
        pixels: IntArray,
        width: Int,
        height: Int
    ): List<Component> {
        val total = width * height
        val queue = IntArray(total)
        val result = mutableListOf<Component>()
        var nextId = 0

        // RMS RGB distance. This allows small export noise / gentle gradients while
        // keeping clearly different frame and slot colors in separate components.
        // Do NOT lock a component to its very first pixel color. A visually uniform
        // slot may contain a slow gradient whose opposite corner differs by > 18 RGB
        // levels. Only local smoothness matters here; the seed mask already blocks
        // high-contrast boundaries.
        val stepColorTolerance = 10.0f

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
                    val cp = pixels[curr]
                    val cr = (cp ushr 16) and 0xFF
                    val cg = (cp ushr 8) and 0xFF
                    val cb = cp and 0xFF

                    area++
                    if (cx < minX) minX = cx
                    if (cx > maxX) maxX = cx
                    if (cy < minY) minY = cy
                    if (cy > maxY) maxY = cy

                    fun tryAdd(n: Int) {
                        if (!mask[n] || componentId[n] != -1) return
                        val np = pixels[n]
                        val nr = (np ushr 16) and 0xFF
                        val ng = (np ushr 8) and 0xFF
                        val nb = np and 0xFF

                        // Also require a small local step. This blocks a long smooth ramp from
                        // slowly walking from the slot color into a different frame color.
                        val stepDistance = colorDistance(nr, ng, nb, cr, cg, cb)
                        if (stepDistance > stepColorTolerance) return

                        componentId[n] = nextId
                        queue[tail++] = n
                    }

                    if (cx > 0) tryAdd(curr - 1)
                    if (cx + 1 < width) tryAdd(curr + 1)
                    if (cy > 0) tryAdd(curr - width)
                    if (cy + 1 < height) tryAdd(curr + width)
                }

                result += Component(nextId, minX, maxX, minY, maxY, area)
                nextId++
            }
        }
        return result
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

    /**
     * Dedicated rectangle recovery for UNIFORM_COLOR.
     *
     * The uniform seed is already a clean interior region. The morphology erosion
     * shrinks every true slot edge by exactly [radius], so the safest reconstruction
     * is to add that radius back. We intentionally DO NOT run the legacy edge-refine
     * search here because that search can walk into a smooth outer frame of another
     * color and make the slot too large.
     */
    private fun fitUniformColorRectangle(
        component: Component,
        width: Int,
        height: Int,
        radius: Int
    ): SlotBounds? {
        val left = (component.minX - radius).coerceIn(0, width - 1)
        val right = (component.maxX + radius).coerceIn(0, width - 1)
        val top = (component.minY - radius).coerceIn(0, height - 1)
        val bottom = (component.maxY + radius).coerceIn(0, height - 1)

        if (right <= left || bottom <= top) return null
        val rectW = right - left + 1
        val rectH = bottom - top + 1
        if (rectW < width * 0.10f || rectH < height * 0.10f) return null

        return SlotBounds(
            minX = left,
            maxX = right,
            minY = top,
            maxY = bottom,
            pixelArea = component.area,
            id = component.id
        )
    }

    /**
     * Recover the real outer boundary of a generic colored slot after local-uniformity
     * analysis + morphology have shrunk it by a few pixels.
     *
     * We scan outward from the detected interior and ask whether each candidate row/column
     * is still mostly the dominant slot color.  Only READS happen outside [initial]; the
     * returned rectangle is then used as the authoritative write boundary.  Decorations may
     * cover part of an edge, so the decision uses a robust majority instead of requiring
     * every pixel to match.
     */
    private fun expandUniformRectangleToTrueBoundary(
        initial: SlotBounds,
        pixels: IntArray,
        width: Int,
        height: Int,
        searchRadius: Int
    ): SlotBounds {
        if (initial.width <= 0 || initial.height <= 0) return initial

        // Robust RGB center from the middle of the already-safe interior.
        val xInset = max(2, (initial.width * 0.12f).roundToInt())
        val yInset = max(2, (initial.height * 0.12f).roundToInt())
        val sx0 = min(initial.maxX, initial.minX + xInset)
        val sx1 = max(initial.minX, initial.maxX - xInset)
        val sy0 = min(initial.maxY, initial.minY + yInset)
        val sy1 = max(initial.minY, initial.maxY - yInset)

        val hr = IntArray(256)
        val hg = IntArray(256)
        val hb = IntArray(256)
        var count = 0
        for (y in sy0..sy1) {
            val row = y * width
            for (x in sx0..sx1) {
                val p = pixels[row + x]
                hr[(p ushr 16) and 0xFF]++
                hg[(p ushr 8) and 0xFF]++
                hb[p and 0xFF]++
                count++
            }
        }
        if (count == 0) return initial

        val cr = histogramMedian(hr, count)
        val cg = histogramMedian(hg, count)
        val cb = histogramMedian(hb, count)

        // Learn real background variation from the safe interior.
        val dh = IntArray(256)
        var dCount = 0
        for (y in sy0..sy1) {
            val row = y * width
            for (x in sx0..sx1) {
                val p = pixels[row + x]
                val d = colorDistance(
                    (p ushr 16) and 0xFF,
                    (p ushr 8) and 0xFF,
                    p and 0xFF,
                    cr, cg, cb
                ).roundToInt().coerceIn(0, 255)
                dh[d]++
                dCount++
            }
        }

        fun dPercentile(q: Float): Float {
            val target = max(1, (dCount * q).roundToInt())
            var seen = 0
            for (d in dh.indices) {
                seen += dh[d]
                if (seen >= target) return d.toFloat()
            }
            return 255f
        }

        val edgeColorThreshold = max(10f, dPercentile(0.92f) + 8f).coerceAtMost(48f)

        fun rowMedianDistance(y: Int, left: Int, right: Int): Float {
            if (y !in 0 until height || right < left) return 255f
            val inset = max(1, ((right - left + 1) * 0.06f).roundToInt())
            val x0 = (left + inset).coerceAtMost(right)
            val x1 = (right - inset).coerceAtLeast(left)
            val values = FloatArray(max(1, x1 - x0 + 1))
            var n = 0
            val row = y * width
            for (x in x0..x1) {
                val p = pixels[row + x]
                values[n++] = colorDistance(
                    (p ushr 16) and 0xFF,
                    (p ushr 8) and 0xFF,
                    p and 0xFF,
                    cr, cg, cb
                )
            }
            if (n == 0) return 255f
            java.util.Arrays.sort(values, 0, n)
            return values[n / 2]
        }

        fun colMedianDistance(x: Int, top: Int, bottom: Int): Float {
            if (x !in 0 until width || bottom < top) return 255f
            val inset = max(1, ((bottom - top + 1) * 0.06f).roundToInt())
            val y0 = (top + inset).coerceAtMost(bottom)
            val y1 = (bottom - inset).coerceAtLeast(top)
            val values = FloatArray(max(1, y1 - y0 + 1))
            var n = 0
            for (y in y0..y1) {
                val p = pixels[y * width + x]
                values[n++] = colorDistance(
                    (p ushr 16) and 0xFF,
                    (p ushr 8) and 0xFF,
                    p and 0xFF,
                    cr, cg, cb
                )
            }
            if (n == 0) return 255f
            java.util.Arrays.sort(values, 0, n)
            return values[n / 2]
        }

        // A PNG edge often has one anti-aliased transition line whose RGB is neither the
        // slot color nor the outer frame color.  Absolute thresholding leaves this exact
        // line behind.  We therefore include ONE transition line when the following line
        // makes a large color-distance jump (slot -> AA edge -> frame).
        fun shouldIncludeTransition(current: Float, next: Float): Boolean {
            val transitionMax = max(52f, edgeColorThreshold * 3.6f)
            val jump = next - current
            return current <= transitionMax &&
                next >= max(current * 1.85f, current + 28f) &&
                jump >= 28f
        }

        var left = initial.minX
        var right = initial.maxX
        var top = initial.minY
        var bottom = initial.maxY

        fun expandLeft() {
            for (step in 1..searchRadius) {
                val x = initial.minX - step
                if (x < 0) break
                val score = colMedianDistance(x, top, bottom)
                if (score <= edgeColorThreshold) {
                    left = x
                    continue
                }
                val nextX = x - 1
                val nextScore = if (nextX >= 0) colMedianDistance(nextX, top, bottom) else 255f
                if (shouldIncludeTransition(score, nextScore)) left = x
                break
            }
        }

        fun expandRight() {
            for (step in 1..searchRadius) {
                val x = initial.maxX + step
                if (x >= width) break
                val score = colMedianDistance(x, top, bottom)
                if (score <= edgeColorThreshold) {
                    right = x
                    continue
                }
                val nextX = x + 1
                val nextScore = if (nextX < width) colMedianDistance(nextX, top, bottom) else 255f
                if (shouldIncludeTransition(score, nextScore)) right = x
                break
            }
        }

        fun expandTop() {
            for (step in 1..searchRadius) {
                val y = initial.minY - step
                if (y < 0) break
                val score = rowMedianDistance(y, left, right)
                if (score <= edgeColorThreshold) {
                    top = y
                    continue
                }
                val nextY = y - 1
                val nextScore = if (nextY >= 0) rowMedianDistance(nextY, left, right) else 255f
                if (shouldIncludeTransition(score, nextScore)) top = y
                break
            }
        }

        fun expandBottom() {
            for (step in 1..searchRadius) {
                val y = initial.maxY + step
                if (y >= height) break
                val score = rowMedianDistance(y, left, right)
                if (score <= edgeColorThreshold) {
                    bottom = y
                    continue
                }
                val nextY = y + 1
                val nextScore = if (nextY < height) rowMedianDistance(nextY, left, right) else 255f
                if (shouldIncludeTransition(score, nextScore)) bottom = y
                break
            }
        }

        expandLeft()
        expandRight()
        expandTop()
        expandBottom()

        return SlotBounds(
            minX = left,
            maxX = right,
            minY = top,
            maxY = bottom,
            pixelArea = initial.pixelArea,
            id = initial.id
        )
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

        // Expand by 1 pixel outwards to ensure the red box tightly covers anti-aliased edges
        left = max(0, left - 1)
        right = min(width - 1, right + 1)
        top = max(0, top - 1)
        bottom = min(height - 1, bottom + 1)

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
        val step = if (searchRight) 1 else -1
        var maxScore = -1

        // Find the absolute maximum score in the search window
        var x = if (searchRight) minCandidate else maxCandidate
        while (if (searchRight) x <= maxCandidate else x >= minCandidate) {
            var score = 0
            for (y in y0..y1) {
                if (mask[y * width + x]) score++
            }
            if (score > maxScore) maxScore = score
            x += step
        }
        
        // Accept the outermost column that has at least 85% of the max score
        val threshold = (maxScore * 0.85f).toInt()
        x = if (searchRight) minCandidate else maxCandidate
        while (if (searchRight) x <= maxCandidate else x >= minCandidate) {
            var score = 0
            for (y in y0..y1) {
                if (mask[y * width + x]) score++
            }
            if (score >= threshold) return x
            x += step
        }
        return if (searchRight) maxCandidate else minCandidate
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
        val step = if (searchDown) 1 else -1
        var maxScore = -1

        var y = if (searchDown) minCandidate else maxCandidate
        while (if (searchDown) y <= maxCandidate else y >= minCandidate) {
            var score = 0
            val row = y * width
            for (x in x0..x1) {
                if (mask[row + x]) score++
            }
            if (score > maxScore) maxScore = score
            y += step
        }
        
        val threshold = (maxScore * 0.85f).toInt()
        y = if (searchDown) minCandidate else maxCandidate
        while (if (searchDown) y <= maxCandidate else y >= minCandidate) {
            var score = 0
            val row = y * width
            for (x in x0..x1) {
                if (mask[row + x]) score++
            }
            if (score >= threshold) return y
            y += step
        }
        return if (searchDown) maxCandidate else minCandidate
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

        val rowCenters = clusterCenters(sorted.map { it.centerY.toFloat() }, max(20f, medianH * 0.15f)).sorted()
        val colCenters = clusterCenters(sorted.map { it.centerX.toFloat() }, max(20f, medianW * 0.15f)).sorted()
        
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

        var effectiveW = medianW
        if (cols == 1) {
            val padL = refinedXStarts[0]
            val padR = width - (refinedXStarts[0] + medianW)
            
            // Only fix obvious bleeding errors, but DO NOT forcibly center asymmetric layouts.
            if (padR < -10 && padL > 0) {
                effectiveW = width - padL
            } else if (padL < -10 && padR > 0) {
                effectiveW = width - padR
                (refinedXStarts as MutableList)[0] = 0
            }
        }

        val effectiveH = if (rows == 1) {
            val padT = refinedYStarts[0]
            val padB = height - (refinedYStarts[0] + medianH)
            if (padB < -10 && padT > 0) {
                height - padT
            } else if (padT < -10 && padB > 0) {
                val newH = height - padB
                (refinedYStarts as MutableList)[0] = 0
                newH
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


    /**
     * Pixel-exact final matte.
     *
     * This is deliberately a FINAL pass, so the existing Stage 1/2/3 logic is left intact.
     * It does not expand the slot rectangle and never writes outside slot.minX..maxX / minY..maxY.
     *
     * Algorithm:
     * - strong/weak color-distance hysteresis (Canny-style segmentation),
     * - keep only weak regions connected to strong decoration seeds,
     * - bridge only short enclosed horizontal/vertical background gaps (white highlights),
     * - fill only tiny enclosed holes,
     * - every non-protected pixel inside the slot becomes transparent,
     * - every protected pixel is restored EXACTLY from sourcePixels.
     */
    private fun applyPixelExactHysteresisMatte(
        slot: SlotBounds,
        sourcePixels: IntArray,
        detectPixels: IntArray,
        punchedPixels: IntArray,
        width: Int,
        height: Int,
        background: Rgb,
        noise95: Float
    ) {
        val sw = slot.width
        val sh = slot.height
        val area = sw * sh
        if (sw <= 0 || sh <= 0 || area <= 0) return

        // Sensitive enough to notice 1 RGB-level difference on a clean PNG,
        // but automatically rises above real background noise on imperfect files.
        val weakThreshold = max(0.65f, noise95 + 0.35f).coerceAtMost(5.0f)
        val strongThreshold = max(5.0f, noise95 + 4.0f).coerceAtMost(18.0f)

        val weak = BooleanArray(area)
        val strong = BooleanArray(area)

        fun localIndex(x: Int, y: Int): Int = (y - slot.minY) * sw + (x - slot.minX)

        // Snapshot of the Stage 1/2/3 result. Stage 4 is intentionally aggressive,
        // so this snapshot lets us restore only tiny notches that Stage 4 creates
        // inside already-preserved decoration. It is local to the detected slot;
        // nothing outside slot bounds can be restored or modified here.
        val preStage4Opaque = BooleanArray(area)
        for (y in slot.minY..slot.maxY) {
            val row = y * width
            for (x in slot.minX..slot.maxX) {
                val gi = row + x
                val li = localIndex(x, y)
                preStage4Opaque[li] = ((punchedPixels[gi] ushr 24) and 0xFF) > 0
            }
        }

        for (y in slot.minY..slot.maxY) {
            val row = y * width
            for (x in slot.minX..slot.maxX) {
                val gi = row + x
                val li = localIndex(x, y)
                val src = sourcePixels[gi]
                val srcA = (src ushr 24) and 0xFF
                if (srcA == 0) continue

                val p = detectPixels[gi]
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                val d = colorDistance(r, g, b, background.r, background.g, background.b)

                // Semi-transparent original edges are useful evidence of decoration.
                val originalAlphaEdge = srcA in 1..251 && d > 0.15f
                weak[li] = d > weakThreshold || originalAlphaEdge
                strong[li] = d > strongThreshold || (srcA in 1..230 && d > weakThreshold)
            }
        }

        // 8-connected hysteresis: keep a weak region only if it contains strong foreground.
        val component = IntArray(area) { -1 }
        val queue = IntArray(area)
        val protect = BooleanArray(area)
        var componentId = 0

        for (start in 0 until area) {
            if (!weak[start] || component[start] != -1) continue

            var head = 0
            var tail = 0
            var containsStrong = false
            queue[tail++] = start
            component[start] = componentId

            while (head < tail) {
                val curr = queue[head++]
                if (strong[curr]) containsStrong = true
                val cx = curr % sw
                val cy = curr / sw

                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = cx + dx
                        val ny = cy + dy
                        if (nx !in 0 until sw || ny !in 0 until sh) continue
                        val ni = ny * sw + nx
                        if (weak[ni] && component[ni] == -1) {
                            component[ni] = componentId
                            queue[tail++] = ni
                        }
                    }
                }
            }

            if (containsStrong) {
                for (i in 0 until tail) protect[queue[i]] = true
            }
            componentId++
        }

        // Pure white / pure black highlights can be mathematically identical to the slot color.
        // We only restore them when they form a SHORT gap bounded by decoration on both sides.
        // This is directional morphological reconstruction, but unlike normal dilation it cannot
        // grow the object outward into the photo background.
        val maxBridgeSpan = max(6, (min(sw, sh) * 0.025f).roundToInt()).coerceAtMost(18)
        repeat(2) { bridgeShortEnclosedGaps(protect, sw, sh, maxBridgeSpan) }

        // Restore only very small fully enclosed holes. Large loop openings of a ribbon remain
        // transparent, while tiny pure-background-colored sparkle/highlight holes stay intact.
        val maxTinyHoleArea = max(24, (area * 0.0015f).roundToInt()).coerceAtMost(900)
        fillTinyEnclosedHoles(protect, sw, sh, maxTinyHoleArea)

        // Repair only micro-notches that were present after Stage 1/2/3 but were
        // accidentally removed by Stage 4. This is designed for cases such as a
        // tiny black/white dash punched into a silver bow highlight.
        //
        // Important safety rules:
        // - candidate pixels must have been opaque before Stage 4,
        // - the candidate component must be small,
        // - it must NOT touch the slot border,
        // - it must be enclosed/sandwiched by already-protected decoration,
        // - restoration copies the exact source ARGB pixel; no blur/interpolation.
        val maxMicroNotchArea = max(8, (area * 0.00045f).roundToInt()).coerceAtMost(120)
        repairMicroNotches(
            protect = protect,
            preStage4Opaque = preStage4Opaque,
            w = sw,
            h = sh,
            maxArea = maxMicroNotchArea
        )

        // Authoritative write. NOTHING outside the red/detected rectangle is touched.
        for (y in slot.minY..slot.maxY) {
            val row = y * width
            for (x in slot.minX..slot.maxX) {
                val gi = row + x
                val li = localIndex(x, y)
                punchedPixels[gi] = if (protect[li]) sourcePixels[gi] else 0x00000000
            }
        }
    }

    /** Fill only bounded short false-runs. Never expands an exterior object boundary. */
    private fun bridgeShortEnclosedGaps(mask: BooleanArray, w: Int, h: Int, maxSpan: Int) {
        val add = BooleanArray(mask.size)

        // Horizontal runs
        for (y in 0 until h) {
            var lastTrue = -1
            var x = 0
            while (x < w) {
                val i = y * w + x
                if (mask[i]) {
                    if (lastTrue >= 0) {
                        val gap = x - lastTrue - 1
                        if (gap in 1..maxSpan) {
                            for (gx in lastTrue + 1 until x) add[y * w + gx] = true
                        }
                    }
                    lastTrue = x
                }
                x++
            }
        }

        // Vertical runs
        for (x in 0 until w) {
            var lastTrue = -1
            var y = 0
            while (y < h) {
                val i = y * w + x
                if (mask[i]) {
                    if (lastTrue >= 0) {
                        val gap = y - lastTrue - 1
                        if (gap in 1..maxSpan) {
                            for (gy in lastTrue + 1 until y) add[gy * w + x] = true
                        }
                    }
                    lastTrue = y
                }
                y++
            }
        }

        for (i in mask.indices) if (add[i]) mask[i] = true
    }

    /** Flood inverse components; fill only tiny holes that do not touch the slot border. */
    private fun fillTinyEnclosedHoles(mask: BooleanArray, w: Int, h: Int, maxArea: Int) {
        val visited = BooleanArray(mask.size)
        val queue = IntArray(mask.size)

        for (start in mask.indices) {
            if (mask[start] || visited[start]) continue

            var head = 0
            var tail = 0
            var touchesBorder = false
            queue[tail++] = start
            visited[start] = true

            while (head < tail) {
                val curr = queue[head++]
                val x = curr % w
                val y = curr / w
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) touchesBorder = true

                if (x > 0) {
                    val n = curr - 1
                    if (!mask[n] && !visited[n]) { visited[n] = true; queue[tail++] = n }
                }
                if (x + 1 < w) {
                    val n = curr + 1
                    if (!mask[n] && !visited[n]) { visited[n] = true; queue[tail++] = n }
                }
                if (y > 0) {
                    val n = curr - w
                    if (!mask[n] && !visited[n]) { visited[n] = true; queue[tail++] = n }
                }
                if (y + 1 < h) {
                    val n = curr + w
                    if (!mask[n] && !visited[n]) { visited[n] = true; queue[tail++] = n }
                }
            }

            if (!touchesBorder && tail <= maxArea) {
                for (i in 0 until tail) mask[queue[i]] = true
            }
        }
    }

    /**
     * Restore tiny notches that Stage 4 punched into a decoration.
     *
     * A candidate is a pixel that:
     * - was still opaque after Stage 1/2/3, and
     * - Stage 4 currently plans to make transparent.
     *
     * We restore only small connected candidate components that are geometrically
     * enclosed by protected decoration. This catches tiny horizontal/vertical dashes
     * inside a bow highlight while refusing large/open slot-background regions.
     */
    private fun repairMicroNotches(
        protect: BooleanArray,
        preStage4Opaque: BooleanArray,
        w: Int,
        h: Int,
        maxArea: Int
    ) {
        if (w <= 0 || h <= 0 || protect.isEmpty()) return

        val candidate = BooleanArray(protect.size)
        for (i in protect.indices) {
            candidate[i] = preStage4Opaque[i] && !protect[i]
        }

        val visited = BooleanArray(candidate.size)
        val queue = IntArray(candidate.size)

        for (start in candidate.indices) {
            if (!candidate[start] || visited[start]) continue

            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true

            var minX = w
            var maxX = -1
            var minY = h
            var maxY = -1
            var touchesBorder = false

            var leftProtected = 0
            var rightProtected = 0
            var topProtected = 0
            var bottomProtected = 0
            var protectedAdjacency = 0
            var exposedAdjacency = 0

            while (head < tail) {
                val curr = queue[head++]
                val x = curr % w
                val y = curr / w

                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y

                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    touchesBorder = true
                }

                // 8-connected component of pixels Stage 4 would newly remove.
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx !in 0 until w || ny !in 0 until h) continue
                        val ni = ny * w + nx
                        if (candidate[ni] && !visited[ni]) {
                            visited[ni] = true
                            queue[tail++] = ni
                        }
                    }
                }

                // 4-neighbour enclosure evidence. We intentionally keep directional
                // counts because a thin horizontal notch only needs strong top/bottom
                // support, while a vertical notch needs strong left/right support.
                fun inspect(nx: Int, ny: Int, direction: Int) {
                    if (nx !in 0 until w || ny !in 0 until h) {
                        exposedAdjacency++
                        return
                    }
                    val ni = ny * w + nx
                    if (protect[ni]) {
                        protectedAdjacency++
                        when (direction) {
                            0 -> leftProtected++
                            1 -> rightProtected++
                            2 -> topProtected++
                            3 -> bottomProtected++
                        }
                    } else if (!candidate[ni]) {
                        exposedAdjacency++
                    }
                }

                inspect(x - 1, y, 0)
                inspect(x + 1, y, 1)
                inspect(x, y - 1, 2)
                inspect(x, y + 1, 3)
            }

            val compArea = tail
            if (compArea <= 0 || compArea > maxArea || touchesBorder) continue

            val compW = maxX - minX + 1
            val compH = maxY - minY + 1
            if (compW <= 0 || compH <= 0) continue

            val hasHorizontalSandwich = leftProtected > 0 && rightProtected > 0
            val hasVerticalSandwich = topProtected > 0 && bottomProtected > 0

            // Tiny round/square chip fully embedded in a decoration.
            val compactNotch =
                compW <= 10 && compH <= 10 &&
                    hasHorizontalSandwich && hasVerticalSandwich

            // Thin horizontal dash, like the small cut visible in the bow highlight.
            // It may be longer than 10 px, but must be shallow and sandwiched by
            // protected decoration above and below.
            val thinHorizontalNotch =
                compH <= 5 && compW <= 28 && hasVerticalSandwich

            // Symmetric case for a vertical scratch/notch.
            val thinVerticalNotch =
                compW <= 5 && compH <= 28 && hasHorizontalSandwich

            // Boundary confidence: true notches are mostly surrounded by decoration,
            // while real photo-slot background tends to open into a much larger region.
            val totalAdjacency = protectedAdjacency + exposedAdjacency
            val protectedBoundaryRatio = if (totalAdjacency == 0) {
                0f
            } else {
                protectedAdjacency.toFloat() / totalAdjacency.toFloat()
            }

            val safeToRestore =
                (compactNotch || thinHorizontalNotch || thinVerticalNotch) &&
                    protectedBoundaryRatio >= 0.58f

            if (safeToRestore) {
                for (i in 0 until tail) {
                    protect[queue[i]] = true
                }
            }
        }
    }


    /**
     * Final matte for a generic colored slot.
     *
     * The detected rectangle is authoritative: every pixel starts as background. We learn
     * the slot's RGB distribution only from the eroded, definite uniform component and
     * restore decoration pixels that fall outside a robust background envelope. This makes
     * orange/red/blue/pink/etc. slots work without hard-coded colors and avoids leaving
     * colored residue around the bow or stars.
     */
    private fun applyUniformColorRobustMatte(
        slot: SlotBounds,
        sourcePixels: IntArray,
        detectPixels: IntArray,
        punchedPixels: IntArray,
        componentId: IntArray,
        width: Int,
        height: Int
    ) {
        val sw = slot.width
        val sh = slot.height
        val localArea = sw * sh
        if (sw <= 0 || sh <= 0 || localArea <= 0) return

        fun localIndex(x: Int, y: Int): Int = (y - slot.minY) * sw + (x - slot.minX)

        // ---------------------------------------------------------------------
        // V15: ROBUST DOMINANT-COLOR MATTE
        // ---------------------------------------------------------------------
        // A colored photo slot normally occupies most of its detected rectangle, while
        // decorations (bow/star/text/sticker) occupy a much smaller percentage.  Instead
        // of learning a very tight RGB envelope from only the eroded seed component, learn
        // the dominant slot color from the whole rectangle using robust percentiles.
        //
        // This deliberately treats small color fluctuations, PNG/JPEG noise and gentle
        // gradients as BACKGROUND.  Decoration is accepted only when it is a coherent,
        // sufficiently large color outlier.  Therefore:
        //   * tiny orange/red/blue speckles disappear,
        //   * the 1-2 px colored residue along the detected rectangle disappears,
        //   * large stars/bows remain exactly sourcePixels (bit-for-bit),
        //   * writes remain strictly inside [slot].
        // ---------------------------------------------------------------------

        val hr = IntArray(256)
        val hg = IntArray(256)
        val hb = IntArray(256)
        var opaqueCount = 0

        // Prefer the definite uniform component for the first estimate, but fall back to
        // the entire slot if a regularized rectangle no longer shares the original id well.
        for (y in slot.minY..slot.maxY) {
            val row = y * width
            for (x in slot.minX..slot.maxX) {
                val gi = row + x
                if (componentId[gi] != slot.id) continue
                val srcA = (sourcePixels[gi] ushr 24) and 0xFF
                if (srcA < 8) continue
                val p = detectPixels[gi]
                hr[(p ushr 16) and 0xFF]++
                hg[(p ushr 8) and 0xFF]++
                hb[p and 0xFF]++
                opaqueCount++
            }
        }

        if (opaqueCount < max(32, (localArea * 0.01f).roundToInt())) {
            hr.fill(0); hg.fill(0); hb.fill(0); opaqueCount = 0
            for (y in slot.minY..slot.maxY) {
                val row = y * width
                for (x in slot.minX..slot.maxX) {
                    val gi = row + x
                    val srcA = (sourcePixels[gi] ushr 24) and 0xFF
                    if (srcA < 8) continue
                    val p = detectPixels[gi]
                    hr[(p ushr 16) and 0xFF]++
                    hg[(p ushr 8) and 0xFF]++
                    hb[p and 0xFF]++
                    opaqueCount++
                }
            }
        }
        if (opaqueCount == 0) return

        fun histPercentile(hist: IntArray, count: Int, q: Float): Int {
            val target = max(1, (count * q.coerceIn(0f, 1f)).roundToInt())
            var seen = 0
            for (v in hist.indices) {
                seen += hist[v]
                if (seen >= target) return v
            }
            return 255
        }

        val r50 = histPercentile(hr, opaqueCount, 0.50f)
        val g50 = histPercentile(hg, opaqueCount, 0.50f)
        val b50 = histPercentile(hb, opaqueCount, 0.50f)

        // Build a distance histogram against the robust center.  The dominant slot color
        // is expected to cover far more than decoration; P82/P90 therefore describe real
        // background variation rather than rare decorative outliers.
        val distanceHist = IntArray(256)
        var distanceCount = 0
        for (y in slot.minY..slot.maxY) {
            val row = y * width
            for (x in slot.minX..slot.maxX) {
                val gi = row + x
                val srcA = (sourcePixels[gi] ushr 24) and 0xFF
                if (srcA < 8) continue
                val p = detectPixels[gi]
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                val d = colorDistance(r, g, b, r50, g50, b50)
                    .roundToInt().coerceIn(0, 255)
                distanceHist[d]++
                distanceCount++
            }
        }

        fun distancePercentile(q: Float): Float {
            val target = max(1, (distanceCount * q.coerceIn(0f, 1f)).roundToInt())
            var seen = 0
            for (d in distanceHist.indices) {
                seen += distanceHist[d]
                if (seen >= target) return d.toFloat()
            }
            return 255f
        }

        val d75 = distancePercentile(0.75f)
        val d82 = distancePercentile(0.82f)
        val d90 = distancePercentile(0.90f)

        // Background thresholds are intentionally generous.  A true decoration on a
        // colored slot is usually much farther away in RGB than slot texture/noise.
        val backgroundCoreThreshold = max(5.0f, d75 + 3.0f).coerceAtMost(32f)
        val backgroundWideThreshold = max(backgroundCoreThreshold + 5.0f, d82 + 7.0f)
            .coerceAtMost(46f)
        val strongDetailThreshold = max(backgroundWideThreshold + 14.0f, d90 + 12.0f)
            .coerceAtMost(82f)

        val bgCandidate = BooleanArray(localArea)
        val strongDetail = BooleanArray(localArea)
        val localDistance = FloatArray(localArea)

        for (y in slot.minY..slot.maxY) {
            val row = y * width
            for (x in slot.minX..slot.maxX) {
                val gi = row + x
                val li = localIndex(x, y)
                val src = sourcePixels[gi]
                val srcA = (src ushr 24) and 0xFF
                if (srcA == 0) {
                    bgCandidate[li] = true
                    continue
                }

                val p = detectPixels[gi]
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                val d = colorDistance(r, g, b, r50, g50, b50)
                localDistance[li] = d

                bgCandidate[li] = d <= backgroundWideThreshold
                strongDetail[li] = d >= strongDetailThreshold ||
                    (srcA in 1..220 && d > backgroundWideThreshold + 4f)
            }
        }

        // Local 3x3 support removes isolated color-noise outliers from foreground candidacy.
        // A decoration edge has many neighboring outliers; a lone orange/red speck does not.
        fun outlierNeighborCount(li: Int): Int {
            val x = li % sw
            val y = li / sw
            var count = 0
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until sw || ny !in 0 until sh) continue
                    val ni = ny * sw + nx
                    if (!bgCandidate[ni]) count++
                }
            }
            return count
        }

        val weakDetail = BooleanArray(localArea)
        for (li in 0 until localArea) {
            if (bgCandidate[li]) continue
            val neighbors = outlierNeighborCount(li)
            weakDetail[li] = strongDetail[li] || neighbors >= 2
        }

        // Group decoration candidates.  Small isolated components are treated as background
        // even if their color is far from the median; this is the key anti-speckle guard.
        val protect = BooleanArray(localArea)
        val visited = BooleanArray(localArea)
        val queue = IntArray(localArea)
        val minDetailArea = max(18, (localArea * 0.00012f).roundToInt()).coerceAtMost(140)
        val minThinDetailSpan = max(10, (min(sw, sh) * 0.025f).roundToInt()).coerceAtMost(34)

        for (start in 0 until localArea) {
            if (!weakDetail[start] || visited[start]) continue

            var head = 0
            var tail = 0
            var strongCount = 0
            var minX = sw
            var maxX = -1
            var minY = sh
            var maxY = -1

            queue[tail++] = start
            visited[start] = true

            while (head < tail) {
                val curr = queue[head++]
                val cx = curr % sw
                val cy = curr / sw
                if (strongDetail[curr]) strongCount++
                if (cx < minX) minX = cx
                if (cx > maxX) maxX = cx
                if (cy < minY) minY = cy
                if (cy > maxY) maxY = cy

                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = cx + dx
                        val ny = cy + dy
                        if (nx !in 0 until sw || ny !in 0 until sh) continue
                        val ni = ny * sw + nx
                        if (weakDetail[ni] && !visited[ni]) {
                            visited[ni] = true
                            queue[tail++] = ni
                        }
                    }
                }
            }

            val compArea = tail
            val compW = maxX - minX + 1
            val compH = maxY - minY + 1
            val longThin = max(compW, compH) >= minThinDetailSpan && min(compW, compH) >= 2
            val keep = strongCount > 0 && (compArea >= minDetailArea || longThin)
            if (keep) {
                for (k in 0 until tail) protect[queue[k]] = true
            }
        }

        // Grow protected decoration only through pixels that are STILL clearly outside the
        // background core.  This recovers anti-aliased silver edges without allowing a bow
        // to expand through orange/red background noise.
        val growLimit = max(backgroundCoreThreshold + 4f, backgroundWideThreshold - 4f)
        val growQueue = IntArray(localArea)
        var gHead = 0
        var gTail = 0
        for (i in 0 until localArea) {
            if (protect[i]) growQueue[gTail++] = i
        }
        while (gHead < gTail) {
            val curr = growQueue[gHead++]
            val cx = curr % sw
            val cy = curr / sw
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = cx + dx
                    val ny = cy + dy
                    if (nx !in 0 until sw || ny !in 0 until sh) continue
                    val ni = ny * sw + nx
                    if (!protect[ni] && localDistance[ni] > growLimit) {
                        protect[ni] = true
                        growQueue[gTail++] = ni
                    }
                }
            }
        }

        // Preserve tiny pure-color highlight gaps only when fully enclosed by decoration.
        val bridge = max(3, (min(sw, sh) * 0.012f).roundToInt()).coerceAtMost(8)
        bridgeShortEnclosedGaps(protect, sw, sh, bridge)
        val tinyHole = max(8, (localArea * 0.00035f).roundToInt()).coerceAtMost(90)
        fillTinyEnclosedHoles(protect, sw, sh, tinyHole)

        // Border-residue guard.  The rectangle itself is authoritative, so a background-like
        // pixel on its 1-3 px inner ring must be transparent.  Decoration crossing the border
        // is kept because only background-colored pixels are cleared here.
        val borderRing = max(1, (min(sw, sh) * 0.004f).roundToInt()).coerceAtMost(3)
        for (ly in 0 until sh) {
            for (lx in 0 until sw) {
                val li = ly * sw + lx
                val nearBorder = lx < borderRing || ly < borderRing ||
                    lx >= sw - borderRing || ly >= sh - borderRing
                if (nearBorder && localDistance[li] <= backgroundWideThreshold + 4f) {
                    protect[li] = false
                }
            }
        }

        // Final isolated-speckle suppression after all repair operations.  Any protected
        // component smaller than this is noise, not a meaningful overlay decoration.
        suppressTinyProtectedComponents(
            mask = protect,
            w = sw,
            h = sh,
            maxNoiseArea = max(10, (localArea * 0.00006f).roundToInt()).coerceAtMost(60)
        )

        // V16: remove the colored 1-3 px rim caused by anti-aliasing at the slot boundary.
        // A real decoration crossing the red rectangle (star/bow) has protected pixels
        // continuing inward, so it survives.  A slot-color rim has no inward support.
        reconstructBorderFromInterior(
            mask = protect,
            w = sw,
            h = sh,
            borderBand = max(2, (min(sw, sh) * 0.0045f).roundToInt()).coerceAtMost(4)
        )

        // One more tiny-component pass after border cleanup because cutting the rim can
        // disconnect little compression islands that were previously attached to it.
        suppressTinyProtectedComponents(
            mask = protect,
            w = sw,
            h = sh,
            maxNoiseArea = max(10, (localArea * 0.00008f).roundToInt()).coerceAtMost(72)
        )

        // Authoritative rectangle-only write: outside the detected slot remains untouched.
        // Protected decoration is restored EXACTLY from sourcePixels; all slot background
        // becomes fully transparent (no partial alpha / no colored fringe).
        for (y in slot.minY..slot.maxY) {
            val row = y * width
            for (x in slot.minX..slot.maxX) {
                val gi = row + x
                val li = localIndex(x, y)
                punchedPixels[gi] = if (protect[li]) sourcePixels[gi] else 0x00000000
            }
        }
    }

    /**
     * Remove unsupported residue in the inner border band of a detected slot.
     *
     * We do NOT blindly clear the whole band because real decorations can overlap a slot
     * boundary.  A border pixel survives only if the protected object continues inward
     * for several pixels along the corresponding edge normal.  This removes the thin
     * orange/red/blue outline while preserving stars and the center bow.
     */
    /**
     * Reconstruct only decoration that genuinely crosses the slot border.
     *
     * The anti-aliased slot rim is a thin protected ring at the exact rectangle edge.
     * Clearing it blindly would also cut stars/bows that cross that edge.  So we:
     *  1) remember the original protected mask,
     *  2) clear the inner border band completely,
     *  3) regrow ONLY original protected pixels from decoration that still exists deeper
     *     inside the slot, for at most borderBand+1 steps.
     *
     * A real star/bow has interior support and grows back to its original edge.  A colored
     * frame/rim has no deep interior body, so it cannot grow back across the whole border.
     */
    private fun reconstructBorderFromInterior(
        mask: BooleanArray,
        w: Int,
        h: Int,
        borderBand: Int
    ) {
        if (w <= 0 || h <= 0 || borderBand <= 0) return

        val original = mask.copyOf()

        fun isBorderBand(x: Int, y: Int): Boolean =
            x < borderBand || y < borderBand ||
                x >= w - borderBand || y >= h - borderBand

        // Remove every protected pixel in the slot's inner rim first.
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (isBorderBand(x, y)) mask[y * w + x] = false
            }
        }

        // Grow original decoration back from the surviving interior body.  Using only a
        // few iterations prevents growth from travelling sideways along a frame-colored rim.
        val add = BooleanArray(mask.size)
        repeat(borderBand + 1) {
            java.util.Arrays.fill(add, false)
            var changed = false

            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (!isBorderBand(x, y)) continue
                    val i = y * w + x
                    if (mask[i] || !original[i]) continue

                    var touchesInteriorDecoration = false
                    loop@ for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            val nx = x + dx
                            val ny = y + dy
                            if (nx !in 0 until w || ny !in 0 until h) continue
                            if (mask[ny * w + nx]) {
                                touchesInteriorDecoration = true
                                break@loop
                            }
                        }
                    }

                    if (touchesInteriorDecoration) {
                        add[i] = true
                        changed = true
                    }
                }
            }

            for (i in mask.indices) if (add[i]) mask[i] = true
            if (!changed) return
        }
    }

    /** Remove tiny disconnected protected islands left by compression/noise. */
    private fun suppressTinyProtectedComponents(
        mask: BooleanArray,
        w: Int,
        h: Int,
        maxNoiseArea: Int
    ) {
        if (maxNoiseArea <= 0) return
        val visited = BooleanArray(mask.size)
        val queue = IntArray(mask.size)

        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true

            while (head < tail) {
                val curr = queue[head++]
                val cx = curr % w
                val cy = curr / w
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = cx + dx
                        val ny = cy + dy
                        if (nx !in 0 until w || ny !in 0 until h) continue
                        val ni = ny * w + nx
                        if (mask[ni] && !visited[ni]) {
                            visited[ni] = true
                            queue[tail++] = ni
                        }
                    }
                }
            }

            if (tail <= maxNoiseArea) {
                for (i in 0 until tail) mask[queue[i]] = false
            }
        }
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