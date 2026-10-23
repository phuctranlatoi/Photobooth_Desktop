package com.phuctran.photobooth.desktop.facemask

import ai.onnxruntime.*
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Test-only ONNX detector; absent model is reported by [available]. */
class YuNetFaceDetector : FaceDetector {
    companion object {
        const val SCORE_THRESHOLD = 0.85f
        const val NMS_IOU_THRESHOLD = 0.30f
        val modelPath: Path = Path.of("app-resources/models/face_detection_yunet_2023mar.onnx")
        val available: Boolean get() = Files.isRegularFile(modelPath)
    }
    override val name = "YuNet-ONNX"
    private val environment = OrtEnvironment.getEnvironment()
    private val session = OrtSession.SessionOptions().use { environment.createSession(modelPath.toString(), it) }
    init {
        println("YuNet inputs: ${session.inputInfo.map { "${it.key}: ${(it.value.info as? TensorInfo)?.shape?.contentToString()}" }}")
        println("YuNet outputs: ${session.outputInfo.map { "${it.key}: ${(it.value.info as? TensorInfo)?.shape?.contentToString()}" }}")
    }
    private val shape = (session.inputInfo["input"]?.info as? TensorInfo)?.shape
        ?: error("YuNet input 'input' not found: ${session.inputInfo.keys}")
    override fun detectAll(image: BufferedImage): List<DetectedFace> = try {
        require(shape.size == 4 && shape[1] == 3L) { "Unsupported YuNet input ${shape.contentToString()}" }
        val width = if (shape[3] > 0) shape[3].toInt() else 640
        val height = if (shape[2] > 0) shape[2].toInt() else 640
        val resized = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        resized.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(image, 0, 0, width, height, null)
            dispose()
        }
        val plane = width * height
        val data = FloatArray(plane * 3)
        for (y in 0 until height) for (x in 0 until width) {
            val rgb = resized.getRGB(x, y); val i = y * width + x
            data[i] = (rgb and 255).toFloat()
            data[plane + i] = (rgb shr 8 and 255).toFloat()
            data[2 * plane + i] = (rgb shr 16 and 255).toFloat()
        }
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(data), longArrayOf(1, 3, height.toLong(), width.toLong())).use { input ->
            session.run(mapOf("input" to input)).use { result ->
                require(width == 640 && height == 640) { "Unsupported YuNet grid: ${width}x$height" }
                fun tensor(name: String, count: Int): FloatArray {
                    val value = result[name].orElseThrow { IllegalStateException("Missing YuNet output $name") } as OnnxTensor
                    return FloatArray(count).also { values ->
                        require(value.floatBuffer.remaining() == count) { "Unexpected $name size" }
                        value.floatBuffer.get(values)
                    }
                }
                val sx = image.width / 640f; val sy = image.height / 640f
                val candidates = ArrayList<DetectedFace>()
                for (stride in listOf(8, 16, 32)) {
                    val gw = 640 / stride; val gh = 640 / stride; val cells = gw * gh
                    val cls = tensor("cls_$stride", cells); val obj = tensor("obj_$stride", cells)
                    val bbox = tensor("bbox_$stride", cells * 4); val kps = tensor("kps_$stride", cells * 10)
                    for (row in 0 until gh) for (col in 0 until gw) {
                        val idx = row * gw + col
                        val score = sqrt(maxOf(0f, cls[idx]) * maxOf(0f, obj[idx]))
                        if (!score.isFinite() || score < SCORE_THRESHOLD) continue
                        val base = idx * 4
                        val anchorX = col.toFloat(); val anchorY = row.toFloat()
                        val cx = (anchorX + bbox[base]) * stride * sx
                        val cy = (anchorY + bbox[base + 1]) * stride * sy
                        val bw = kotlin.math.exp(bbox[base + 2]) * stride * sx
                        val bh = kotlin.math.exp(bbox[base + 3]) * stride * sy
                        if (!bw.isFinite() || !bh.isFinite() || bw <= 4f || bh <= 4f) continue
                        val x1 = (cx - bw / 2f).coerceIn(0f, image.width.toFloat())
                        val y1 = (cy - bh / 2f).coerceIn(0f, image.height.toFloat())
                        val x2 = (cx + bw / 2f).coerceIn(0f, image.width.toFloat())
                        val y2 = (cy + bh / 2f).coerceIn(0f, image.height.toFloat())
                        if (x2 - x1 <= 4f || y2 - y1 <= 4f) continue
                        val kp = idx * 10
                        fun px(offset: Int) = (anchorX + kps[kp + offset]) * stride * sx
                        fun py(offset: Int) = (anchorY + kps[kp + offset]) * stride * sy
                        val rmx = px(6); val rmy = py(7)
                        val lmx = px(8); val lmy = py(9)
                        val mouthX = (rmx + lmx) * .5f; val mouthY = (rmy + lmy) * .5f
                        val noseX = px(4); val noseY = py(5)
                        val landmarks = FaceLandmarks(px(0), py(1), px(2), py(3), mouthX, mouthY, noseX, noseY, rmx, rmy, lmx, lmy)
                        if (!listOf(landmarks.lx, landmarks.ly, landmarks.rx, landmarks.ry, landmarks.mx, landmarks.my,
                                noseX, noseY, rmx, rmy, lmx, lmy).all { it.isFinite() } || landmarks.d <= 2f) continue
                        candidates.add(DetectedFace(FaceBounds(x1.roundToInt(), y1.roundToInt(),
                            (x2.roundToInt() - x1.roundToInt()), (y2.roundToInt() - y1.roundToInt())), landmarks, score))
                    }
                }
                val kept = ArrayList<DetectedFace>()
                candidates.sortByDescending { it.confidence }
                for (candidate in candidates) {
                    fun iou(a: FaceBounds, b: FaceBounds): Float {
                        val intersection = maxOf(0, minOf(a.x + a.width, b.x + b.width) - maxOf(a.x, b.x)).toFloat() *
                            maxOf(0, minOf(a.y + a.height, b.y + b.height) - maxOf(a.y, b.y))
                        val union = a.width.toFloat() * a.height + b.width.toFloat() * b.height - intersection
                        return if (union > 0) intersection / union else 0f
                    }
                    if (kept.none { iou(it.bounds, candidate.bounds) > NMS_IOU_THRESHOLD }) kept.add(candidate)
                }
                return kept
            }
        }
    } catch (error: Exception) {
        throw IllegalStateException("YuNet detection failed", error)
    }
    override fun close() = session.close()
}
