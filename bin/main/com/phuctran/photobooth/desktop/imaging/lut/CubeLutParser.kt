package com.phuctran.photobooth.desktop.imaging.lut

import java.nio.file.Files
import java.nio.file.Path

object CubeLutParser {
    fun parse(path: Path): Lut3D = Files.newBufferedReader(path).use { parse(it.readText()) }

    fun parse(text: String): Lut3D {
        var size = 0
        var title: String? = null
        var min = floatArrayOf(0f, 0f, 0f)
        var max = floatArrayOf(1f, 1f, 1f)
        val values = ArrayList<Float>()
        var rows = 0
        val directives = mutableSetOf<String>()
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.substringBefore('#').trim().removePrefix("\uFEFF")
            if (line.isEmpty()) return@forEachIndexed
            val parts = line.split(Regex("\\s+"))
            fun triple(): FloatArray {
                require(parts.size == 4) { "Line ${index + 1}: expected three values" }
                return FloatArray(3) { i ->
                    val value = parts[i + 1].toFloatOrNull()
                    require(value != null && value.isFinite()) { "Line ${index + 1}: invalid number" }
                    value
                }
            }
            val directive = parts[0].uppercase()
            if (directive in setOf("TITLE", "LUT_3D_SIZE", "DOMAIN_MIN", "DOMAIN_MAX")) {
                require(directives.add(directive) && rows == 0) { "Line ${index + 1}: duplicate or late $directive" }
            }
            when (directive) {
                "TITLE" -> title = line.substringAfter(parts[0]).trim().trim('"')
                "LUT_1D_SIZE" -> throw IllegalArgumentException("1D LUT files are not supported")
                "LUT_3D_SIZE" -> {
                    require(size == 0 && rows == 0 && parts.size == 2) { "Line ${index + 1}: invalid LUT_3D_SIZE" }
                    size = parts[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid LUT size")
                    require(size in 2..65) { "3D LUT size must be between 2 and 65" }
                }
                "DOMAIN_MIN" -> { require(rows == 0); min = triple() }
                "DOMAIN_MAX" -> { require(rows == 0); max = triple() }
                else -> {
                    require(parts[0].toFloatOrNull() != null) { "Line ${index + 1}: unknown directive ${parts[0]}" }
                    require(size != 0) { "Line ${index + 1}: missing LUT_3D_SIZE or unknown directive" }
                    require(parts.size == 3 && rows < size * size * size) { "Line ${index + 1}: invalid LUT data row" }
                    parts.forEach { token ->
                        val value = token.toFloatOrNull()
                        require(value != null && value.isFinite()) { "Line ${index + 1}: invalid LUT value" }
                        values.add(value)
                    }
                    rows++
                }
            }
        }
        require(size != 0 && rows == size * size * size) { "Expected ${size * size * size} 3D LUT rows, got $rows" }
        require((0..2).all { max[it] > min[it] }) { "Invalid LUT domain" }
        return Lut3D(size, title, min, max, values.toFloatArray())
    }
}
