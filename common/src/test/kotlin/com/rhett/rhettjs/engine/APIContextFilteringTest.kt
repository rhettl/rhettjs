package com.rhett.rhettjs.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import com.rhett.rhettjs.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for API context filtering.
 * Tests that scripts only have access to APIs appropriate for their execution context:
 * - CLIENT scripts: UI + universal APIs (Store, NBT, Runtime, Console)
 * - SERVER scripts: World, Server, Commands + universal APIs
 * - STARTUP/UTILITY: Universal APIs
 */
class APIContextFilteringTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        ConfigManager.init(tempDir)
        GraalEngine.setScriptsDirectory(tempDir)
        GraalEngine.reset()
    }

    // ========================================
    // CLIENT script tests - Server API access should FAIL
    // ========================================

    @Test
    fun `test CLIENT script cannot access World API`() {
        val script = ScriptInfo(
            name = "test-client-no-world.js",
            path = createTempScript("""
                import World from 'World';
                console.log('Should not reach here');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Error, "CLIENT script should not be able to import World API")
    }

    @Test
    fun `test CLIENT script cannot access Server API`() {
        val script = ScriptInfo(
            name = "test-client-no-server.js",
            path = createTempScript("""
                import Server from 'Server';
                console.log('Should not reach here');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Error, "CLIENT script should not be able to import Server API")
    }

    @Test
    fun `test CLIENT script cannot access Commands API`() {
        val script = ScriptInfo(
            name = "test-client-no-commands.js",
            path = createTempScript("""
                import Commands from 'Commands';
                console.log('Should not reach here');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Error, "CLIENT script should not be able to import Commands API")
    }

    @Test
    fun `test CLIENT script cannot access WorldgenStructure API`() {
        val script = ScriptInfo(
            name = "test-client-no-worldgen.js",
            path = createTempScript("""
                import WorldgenStructure from 'WorldgenStructure';
                console.log('Should not reach here');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Error, "CLIENT script should not be able to import WorldgenStructure API")
    }

    // ========================================
    // CLIENT script tests - Client API access should SUCCEED
    // ========================================

    @Test
    fun `test CLIENT script CAN access UI API`() {
        val script = ScriptInfo(
            name = "test-client-has-ui.js",
            path = createTempScript("""
                import UI from 'UI';

                if (typeof UI !== 'object') {
                    throw new Error('UI should be an object');
                }

                const methods = ['createScreen', 'getScreen', 'removeScreen'];
                for (const method of methods) {
                    if (typeof UI[method] !== 'function') {
                        throw new Error('UI.' + method + ' should be a function');
                    }
                }

                console.log('CLIENT script can access UI API');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "CLIENT script should be able to access UI API")
    }

    // ========================================
    // CLIENT script tests - Universal API access should SUCCEED
    // ========================================

    @Test
    fun `test CLIENT script CAN access Store API`() {
        val script = ScriptInfo(
            name = "test-client-has-store.js",
            path = createTempScript("""
                import Store from 'Store';

                if (typeof Store !== 'object') {
                    throw new Error('Store should be an object');
                }

                // Store requires namespace() - get default namespace
                const store = Store.namespace('test');
                store.set('test', { value: 123 });
                const data = store.get('test');

                if (data.value !== 123) {
                    throw new Error('Store should persist data');
                }

                console.log('CLIENT script can access Store API');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "CLIENT script should be able to access Store API")
    }

    @Test
    fun `test CLIENT script CAN access NBT API`() {
        val script = ScriptInfo(
            name = "test-client-has-nbt.js",
            path = createTempScript("""
                import NBT from 'NBT';

                if (typeof NBT !== 'object') {
                    throw new Error('NBT should be an object');
                }

                // NBT has compound(), not create()
                const nbt = NBT.compound({ test: 'value', count: 42 });

                if (typeof nbt !== 'object') {
                    throw new Error('NBT.compound should return an object');
                }

                console.log('CLIENT script can access NBT API');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "CLIENT script should be able to access NBT API")
    }

    @Test
    fun `test CLIENT script CAN access Runtime global`() {
        val script = ScriptInfo(
            name = "test-client-has-runtime.js",
            path = createTempScript("""
                // Runtime is global, no import needed
                if (typeof Runtime !== 'object') {
                    throw new Error('Runtime should be an object');
                }

                if (typeof Runtime.env !== 'object') {
                    throw new Error('Runtime.env should be an object');
                }

                console.log('CLIENT script can access Runtime');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "CLIENT script should be able to access Runtime global")
    }

    @Test
    fun `test CLIENT script CAN access Console global`() {
        val script = ScriptInfo(
            name = "test-client-has-console.js",
            path = createTempScript("""
                // Console is global, no import needed
                if (typeof console !== 'object') {
                    throw new Error('console should be an object');
                }

                console.log('CLIENT script can access console');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "CLIENT script should be able to access console")
    }

    // ========================================
    // SERVER script tests - Client API access should FAIL
    // ========================================

    @Test
    fun `test SERVER script cannot access UI API`() {
        val script = ScriptInfo(
            name = "test-server-no-ui.js",
            path = createTempScript("""
                import UI from 'UI';
                console.log('Should not reach here');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Error, "SERVER script should not be able to import UI API")
    }

    // ========================================
    // SERVER script tests - Server API access should SUCCEED
    // ========================================

    @Test
    fun `test SERVER script CAN access World API`() {
        val script = ScriptInfo(
            name = "test-server-has-world.js",
            path = createTempScript("""
                import World from 'World';

                if (typeof World !== 'object') {
                    throw new Error('World should be an object');
                }

                console.log('SERVER script can access World API');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "SERVER script should be able to access World API")
    }

    @Test
    fun `test SERVER script CAN access Server API`() {
        val script = ScriptInfo(
            name = "test-server-has-server.js",
            path = createTempScript("""
                import Server from 'Server';

                if (typeof Server !== 'object') {
                    throw new Error('Server should be an object');
                }

                console.log('SERVER script can access Server API');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "SERVER script should be able to access Server API")
    }

    @Test
    fun `test SERVER script CAN access Commands API`() {
        val script = ScriptInfo(
            name = "test-server-has-commands.js",
            path = createTempScript("""
                import Commands from 'Commands';

                if (typeof Commands !== 'object') {
                    throw new Error('Commands should be an object');
                }

                console.log('SERVER script can access Commands API');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "SERVER script should be able to access Commands API")
    }

    // ========================================
    // SERVER script tests - Universal API access should SUCCEED
    // ========================================

    @Test
    fun `test SERVER script CAN access Store API`() {
        val script = ScriptInfo(
            name = "test-server-has-store.js",
            path = createTempScript("""
                import Store from 'Store';

                if (typeof Store !== 'object') {
                    throw new Error('Store should be an object');
                }

                console.log('SERVER script can access Store API');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "SERVER script should be able to access Store API")
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
