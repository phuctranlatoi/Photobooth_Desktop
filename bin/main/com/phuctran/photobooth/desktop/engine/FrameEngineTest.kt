package com.phuctran.photobooth.desktop.engine

import java.io.File
import javax.imageio.ImageIO

fun main(args: Array<String>) {
    println("=== Auto Frame Slot Detection & Compositor Test ===")
    
    // Yêu cầu user cung cấp file frame mẫu
    val frameFile = File("frame_template.png")
    if (!frameFile.exists()) {
        println("LỖI: Không tìm thấy file frame_template.png")
        println("Bạn hãy copy 1 file Frame đục lỗ (PNG) vào thư mục gốc của project")
        println("Và đổi tên nó thành 'frame_template.png' rồi chạy lại nhé!")
        return
    }

    println("Đang đọc file ${frameFile.name}...")
    val frameImage = ImageIO.read(frameFile)
    
    println("Kích thước: ${frameImage.width}x${frameImage.height}")

    val engine = SlotDetectionEngine()
    val startDetect = System.currentTimeMillis()
    val result = engine.detect(frameImage)
    val endDetect = System.currentTimeMillis()

    println("Phát hiện xong trong ${endDetect - startDetect}ms")
    println("Số lượng slot tìm thấy: ${result.slots.size}")
    if (result.warnings.isNotEmpty()) {
        println("Cảnh báo: ${result.warnings}")
    }

    result.slots.forEach { slot ->
        println(" - Slot ${slot.index}: X=${slot.x}, Y=${slot.y}, W=${slot.width}, H=${slot.height}")
    }

    if (result.slots.isEmpty()) {
        println("Dừng lại vì không tìm thấy lỗ ảnh nào!")
        return
    }

    // Load ảnh mẫu để ghép thử
    val photoFiles = listOf(
        File("photo1.jpg"),
        File("photo2.jpg"),
        File("photo3.jpg"),
        File("photo4.jpg")
    )
    
    val photos = mutableListOf<java.awt.image.BufferedImage>()
    for (pf in photoFiles) {
        if (pf.exists()) {
            photos.add(ImageIO.read(pf))
        }
    }

    if (photos.isEmpty()) {
        println("\nCẢNH BÁO: Không có file ảnh photo1.jpg, photo2.jpg... để ghép thử!")
        println("Bạn có thể bỏ vài file ảnh jpg/png vào thư mục gốc đặt tên là photo1.jpg, photo2.jpg...")
        println("Tuy nhiên, mình sẽ dùng màu trơn (solid color) để ghép thử nhé.")
        
        for (i in 0 until result.slots.size) {
            val dummy = java.awt.image.BufferedImage(600, 400, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val ctx = dummy.createGraphics()
            ctx.color = java.awt.Color((Math.random() * 0xFFFFFF).toInt())
            ctx.fillRect(0, 0, 600, 400)
            ctx.color = java.awt.Color.WHITE
            ctx.font = java.awt.Font("Arial", java.awt.Font.BOLD, 48)
            ctx.drawString("PHOTO ${i+1}", 200, 200)
            ctx.dispose()
            photos.add(dummy)
        }
    }

    println("\nBắt đầu ghép ảnh (Compositing)...")
    val compositor = PhotoCompositor()
    val startComposite = System.currentTimeMillis()
    // Giả sử muốn in ra ở DPI gốc của PNG
    val finalImage = compositor.renderComposition(
        frameImage = frameImage,
        targetWidth = frameImage.width,
        targetHeight = frameImage.height,
        slots = result.slots,
        photos = photos
    )
    val endComposite = System.currentTimeMillis()
    println("Ghép ảnh xong trong ${endComposite - startComposite}ms")

    val outputDir = File("data/output")
    if (!outputDir.exists()) outputDir.mkdirs()
    
    val outputFile = File(outputDir, "test_result.png")
    ImageIO.write(finalImage, "png", outputFile)
    
    println(">>> HOÀN TẤT! Hãy mở file kết quả tại: ${outputFile.absolutePath}")
}
