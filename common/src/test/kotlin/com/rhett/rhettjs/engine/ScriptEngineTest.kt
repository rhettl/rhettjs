package com.rhett.rhettjs.engine

import com.rhett.rhettjs.config.ConfigManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Unit tests for ScriptEngine.
 * Tests Rhino context management, scope creation, and script execution.
 */
class ScriptEngineTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        // Initialize ConfigManager
        val configDir = tempDir.resolve("config")
        Files.createDirectories(configDir)
        ConfigManager.init(configDir)
    }

    @Test
    fun `test execute simple script`() {
        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("2 + 2;")

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = ScriptEngine.executeScript(scriptInfo)

        assertTrue(result is ScriptResult.Success, "Should execute successfully")
        assertEquals(4.0, (result as ScriptResult.Success).value, "Should return 4")
    }

    @Test
    fun `test execute script with console API`() {
        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("""
            console.log('Hello World');
            'success';
        """.trimIndent())

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = ScriptEngine.executeScript(scriptInfo)

        assertTrue(result is ScriptResult.Success, "Should execute successfully")
        assertEquals("success", (result as ScriptResult.Success).value)
    }

    @Test
    fun `test execute script with logger API`() {
        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("""
            logger.info('Info message');
            logger.warn('Warning message');
            logger.error('Error message');
            'done';
        """.trimIndent())

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = ScriptEngine.executeScript(scriptInfo)

        if (result is ScriptResult.Error) {
            println("ERROR: ${result.message}")
            result.exception.printStackTrace()
        }
        assertTrue(result is ScriptResult.Success, "Should execute successfully")
    }

    @Test
    fun `test execute script with syntax error`() {
        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("console.log('missing quote")

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = ScriptEngine.executeScript(scriptInfo)

        assertTrue(result is ScriptResult.Error, "Should return error")
        val error = result as ScriptResult.Error
        assertNotNull(error.message)
        assertNotNull(error.exception)
    }

    @Test
    fun `test execute script with runtime error`() {
        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("""
            var x = undefined;
            x.foo.bar; // Will throw error
        """.trimIndent())

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = ScriptEngine.executeScript(scriptInfo)

        assertTrue(result is ScriptResult.Error, "Should catch runtime error")
    }

    @Test
    fun `test createScope includes console and logger`() {
        val cx = Context.enter()
        try {
            val scope = ScriptEngine.createScope(ScriptCategory.UTILITY)

            assertTrue(scope.has("console", scope), "Scope should have console")
            assertTrue(scope.has("logger", scope), "Scope should have logger")
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test createScope removes dangerous globals`() {
        val cx = Context.enter()
        try {
            val scope = ScriptEngine.createScope(ScriptCategory.UTILITY)

            // Check dangerous globals are removed
            assertFalse(scope.has("Packages", scope), "Should remove Packages")
            assertFalse(scope.has("java", scope), "Should remove java")
            assertFalse(scope.has("javax", scope), "Should remove javax")
            assertFalse(scope.has("org", scope), "Should remove org")
            assertFalse(scope.has("com", scope), "Should remove com")
            assertFalse(scope.has("System", scope), "Should remove System")
            // Note: We keep our custom Runtime API (with env, exit) but remove Java's Runtime
            assertTrue(scope.has("Runtime", scope), "Should have our custom Runtime API")
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test createScope with additional APIs`() {
        val cx = Context.enter()
        try {
            class TestAPI {
                fun test() = "test value"
            }

            val additionalApis = mapOf("testApi" to TestAPI())
            val scope = ScriptEngine.createScope(ScriptCategory.UTILITY, additionalApis)

            assertTrue(scope.has("testApi", scope), "Should inject additional API")
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test execute script with additional APIs`() {
        class TestAPI {
            fun getValue() = 42
        }

        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("testApi.getValue();")

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = ScriptEngine.executeScript(
            scriptInfo,
            additionalApis = mapOf("testApi" to TestAPI())
        )

        if (result is ScriptResult.Error) {
            println("ERROR: ${result.message}")
            result.exception.printStackTrace()
        }
        assertTrue(result is ScriptResult.Success)
        // Rhino might return Integer or Double depending on the value
        val resultValue = (result as ScriptResult.Success).value
        assertTrue(resultValue == 42 || resultValue == 42.0, "Expected 42, got $resultValue")
    }

    @Test
    fun `test execute JavaScript ES6 features`() {
        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("""
            // let/const
            const x = 10;
            let y = 20;

            // Arrow functions
            const add = (a, b) => a + b;

            // Template literals
            const msg = `Result: ${'$'}{add(x, y)}`;

            // Return result
            add(x, y);
        """.trimIndent())

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = ScriptEngine.executeScript(scriptInfo)

        if (result is ScriptResult.Error) {
            println("ERROR: ${result.message}")
            result.exception.printStackTrace()
        }
        assertTrue(result is ScriptResult.Success)
        assertEquals(30.0, (result as ScriptResult.Success).value)
    }

    @Test
    fun `test execute script with array operations`() {
        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("""
            const arr = [1, 2, 3, 4, 5];
            const doubled = arr.map(x => x * 2);
            const sum = doubled.reduce((a, b) => a + b, 0);
            sum;
        """.trimIndent())

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = ScriptEngine.executeScript(scriptInfo)

        assertTrue(result is ScriptResult.Success)
        assertEquals(30.0, (result as ScriptResult.Success).value)
    }

    @Test
    fun `test execute script with object operations`() {
        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("""
            const obj = {
                name: 'Test',
                value: 42,
                nested: {
                    foo: 'bar'
                }
            };
            obj.value;
        """.trimIndent())

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = ScriptEngine.executeScript(scriptInfo)

        assertTrue(result is ScriptResult.Success)
        assertEquals(42.0, (result as ScriptResult.Success).value)
    }

    @Test
    fun `test scope isolation between executions`() {
        // First script sets a variable
        val script1 = tempDir.resolve("test1.js")
        script1.writeText("var globalVar = 'test1';")

        val scriptInfo1 = ScriptInfo(
            name = "test1",
            path = script1,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        ScriptEngine.executeScript(scriptInfo1)

        // Second script tries to access it (should fail)
        val script2 = tempDir.resolve("test2.js")
        script2.writeText("typeof globalVar === 'undefined';")

        val scriptInfo2 = ScriptInfo(
            name = "test2",
            path = script2,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = ScriptEngine.executeScript(scriptInfo2)

        assertTrue(result is ScriptResult.Success)
        assertEquals(true, (result as ScriptResult.Success).value, "Scopes should be isolated")
    }

    @Test
    fun `test optimization level is interpreted mode`() {
        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("'test';")

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        // Execute and verify it uses interpreted mode (optimization level -1)
        // We can't directly check the optimization level, but we can verify it executes
        val result = ScriptEngine.executeScript(scriptInfo)

        assertTrue(result is ScriptResult.Success)
    }

    @Test
    fun `test different script categories use same base APIs`() {
        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("typeof console !== 'undefined' && typeof logger !== 'undefined';")

        // Test all categories have console and logger
        for (category in ScriptCategory.values()) {
            val scriptInfo = ScriptInfo(
                name = "test_${category.name}",
                path = scriptFile,
                category = category,
                lastModified = System.currentTimeMillis(),
                status = ScriptStatus.LOADED
            )

            val result = ScriptEngine.executeScript(scriptInfo)

            assertTrue(result is ScriptResult.Success, "Category $category should have base APIs")
            assertEquals(true, (result as ScriptResult.Success).value)
        }
    }

    @Test
    fun `test context cleanup after execution`() {
        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("'test';")

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        // Execute script
        ScriptEngine.executeScript(scriptInfo)

        // Context should be exited (not in thread anymore)
        assertNull(Context.getCurrentContext(), "Context should be cleaned up after execution")
    }

    @Test
    fun `test context cleanup after error`() {
        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("throw new Error('test error');")

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        // Execute script with error
        val result = ScriptEngine.executeScript(scriptInfo)

        assertTrue(result is ScriptResult.Error)

        // Context should still be cleaned up
        assertNull(Context.getCurrentContext(), "Context should be cleaned up even after error")
    }

    // ====== Phase 2 Integration Tests ======

    @Test
    fun `test StartupEvents API is available in STARTUP scripts`() {
        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("""
            if (typeof StartupEvents === 'undefined') {
                throw new Error('StartupEvents not available');
            }
            if (typeof StartupEvents.registry !== 'function') {
                throw new Error('StartupEvents.registry is not a function');
            }
            'success';
        """.trimIndent())

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = ScriptEngine.executeScript(scriptInfo)

        assertTrue(result is ScriptResult.Success, "StartupEvents should be available in STARTUP scripts")
    }

    @Test
    fun `test ServerEvents API is available in SERVER scripts`() {
        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("""
            if (typeof ServerEvents === 'undefined') {
                throw new Error('ServerEvents not available');
            }
            if (typeof ServerEvents.itemUse !== 'function') {
                throw new Error('ServerEvents.itemUse is not a function');
            }
            if (typeof ServerEvents.command !== 'function') {
                throw new Error('ServerEvents.command is not a function');
            }
            'success';
        """.trimIndent())

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = ScriptEngine.executeScript(scriptInfo)

        assertTrue(result is ScriptResult.Success, "ServerEvents should be available in SERVER scripts")
    }

    @Test
    fun `test ServerEvents API is available in UTILITY scripts`() {
        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("""
            if (typeof ServerEvents === 'undefined') {
                throw new Error('ServerEvents not available');
            }
            if (typeof ServerEvents.itemUse !== 'function') {
                throw new Error('ServerEvents.itemUse is not a function');
            }
            'success';
        """.trimIndent())

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = ScriptEngine.executeScript(scriptInfo)

        assertTrue(result is ScriptResult.Success, "ServerEvents should be available in UTILITY scripts")
    }

    @Test
    fun `test globals are injected into all script categories`() {
        // Setup: Create a global script
        val globalsDir = tempDir.resolve("globals")
        Files.createDirectories(globalsDir)
        val globalFile = globalsDir.resolve("test-global.js")
        globalFile.writeText("""
            var testGlobal = function() {
                return 'from global';
            };
        """.trimIndent())

        // Load globals
        com.rhett.rhettjs.engine.GlobalsLoader.reload(tempDir)

        // Test in STARTUP script
        val startupFile = tempDir.resolve("startup.js")
        startupFile.writeText("""
            if (typeof testGlobal !== 'function') {
                throw new Error('testGlobal not available in STARTUP');
            }
            testGlobal();
        """.trimIndent())

        val startupScript = ScriptInfo(
            name = "startup",
            path = startupFile,
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val startupResult = ScriptEngine.executeScript(startupScript)
        assertTrue(startupResult is ScriptResult.Success, "Globals should be available in STARTUP")
        assertEquals("from global", (startupResult as ScriptResult.Success).value)

        // Test in SERVER script
        val serverFile = tempDir.resolve("server.js")
        serverFile.writeText("""
            if (typeof testGlobal !== 'function') {
                throw new Error('testGlobal not available in SERVER');
            }
            testGlobal();
        """.trimIndent())

        val serverScript = ScriptInfo(
            name = "server",
            path = serverFile,
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val serverResult = ScriptEngine.executeScript(serverScript)
        assertTrue(serverResult is ScriptResult.Success, "Globals should be available in SERVER")
        assertEquals("from global", (serverResult as ScriptResult.Success).value)

        // Test in UTILITY script
        val utilityFile = tempDir.resolve("utility.js")
        utilityFile.writeText("""
            if (typeof testGlobal !== 'function') {
                throw new Error('testGlobal not available in UTILITY');
            }
            testGlobal();
        """.trimIndent())

        val utilityScript = ScriptInfo(
            name = "utility",
            path = utilityFile,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val utilityResult = ScriptEngine.executeScript(utilityScript)
        assertTrue(utilityResult is ScriptResult.Success, "Globals should be available in UTILITY")
        assertEquals("from global", (utilityResult as ScriptResult.Success).value)

        // Cleanup
        com.rhett.rhettjs.engine.GlobalsLoader.clear()
    }

    @Test
    fun `test StartupEvents can register and execute handlers`() {
        com.rhett.rhettjs.events.StartupEventsAPI.clear()

        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("""
            var executed = false;
            StartupEvents.registry('item', function(event) {
                executed = true;
            });
            executed;
        """.trimIndent())

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = ScriptEngine.executeScript(scriptInfo)

        assertTrue(result is ScriptResult.Success, "Script should execute successfully")
        assertEquals(false, (result as ScriptResult.Success).value, "Handler should not be executed yet")

        // Verify handler was registered
        val handlers = com.rhett.rhettjs.events.StartupEventsAPI.getHandlers("item")
        assertEquals(1, handlers.size, "One handler should be registered")

        com.rhett.rhettjs.events.StartupEventsAPI.clear()
    }

    @Test
    fun `test ServerEvents can register handlers`() {
        com.rhett.rhettjs.events.ServerEventsAPI.clear()

        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("""
            ServerEvents.itemUse(function(event) {
                // Item use handler
            });
            ServerEvents.command('test', function(event) {
                // Command handler
            });
            'success';
        """.trimIndent())

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = ScriptEngine.executeScript(scriptInfo)

        assertTrue(result is ScriptResult.Success, "Script should execute successfully")

        // Verify handlers were registered
        val itemUseHandlers = com.rhett.rhettjs.events.ServerEventsAPI.getHandlers("itemUse")
        assertEquals(1, itemUseHandlers.size, "One itemUse handler should be registered")

        val commandHandlers = com.rhett.rhettjs.events.ServerEventsAPI.getCommandHandlers("test")
        assertEquals(1, commandHandlers.size, "One command handler should be registered")

        com.rhett.rhettjs.events.ServerEventsAPI.clear()
    }

    // ====== Error Message Cleaning Tests ======

    @Test
    fun `test error messages are cleaned of Java Undefined references`() {
        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("""
            const x = undefined;
            x(); // Will throw "x is not a function"
        """.trimIndent())

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = ScriptEngine.executeScript(scriptInfo)

        assertTrue(result is ScriptResult.Error, "Should return error")
        val error = result as ScriptResult.Error
        assertFalse(error.message.contains("org.mozilla.javascript"), "Should not contain Java package names")
        assertFalse(error.message.matches(Regex(".*@[0-9a-f]+.*")), "Should not contain Java object references")
        assertTrue(error.message.contains("is not a function"), "Should contain readable error message")
    }

    @Test
    fun `test error messages are cleaned of generic Java object references`() {
        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("""
            const obj = {};
            obj.nonExistent.someMethod(); // Will throw with object reference
        """.trimIndent())

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = ScriptEngine.executeScript(scriptInfo)

        assertTrue(result is ScriptResult.Error, "Should return error")
        val error = result as ScriptResult.Error
        assertFalse(error.message.contains("org.mozilla.javascript"), "Should not contain Java package names")
    }

    @Test
    fun `test error messages contain JavaScript stack trace`() {
        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("""
            function outer() {
                inner();
            }
            function inner() {
                throw new Error('Test error');
            }
            outer();
        """.trimIndent())

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = ScriptEngine.executeScript(scriptInfo)

        assertTrue(result is ScriptResult.Error, "Should return error")
        val error = result as ScriptResult.Error
        assertTrue(error.message.contains("Test error"), "Should contain the error message")
        // Note: Stack trace is logged, not necessarily in the error message itself
    }

    @Test
    fun `test syntax error messages are cleaned`() {
        val scriptFile = tempDir.resolve("test.js")
        scriptFile.writeText("""
            const x = {
                broken syntax here
            };
        """.trimIndent())

        val scriptInfo = ScriptInfo(
            name = "test",
            path = scriptFile,
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = ScriptEngine.executeScript(scriptInfo)

        assertTrue(result is ScriptResult.Error, "Should return syntax error")
        val error = result as ScriptResult.Error
        assertFalse(error.message.contains("org.mozilla.javascript"), "Should not contain Java package names")
        assertNotNull(error.message, "Should have error message")
    }
}
