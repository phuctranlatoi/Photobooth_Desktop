package com.phuctran.photobooth.desktop.services

import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import com.phuctran.photobooth.desktop.imaging.DesktopImageProcessor
import com.phuctran.photobooth.desktop.model.CapturedMoment
import com.phuctran.photobooth.desktop.model.FramePack
import com.phuctran.photobooth.desktop.model.LayoutMode
import com.phuctran.photobooth.desktop.remote.AlbumUploadResult
import com.phuctran.photobooth.desktop.remote.CreateAlbumRequest
import com.phuctran.photobooth.desktop.remote.DesktopCloudinaryClient
import com.phuctran.photobooth.desktop.remote.DesktopWebAlbumClient
import java.nio.file.Path

class DesktopAlbumUploader(
    private val projectDir: Path,
    private val config: DesktopBoothConfig,
    private val cloudinaryClient: DesktopCloudinaryClient = DesktopCloudinaryClient(),
    private val webAlbumClient: DesktopWebAlbumClient = DesktopWebAlbumClient(),
    private val imageProcessor: DesktopImageProcessor = DesktopImageProcessor()
) {
    fun uploadSessionPhotos(
        sessionId: String,
        preCreatedAlbumId: String?,
        capturedMoments: List<CapturedMoment>,
        masterPrint: Path?
    ): AlbumUploadResult {
        if (!config.canUploadAlbum) {
            return AlbumUploadResult(
                albumId = null,
                albumUrl = null,
                originalPhotoUrls = emptyList(),
                finalPhotoUrl = null,
                uploadedCount = 0,
                finalizedCount = 0,
                errorMessage = "Thiếu cấu hình .env."
            )
        }

        return runCatching {
            val exportDir = projectDir.resolve("data").resolve("sessions").resolve(sessionId).resolve("exports")
            val originalJpegs = capturedMoments.mapNotNull { moment ->
                moment.photoPath?.let { imageProcessor.ensureJpeg(it, exportDir, "original_${moment.index}") }
            }

            val uploadedOriginalAssets = originalJpegs.mapIndexedNotNull { index, file ->
                cloudinaryClient.uploadAssetToCloudinary(
                    config = config,
                    sessionId = sessionId,
                    file = file,
                    position = index,
                    kind = "ORIGINAL"
                )
            }

            val uploadedFinalAsset = masterPrint?.let { file ->
                val jpeg = imageProcessor.ensureJpeg(file, exportDir, "final")
                jpeg?.let {
                    cloudinaryClient.uploadAssetToCloudinary(
                        config = config,
                        sessionId = sessionId,
                        file = it,
                        position = uploadedOriginalAssets.size,
                        kind = "FINAL"
                    )
                }
            }
            
            val uploadedAssets = uploadedOriginalAssets + listOfNotNull(uploadedFinalAsset)
            if (uploadedAssets.isEmpty()) {
                return AlbumUploadResult(null, null, emptyList(), null, 0, 0, "Không có ảnh để upload")
            }

            val albumId = preCreatedAlbumId ?: webAlbumClient.createAlbum(
                config = config,
                request = CreateAlbumRequest(sessionId, uploadedAssets.size, config.albumExpiresInDays)
            )?.albumId ?: return AlbumUploadResult(null, null, uploadedOriginalAssets.map { it.secureUrl }, uploadedFinalAsset?.secureUrl, uploadedAssets.size, 0, "Không tạo được album web")

            val finalizedCount = uploadedAssets.count { asset ->
                webAlbumClient.finalizeAsset(config, albumId, asset.finalizeRequest)
            }

            val finalAlbumUrl = "${config.webAlbumBaseUrl.trimEnd('/')}/album/${sessionId}"

            AlbumUploadResult(
                albumId = albumId,
                albumUrl = finalAlbumUrl,
                originalPhotoUrls = uploadedOriginalAssets.map { it.secureUrl },
                finalPhotoUrl = uploadedFinalAsset?.secureUrl,
                uploadedCount = uploadedAssets.size,
                finalizedCount = finalizedCount,
                errorMessage = null
            )
        }.getOrElse { error ->
            AlbumUploadResult(null, null, emptyList(), null, 0, 0, error.message)
        }
    }

    fun uploadSessionVideo(
        sessionId: String,
        albumId: String,
        masterVideo: Path,
        startPosition: Int
    ): Boolean {
        if (!config.canUploadAlbum) return false
        return runCatching {
            val uploadedVideo = cloudinaryClient.uploadAssetToCloudinary(
                config = config,
                sessionId = sessionId,
                file = masterVideo,
                position = startPosition,
                kind = "GIF"
            ) ?: return false
            
            webAlbumClient.finalizeAsset(config, albumId, uploadedVideo.finalizeRequest)
        }.getOrDefault(false)
    }

    fun completeSessionAlbum(albumId: String): Boolean {
        if (!config.canUploadAlbum) return false
        return runCatching {
            webAlbumClient.completeAlbum(config, albumId)
        }.getOrDefault(false)
    }
}
