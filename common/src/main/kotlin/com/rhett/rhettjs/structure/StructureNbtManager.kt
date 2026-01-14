package com.rhett.rhettjs.structure

import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.structure.models.StructureData
import com.rhett.rhettjs.structure.operations.StructureNbtBackup
import com.rhett.rhettjs.structure.operations.StructureNbtBlocks
import com.rhett.rhettjs.structure.operations.StructureNbtCapture
import com.rhett.rhettjs.structure.operations.StructureNbtCore
import com.rhett.rhettjs.structure.utils.StructureNbtUtils
import net.minecraft.server.MinecraftServer
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.exists

/**
 * Manager for Structure API operations with JavaScript.
 *
 * This is the anti-corruption layer between JavaScript and Minecraft structure files.
 * It ensures:
 * - All file I/O is async (return CompletableFuture)
 * - No Minecraft types exposed to JavaScript
 * - Pure JS objects using adapters
 * - Structure files stored in world/structures/ directory
 *
 * Design principles:
 * - Async for I/O: All operations return CompletableFuture
 * - Anti-corruption: Convert all MC types to JS via models
 * - Namespace format: "[namespace:]name" (defaults to "minecraft:")
 * - Position objects: {x, y, z, dimension?}
 *
 * Architecture:
 * This manager delegates to specialized operation classes:
 * - StructureNbtCore: File operations (load, save, list, remove, etc.)
 * - StructureNbtCapture: World capture and placement
 * - StructureNbtBlocks: Block analysis and replacement
 * - StructureNbtBackup: Backup and restore operations
 */
object StructureNbtManager {

    @Volatile
    private var server: MinecraftServer? = null

    @Volatile
    private var graalContext: Context? = null

    @Volatile
    private var structuresPath: Path? = null

    @Volatile
    private var nbtApi: com.rhett.rhettjs.api.NBTAPI? = null

    // Operation delegates
    private var coreOps: StructureNbtCore? = null
    private var captureOps: StructureNbtCapture? = null
    private var blocksOps: StructureNbtBlocks? = null
    private var backupOps: StructureNbtBackup? = null

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
                ConfigManager.debug("[StructureManager] Created generated directory: $path")
            }
        }
        if (!backupsPath.exists()) {
            Files.createDirectories(backupsPath)
            ConfigManager.debug("[StructureManager] Created backups directory: $backupsPath")
        }

        // Note: NBTAPI still uses structuresPath as root, but we organize as <root>/<namespace>/GENERATED_STRUCTURES_DIR/
        // This matches Minecraft's convention: generated/<namespace>/structures/ (plural for runtime saves)
        nbtApi = com.rhett.rhettjs.api.NBTAPI(structuresPath, backupsPath)

        // Initialize operation delegates
        coreOps = StructureNbtCore(minecraftServer, structuresPath!!, nbtApi!!)
        captureOps = StructureNbtCapture(minecraftServer, coreOps!!)
        blocksOps = StructureNbtBlocks(coreOps!!)
        backupOps = StructureNbtBackup(nbtApi!!)

        ConfigManager.debug("[StructureManager] Minecraft server reference set")
    }

    /**
     * Set the GraalVM context reference.
     * Called when GraalEngine initializes the context.
     */
    fun setContext(context: Context) {
        graalContext = context
        ConfigManager.debug("[StructureManager] GraalVM context reference set")
    }

    // ============ Core Operations ============

    /**
     * Check if a structure exists (async).
     * Returns Promise<boolean>.
     */
    fun exists(nameWithNamespace: String): CompletableFuture<Boolean> {
        return coreOps?.exists(nameWithNamespace)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    /**
     * List available structures from resource system (async).
     * Returns Promise<string[]> where each name is in format "namespace:name".
     */
    fun list(namespaceFilter: String?): CompletableFuture<List<String>> {
        return coreOps?.list(namespaceFilter)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    /**
     * List structures from world/generated/ directory only (async).
     * Returns Promise<string[]> where each name is in format "namespace:name".
     */
    fun listGenerated(namespaceFilter: String?): CompletableFuture<List<String>> {
        return coreOps?.listGenerated(namespaceFilter)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    /**
     * Remove a structure file (async).
     * Returns Promise<boolean> (true if removed, false if didn't exist).
     */
    fun remove(nameWithNamespace: String): CompletableFuture<Boolean> {
        return coreOps?.remove(nameWithNamespace)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    /**
     * Load a structure from resource system (async).
     * Returns Promise<StructureData>.
     */
    fun load(nameWithNamespace: String): CompletableFuture<StructureData> {
        return coreOps?.load(nameWithNamespace)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    /**
     * Save a structure to file (async) with automatic backup.
     * Returns Promise<void>.
     */
    fun save(nameWithNamespace: String, data: StructureData, skipBackup: Boolean = false): CompletableFuture<Void> {
        return coreOps?.save(nameWithNamespace, data, skipBackup)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    /**
     * Get the size of a single structure (async).
     * Returns Promise<{x, y, z}>.
     */
    fun getSize(nameWithNamespace: String): CompletableFuture<Map<String, Int>> {
        return coreOps?.getSize(nameWithNamespace)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    // ============ Capture/Place Operations ============

    /**
     * Capture a region and convert to structure data (async).
     * Returns Promise<void> after saving structure file.
     */
    fun capture(pos1: Value, pos2: Value, nameWithNamespace: String, options: Value?, skipBackup: Boolean = false): CompletableFuture<Void> {
        return captureOps?.capture(pos1, pos2, nameWithNamespace, options, skipBackup)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    /**
     * Place a structure at a position (async).
     * Returns Promise<void> after placing all blocks.
     */
    fun place(position: Value, nameWithNamespace: String, options: Value?): CompletableFuture<Void> {
        return captureOps?.place(position, nameWithNamespace, options)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    // ============ Block Operations ============

    /**
     * List all unique blocks in a structure with their counts (async).
     * Returns Promise<Map<String, Int>> of blockId → count.
     */
    fun blocksList(nameWithNamespace: String): CompletableFuture<Map<String, Int>> {
        return blocksOps?.blocksList(nameWithNamespace)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    /**
     * Extract unique mod namespaces from structure blocks (async).
     * Returns Promise<List<String>> of unique namespaces (e.g., ["minecraft", "terralith"]).
     */
    fun blocksNamespaces(nameWithNamespace: String): CompletableFuture<List<String>> {
        return blocksOps?.blocksNamespaces(nameWithNamespace)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    /**
     * Replace blocks in a structure according to replacement map (async).
     * Returns Promise<void> after saving modified structure (with backup).
     */
    fun blocksReplace(nameWithNamespace: String, replacementMap: Map<String, String>): CompletableFuture<Void> {
        return blocksOps?.blocksReplace(nameWithNamespace, replacementMap)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    // ============ Backup Operations ============

    /**
     * List available backups for a structure (async).
     * Returns Promise<List<String>> of backup timestamps.
     */
    fun listBackups(nameWithNamespace: String): CompletableFuture<List<String>> {
        return backupOps?.listBackups(nameWithNamespace)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    /**
     * Restore structure from backup (async).
     * Returns Promise<void> after restoration.
     */
    fun restoreBackup(nameWithNamespace: String, timestamp: String?): CompletableFuture<Void> {
        return backupOps?.restoreBackup(nameWithNamespace, timestamp)
            ?: CompletableFuture.failedFuture(IllegalStateException("Server not initialized"))
    }

    /**
     * Clear all state (called on reset/reload).
     * Clears context reference (context will be recreated).
     */
    fun reset() {
        graalContext = null
        ConfigManager.debug("[StructureManager] Reset complete")
    }
}
