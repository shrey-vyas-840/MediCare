package com.medicare.app.data.storage

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class StorageContainer<T>(
    val schemaVersion: Int = 1,
    val data: T
)

class JsonStorage(
    val baseDir: File,
    val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true }
) {
    constructor(context: Context) : this(context.filesDir)

    @Synchronized
    inline fun <reified T> save(fileName: String, data: T, schemaVersion: Int = 1): Boolean {
        val file = File(baseDir, fileName)
        val tmpFile = File(baseDir, "$fileName.tmp")
        val backupFile = File(baseDir, "$fileName.bak")

        try {
            val container = StorageContainer(schemaVersion = schemaVersion, data = data)
            val jsonString = json.encodeToString(container)

            // Write to temp file
            tmpFile.writeText(jsonString)

            // If primary file exists, make backup before replacing
            if (file.exists()) {
                try {
                    Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } catch (_: Exception) {}
            }

            // Atomic move temp to target
            try {
                Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                // Fallback to non-atomic move or rename
                Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            // Cleanup tmp if left over
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
            return false
        }
    }

    @Synchronized
    inline fun <reified T> load(fileName: String, defaultValue: T, currentSchemaVersion: Int = 1, noinline migration: ((Int, T) -> T)? = null): T {
        val file = File(baseDir, fileName)
        val backupFile = File(baseDir, "$fileName.bak")

        if (!file.exists() && !backupFile.exists()) {
            return defaultValue
        }

        // Try loading primary file
        val result = loadFromFile<T>(file, currentSchemaVersion, migration)
        if (result != null) {
            return result
        }

        // Try backup file if primary failed
        if (backupFile.exists()) {
            val backupResult = loadFromFile<T>(backupFile, currentSchemaVersion, migration)
            if (backupResult != null) {
                // Restore backup to primary
                try {
                    Files.copy(backupFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } catch (_: Exception) {}
                return backupResult
            }
        }

        return defaultValue
    }

    inline fun <reified T> loadFromFile(file: File, currentSchemaVersion: Int, noinline migration: ((Int, T) -> T)?): T? {
        if (!file.exists()) return null
        try {
            val text = file.readText()
            if (text.isBlank()) return null

            // Try parsing as StorageContainer first
            val container = try {
                json.decodeFromString<StorageContainer<T>>(text)
            } catch (_: Exception) {
                // If legacy format without container wrapper, try parsing T directly
                val rawData = json.decodeFromString<T>(text)
                StorageContainer(schemaVersion = 1, data = rawData)
            }

            var data = container.data
            if (container.schemaVersion < currentSchemaVersion && migration != null) {
                data = migration(container.schemaVersion, data)
            }
            return data
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
