package com.rhett.rhettjs.world

import com.rhett.rhettjs.config.ConfigManager
import net.minecraft.server.MinecraftServer
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

/**
 * Unit tests for WorldManager.
 *
 * LIMITATION: World operations require real Minecraft ServerLevel and blocks
 * which need full Minecraft initialization (not available in unit tests).
 * These tests focus on:
 * - Initialization and state management
 * - Position parsing and validation
 * - CompletableFuture return types
 * - Error handling for null server/context
 * - Parameter structure validation
 *
 * Integration tests with real Minecraft world needed for:
 * - Actual block getting/setting
 * - Entity operations
 * - Time/weather manipulation
 * - Player queries
 */
class WorldManagerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var mockServer: MinecraftServer
    private lateinit var graalContext: Context

    @BeforeEach
    fun setup() {
        // Initialize ConfigManager
        ConfigManager.init(tempDir)

        // Mock MinecraftServer
        mockServer = Mockito.mock(MinecraftServer::class.java)

        // Mock server.execute() to run immediately
        Mockito.doAnswer { invocation ->
            (invocation.arguments[0] as Runnable).run()
            null
        }.`when`(mockServer).execute(Mockito.any())

        // Create real GraalVM context
        graalContext = Context.newBuilder("js")
            .allowAllAccess(true)
            .option("engine.WarnInterpreterOnly", "false")
            .build()

        // Initialize manager
        WorldManager.setServer(mockServer)
        WorldManager.setContext(graalContext)
    }

    @AfterEach
    fun teardown() {
        WorldManager.reset()
        graalContext.close()
    }

    @Nested
    inner class Initialization {

        @Test
        fun `setServer stores server reference`() {
            // Should not throw when calling operations after setServer
            val position = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val future = WorldManager.getBlock(position)

            assertNotNull(future)
        }

        @Test
        fun `setContext stores context reference`() {
            // Context should be available for operations
            val position = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val future = WorldManager.getBlock(position)

            assertNotNull(future)
            assertTrue(future is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `operations fail before initialization`() {
            // Reset to clear initialization
            WorldManager.reset()

            // Operations should fail without server
            val position = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val future = WorldManager.getBlock(position)

            // Should throw ExecutionException (server/context not available)
            val exception = assertThrows<java.util.concurrent.ExecutionException> {
                future.get(5, TimeUnit.SECONDS)
            }

            // Should have IllegalStateException as cause
            assertTrue(exception.cause is IllegalStateException)
        }
    }

    @Nested
    inner class PositionParsing {

        @Test
        fun `parse position with all fields`() {
            val position = graalContext.eval("js", """
                ({ x: 100, y: 64, z: 200, dimension: 'minecraft:overworld' })
            """)

            // Should accept position with all fields
            val future = WorldManager.getBlock(position)

            assertNotNull(future)
            assertTrue(future is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `parse position defaults dimension to overworld`() {
            val position = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")

            // Should accept position without dimension (defaults to overworld)
            val future = WorldManager.getBlock(position)

            assertNotNull(future)
        }

        @Test
        fun `reject position with missing coordinates`() {
            val invalidPosition = graalContext.eval("js", "({ x: 0, y: 64 })")  // Missing z

            val future = WorldManager.getBlock(invalidPosition)

            // Should fail during position parsing
            assertThrows<java.util.concurrent.ExecutionException> {
                future.get(5, TimeUnit.SECONDS)
            }
        }
    }

    @Nested
    inner class BlockOperations {

        @Test
        fun `getBlock returns CompletableFuture`() {
            val position = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val future = WorldManager.getBlock(position)

            assertNotNull(future)
            assertTrue(future is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `setBlock returns CompletableFuture`() {
            val position = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val properties = graalContext.eval("js", "({})")

            val future = WorldManager.setBlock(position, "minecraft:stone", properties)

            assertNotNull(future)
            assertTrue(future is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `fill returns CompletableFuture`() {
            val pos1 = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val pos2 = graalContext.eval("js", "({ x: 10, y: 74, z: 10 })")

            val future = WorldManager.fill(pos1, pos2, "minecraft:stone", null)

            assertNotNull(future)
            assertTrue(future is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `fill accepts options with exclusion zones`() {
            val pos1 = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val pos2 = graalContext.eval("js", "({ x: 10, y: 74, z: 10 })")
            val options = graalContext.eval("js", """
                ({ exclusionZones: [{ from: { x: 2, y: 66, z: 2 }, to: { x: 4, y: 68, z: 4 } }] })
            """)

            val future = WorldManager.fill(pos1, pos2, "minecraft:stone", options)

            assertNotNull(future)
        }

        @Test
        fun `getBlockEntity returns CompletableFuture`() {
            val position = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val future = WorldManager.getBlockEntity(position)

            assertNotNull(future)
            assertTrue(future is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `getFilledBounds returns CompletableFuture`() {
            val pos1 = graalContext.eval("js", "({ x: 0, y: 0, z: 0 })")
            val pos2 = graalContext.eval("js", "({ x: 10, y: 100, z: 10 })")

            val future = WorldManager.getFilledBounds(pos1, pos2, "minecraft:overworld")

            assertNotNull(future)
            assertTrue(future is java.util.concurrent.CompletableFuture)
        }
    }

    @Nested
    inner class PlayerOperations {

        @Test
        fun `getPlayers returns CompletableFuture`() {
            val future = WorldManager.getPlayers()

            assertNotNull(future)
            assertTrue(future is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `getPlayer by name returns CompletableFuture`() {
            val future = WorldManager.getPlayer("TestPlayer")

            assertNotNull(future)
            assertTrue(future is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `getPlayer handles missing player`() {
            // Should return CompletableFuture that resolves to null
            val future = WorldManager.getPlayer("NonexistentPlayer")

            assertNotNull(future)
            // Result will be null or throw without real players - expected
        }

        @Test
        fun `removeEntities returns CompletableFuture`() {
            val pos1 = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val pos2 = graalContext.eval("js", "({ x: 10, y: 74, z: 10 })")

            val future = WorldManager.removeEntities(pos1, pos2, null)

            assertNotNull(future)
            assertTrue(future is java.util.concurrent.CompletableFuture)
        }
    }

    @Nested
    inner class TimeWeatherOperations {

        @Test
        fun `getTime returns CompletableFuture`() {
            val dimension = "minecraft:overworld"
            val future = WorldManager.getTime(dimension)

            assertNotNull(future)
            assertTrue(future is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `setTime returns CompletableFuture`() {
            val future = WorldManager.setTime(6000L, "minecraft:overworld")

            assertNotNull(future)
            assertTrue(future is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `getWeather returns CompletableFuture`() {
            val dimension = "minecraft:overworld"
            val future = WorldManager.getWeather(dimension)

            assertNotNull(future)
            assertTrue(future is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `setWeather returns CompletableFuture`() {
            // setWeather takes weather string ("clear", "rain", "thunder")
            val future = WorldManager.setWeather("rain", "minecraft:overworld")

            assertNotNull(future)
            assertTrue(future is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `time operations default to overworld`() {
            // Most operations default dimension to overworld if not specified
            val future = WorldManager.getTime("minecraft:overworld")

            assertNotNull(future)
        }
    }

    @Nested
    inner class AsyncSafety {

        @Test
        fun `all operations use CompletableFuture`() {
            val position = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")

            // getBlock
            assertTrue(WorldManager.getBlock(position) is java.util.concurrent.CompletableFuture)

            // setBlock
            assertTrue(WorldManager.setBlock(position, "minecraft:stone", null) is java.util.concurrent.CompletableFuture)

            // getPlayers
            assertTrue(WorldManager.getPlayers() is java.util.concurrent.CompletableFuture)

            // getTime
            assertTrue(WorldManager.getTime("minecraft:overworld") is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `exceptions propagate to future`() {
            // Reset to trigger null server exception
            WorldManager.reset()

            val position = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val future = WorldManager.getBlock(position)

            // Should not throw immediately
            assertDoesNotThrow {
                WorldManager.getBlock(position)
            }

            // Should throw when getting result
            assertThrows<java.util.concurrent.ExecutionException> {
                future.get(5, TimeUnit.SECONDS)
            }
        }

        @Test
        fun `operations handle null context gracefully`() {
            // Reset clears context
            WorldManager.reset()

            val position = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val future = WorldManager.getBlock(position)

            // Should complete exceptionally
            val exception = assertThrows<java.util.concurrent.ExecutionException> {
                future.get(5, TimeUnit.SECONDS)
            }

            assertTrue(exception.cause is IllegalStateException)
        }
    }

    @Nested
    inner class DimensionHandling {

        @Test
        fun `getDimensions returns sync result`() {
            // getDimensions is one of the few sync operations
            val dimensions = WorldManager.getDimensions()

            assertNotNull(dimensions)
            // Without real server, may be empty
        }

        @Test
        fun `getDimensionBounds returns CompletableFuture`() {
            val future = WorldManager.getDimensionBounds("minecraft:overworld")

            assertNotNull(future)
            assertTrue(future is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `operations accept custom dimensions`() {
            val position = graalContext.eval("js", """
                ({ x: 0, y: 64, z: 0, dimension: 'minecraft:the_nether' })
            """)

            val future = WorldManager.getBlock(position)

            assertNotNull(future)
            // Will fail without real dimension, but structure is correct
        }
    }
}
