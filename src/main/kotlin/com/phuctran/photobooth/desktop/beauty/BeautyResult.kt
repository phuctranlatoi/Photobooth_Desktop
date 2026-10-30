package com.phuctran.photobooth.desktop.beauty

import java.awt.image.BufferedImage

data class BeautyResult(
    val image: BufferedImage,
    val status: String,
    val latencyMs: Double,
    val diagnostics: Map<String, Any> = emptyMap()
)
