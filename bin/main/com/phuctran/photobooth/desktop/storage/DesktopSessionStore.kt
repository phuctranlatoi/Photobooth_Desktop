package com.phuctran.photobooth.desktop.storage

import com.phuctran.photobooth.desktop.model.BoothSession
import com.phuctran.photobooth.desktop.remote.SimpleJson
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class DesktopSessionStore(projectDir: Path) {
    private val sessionDir = projectDir.resolve("data").resolve("sessions")
    private val sessionLog = sessionDir.resolve("sessions.jsonl")

    fun saveSession(session: BoothSession) {
        Files.createDirectories(sessionDir)
        val json = SimpleJson.obj(
            "id" to session.id,
            "boothId" to session.boothId,
            "productId" to session.productId,
            "state" to session.state,
            "qrCodeUrl" to session.qrCodeUrl,
            "photoUrls" to session.photoUrls.joinToString("|"),
            "videoUrls" to session.videoUrls.joinToString("|"),
            "masterUrl" to session.masterUrl,
            "startedAt" to session.startedAt,
            "paidAt" to session.paidAt,
            "completedAt" to session.completedAt
        )
        Files.writeString(
            sessionLog,
            json + System.lineSeparator(),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
    }
}
