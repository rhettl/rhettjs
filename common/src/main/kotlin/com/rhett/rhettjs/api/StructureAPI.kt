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
     *
     * Example:
     *   write("village/houses/house_1", structureData)
     *   write("village/houses/house_1.nbt", structureData)  // same
     */
    fun write(name: String, data: Any) {
        val normalizedName = ensureNbtExtension(name)
        nbtApi.write(normalizedName, data)
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
