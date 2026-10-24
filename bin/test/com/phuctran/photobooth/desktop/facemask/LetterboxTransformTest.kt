package com.phuctran.photobooth.desktop.facemask

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LetterboxTransformTest {
    @Test fun mappingRoundTrips() {
        for ((width, height) in listOf(3984 to 2656, 1920 to 1080, 640 to 640)) {
            val transform = LetterboxTransform(width, height, 640, 640)
            for (x in listOf(0f, width / 3f, width.toFloat())) {
                assertTrue(kotlin.math.abs(transform.toSourceX(transform.toTargetX(x)) - x) < 0.01f)
            }
            for (y in listOf(0f, height / 3f, height.toFloat())) {
                assertTrue(kotlin.math.abs(transform.toSourceY(transform.toTargetY(y)) - y) < 0.01f)
            }
        }
    }
}
