package com.phuctran.photobooth.desktop.config

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object DesktopAppPaths {
    const val APP_DIR_NAME = "PrettyBoothDesktop"

    fun appDataDir(): Path {
        val localAppData = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
        val base = if (localAppData != null) {
            Paths.get(localAppData)
        } else {
            Paths.get(System.getProperty("user.home"), "AppData", "Local")
        }
        return base.resolve(APP_DIR_NAME).toAbsolutePath().also { Files.createDirectories(it) }
    }

    fun workingDir(): Path = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
}
