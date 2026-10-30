package com.phuctran.photobooth.desktop.facemask

import java.awt.image.BufferedImage
import kotlin.math.atan2
import kotlin.math.hypot

data class FaceBounds(val x: Int, val y: Int, val width: Int, val height: Int)
data class FaceLandmarks(
    val lx: Float, val ly: Float, val rx: Float, val ry: Float, val mx: Float, val my: Float,
    val nx: Float = (lx + rx) / 2f,
    val ny: Float = (ly + ry) / 2f + 0.55f * hypot((rx - lx).toDouble(), (ry - ly).toDouble()).toFloat(),
    val rmx: Float = mx, val rmy: Float = my, val lmx: Float = mx, val lmy: Float = my
) {
    val d: Float get() = hypot((rx - lx).toDouble(), (ry - ly).toDouble()).toFloat()
    val cx: Float get() = (lx + rx) / 2f
    val eyeY: Float get() = (ly + ry) / 2f
    val mouthY: Float get() = my
    val roll: Double get() = atan2((ry - ly).toDouble(), (rx - lx).toDouble())
}
data class FeatureEllipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float)
data class FeatureRegion(val name: String, val ellipse: FeatureEllipse)
data class DetectedFace(val bounds: FaceBounds, val landmarks: FaceLandmarks, val confidence: Float = 1.0f)

interface FaceDetector : AutoCloseable {
    val name: String
    fun detectAll(image: BufferedImage): List<DetectedFace>
    fun detect(image: BufferedImage): DetectedFace? = detectAll(image).maxByOrNull { it.bounds.width * it.bounds.height }
}
