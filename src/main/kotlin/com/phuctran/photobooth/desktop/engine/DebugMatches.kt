package com.phuctran.photobooth.desktop.engine

import java.io.File
import javax.imageio.ImageIO
import java.awt.Color

fun main() {
    val file = File("E:/HK1_2026_2027/PhotoboothDesktop/frame/Frame (Bài thuyết trình) (7).png")
    val image = ImageIO.read(file)
    val width = image.width
    val height = image.height
    val pixels = image.getRGB(0, 0, width, height, null, 0, width)
    
    val slotColor = pixels[41 * width + 272]
    val r = (slotColor ushr 16) and 0xFF
    val g = (slotColor ushr 8) and 0xFF
    val b = slotColor and 0xFF
    println("Slot Color: RGB($r, $g, $b)")
}
