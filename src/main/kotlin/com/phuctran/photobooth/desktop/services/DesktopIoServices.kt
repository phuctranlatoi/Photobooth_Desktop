package com.phuctran.photobooth.desktop.services

import com.phuctran.photobooth.desktop.model.CapturedPhoto
import com.phuctran.photobooth.desktop.model.FramePack
import com.phuctran.photobooth.desktop.model.LayoutMode
import java.awt.Desktop
import java.nio.file.Path

interface CameraService {
    fun capture(): Result<CapturedPhoto>
}

interface PrinterService {
    fun printImage(path: Path): Result<String>
}

interface AlbumUploadService {
    fun uploadAlbum(
        sessionId: String,
        layout: LayoutMode,
        frame: FramePack,
        allPhotos: List<CapturedPhoto>,
        printImage: Path
    ): Result<String?>
}

class SystemPrinterService : PrinterService {
    override fun printImage(path: Path): Result<String> = runCatching {
        require(path.toFile().exists()) {
            "File ảnh in không tồn tại."
        }
        require(Desktop.isDesktopSupported()) {
            "Máy này chưa hỗ trợ Windows print từ app."
        }
        val desktop = Desktop.getDesktop()
        require(desktop.isSupported(Desktop.Action.PRINT)) {
            "Windows print action chưa khả dụng."
        }
        desktop.print(path.toFile())
        "Đã gửi ảnh sang hộp thoại in của Windows."
    }
}

class LocalOnlyAlbumUploadService : AlbumUploadService {
    override fun uploadAlbum(
        sessionId: String,
        layout: LayoutMode,
        frame: FramePack,
        allPhotos: List<CapturedPhoto>,
        printImage: Path
    ): Result<String?> = Result.success(null)
}
