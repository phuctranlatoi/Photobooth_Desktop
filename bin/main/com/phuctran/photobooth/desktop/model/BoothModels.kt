package com.phuctran.photobooth.desktop.model

import java.nio.file.Path
import kotlin.math.ceil

enum class LayoutFamily {
    Row,
    Column,
    Grid
}

data class LayoutSlot(
    val index: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

data class LayoutMode(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val family: LayoutFamily,
    val shotCount: Int,
    val selectCount: Int,
    val countdownSeconds: Int = 3,
    val basePrice: Long,
    val mediaLabel: String,
    val accentColor: Long,
    val absoluteSlots: List<LayoutSlot> = emptyList(),
    val gridColumns: Int = 1,
    val printSizeLabel: String = "15 x 10 cm",
    val printAspectRatio: Float = 15f / 10f,
    val paddingTopRatio: Float = 0.04f,
    val paddingBottomRatio: Float = 0.24f,
    val paddingHorizontalRatio: Float = 0.04f,
    val paddingLeftRatio: Float = paddingHorizontalRatio,
    val paddingRightRatio: Float = paddingHorizontalRatio,
    val gapRatio: Float = paddingTopRatio,
    val gapHorizontalRatio: Float = gapRatio,
    val gapVerticalRatio: Float = gapRatio,
    val qrCodeX: Int? = null,
    val qrCodeY: Int? = null,
    val qrCodeSize: Int? = null,
    val photoAspectRatio: Float = photoSlotAspectRatio(
        printAspectRatio = printAspectRatio,
        selectedPhotos = selectCount,
        gridColumns = gridColumns,
        paddingTopRatio = paddingTopRatio,
        paddingBottomRatio = paddingBottomRatio,
        paddingHorizontalRatio = paddingHorizontalRatio,
        gapHorizontalRatio = gapHorizontalRatio,
        gapVerticalRatio = gapVerticalRatio
    )
)

private fun photoSlotAspectRatio(
    printAspectRatio: Float,
    selectedPhotos: Int,
    gridColumns: Int,
    paddingTopRatio: Float,
    paddingBottomRatio: Float,
    paddingHorizontalRatio: Float,
    gapHorizontalRatio: Float,
    gapVerticalRatio: Float
): Float {
    val safeColumns = gridColumns.coerceAtLeast(1)
    val safePhotos = selectedPhotos.coerceAtLeast(1)
    val rows = ceil(safePhotos / safeColumns.toFloat()).toInt().coerceAtLeast(1)
    val slotWidthRatio = (1f - 2 * paddingHorizontalRatio - (safeColumns - 1) * gapHorizontalRatio) / safeColumns
    val availableHeightRatio = (1f / printAspectRatio) - paddingTopRatio - paddingBottomRatio - (rows - 1) * gapVerticalRatio
    val slotHeightRatio = availableHeightRatio / rows
    return (slotWidthRatio / slotHeightRatio).coerceAtLeast(0.2f)
}

data class EffectMode(
    val id: String,
    val title: String,
    val description: String,
    val accentColor: Long,
    val saturation: Float = 1.0f,
    val contrast: Float = 1.0f,
    val brightness: Float = 0.0f,
    val warmth: Float = 0.0f,
    val tint: Float = 0.0f
) {
    fun toComposeColorMatrix(): androidx.compose.ui.graphics.ColorMatrix {
        val matrix = androidx.compose.ui.graphics.ColorMatrix()
        
        // Saturation
        if (saturation != 1.0f) {
            matrix.setToSaturation(saturation)
        }

        // Contrast and Brightness
        if (contrast != 1.0f || brightness != 0.0f) {
            val c = contrast
            val t = (1f - c) * 255f / 2f + (brightness * 255f)
            val cbMatrix = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.timesAssign(cbMatrix)
        }

        // Warmth (Orange/Blue balance)
        if (warmth != 0.0f) {
            val w = warmth * 255f * 0.5f // Scale warmth
            val wMatrix = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, w,       // Increase/Decrease Red
                0f, 1f, 0f, 0f, w * 0.2f,// Slightly adjust Green
                0f, 0f, 1f, 0f, -w,      // Decrease/Increase Blue
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.timesAssign(wMatrix)
        }

        // Tint (Magenta/Green balance)
        if (tint != 0.0f) {
            val tintVal = tint * 255f * 0.5f
            val tintMatrix = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, tintVal, // Increase/Decrease Red
                0f, 1f, 0f, 0f, -tintVal,// Decrease/Increase Green
                0f, 0f, 1f, 0f, tintVal, // Increase/Decrease Blue
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.timesAssign(tintMatrix)
        }

        return matrix
    }
}

data class FramePack(
    val id: String,
    val title: String,
    val description: String,
    val accentColor: Long,
    val isCustom: Boolean = false,
    val isSpecial: Boolean = false,
    val specialEventName: String? = null,
    val customImagePath: Path? = null,
    val targetPrintSize: String? = null,
    val targetLayoutId: String? = null,
    val qrCodeX: Int? = null,
    val qrCodeY: Int? = null,
    val qrCodeSize: Int? = null
)

data class CapturedMoment(
    val index: Int,
    val photoLabel: String,
    val videoLabel: String,
    val photoPath: Path? = null,
    val videoPath: Path? = null
)

data class CapturedPhoto(
    val index: Int,
    val label: String,
    val path: Path
)

data class ExportSummary(
    val printPhotoCount: Int,
    val uploadedPhotoCount: Int,
    val uploadedVideoCount: Int,
    val qrUrl: String? = null,
    val masterUrl: String? = null,
    val albumId: String? = null,
    val outputPath: Path? = null,
    val printStatus: String? = null
)

data class RenderResult(
    val finalImagePath: Path,
    val printImagePath: Path,
    val selectedCount: Int,
    val frameTitle: String
)

data class BoothSession(
    val id: String,
    val boothId: String,
    val productId: String,
    val state: String,
    val qrCodeUrl: String? = null,
    val photoUrls: List<String> = emptyList(),
    val videoUrls: List<String> = emptyList(),
    val masterUrl: String? = null,
    val startedAt: Long,
    val paidAt: Long?,
    val completedAt: Long?
)

val DefaultLayoutModes = listOf(
    LayoutMode(
        id = "fallback_layout",
        title = "Chưa có bố cục",
        subtitle = "Đang tải",
        description = "Chưa tải được bố cục từ Firebase. Vui lòng kiểm tra kết nối.",
        family = LayoutFamily.Column,
        shotCount = 1,
        selectCount = 1,
        basePrice = 0L,
        mediaLabel = "Fallback",
        accentColor = 0xFF475569,
        gridColumns = 1,
        printSizeLabel = "10 x 15 cm",
        printAspectRatio = 10f / 15f,
        photoAspectRatio = 1f,
        paddingTopRatio = 0.1f,
        paddingBottomRatio = 0.1f,
        paddingLeftRatio = 0.1f,
        paddingRightRatio = 0.1f,
        gapRatio = 0.1f
    )
)

val DefaultEffectModes = listOf(
    EffectMode(
        id = "normal",
        title = "Tự nhiên",
        description = "Màu tươi, nét cao, hợp đèn studio.",
        accentColor = 0xFF475569
    ),
    EffectMode(
        id = "black_white",
        title = "Trắng Đen",
        description = "Tối giản, cổ điển, nổi thần thái khuôn mặt.",
        accentColor = 0xFF2F3338,
        saturation = 0.0f,
        contrast = 1.2f,
        brightness = 0.05f
    ),
    EffectMode(
        id = "vintage",
        title = "Cổ điển",
        description = "Hạt phim nhẹ và ánh sáng hoài cổ.",
        accentColor = 0xFF7A7268,
        saturation = 0.6f,
        contrast = 0.9f,
        warmth = 0.3f,
        tint = 0.1f,
        brightness = 0.1f
    )
)

val DefaultFramePacks = listOf(
    FramePack(
        id = "studio_line",
        title = "Studio Line",
        description = "Khung viền mảnh, sạch, hợp nhóm bạn và gia đình.",
        accentColor = 0xFF475569
    ),
    FramePack(
        id = "pearl_white",
        title = "Pearl White",
        description = "Trắng ngọc trai, sạch và sang.",
        accentColor = 0xFFB7C0C9
    ),
    FramePack(
        id = "soft_taupe",
        title = "Soft Taupe",
        description = "Tông xám ấm nhẹ, nhìn tự nhiên và dễ dùng.",
        accentColor = 0xFF9A8F83
    ),
    FramePack(
        id = "minimal_stamp",
        title = "Minimal Stamp",
        description = "Khung mảnh, chữ nhỏ, dễ thay bằng asset sau.",
        accentColor = 0xFF4F766F
    )
)
