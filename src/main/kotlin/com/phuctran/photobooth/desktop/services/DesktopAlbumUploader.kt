package com.phuctran.photobooth.desktop.services

import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import com.phuctran.photobooth.desktop.imaging.DesktopImageProcessor
import com.phuctran.photobooth.desktop.model.CapturedMoment
import com.phuctran.photobooth.desktop.remote.AlbumUploadResult
import com.phuctran.photobooth.desktop.remote.CreateAlbumRequest
import com.phuctran.photobooth.desktop.remote.DesktopCloudinaryClient
import com.phuctran.photobooth.desktop.remote.DesktopWebAlbumClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.nio.file.Path

class DesktopAlbumUploader(
    private val projectDir: Path,
    private val config: DesktopBoothConfig,
    private val cloudinaryClient: DesktopCloudinaryClient = DesktopCloudinaryClient(),
    private val webAlbumClient: DesktopWebAlbumClient = DesktopWebAlbumClient(),
    private val imageProcessor: DesktopImageProcessor = DesktopImageProcessor()
) {
    suspend fun uploadSessionPhotos(
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
            coroutineScope {
                val exportDir = projectDir.resolve("data").resolve("sessions").resolve(sessionId).resolve("exports")
                
                // 1. Process all JPEGs in parallel
                val originalJpegsDeferred = capturedMoments.map { moment ->
                    async {
                        moment.photoPath?.let { imageProcessor.ensureJpeg(it, exportDir, "original_${moment.index}") }
                    }
                }
                val finalJpegDeferred = masterPrint?.let { file -> 
                    async { imageProcessor.ensureJpeg(file, exportDir, "final") } 
                }
                
                val originalJpegs = originalJpegsDeferred.awaitAll().filterNotNull()
                val finalJpeg = finalJpegDeferred?.await()

                // 2. Upload all assets in parallel
                val uploadedOriginalAssetsDeferred = originalJpegs.mapIndexed { index, file ->
                    async {
                        cloudinaryClient.uploadAssetToCloudinary(
                            config = config,
                            sessionId = sessionId,
                            file = file,
                            position = index,
                            kind = "ORIGINAL"
                        )
                    }
                }
                val uploadedFinalAssetDeferred = finalJpeg?.let { file ->
                    async {
                        cloudinaryClient.uploadAssetToCloudinary(
                            config = config,
                            sessionId = sessionId,
                            file = file,
                            position = originalJpegs.size,
                            kind = "FINAL"
                        )
                    }
                }

                val uploadedOriginalAssets = uploadedOriginalAssetsDeferred.awaitAll().filterNotNull()
                val uploadedFinalAsset = uploadedFinalAssetDeferred?.await()
                
                val uploadedAssets = uploadedOriginalAssets + listOfNotNull(uploadedFinalAsset)
                if (uploadedAssets.isEmpty()) {
                    return@coroutineScope AlbumUploadResult(null, null, emptyList(), null, 0, 0, "Không có ảnh để upload")
                }

                // 3. Create album
                val albumId = preCreatedAlbumId ?: webAlbumClient.createAlbum(
                    config = config,
                    request = CreateAlbumRequest(sessionId, uploadedAssets.size, config.albumExpiresInDays)
                )?.albumId ?: return@coroutineScope AlbumUploadResult(null, null, uploadedOriginalAssets.map { it.secureUrl }, uploadedFinalAsset?.secureUrl, uploadedAssets.size, 0, "Không tạo được album web")

                // 4. Finalize all assets SEQUENTIALLY to prevent backend race conditions and preserve order
                var finalizedCount = 0
                for (asset in uploadedAssets) {
                    if (webAlbumClient.finalizeAsset(config, albumId, asset.finalizeRequest)) {
                        finalizedCount++
                    }
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
            }
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
