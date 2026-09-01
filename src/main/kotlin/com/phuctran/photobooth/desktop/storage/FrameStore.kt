package com.phuctran.photobooth.desktop.storage

import com.phuctran.photobooth.desktop.model.FramePack
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.stream.Collectors
import kotlin.io.path.nameWithoutExtension
import com.google.gson.Gson
import com.google.gson.JsonObject

class FrameStore(projectDir: Path) {
    val frameDir = projectDir.resolve("data").resolve("frames")
    val coverDir = projectDir.resolve("data").resolve("covers")

    fun getSpecialEventCoverPath(eventName: String): Path {
        return coverDir.resolve("$eventName.png")
    }

    fun setSpecialEventCover(eventName: String, source: Path) {
        val dest = getSpecialEventCoverPath(eventName)
        Files.createDirectories(dest.parent)
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING)
    }

    fun loadFrames(): List<FramePack> {
        Files.createDirectories(frameDir)
        val customFrames = mutableListOf<FramePack>()
        Files.walk(frameDir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.isSupportedImage() }
                .sorted()
                .forEach { customFrames.add(it.toFramePack()) }
        }
        return customFrames
    }

    fun addCustomFrame(
        source: Path, 
        printSizeLabel: String, 
        layoutId: String, 
        isSpecial: Boolean = false,
        specialEventName: String? = null,
        qrCodeX: Int? = null,
        qrCodeY: Int? = null,
        qrCodeSize: Int? = null
    ): FramePack {
        require(Files.isRegularFile(source)) {
            "Không tìm thấy file frame: $source"
        }
        require(source.isSupportedImage()) {
            "Frame cần là PNG trong suốt."
        }

        val category = if (isSpecial) "Special" else "Standard"
        var targetDir = frameDir.resolve(printSizeLabel).resolve(layoutId).resolve(category)
        if (isSpecial && !specialEventName.isNullOrBlank()) {
            targetDir = targetDir.resolve(specialEventName.trim())
        }
        Files.createDirectories(targetDir)
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val slug = source.fileName.toString()
            .substringBeforeLast('.')
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "custom_frame" }
        val extension = source.fileName.toString().substringAfterLast('.', "png").lowercase(Locale.ROOT)
        val destination = targetDir.resolve("${slug}_$timestamp.$extension")
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)

        if (qrCodeX != null && qrCodeY != null && qrCodeSize != null) {
            val jsonPath = targetDir.resolve("${destination.nameWithoutExtension}.json")
            val jsonObj = JsonObject().apply {
                addProperty("qrCodeX", qrCodeX)
                addProperty("qrCodeY", qrCodeY)
                addProperty("qrCodeSize", qrCodeSize)
            }
            Files.writeString(jsonPath, Gson().toJson(jsonObj))
        }

        return destination.toFramePack()
    }

    private fun Path.toFramePack(): FramePack {
        val name = fileName.toString().substringBeforeLast('.')
        val title = name
            .replace('_', ' ')
            .replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { char -> char.titlecase(Locale.ROOT) } }
            .ifBlank { "Custom Frame" }

        // The path structure is: data/frames/<printSizeLabel>/<layoutId>/<Standard|Special>/[EventName]/frame.png
        val relativePath = frameDir.relativize(this)
        val parts = relativePath.map { it.toString() }
        
        val targetPrintSize = if (parts.size >= 1) parts[0] else null
        val targetLayoutId = if (parts.size >= 2) parts[1] else null
        val isSpecial = parts.any { it.equals("Special", ignoreCase = true) }
        val specialEventName = if (isSpecial && parts.size >= 5) parts[3] else null

        // Đọc cấu hình QR từ file JSON (nếu có)
        val jsonPath = this.parent.resolve("$name.json")
        var qrX: Int? = null
        var qrY: Int? = null
        var qrSize: Int? = null

        if (Files.exists(jsonPath)) {
            runCatching {
                val jsonStr = Files.readString(jsonPath)
                val jsonObject = Gson().fromJson(jsonStr, JsonObject::class.java)
                if (jsonObject.has("qrCodeX")) qrX = jsonObject.get("qrCodeX").asInt
                if (jsonObject.has("qrCodeY")) qrY = jsonObject.get("qrCodeY").asInt
                if (jsonObject.has("qrCodeSize")) qrSize = jsonObject.get("qrCodeSize").asInt
            }
        }

        return FramePack(
            id = name,
            title = title,
            description = "Frame custom từ thư mục data/frames.",
            accentColor = 0xFF5F6B7A,
            isCustom = true,
            isSpecial = isSpecial,
            specialEventName = specialEventName,
            customImagePath = this,
            targetPrintSize = targetPrintSize,
            targetLayoutId = targetLayoutId,
            qrCodeX = qrX,
            qrCodeY = qrY,
            qrCodeSize = qrSize
        )
    }

    private fun Path.isSupportedImage(): Boolean {
        val fileNameStr = fileName.toString()
        if (fileNameStr.startsWith("thumb_")) return false
        val extension = fileNameStr.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension == "png"
    }
}
