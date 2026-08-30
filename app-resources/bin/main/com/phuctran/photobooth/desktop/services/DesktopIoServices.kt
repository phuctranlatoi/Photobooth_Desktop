package com.phuctran.photobooth.desktop.services

import com.phuctran.photobooth.desktop.model.CapturedPhoto
import com.phuctran.photobooth.desktop.model.FramePack
import com.phuctran.photobooth.desktop.model.LayoutMode
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.print.attribute.HashPrintRequestAttributeSet
import javax.print.attribute.standard.Copies
import javax.print.attribute.standard.OrientationRequested
import javax.print.attribute.standard.MediaSizeName
import javax.print.attribute.standard.MediaPrintableArea
import java.awt.print.PageFormat
import java.awt.print.Paper
import java.awt.print.Printable
import java.awt.print.PrinterJob

interface CameraService {
    fun capture(): Result<CapturedPhoto>
}

interface PrinterService {
    fun printImage(path: Path, copies: Int = 1, printerName: String? = null): Result<String>
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
    override fun printImage(path: Path, copies: Int, printerName: String?): Result<String> = runCatching {
        require(path.toFile().exists()) { "File ảnh in không tồn tại." }
        
        val image = ImageIO.read(path.toFile()) ?: throw Exception("Không thể đọc file ảnh.")
        val printJob = PrinterJob.getPrinterJob()
        
        if (printerName != null) {
            val services = PrinterJob.lookupPrintServices()
            val service = services.find { it.name.contains(printerName, ignoreCase = true) }
            if (service != null) {
                printJob.printService = service
            }
        }
        
        val attributes = HashPrintRequestAttributeSet()
        attributes.add(Copies(copies))
        
        // Loại bỏ lề mặc định 1 inch của Java để in tràn viền (borderless)
        val pageFormat = printJob.defaultPage()
        val paper = pageFormat.paper
        paper.setImageableArea(0.0, 0.0, paper.width, paper.height)
        pageFormat.paper = paper
        
        printJob.setPrintable(object : Printable {
            override fun print(graphics: java.awt.Graphics, pf: PageFormat, pageIndex: Int): Int {
                if (pageIndex > 0) return Printable.NO_SUCH_PAGE
                
                val g2d = graphics as java.awt.Graphics2D
                g2d.translate(pf.imageableX, pf.imageableY)
                
                val destWidth = pf.imageableWidth.toInt()
                val destHeight = pf.imageableHeight.toInt()
                
                val isImageLandscape = image.width > image.height
                val isPaperLandscape = destWidth > destHeight
                
                if (isImageLandscape != isPaperLandscape) {
                    // Mismatched orientation (e.g. Portrait image on Landscape paper)
                    // Rotate 90 degrees to fit
                    g2d.translate(destWidth / 2.0, destHeight / 2.0)
                    g2d.rotate(Math.PI / 2)
                    g2d.drawImage(image, -destHeight / 2, -destWidth / 2, destHeight, destWidth, null)
                } else {
                    // Orientation matches
                    g2d.drawImage(image, 0, 0, destWidth, destHeight, null)
                }
                
                return Printable.PAGE_EXISTS
            }
        }, pageFormat)
        
        printJob.print(attributes)
        
        "Đã gửi lệnh in $copies bản tới ${printJob.printService?.name ?: "máy in mặc định"}."
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
