package com.rhett.rhettjs.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Unit tests for ConfigManager.
 * Tests configuration loading, saving, and debug logging functionality.
 */
class ConfigManagerTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        // Reset ConfigManager state between tests by reinitializing
        ConfigManager.init(tempDir)
    }

    @Test
    fun `test default config creation`() {
        val configFile = tempDir.resolve("rhettjs.json")

        // Config file should be created
        assertTrue(Files.exists(configFile), "Config file should be created")

        // Check default values
        val config = ConfigManager.get()
        assertTrue(config.enabled, "Should be enabled by default")
        assertTrue(config.debug_logging, "Debug logging should be enabled by default")
    }

    @Test
    fun `test config file format`() {
        val configFile = tempDir.resolve("rhettjs.json")
        val content = configFile.readText()

        // Should be valid JSON with expected fields
        assertTrue(content.contains("enabled"), "Should contain 'enabled' field")
        assertTrue(content.contains("debug_logging"), "Should contain 'debug_logging' field")
    }

    @Test
    fun `test load existing config`() {
        val configFile = tempDir.resolve("rhettjs.json")

        // Write a custom config
        configFile.writeText("""
            {
              "enabled": false,
              "debug_logging": false
            }
        """.trimIndent())

        // Reload
        ConfigManager.load()

        val config = ConfigManager.get()
        assertFalse(config.enabled, "Should load enabled=false")
        assertFalse(config.debug_logging, "Should load debug_logging=false")
    }

    @Test
    fun `test save config`() {
        val configFile = tempDir.resolve("rhettjs.json")

        // Save should work without errors
        assertDoesNotThrow {
            ConfigManager.save()
        }

        // File should exist
        assertTrue(Files.exists(configFile), "Config file should exist after save")
    }

    @Test
    fun `test isEnabled returns correct value`() {
        assertTrue(ConfigManager.isEnabled(), "Should be enabled by default")

        // Manually set to false and reload
        val configFile = tempDir.resolve("rhettjs.json")
        configFile.writeText("""{"enabled": false, "debug_logging": true}""")
        ConfigManager.load()

        assertFalse(ConfigManager.isEnabled(), "Should return false when disabled")
    }

    @Test
    fun `test isDebugEnabled returns correct value`() {
        assertTrue(ConfigManager.isDebugEnabled(), "Debug should be enabled by default")

        // Manually set to false and reload
        val configFile = tempDir.resolve("rhettjs.json")
        configFile.writeText("""{"enabled": true, "debug_logging": false}""")
        ConfigManager.load()

        assertFalse(ConfigManager.isDebugEnabled(), "Should return false when debug disabled")
    }

    @Test
    fun `test debug method does not throw`() {
        assertDoesNotThrow {
            ConfigManager.debug("Test debug message")
        }
    }

    @Test
    fun `test debugLazy method does not throw`() {
        assertDoesNotThrow {
            ConfigManager.debugLazy { "Lazy debug message" }
        }
    }

    @Test
    fun `test debugLazy only evaluates when debug enabled`() {
        var evaluated = false

        ConfigManager.debugLazy {
            evaluated = true
            "Test"
        }

        // With debug enabled (default), should evaluate
        assertTrue(evaluated, "Should evaluate lambda when debug enabled")

        // Disable debug
        val configFile = tempDir.resolve("rhettjs.json")
        configFile.writeText("""{"enabled": true, "debug_logging": false}""")
        ConfigManager.load()

        evaluated = false
        ConfigManager.debugLazy {
            evaluated = true
            "Test"
        }

        assertFalse(evaluated, "Should NOT evaluate lambda when debug disabled")
    }

    @Test
    fun `test malformed config uses defaults`() {
        val configFile = tempDir.resolve("rhettjs.json")
        configFile.writeText("{ invalid json }")

        // Should not crash, should use defaults
        assertDoesNotThrow {
            ConfigManager.load()
        }

        val config = ConfigManager.get()
        assertTrue(config.enabled, "Should fallback to default enabled=true")
        assertFalse(config.debug_logging, "Should fallback to default debug_logging=false")
    }

    @Test
    fun `test missing config directory is created`() {
        val nestedDir = tempDir.resolve("nested/config/dir")

        assertDoesNotThrow {
            ConfigManager.init(nestedDir)
        }

        assertTrue(Files.exists(nestedDir), "Nested directory should be created")
        assertTrue(Files.exists(nestedDir.resolve("rhettjs.json")), "Config file should be created in nested dir")
    }
}
