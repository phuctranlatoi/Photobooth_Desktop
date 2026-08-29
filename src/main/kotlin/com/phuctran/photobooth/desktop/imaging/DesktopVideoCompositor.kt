package com.phuctran.photobooth.desktop.imaging

import com.phuctran.photobooth.desktop.model.CapturedMoment
import com.phuctran.photobooth.desktop.model.FramePack
import com.phuctran.photobooth.desktop.model.LayoutMode
import java.awt.Rectangle
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.roundToInt

class DesktopVideoCompositor(private val projectDir: Path) {

    fun createCompositeVideo(
        layout: LayoutMode,
        frame: FramePack,
        selectedMoments: List<CapturedMoment>,
        outputDir: Path,
        sessionId: String
    ): Path? {
        val frameFile = frame.customImagePath ?: projectDir.resolve("data").resolve("frames").resolve(frame.id).resolve("frame.png")
        if (!Files.exists(frameFile)) {
            println("Master Video Error: Frame file not found at $frameFile")
            return null
        }

        // Get dimensions from frame
        val frameImg = ImageIO.read(frameFile.toFile()) ?: return null
        val canvasWidth = frameImg.width
        val canvasHeight = frameImg.height

        val slots = computeSlots(layout, canvasWidth, canvasHeight)
        
        Files.createDirectories(outputDir)
        val outputPath = outputDir.resolve("final_video_$sessionId.mp4")

        // Build FFmpeg command
        val cmd = mutableListOf<String>()
        val localAppData = System.getenv("LOCALAPPDATA")
        val wingetPath = if (localAppData != null) "$localAppData\\Microsoft\\WinGet\\Links\\ffmpeg.exe" else "ffmpeg.exe"
        val localBinPath = projectDir.resolve("bin").resolve("ffmpeg.exe").toAbsolutePath().toString()
        
        val ffmpegExecutable = when {
            Files.exists(Path.of(localBinPath)) -> localBinPath
            Files.exists(Path.of(wingetPath)) -> wingetPath
            else -> "ffmpeg"
        }

        cmd.add(ffmpegExecutable)
        cmd.add("-y")

        // Inputs: Add all selected videos
        val validMoments = selectedMoments.take(layout.selectCount)
        validMoments.forEach { moment ->
            if (moment.videoPath != null && Files.exists(moment.videoPath)) {
                cmd.add("-i")
                cmd.add(moment.videoPath.toAbsolutePath().toString())
            } else {
                // If video is missing, we could add a dummy, but for now just fail or handle it
                return null
            }
        }
        
        // Input: Frame PNG
        cmd.add("-i")
        cmd.add(frameFile.toAbsolutePath().toString())

        // Build filter_complex
        val filter = StringBuilder()
        // 1. Scale videos
        validMoments.forEachIndexed { i, _ ->
            val slot = slots[i]
            // Ensure even dimensions
            val w = if (slot.width % 2 != 0) slot.width - 1 else slot.width
            val h = if (slot.height % 2 != 0) slot.height - 1 else slot.height
            // Scale and crop to fill the slot exactly (object-fit: cover behavior)
            filter.append("[$i:v]scale=$w:$h:force_original_aspect_ratio=increase,crop=$w:$h[v$i];")
        }

        // 2. Create background
        filter.append("color=c=white:s=${canvasWidth}x${canvasHeight}:d=4[bg];")

        // 3. Overlay videos onto background
        var lastOut = "bg"
        validMoments.forEachIndexed { i, _ ->
            val slot = slots[i]
            val nextOut = "bg${i + 1}"
            filter.append("[$lastOut][v$i]overlay=${slot.x}:${slot.y}:shortest=1[$nextOut];")
            lastOut = nextOut
        }

        // 4. Overlay frame PNG on top of everything
        val frameIndex = validMoments.size
        
        val isDoubleWidth = layout.printSizeLabel.contains("5x15", ignoreCase = true) || layout.printSizeLabel.contains("5 x 15", ignoreCase = true)
        
        if (isDoubleWidth) {
            val singleWidth = canvasWidth / 2
            filter.append("[$lastOut][$frameIndex:v]overlay=0:0,crop=$singleWidth:$canvasHeight:0:0[out]")
        } else {
            filter.append("[$lastOut][$frameIndex:v]overlay=0:0[out]")
        }

        cmd.add("-filter_complex")
        cmd.add(filter.toString())
        cmd.add("-map")
        cmd.add("[out]")
        
        // Video codec options for web-optimized mp4
        cmd.add("-c:v")
        cmd.add("libx264")
        cmd.add("-preset")
        cmd.add("veryfast")
        cmd.add("-crf")
        cmd.add("23")
        cmd.add("-pix_fmt")
        cmd.add("yuv420p")
        
        // Since we are combining multiple clips, we must ensure there's no audio mapping issue
        cmd.add("-an")

        // Output path
        cmd.add(outputPath.toAbsolutePath().toString())

        println("FFmpeg Command: ${cmd.joinToString(" ")}")

        return try {
            val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
            // Read output for debugging
            Thread {
                process.inputStream.bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        println("FFMPEG COMPOSITOR: $line")
                    }
                }
            }.start()

            val exited = process.waitFor(60, TimeUnit.SECONDS)
            if (exited && process.exitValue() == 0 && Files.exists(outputPath)) {
                // If double width is required for 5x15 strips, we can duplicate it, 
                // but usually the web version of the layout is a single image.
                outputPath
            } else {
                process.destroyForcibly()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun computeSlots(layout: LayoutMode, canvasWidth: Int, canvasHeight: Int): List<Rectangle> {
        val isDoubleWidth = layout.printSizeLabel.contains("5x15", ignoreCase = true) || layout.printSizeLabel.contains("5 x 15", ignoreCase = true)
        val singleWidth = if (isDoubleWidth) canvasWidth / 2 else canvasWidth
        
        if (layout.absoluteSlots.isNotEmpty()) {
            return layout.absoluteSlots.map { slot ->
                Rectangle(
                    (singleWidth * slot.x).roundToInt(),
                    (canvasHeight * slot.y).roundToInt(),
                    (singleWidth * slot.width).roundToInt(),
                    (canvasHeight * slot.height).roundToInt()
                )
            }
        }

        val width = singleWidth.toFloat()
        val top = (width * layout.paddingTopRatio).roundToInt()
        val left = (width * layout.paddingLeftRatio).roundToInt()
        val right = (width * layout.paddingRightRatio).roundToInt()
        val gapX = (width * layout.gapHorizontalRatio).roundToInt()
        val gapY = (width * layout.gapVerticalRatio).roundToInt()
        val columns = layout.gridColumns.coerceAtLeast(1)
        val slotWidth = ((singleWidth - left - right - gapX * (columns - 1)).toFloat() / columns)
            .roundToInt()
            .coerceAtLeast(1)
        val slotHeight = (slotWidth / layout.photoAspectRatio)
            .roundToInt()
            .coerceAtLeast(1)

        return List(layout.selectCount) { index ->
            val row = index / columns
            val column = index % columns
            Rectangle(
                left + column * (slotWidth + gapX),
                top + row * (slotHeight + gapY),
                slotWidth,
                slotHeight
            )
        }
    }
}
