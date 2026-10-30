package com.phuctran.photobooth.desktop.facemask

import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage

data class LetterboxTransform(val sourceWidth: Int, val sourceHeight: Int, val targetWidth: Int, val targetHeight: Int) {
    init {
        require(sourceWidth > 0 && sourceHeight > 0 && targetWidth > 0 && targetHeight > 0)
    }

    val scale = minOf(targetWidth.toFloat() / sourceWidth, targetHeight.toFloat() / sourceHeight)
    val contentWidth = sourceWidth * scale
    val contentHeight = sourceHeight * scale
    val padX = (targetWidth - contentWidth) / 2f
    val padY = (targetHeight - contentHeight) / 2f

    fun toTargetX(sourceX: Float): Float = sourceX * scale + padX
    fun toTargetY(sourceY: Float): Float = sourceY * scale + padY
    fun toSourceX(targetX: Float): Float = (targetX - padX) / scale
    fun toSourceY(targetY: Float): Float = (targetY - padY) / scale
    fun toSourceWidth(targetWidth: Float): Float = targetWidth / scale
    fun toSourceHeight(targetHeight: Float): Float = targetHeight / scale

    fun renderLetterbox(source: BufferedImage): BufferedImage {
        require(source.width == sourceWidth && source.height == sourceHeight)
        return BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB).also { result ->
            result.createGraphics().apply {
                color = Color.BLACK
                fillRect(0, 0, targetWidth, targetHeight)
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                val at = AffineTransform().apply {
                    translate(padX.toDouble(), padY.toDouble())
                    scale(scale.toDouble(), scale.toDouble())
                }
                drawImage(source, at, null)
                dispose()
            }
        }
    }
}
