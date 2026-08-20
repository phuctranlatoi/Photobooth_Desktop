package com.phuctran.photobooth.desktop.remote

import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

class DesktopWebAlbumClient(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
) {
    fun createAlbum(
        config: DesktopBoothConfig,
        request: CreateAlbumRequest
    ): CreateAlbumResponse? {
        val json = SimpleJson.obj(
            "externalSessionId" to request.externalSessionId,
            "expectedAssets" to request.expectedAssets,
            "expiresInDays" to request.expiresInDays
        )
        val response = postJson(config, "api/v1/albums", json)
        response.requireSuccess("Tạo album web", "api/v1/albums")
        val body = response.body()
        return CreateAlbumResponse(
            albumId = SimpleJson.string(body, "albumId")
                ?: error("Tạo album web thiếu albumId trong response."),
            accessToken = SimpleJson.string(body, "accessToken").orEmpty(),
            albumUrl = SimpleJson.string(body, "albumUrl")
                ?: error("Tạo album web thiếu albumUrl trong response."),
            status = SimpleJson.string(body, "status").orEmpty(),
            expiresAt = SimpleJson.string(body, "expiresAt").orEmpty()
        )
    }

    fun finalizeAsset(
        config: DesktopBoothConfig,
        albumId: String,
        request: FinalizeAssetRequest
    ): Boolean {
        val json = SimpleJson.obj(
            "kind" to request.kind,
            "position" to request.position,
            "assetId" to request.assetId,
            "publicId" to request.publicId,
            "version" to request.version,
            "format" to request.format,
            "resourceType" to request.resourceType,
            "deliveryType" to request.deliveryType,
            "width" to request.width,
            "height" to request.height,
            "bytes" to request.bytes
        )
        val response = postJson(config, "api/v1/albums/${urlSegment(albumId)}/assets", json)
        response.requireSuccess("Lưu metadata ảnh album", "api/v1/albums/$albumId/assets")
        return true
    }

    fun completeAlbum(config: DesktopBoothConfig, albumId: String): Boolean {
        val response = postJson(config, "api/v1/albums/${urlSegment(albumId)}/complete", "")
        response.requireSuccess("Hoàn tất album web", "api/v1/albums/$albumId/complete")
        return true
    }

    private fun postJson(
        config: DesktopBoothConfig,
        path: String,
        body: String
    ): HttpResponse<String> {
        val uri = config.webAlbumBaseUrl.trimEnd('/') + "/" + path.trimStart('/')
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .header("Authorization", config.authHeader)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))

        if (body.isNotEmpty()) {
            builder.header("Content-Type", "application/json")
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
    }

    private fun urlSegment(value: String): String {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)
    }

    private fun HttpResponse<String>.requireSuccess(action: String, path: String) {
        if (statusCode() in 200..299) return
        val body = body()
            .replace(Regex("\\s+"), " ")
            .take(320)
        error("$action lỗi HTTP ${statusCode()} tại $path: $body")
    }
}
