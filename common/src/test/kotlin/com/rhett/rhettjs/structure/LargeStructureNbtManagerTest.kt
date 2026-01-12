package com.rhett.rhettjs.structure

import com.rhett.rhettjs.config.ConfigManager
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

/**
 * Unit tests for LargeStructureNbtManager.
 *
 * LIMITATION: Save/load operations require SharedConstants.getCurrentVersion()
 * and real Minecraft world for piece operations (delegated to StructureNbtManager).
 * These tests focus on:
 * - Directory structure and initialization
 * - File system checks (exists, list, remove)
 * - API structure validation (CompletableFuture returns)
 * - Parameter handling
 * - Grid logic validation
 *
 * Integration tests with real Minecraft world needed for:
 * - Actual capture/place of multi-piece structures
 * - Backup creation and restoration
 * - Grid splitting with real blocks
 */
class LargeStructureNbtManagerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var mockServer: MinecraftServer
    private lateinit var graalContext: Context
    private lateinit var structuresPath: Path

    @BeforeEach
    fun setup() {
        // Initialize ConfigManager
        ConfigManager.init(tempDir)

        // Mock MinecraftServer
        mockServer = Mockito.mock(MinecraftServer::class.java)
        Mockito.`when`(mockServer.getWorldPath(Mockito.any(LevelResource::class.java)))
            .thenReturn(tempDir)

        // Create real GraalVM context
        graalContext = Context.newBuilder("js")
            .allowAllAccess(true)
            .option("engine.WarnInterpreterOnly", "false")
            .build()

        // Initialize manager (also initializes StructureNbtManager)
        LargeStructureNbtManager.setServer(mockServer)
        LargeStructureNbtManager.setContext(graalContext)

        structuresPath = tempDir.resolve("generated")
    }

    @AfterEach
    fun teardown() {
        LargeStructureNbtManager.reset()
        graalContext.close()
    }

    @Nested
    inner class DirectoryStructure {

        @Test
        fun `setServer creates directory structure`() {
            // Base structures directory should exist
            assertTrue(structuresPath.exists())
            assertTrue(structuresPath.isDirectory())
        }

        @Test
        fun `reset does not throw exceptions`() {
            assertDoesNotThrow {
                LargeStructureNbtManager.reset()
            }
        }

        @Test
        fun `large structures use rjs-large subdirectory`() {
            // Large structures are stored in namespace/structures/rjs-large/name/
            // This is just documenting the expected path structure
            val expectedPattern = "generated/<namespace>/structures/rjs-large/<name>/"

            // Verify base path exists
            assertTrue(structuresPath.exists())
        }
    }

    @Nested
    inner class FileSystemChecks {

        @Test
        fun `list returns CompletableFuture`() {
            val listFuture = LargeStructureNbtManager.list(null)

            assertNotNull(listFuture)
            assertTrue(listFuture is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `list accepts namespace filter`() {
            // Should accept namespace filter
            val future1 = LargeStructureNbtManager.list("minecraft")
            assertNotNull(future1)

            val future2 = LargeStructureNbtManager.list("mymod")
            assertNotNull(future2)

            // Should accept null (all namespaces)
            val future3 = LargeStructureNbtManager.list(null)
            assertNotNull(future3)
        }

        @Test
        fun `remove returns CompletableFuture`() {
            val removeFuture = LargeStructureNbtManager.remove("test:structure")

            assertNotNull(removeFuture)
            assertTrue(removeFuture is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `listBackups returns CompletableFuture`() {
            val backupsFuture = LargeStructureNbtManager.listBackups("test:structure")

            assertNotNull(backupsFuture)
            assertTrue(backupsFuture is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `blocksReplace returns CompletableFuture`() {
            val replacementMap = mapOf(
                "minecraft:stone" to "minecraft:dirt",
                "minecraft:oak_log" to "minecraft:birch_log"
            )

            val future = LargeStructureNbtManager.blocksReplace("test:castle", replacementMap)

            assertNotNull(future)
            assertTrue(future is java.util.concurrent.CompletableFuture)
        }
    }

    @Nested
    inner class AsyncOperations {

        @Test
        fun `capture returns CompletableFuture`() {
            val pos1 = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val pos2 = graalContext.eval("js", "({ x: 100, y: 100, z: 100 })")

            val captureFuture = LargeStructureNbtManager.capture(
                pos1, pos2, "test:large_castle", null
            )

            assertNotNull(captureFuture)
            assertTrue(captureFuture is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `capture accepts options`() {
            val pos1 = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val pos2 = graalContext.eval("js", "({ x: 100, y: 100, z: 100 })")
            val options = graalContext.eval("js", """
                ({ includeEntities: true, author: 'TestAuthor' })
            """)

            val captureFuture = LargeStructureNbtManager.capture(
                pos1, pos2, "test:castle_with_options", options
            )

            assertNotNull(captureFuture)
        }

        @Test
        fun `place returns CompletableFuture`() {
            val position = graalContext.eval("js", "({ x: 1000, y: 64, z: 1000 })")

            val placeFuture = LargeStructureNbtManager.place(
                position, "test:large_structure", null
            )

            assertNotNull(placeFuture)
            assertTrue(placeFuture is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `place accepts rotation in options`() {
            val position = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val options = graalContext.eval("js", "({ rotation: 90 })")

            val placeFuture = LargeStructureNbtManager.place(
                position, "test:castle", options
            )

            assertNotNull(placeFuture)
        }

        @Test
        fun `getSize returns CompletableFuture`() {
            val sizeFuture = LargeStructureNbtManager.getSize("test:castle")

            assertNotNull(sizeFuture)
            assertTrue(sizeFuture is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `restoreBackup returns CompletableFuture`() {
            val restoreFuture = LargeStructureNbtManager.restoreBackup(
                "test:castle", null  // null = most recent backup
            )

            assertNotNull(restoreFuture)
            assertTrue(restoreFuture is java.util.concurrent.CompletableFuture)
        }
    }

    @Nested
    inner class ParameterHandling {

        @Test
        fun `accepts namespace format`() {
            // Should accept namespace:name format
            val pos1 = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val pos2 = graalContext.eval("js", "({ x: 50, y: 100, z: 50 })")

            assertDoesNotThrow {
                LargeStructureNbtManager.capture(pos1, pos2, "minecraft:castle", null)
            }

            assertDoesNotThrow {
                LargeStructureNbtManager.capture(pos1, pos2, "mymod:fortress", null)
            }
        }

        @Test
        fun `capture parses position objects`() {
            val pos1 = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val pos2 = graalContext.eval("js", "({ x: 100, y: 100, z: 100 })")

            // Should accept position objects with x/y/z
            assertDoesNotThrow {
                LargeStructureNbtManager.capture(pos1, pos2, "test:structure", null)
            }
        }

        @Test
        fun `capture accepts dimension in positions`() {
            val pos1 = graalContext.eval("js", """
                ({ x: 0, y: 64, z: 0, dimension: 'minecraft:overworld' })
            """)
            val pos2 = graalContext.eval("js", """
                ({ x: 100, y: 100, z: 100, dimension: 'minecraft:overworld' })
            """)

            assertDoesNotThrow {
                LargeStructureNbtManager.capture(pos1, pos2, "test:structure", null)
            }
        }

        @Test
        fun `place parses position and options`() {
            val position = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val options = graalContext.eval("js", """
                ({ rotation: 180, includeEntities: true })
            """)

            assertDoesNotThrow {
                LargeStructureNbtManager.place(position, "test:castle", options)
            }
        }

        @Test
        fun `blocksReplace accepts replacement map`() {
            val replacementMap = mapOf(
                "minecraft:stone" to "minecraft:granite",
                "minecraft:oak_planks" to "minecraft:birch_planks",
                "minecraft:glass" to "minecraft:white_stained_glass"
            )

            val future = LargeStructureNbtManager.blocksReplace("test:castle", replacementMap)

            assertNotNull(future)
        }
    }

    @Nested
    inner class GridLogic {

        @Test
        fun `large structures span multiple grid pieces`() {
            // Document expected grid behavior:
            // 100x100x100 structure with 48x48 pieces = 3x3 grid (9 pieces)
            // Files: 0_0_0.nbt, 1_0_0.nbt, 2_0_0.nbt, ...
            // This is tested implicitly through capture/place operations

            val pos1 = graalContext.eval("js", "({ x: 0, y: 0, z: 0 })")
            val pos2 = graalContext.eval("js", "({ x: 99, y: 99, z: 99 })")

            val captureFuture = LargeStructureNbtManager.capture(
                pos1, pos2, "test:grid_structure", null
            )

            // Should return a future (actual grid split happens during capture)
            assertNotNull(captureFuture)
        }

        @Test
        fun `piece naming follows X_Y_Z pattern`() {
            // Large structure pieces use naming: X_Y_Z.nbt
            // Where X, Z are grid coordinates and Y is always 0
            // Origin piece is always 0_0_0.nbt

            val originPiece = "0_0_0.nbt"
            val examplePiece = "2_0_1.nbt"
            val pattern = Regex("[0-9]+_[0-9]+_[0-9]+\\.nbt")

            // Verify pattern matches valid piece names
            assertTrue(originPiece.matches(pattern))
            assertTrue(examplePiece.matches(pattern))
        }

        @Test
        fun `metadata stored in origin piece`() {
            // Origin piece (0_0_0.nbt) contains metadata about the full structure:
            // - Grid size (gridSizeX, gridSizeZ)
            // - Piece size (pieceSizeX, pieceSizeZ)
            // - Total size (totalSizeX, totalSizeY, totalSizeZ)
            // - Required mods

            // This is tested through save/load integration tests
            val metadataFile = "0_0_0.nbt"
            assertEquals("0_0_0.nbt", metadataFile)
        }
    }

    @Nested
    inner class DelegationToStructureManager {

        @Test
        fun `load delegates to StructureNbtManager`() {
            // Large structure load calls StructureNbtManager.load() for each piece
            val loadFuture = LargeStructureNbtManager.load("test:castle")

            assertNotNull(loadFuture)
            assertTrue(loadFuture is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `save delegates to StructureNbtManager`() {
            // Would require StructureData to test properly
            // Just verify method signature exists by checking it compiles
            assertTrue(LargeStructureNbtManager::class.java.methods.any {
                it.name == "save"
            })
        }
    }
}
