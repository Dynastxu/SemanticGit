package com.github.semanticgit.ui.config

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

object DbConfig {
    private val configDir = File(System.getProperty("user.home"), ".semanticgit")
    private val configFile = File(configDir, "config.properties")

    private val props = Properties()

    val DEFAULT_STORAGE_PATH = "${System.getProperty("user.home")}/.semanticgit/db"
    const val DEFAULT_FILENAME_FORMAT = $$"${name}_${hash}"

    var storagePath: String
        get() = props.getProperty("db.storage.path", DEFAULT_STORAGE_PATH)
        set(value) {
            props.setProperty("db.storage.path", value)
            save()
        }

    var filenameFormat: String
        get() = props.getProperty("db.filename.format", DEFAULT_FILENAME_FORMAT)
        set(value) {
            props.setProperty("db.filename.format", value)
            save()
        }

    init {
        if (configFile.exists()) {
            try {
                configFile.inputStream().use { props.load(it) }
            } catch (_: Exception) {
            }
        }
    }

    fun save() {
        try {
            configDir.mkdirs()
            configFile.outputStream().use { props.store(it, "SemanticGit Config") }
        } catch (_: Exception) {
        }
    }

    fun resetToDefault() {
        storagePath = DEFAULT_STORAGE_PATH
        filenameFormat = DEFAULT_FILENAME_FORMAT
    }

    fun resolveDbDir(): String = storagePath

    fun resolveDbName(repoPath: String, timestamp: Long = System.currentTimeMillis()): String {
        val repoName = File(repoPath).name
        val pathHash = repoPathHash(repoPath)
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss")
        val timeStr = sdf.format(Date(timestamp))
        return filenameFormat
            .replace($$"${name}", repoName)
            .replace($$"${hash}", pathHash)
            .replace($$"${time}", timeStr)
    }

    fun resolveDbFile(repoPath: String, timestamp: Long = System.currentTimeMillis()): File {
        return File(resolveDbDir(), "${resolveDbName(repoPath, timestamp)}.db")
    }

    fun findLatestDbFile(repoPath: String): File? {
        val dbDir = File(resolveDbDir())
        if (!dbDir.exists() || !dbDir.isDirectory) return null

        val repoName = File(repoPath).name
        val pathHash = repoPathHash(repoPath)

        val pattern = Regex.escape(filenameFormat)
            .replace("\\$\\{name}".toRegex(), Regex.escape(repoName))
            .replace("\\$\\{hash}".toRegex(), Regex.escape(pathHash))
            .replace("\\$\\{time}".toRegex(), "(\\d{8}_\\d{6})")

        val regex = Regex("^$pattern\\.db$")

        val matchingFiles = dbDir.listFiles()?.filter { it.isFile && it.name.matches(regex) }
        if (!matchingFiles.isNullOrEmpty()) {
            return matchingFiles.maxByOrNull { it.lastModified() }
        }

        val legacyDbName = Integer.toHexString(File(repoPath).absolutePath.hashCode())
        val legacyFile = File(dbDir, "$legacyDbName.db")
        if (legacyFile.exists()) return legacyFile

        return null
    }

    private fun repoPathHash(repoPath: String): String {
        return Integer.toHexString(File(repoPath).absolutePath.hashCode())
    }
}
