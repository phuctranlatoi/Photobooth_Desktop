package com.phuctran.photobooth.desktop.beauty

import com.phuctran.photobooth.desktop.facemask.FaceAlignment
import com.phuctran.photobooth.desktop.facemask.DetectedFace
import com.phuctran.photobooth.desktop.facemask.FaceOvalMaskBuilder
import com.phuctran.photobooth.desktop.facemask.OnnxFaceParsingService
import com.phuctran.photobooth.desktop.facemask.YuNetFaceDetector
import java.awt.image.BufferedImage

/** Offline experiment only: never called by the production capture workflow. */
class BeautyMaskPipeline internal constructor(private val detectOverride: ((BufferedImage) -> List<DetectedFace>)? = null) : AutoCloseable {
    private val detectorDelegate = lazy { YuNetFaceDetector() }
    private val parserDelegate = lazy { OnnxFaceParsingService() }
    private val detector by detectorDelegate
    private val parser by parserDelegate
    private val builder = FaceOvalMaskBuilder()

    data class Output(val result: BeautyResult, val mask: FloatArray, val faceCount: Int)

    fun run(image: BufferedImage, config: BeautyConfig = BeautyConfig()): Output {
        var detectedFaceCount = 0
        try {
            val faces = detectOverride?.invoke(image) ?: detector.detectAll(image)
            detectedFaceCount = faces.size
            val masks = faces.mapNotNull { face ->
                try {
                    val alignment = FaceAlignment(face.landmarks, parser.defaultSize)
                    builder.build(image.width, image.height, parser.parse(alignment.crop(image)), face, alignment).final
                } catch (_: Exception) {
                    null
                }
            }
            val combined = builder.composite(masks, image.width, image.height)
            if (masks.isEmpty()) {
                val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
                val status = if (pixels.isNotEmpty() && pixels.all { it == pixels[0] }) "TRUE_NEGATIVE" else "FALLBACK_NO_FACE"
                return Output(BeautyResult(PixelSafeCompositor.copy(image), status, 0.0), combined, 0)
            }
            if (combined.all { it == 0f }) {
                return Output(BeautyResult(PixelSafeCompositor.copy(image), "VALID_SAFE_FALLBACK", 0.0), combined, masks.size)
            }
            return Output(BeautyProcessor().process(image, combined, config), combined, masks.size)
        } catch (_: Exception) {
            val combined = FloatArray(image.width * image.height)
            return Output(BeautyResult(PixelSafeCompositor.copy(image), "FALLBACK_PROCESSING_ERROR", 0.0), combined, detectedFaceCount)
        }
    }

    override fun close() {
        var failure: Exception? = null
        if (parserDelegate.isInitialized()) try { parser.close() } catch (e: Exception) { failure = e }
        if (detectorDelegate.isInitialized()) try { detector.close() } catch (e: Exception) {
            if (failure == null) failure = e else failure.addSuppressed(e)
        }
        if (failure != null) throw failure
    }
}
