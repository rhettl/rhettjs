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
 * TDD tests for ServerEventsAPI.
 * Tests runtime event registration (server/ and scripts/ scripts).
 */
class ServerEventsAPITest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        val configDir = tempDir.resolve("config")
        Files.createDirectories(configDir)
        ConfigManager.init(configDir)

        // Clear any previous state
        ServerEventsAPI.clear()
    }

    @Test
    fun `test itemUse method exists and is callable`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            assertDoesNotThrow {
                cx.evaluateString(
                    scope,
                    "ServerEvents.itemUse(function(event) {});",
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
    fun `test command method exists and is callable`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            assertDoesNotThrow {
                cx.evaluateString(
                    scope,
                    "ServerEvents.command('test', function(event) {});",
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
    fun `test itemUse registers handler`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            cx.evaluateString(
                scope,
                "ServerEvents.itemUse(function(event) {});",
                "test",
                1,
                null
            )

            val handlers = ServerEventsAPI.getHandlers("itemUse")
            assertEquals(1, handlers.size, "Should register one itemUse handler")
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test multiple itemUse handlers`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            cx.evaluateString(
                scope,
                """
                ServerEvents.itemUse(function(event) { /* handler 1 */ });
                ServerEvents.itemUse(function(event) { /* handler 2 */ });
                ServerEvents.itemUse(function(event) { /* handler 3 */ });
                """.trimIndent(),
                "test",
                1,
                null
            )

            val handlers = ServerEventsAPI.getHandlers("itemUse")
            assertEquals(3, handlers.size, "Should register three itemUse handlers")
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test command registers handler with command name`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            cx.evaluateString(
                scope,
                "ServerEvents.command('spawn', function(event) {});",
                "test",
                1,
                null
            )

            val handlers = ServerEventsAPI.getCommandHandlers("spawn")
            assertEquals(1, handlers.size, "Should register one handler for 'spawn' command")
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test different commands have separate handlers`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            cx.evaluateString(
                scope,
                """
                ServerEvents.command('spawn', function(event) {});
                ServerEvents.command('teleport', function(event) {});
                """.trimIndent(),
                "test",
                1,
                null
            )

            assertEquals(1, ServerEventsAPI.getCommandHandlers("spawn").size)
            assertEquals(1, ServerEventsAPI.getCommandHandlers("teleport").size)
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test triggerItemUse executes handlers`() {
        var handlerCalled = false

        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            scope.put("testFlag", scope, false)
            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            cx.evaluateString(
                scope,
                "ServerEvents.itemUse(function(event) { testFlag = true; });",
                "test",
                1,
                null
            )

            ServerEventsAPI.triggerItemUse(scope)

            val flagValue = scope.get("testFlag", scope)
            assertTrue(flagValue as Boolean, "Handler should have been called")
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test triggerCommand executes handlers for specific command`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            scope.put("spawnCalled", scope, false)
            scope.put("teleportCalled", scope, false)
            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            cx.evaluateString(
                scope,
                """
                ServerEvents.command('spawn', function(event) { spawnCalled = true; });
                ServerEvents.command('teleport', function(event) { teleportCalled = true; });
                """.trimIndent(),
                "test",
                1,
                null
            )

            ServerEventsAPI.triggerCommand("spawn", scope)

            assertTrue(scope.get("spawnCalled", scope) as Boolean, "spawn handler should have been called")
            assertFalse(scope.get("teleportCalled", scope) as Boolean, "teleport handler should NOT have been called")
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
            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            cx.evaluateString(
                scope,
                "ServerEvents.itemUse(function(event) { receivedEvent = (typeof event !== 'undefined'); });",
                "test",
                1,
                null
            )

            ServerEventsAPI.triggerItemUse(scope)

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
            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            cx.evaluateString(
                scope,
                """
                ServerEvents.itemUse(function(event) { handler1Called = true; });
                ServerEvents.itemUse(function(event) { throw new Error("Handler 2 error"); });
                ServerEvents.itemUse(function(event) { handler3Called = true; });
                """.trimIndent(),
                "test",
                1,
                null
            )

            assertDoesNotThrow {
                ServerEventsAPI.triggerItemUse(scope)
            }

            assertTrue(scope.get("handler1Called", scope) as Boolean)
            assertTrue(scope.get("handler3Called", scope) as Boolean)
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test clear removes all handlers`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            cx.evaluateString(
                scope,
                """
                ServerEvents.itemUse(function(event) {});
                ServerEvents.command('test', function(event) {});
                """.trimIndent(),
                "test",
                1,
                null
            )

            assertEquals(1, ServerEventsAPI.getHandlers("itemUse").size)
            assertEquals(1, ServerEventsAPI.getCommandHandlers("test").size)

            ServerEventsAPI.clear()

            assertEquals(0, ServerEventsAPI.getHandlers("itemUse").size)
            assertEquals(0, ServerEventsAPI.getCommandHandlers("test").size)
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test getHandlers returns empty for unregistered event`() {
        val handlers = ServerEventsAPI.getHandlers("nonexistent")
        assertEquals(0, handlers.size)
    }

    @Test
    fun `test getCommandHandlers returns empty for unregistered command`() {
        val handlers = ServerEventsAPI.getCommandHandlers("nonexistent")
        assertEquals(0, handlers.size)
    }

    @Test
    fun `test trigger with no handlers does nothing`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            assertDoesNotThrow {
                ServerEventsAPI.triggerItemUse(scope)
                ServerEventsAPI.triggerCommand("nonexistent", scope)
            }
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test multiple handlers are all executed`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            scope.put("callCount", scope, 0)
            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            cx.evaluateString(
                scope,
                """
                ServerEvents.itemUse(function(event) { callCount++; });
                ServerEvents.itemUse(function(event) { callCount++; });
                ServerEvents.itemUse(function(event) { callCount++; });
                """.trimIndent(),
                "test",
                1,
                null
            )

            ServerEventsAPI.triggerItemUse(scope)

            val count = scope.get("callCount", scope)
            assertEquals(3, Context.toNumber(count).toInt(), "All three handlers should have been called")
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
            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            cx.evaluateString(
                scope,
                """
                ServerEvents.itemUse((function() {
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

            ServerEventsAPI.triggerItemUse(scope)

            val result = scope.get("complexResult", scope)
            assertEquals(420, Context.toNumber(result).toInt())
        } finally {
            Context.exit()
        }
    }
}
