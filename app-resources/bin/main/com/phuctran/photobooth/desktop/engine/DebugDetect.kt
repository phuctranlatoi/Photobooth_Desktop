package com.phuctran.photobooth.desktop.engine

import java.io.File
import javax.imageio.ImageIO

fun main() {
    val file = File("E:/HK1_2026_2027/PhotoboothDesktop/frame/Frame (Bài thuyết trình) (7).png")
    if (!file.exists()) {
        println("File not found")
        return
    }
    val image = ImageIO.read(file)
    val engine = SlotDetectionEngine()
    
    val result = engine.detect(image)
    
    println("--- FINAL DETECTED SLOTS ---")
    result.slots.forEach { slot ->
        println("Slot ${slot.index}: x=${slot.x}, y=${slot.y}, width=${slot.width}, height=${slot.height}")
        println("Raw pixel sizes:")
        val px = (slot.x * image.width).toInt()
        val py = (slot.y * image.height).toInt()
        val pw = (slot.width * image.width).toInt()
        val ph = (slot.height * image.height).toInt()
        println("  -> X: $px, Y: $py, W: $pw, H: $ph")
    }
}
