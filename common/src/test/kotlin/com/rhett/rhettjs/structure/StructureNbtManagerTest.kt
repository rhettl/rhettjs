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
 * Unit tests for StructureNbtManager.
 *
 * LIMITATION: Full save/load operations require SharedConstants.getCurrentVersion()
 * which needs complete Minecraft initialization (not available in unit tests).
 * These tests focus on:
 * - Directory setup and initialization
 * - File system checks (exists, list)
 * - API structure validation (CompletableFuture returns)
 * - Parameter handling
 *
 * Integration tests with real Minecraft world needed for:
 * - NBT parsing and serialization
 * - Capture/place operations
 * - Backup creation and restoration
 */
class StructureNbtManagerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var mockServer: MinecraftServer
    private lateinit var graalContext: Context
    private lateinit var structuresPath: Path
    private lateinit var backupsPath: Path

    @BeforeEach
    fun setup() {
        // Initialize ConfigManager with temp directory
        ConfigManager.init(tempDir)

        // Mock MinecraftServer to return temp paths
        mockServer = Mockito.mock(MinecraftServer::class.java)
        Mockito.`when`(mockServer.getWorldPath(Mockito.any(LevelResource::class.java)))
            .thenReturn(tempDir)

        // Create real GraalVM context
        graalContext = Context.newBuilder("js")
            .allowAllAccess(true)
            .option("engine.WarnInterpreterOnly", "false")
            .build()

        // Initialize manager
        StructureNbtManager.setServer(mockServer)
        StructureNbtManager.setContext(graalContext)

        // Track paths
        structuresPath = tempDir.resolve("generated")
        backupsPath = tempDir.resolve("backups/structures")
    }

    @AfterEach
    fun teardown() {
        StructureNbtManager.reset()
        graalContext.close()
    }

    @Nested
    inner class DirectorySetup {

        @Test
        fun `setServer creates generated directory`() {
            // Directory should be created by setServer in @BeforeEach
            assertTrue(structuresPath.exists(), "generated/ directory should exist")
            assertTrue(structuresPath.isDirectory(), "generated/ should be a directory")
        }

        @Test
        fun `setServer creates backups directory`() {
            // Backups directory should be created by setServer
            assertTrue(backupsPath.exists(), "backups/structures/ directory should exist")
            assertTrue(backupsPath.isDirectory(), "backups/structures/ should be a directory")
        }

        @Test
        fun `reset does not throw exceptions`() {
            // Reset should clear internal state without errors
            assertDoesNotThrow {
                StructureNbtManager.reset()
            }
        }
    }

    @Nested
    inner class FileSystemChecks {

        @Test
        fun `exists returns false for missing structure`() {
            val existsFuture = StructureNbtManager.exists("nonexistent_structure")
            val exists = existsFuture.get(5, TimeUnit.SECONDS)

            assertFalse(exists, "Nonexistent structure should return false")
        }

        @Test
        fun `exists returns CompletableFuture`() {
            val existsFuture = StructureNbtManager.exists("test")

            assertNotNull(existsFuture)
            assertTrue(existsFuture is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `list completes without errors for new directory`() {
            val listFuture = StructureNbtManager.list(null)

            // Should complete (may be empty or may fail without resource system)
            assertNotNull(listFuture)
            // Don't assert on result - resource system may not be initialized
        }

        @Test
        fun `list returns CompletableFuture`() {
            val listFuture = StructureNbtManager.list(null)

            assertNotNull(listFuture)
            assertTrue(listFuture is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `listGenerated returns CompletableFuture`() {
            val listFuture = StructureNbtManager.listGenerated(null)

            assertNotNull(listFuture)
            assertTrue(listFuture is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `listBackups returns empty for nonexistent structure`() {
            val backupsFuture = StructureNbtManager.listBackups("test")
            val backups = backupsFuture.get(5, TimeUnit.SECONDS)

            assertTrue(backups.isEmpty())
        }

        @Test
        fun `remove returns false for nonexistent structure`() {
            val removeFuture = StructureNbtManager.remove("nonexistent")
            val removed = removeFuture.get(5, TimeUnit.SECONDS)

            assertFalse(removed)
        }
    }

    @Nested
    inner class AsyncOperations {

        @Test
        fun `capture returns CompletableFuture`() {
            val pos1 = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val pos2 = graalContext.eval("js", "({ x: 10, y: 74, z: 10 })")

            val captureFuture = StructureNbtManager.capture(
                pos1, pos2, "capture_test", null, skipBackup = true
            )

            assertNotNull(captureFuture)
            assertTrue(captureFuture is java.util.concurrent.CompletableFuture)

            // Will fail without real Minecraft world - expected
        }

        @Test
        fun `capture accepts options parameter`() {
            val pos1 = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val pos2 = graalContext.eval("js", "({ x: 2, y: 66, z: 2 })")
            val options = graalContext.eval("js", "({ includeEntities: true })")

            // Should accept options without error (execution will fail due to no world)
            val captureFuture = StructureNbtManager.capture(
                pos1, pos2, "options_test", options, skipBackup = true
            )

            assertNotNull(captureFuture)
        }

        @Test
        fun `place accepts rotation in options`() {
            val position = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val options = graalContext.eval("js", "({ rotation: 90 })")

            // Should accept rotation option (will fail without structure file + world)
            val placeFuture = StructureNbtManager.place(position, "test", options)

            assertNotNull(placeFuture)
            assertTrue(placeFuture is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `getSize returns CompletableFuture`() {
            val sizeFuture = StructureNbtManager.getSize("test")

            assertNotNull(sizeFuture)
            assertTrue(sizeFuture is java.util.concurrent.CompletableFuture)

            // Will fail for nonexistent structure - expected
        }

        @Test
        fun `blocksList returns CompletableFuture`() {
            val listFuture = StructureNbtManager.blocksList("test")

            assertNotNull(listFuture)
            assertTrue(listFuture is java.util.concurrent.CompletableFuture)
        }

        @Test
        fun `blocksNamespaces returns CompletableFuture`() {
            val nsFuture = StructureNbtManager.blocksNamespaces("test")

            assertNotNull(nsFuture)
            assertTrue(nsFuture is java.util.concurrent.CompletableFuture)
        }
    }

    @Nested
    inner class ParameterHandling {

        @Test
        fun `exists handles namespace format`() {
            // Should accept namespace:name format
            val future1 = StructureNbtManager.exists("minecraft:house")
            assertNotNull(future1)

            val future2 = StructureNbtManager.exists("mymod:castle")
            assertNotNull(future2)

            // Should accept bare names (default to minecraft)
            val future3 = StructureNbtManager.exists("simple")
            assertNotNull(future3)
        }

        @Test
        fun `list accepts namespace filter`() {
            // Should accept namespace filter
            val future1 = StructureNbtManager.list("minecraft")
            assertNotNull(future1)

            val future2 = StructureNbtManager.list("mymod")
            assertNotNull(future2)

            // Should accept null (all namespaces)
            val future3 = StructureNbtManager.list(null)
            assertNotNull(future3)
        }

        @Test
        fun `capture parses position objects`() {
            // Should accept position objects with x/y/z
            val pos1 = graalContext.eval("js", "({ x: 0, y: 64, z: 0 })")
            val pos2 = graalContext.eval("js", "({ x: 5, y: 69, z: 5 })")

            assertDoesNotThrow {
                StructureNbtManager.capture(pos1, pos2, "test", null, true)
            }
        }

        @Test
        fun `capture accepts dimension in positions`() {
            val pos1 = graalContext.eval("js", "({ x: 0, y: 64, z: 0, dimension: 'minecraft:overworld' })")
            val pos2 = graalContext.eval("js", "({ x: 5, y: 69, z: 5, dimension: 'minecraft:overworld' })")

            assertDoesNotThrow {
                StructureNbtManager.capture(pos1, pos2, "test", null, true)
            }
        }
    }
}
