package com.phuctran.photobooth.desktop.config

import java.nio.file.Files
import java.nio.file.Path

data class DesktopBoothConfig(
    val boothId: String = "booth_1",
    val boothApiKey: String = "",
    val webAlbumBaseUrl: String = "https://web-photobooth-pink.vercel.app/",
    val cloudinaryCloudName: String = "",
    val cloudinaryUploadPreset: String = "",
    val albumExpiresInDays: Int = 7,
    val enableSystemPrint: Boolean = false,
    val useHotFolder: Boolean = true,
    val hotFolderPath: String = "E:\\Photobooth\\HotFolder",
    val useNativeSdk: Boolean = true,
    val useCliCamera: Boolean = false,
    val cliCaptureCommand: String = "CameraControlCmd.exe /capturenoaf /filename \"%s\"",
    val enableLocalServer: Boolean = true,
    val localServerPort: Int = 8080,
    val appDataDir: Path? = null,
    val envSource: Path? = null,
    val payosClientId: String = "",
    val payosApiKey: String = "",
    val payosChecksumKey: String = ""
) {
    val authHeader: String get() = "Bearer $boothApiKey"
    val canUploadAlbum: Boolean
        get() = boothApiKey.isNotBlank() &&
            cloudinaryCloudName.isNotBlank() &&
            cloudinaryUploadPreset.isNotBlank() &&
            webAlbumBaseUrl.isNotBlank()
}

object DesktopConfigLoader {
    fun load(appDataDir: Path): DesktopBoothConfig {
        val workingEnv = DesktopAppPaths.workingDir().resolve(".env")
        val appEnv = appDataDir.resolve(".env")
        val env = readDotEnv(workingEnv) + readDotEnv(appEnv) + System.getenv()
        val envSource = when {
            Files.isRegularFile(appEnv) -> appEnv
            Files.isRegularFile(workingEnv) -> workingEnv
            else -> null
        }
        val webAlbumBaseUrl = (env["WEB_ALBUM_BASE_URL"]
            ?: env["ALBUM_BASE_URL"]
            ?: "https://web-photobooth-pink.vercel.app/")
            .toWebAlbumApiBaseUrl()
        return DesktopBoothConfig(
            boothId = env["BOOTH_ID"].orDefault("booth_1"),
            boothApiKey = env["BOOTH_API_KEY"].orEmpty(),
            webAlbumBaseUrl = webAlbumBaseUrl,
            cloudinaryCloudName = env["CLOUDINARY_CLOUD_NAME"].orEmpty(),
            cloudinaryUploadPreset = env["CLOUDINARY_UPLOAD_PRESET"].orEmpty(),
            albumExpiresInDays = env["ALBUM_EXPIRES_IN_DAYS"]?.toIntOrNull()?.coerceAtLeast(1) ?: 7,
            enableSystemPrint = env["ENABLE_SYSTEM_PRINT"].toBooleanFlag(default = false),
            useHotFolder = env["USE_HOT_FOLDER"]?.toBooleanStrictOrNull() ?: true,
            hotFolderPath = env["HOT_FOLDER_PATH"].orDefault("E:\\Photobooth\\HotFolder"),
            useNativeSdk = env["USE_NATIVE_SDK"]?.toBooleanStrictOrNull() ?: true,
            useCliCamera = env["USE_CLI_CAMERA"]?.toBooleanStrictOrNull() ?: false,
            cliCaptureCommand = env["CLI_CAPTURE_COMMAND"].orDefault("CameraControlCmd.exe /capturenoaf /filename \"%s\""),
            enableLocalServer = env["ENABLE_LOCAL_SERVER"].toBooleanFlag(default = true),
            localServerPort = env["LOCAL_SERVER_PORT"]?.toIntOrNull() ?: 8080,
            appDataDir = appDataDir,
            envSource = envSource,
            payosClientId = env["PAYOS_CLIENT_ID"].orEmpty(),
            payosApiKey = env["PAYOS_API_KEY"].orEmpty(),
            payosChecksumKey = env["PAYOS_CHECKSUM_KEY"].orEmpty()
        )
    }

    private fun readDotEnv(path: Path): Map<String, String> {
        if (!Files.isRegularFile(path)) return emptyMap()
        return Files.readAllLines(path)
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isBlank() || trimmed.startsWith("#") || "=" !in trimmed) {
                    null
                } else {
                    val key = trimmed.substringBefore("=").trim()
                    val value = trimmed.substringAfter("=").trim().trim('"').trim('\'')
                    key to value
                }
            }
            .toMap()
    }

    private fun String?.orDefault(default: String): String = if (isNullOrBlank()) default else this

    private fun String.toWebAlbumApiBaseUrl(): String {
        val trimmed = trim().trimEnd('/')
        return if (trimmed.endsWith("/a", ignoreCase = true)) {
            trimmed.dropLast(2)
        } else {
            trimmed
        }
    }

    private fun String?.toBooleanFlag(default: Boolean): Boolean {
        val normalized = this?.trim()?.lowercase()
        return when (normalized) {
            "1", "true", "yes", "y", "on" -> true
            "0", "false", "no", "n", "off" -> false
            else -> default
        }
    }
}
