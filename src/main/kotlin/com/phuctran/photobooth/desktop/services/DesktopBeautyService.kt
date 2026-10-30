package com.phuctran.photobooth.desktop.services

import com.phuctran.photobooth.desktop.beauty.BeautyConfig
import com.phuctran.photobooth.desktop.beauty.BeautyProcessor
import com.phuctran.photobooth.desktop.facemask.CustomerFaceSelector
import com.phuctran.photobooth.desktop.facemask.FaceAlignment
import com.phuctran.photobooth.desktop.facemask.FaceOvalMaskBuilder
import com.phuctran.photobooth.desktop.facemask.FaceRole
import com.phuctran.photobooth.desktop.facemask.OnnxFaceParsingService
import com.phuctran.photobooth.desktop.facemask.YuNetFaceDetector
import java.io.File
import javax.imageio.ImageIO

class DesktopBeautyService : AutoCloseable {
    private var closed = false
    private var detector: YuNetFaceDetector? = null
    private var parser: OnnxFaceParsingService? = null
    private val detectorLock = Any()
    private val parserLock = Any()
    private val selector = CustomerFaceSelector()
    private val builder = FaceOvalMaskBuilder()
    private val processor = BeautyProcessor()

    fun processCapture(sourceImageFile: File, outputImageFile: File, config: BeautyConfig, isCancelled: () -> Boolean = { false }): File {
        if (!config.enabled || config.isZeroEffect) return sourceImageFile
        return try {
            val image = ImageIO.read(sourceImageFile) ?: error("Cannot decode ${sourceImageFile.name}")
            if (isCancelled()) return sourceImageFile
            val faces = synchronized(detectorLock) {
                check(!closed) { "Beauty service is closed" }
                (detector ?: YuNetFaceDetector().also { detector = it }).detectAll(image)
            }
            val roles = selector.classify(image.width, image.height, faces)
            if (roles.none { it.role == FaceRole.FOREGROUND_CUSTOMER }) return sourceImageFile
            val masks = roles.filter { it.role == FaceRole.FOREGROUND_CUSTOMER }.map { item ->
                if (isCancelled()) return sourceImageFile
                val size = synchronized(parserLock) {
                    check(!closed) { "Beauty service is closed" }
                    (parser ?: OnnxFaceParsingService().also { parser = it }).defaultSize
                }
                val alignment = FaceAlignment(item.face.landmarks, size)
                val crop = alignment.crop(image)
                if (isCancelled()) return sourceImageFile
                val parsing = synchronized(parserLock) {
                    check(!closed) { "Beauty service is closed" }
                    (parser ?: OnnxFaceParsingService().also { parser = it }).parse(crop)
                }
                builder.build(image.width, image.height, parsing, item.face, alignment, alignedCrop = crop).final
            }
            if (masks.isEmpty()) return sourceImageFile
            val composite = builder.composite(masks, image.width, image.height)
            if (composite.all { it == 0f }) return sourceImageFile
            if (isCancelled()) return sourceImageFile
            val result = processor.process(image, composite, config)
            if (result.status != "APPLIED") return sourceImageFile
            if (isCancelled()) return sourceImageFile
            outputImageFile.parentFile?.mkdirs()
            check(ImageIO.write(result.image, "png", outputImageFile) && outputImageFile.isFile && outputImageFile.length() > 0L)
            outputImageFile
        } catch (error: Exception) {
            System.err.println("Beauty processing skipped for ${sourceImageFile.name}: ${error.message}")
            sourceImageFile
        }
    }

    override fun close() {
        synchronized(detectorLock) {
            synchronized(parserLock) {
                if (closed) return
                closed = true
                detector?.close()
                parser?.close()
            }
        }
    }
}
