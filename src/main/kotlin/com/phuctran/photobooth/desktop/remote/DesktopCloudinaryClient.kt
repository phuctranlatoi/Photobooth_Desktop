package com.phuctran.photobooth.desktop.remote

import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class DesktopCloudinaryClient(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
) {
    fun uploadImageToCloudinary(
        config: DesktopBoothConfig,
        sessionId: String,
        file: Path,
        position: Int,
        kind: String = "ORIGINAL"
    ): UploadedCloudAsset? {
        if (!Files.isRegularFile(file) || Files.size(file) <= 0L) return null
        val extension = file.fileName.toString().substringAfterLast('.', "").lowercase()
        if (extension != "jpg" && extension != "jpeg" && extension != "png") return null
        if (config.cloudinaryCloudName.isBlank() || config.cloudinaryUploadPreset.isBlank()) return null

        val uploadUrl = "https://api.cloudinary.com/v1_1/${config.cloudinaryCloudName}/image/upload"
        val boundary = "----PrettyBooth${UUID.randomUUID().toString().replace("-", "")}"
        val mimeType = if (extension == "png") "image/png" else "image/jpeg"
        val body = multipartBody(
            boundary = boundary,
            fields = mapOf("upload_preset" to config.cloudinaryUploadPreset),
            fileField = "file",
            fileName = file.fileName.toString(),
            mimeType = mimeType,
            fileBytes = Files.readAllBytes(file)
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create(uploadUrl))
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) return null

        val json = response.body()
        val secureUrl = SimpleJson.string(json, "secure_url") ?: return null
        val assetId = SimpleJson.string(json, "asset_id")
        val publicId = SimpleJson.string(json, "public_id").orEmpty()
        val version = SimpleJson.long(json, "version")?.toString() ?: "1"
        val format = SimpleJson.string(json, "format") ?: extension
        val resourceType = SimpleJson.string(json, "resource_type") ?: "image"
        val deliveryType = SimpleJson.string(json, "type") ?: "upload"
        val width = SimpleJson.int(json, "width") ?: 0
        val height = SimpleJson.int(json, "height") ?: 0
        val bytes = SimpleJson.int(json, "bytes") ?: Files.size(file).toInt()

        return UploadedCloudAsset(
            secureUrl = secureUrl,
            finalizeRequest = FinalizeAssetRequest(
                kind = kind,
                position = position,
                assetId = assetId ?: publicId,
                publicId = publicId,
                version = version,
                format = format,
                resourceType = resourceType,
                deliveryType = deliveryType,
                width = width,
                height = height,
                bytes = bytes
            )
        )
    }

    private fun multipartBody(
        boundary: String,
        fields: Map<String, String>,
        fileField: String,
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray
    ): ByteArray {
        val lineBreak = "\r\n"
        val output = ByteArrayOutputStream()

        fun write(value: String) {
            output.write(value.toByteArray(StandardCharsets.UTF_8))
        }

        fields.forEach { (key, value) ->
            write("--$boundary$lineBreak")
            write("Content-Disposition: form-data; name=\"$key\"$lineBreak$lineBreak")
            write(value)
            write(lineBreak)
        }

        write("--$boundary$lineBreak")
        write("Content-Disposition: form-data; name=\"$fileField\"; filename=\"$fileName\"$lineBreak")
        write("Content-Type: $mimeType$lineBreak$lineBreak")
        output.write(fileBytes)
        write(lineBreak)
        write("--$boundary--$lineBreak")

        return output.toByteArray()
    }
}
