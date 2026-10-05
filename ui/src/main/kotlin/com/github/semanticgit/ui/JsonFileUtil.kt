package com.github.semanticgit.ui

import java.io.File

object JsonFileUtil {

    fun readStringArray(file: File): List<String> {
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText().trim()
            if (text.isEmpty() || text == "[]") return emptyList()
            val content = text.removeSurrounding("[", "]").trim()
            if (content.isEmpty()) return emptyList()
            val items = mutableListOf<String>()
            var i = 0
            while (i < content.length) {
                if (content[i] == '\"') {
                    val end = findStringEnd(content, i + 1)
                    val value = content.substring(i + 1, end)
                        .replace("\\\\", "\\")
                        .replace("\\\"", "\"")
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\t", "\t")
                    items.add(value)
                    i = end + 1
                } else if (content[i] == ',' || content[i].isWhitespace()) {
                    i++
                } else {
                    i++
                }
            }
            items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun writeStringArray(file: File, items: List<String>) {
        try {
            file.parentFile?.mkdirs()
            val sb = StringBuilder("[\n")
            items.forEachIndexed { index, item ->
                if (index > 0) sb.append(",\n")
                sb.append("  \"")
                sb.append(
                    item
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t")
                )
                sb.append("\"")
            }
            sb.append("\n]")
            file.writeText(sb.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun readConfig(file: File): Map<String, Any> {
        if (!file.exists()) return emptyMap()
        return try {
            val text = file.readText().trim()
            if (text.isEmpty() || text == "{}") return emptyMap()
            val content = text.removeSurrounding("{", "}").trim()
            if (content.isEmpty()) return emptyMap()
            val map = mutableMapOf<String, Any>()
            var i = 0
            while (i < content.length) {
                skipWhitespace(content, i).let { i = it }
                if (i >= content.length) break
                if (content[i] != '\"') break
                val keyEnd = findStringEnd(content, i + 1)
                val key = content.substring(i + 1, keyEnd)
                    .replace("\\\\", "\\")
                    .replace("\\\"", "\"")
                i = keyEnd + 1
                skipWhitespace(content, i).let { i = it }
                if (i >= content.length || content[i] != ':') break
                i++
                skipWhitespace(content, i).let { i = it }
                if (i >= content.length) break
                when {
                    content[i] == '\"' -> {
                        val valEnd = findStringEnd(content, i + 1)
                        val value = content.substring(i + 1, valEnd)
                            .replace("\\\\", "\\")
                            .replace("\\\"", "\"")
                        map[key] = value
                        i = valEnd + 1
                    }
                    content[i].isDigit() || content[i] == '-' -> {
                        val numStart = i
                        while (i < content.length && (content[i].isDigit() || content[i] == '.' || content[i] == '-')) i++
                        val numStr = content.substring(numStart, i)
                        map[key] = if (numStr.contains('.')) numStr.toDouble() else numStr.toInt()
                    }
                    content[i] == 't' || content[i] == 'f' -> {
                        if (content.startsWith("true", i)) {
                            map[key] = true
                            i += 4
                        } else if (content.startsWith("false", i)) {
                            map[key] = false
                            i += 5
                        }
                    }
                    else -> i++
                }
                skipWhitespace(content, i).let { i = it }
                if (i < content.length && content[i] == ',') i++
            }
            map
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    fun writeConfig(file: File, config: Map<String, Any>) {
        try {
            file.parentFile?.mkdirs()
            val sb = StringBuilder("{\n")
            config.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) sb.append(",\n")
                sb.append("  \"${escapeJsonString(key)}\": ")
                when (value) {
                    is String -> sb.append("\"${escapeJsonString(value)}\"")
                    is Number -> sb.append(value.toString())
                    is Boolean -> sb.append(value.toString())
                    else -> sb.append("\"${escapeJsonString(value.toString())}\"")
                }
            }
            sb.append("\n}")
            file.writeText(sb.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getConfigInt(config: Map<String, Any>, key: String, default: Int = -1): Int {
        return when (val v = config[key]) {
            is Int -> v
            is Double -> v.toInt()
            is String -> v.toIntOrNull() ?: default
            else -> default
        }
    }

    fun getConfigString(config: Map<String, Any>, key: String, default: String = ""): String {
        return config[key]?.toString() ?: default
    }

    private fun findStringEnd(s: String, start: Int): Int {
        var i = start
        while (i < s.length) {
            when (s[i]) {
                '\\' -> i += 2
                '\"' -> return i
                else -> i++
            }
        }
        return s.length
    }

    private fun skipWhitespace(s: String, start: Int): Int {
        var i = start
        while (i < s.length && s[i].isWhitespace()) i++
        return i
    }

    private fun escapeJsonString(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
