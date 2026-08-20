package com.phuctran.photobooth.desktop.services

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors

class LocalFileServer(
    private val outputDir: Path,
    private val port: Int = 8080
) {
    private var server: HttpServer? = null
    
    val isRunning: Boolean
        get() = server != null

    fun start() {
        if (server != null) return
        
        try {
            Files.createDirectories(outputDir)
            
            // Lắng nghe trên mọi interface (0.0.0.0) để điện thoại cùng LAN có thể truy cập
            server = HttpServer.create(InetSocketAddress("0.0.0.0", port), 0).apply {
                createContext("/download") { exchange ->
                    try {
                        val path = exchange.requestURI.path
                        val filename = path.substringAfterLast("/")
                        
                        // Chặn path traversal (bảo mật)
                        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                            exchange.sendResponseHeaders(403, -1)
                            exchange.close()
                            return@createContext
                        }
                        
                        val file = outputDir.resolve(filename).toFile()
                        if (!file.exists() || !file.isFile) {
                            val response = "File not found."
                            exchange.sendResponseHeaders(404, response.toByteArray().size.toLong())
                            exchange.responseBody.use { os ->
                                os.write(response.toByteArray())
                            }
                            return@createContext
                        }

                        // Trả về file
                        exchange.responseHeaders.add("Content-Type", "image/jpeg")
                        // Ép trình duyệt tải về thay vì chỉ xem (tuỳ chọn)
                        // exchange.responseHeaders.add("Content-Disposition", "attachment; filename=\"\$filename\"")
                        
                        exchange.sendResponseHeaders(200, file.length())
                        exchange.responseBody.use { os ->
                            file.inputStream().use { input ->
                                input.copyTo(os)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runCatching { 
                            exchange.sendResponseHeaders(500, -1)
                            exchange.close()
                        }
                    }
                }
                
                // Dùng pool thread nhẹ để xử lý nhiều thiết bị tải cùng lúc
                executor = Executors.newFixedThreadPool(5)
                start()
            }
            println("Local Web Server started on port $port")
        } catch (e: Exception) {
            e.printStackTrace()
            server = null
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
        println("Local Web Server stopped")
    }
}
