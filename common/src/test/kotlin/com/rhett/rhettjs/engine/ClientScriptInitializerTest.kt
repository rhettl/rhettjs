package com.rhett.rhettjs.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import com.rhett.rhettjs.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Unit tests for ClientScriptInitializer.
 * Tests client script discovery, execution, and initialization flow.
 *
 * Note: Some integration tests (F3+T reload, Minecraft client interaction)
 * require runtime testing and are not covered here.
 */
class ClientScriptInitializerTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        ConfigManager.init(tempDir)
        GraalEngine.setScriptsDirectory(tempDir)
        GraalEngine.reset()
        ScriptRegistry.clear()

        // Create client directory
        Files.createDirectories(tempDir.resolve("client"))
    }

    @Test
    fun `test client scripts are scanned from client directory`() {
        // Create client scripts
        tempDir.resolve("client/ui-setup.js").writeText("console.log('ui setup');")
        tempDir.resolve("client/keybinds.js").writeText("console.log('keybinds');")

        // Scan scripts
        ScriptRegistry.scan(tempDir)

        // Verify CLIENT scripts were found
        val clientScripts = ScriptRegistry.getScripts(ScriptCategory.CLIENT)
        assertEquals(2, clientScripts.size, "Should find both client scripts")

        val scriptNames = clientScripts.map { it.name }.sorted()
        assertEquals(listOf("client/keybinds", "client/ui-setup"), scriptNames)
    }

    @Test
    fun `test nested client scripts are discovered`() {
        // Create nested client scripts
        val nestedDir = tempDir.resolve("client/screens/inventory")
        Files.createDirectories(nestedDir)
        nestedDir.resolve("custom-inventory.js").writeText("console.log('custom inventory');")

        ScriptRegistry.scan(tempDir)

        val clientScripts = ScriptRegistry.getScripts(ScriptCategory.CLIENT)
        assertEquals(1, clientScripts.size)

        val script = ScriptRegistry.getScript("client/screens/inventory/custom-inventory", ScriptCategory.CLIENT)
        assertNotNull(script, "Should find nested client script")
    }

    @Test
    fun `test client scripts can be executed individually`() {
        val script = ScriptInfo(
            name = "test-client-script.js",
            path = createTempScript("""
                console.log('CLIENT script executed');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "CLIENT script should execute successfully")
    }

    @Test
    fun `test client script with UI API access`() {
        val script = ScriptInfo(
            name = "test-ui-access.js",
            path = createTempScript("""
                import UI from 'UI';

                const screen = UI.createScreen('test-screen');
                if (!screen) {
                    throw new Error('UI.createScreen should return a screen object');
                }

                console.log('UI API access works in CLIENT script');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "CLIENT script should access UI API")
    }

    @Test
    fun `test client script with error is marked as failed`() {
        tempDir.resolve("client/broken.js").writeText("syntax error {")

        ScriptRegistry.scan(tempDir)

        val script = ScriptRegistry.getScript("client/broken", ScriptCategory.CLIENT)
        assertNotNull(script, "Should register broken script")
        assertEquals(ScriptStatus.ERROR, script?.status, "Should mark as ERROR")
    }

    @Test
    fun `test executeClientScripts processes all CLIENT scripts`() {
        // Create multiple client scripts
        tempDir.resolve("client/script1.js").writeText("console.log('script 1');")
        tempDir.resolve("client/script2.js").writeText("console.log('script 2');")
        tempDir.resolve("client/script3.js").writeText("console.log('script 3');")

        // Scan scripts
        ScriptRegistry.scan(tempDir)

        // Verify all were found
        val clientScripts = ScriptRegistry.getScripts(ScriptCategory.CLIENT)
        assertEquals(3, clientScripts.size, "Should find 3 client scripts")

        // Execute each script individually (simulating what executeClientScripts does)
        var successCount = 0
        clientScripts.forEach { script ->
            val result = GraalEngine.executeScript(script)
            if (result is ScriptResult.Success) {
                successCount++
            }
        }

        assertEquals(3, successCount, "All 3 scripts should execute successfully")
    }

    @Test
    fun `test context reset between executions`() {
        // First execution - set a global variable
        val script1 = ScriptInfo(
            name = "set-global.js",
            path = createTempScript("""
                globalThis.testValue = 'first execution';
                console.log('Set global:', globalThis.testValue);
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result1 = GraalEngine.executeScript(script1)
        assertTrue(result1 is ScriptResult.Success)

        // Reset context (simulating reload)
        GraalEngine.reset()

        // Second execution - global should be reset
        val script2 = ScriptInfo(
            name = "check-global.js",
            path = createTempScript("""
                if (typeof globalThis.testValue !== 'undefined') {
                    throw new Error('Global should be reset after context reset');
                }
                console.log('Global is reset');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result2 = GraalEngine.executeScript(script2)
        assertTrue(result2 is ScriptResult.Success, "Global should be cleared after reset")
    }

    @Test
    fun `test client scripts isolated from server scripts`() {
        // Create both client and server scripts
        Files.createDirectories(tempDir.resolve("server"))
        tempDir.resolve("client/client-script.js").writeText("console.log('client');")
        tempDir.resolve("server/server-script.js").writeText("console.log('server');")

        ScriptRegistry.scan(tempDir)

        val clientScripts = ScriptRegistry.getScripts(ScriptCategory.CLIENT)
        val serverScripts = ScriptRegistry.getScripts(ScriptCategory.SERVER)

        assertEquals(1, clientScripts.size, "Should find 1 client script")
        assertEquals(1, serverScripts.size, "Should find 1 server script")

        // Verify they have different categories
        assertEquals(ScriptCategory.CLIENT, clientScripts[0].category)
        assertEquals(ScriptCategory.SERVER, serverScripts[0].category)
    }

    @Test
    fun `test client script can access StructureNbt API (universal)`() {
        val script = ScriptInfo(
            name = "test-structure-access.js",
            path = createTempScript("""
                import StructureNbt from 'StructureNbt';

                if (typeof StructureNbt !== 'object') {
                    throw new Error('StructureNbt should be accessible');
                }

                console.log('StructureNbt API access works in CLIENT script');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "CLIENT script should access StructureNbt (universal API)")
    }

    // ========================================
    // Helper methods
    // ========================================

    private fun createTempScript(content: String): Path {
        val scriptFile = Files.createTempFile(tempDir, "test", ".js")
        Files.writeString(scriptFile, content)
        return scriptFile
    }
}
