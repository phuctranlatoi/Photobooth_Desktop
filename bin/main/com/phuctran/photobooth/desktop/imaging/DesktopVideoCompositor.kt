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

        val isStrip = layout.printSizeLabel.contains("5x15", ignoreCase = true) || layout.printSizeLabel.contains("5 x 15", ignoreCase = true)
        val canvasWidth = if (isStrip) 592 else 1184
        val canvasHeight = 1790

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

        // 2. Create background (duration 15s to ensure it covers long clips)
        filter.append("color=c=white:s=${canvasWidth}x${canvasHeight}:d=15[bg];")

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
        
        // Scale the frame to match canvas (in case user uploaded a 1184x1790 double-strip PNG)
        filter.append("[$frameIndex:v]scale=$canvasWidth:$canvasHeight[scaled_frame];")
        filter.append("[$lastOut][scaled_frame]overlay=0:0[out]")

        cmd.add("-filter_complex")
        cmd.add(filter.toString())
        cmd.add("-map")
        cmd.add("[out]")
        
        // Video codec options for web-optimized mp4
        cmd.add("-c:v")
        cmd.add("libx264")
        cmd.add("-preset")
        cmd.add("fast")
        cmd.add("-crf")
        cmd.add("17")
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
        if (layout.absoluteSlots.isNotEmpty()) {
            return layout.absoluteSlots.map { slot ->
                Rectangle(
                    (slot.x * canvasWidth).roundToInt(),
                    (slot.y * canvasHeight).roundToInt(),
                    (slot.width * canvasWidth).roundToInt(),
                    (slot.height * canvasHeight).roundToInt()
                )
            }
        }

        val width = canvasWidth.toFloat()
        val top = (width * layout.paddingTopRatio).roundToInt()
        val left = (width * layout.paddingLeftRatio).roundToInt()
        val right = (width * layout.paddingRightRatio).roundToInt()
        val gapX = (width * layout.gapHorizontalRatio).roundToInt()
        val gapY = (width * layout.gapVerticalRatio).roundToInt()
        val columns = layout.gridColumns.coerceAtLeast(1)
        val slotWidth = ((canvasWidth - left - right - gapX * (columns - 1)).toFloat() / columns)
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
