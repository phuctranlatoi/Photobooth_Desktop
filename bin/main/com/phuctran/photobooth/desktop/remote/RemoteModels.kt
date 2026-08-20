package com.phuctran.photobooth.desktop.remote

data class CreateAlbumRequest(
    val externalSessionId: String,
    val expectedAssets: Int,
    val expiresInDays: Int = 7
)

data class CreateAlbumResponse(
    val albumId: String,
    val accessToken: String,
    val albumUrl: String,
    val status: String,
    val expiresAt: String
)

data class FinalizeAssetRequest(
    val kind: String,
    val position: Int,
    val assetId: String,
    val publicId: String,
    val version: String,
    val format: String,
    val resourceType: String,
    val deliveryType: String,
    val width: Int,
    val height: Int,
    val bytes: Int
)

data class UploadedCloudAsset(
    val secureUrl: String,
    val finalizeRequest: FinalizeAssetRequest
)

data class AlbumUploadResult(
    val albumId: String?,
    val albumUrl: String?,
    val originalPhotoUrls: List<String>,
    val finalPhotoUrl: String?,
    val uploadedCount: Int,
    val finalizedCount: Int,
    val errorMessage: String? = null
)
