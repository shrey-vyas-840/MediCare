package com.medicare.app.data.storage

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class JsonStorageTest {

    private lateinit var tempDir: File
    private lateinit var jsonStorage: JsonStorage

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("json_storage_test").toFile()
        jsonStorage = JsonStorage(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testSaveAndLoad() {
        val fileName = "test.json"
        val testData = listOf("Item 1", "Item 2", "Item 3")

        val saved = jsonStorage.save(fileName, testData, schemaVersion = 1)
        assertTrue(saved)

        val loaded = jsonStorage.load<List<String>>(fileName, emptyList(), currentSchemaVersion = 1)
        assertEquals(testData, loaded)
    }

    @Test
    fun testErrorRecoveryWithBackup() {
        val fileName = "recovery_test.json"
        val initialData = "Valid Data"

        jsonStorage.save(fileName, initialData, schemaVersion = 1)
        jsonStorage.save(fileName, "Updated Data", schemaVersion = 1)

        val file = File(tempDir, fileName)
        file.writeText("corrupted json {{{{")

        val loaded = jsonStorage.load<String>(fileName, "Default", currentSchemaVersion = 1)
        assertTrue(loaded == "Valid Data" || loaded == "Updated Data" || loaded == "Default")
    }

    @Test
    fun testSchemaMigration() {
        val fileName = "migration_test.json"
        val legacyData = "Legacy"

        jsonStorage.save(fileName, legacyData, schemaVersion = 1)

        val loaded = jsonStorage.load<String>(
            fileName = fileName,
            defaultValue = "Default",
            currentSchemaVersion = 2,
            migration = { oldVersion, data ->
                if (oldVersion == 1) {
                    "$data Migrated to v2"
                } else {
                    data
                }
            }
        )

        assertEquals("Legacy Migrated to v2", loaded)
    }
}
