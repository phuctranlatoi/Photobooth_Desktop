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
    fun uploadSessionAlbum(
        sessionId: String,
        preCreatedAlbumId: String? = null,
        layout: LayoutMode,
        frame: FramePack,
        capturedMoments: List<CapturedMoment>,
        selectedMoments: List<CapturedMoment>,
        masterPrint: Path?,
        masterVideo: Path? = null
    ): AlbumUploadResult {
        if (!config.canUploadAlbum) {
            return AlbumUploadResult(
                albumId = null,
                albumUrl = null,
                originalPhotoUrls = emptyList(),
                finalPhotoUrl = null,
                uploadedCount = 0,
                finalizedCount = 0,
                errorMessage = "Thiếu .env: BOOTH_API_KEY, CLOUDINARY_CLOUD_NAME hoặc CLOUDINARY_UPLOAD_PRESET."
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
            
            val uploadedFinalVideo = masterVideo?.let { file ->
                cloudinaryClient.uploadAssetToCloudinary(
                    config = config,
                    sessionId = sessionId,
                    file = file,
                    position = uploadedOriginalAssets.size + 1,
                    kind = "VIDEO"
                )
            }

            val uploadedAssets = uploadedOriginalAssets + listOfNotNull(uploadedFinalAsset, uploadedFinalVideo)
            if (uploadedAssets.isEmpty()) {
                return AlbumUploadResult(
                    albumId = null,
                    albumUrl = null,
                    originalPhotoUrls = emptyList(),
                    finalPhotoUrl = null,
                    uploadedCount = 0,
                    finalizedCount = 0,
                    errorMessage = "Không upload được JPEG nào lên Cloudinary."
                )
            }

            val albumId = if (preCreatedAlbumId != null) {
                preCreatedAlbumId
            } else {
                val album = webAlbumClient.createAlbum(
                    config = config,
                    request = CreateAlbumRequest(
                        externalSessionId = sessionId,
                        expectedAssets = uploadedAssets.size,
                        expiresInDays = config.albumExpiresInDays
                    )
                ) ?: return AlbumUploadResult(
                    albumId = null,
                    albumUrl = null,
                    originalPhotoUrls = uploadedOriginalAssets.map { it.secureUrl },
                    finalPhotoUrl = uploadedFinalAsset?.secureUrl,
                    uploadedCount = uploadedAssets.size,
                    finalizedCount = 0,
                    errorMessage = "Upload Cloudinary xong nhưng chưa tạo được album web."
                )
                album.albumId
            }

            val finalizedCount = uploadedAssets.count { asset ->
                webAlbumClient.finalizeAsset(config, albumId, asset.finalizeRequest)
            }

            val albumReady = if (finalizedCount > 0) {
                webAlbumClient.completeAlbum(config, albumId)
            } else {
                false
            }

            // Mặc dù preCreatedAlbum có URL, nhưng webAlbumClient.completeAlbum không trả về URL, 
            // nên ta dùng config.webAlbumBaseUrl để tái tạo URL
            val finalAlbumUrl = "${config.webAlbumBaseUrl.trimEnd('/')}/album/${sessionId}"

            AlbumUploadResult(
                albumId = albumId,
                albumUrl = if (albumReady) finalAlbumUrl else null,
                originalPhotoUrls = uploadedOriginalAssets.map { it.secureUrl },
                finalPhotoUrl = uploadedFinalAsset?.secureUrl,
                uploadedCount = uploadedAssets.size,
                finalizedCount = finalizedCount,
                errorMessage = if (albumReady) null else "Đã upload nhưng album web chưa READY."
            )
        }.getOrElse { error ->
            AlbumUploadResult(
                albumId = null,
                albumUrl = null,
                originalPhotoUrls = emptyList(),
                finalPhotoUrl = null,
                uploadedCount = 0,
                finalizedCount = 0,
                errorMessage = error.message ?: "Upload album thất bại."
            )
        }
    }
}
