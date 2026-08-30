package com.phuctran.photobooth.desktop.remote

internal object SimpleJson {
    fun obj(vararg pairs: Pair<String, Any?>): String {
        return pairs.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "\"${escape(key)}\":${value.toJsonValue()}"
        }
    }

    fun string(json: String, key: String): String? {
        val pattern = "\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
        val match = Regex(pattern).find(json)
        return match?.groupValues?.get(1)?.unescape()
    }

    fun int(json: String, key: String): Int? {
        val pattern = "\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)"
        return Regex(pattern).find(json)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    }

    fun long(json: String, key: String): Long? {
        val pattern = "\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)"
        return Regex(pattern).find(json)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
    }

    fun escape(value: String): String {
        return buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }

    private fun Any?.toJsonValue(): String = when (this) {
        null -> "null"
        is Number, is Boolean -> toString()
        else -> "\"${escape(toString())}\""
    }

    private fun String.unescape(): String {
        return replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }
}
