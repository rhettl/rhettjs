package com.rhett.rhettjs.api

import com.rhett.rhettjs.config.ConfigManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Basic NBT API tests to verify Tag API integration.
 */
class NBTAPIBasicTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var nbtApi: NBTAPI
    private lateinit var structuresDir: Path
    private lateinit var backupsDir: Path

    @BeforeEach
    fun setup() {
        val configDir = tempDir.resolve("config")
        Files.createDirectories(configDir)
        ConfigManager.init(configDir)

        structuresDir = tempDir.resolve("structures")
        backupsDir = tempDir.resolve("backups/structures")
        Files.createDirectories(structuresDir)
        Files.createDirectories(backupsDir)

        nbtApi = NBTAPI(structuresDir, backupsDir)
    }

    @Test
    fun `test write and read simple NBT`() {
        val data = mapOf(
            "name" to "TestStructure",
            "value" to 42
        )

        nbtApi.write("test.nbt", data)

        val readData = nbtApi.read("test.nbt")
        assertNotNull(readData)
        assertTrue(readData is Map<*, *>)

        val map = readData as Map<*, *>
        assertEquals("TestStructure", map["name"])
        assertEquals(42, (map["value"] as Number).toInt())
    }

    @Test
    fun `test path validation rejects parent traversal`() {
        val data = mapOf("test" to "value")

        assertThrows(SecurityException::class.java) {
            nbtApi.write("../../../etc/passwd", data)
        }
    }

    @Test
    fun `test backup created when overwriting`() {
        val originalData = mapOf("version" to 1)
        val updatedData = mapOf("version" to 2)

        nbtApi.write("test.nbt", originalData)
        nbtApi.write("test.nbt", updatedData)

        // Check that at least one backup file was created (with timestamp)
        val hasBackup = Files.walk(backupsDir, 2)
            .filter { it.fileName.toString().startsWith("test.nbt.") }
            .filter { it.fileName.toString().endsWith(".bak") }
            .findFirst()
            .isPresent

        assertTrue(hasBackup, "Backup should be created")
    }

    @Test
    fun `test forEach traversal`() {
        val data = mapOf(
            "name" to "test",
            "nested" to mapOf("value" to 42)
        )

        val visited = mutableListOf<Any?>()
        nbtApi.forEach(data) { value, path, parent ->
            visited.add(value)
        }

        assertTrue(visited.size > 0, "Should visit elements")
    }

    @Test
    fun `test filter finds matching elements`() {
        val data = mapOf(
            "items" to listOf(
                mapOf("type" to "stone"),
                mapOf("type" to "dirt")
            )
        )

        val stones = nbtApi.filter(data) { value, path, parent ->
            value == "stone"
        }

        assertEquals(1, stones.size, "Should find one stone")
    }
}
