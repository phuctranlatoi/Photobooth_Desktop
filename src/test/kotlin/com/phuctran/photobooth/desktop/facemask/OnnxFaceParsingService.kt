package com.phuctran.photobooth.desktop.facemask

import ai.onnxruntime.*
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.exp

/** Reusable, test-only BiSeNet session. Caller owns and closes the service. */
class OnnxFaceParsingService : AutoCloseable {
    companion object {
        const val SIZE = 512
        const val CLASSES = 19
        fun modelPath(): Path {
            val local = Path.of("app-resources/models/bisenet_face_parsing.onnx")
            if (Files.isRegularFile(local)) return local
            val resource = OnnxFaceParsingService::class.java.getResourceAsStream("/models/bisenet_face_parsing.onnx") ?: error("Missing BiSeNet model: $local")
            return Files.createTempFile("bisenet-face-parsing-", ".onnx").also { path ->
                resource.use { Files.copy(it, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
                path.toFile().deleteOnExit()
            }
        }
    }
    private val environment = OrtEnvironment.getEnvironment()
    private val session = OrtSession.SessionOptions().use { environment.createSession(modelPath().toString(), it) }
    private val input = session.inputInfo.entries.single().also { require(it.value.info is TensorInfo) }
    val inputShape: LongArray = (input.value.info as TensorInfo).shape
    val dynamicSpatial: Boolean = inputShape.size == 4 && inputShape[2] == -1L && inputShape[3] == -1L
    init {
        require(inputShape.size == 4 && inputShape[0] in listOf(1L, -1L) && inputShape[1] == 3L &&
            (dynamicSpatial || (inputShape[2] > 0 && inputShape[2] == inputShape[3]))) { "Unsupported input shape ${inputShape.contentToString()}" }
        println("BiSeNet input '${input.key}': ${inputShape.contentToString()}; ${if (dynamicSpatial) "dynamic 512/640/768" else "fixed ${inputShape[2]}"}")
    }
    val defaultSize: Int get() = if (dynamicSpatial) SIZE else inputShape[2].toInt()
    private var closed = false
    class Parsing(val scores: FloatArray, val labels: ByteArray, val size: Int) {
        private val plane = size * size
        private val probabilities = FloatArray(scores.size)
        val skinNoseProb = FloatArray(plane)
        val neckProb = FloatArray(plane)
        val clothProb = FloatArray(plane)
        val bgProb = FloatArray(plane)
        val hairProb = FloatArray(plane)
        val lipsProb = FloatArray(plane)
        val eyesProb = FloatArray(plane)
        val glassesProb = FloatArray(plane)
        val browsProb = FloatArray(plane)
        init {
            require(scores.size == CLASSES * plane && labels.size == plane)
            for (i in 0 until plane) {
                var maximum = Float.NEGATIVE_INFINITY
                for (c in 0 until CLASSES) maximum = maxOf(maximum, scores[c * plane + i])
                if (!maximum.isFinite()) continue
                var sum = 0f
                for (c in 0 until CLASSES) {
                    val value = exp((scores[c * plane + i] - maximum).toDouble()).toFloat()
                    probabilities[c * plane + i] = value
                    sum += value
                }
                if (!sum.isFinite() || sum <= 0f) continue
                for (c in 0 until CLASSES) probabilities[c * plane + i] /= sum
                fun p(c: Int) = probabilities[c * plane + i]
                skinNoseProb[i] = p(1) + p(10)
                neckProb[i] = p(14) + p(15)
                clothProb[i] = p(16)
                bgProb[i] = p(0)
                hairProb[i] = p(17)
                lipsProb[i] = p(11) + p(12) + p(13)
                eyesProb[i] = p(4) + p(5)
                glassesProb[i] = p(6)
                browsProb[i] = p(2) + p(3)
            }
        }
        fun label(x: Int, y: Int): Int = labels[y.coerceIn(0, size - 1) * size + x.coerceIn(0, size - 1)].toInt() and 255
        fun probability(label: Int, x: Int, y: Int): Float = probabilities[label * plane + y.coerceIn(0, size - 1) * size + x.coerceIn(0, size - 1)]
    }    fun parse(source: BufferedImage, inputSize: Int = defaultSize): Parsing {
        check(!closed) { "OnnxFaceParsingService is already closed" }
        require(inputSize == defaultSize || (dynamicSpatial && inputSize in listOf(512, 640, 768))) {
            "Model input ${inputShape.contentToString()} requires ${if (dynamicSpatial) "512, 640 or 768" else defaultSize} (requested $inputSize)"
        }
        val resized = BufferedImage(inputSize, inputSize, BufferedImage.TYPE_INT_RGB)
        resized.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(source, 0, 0, inputSize, inputSize, null)
            dispose()
        }
        val plane = inputSize * inputSize
        val data = FloatArray(3 * plane)
        val mean = floatArrayOf(.485f, .456f, .406f); val std = floatArrayOf(.229f, .224f, .225f)
        for (y in 0 until inputSize) for (x in 0 until inputSize) {
            val rgb = resized.getRGB(x, y)
            for (c in 0..2) data[c * plane + y * inputSize + x] = (((rgb shr (16 - c * 8)) and 255) / 255f - mean[c]) / std[c]
        }
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(data), longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())).use { tensor ->
            session.run(mapOf(input.key to tensor)).use { result ->
                val output = result[0] as OnnxTensor
                require(output.info.shape.contentEquals(longArrayOf(1, CLASSES.toLong(), inputSize.toLong(), inputSize.toLong()))) {
                    "Unexpected BiSeNet output ${output.info.shape.contentToString()}"
                }
                val scores = FloatArray(CLASSES * plane)
                output.floatBuffer.get(scores)
                val labels = ByteArray(plane)
                for (i in 0 until plane) {
                    var best = 0
                    for (c in 1 until CLASSES) if (scores[c * plane + i] > scores[best * plane + i]) best = c
                    labels[i] = best.toByte()
                }
                return Parsing(scores, labels, inputSize)
            }
        }
    }
    override fun close() {
        if (!closed) {
            closed = true
            session.close()
        }
    }
}

