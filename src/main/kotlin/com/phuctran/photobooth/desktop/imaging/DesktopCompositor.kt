package com.phuctran.photobooth.desktop.imaging

import com.phuctran.photobooth.desktop.model.FramePack
import com.phuctran.photobooth.desktop.model.LayoutMode
import com.phuctran.photobooth.desktop.model.RenderResult
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import kotlin.math.roundToInt

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

class DesktopCompositor(
    private val renderWidth: Int = 1800
) {
    fun renderFinal(
        layout: LayoutMode,
        frame: FramePack,
        photoPaths: List<Path>,
        outputDir: Path,
        qrCodeUrl: String? = null
    ): RenderResult {
        require(photoPaths.size >= layout.selectCount) {
            "Layout ${layout.title} cần đủ ${layout.selectCount} ảnh."
        }

        Files.createDirectories(outputDir)

        val isStrip = layout.printSizeLabel.contains("5x15", ignoreCase = true) || layout.printSizeLabel.contains("5 x 15", ignoreCase = true)

        val renderScale = 2 // Render ở 600 DPI thay vì 300 DPI để ảnh cực nét
        val width = (if (isStrip) 592 else 1184) * renderScale
        val height = 1790 * renderScale // Tỷ lệ cứng chuẩn theo kích thước người dùng đo được
        val canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = canvas.createGraphics()
        
        graphics.use {
            it.configure()
            it.color = Color.WHITE
            it.fillRect(0, 0, width, height)

            val slots = computeSlots(layout, width, height)
            slots.zip(photoPaths.take(layout.selectCount)).forEach { (slot, photoPath) ->
                it.color = Color(244, 244, 242)
                it.fill(slot)

                val image = ImageIO.read(photoPath.toFile())
                if (image != null) {
                    // Increase bleed margin to 80 pixels total (40px per side) at 2x scale
                    val bleedPx = 40 * renderScale
                    val bleedSlot = Rectangle(
                        slot.x - bleedPx / 2,
                        slot.y - bleedPx / 2,
                        slot.width + bleedPx,
                        slot.height + bleedPx
                    )
                    it.drawCenterCrop(image, bleedSlot)
                }

                if (!frame.isCustom) {
                    it.color = Color.WHITE
                    it.stroke = BasicStroke((width * 0.006f).coerceAtLeast(2f))
                    it.draw(slot)
                }
            }

            if (frame.isCustom && frame.customImagePath != null) {
                it.drawCustomOverlay(frame.customImagePath, width, height)
            } else {
                it.drawDefaultFrame(frame, layout, slots, width, height)
            }
        }

        val fileName = buildFileName(layout.id, frame.id)
        val finalPath = outputDir.resolve(fileName)
        
        // Vẽ mã QR nếu có URL
        if (qrCodeUrl != null) {
            drawQRCode(canvas, qrCodeUrl, width, height, frame)
        }

        // Lưu ảnh nguyên bản (dùng cho web/Cloudinary) bằng định dạng PNG không nén để tránh bị bể nét
        ImageIO.write(canvas, "png", finalPath.toFile())
        
        val printPath = if (isStrip) {
            val paperWidth = 1226 * renderScale 
            val paperHeight = 1829 * renderScale
            
            val compositeCanvas = BufferedImage(paperWidth, paperHeight, BufferedImage.TYPE_INT_RGB)
            val g2 = compositeCanvas.createGraphics()
            g2.configure()
            g2.color = Color.WHITE
            g2.fillRect(0, 0, paperWidth, paperHeight)
            
            val paddingTop = 39 * renderScale
            val paddingLeft = 14 * renderScale
            val gap = 2 * renderScale
            
            val scaledWidth = 592 * renderScale
            val scaledHeight = 1790 * renderScale
            
            // Tấm 1
            g2.drawImage(canvas, paddingLeft, paddingTop, scaledWidth, scaledHeight, null)
            
            // Tấm 2
            g2.drawImage(canvas, paddingLeft + scaledWidth + gap, paddingTop, scaledWidth, scaledHeight, null)
            
            g2.dispose()
            
            val printFileName = "print_$fileName"
            val pPath = outputDir.resolve(printFileName)
            ImageIO.write(compositeCanvas, "png", pPath.toFile())
            pPath
        } else {
            val printFileName = "print_$fileName"
            val pPath = outputDir.resolve(printFileName)
            
            val paperWidth = 1226 * renderScale
            val paperHeight = 1829 * renderScale // Khổ giấy thực tế in
            
            val compositeCanvas = BufferedImage(paperWidth, paperHeight, BufferedImage.TYPE_INT_RGB)
            val g2 = compositeCanvas.createGraphics()
            g2.configure()
            g2.color = Color.WHITE
            g2.fillRect(0, 0, paperWidth, paperHeight)
            
            val paddingTop = 39 * renderScale
            val paddingLeft = 14 * renderScale // Cho đồng bộ với Strip
            
            val scaledWidth = 1184 * renderScale // Hoặc 1186 nếu mún full gap
            val scaledHeight = 1790 * renderScale
            
            g2.drawImage(canvas, paddingLeft, paddingTop, scaledWidth, scaledHeight, null)
            g2.dispose()
            
            ImageIO.write(compositeCanvas, "png", pPath.toFile())
            pPath
        }

        return RenderResult(
            finalImagePath = finalPath,
            printImagePath = printPath,
            selectedCount = layout.selectCount,
            frameTitle = frame.title
        )
    }

    private fun computeSlots(layout: LayoutMode, canvasWidth: Int, canvasHeight: Int): List<Rectangle> {
        if (layout.absoluteSlots.isNotEmpty()) {
            return layout.absoluteSlots.map { slot ->
                Rectangle(
                    (canvasWidth * slot.x).roundToInt(),
                    (canvasHeight * slot.y).roundToInt(),
                    (canvasWidth * slot.width).roundToInt(),
                    (canvasHeight * slot.height).roundToInt()
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

    private fun Graphics2D.configure() {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    }

    private fun Graphics2D.drawCenterCrop(image: BufferedImage, destination: Rectangle) {
        val sourceRatio = image.width.toDouble() / image.height.toDouble()
        val destinationRatio = destination.width.toDouble() / destination.height.toDouble()

        val source = if (sourceRatio > destinationRatio) {
            val sourceWidth = (image.height * destinationRatio).roundToInt().coerceAtMost(image.width)
            Rectangle((image.width - sourceWidth) / 2, 0, sourceWidth, image.height)
        } else {
            val sourceHeight = (image.width / destinationRatio).roundToInt().coerceAtMost(image.height)
            Rectangle(0, (image.height - sourceHeight) / 2, image.width, sourceHeight)
        }

        drawImage(
            image,
            destination.x,
            destination.y,
            destination.x + destination.width,
            destination.y + destination.height,
            source.x,
            source.y,
            source.x + source.width,
            source.y + source.height,
            null
        )
    }

    private fun Graphics2D.drawCustomOverlay(path: Path, width: Int, height: Int) {
        val overlay = ImageIO.read(path.toFile()) ?: return
        drawImage(overlay, 0, 0, width, height, null)
    }

    private fun Graphics2D.drawDefaultFrame(
        frame: FramePack,
        layout: LayoutMode,
        slots: List<Rectangle>,
        width: Int,
        height: Int
    ) {
        val accent = frame.accentColor.toAwtColor()
        val border = (width * 0.014f).coerceAtLeast(8f)
        val margin = (width * 0.026f).roundToInt()

        color = accent
        stroke = BasicStroke(border)
        drawRoundRect(
            margin,
            margin,
            width - margin * 2,
            height - margin * 2,
            (width * 0.02f).roundToInt(),
            (width * 0.02f).roundToInt()
        )

        slots.forEach {
            color = Color(255, 255, 255, 190)
            stroke = BasicStroke((width * 0.008f).coerceAtLeast(4f))
            draw(it)
        }

        val lastSlotBottom = slots.maxOfOrNull { it.y + it.height } ?: (height * 0.7f).roundToInt()
        val footerTop = (lastSlotBottom + width * 0.03f).roundToInt().coerceAtMost(height - margin)
        if (height - footerTop > width * 0.06f) {
            color = Color(250, 250, 248)
            fillRoundRect(
                margin + border.roundToInt(),
                footerTop,
                width - (margin + border.roundToInt()) * 2,
                height - footerTop - margin,
                (width * 0.018f).roundToInt(),
                (width * 0.018f).roundToInt()
            )
            color = accent
            font = Font("SansSerif", Font.BOLD, (width * 0.028f).roundToInt())
            drawString(frame.title.uppercase(), margin + border.roundToInt() * 2, footerTop + (width * 0.045f).roundToInt())

            color = Color(110, 110, 105)
            font = Font("SansSerif", Font.PLAIN, (width * 0.017f).roundToInt())
            drawString(layout.printSizeLabel, margin + border.roundToInt() * 2, footerTop + (width * 0.074f).roundToInt())
        }
    }

    private fun buildFileName(layoutId: String, frameId: String): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val safeLayout = layoutId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val safeFrame = frameId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "print_${timestamp}_${safeLayout}_${safeFrame}.png"
    }

    private fun drawQRCode(canvas: BufferedImage, url: String, canvasWidth: Int, canvasHeight: Int, frame: FramePack) {
        try {
            val qrSize = frame.qrCodeSize ?: (canvasWidth * 0.15f).roundToInt().coerceAtLeast(100) // 15% width mặc định
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
            hints[EncodeHintType.MARGIN] = 1

            val bitMatrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, qrSize, qrSize, hints)
            
            // Lấy tọa độ từ cấu hình frame, nếu không có thì mặc định góc dưới bên phải
            val qrX = frame.qrCodeX ?: (canvasWidth - qrSize - 30)
            val qrY = frame.qrCodeY ?: (canvasHeight - qrSize - 30)
            
            val g2 = canvas.createGraphics()
            g2.configure()
            
            for (x in 0 until qrSize) {
                for (y in 0 until qrSize) {
                    val color = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                    canvas.setRGB(qrX + x, qrY + y, color.rgb)
                }
            }
            g2.dispose()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private fun Long.toAwtColor(): Color {
    val value = toInt()
    val alpha = (value ushr 24) and 0xFF
    val red = (value ushr 16) and 0xFF
    val green = (value ushr 8) and 0xFF
    val blue = value and 0xFF
    return Color(red, green, blue, alpha)
}

private inline fun Graphics2D.use(block: (Graphics2D) -> Unit) {
    try {
        block(this)
    } finally {
        dispose()
    }
}
