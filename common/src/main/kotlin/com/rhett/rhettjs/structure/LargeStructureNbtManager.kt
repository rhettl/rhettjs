package com.rhett.rhettjs.structure

import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.structure.large.LargeStructureNbtBackup
import com.rhett.rhettjs.structure.large.LargeStructureNbtCapture
import com.rhett.rhettjs.structure.large.LargeStructureNbtCore
import com.rhett.rhettjs.structure.models.StructureData
import com.rhett.rhettjs.structure.utils.StructureNbtUtils
import net.minecraft.server.MinecraftServer
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.exists

/**
 * Manager for LargeStructureNbt API operations with JavaScript.
 *
 * Handles multi-chunk .nbt structures stored in rjs-large/ directories.
 * Uses StructureNbtManager as lower-level API for individual piece operations.
 * For single .nbt files, see StructureNbtManager.
 * For worldgen structures (villages, bastions), see WorldgenStructureManager.
 *
 * Large structures are split into chunks (e.g., 48x48x48) and stored as:
 *   world/generated/<namespace>/structures/rjs-large/<name>/<X>_<Y>_<Z>.nbt
 *
 * This is the anti-corruption layer between JavaScript and large structure files.
 * It ensures:
 * - All file I/O is async (return CompletableFuture)
 * - No Minecraft types exposed to JavaScript
 * - Pure JS objects using adapters
 * - Uses StructureNbtManager for piece-level operations
 *
 * Design principles:
 * - Async for I/O: All operations return CompletableFuture
 * - Anti-corruption: Convert all MC types to JS via models
 * - Delegation: Uses StructureNbtManager.capture/place/load for pieces
 * - Namespace format: "[namespace:]name" (defaults to "minecraft:")
 *
 * Architecture:
 * This manager delegates to specialized operation classes:
 * - LargeStructureNbtCore: Core operations (list, remove, getSize, blocksReplace)
 * - LargeStructureNbtCapture: Capture and place with grid logic
 * - LargeStructureNbtBackup: Directory-based backup and restore
 */
object LargeStructureNbtManager {

    @Volatile
    internal var server: MinecraftServer? = null

    @Volatile
    private var graalContext: Context? = null

    @Volatile
    internal var structuresPath: Path? = null

    // Operation delegates
    private var coreOps: LargeStructureNbtCore? = null
    private var captureOps: LargeStructureNbtCapture? = null
    private var backupOps: LargeStructureNbtBackup? = null

    /**
     * Set the Minecraft server reference.
     * Called during server startup.
     */
    fun setServer(minecraftServer: MinecraftServer) {
        server = minecraftServer
        // Structures stored in world/generated/ (namespaced)
        structuresPath = minecraftServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("generated")

        val backupsPath = minecraftServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("backups").resolve(StructureNbtUtils.GENERATED_STRUCTURES_DIR)

        // Ensure base directories exist
        structuresPath?.let { path ->
            if (!path.exists()) {
                Files.createDirectories(path)
                ConfigManager.debug("[LargeStructureNbtManager] Created generated directory: $path")
            }
        }
        if (!backupsPath.exists()) {
            Files.createDirectories(backupsPath)
            ConfigManager.debug("[LargeStructureNbtManager] Created backups directory: $backupsPath")
        }

        // Initialize operation delegates
        backupOps = LargeStructureNbtBackup(structuresPath!!)
        coreOps = LargeStructureNbtCore(minecraftServer, structuresPath!!, backupOps!!)
        captureOps = LargeStructureNbtCapture(minecraftServer, structuresPath!!, graalContext, backupOps!!)

        ConfigManager.debug("[LargeStructureNbtManager] Minecraft server reference set")
    }

    /**
     * Set the GraalVM context reference.
     * Called when GraalEngine initializes the context.
     */
    fun setContext(context: Context) {
        graalContext = context
        // Update captureOps with new context if already initialized
        if (server != null && structuresPath != null && backupOps != null) {
            captureOps = LargeStructureNbtCapture(server!!, structuresPath!!, graalContext, backupOps!!)
        }
        ConfigManager.debug("[LargeStructureNbtManager] GraalVM context reference set")
    }

