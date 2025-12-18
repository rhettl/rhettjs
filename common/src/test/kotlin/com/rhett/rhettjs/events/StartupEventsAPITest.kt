package com.rhett.rhettjs.events

import com.rhett.rhettjs.config.ConfigManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.mozilla.javascript.Context
import java.nio.file.Files
import java.nio.file.Path

/**
 * TDD tests for StartupEventsAPI.
 * Tests event registration during mod initialization (startup/ scripts).
 */
class StartupEventsAPITest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        val configDir = tempDir.resolve("config")
        Files.createDirectories(configDir)
        ConfigManager.init(configDir)

        // Clear any previous state
        StartupEventsAPI.clear()
    }

    @Test
    fun `test registry method exists and is callable`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            // Inject StartupEvents API
            scope.put("StartupEvents", scope, Context.javaToJS(StartupEventsAPI, scope))

            // Should be able to call registry method
            assertDoesNotThrow {
                cx.evaluateString(
                    scope,
                    "StartupEvents.registry('item', function(event) {});",
                    "test",
                    1,
                    null
                )
            }
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test registry registers item handler`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            scope.put("StartupEvents", scope, Context.javaToJS(StartupEventsAPI, scope))

            cx.evaluateString(
                scope,
                """
                StartupEvents.registry('item', function(event) {
                    // Handler function
                });
                """.trimIndent(),
                "test",
                1,
                null
            )

            // Should have registered one handler
            val handlers = StartupEventsAPI.getHandlers("item")
            assertEquals(1, handlers.size, "Should register one item handler")
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test registry registers block handler`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            scope.put("StartupEvents", scope, Context.javaToJS(StartupEventsAPI, scope))

            cx.evaluateString(
                scope,
                """
                StartupEvents.registry('block', function(event) {
                    // Handler function
                });
                """.trimIndent(),
                "test",
                1,
                null
            )

            val handlers = StartupEventsAPI.getHandlers("block")
            assertEquals(1, handlers.size, "Should register one block handler")
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test multiple handlers for same registry type`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            scope.put("StartupEvents", scope, Context.javaToJS(StartupEventsAPI, scope))

            cx.evaluateString(
                scope,
                """
                StartupEvents.registry('item', function(event) { /* handler 1 */ });
                StartupEvents.registry('item', function(event) { /* handler 2 */ });
                StartupEvents.registry('item', function(event) { /* handler 3 */ });
                """.trimIndent(),
                "test",
                1,
                null
            )

            val handlers = StartupEventsAPI.getHandlers("item")
            assertEquals(3, handlers.size, "Should register three item handlers")
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test handlers for different registry types are separate`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            scope.put("StartupEvents", scope, Context.javaToJS(StartupEventsAPI, scope))

            cx.evaluateString(
                scope,
                """
                StartupEvents.registry('item', function(event) { /* item handler */ });
                StartupEvents.registry('block', function(event) { /* block handler */ });
                """.trimIndent(),
                "test",
                1,
                null
            )

            assertEquals(1, StartupEventsAPI.getHandlers("item").size)
            assertEquals(1, StartupEventsAPI.getHandlers("block").size)
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test getHandlers returns empty list for unregistered type`() {
        val handlers = StartupEventsAPI.getHandlers("nonexistent")
        assertEquals(0, handlers.size, "Should return empty list for unregistered type")
    }

    @Test
    fun `test clear removes all handlers`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            scope.put("StartupEvents", scope, Context.javaToJS(StartupEventsAPI, scope))

            cx.evaluateString(
                scope,
                """
                StartupEvents.registry('item', function(event) {});
                StartupEvents.registry('block', function(event) {});
                """.trimIndent(),
                "test",
                1,
                null
            )

            assertEquals(1, StartupEventsAPI.getHandlers("item").size)
            assertEquals(1, StartupEventsAPI.getHandlers("block").size)

            StartupEventsAPI.clear()

            assertEquals(0, StartupEventsAPI.getHandlers("item").size)
            assertEquals(0, StartupEventsAPI.getHandlers("block").size)
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test executeRegistrations calls handlers`() {
        var handlerCalled = false

        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            // Add a flag that handlers can set
            scope.put("testFlag", scope, false)
            scope.put("StartupEvents", scope, Context.javaToJS(StartupEventsAPI, scope))

            cx.evaluateString(
                scope,
                """
                StartupEvents.registry('item', function(event) {
                    testFlag = true;
                });
                """.trimIndent(),
                "test",
                1,
                null
            )

            // Execute the handlers
            StartupEventsAPI.executeRegistrations("item", scope)

            // Check that handler was called
            val flagValue = scope.get("testFlag", scope)
            assertTrue(flagValue as Boolean, "Handler should have set testFlag to true")
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test executeRegistrations with multiple handlers calls all`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            // Track how many times handlers are called
            scope.put("callCount", scope, 0)
            scope.put("StartupEvents", scope, Context.javaToJS(StartupEventsAPI, scope))

            cx.evaluateString(
                scope,
                """
                StartupEvents.registry('item', function(event) { callCount++; });
                StartupEvents.registry('item', function(event) { callCount++; });
                StartupEvents.registry('item', function(event) { callCount++; });
                """.trimIndent(),
                "test",
                1,
                null
            )

            StartupEventsAPI.executeRegistrations("item", scope)

            val count = scope.get("callCount", scope)
            assertEquals(3, Context.toNumber(count).toInt(), "All three handlers should have been called")
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test handler receives event object`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            scope.put("receivedEvent", scope, false)
            scope.put("StartupEvents", scope, Context.javaToJS(StartupEventsAPI, scope))

            cx.evaluateString(
                scope,
                """
                StartupEvents.registry('item', function(event) {
                    receivedEvent = (typeof event !== 'undefined');
                });
                """.trimIndent(),
                "test",
                1,
                null
            )

            StartupEventsAPI.executeRegistrations("item", scope)

            val received = scope.get("receivedEvent", scope)
            assertTrue(received as Boolean, "Handler should receive event parameter")
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test handler error does not stop other handlers`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            scope.put("handler1Called", scope, false)
            scope.put("handler3Called", scope, false)
            scope.put("StartupEvents", scope, Context.javaToJS(StartupEventsAPI, scope))

            cx.evaluateString(
                scope,
                """
                StartupEvents.registry('item', function(event) {
                    handler1Called = true;
                });
                StartupEvents.registry('item', function(event) {
                    throw new Error("Handler 2 error");
                });
                StartupEvents.registry('item', function(event) {
                    handler3Called = true;
                });
                """.trimIndent(),
                "test",
                1,
                null
            )

            // Should not throw, should continue with other handlers
            assertDoesNotThrow {
                StartupEventsAPI.executeRegistrations("item", scope)
            }

            assertTrue(scope.get("handler1Called", scope) as Boolean, "Handler 1 should have been called")
            assertTrue(scope.get("handler3Called", scope) as Boolean, "Handler 3 should have been called despite handler 2 error")
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test executeRegistrations for unregistered type does nothing`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            // Should not throw
            assertDoesNotThrow {
                StartupEventsAPI.executeRegistrations("nonexistent", scope)
            }
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test getSupportedTypes returns expected types`() {
        val types = StartupEventsAPI.getSupportedTypes()

        assertTrue(types.contains("item"), "Should support 'item' registry")
        assertTrue(types.contains("block"), "Should support 'block' registry")
    }

    @Test
    fun `test registry with unsupported type is rejected`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            scope.put("StartupEvents", scope, Context.javaToJS(StartupEventsAPI, scope))

            // Should throw or log error for unsupported type
            val result = assertThrows(Exception::class.java) {
                cx.evaluateString(
                    scope,
                    """
                    StartupEvents.registry('unsupported_type', function(event) {});
                    """.trimIndent(),
                    "test",
                    1,
                    null
                )
            }

            assertNotNull(result)
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test handler can be complex IIFE`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            scope.put("complexResult", scope, 0)
            scope.put("StartupEvents", scope, Context.javaToJS(StartupEventsAPI, scope))

            cx.evaluateString(
                scope,
                """
                StartupEvents.registry('item', (function() {
                    // IIFE pattern
                    const multiplier = 10;

                    return function(event) {
                        complexResult = 42 * multiplier;
                    };
                })());
                """.trimIndent(),
                "test",
                1,
                null
            )

            StartupEventsAPI.executeRegistrations("item", scope)

            val result = scope.get("complexResult", scope)
            assertEquals(420, Context.toNumber(result).toInt(), "Complex IIFE handler should work")
        } finally {
            Context.exit()
        }
    }
}
