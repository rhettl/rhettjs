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
    fun `test basicCommand method exists and is callable`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            assertDoesNotThrow {
                cx.evaluateString(
                    scope,
                    "ServerEvents.basicCommand('test', function(event) {});",
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
    fun `test basicCommand registers handler with command name`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            cx.evaluateString(
                scope,
                "ServerEvents.basicCommand('spawn', function(event) {});",
                "test",
                1,
                null
            )

            val handlers = ServerEventsAPI.getCommandHandlers()
            assertEquals(1, handlers.size, "Should register one command")
            assertTrue(handlers.containsKey("spawn"), "Should register command 'spawn'")
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
                ServerEvents.basicCommand('spawn', function(event) {});
                ServerEvents.basicCommand('teleport', function(event) {});
                """.trimIndent(),
                "test",
                1,
                null
            )

            val handlers = ServerEventsAPI.getCommandHandlers()
            assertEquals(2, handlers.size)
            assertTrue(handlers.containsKey("spawn"))
            assertTrue(handlers.containsKey("teleport"))
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
                ServerEvents.basicCommand('test', function(event) {});
                """.trimIndent(),
                "test",
                1,
                null
            )

            assertEquals(1, ServerEventsAPI.getHandlers("itemUse").size)
            val commandHandlers = ServerEventsAPI.getCommandHandlers()
            assertEquals(1, commandHandlers.size)
            assertTrue(commandHandlers.containsKey("test"))

            ServerEventsAPI.clear()

            assertEquals(0, ServerEventsAPI.getHandlers("itemUse").size)
            assertEquals(0, ServerEventsAPI.getCommandHandlers().size)
            assertEquals(0, ServerEventsAPI.getFullCommandHandlers().size)
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
    fun `test getCommandHandlers returns empty map when no commands registered`() {
        val handlers = ServerEventsAPI.getCommandHandlers()
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

    // ============================================================================
    // Block Events Tests
    // ============================================================================

    @Test
    fun `test blockRightClicked registration without filter`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            assertDoesNotThrow {
                cx.evaluateString(
                    scope,
                    "ServerEvents.blockRightClicked(function(event) {});",
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
    fun `test blockRightClicked registration with block filter`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            assertDoesNotThrow {
                cx.evaluateString(
                    scope,
                    "ServerEvents.blockRightClicked('minecraft:stone', function(event) {});",
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
    fun `test blockLeftClicked registration`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            assertDoesNotThrow {
                cx.evaluateString(
                    scope,
                    "ServerEvents.blockLeftClicked('minecraft:dirt', function(event) {});",
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
    fun `test blockPlaced registration`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            assertDoesNotThrow {
                cx.evaluateString(
                    scope,
                    "ServerEvents.blockPlaced('minecraft:torch', function(event) {});",
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
    fun `test blockBroken registration`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            assertDoesNotThrow {
                cx.evaluateString(
                    scope,
                    "ServerEvents.blockBroken(function(event) {});",
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
    fun `test blockRightClicked triggers handler`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            // Register handler
            cx.evaluateString(
                scope,
                "var clicked = false; ServerEvents.blockRightClicked(function(event) { clicked = true; });",
                "test",
                1,
                null
            )

            // Create event data
            val eventData = BlockEventData.Click(
                position = BlockPosition(10, 64, 20, "minecraft:overworld"),
                block = BlockData("minecraft:stone"),
                player = PlayerData("TestPlayer", "test-uuid", false),
                item = null,
                face = BlockFace.UP,
                isRightClick = true
            )

            // Trigger event
            ServerEventsAPI.triggerBlockEvent("blockRightClicked", scope, eventData)

            // Verify handler was called
            val clicked = scope.get("clicked", scope)
            assertTrue(clicked as Boolean)
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test blockRightClicked event has correct properties`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            // Register handler that captures event data
            cx.evaluateString(
                scope,
                """
                var capturedEvent = null;
                ServerEvents.blockRightClicked(function(event) {
                    capturedEvent = event;
                });
                """.trimIndent(),
                "test",
                1,
                null
            )

            // Create event data
            val eventData = BlockEventData.Click(
                position = BlockPosition(10, 64, 20, "minecraft:overworld"),
                block = BlockData("minecraft:stone"),
                player = PlayerData("TestPlayer", "test-uuid", false),
                item = ItemData("minecraft:stick", 1, null, null),
                face = BlockFace.UP,
                isRightClick = true
            )

            // Trigger event
            ServerEventsAPI.triggerBlockEvent("blockRightClicked", scope, eventData)

            // Verify event properties
            val capturedEvent = scope.get("capturedEvent", scope)
            assertNotNull(capturedEvent)

            cx.evaluateString(
                scope,
                """
                var tests = {
                    posX: capturedEvent.position.x === 10,
                    posY: capturedEvent.position.y === 64,
                    posZ: capturedEvent.position.z === 20,
                    blockId: capturedEvent.block.id === 'minecraft:stone',
                    playerName: capturedEvent.playerData.name === 'TestPlayer',
                    itemId: capturedEvent.item.id === 'minecraft:stick',
                    face: capturedEvent.face === 'UP',
                    isRightClick: capturedEvent.isRightClick === true
                };
                """.trimIndent(),
                "test",
                1,
                null
            )

            val tests = scope.get("tests", scope) as org.mozilla.javascript.Scriptable
            assertTrue(Context.toBoolean(tests.get("posX", tests)))
            assertTrue(Context.toBoolean(tests.get("posY", tests)))
            assertTrue(Context.toBoolean(tests.get("posZ", tests)))
            assertTrue(Context.toBoolean(tests.get("blockId", tests)))
            assertTrue(Context.toBoolean(tests.get("playerName", tests)))
            assertTrue(Context.toBoolean(tests.get("itemId", tests)))
            assertTrue(Context.toBoolean(tests.get("face", tests)))
            assertTrue(Context.toBoolean(tests.get("isRightClick", tests)))
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test block filter works correctly`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            // Register handlers with different filters
            cx.evaluateString(
                scope,
                """
                var stoneClicked = false;
                var dirtClicked = false;
                var anyClicked = false;
                ServerEvents.blockRightClicked('minecraft:stone', function(event) {
                    stoneClicked = true;
                });
                ServerEvents.blockRightClicked('minecraft:dirt', function(event) {
                    dirtClicked = true;
                });
                ServerEvents.blockRightClicked(function(event) {
                    anyClicked = true;
                });
                """.trimIndent(),
                "test",
                1,
                null
            )

            // Trigger stone click
            val stoneEvent = BlockEventData.Click(
                position = BlockPosition(10, 64, 20, "minecraft:overworld"),
                block = BlockData("minecraft:stone"),
                player = PlayerData("TestPlayer", "test-uuid", false),
                item = null,
                face = BlockFace.UP,
                isRightClick = true
            )

            ServerEventsAPI.triggerBlockEvent("blockRightClicked", scope, stoneEvent)

            // Only stone and any handlers should be called
            assertTrue(scope.get("stoneClicked", scope) as Boolean)
            assertFalse(scope.get("dirtClicked", scope) as Boolean)
            assertTrue(scope.get("anyClicked", scope) as Boolean)
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test blockLeftClicked triggers with correct isRightClick value`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))

            // Register handler
            cx.evaluateString(
                scope,
                "var isRight = null; ServerEvents.blockLeftClicked(function(event) { isRight = event.isRightClick; });",
                "test",
                1,
                null
            )

            // Create left-click event
            val eventData = BlockEventData.Click(
                position = BlockPosition(10, 64, 20, "minecraft:overworld"),
                block = BlockData("minecraft:stone"),
                player = PlayerData("TestPlayer", "test-uuid", false),
                item = null,
                face = BlockFace.UP,
                isRightClick = false
            )

            // Trigger event
            ServerEventsAPI.triggerBlockEvent("blockLeftClicked", scope, eventData)

            // Verify isRightClick is false
            val isRight = scope.get("isRight", scope)
            assertFalse(isRight as Boolean)
        } finally {
            Context.exit()
        }
    }
}
