package com.phuctran.photobooth.desktop.engine

data class DetectionConfig(
    val alphaThreshold: Int = 16,
    val minAreaRatio: Float = 0.015f,
    val minWidthRatio: Float = 0.15f,
    val minHeightRatio: Float = 0.10f,
    val maxAreaRatio: Float = 1.0f,
    val minRectangularity: Float = 0.15f,
    val maxSlots: Int = 12
)

data class FrameSlot(
    val id: String,
    val index: Int,
    // Normalized coordinates (0..1)
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val centerX: Float,
    val centerY: Float,
    val areaRatio: Float,
    val shape: String // "RECT"
)

data class FrameDefinition(
    val id: String,
    val name: String,
    val sourceWidthPx: Int,
    val sourceHeightPx: Int,
    val slots: List<FrameSlot>
)

data class DetectionResult(
    val width: Int,
    val height: Int,
    val slots: List<FrameSlot>,
    val warnings: List<String>,
    val punchedImage: java.awt.image.BufferedImage? = null,
    val qrSlot: FrameSlot? = null
)
