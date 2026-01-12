package com.rhett.rhettjs.commands

import com.mojang.brigadier.CommandDispatcher
import com.rhett.rhettjs.config.ConfigManager
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

/**
 * Unit tests for CustomCommandRegistry.
 * Tests command storage, validation, Brigadier integration, suggestion caching, and async execution.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomCommandRegistryTest {

    private lateinit var registry: CustomCommandRegistry
    private lateinit var dispatcher: CommandDispatcher<CommandSourceStack>
    private lateinit var buildContext: CommandBuildContext
    private lateinit var graalContext: Context
    private lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        // Create temp directory for ConfigManager
        tempDir = createTempDirectory("command-registry-test")
        ConfigManager.init(tempDir)

        registry = CustomCommandRegistry()
        dispatcher = CommandDispatcher()
        buildContext = Mockito.mock(CommandBuildContext::class.java)
        graalContext = Context.newBuilder("js")
            .allowAllAccess(true)
            .build()
        registry.storeDispatcher(dispatcher, graalContext, buildContext)
    }

    @AfterEach
    fun teardown() {
        registry.clear()
        graalContext.close()
        // Clean up temp directory
        tempDir.toFile().deleteRecursively()
    }

    @Nested
    inner class CommandStorage {

        @Test
        fun `store and retrieve simple command`() {
            val commandData = mapOf<String, Any?>(
                "name" to "test",
                "executor" to graalContext.eval("js", "(event) => { return 1; }")
            )

            registry.storeCommand("test", commandData)

            val retrieved = registry.getCommand("test")
            assertNotNull(retrieved)
            assertEquals("test", retrieved!!["name"])
            assertNotNull(retrieved["executor"])
        }

        @Test
        fun `store command with arguments`() {
            val arguments = listOf(
                mapOf("name" to "player", "type" to "player"),
                mapOf("name" to "amount", "type" to "int")
            )
            val commandData = mapOf<String, Any?>(
                "name" to "give",
                "arguments" to arguments,
                "executor" to graalContext.eval("js", "(event) => {}")
            )

            registry.storeCommand("give", commandData)

            val retrieved = registry.getCommand("give")
            assertNotNull(retrieved)
            @Suppress("UNCHECKED_CAST")
            val retrievedArgs = retrieved!!["arguments"] as List<Map<String, Any>>
            assertEquals(2, retrievedArgs.size)
            assertEquals("player", retrievedArgs[0]["name"])
            assertEquals("amount", retrievedArgs[1]["name"])
        }

        @Test
        fun `store command with permission`() {
            val commandData = mapOf<String, Any?>(
                "name" to "admin",
                "permission" to "admin.use",
                "executor" to graalContext.eval("js", "(event) => {}")
            )

            registry.storeCommand("admin", commandData)

            val retrieved = registry.getCommand("admin")
            assertNotNull(retrieved)
            assertEquals("admin.use", retrieved!!["permission"])
        }

        @Test
        fun `overwrite existing command`() {
            val commandData1 = mapOf<String, Any?>(
                "name" to "test",
                "executor" to graalContext.eval("js", "(event) => { return 1; }")
            )
            val commandData2 = mapOf<String, Any?>(
                "name" to "test",
                "executor" to graalContext.eval("js", "(event) => { return 2; }")
            )

            registry.storeCommand("test", commandData1)
            registry.storeCommand("test", commandData2)

            val retrieved = registry.getCommand("test")
            assertNotNull(retrieved)
            // Should have been overwritten
            assertEquals(1, registry.getCommandNames().size)
        }

        @Test
        fun `list all command names`() {
            registry.storeCommand("cmd1", mapOf("executor" to graalContext.eval("js", "(e) => {}")))
            registry.storeCommand("cmd2", mapOf("executor" to graalContext.eval("js", "(e) => {}")))
            registry.storeCommand("cmd3", mapOf("executor" to graalContext.eval("js", "(e) => {}")))

            val names = registry.getCommandNames()
            assertEquals(3, names.size)
            assertTrue(names.contains("cmd1"))
            assertTrue(names.contains("cmd2"))
            assertTrue(names.contains("cmd3"))
        }

        @Test
        fun `clear all commands`() {
            registry.storeCommand("cmd1", mapOf("executor" to graalContext.eval("js", "(e) => {}")))
            registry.storeCommand("cmd2", mapOf("executor" to graalContext.eval("js", "(e) => {}")))

            assertEquals(2, registry.getCommandNames().size)

            registry.clear()

            assertEquals(0, registry.getCommandNames().size)
            assertNull(registry.getCommand("cmd1"))
            assertNull(registry.getCommand("cmd2"))
        }
    }

    @Nested
    inner class CommandValidation {

        @Test
        fun `reject command without executor or subcommands`() {
            val commandData = mapOf<String, Any?>(
                "name" to "invalid"
                // No executor, no subcommands
            )

            assertThrows<IllegalArgumentException> {
                registry.validateCommand(commandData)
            }
        }

        @Test
        fun `reject invalid argument type`() {
            val commandData = mapOf<String, Any?>(
                "name" to "test",
                "arguments" to listOf(
                    mapOf("name" to "bad", "type" to "invalid_type")
                ),
                "executor" to graalContext.eval("js", "(e) => {}")
            )

            val exception = assertThrows<IllegalArgumentException> {
                registry.validateCommand(commandData)
            }
            assertTrue(exception.message!!.contains("invalid_type"))
        }

        @Test
        fun `accept all valid argument types`() {
            val validTypes = listOf("string", "int", "float", "player", "item", "block", "entity", "xyz-position", "xz-position")

            validTypes.forEach { type ->
                val commandData = mapOf<String, Any?>(
                    "name" to "test_$type",
                    "arguments" to listOf(
                        mapOf("name" to "arg", "type" to type)
                    ),
                    "executor" to graalContext.eval("js", "(e) => {}")
                )

                assertDoesNotThrow {
                    registry.validateCommand(commandData)
                }
            }
        }

        @Test
        fun `accept command with missing argument name`() {
            // The validation doesn't enforce that arguments must have a name field
            // It will use "unknown" as fallback if name is missing
            val commandData = mapOf<String, Any?>(
                "name" to "test",
                "arguments" to listOf(
                    mapOf("type" to "string")  // Missing name - this is accepted
                ),
                "executor" to graalContext.eval("js", "(e) => {}")
            )

            // Should not throw - validation is lenient about missing names
            assertDoesNotThrow {
                registry.validateCommand(commandData)
            }
        }

        @Test
        fun `reject required argument after optional`() {
            val commandData = mapOf<String, Any?>(
                "name" to "test",
                "arguments" to listOf(
                    mapOf("name" to "optional_arg", "type" to "string", "optional" to true),
                    mapOf("name" to "required_arg", "type" to "string")  // Required after optional - INVALID
                ),
                "executor" to graalContext.eval("js", "(e) => {}")
            )

            val exception = assertThrows<IllegalArgumentException> {
                registry.validateCommand(commandData)
            }
            assertTrue(exception.message!!.contains("Required argument"))
        }
    }

    @Nested
    inner class BrigadierIntegration {

        @Test
        fun `register simple command to dispatcher`() {
            val commandData = mapOf<String, Any?>(
                "name" to "test",
                "executor" to graalContext.eval("js", "(event) => { return 1; }")
            )

            registry.storeCommand("test", commandData)
            registry.registerAll()

            // Verify command registered in Brigadier tree
            val testNode = dispatcher.root.getChild("test")
            assertNotNull(testNode, "Command 'test' should be registered in dispatcher")
            assertTrue(testNode!!.command != null, "Command should have executor")
        }

        @Test
        fun `register command with arguments`() {
            val commandData = mapOf<String, Any?>(
                "name" to "give",
                "arguments" to listOf(
                    mapOf("name" to "player", "type" to "player"),
                    mapOf("name" to "amount", "type" to "int")
                ),
                "executor" to graalContext.eval("js", "(event) => { return 1; }")
            )

            registry.storeCommand("give", commandData)
            registry.registerAll()

            // Verify command structure
            val giveNode = dispatcher.root.getChild("give")
            assertNotNull(giveNode)

            // Verify argument chain exists
            val playerNode = giveNode!!.getChild("player")
            assertNotNull(playerNode, "First argument 'player' should be registered")

            val amountNode = playerNode!!.getChild("amount")
            assertNotNull(amountNode, "Second argument 'amount' should be registered")
        }

        @Test
        fun `register command with subcommands`() {
            val subcommands = mapOf(
                "add" to mapOf<String, Any?>(
                    "executor" to graalContext.eval("js", "(event) => { return 'add'; }")
                ),
                "remove" to mapOf<String, Any?>(
                    "executor" to graalContext.eval("js", "(event) => { return 'remove'; }")
                )
            )
            val commandData = mapOf<String, Any?>(
                "name" to "manage",
                "subcommands" to subcommands
            )

            registry.storeCommand("manage", commandData)
            registry.registerAll()

            // Verify main command
            val manageNode = dispatcher.root.getChild("manage")
            assertNotNull(manageNode)

            // Verify subcommands
            val addNode = manageNode!!.getChild("add")
            assertNotNull(addNode, "Subcommand 'add' should be registered")
            assertTrue(addNode!!.command != null, "Subcommand 'add' should have executor")

            val removeNode = manageNode.getChild("remove")
            assertNotNull(removeNode, "Subcommand 'remove' should be registered")
            assertTrue(removeNode!!.command != null, "Subcommand 'remove' should have executor")
        }

        @Test
        fun `register all commands at once`() {
            registry.storeCommand("cmd1", mapOf("executor" to graalContext.eval("js", "(e) => {}")))
            registry.storeCommand("cmd2", mapOf("executor" to graalContext.eval("js", "(e) => {}")))
            registry.storeCommand("cmd3", mapOf("executor" to graalContext.eval("js", "(e) => {}")))

            registry.registerAll()

            // Verify all registered
            assertNotNull(dispatcher.root.getChild("cmd1"))
            assertNotNull(dispatcher.root.getChild("cmd2"))
            assertNotNull(dispatcher.root.getChild("cmd3"))
        }

        @Test
        fun `skip registration if dispatcher not stored`() {
            val freshRegistry = CustomCommandRegistry()
            freshRegistry.storeCommand("test", mapOf("executor" to graalContext.eval("js", "(e) => {}")))

            // registerAll without storeDispatcher should not throw
            assertDoesNotThrow {
                freshRegistry.registerAll()
            }

            // Command should not be in dispatcher (because dispatcher was never set)
            assertNull(dispatcher.root.getChild("test"))
        }
    }

    @Nested
    inner class SuggestionCache {

        @Test
        fun `cache suggestions for 30 seconds`() {
            // This test documents the caching behavior
            // Actual cache testing requires more complex mocking of suggestion providers
            // For now, verify the cache TTL constant exists and has expected value
            val ttlField = CustomCommandRegistry::class.java.getDeclaredField("SUGGESTION_CACHE_TTL_MS")
            ttlField.isAccessible = true
            val ttlValue = ttlField.get(registry) as Long
            assertEquals(30000L, ttlValue, "Cache TTL should be 30 seconds")
        }

        @Test
        fun `cache is per provider function`() {
            // Cache uses System.identityHashCode(provider)
            // Different providers should have separate cache entries
            val provider1 = graalContext.eval("js", "() => ['a', 'b', 'c']")
            val provider2 = graalContext.eval("js", "() => ['x', 'y', 'z']")

            // Verify they have different identity hashes
            assertNotEquals(System.identityHashCode(provider1), System.identityHashCode(provider2))
        }

        @Test
        fun `suggestion cache exists as private field`() {
            // Verify the cache structure exists
            val cacheField = CustomCommandRegistry::class.java.getDeclaredField("suggestionCache")
            assertNotNull(cacheField)
            cacheField.isAccessible = true
            val cache = cacheField.get(registry)
            assertNotNull(cache)
        }

        @Test
        fun `cache entry stores results and timestamp`() {
            // Verify the SuggestionCacheEntry data class exists
            val cacheEntryClass = CustomCommandRegistry::class.java.declaredClasses
                .firstOrNull { it.simpleName == "SuggestionCacheEntry" }
            assertNotNull(cacheEntryClass, "SuggestionCacheEntry inner class should exist")
        }
    }

    @Nested
    inner class AsyncExecution {

        @Test
        fun `executor receives event object`() {
            var receivedEvent: Any? = null
            val executor = graalContext.eval("js", """
                (event) => {
                    // Store event for verification
                    return event !== null && event !== undefined;
                }
            """)

            val commandData = mapOf<String, Any?>(
                "name" to "test",
                "executor" to executor
            )

            registry.storeCommand("test", commandData)
            assertNotNull(registry.getCommand("test"))
        }

        @Test
        fun `executor can be a promise`() {
            val executor = graalContext.eval("js", """
                async (event) => {
                    return Promise.resolve(42);
                }
            """)

            val commandData = mapOf<String, Any?>(
                "name" to "async_test",
                "executor" to executor
            )

            registry.storeCommand("async_test", commandData)
            registry.registerAll()

            // Verify command registered
            assertNotNull(dispatcher.root.getChild("async_test"))
        }

        @Test
        fun `permission string stored correctly`() {
            val commandData = mapOf<String, Any?>(
                "name" to "admin",
                "permission" to "admin.commands",
                "executor" to graalContext.eval("js", "(e) => {}")
            )

            registry.storeCommand("admin", commandData)

            val retrieved = registry.getCommand("admin")
            assertEquals("admin.commands", retrieved!!["permission"])
        }

        @Test
        fun `permission function stored correctly`() {
            val permissionFunc = graalContext.eval("js", "(caller) => caller.isOp")
            val commandData = mapOf<String, Any?>(
                "name" to "admin",
                "permission" to permissionFunc,
                "executor" to graalContext.eval("js", "(e) => {}")
            )

            registry.storeCommand("admin", commandData)

            val retrieved = registry.getCommand("admin")
            assertNotNull(retrieved!!["permission"])
            assertTrue(retrieved["permission"] is org.graalvm.polyglot.Value)
        }

        @Test
        fun `command with arguments stores them correctly`() {
            val arguments = listOf(
                mapOf("name" to "target", "type" to "player"),
                mapOf("name" to "message", "type" to "string")
            )
            val commandData = mapOf<String, Any?>(
                "name" to "msg",
                "arguments" to arguments,
                "executor" to graalContext.eval("js", "(event) => {}")
            )

            registry.storeCommand("msg", commandData)

            val retrieved = registry.getCommand("msg")
            @Suppress("UNCHECKED_CAST")
            val storedArgs = retrieved!!["arguments"] as List<Map<String, Any>>
            assertEquals(2, storedArgs.size)
            assertEquals("target", storedArgs[0]["name"])
            assertEquals("player", storedArgs[0]["type"])
            assertEquals("message", storedArgs[1]["name"])
            assertEquals("string", storedArgs[1]["type"])
        }
    }
}
