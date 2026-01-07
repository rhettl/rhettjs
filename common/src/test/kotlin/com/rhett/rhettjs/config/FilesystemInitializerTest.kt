package com.rhett.rhettjs.config

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Tests for FilesystemInitializer - verifies directory structure creation
 * and resource extraction from JAR.
 */
class FilesystemInitializerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var rjsDir: Path

    @BeforeEach
    fun setup() {
        rjsDir = tempDir.resolve("rjs")
        ConfigManager.init(tempDir)
    }

    @AfterEach
    fun tearDown() {
        // Clean up if needed
    }

    @Test
    fun `initialize creates required directories`() {
        // Act
        FilesystemInitializer.initialize(rjsDir)

        // Assert - check all required directories exist
        val requiredDirs = FilesystemInitializer.getRequiredDirectories()
        assertTrue(requiredDirs.isNotEmpty(), "Should have required directories defined")

        for (dirName in requiredDirs) {
            val dir = rjsDir.resolve(dirName)
            assertTrue(dir.exists(), "Directory '$dirName' should exist")
            assertTrue(Files.isDirectory(dir), "Path '$dirName' should be a directory")
        }
    }

    @Test
    fun `initialize extracts type definitions`() {
        // Act
        FilesystemInitializer.initialize(rjsDir)

        // Assert - check type definition file was extracted
        val rhettjsDts = rjsDir.resolve("__types/rhettjs.d.ts")

        assertTrue(rhettjsDts.exists(), "rhettjs.d.ts should be extracted")

        // Verify content is not empty
        val rhettjsContent = rhettjsDts.readText()

        assertTrue(rhettjsContent.isNotEmpty(), "rhettjs.d.ts should have content")

        // Verify it contains expected declarations
        assertTrue(rhettjsContent.contains("declare module"), "rhettjs.d.ts should contain module declarations")
        assertTrue(rhettjsContent.contains("Structure"), "rhettjs.d.ts should contain Structure API")
        assertTrue(rhettjsContent.contains("World"), "rhettjs.d.ts should contain World API")
    }

    @Test
    fun `initialize extracts README`() {
        // Act
        FilesystemInitializer.initialize(rjsDir)

        // Assert - check README was extracted
        val readme = rjsDir.resolve("__types/README.md")
        assertTrue(readme.exists(), "__types/README.md should be extracted")

        val content = readme.readText()
        assertTrue(content.isNotEmpty(), "README should have content")
        assertTrue(content.contains("RhettJS TypeScript Definitions"), "README should contain title")
        assertTrue(content.contains("IDE Setup"), "README should contain IDE setup instructions")
    }

    @Test
    fun `initialize is idempotent`() {
        // Act - initialize twice
        FilesystemInitializer.initialize(rjsDir)
        FilesystemInitializer.initialize(rjsDir)

        // Assert - directories still exist and files are intact
        val rhettjsDts = rjsDir.resolve("__types/rhettjs.d.ts")
        assertTrue(rhettjsDts.exists(), "Type definitions should still exist after second initialization")

        val content = rhettjsDts.readText()
        assertTrue(content.isNotEmpty(), "Type definitions should still have content")
    }

    @Test
    fun `validateStructure returns false for missing directories`() {
        // Arrange - rjsDir doesn't exist yet
        assertFalse(rjsDir.exists(), "RJS directory should not exist yet")

        // Act
        val isValid = FilesystemInitializer.validateStructure(rjsDir)

        // Assert
        assertFalse(isValid, "Structure should be invalid when directory doesn't exist")
    }

    @Test
    fun `validateStructure returns true after initialization`() {
        // Act
        FilesystemInitializer.initialize(rjsDir)

        // Assert
        assertTrue(FilesystemInitializer.validateStructure(rjsDir), "Structure should be valid after initialization")
    }

    @Test
    fun `getRequiredDirectories returns expected directories`() {
        // Act
        val directories = FilesystemInitializer.getRequiredDirectories()

        // Assert - check expected directories are present
        assertTrue(directories.contains("__types"), "Should include __types directory")
        assertTrue(directories.contains("modules"), "Should include modules directory")
        assertTrue(directories.contains("scripts"), "Should include scripts directory")
        assertTrue(directories.contains("startup"), "Should include startup directory")
        assertTrue(directories.contains("server"), "Should include server directory")
        assertTrue(directories.contains("data"), "Should include data directory")
        assertTrue(directories.contains("assets"), "Should include assets directory")
        assertTrue(directories.contains("client"), "Should include client directory")
    }

    @Test
    fun `initialize creates root directory if missing`() {
        // Arrange - ensure rjsDir doesn't exist
        assertFalse(rjsDir.exists(), "RJS directory should not exist initially")

        // Act
        FilesystemInitializer.initialize(rjsDir)

        // Assert
        assertTrue(rjsDir.exists(), "RJS root directory should be created")
        assertTrue(Files.isDirectory(rjsDir), "RJS root should be a directory")
    }

    @Test
    fun `initialize does not re-extract existing type definitions`() {
        // Arrange - initialize once
        FilesystemInitializer.initialize(rjsDir)

        val rhettjsDts = rjsDir.resolve("__types/rhettjs.d.ts")
        val originalContent = rhettjsDts.readText()
        val originalModifiedTime = Files.getLastModifiedTime(rhettjsDts)

        // Wait a bit to ensure timestamp would change if file was rewritten
        Thread.sleep(100)

        // Act - initialize again
        FilesystemInitializer.initialize(rjsDir)

        // Assert - file should not be modified
        val newModifiedTime = Files.getLastModifiedTime(rhettjsDts)
        assertEquals(originalModifiedTime, newModifiedTime, "File should not be re-extracted if it already exists")

        val newContent = rhettjsDts.readText()
        assertEquals(originalContent, newContent, "Content should remain the same")
    }
}
