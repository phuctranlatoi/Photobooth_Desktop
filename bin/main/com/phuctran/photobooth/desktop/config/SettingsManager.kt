package com.phuctran.photobooth.desktop.config

import java.nio.file.Files
import java.nio.file.Path

object SettingsManager {

    fun updateSettings(
        envFile: Path,
        updates: Map<String, String>
    ) {
        if (!Files.exists(envFile)) {
            val defaultContent = updates.map { "${it.key}=${it.value}" }.joinToString("\n")
            Files.writeString(envFile, defaultContent)
            return
        }

        val lines = Files.readAllLines(envFile).toMutableList()
        val keysFound = mutableSetOf<String>()

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.isBlank() || line.startsWith("#")) continue

            val key = line.substringBefore("=").trim()
            if (updates.containsKey(key)) {
                lines[i] = "$key=${updates[key]}"
                keysFound.add(key)
            }
        }

        updates.forEach { (key, value) ->
            if (!keysFound.contains(key)) {
                lines.add("$key=$value")
            }
        }

        Files.writeString(envFile, lines.joinToString("\n") + "\n")
    }
}
