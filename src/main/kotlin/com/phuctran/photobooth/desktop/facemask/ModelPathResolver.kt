package com.phuctran.photobooth.desktop.facemask

import java.nio.file.Files
import java.nio.file.Path

internal object ModelPathResolver {
    fun resolve(name: String): Path {
        val packaged = System.getProperty("compose.application.resources.dir")
            ?.takeIf { it.isNotBlank() }
            ?.let { Path.of(it).resolve("models").resolve(name) }
        if (packaged != null && Files.isRegularFile(packaged)) return packaged
        return Path.of("app-resources", "models", name)
    }
}
