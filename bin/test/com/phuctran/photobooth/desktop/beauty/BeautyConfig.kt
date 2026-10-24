package com.phuctran.photobooth.desktop.beauty

enum class BeautyPreset(val smoothingStrength: Int, val whiteningStrength: Int, val rosyStrength: Int, val detailPreservation: Int) {
    NATURAL(30, 20, 12, 85), STUDIO(60, 50, 25, 65), BRIGHT(70, 75, 20, 55), STRONG(85, 95, 25, 40)
}

data class BeautyConfig(
    val smoothingStrength: Int = 60,
    val whiteningStrength: Int = 50,
    val rosyStrength: Int = 25,
    val detailPreservation: Int = 65,
    val enabled: Boolean = true,
    val preset: BeautyPreset = BeautyPreset.STUDIO
) {
    companion object {
        fun fromPreset(preset: BeautyPreset, enabled: Boolean = true) = BeautyConfig(
            preset.smoothingStrength, preset.whiteningStrength, preset.rosyStrength,
            preset.detailPreservation, enabled, preset
        )
    }
    init {
        require(smoothingStrength in 0..100) { "smoothing out of bounds [0, 100]: $smoothingStrength" }
        require(whiteningStrength in 0..100) { "whitening out of bounds [0, 100]: $whiteningStrength" }
        require(rosyStrength in 0..100) { "rosy out of bounds [0, 100]: $rosyStrength" }
        require(detailPreservation in 0..100) { "detailPreservation out of bounds [0, 100]: $detailPreservation" }
    }
    val isZeroEffect: Boolean get() = !enabled || (smoothingStrength == 0 && whiteningStrength == 0 && rosyStrength == 0)
}
