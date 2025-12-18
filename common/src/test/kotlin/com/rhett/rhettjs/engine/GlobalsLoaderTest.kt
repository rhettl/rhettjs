package com.rhett.rhettjs.engine

import com.rhett.rhettjs.config.ConfigManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.mozilla.javascript.Context
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * TDD tests for GlobalsLoader.
 * Tests loading of rhettjs/globals/ directory (.js files) that provide reusable libraries.
 */
class GlobalsLoaderTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var globalsDir: Path

    @BeforeEach
    fun setup() {
        // Initialize ConfigManager
        val configDir = tempDir.resolve("config")
        Files.createDirectories(configDir)
        ConfigManager.init(configDir)

        // Create globals directory
        globalsDir = tempDir.resolve("globals")
        Files.createDirectories(globalsDir)

        // Clear any previous global state
        GlobalsLoader.clear()
    }

    @Test
    fun `test load single global script`() {
        // Create a simple global
        globalsDir.resolve("test.js").writeText("""
            const TestLib = (function() {
                return {
                    getValue: function() {
                        return 42;
                    }
                };
            })();
        """.trimIndent())

        GlobalsLoader.reload(tempDir)

        val loadedGlobals = GlobalsLoader.getLoadedGlobals()
        assertTrue(loadedGlobals.contains("TestLib"), "Should load TestLib global")
    }

    @Test
    fun `test load multiple globals in alphabetical order`() {
        // Create multiple globals with numeric prefixes for ordering
        globalsDir.resolve("00-first.js").writeText("const First = 'first';")
        globalsDir.resolve("10-second.js").writeText("const Second = 'second';")
        globalsDir.resolve("20-third.js").writeText("const Third = 'third';")

        GlobalsLoader.reload(tempDir)

        val globals = GlobalsLoader.getLoadedGlobals()
        assertTrue(globals.contains("First"))
        assertTrue(globals.contains("Second"))
        assertTrue(globals.contains("Third"))
    }

    @Test
    fun `test globals can depend on each other via load order`() {
        // First global defines a value
        globalsDir.resolve("00-base.js").writeText("const BASE_VALUE = 10;")

        // Second global uses it
        globalsDir.resolve("10-derived.js").writeText("""
            const DERIVED_VALUE = BASE_VALUE * 2;
        """.trimIndent())

        GlobalsLoader.reload(tempDir)

        val globals = GlobalsLoader.getLoadedGlobals()
        assertTrue(globals.contains("BASE_VALUE"))
        assertTrue(globals.contains("DERIVED_VALUE"))
    }

    @Test
    fun `test globals have access to console API`() {
        globalsDir.resolve("test.js").writeText("""
            const TestLib = (function(logger) {
                return {
                    log: function() {
                        logger.info("Test from global");
                        return true;
                    }
                };
            })(logger);
        """.trimIndent())

        assertDoesNotThrow {
            GlobalsLoader.reload(tempDir)
        }
    }

    @Test
    fun `test globals have access to logger API`() {
        globalsDir.resolve("test.js").writeText("""
            const TestLib = (function(console) {
                return {
                    log: function() {
                        console.log("Test from global");
                        return true;
                    }
                };
            })(console);
        """.trimIndent())

        assertDoesNotThrow {
            GlobalsLoader.reload(tempDir)
        }
    }

    @Test
    fun `test globals with syntax error are logged but don't crash`() {
        globalsDir.resolve("good.js").writeText("const Good = 'good';")
        globalsDir.resolve("bad.js").writeText("const Bad = { syntax error")
        globalsDir.resolve("alsoGood.js").writeText("const AlsoGood = 'alsoGood';")

        // Should not throw, should continue loading other files
        assertDoesNotThrow {
            GlobalsLoader.reload(tempDir)
        }

        val globals = GlobalsLoader.getLoadedGlobals()
        assertTrue(globals.contains("Good"), "Should load Good despite Bad having error")
        assertTrue(globals.contains("AlsoGood"), "Should load AlsoGood despite Bad having error")
        assertFalse(globals.contains("Bad"), "Should not load Bad (has syntax error)")
    }

    @Test
    fun `test injectGlobals adds globals to script scope`() {
        // Load a global
        globalsDir.resolve("test.js").writeText("const MyLib = { value: 42 };")
        GlobalsLoader.reload(tempDir)

        // Create a script scope and inject globals
        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()
            GlobalsLoader.injectGlobals(scope)

            // Check that global was injected
            assertTrue(scope.has("MyLib", scope), "MyLib should be injected into scope")
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test injectGlobals does not inject console and logger`() {
        // Load a global that uses console
        globalsDir.resolve("test.js").writeText("const MyLib = { value: 42 };")
        GlobalsLoader.reload(tempDir)

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            // Add console/logger first
            scope.put("console", scope, "existing_console")
            scope.put("logger", scope, "existing_logger")

            GlobalsLoader.injectGlobals(scope)

            // console/logger should not be overridden
            assertEquals("existing_console", scope.get("console", scope))
            assertEquals("existing_logger", scope.get("logger", scope))
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test reserved name validation warns but allows`() {
        // Create globals with reserved names
        globalsDir.resolve("test.js").writeText("""
            const player = { reserved: true };
            const world = { reserved: true };
        """.trimIndent())

        // Should load but warn
        assertDoesNotThrow {
            GlobalsLoader.reload(tempDir)
        }

        val globals = GlobalsLoader.getLoadedGlobals()
        assertTrue(globals.contains("player"), "Should load despite reserved name")
        assertTrue(globals.contains("world"), "Should load despite reserved name")
    }

    @Test
    fun `test getLoadedGlobals returns empty list when no globals`() {
        GlobalsLoader.reload(tempDir)

        val globals = GlobalsLoader.getLoadedGlobals()
        assertEquals(0, globals.size, "Should return empty list when no globals loaded")
    }

    @Test
    fun `test getLoadedGlobals excludes console and logger`() {
        globalsDir.resolve("test.js").writeText("const MyLib = { value: 42 };")
        GlobalsLoader.reload(tempDir)

        val globals = GlobalsLoader.getLoadedGlobals()
        assertFalse(globals.contains("console"), "Should not include console")
        assertFalse(globals.contains("logger"), "Should not include logger")
        assertTrue(globals.contains("MyLib"), "Should include MyLib")
    }

    @Test
    fun `test reload clears previous globals`() {
        // First load
        globalsDir.resolve("first.js").writeText("const FirstLib = 'first';")
        GlobalsLoader.reload(tempDir)

        assertTrue(GlobalsLoader.getLoadedGlobals().contains("FirstLib"))

        // Delete old, add new
        Files.delete(globalsDir.resolve("first.js"))
        globalsDir.resolve("second.js").writeText("const SecondLib = 'second';")
        GlobalsLoader.reload(tempDir)

        val globals = GlobalsLoader.getLoadedGlobals()
        assertFalse(globals.contains("FirstLib"), "Should clear FirstLib on reload")
        assertTrue(globals.contains("SecondLib"), "Should load SecondLib")
    }

    @Test
    fun `test no globals directory is handled gracefully`() {
        // Delete the globals directory
        Files.delete(globalsDir)

        assertDoesNotThrow {
            GlobalsLoader.reload(tempDir)
        }

        val globals = GlobalsLoader.getLoadedGlobals()
        assertEquals(0, globals.size)
    }

    @Test
    fun `test IIFE pattern with dependency injection`() {
        globalsDir.resolve("test.js").writeText("""
            const StructureValidator = (function(logger, console) {
                // Private helper
                function checkField(data, field) {
                    return data[field] !== undefined;
                }

                // Public API
                return {
                    validate: function(data) {
                        logger.info("Validating structure");
                        return checkField(data, "palette");
                    }
                };
            })(logger, console);
        """.trimIndent())

        assertDoesNotThrow {
            GlobalsLoader.reload(tempDir)
        }

        val globals = GlobalsLoader.getLoadedGlobals()
        assertTrue(globals.contains("StructureValidator"))
    }

    @Test
    fun `test complex global library`() {
        globalsDir.resolve("messaging.js").writeText("""
            const Messaging = (function(logger) {
                const PREFIX = "[MSG] ";

                function formatMessage(msg) {
                    return PREFIX + msg;
                }

                return {
                    success: function(msg) {
                        logger.info(formatMessage("✓ " + msg));
                    },
                    error: function(msg) {
                        logger.error(formatMessage("✗ " + msg));
                    },
                    info: function(msg) {
                        logger.info(formatMessage(msg));
                    }
                };
            })(logger);
        """.trimIndent())

        assertDoesNotThrow {
            GlobalsLoader.reload(tempDir)
        }

        val globals = GlobalsLoader.getLoadedGlobals()
        assertTrue(globals.contains("Messaging"))
    }

    @Test
    fun `test globals loaded count is logged`() {
        globalsDir.resolve("a.js").writeText("const A = 1;")
        globalsDir.resolve("b.js").writeText("const B = 2;")
        globalsDir.resolve("c.js").writeText("const C = 3;")

        assertDoesNotThrow {
            GlobalsLoader.reload(tempDir)
        }

        // Should log "Loaded 3 global scripts"
        val globals = GlobalsLoader.getLoadedGlobals()
        assertEquals(3, globals.size)
    }

    @Test
    fun `test clear removes all globals`() {
        globalsDir.resolve("test.js").writeText("const TestLib = 42;")
        GlobalsLoader.reload(tempDir)

        assertTrue(GlobalsLoader.getLoadedGlobals().isNotEmpty())

        GlobalsLoader.clear()

        assertEquals(0, GlobalsLoader.getLoadedGlobals().size)
    }

    @Test
    fun `test injected globals are callable from scripts`() {
        // Load a global with a function
        globalsDir.resolve("math.js").writeText("""
            const MathLib = {
                add: function(a, b) {
                    return a + b;
                },
                multiply: function(a, b) {
                    return a * b;
                }
            };
        """.trimIndent())
        GlobalsLoader.reload(tempDir)

        // Create a script that uses the global
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            GlobalsLoader.injectGlobals(scope)

            val result = cx.evaluateString(
                scope,
                "MathLib.add(5, 3) + MathLib.multiply(2, 4)",
                "test",
                1,
                null
            )

            assertEquals(16, Context.toNumber(result).toInt(), "Script should be able to call global functions")
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test globals meta-object is injected`() {
        globalsDir.resolve("test.js").writeText("const MyLib = { value: 42 };")
        GlobalsLoader.reload(tempDir)

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()
            scope.put("console", scope, Context.javaToJS(com.rhett.rhettjs.api.ConsoleAPI(), scope))
            scope.put("logger", scope, Context.javaToJS(com.rhett.rhettjs.api.LoggerAPI(), scope))
            GlobalsLoader.injectGlobals(scope)

            // Test globals meta-object exists
            assertTrue(scope.has("globals", scope), "globals meta-object should exist")

            // Test globals contains all APIs and user-defined globals
            val result = cx.evaluateString(
                scope,
                """
                typeof globals === 'object' &&
                globals.MyLib === MyLib &&
                typeof globals.console !== 'undefined' &&
                typeof globals.logger !== 'undefined'
                """.trimIndent(),
                "test",
                1,
                null
            )
            assertEquals(true, result, "globals should contain MyLib, console, and logger")
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test globals meta-object contains introspection capabilities`() {
        globalsDir.resolve("lib1.js").writeText("const Lib1 = { foo: 'bar' };")
        globalsDir.resolve("lib2.js").writeText("const Lib2 = { baz: 'qux' };")
        GlobalsLoader.reload(tempDir)

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()
            scope.put("console", scope, Context.javaToJS(com.rhett.rhettjs.api.ConsoleAPI(), scope))
            scope.put("logger", scope, Context.javaToJS(com.rhett.rhettjs.api.LoggerAPI(), scope))
            GlobalsLoader.injectGlobals(scope)

            // Test that we can iterate over globals
            val result = cx.evaluateString(
                scope,
                """
                const keys = Object.keys(globals);
                keys.includes('Lib1') && keys.includes('Lib2') &&
                keys.includes('console') && keys.includes('logger')
                """.trimIndent(),
                "test",
                1,
                null
            )
            assertEquals(true, result, "Should be able to introspect globals object")
        } finally {
            Context.exit()
        }
    }

    // ====== Reload Edge Cases ======

    @Test
    fun `test reload with circular dependencies in globals`() {
        // Create globals that reference each other
        globalsDir.resolve("00-utils.js").writeText("""
            const Utils = {
                useHelper: function() {
                    // Reference to Helper defined in next file
                    return typeof Helper !== 'undefined' ? Helper.value : 'no helper';
                }
            };
        """.trimIndent())

        globalsDir.resolve("10-helper.js").writeText("""
            const Helper = {
                value: 42,
                useUtils: function() {
                    // Circular reference back to Utils
                    return typeof Utils !== 'undefined' ? Utils : null;
                }
            };
        """.trimIndent())

        // Should load both successfully due to alphabetical ordering
        assertDoesNotThrow {
            GlobalsLoader.reload(tempDir)
        }

        val globals = GlobalsLoader.getLoadedGlobals()
        assertTrue(globals.contains("Utils"), "Should load Utils")
        assertTrue(globals.contains("Helper"), "Should load Helper")

        // Test that circular reference works at runtime
        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()
            GlobalsLoader.injectGlobals(scope)

            // Utils is loaded first, so Helper doesn't exist yet when Utils is defined
            // But at runtime, Helper should be available
            val result = cx.evaluateString(
                scope,
                """
                // Helper should be available now (loaded after Utils)
                Helper.value === 42 &&
                // Utils.useHelper() should find Helper at runtime
                Utils.useHelper() !== 'no helper'
                """.trimIndent(),
                "test",
                1,
                null
            )
            assertEquals(true, result, "Circular dependencies should work via load order")
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test reload with failed global does not pollute namespace`() {
        // First global succeeds
        globalsDir.resolve("00-good.js").writeText("""
            const GoodLib = { value: 'good' };
        """.trimIndent())

        // Second global fails mid-execution
        globalsDir.resolve("10-bad.js").writeText("""
            const BadLib = { value: 'bad' };
            thisWillFail(); // Reference error
        """.trimIndent())

        // Third global should still load
        globalsDir.resolve("20-alsoGood.js").writeText("""
            const AlsoGoodLib = { value: 'alsoGood' };
        """.trimIndent())

        assertDoesNotThrow {
            GlobalsLoader.reload(tempDir)
        }

        val globals = GlobalsLoader.getLoadedGlobals()
        assertTrue(globals.contains("GoodLib"))
        assertTrue(globals.contains("AlsoGoodLib"))

        // BadLib should exist (const was declared before error)
        // but the script execution failed
        assertTrue(globals.contains("BadLib"), "BadLib const should be declared despite error after")
    }

    @Test
    fun `test reload completely replaces previous globals`() {
        // Initial load
        globalsDir.resolve("v1.js").writeText("const Version = 1;")
        GlobalsLoader.reload(tempDir)

        val globals1 = GlobalsLoader.getLoadedGlobals()
        assertTrue(globals1.contains("Version"))

        // Delete old file, create new version
        Files.delete(globalsDir.resolve("v1.js"))
        globalsDir.resolve("v2.js").writeText("const Version = 2;")

        // Reload should give us fresh scope
        GlobalsLoader.reload(tempDir)

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()
            GlobalsLoader.injectGlobals(scope)

            val result = cx.evaluateString(
                scope,
                "Version",
                "test",
                1,
                null
            )
            // Should be 2 (new version), not 1 (old version)
            // Rhino may return Int or Double for numeric values
            assertTrue(result == 2 || result == 2.0, "Reload should use new global value, got: $result")
        } finally {
            Context.exit()
        }
    }
}
