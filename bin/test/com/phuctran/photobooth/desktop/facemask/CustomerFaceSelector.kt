package com.phuctran.photobooth.desktop.facemask

import kotlin.math.hypot
import kotlin.math.sqrt

enum class FaceRole { FOREGROUND_CUSTOMER, BACKGROUND_BYSTANDER, REJECTED }

data class ClassifiedFace(
    val face: DetectedFace,
    val role: FaceRole,
    val priorityScore: Float,
    val areaRatio: Float,
    val imageAreaFraction: Float,
    val reasons: List<String>
)

class CustomerFaceSelector(
    val minConfidence: Float = 0.80f,
    val minAreaRatio: Float = 0.28f,
    val minImageAreaFraction: Float = 0.0035f,
    val clusterAffinityRatio: Float = 0.22f
) {
    fun classify(imageWidth: Int, imageHeight: Int, faces: List<DetectedFace>): List<ClassifiedFace> {
        require(imageWidth > 0 && imageHeight > 0)
        val (valid, invalid) = faces.partition { face ->
            face.confidence.isFinite() && face.confidence >= minConfidence &&
                face.bounds.width > 0 && face.bounds.height > 0 &&
                listOf(face.landmarks.lx, face.landmarks.ly, face.landmarks.rx, face.landmarks.ry,
                    face.landmarks.mx, face.landmarks.my, face.landmarks.nx, face.landmarks.ny).all { it.isFinite() }
        }
        val maxArea = valid.maxOfOrNull { it.bounds.width.toLong() * it.bounds.height } ?: 0L
        val imageArea = imageWidth.toLong() * imageHeight
        data class Metrics(val face: DetectedFace, val ratio: Float, val fraction: Float, val score: Float, val centerY: Float)
        val metrics = valid.map { face ->
            val bounds = face.bounds
            val area = bounds.width.toLong() * bounds.height
            val ratio = area.toFloat() / maxArea
            val fraction = area.toFloat() / imageArea
            val centerX = bounds.x + bounds.width / 2f
            val centerY = bounds.y + bounds.height / 2f
            val normDist = hypot((centerX - imageWidth / 2f) / (imageWidth / 2f),
                (centerY - imageHeight / 2f) / (imageHeight / 2f))
            val centrality = 1f - (normDist / sqrt(2f)).coerceIn(0f, 1f)
            Metrics(face, ratio, fraction, 0.60f * sqrt(ratio) + 0.25f * centrality + 0.15f * face.confidence, centerY)
        }
        val primary = metrics.filter { it.ratio >= minAreaRatio && it.fraction >= minImageAreaFraction }
        val classified = metrics.map { metric ->
            val clustered = metric.ratio >= clusterAffinityRatio && primary.any {
                it !== metric && kotlin.math.abs(metric.centerY - it.centerY) <=
                    maxOf(metric.face.bounds.height, it.face.bounds.height).toFloat()
            }
            val (role, reason) = when {
                metric in primary -> FaceRole.FOREGROUND_CUSTOMER to
                    "Primary foreground subject (areaRatio=${metric.ratio}, fraction=${metric.fraction})"
                clustered -> FaceRole.FOREGROUND_CUSTOMER to "Clustered with primary foreground subject"
                else -> FaceRole.BACKGROUND_BYSTANDER to
                    "Background bystander (areaRatio=${metric.ratio} < $minAreaRatio)"
            }
            ClassifiedFace(metric.face, role, metric.score, metric.ratio, metric.fraction, listOf(reason))
        } + invalid.map { face ->
            ClassifiedFace(face, FaceRole.REJECTED, Float.NEGATIVE_INFINITY, 0f, 0f,
                listOf("Invalid bounds, coordinates, or confidence below $minConfidence"))
        }
        return classified.sortedByDescending { it.priorityScore }
    }
}
