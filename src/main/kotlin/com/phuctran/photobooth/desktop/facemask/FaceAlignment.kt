package com.phuctran.photobooth.desktop.facemask

import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D
import java.awt.image.BufferedImage

/** Isotropic eye-aligned mapping between the original portrait and model coordinates. */
class FaceAlignment(val landmarks: FaceLandmarks, val size: Int) {
    init {
        require(size > 0) { "Alignment size must be positive" }
        require(landmarks.d.isFinite() && landmarks.d > 2f) { "Eye distance must be finite and greater than 2" }
    }
    val forward: AffineTransform = AffineTransform().apply {
        translate(size * .5, size * .38)
        scale(size / (3.6 * landmarks.d), size / (3.6 * landmarks.d))
        rotate(-landmarks.roll)
        translate(-landmarks.cx.toDouble(), -landmarks.eyeY.toDouble())
    }
    val inverse: AffineTransform = forward.createInverse()
    fun toCrop(x: Double, y: Double): Point2D = forward.transform(Point2D.Double(x, y), null)
    fun toOriginal(x: Double, y: Double): Point2D = inverse.transform(Point2D.Double(x, y), null)
    fun crop(source: BufferedImage): BufferedImage = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB).also { output ->
        output.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(source, forward, null)
            dispose()
        }
    }
}
