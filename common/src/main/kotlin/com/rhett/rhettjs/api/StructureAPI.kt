package com.rhett.rhettjs.api

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension

/**
 * High-level API for working with Minecraft structure files.
 * Wraps NBTAPI with structure-specific convenience methods.
 *
 * Structures are stored in: <world>/structures/
 * Organized in pools: village/, desert/, etc.
 */
class StructureAPI(
    private val structuresDir: Path,
    private val backupsDir: Path
) {

    private val nbtApi = NBTAPI(structuresDir, backupsDir)

    /**
     * List all structure files, optionally filtered by pool.
     *
     * @param pool Optional pool name to filter by (e.g., "village", "desert")
     * @return List of structure names (relative paths without .nbt extension)
     *
     * Example:
     *   list()           → ["village/houses/house_1", "desert/temples/temple_1", ...]
     *   list("village")  → ["village/houses/house_1", "village/houses/house_2", ...]
     */
    fun list(pool: String? = null): List<String> {
        if (!structuresDir.exists()) {
            return emptyList()
        }

        val searchDir = if (pool != null) {
            structuresDir.resolve(pool)
        } else {
            structuresDir
        }

        if (!searchDir.exists()) {
            return emptyList()
        }

        return Files.walk(searchDir)
            .filter { it.isRegularFile() }
            .filter { it.extension == "nbt" }
            .filter { !it.fileName.toString().endsWith(".bak") }
            .map { file ->
                // Get path relative to structures directory
                val relativePath = structuresDir.relativize(file)
                // Remove .nbt extension
                removeNbtExtension(relativePath.toString())
            }
            .sorted()
            .toList()
    }

    /**
     * Read a structure file by name.
     *
     * @param name Structure name (with or without .nbt extension)
     * @return Structure data as Map/List, or null if not found
     *
     * Example:
     *   read("village/houses/house_1")      → {size: [16,5,16], palette: [...], ...}
     *   read("village/houses/house_1.nbt")  → same (extension optional)
     */
    fun read(name: String): Any? {
        val normalizedName = ensureNbtExtension(name)
        return nbtApi.read(normalizedName)
    }

    /**
     * Write a structure file by name.
     *
     * @param name Structure name (with or without .nbt extension)
     * @param data Structure data (Map with size, palette, blocks, entities, etc.)
     * @param skipBackup If true, skip automatic backup creation (default: false)
     *
     * Example:
     *   write("village/houses/house_1", structureData)                 // Auto-backup enabled
     *   write("village/houses/house_1", structureData, skipBackup: true)  // No auto-backup
     */
    fun write(name: String, data: Any, skipBackup: Boolean = false) {
        val normalizedName = ensureNbtExtension(name)
        nbtApi.write(normalizedName, data, skipBackup)
    }

    /**
     * Manually create a timestamped backup of a structure file.
     * Automatically cleans up old backups (keeps last 5).
     *
     * @param name Structure name (with or without .nbt extension)
     * @return The backup filename that was created, or null if structure doesn't exist
     *
     * Example:
     *   backup("village/houses/house_1")
     *   → Creates: backups/structures/village/houses/house_1.nbt.2024-12-19_00-42-15.bak
     *   → Returns: "house_1.nbt.2024-12-19_00-42-15.bak"
     */
    fun backup(name: String): String? {
        val normalizedName = ensureNbtExtension(name)
        return nbtApi.createManualBackup(normalizedName)
    }

    /**
     * List all available backups for a structure, sorted by timestamp (newest first).
     *
     * @param name Structure name (with or without .nbt extension)
     * @return List of backup filenames (e.g., ["house_1.nbt.2024-12-19_00-42-15.bak", ...])
     *
     * Example:
     *   listBackups("village/houses/house_1")
     *   → ["house_1.nbt.2024-12-19_00-45-00.bak", "house_1.nbt.2024-12-19_00-42-15.bak"]
     */
    fun listBackups(name: String): List<String> {
        val normalizedName = ensureNbtExtension(name)
        return nbtApi.listBackups(normalizedName)
    }

    /**
     * Restore a structure from its backup.
     * By default, restores to the original location. Optionally restore to a new name.
     *
     * @param name Source structure name (with or without .nbt extension)
     * @param targetName Optional new name for restored structure (defaults to original name)
     * @param backupTimestamp Optional specific backup timestamp (defaults to most recent)
     * @return true if restore succeeded, false if backup not found
     *
     * Examples:
     *   restore("academy")
     *   → Restores most recent backup to: structures/academy.nbt
     *
     *   restore("academy", "academy-restored")
     *   → Restores most recent backup to: structures/academy-restored.nbt
     *
     *   restore("academy", null, "2024-12-19_00-42-15")
     *   → Restores specific backup to: structures/academy.nbt
     */
    fun restore(name: String, targetName: String? = null, backupTimestamp: String? = null): Boolean {
        val normalizedName = ensureNbtExtension(name)

        // If restoring to same location, use NBTAPI's restore method
        if (targetName == null) {
            return nbtApi.restoreFromBackup(normalizedName, backupTimestamp)
        }

        // Otherwise, restore to a different name
        val backups = nbtApi.listBackups(normalizedName)
        if (backups.isEmpty()) {
            return false
        }

        val backupToRestore = if (backupTimestamp != null) {
            backups.firstOrNull { it.contains(backupTimestamp) }
        } else {
            backups.firstOrNull()
        } ?: return false

        // Read backup data
        val backupPath = backupsDir.resolve(normalizedName).parent?.resolve(backupToRestore) ?: return false
        if (!backupPath.exists()) {
            return false
        }

        val backupData = nbtApi.read(backupPath.toString().removePrefix(structuresDir.toString()).removePrefix("/"))
            ?: return false

        // Write to target location
        write(targetName, backupData)

        return true
    }

    /**
     * Get the NBTAPI instance for advanced operations.
     * Allows access to forEach, filter, find, some methods.
     */
    fun getNbtApi(): NBTAPI = nbtApi

    // ====== Helper Methods ======

    /**
     * Ensure path has .nbt extension.
     */
    private fun ensureNbtExtension(path: String): String {
        return if (path.endsWith(".nbt")) {
            path
        } else {
            "$path.nbt"
        }
    }

    /**
     * Remove .nbt extension from path.
     */
    private fun removeNbtExtension(path: String): String {
        return if (path.endsWith(".nbt")) {
            path.substring(0, path.length - 4)
        } else {
            path
        }
    }
}
