package com.phuctran.photobooth.desktop.facemask

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CustomerFaceSelectorTest {
    @Test fun img004ForegroundPairAndBackground() {
        val landmarks = FaceLandmarks(0f, 0f, 10f, 0f, 5f, 10f)
        val faces = listOf(
            DetectedFace(FaceBounds(900, 800, 360, 360), landmarks, .95f),
            DetectedFace(FaceBounds(1700, 820, 350, 375), landmarks, .94f),
            DetectedFace(FaceBounds(3000, 100, 150, 200), landmarks, .91f)
        )
        val result = CustomerFaceSelector().classify(3984, 2656, faces)
        assertEquals(FaceRole.FOREGROUND_CUSTOMER, result.single { it.face == faces[0] }.role)
        assertEquals(FaceRole.FOREGROUND_CUSTOMER, result.single { it.face == faces[1] }.role)
        assertEquals(FaceRole.BACKGROUND_BYSTANDER, result.single { it.face == faces[2] }.role)
    }
}
