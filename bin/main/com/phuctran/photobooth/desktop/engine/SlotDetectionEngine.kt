package com.phuctran.photobooth.desktop.engine

import java.awt.image.BufferedImage
import java.util.UUID
import java.awt.Graphics2D
import java.awt.AlphaComposite
import java.awt.Color

class SlotDetectionEngine(private val config: DetectionConfig = DetectionConfig()) {

    private data class ComponentStats(
        val minX: Int, val minY: Int,
        val maxX: Int, val maxY: Int,
        val pixelArea: Int,
        val id: Int
    )

    fun detect(originalImage: BufferedImage): DetectionResult {
        // 1. Auto-Trim ONLY Transparent Edges
        var minXTrim = originalImage.width
        var minYTrim = originalImage.height
        var maxXTrim = -1
        var maxYTrim = -1

        for (y in 0 until originalImage.height) {
            for (x in 0 until originalImage.width) {
                val rgb = originalImage.getRGB(x, y)
                val a = (rgb ushr 24) and 0xFF
                val r = (rgb ushr 16) and 0xFF
                val g = (rgb ushr 8) and 0xFF
                val b = rgb and 0xFF

                val isTransparent = a < 10
                val isWhite = r > 245 && g > 245 && b > 245

                if (!isTransparent && !isWhite) {
                    if (x < minXTrim) minXTrim = x
                    if (x > maxXTrim) maxXTrim = x
                    if (y < minYTrim) minYTrim = y
                    if (y > maxYTrim) maxYTrim = y
                }
            }
        }

        if (maxXTrim < minXTrim || maxYTrim < minYTrim) {
            return DetectionResult(0, 0, emptyList(), listOf("NO_SLOTS_FOUND"), originalImage) // Empty image
        }

        // Add 1px padding if possible to ensure slots touching the edge are closed components
        minXTrim = maxOf(0, minXTrim - 1)
        minYTrim = maxOf(0, minYTrim - 1)
        maxXTrim = minOf(originalImage.width - 1, maxXTrim + 1)
        maxYTrim = minOf(originalImage.height - 1, maxYTrim + 1)

        val width = maxXTrim - minXTrim + 1
        val height = maxYTrim - minYTrim + 1
        val image = originalImage.getSubimage(minXTrim, minYTrim, width, height)
        val totalPixels = width * height
        val pixels = IntArray(totalPixels)
        image.getRGB(0, 0, width, height, pixels, 0, width)

        fun colorDist(c1: Int, c2: Int): Int {
            val r1 = (c1 ushr 16) and 0xFF; val g1 = (c1 ushr 8) and 0xFF; val b1 = c1 and 0xFF
            val r2 = (c2 ushr 16) and 0xFF; val g2 = (c2 ushr 8) and 0xFF; val b2 = c2 and 0xFF
            return maxOf(Math.abs(r1 - r2), Math.abs(g1 - g2), Math.abs(b1 - b2))
        }

        // 2. Connected Components by Color Tolerance
        val componentId = IntArray(totalPixels) { -1 }
        val components = mutableListOf<ComponentStats>()
        val queue = IntArray(totalPixels)
        val dx = intArrayOf(0, 1, 0, -1)
        val dy = intArrayOf(-1, 0, 1, 0)
        var currentId = 0

        for (i in 0 until totalPixels) {
            if (componentId[i] == -1) {
                val startPixel = pixels[i]
                val startAlpha = (startPixel ushr 24) and 0xFF
                val isStartTransparent = startAlpha <= config.alphaThreshold

                var minX = width; var maxX = -1
                var minY = height; var maxY = -1
                var pixelArea = 0

                var head = 0
                var tail = 0
                queue[tail++] = i
                componentId[i] = currentId

                while (head < tail) {
                    val idx = queue[head++]
                    val x = idx % width
                    val y = idx / width

                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    pixelArea++

                    for (dir in 0..3) {
                        val nx = x + dx[dir]
                        val ny = y + dy[dir]
                        if (nx in 0 until width && ny in 0 until height) {
                            val nIdx = ny * width + nx
                            if (componentId[nIdx] == -1) {
                                val nPixel = pixels[nIdx]
                                val nAlpha = (nPixel ushr 24) and 0xFF
                                val nTransparent = nAlpha <= config.alphaThreshold
                                
                                var match = false
                                if (isStartTransparent) {
                                    match = nTransparent
                                } else if (!nTransparent) {
                                    // Extremely strict tolerance (1) to prevent leaking into white-ish frames
                                    match = colorDist(startPixel, nPixel) <= 1 
                                }
                                
                                if (match) {
                                    componentId[nIdx] = currentId
                                    queue[tail++] = nIdx
                                }
                            }
                        }
                    }
                }
                
                // Only keep components that are at least 1.5% of the image to save memory and processing
                val areaRatio = pixelArea.toFloat() / totalPixels
                if (areaRatio >= 0.015f) {
                    components.add(ComponentStats(minX, minY, maxX, maxY, pixelArea, currentId))
                }
                currentId++
            }
        }

        // 3. Filter Initial Candidates (Raw Bounding Box)
        val warnings = mutableListOf<String>()
        val initialCandidates = mutableListOf<ComponentStats>()
        for (c in components) {
            val bboxWidth = c.maxX - c.minX + 1
            val bboxHeight = c.maxY - c.minY + 1
            val bboxArea = bboxWidth * bboxHeight
            val areaRatio = c.pixelArea.toFloat() / totalPixels
            val bboxAreaRatio = bboxArea.toFloat() / totalPixels
            val widthRatio = bboxWidth.toFloat() / width
            val heightRatio = bboxHeight.toFloat() / height
            val fillRatio = c.pixelArea.toFloat() / bboxArea
            val isBackground = bboxAreaRatio > 0.50f && fillRatio < 0.70f

            val valid = areaRatio >= config.minAreaRatio &&
                        areaRatio <= config.maxAreaRatio &&
                        widthRatio >= config.minWidthRatio &&
                        heightRatio >= config.minHeightRatio &&
                        fillRatio >= config.minRectangularity &&
                        !isBackground
            if (valid) {
                initialCandidates.add(c)
            }
        }
        
        // 4. Enclosure Rejection
        val nonEnclosingCandidates = mutableListOf<ComponentStats>()
        for (i in 0 until initialCandidates.size) {
            val a = initialCandidates[i]
            var isEnclosing = false
            for (j in 0 until initialCandidates.size) {
                if (i == j) continue
                val b = initialCandidates[j]
                
                val overlapMinX = maxOf(a.minX, b.minX)
                val overlapMinY = maxOf(a.minY, b.minY)
                val overlapMaxX = minOf(a.maxX, b.maxX)
                val overlapMaxY = minOf(a.maxY, b.maxY)
                
                if (overlapMaxX >= overlapMinX && overlapMaxY >= overlapMinY) {
                    val overlapArea = (overlapMaxX - overlapMinX + 1) * (overlapMaxY - overlapMinY + 1)
                    val bArea = (b.maxX - b.minX + 1) * (b.maxY - b.minY + 1)
                    val aArea = (a.maxX - a.minX + 1) * (a.maxY - a.minY + 1)
                    
                    if (overlapArea.toFloat() / bArea > 0.80f && aArea > bArea * 1.5f) {
                        isEnclosing = true
                        break
                    }
                }
            }
            if (!isEnclosing) {
                nonEnclosingCandidates.add(a)
            }
        }

        // 5. Two-Phase Consensus Bounding Box Refinement
        val strictWidths = mutableListOf<Int>()
        val strictHeights = mutableListOf<Int>()
        
        // Phase 1: Compute Strict Sizes using 50% threshold
        for (c in nonEnclosingCandidates) {
            val bboxWidth = c.maxX - c.minX + 1
            val bboxHeight = c.maxY - c.minY + 1
            val projX = IntArray(bboxWidth)
            val projY = IntArray(bboxHeight)
            for (y in c.minY..c.maxY) {
                for (x in c.minX..c.maxX) {
                    if (componentId[y * width + x] == c.id) {
                        projX[x - c.minX]++
                        projY[y - c.minY]++
                    }
                }
            }
            val maxProjX = projX.maxOrNull() ?: 0
            val maxProjY = projY.maxOrNull() ?: 0
            var strictW = 0
            var strictH = 0
            
            val thresholdX = maxProjX * 0.50f
            var sMinX = c.minX; var sMaxX = c.maxX
            for (i in 0 until bboxWidth) if (projX[i] >= thresholdX) { sMinX = c.minX + i; break }
            for (i in bboxWidth - 1 downTo 0) if (projX[i] >= thresholdX) { sMaxX = c.minX + i; break }
            strictW = sMaxX - sMinX + 1
            
            val thresholdY = maxProjY * 0.50f
            var sMinY = c.minY; var sMaxY = c.maxY
            for (i in 0 until bboxHeight) if (projY[i] >= thresholdY) { sMinY = c.minY + i; break }
            for (i in bboxHeight - 1 downTo 0) if (projY[i] >= thresholdY) { sMaxY = c.minY + i; break }
            strictH = sMaxY - sMinY + 1
            
            strictWidths.add(strictW)
            strictHeights.add(strictH)
        }
        
        val candidates = mutableListOf<ComponentStats>()
        if (nonEnclosingCandidates.isNotEmpty()) {
            strictWidths.sort()
            strictHeights.sort()
            val targetW = strictWidths[strictWidths.size / 2]
            val targetH = strictHeights[strictHeights.size / 2]
            
            // Phase 2: Greedily trim raw bounding boxes to target dimensions
            for (c in nonEnclosingCandidates) {
                val bboxWidth = c.maxX - c.minX + 1
                val bboxHeight = c.maxY - c.minY + 1
                var trueMinX = c.minX
                var trueMaxX = c.maxX
                var trueMinY = c.minY
                var trueMaxY = c.maxY
                
                val projX = IntArray(bboxWidth)
                val projY = IntArray(bboxHeight)
                for (y in c.minY..c.maxY) {
                    for (x in c.minX..c.maxX) {
                        if (componentId[y * width + x] == c.id) {
                            projX[x - c.minX]++
                            projY[y - c.minY]++
                        }
                    }
                }
                
                var trimX = bboxWidth - targetW
                while (trimX > 0) {
                    val leftProj = projX[trueMinX - c.minX]
                    val rightProj = projX[trueMaxX - c.minX]
                    if (leftProj <= rightProj) trueMinX++ else trueMaxX--
                    trimX--
                }
                
                var trimY = bboxHeight - targetH
                while (trimY > 0) {
                    val topProj = projY[trueMinY - c.minY]
                    val bottomProj = projY[trueMaxY - c.minY]
                    if (topProj <= bottomProj) trueMinY++ else trueMaxY--
                    trimY--
                }
                
                var newPixelArea = 0
                for (y in trueMinY..trueMaxY) {
                    for (x in trueMinX..trueMaxX) {
                        if (componentId[y * width + x] == c.id) newPixelArea++
                    }
                }
                candidates.add(ComponentStats(trueMinX, trueMinY, trueMaxX, trueMaxY, newPixelArea, c.id))
            }
        }

        if (candidates.size > config.maxSlots) {
            warnings.add("TOO_MANY_SLOTS")
            return DetectionResult(width, height, emptyList(), warnings)
        }

        // 5. Sort and map slots
        val sortedCandidates = candidates.sortedWith(Comparator { a, b ->
            val aCenterY = (a.minY + a.maxY) / 2
            val bCenterY = (b.minY + b.maxY) / 2
            val aCenterX = (a.minX + a.maxX) / 2
            val bCenterX = (b.minX + b.maxX) / 2
            val avgHeight = (a.maxY - a.minY + b.maxY - b.minY) / 2.0
            val tolerance = avgHeight * 0.1

            if (Math.abs(aCenterY - bCenterY) > tolerance) {
                aCenterY.compareTo(bCenterY)
            } else {
                aCenterX.compareTo(bCenterX)
            }
        })

        val slots = sortedCandidates.mapIndexed { index, c ->
            val bboxWidth = c.maxX - c.minX + 1
            val bboxHeight = c.maxY - c.minY + 1
            val centerX = c.minX + bboxWidth / 2f
            val centerY = c.minY + bboxHeight / 2f
            FrameSlot(
                id = UUID.randomUUID().toString(),
                index = index,
                x = c.minX.toFloat() / width,
                y = c.minY.toFloat() / height,
                width = bboxWidth.toFloat() / width,
                height = bboxHeight.toFloat() / height,
                centerX = centerX / width,
                centerY = centerY / height,
                areaRatio = c.pixelArea.toFloat() / totalPixels,
                shape = "RECT"
            )
        }

        if (slots.isEmpty()) {
            warnings.add("NO_SLOTS_FOUND")
        }

        // 6. Create punched image
        val punched = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2d = punched.createGraphics()
        g2d.drawImage(image, 0, 0, null)
        g2d.dispose()

        val punchedPixels = IntArray(totalPixels)
        punched.getRGB(0, 0, width, height, punchedPixels, 0, width)

        // Find representative slot color for each candidate (to support Color-To-Alpha)
        val slotColors = mutableMapOf<Int, Int>()
        for (c in candidates) {
            for (y in c.minY..c.maxY) {
                for (x in c.minX..c.maxX) {
                    val i = y * width + x
                    if (componentId[i] == c.id) {
                        slotColors[c.id] = punchedPixels[i]
                        break
                    }
                }
                if (slotColors.containsKey(c.id)) break
            }
        }
        
        // Punch PERFECT SQUARE holes using the Refined Bounding Boxes
        // We use Distance-based Alpha Matting ONLY on a 3-pixel boundary to preserve opaque overlapping details
        for (c in candidates) {
            val slotColor = slotColors[c.id] ?: 0xFFFFFFFF.toInt()
            val bR = (slotColor ushr 16) and 0xFF
            val bG = (slotColor ushr 8) and 0xFF
            val bB = slotColor and 0xFF
            val isTransparentSlot = ((slotColor ushr 24) and 0xFF) <= config.alphaThreshold
            
            val minDist = 15f
            val maxDist = 60f
            
            // Generate a mathematical forcefield to protect delicate sticker outlines and highlights.
            // Any pixel within a 2-pixel radius of a NON-WHITE foreground pixel is protected.
            val bWidth = c.maxX - c.minX + 1
            val bHeight = c.maxY - c.minY + 1
            val forcefield = BooleanArray(bWidth * bHeight)
            val radius = 2
            
            for (y in c.minY..c.maxY) {
                for (x in c.minX..c.maxX) {
                    val pIdx = y * width + x
                    if (componentId[pIdx] != c.id) {
                        val p = pixels[pIdx]
                        val r = (p ushr 16) and 0xFF; val g = (p ushr 8) and 0xFF; val b = p and 0xFF
                        val isWhite = r > 240 && g > 240 && b > 240
                        if (!isWhite) {
                            for (dy in -radius..radius) {
                                for (dx in -radius..radius) {
                                    val nx = x + dx
                                    val ny = y + dy
                                    if (nx in c.minX..c.maxX && ny in c.minY..c.maxY) {
                                        forcefield[(ny - c.minY) * bWidth + (nx - c.minX)] = true
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            for (y in c.minY..c.maxY) {
                for (x in c.minX..c.maxX) {
                    val i = y * width + x
                    
                    if (isTransparentSlot) {
                        if (componentId[i] == c.id) {
                            punchedPixels[i] = 0x00000000
                        }
                    } else {
                        val inForcefield = forcefield[(y - c.minY) * bWidth + (x - c.minX)]
                        
                        if (componentId[i] == c.id) {
                            if (!inForcefield) {
                                punchedPixels[i] = 0x00000000 // Punch normal background
                            }
                            // Else: It's a protected white outline/highlight! Leave it opaque.
                        } else {
                            val p = pixels[i]
                            val r = (p ushr 16) and 0xFF; val g = (p ushr 8) and 0xFF; val b = p and 0xFF
                            val isWhite = r > 240 && g > 240 && b > 240
                            
                            if (isWhite && !inForcefield) {
                                // It's a trapped island (e.g. bow loop hole). Punch it!
                                punchedPixels[i] = 0x00000000
                            } else {
                                // Boundary check for Alpha Matting (smooth edges of stickers)
                                var isBoundary = false
                                for (dy in -4..4) {
                                    for (dx in -4..4) {
                                        val nx = x + dx
                                        val ny = y + dy
                                        if (nx in c.minX..c.maxX && ny in c.minY..c.maxY) {
                                            val nIdx = ny * width + nx
                                            val nInForcefield = forcefield[(ny - c.minY) * bWidth + (nx - c.minX)]
                                            
                                            // It is a boundary if it touches a pixel that was actually PUNCHED
                                            if (componentId[nIdx] == c.id && !nInForcefield) {
                                                isBoundary = true
                                                break
                                            }
                                            
                                            val np = pixels[nIdx]
                                            val nr = (np ushr 16) and 0xFF; val ng = (np ushr 8) and 0xFF; val nb = np and 0xFF
                                            val nWhite = nr > 240 && ng > 240 && nb > 240
                                            if (componentId[nIdx] != c.id && nWhite && !nInForcefield) {
                                                isBoundary = true
                                                break
                                            }
                                        }
                                    }
                                    if (isBoundary) break
                                }

                                if (isBoundary) {
                                    val pr = (p ushr 16) and 0xFF
                                    val pg = (p ushr 8) and 0xFF
                                    val pb = p and 0xFF

                                    var ar = 0f
                                    if (pr < bR && bR > 0) ar = (pr - bR).toFloat() / (0f - bR)
                                    else if (pr > bR && bR < 255) ar = (pr - bR).toFloat() / (255f - bR)

                                    var ag = 0f
                                    if (pg < bG && bG > 0) ag = (pg - bG).toFloat() / (0f - bG)
                                    else if (pg > bG && bG < 255) ag = (pg - bG).toFloat() / (255f - bG)

                                    var ab = 0f
                                    if (pb < bB && bB > 0) ab = (pb - bB).toFloat() / (0f - bB)
                                    else if (pb > bB && bB < 255) ab = (pb - bB).toFloat() / (255f - bB)

                                    val a = maxOf(ar, ag, ab)
                                    if (a <= 0.05f) {
                                        punchedPixels[i] = 0x00000000
                                    } else if (a >= 0.95f) {
                                        // Keep original
                                    } else {
                                        var nr = bR + (pr - bR) / a
                                        var ng = bG + (pg - bG) / a
                                        var nb = bB + (pb - bB) / a
                                        
                                        nr = nr.coerceIn(0f, 255f)
                                        ng = ng.coerceIn(0f, 255f)
                                        nb = nb.coerceIn(0f, 255f)
                                        
                                        val currentA = (p ushr 24) and 0xFF
                                        val finalA = (currentA * a).toInt().coerceIn(0, 255)
                                        
                                        punchedPixels[i] = (finalA shl 24) or (nr.toInt() shl 16) or (ng.toInt() shl 8) or nb.toInt()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        punched.setRGB(0, 0, width, height, punchedPixels, 0, width)

        return DetectionResult(width, height, slots, warnings, punched)
    }
}

