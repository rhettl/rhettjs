package com.rhett.rhettjs.engine

import com.rhett.rhettjs.config.ConfigManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Unit tests for ScriptRegistry.
 * Tests script discovery, validation, and inventory management.
 */
class ScriptRegistryTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        // Initialize ConfigManager with temp directory
        val configDir = tempDir.resolve("config")
        Files.createDirectories(configDir)
        ConfigManager.init(configDir)

        // Clear registry between tests
        ScriptRegistry.clear()

        // Create script directories
        Files.createDirectories(tempDir.resolve("startup"))
        Files.createDirectories(tempDir.resolve("server"))
        Files.createDirectories(tempDir.resolve("scripts"))
    }

    @Test
    fun `test scan empty directories`() {
        ScriptRegistry.scan(tempDir)

        assertEquals(0, ScriptRegistry.getAllScripts().size, "Should find no scripts in empty directories")
    }

    @Test
    fun `test discover single script`() {
        // Create a valid script
        val scriptFile = tempDir.resolve("scripts/test.js")
        scriptFile.writeText("console.log('test');")

        ScriptRegistry.scan(tempDir)

        assertEquals(1, ScriptRegistry.getAllScripts().size, "Should find one script")

        val script = ScriptRegistry.getScript("test")
        assertNotNull(script, "Should find script by name")
        assertEquals("test", script?.name)
        assertEquals(ScriptCategory.UTILITY, script?.category)
        assertEquals(ScriptStatus.LOADED, script?.status)
    }

    @Test
    fun `test discover scripts in multiple categories`() {
        // Create scripts in different categories
        tempDir.resolve("startup/init.js").writeText("console.log('startup');")
        tempDir.resolve("server/events.js").writeText("console.log('server');")
        tempDir.resolve("scripts/util.js").writeText("console.log('utility');")

        ScriptRegistry.scan(tempDir)

        assertEquals(3, ScriptRegistry.getAllScripts().size, "Should find all scripts")
        assertEquals(1, ScriptRegistry.getScripts(ScriptCategory.STARTUP).size)
        assertEquals(1, ScriptRegistry.getScripts(ScriptCategory.SERVER).size)
        assertEquals(1, ScriptRegistry.getScripts(ScriptCategory.UTILITY).size)
    }

    @Test
    fun `test discover nested scripts`() {
        // Create nested script
        val nestedDir = tempDir.resolve("scripts/nested/deep")
        Files.createDirectories(nestedDir)
        nestedDir.resolve("nested.js").writeText("console.log('nested');")

        ScriptRegistry.scan(tempDir)

        assertEquals(1, ScriptRegistry.getAllScripts().size)

        val script = ScriptRegistry.getScript("nested/deep/nested")
        assertNotNull(script, "Should find nested script")
    }

    @Test
    fun `test validate syntax error script`() {
        // Create script with syntax error
        val scriptFile = tempDir.resolve("scripts/error.js")
        scriptFile.writeText("console.log('missing semicolon' // syntax error")

        ScriptRegistry.scan(tempDir)

        val script = ScriptRegistry.getScript("error")
        assertNotNull(script, "Should still register script with error")
        assertEquals(ScriptStatus.ERROR, script?.status, "Should mark as ERROR")
    }

    @Test
    fun `test get script by name and category`() {
        tempDir.resolve("scripts/test.js").writeText("console.log('test');")
        tempDir.resolve("server/test.js").writeText("console.log('server');")

        ScriptRegistry.scan(tempDir)

        // Get by name only (first match)
        val scriptAny = ScriptRegistry.getScript("test")
        assertNotNull(scriptAny)

        // Get by name and category
        val scriptUtility = ScriptRegistry.getScript("test", ScriptCategory.UTILITY)
        assertNotNull(scriptUtility)
        assertEquals(ScriptCategory.UTILITY, scriptUtility?.category)

        val scriptServer = ScriptRegistry.getScript("server/test", ScriptCategory.SERVER)
        assertNotNull(scriptServer)
        assertEquals(ScriptCategory.SERVER, scriptServer?.category)
    }

    @Test
    fun `test getScripts by category`() {
        tempDir.resolve("scripts/a.js").writeText("console.log('a');")
        tempDir.resolve("scripts/b.js").writeText("console.log('b');")
        tempDir.resolve("server/c.js").writeText("console.log('c');")

        ScriptRegistry.scan(tempDir)

        val utilityScripts = ScriptRegistry.getScripts(ScriptCategory.UTILITY)
        assertEquals(2, utilityScripts.size)

        val serverScripts = ScriptRegistry.getScripts(ScriptCategory.SERVER)
        assertEquals(1, serverScripts.size)

        val startupScripts = ScriptRegistry.getScripts(ScriptCategory.STARTUP)
        assertEquals(0, startupScripts.size)
    }

    @Test
    fun `test markFailed updates script status`() {
        tempDir.resolve("scripts/test.js").writeText("console.log('test');")
        ScriptRegistry.scan(tempDir)

        val script = ScriptRegistry.getScript("test")
        assertEquals(ScriptStatus.LOADED, script?.status)

        // Mark as failed
        ScriptRegistry.markFailed("test", RuntimeException("Test error"))

        val updatedScript = ScriptRegistry.getScript("test")
        assertEquals(ScriptStatus.ERROR, updatedScript?.status)
    }

    @Test
    fun `test getFailedScripts`() {
        tempDir.resolve("scripts/good.js").writeText("console.log('good');")
        tempDir.resolve("scripts/bad.js").writeText("console.log( // syntax error")

        ScriptRegistry.scan(tempDir)

        val failedScripts = ScriptRegistry.getFailedScripts()
        assertEquals(1, failedScripts.size)
        assertEquals("bad", failedScripts[0].name)
    }

    @Test
    fun `test getFailedScripts by category`() {
        tempDir.resolve("scripts/bad1.js").writeText("syntax error {")
        tempDir.resolve("server/bad2.js").writeText("syntax error }")

        ScriptRegistry.scan(tempDir)

        val allFailed = ScriptRegistry.getFailedScripts()
        assertEquals(2, allFailed.size)

        val utilityFailed = ScriptRegistry.getFailedScripts(ScriptCategory.UTILITY)
        assertEquals(1, utilityFailed.size)

        val serverFailed = ScriptRegistry.getFailedScripts(ScriptCategory.SERVER)
        assertEquals(1, serverFailed.size)
    }

    @Test
    fun `test clear registry`() {
        tempDir.resolve("scripts/test.js").writeText("console.log('test');")
        ScriptRegistry.scan(tempDir)

        assertEquals(1, ScriptRegistry.getAllScripts().size)

        ScriptRegistry.clear()

        assertEquals(0, ScriptRegistry.getAllScripts().size)
    }

    @Test
    fun `test scan ignores non-js files`() {
        tempDir.resolve("scripts/test.js").writeText("console.log('test');")
        tempDir.resolve("scripts/readme.txt").writeText("This is a readme")
        tempDir.resolve("scripts/config.json").writeText("{}")

        ScriptRegistry.scan(tempDir)

        assertEquals(1, ScriptRegistry.getAllScripts().size, "Should only find .js files")
    }

    @Test
    fun `test scan handles missing directories`() {
        // Don't create the directories
        val emptyDir = tempDir.resolve("empty")
        Files.createDirectories(emptyDir)

        assertDoesNotThrow {
            ScriptRegistry.scan(emptyDir)
        }

        // Should create missing directories
        assertTrue(Files.exists(emptyDir.resolve("startup")))
        assertTrue(Files.exists(emptyDir.resolve("server")))
        assertTrue(Files.exists(emptyDir.resolve("scripts")))
    }

    @Test
    fun `test lastModified is captured`() {
        val scriptFile = tempDir.resolve("scripts/test.js")
        scriptFile.writeText("console.log('test');")

        val beforeScan = System.currentTimeMillis()
        Thread.sleep(10) // Ensure some time passes

        ScriptRegistry.scan(tempDir)

        val script = ScriptRegistry.getScript("test")
        assertNotNull(script)
        assertTrue(script!!.lastModified > 0, "Should capture lastModified timestamp")
        assertTrue(script.lastModified <= System.currentTimeMillis(), "Timestamp should be in the past")
    }

    @Test
    fun `test rescan updates registry`() {
        // Initial scan with one script
        tempDir.resolve("scripts/test1.js").writeText("console.log('test1');")
        ScriptRegistry.scan(tempDir)
        assertEquals(1, ScriptRegistry.getAllScripts().size)

        // Add another script and rescan
        tempDir.resolve("scripts/test2.js").writeText("console.log('test2');")
        ScriptRegistry.scan(tempDir)
        assertEquals(2, ScriptRegistry.getAllScripts().size)
    }
}
