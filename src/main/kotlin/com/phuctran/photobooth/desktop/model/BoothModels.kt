package com.phuctran.photobooth.desktop.model

import java.nio.file.Path
import kotlin.math.ceil

enum class LayoutFamily {
    Row,
    Column,
    Grid
}

data class LayoutMode(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val family: LayoutFamily,
    val shotCount: Int,
    val selectCount: Int,
    val basePrice: Long,
    val mediaLabel: String,
    val accentColor: Long,
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
    val accentColor: Long
)

data class FramePack(
    val id: String,
    val title: String,
    val description: String,
    val accentColor: Long,
    val isCustom: Boolean = false,
    val customImagePath: Path? = null,
    val targetPrintSize: String? = null,
    val targetLayoutId: String? = null
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
        id = "Quoc_khanh_2_9",
        title = "Quốc Khánh 2/9",
        subtitle = "Frame 5x15",
        description = "Quốc Khánh 2/9",
        family = LayoutFamily.Column,
        shotCount = 3,
        selectCount = 2,
        basePrice = 2000L,
        mediaLabel = "Frame 5x15 • 2 ảnh",
        accentColor = 0xFF475569,
        gridColumns = 1,
        printSizeLabel = "5 x 15 cm",
        printAspectRatio = 5f / 15f,
        photoAspectRatio = 4.245f / 5f,
        paddingTopRatio = 3.36f / 4.7f,
        paddingBottomRatio = 0.5f / 4.7f,
        paddingLeftRatio = 0.19f / 4.7f,
        paddingRightRatio = 0.215f / 4.7f,
        gapRatio = 0.19f / 4.7f
    ),
    LayoutMode(
        id = "custom_v2",
        title = "3 ảnh ngang 10x15",
        subtitle = "Frame 10x15",
        description = "Ba ảnh ngang xếp dọc, chừa footer lớn để gắn frame.",
        family = LayoutFamily.Column,
        shotCount = 3,
        selectCount = 3,
        basePrice = 2000L,
        mediaLabel = "Frame 10x15 • 3 ảnh",
        accentColor = 0xFF475569,
        gridColumns = 1,
        printSizeLabel = "10 x 15 cm",
        printAspectRatio = 10f / 15f,
        photoAspectRatio = 6.6f / 4.807f,
        paddingTopRatio = 0.53f / 16.125f,
        paddingBottomRatio = 0.75f / 16.125f,
        paddingLeftRatio = 3.3f / 10.75f,
        paddingRightRatio = 0.758f / 10.75f,
        gapRatio = 0.37f / 16.125f,
    ),
    LayoutMode(
        id = "custom_126x187",
        title = "2 ảnh dọc",
        subtitle = "Frame 10x15",
        description = "2 ảnh dọc lệch trái.",
        family = LayoutFamily.Column,
        shotCount = 2,
        selectCount = 2,
        basePrice = 2000L,
        mediaLabel = "Frame 10x15 • 2 ảnh",
        accentColor = 0xFF5F6B7A,
        gridColumns = 1,
        printSizeLabel = "10 x 15 cm",
        printAspectRatio = 10f / 15f,
        photoAspectRatio = 57.5f / 81f,
        paddingTopRatio = 14.3f / 126f,
        paddingBottomRatio = 10f / 126f,
        paddingLeftRatio = 7.5f / 126f,
        paddingRightRatio = 60.5f / 126f,
        gapRatio = 2.5f / 126f
    ),
    LayoutMode(
        id = "strip_3_landscape",
        title = "3 ảnh ngang",
        subtitle = "Strip 5x15",
        description = "Ba ảnh ngang xếp dọc, chừa footer lớn để gắn frame.",
        family = LayoutFamily.Grid,
        shotCount = 3,
        selectCount = 3,
        basePrice = 45000L,
        mediaLabel = "Strip 5x15 • 3 ảnh",
        accentColor = 0xFF475569,
        gridColumns = 1,
        printSizeLabel = "5 x 15 cm",
        printAspectRatio = 5.05f / 15f,
        photoAspectRatio = 310f / 279f,
        paddingTopRatio = 2.2f / 50f,
        paddingLeftRatio = 0.04f,
        paddingRightRatio = 0.04f,
        paddingHorizontalRatio = 3f / 50f,
        gapRatio = 1.9f / 50f
    ),
        LayoutMode(
        id = "Frame (Bài thuyết trình) (7)",
        title = "4 ảnh ngang nhỏ trang trí",
        subtitle = "Frame 5x15",
        description = "4 ảnh ngang nhỏ trang trí",
        family = LayoutFamily.Grid, // Bấm chọn Grid nếu frame này là lưới
        shotCount = 4,
        selectCount = 4,
        basePrice = 50000L,
        mediaLabel = "Strip 5x15 • 4 ảnh",
        accentColor = 0xFF475569,
        gridColumns = 1, // Đổi thành 2 nếu frame là lưới 2 cột
        printSizeLabel = "5 x 15 cm",
        printAspectRatio = 5f / 15f,
        photoAspectRatio = 1.5233f,
        paddingTopRatio = 0.2142f,
        paddingBottomRatio = 0.2336f,
        paddingLeftRatio = 0.0220f,
        paddingRightRatio = 0.0186f,
        gapHorizontalRatio = 0.0110f,
        gapVerticalRatio = 0.0110f
    ),
        
    LayoutMode(
        id = "strip_2_landscape",
        title = "2 ảnh dọc",
        subtitle = "Strip 5x15",
        description = "2 ảnh xếp dọc, footer lớn đồng bộ.",
        family = LayoutFamily.Grid,
        shotCount = 8,
        selectCount = 2,
        basePrice = 2000L,
        mediaLabel = "Strip 5x15 • 2 ảnh",
        accentColor = 0xFF5F6B7A,
        gridColumns = 1,
        printSizeLabel = "5 x 15 cm",
        printAspectRatio = 5f / 15f,
        photoAspectRatio = 216f / 302f,
        paddingTopRatio = 4f / 50f,
        paddingHorizontalRatio = 3f / 50f,
        gapRatio = 4f / 50f
    ),
    LayoutMode(
        id = "strip_4_landscape",
        title = "4 ảnh ngang v2",
        subtitle = "Strip 5x15",
        description = "Bốn ảnh ngang xếp dọc, footer gọn cho logo hoặc chữ nhỏ.",
        family = LayoutFamily.Grid,
        shotCount = 8,
        selectCount = 4,
        basePrice = 2000L,
        mediaLabel = "Strip 5x15 • 4 ảnh",
        accentColor = 0xFF5F6B7A,
        gridColumns = 1,
        printSizeLabel = "5 x 15 cm",
        printAspectRatio = 5f / 15f,
        paddingTopRatio = 0.04f,
        paddingHorizontalRatio = 0.04f,
        paddingBottomRatio = 0.12f
    ),
    LayoutMode(
        id = "portrait_4_roomy",
        title = "4 ảnh 2x2",
        subtitle = "4R dọc",
        description = "Bốn ô ảnh cao vừa, chừa footer lớn đồng bộ.",
        family = LayoutFamily.Grid,
        shotCount = 4,
        selectCount = 4,
        basePrice = 65000L,
        mediaLabel = "4R dọc • 4 ảnh",
        accentColor = 0xFF7A7268,
        gridColumns = 2,
        printSizeLabel = "10 x 15 cm",
        printAspectRatio = 9.08f / 13.62f,
        photoAspectRatio = 4.16f/5.71f,
        paddingTopRatio = 0.25f/9.08f,
        paddingHorizontalRatio = 0.02f,
        paddingBottomRatio = 1.67f/9.08f,
        gapRatio = 0.17f/9.08f,
        gapHorizontalRatio = 0.12f/9.08f,
        gapVerticalRatio = 0.17f/9.08f,
        paddingLeftRatio = 0.29f/9.08f,
        paddingRightRatio = 0.3f/9.08f
    ),
    LayoutMode(
        id = "portrait_4_compact",
        title = "4 ảnh lớn",
        subtitle = "4R dọc",
        description = "Bốn ô ảnh cao hơn, footer nhỏ để giữ ảnh nổi bật.",
        family = LayoutFamily.Grid,
        shotCount = 8,
        selectCount = 4,
        basePrice = 65000L,
        mediaLabel = "4R dọc • 4 ảnh lớn",
        accentColor = 0xFF5F6B7A,
        gridColumns = 2,
        printSizeLabel = "10 x 15 cm",
        printAspectRatio = 10f / 15f,
        paddingTopRatio = 0.02f,
        paddingHorizontalRatio = 0.02f,
        paddingBottomRatio = 0.05f
    ),
    LayoutMode(
        id = "portrait_6_grid",
        title = "6 ảnh lưới",
        subtitle = "4R dọc",
        description = "Sáu ô ảnh theo lưới 2x3, gần mẫu sticker frame.",
        family = LayoutFamily.Grid,
        shotCount = 8,
        selectCount = 6,
        basePrice = 70000L,
        mediaLabel = "4R dọc • 6 ảnh",
        accentColor = 0xFF4F6373,
        gridColumns = 2,
        printSizeLabel = "10 x 15 cm",
        printAspectRatio = 10f / 15f,
        paddingTopRatio = 0.02f,
        paddingHorizontalRatio = 0.02f,
        paddingBottomRatio = 0.05f
    ),
    LayoutMode(
        id = "landscape_4_roomy",
        title = "4 ảnh 2x2",
        subtitle = "4R ngang",
        description = "Bốn ô ảnh rộng, footer vừa để thêm logo hoặc ngày chụp.",
        family = LayoutFamily.Grid,
        shotCount = 8,
        selectCount = 4,
        basePrice = 65000L,
        mediaLabel = "4R ngang • 4 ảnh",
        accentColor = 0xFF4F766F,
        gridColumns = 2,
        printSizeLabel = "15 x 10 cm",
        printAspectRatio = 15f / 10f,
        paddingTopRatio = 0.04f,
        paddingHorizontalRatio = 0.03f,
        paddingBottomRatio = 0.15f
    ),
    LayoutMode(
        id = "landscape_4_compact",
        title = "4 ảnh lớn",
        subtitle = "4R ngang",
        description = "Bốn ô ảnh rộng 2x2, footer mỏng để ưu tiên ảnh.",
        family = LayoutFamily.Grid,
        shotCount = 8,
        selectCount = 4,
        basePrice = 65000L,
        mediaLabel = "4R ngang • 4 ảnh lớn",
        accentColor = 0xFF5F6B7A,
        gridColumns = 2,
        printSizeLabel = "15 x 10 cm",
        printAspectRatio = 15f / 10f,
        paddingTopRatio = 0.02f,
        paddingHorizontalRatio = 0.02f,
        paddingBottomRatio = 0.05f
    ),
    LayoutMode(
        id = "landscape_6_grid",
        title = "6 ảnh 3x2",
        subtitle = "4R ngang",
        description = "Sáu ô ảnh theo lưới 3x2, footer rất gọn.",
        family = LayoutFamily.Grid,
        shotCount = 8,
        selectCount = 6,
        basePrice = 75000L,
        mediaLabel = "4R ngang • 6 ảnh",
        accentColor = 0xFF4F6373,
        gridColumns = 3,
        printSizeLabel = "15 x 10 cm",
        printAspectRatio = 15f / 10f,
        paddingTopRatio = 0.02f,
        paddingHorizontalRatio = 0.02f,
        paddingBottomRatio = 0.05f
    )
)

val DefaultEffectModes = listOf(
    EffectMode(
        id = "normal",
        title = "Normal",
        description = "Màu tươi, nét cao, hợp đèn studio.",
        accentColor = 0xFF475569
    ),
    EffectMode(
        id = "black_white",
        title = "Black & White",
        description = "Tối giản, cổ điển, nổi thần thái khuôn mặt.",
        accentColor = 0xFF2F3338
    ),
    EffectMode(
        id = "vintage",
        title = "Vintage",
        description = "Hạt phim nhẹ và ánh sáng hoài cổ.",
        accentColor = 0xFF7A7268
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