    // ============ Delegation Methods (for StructureNbtManager compatibility) ============

    /**
     * Load a structure from resource system (async).
     * Delegates to StructureNbtManager for consistency.
     */
    fun load(nameWithNamespace: String): CompletableFuture<StructureData> {
        return StructureNbtManager.load(nameWithNamespace)
    }

    /**
     * Save a structure to file (async) with automatic backup.
     * Delegates to StructureNbtManager for consistency.
     */
    fun save(nameWithNamespace: String, data: StructureData, skipBackup: Boolean = false): CompletableFuture<Void> {
        return StructureNbtManager.save(nameWithNamespace, data, skipBackup)
    }

    // ============ Core Operations ============

    /**
     * List large structures from resource system (async).
     * Returns Promise<string[]> where each name is in format "namespace:name".
     */
    fun list(namespaceFilter: String?): CompletableFuture<List<String>> {
        return coreOps?.list(namespaceFilter)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    /**
     * Remove a large structure (all pieces) (async).
     * Returns Promise<boolean> (true if removed, false if didn't exist).
     */
    fun remove(nameWithNamespace: String): CompletableFuture<Boolean> {
        val (namespace, baseName) = StructureNbtUtils.parseStructureName(nameWithNamespace)
        return coreOps?.remove(namespace, baseName)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    /**
     * Get the size of a large structure (async).
     * Returns Promise<{x, y, z}>.
     */
    fun getSize(nameWithNamespace: String): CompletableFuture<Map<String, Int>> {
        val (namespace, baseName) = StructureNbtUtils.parseStructureName(nameWithNamespace)
        return coreOps?.getSize(namespace, baseName)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    /**
     * Replace blocks in all pieces of a large structure (async).
     * Returns Promise<void> after saving all modified pieces (with backup).
     */
    fun blocksReplace(nameWithNamespace: String, replacementMap: Map<String, String>): CompletableFuture<Void> {
        val (namespace, baseName) = StructureNbtUtils.parseStructureName(nameWithNamespace)
        return coreOps?.blocksReplace(namespace, baseName, replacementMap)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    // ============ Capture/Place Operations ============

    /**
     * Capture a large region split into multiple piece files (async).
     * Returns Promise<void> after saving all piece files.
     */
    fun capture(pos1: Value, pos2: Value, nameWithNamespace: String, options: Value?): CompletableFuture<Void> {
        val (namespace, baseName) = StructureNbtUtils.parseStructureName(nameWithNamespace)
        return captureOps?.capture(pos1, pos2, namespace, baseName, options)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    /**
     * Place a large structure (async).
     * Returns Promise<void> after placing all pieces.
     */
    fun place(position: Value, nameWithNamespace: String, options: Value?): CompletableFuture<Void> {
        val (namespace, baseName) = StructureNbtUtils.parseStructureName(nameWithNamespace)
        return captureOps?.place(position, namespace, baseName, options)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    // ============ Backup Operations ============

    /**
     * List available directory backups for a large structure (async).
     * Returns Promise<List<String>> of backup timestamps.
     */
    fun listBackups(nameWithNamespace: String): CompletableFuture<List<String>> {
        val (namespace, baseName) = StructureNbtUtils.parseStructureName(nameWithNamespace)
        return backupOps?.listBackups(namespace, baseName)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    /**
     * Restore large structure from directory backup (async).
     * Returns Promise<void> after restoration.
     */
    fun restoreBackup(nameWithNamespace: String, timestamp: String?): CompletableFuture<Void> {
        val (namespace, baseName) = StructureNbtUtils.parseStructureName(nameWithNamespace)
        return backupOps?.restoreBackup(namespace, baseName, timestamp)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    /**
     * Clear all state (called on reset/reload).
     * Clears context reference (context will be recreated).
     */
    fun reset() {
        graalContext = null
        ConfigManager.debug("[LargeStructureNbtManager] Reset complete")
    }
}
