package com.rhett.rhettjs.api

import net.minecraft.nbt.*
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/**
 * NBT API for reading/writing NBT files with path validation and automatic backups.
 * Provides JavaScript-style utility methods for traversing NBT data.
 */
class NBTAPI(
    private val structuresDir: Path? = null,
    private val backupsDir: Path? = null
) {

    /**
     * Result of filter/find operations containing matched element and its context.
     */
    data class FilterResult(
        val value: Any?,
        val path: List<Any>,
        val parent: Any?
    )

    /**
     * Read NBT file and convert to JavaScript-friendly Map/List structure.
     *
     * @param path Relative path within structures directory
     * @return Converted NBT data, or null if file doesn't exist
     */
    fun read(path: String): Any? {
        val dir = structuresDir ?: throw IllegalStateException("Structures directory not set")
        val file = validateAndResolvePath(dir, path)

        if (!file.exists()) {
            return null
        }

        return try {
            val nbt = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap())
            nbtToJs(nbt)
        } catch (e: IOException) {
            throw IOException("Failed to read NBT file: $path", e)
        }
    }

    /**
     * Write JavaScript data to NBT file with automatic backup and path validation.
     *
     * @param path Relative path within structures directory
     * @param data JavaScript-friendly data (Map/List/primitives)
     * @param skipBackup If true, skip automatic backup creation (default: false)
     * @throws SecurityException if path is invalid or outside structures directory
     */
    fun write(path: String, data: Any, skipBackup: Boolean = false) {
        val dir = structuresDir ?: throw IllegalStateException("Structures directory not set")
        val file = validateAndResolvePath(dir, path)

        // Create parent directories if needed
        file.parent?.let { Files.createDirectories(it) }

        // Create backup if file exists (unless explicitly skipped)
        var backupFilename: String? = null
        if (!skipBackup && file.exists() && backupsDir != null) {
            backupFilename = createBackup(path, file)
            com.rhett.rhettjs.config.ConfigManager.debug("[NBT] Created backup: ${backupsDir?.resolve(backupFilename ?: "")}")
        }

        // Convert JS data to NBT
        val nbt = jsToNbt(data) as CompoundTag

        // Write to file
        try {
            NbtIo.writeCompressed(nbt, file)
            com.rhett.rhettjs.config.ConfigManager.debug("[NBT] Wrote file: $file")
            if (backupFilename != null) {
                com.rhett.rhettjs.RhettJSCommon.LOGGER.info("[RhettJS] Saved $path (backup: $backupFilename)")
            } else {
                com.rhett.rhettjs.RhettJSCommon.LOGGER.info("[RhettJS] Saved $path (no backup created)")
            }
        } catch (e: IOException) {
            throw IOException("Failed to write NBT file: $path", e)
        }
    }

    /**
     * Iterate over all elements in NBT data tree (like Array.forEach).
     *
     * @param data NBT data as Map/List structure
     * @param callback Called for each element with (value, path, parent)
     */
    fun forEach(data: Any, callback: (value: Any?, path: List<Any>, parent: Any?) -> Unit) {
        traverse(data, mutableListOf(), null, callback)
    }

    /**
     * Filter NBT data tree for matching elements (like Array.filter).
     *
     * @param data NBT data as Map/List structure
     * @param predicate Filter function returning true for matches
     * @return List of FilterResult objects containing matched elements
     */
    fun filter(data: Any, predicate: (value: Any?, path: List<Any>, parent: Any?) -> Boolean): List<FilterResult> {
        val results = mutableListOf<FilterResult>()
        traverse(data, mutableListOf(), null) { value, path, parent ->
            if (predicate(value, path, parent)) {
                results.add(FilterResult(value, path.toList(), parent))
            }
        }
        return results
    }

    /**
     * Find first matching element in NBT data tree (like Array.find).
     *
     * @param data NBT data as Map/List structure
     * @param predicate Filter function returning true for match
     * @return FilterResult for first match, or null if no match
     */
    fun find(data: Any, predicate: (value: Any?, path: List<Any>, parent: Any?) -> Boolean): FilterResult? {
        var result: FilterResult? = null
        var found = false

        traverse(data, mutableListOf(), null) { value, path, parent ->
            if (!found && predicate(value, path, parent)) {
                result = FilterResult(value, path.toList(), parent)
                found = true
            }
        }

        return result
    }

    /**
     * Check if any element matches predicate (like Array.some).
     *
     * @param data NBT data as Map/List structure
     * @param predicate Filter function returning true for match
     * @return true if at least one element matches
     */
    fun some(data: Any, predicate: (value: Any?, path: List<Any>, parent: Any?) -> Boolean): Boolean {
        var found = false

        traverse(data, mutableListOf(), null) { value, path, parent ->
            if (!found && predicate(value, path, parent)) {
                found = true
            }
        }

        return found
    }

    // ====== Private Helper Methods ======

    /**
     * Validate path and resolve it within the structures directory.
     * Prevents path traversal attacks.
     */
    private fun validateAndResolvePath(baseDir: Path, path: String): Path {
        // Reject obvious traversal attempts
        if (path.contains("..")) {
            throw SecurityException("Path traversal not allowed: $path")
        }

        // Reject absolute paths
        if (path.startsWith("/") || path.startsWith("\\")) {
            throw SecurityException("Absolute paths not allowed: $path")
        }

        // Resolve and normalize
        val resolved = baseDir.resolve(path).normalize()

        // Ensure result is within base directory
        if (!resolved.startsWith(baseDir)) {
            throw SecurityException("Path must be within structures directory: $path")
        }

        return resolved
    }

    /**
     * Create timestamped backup of existing file.
     * Automatically cleans up old backups (keeps last 5).
     *
     * @param path Relative path to the file
     * @param file Absolute path to the file
     * @return The backup filename that was created, or null if backup failed
     */
    private fun createBackup(path: String, file: Path): String? {
        val dir = backupsDir ?: return null

        // Create timestamped backup
        val timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val backupName = "$path.$timestamp.bak"
        val backupPath = dir.resolve(backupName)

        // Create parent directories
        backupPath.parent?.let { Files.createDirectories(it) }

        // Copy file to backup
        Files.copy(file, backupPath, StandardCopyOption.REPLACE_EXISTING)

        // Cleanup: keep only last 5 backups
        cleanupOldBackups(path, maxBackups = 5)

        return backupPath.fileName.toString()
    }

    /**
     * List all available backups for a file, sorted by timestamp (newest first).
     *
     * @param path Relative path within structures directory
     * @return List of backup filenames
     */
    fun listBackups(path: String): List<String> {
        val dir = backupsDir ?: return emptyList()
        val backupPattern = "$path."
        val backupDir = dir.resolve(path).parent ?: return emptyList()

        if (!backupDir.exists()) {
            return emptyList()
        }

        return Files.walk(backupDir, 1)
            .filter { it.isRegularFile() }
            .filter { it.fileName.toString().startsWith(backupPattern) }
            .filter { it.fileName.toString().endsWith(".bak") }
            .sorted(Comparator.reverseOrder()) // Newest first
            .map { it.fileName.toString() }
            .toList()
    }

    /**
     * Clean up old backups, keeping only the most recent N backups.
     */
    private fun cleanupOldBackups(path: String, maxBackups: Int) {
        val backups = listBackups(path)

        if (backups.size > maxBackups) {
            val dir = backupsDir ?: return
            val backupDir = dir.resolve(path).parent ?: return
            val toDelete = backups.drop(maxBackups)

            toDelete.forEach { backupFilename ->
                val backupFile = backupDir.resolve(backupFilename)
                if (backupFile.exists()) {
                    Files.delete(backupFile)
                }
            }
        }
    }

    /**
     * Manually create a backup of a file.
     *
     * @param path Relative path within structures directory
     * @return The backup filename that was created, or null if file doesn't exist
     */
    fun createManualBackup(path: String): String? {
        val dir = structuresDir ?: throw IllegalStateException("Structures directory not set")
        val file = validateAndResolvePath(dir, path)

        if (!file.exists()) {
            return null
        }

        return createBackup(path, file)
    }

    /**
     * Restore a file from its backup.
     *
     * @param path Relative path to restore to
     * @param backupTimestamp Optional specific backup timestamp (defaults to most recent)
     * @return true if restore succeeded, false if backup not found
     */
    fun restoreFromBackup(path: String, backupTimestamp: String? = null): Boolean {
        val dir = structuresDir ?: throw IllegalStateException("Structures directory not set")
        val backupsDirectory = backupsDir ?: return false

        // Find the backup to restore
        val backups = listBackups(path)
        if (backups.isEmpty()) {
            return false
        }

        val backupToRestore = if (backupTimestamp != null) {
            backups.firstOrNull { it.contains(backupTimestamp) }
        } else {
            backups.firstOrNull()
        } ?: return false

        // Read backup data
        val backupPath = backupsDirectory.resolve(path).parent?.resolve(backupToRestore) ?: return false
        if (!backupPath.exists()) {
            return false
        }

        val backupData = try {
            val nbt = NbtIo.readCompressed(backupPath, NbtAccounter.unlimitedHeap())
            nbtToJs(nbt)
        } catch (e: IOException) {
            return false
        } ?: return false

        // Write to target location (this will create another backup)
        write(path, backupData)

        return true
    }

    /**
     * Traverse NBT data tree depth-first, calling callback for each element.
     */
    private fun traverse(
        value: Any?,
        path: MutableList<Any>,
        parent: Any?,
        callback: (value: Any?, path: List<Any>, parent: Any?) -> Unit
    ) {
        // Call callback for current element
        callback(value, path.toList(), parent)

        // Recurse based on type
        when (value) {
            is Map<*, *> -> {
                value.forEach { (key, childValue) ->
                    path.add(key as Any)
                    traverse(childValue, path, value, callback)
                    path.removeAt(path.size - 1)
                }
            }
            is List<*> -> {
                value.forEachIndexed { index, childValue ->
                    path.add(index)
                    traverse(childValue, path, value, callback)
                    path.removeAt(path.size - 1)
                }
            }
        }
    }

    /**
     * Convert Minecraft NBT (Tag) to JavaScript-friendly structure.
     * Note: Minecraft 1.21.1 uses *Tag classes (StringTag, IntTag, etc.)
     */
    private fun nbtToJs(tag: Tag): Any? {
        return when (tag) {
            is CompoundTag -> {
                val map = mutableMapOf<String, Any?>()
                for (key in tag.allKeys) {
                    map[key] = nbtToJs(tag.get(key)!!)
                }
                map
            }
            is ListTag -> {
                val list = mutableListOf<Any?>()
                for (i in 0 until tag.size) {
                    list.add(nbtToJs(tag[i]))
                }
                list
            }
            is StringTag -> tag.asString
            is IntTag -> tag.asInt
            is ByteTag -> tag.asByte.toInt() // Keep as number (used for enums, not just booleans)
            is ShortTag -> tag.asShort.toInt()
            is LongTag -> tag.asLong
            is FloatTag -> tag.asFloat
            is DoubleTag -> tag.asDouble
            is ByteArrayTag -> tag.asByteArray.toList()
            is IntArrayTag -> tag.asIntArray.toList()
            is LongArrayTag -> tag.asLongArray.toList()
            is EndTag -> null
            else -> tag.asString
        }
    }

    /**
     * Convert JavaScript data to Minecraft NBT (Tag).
     * Note: Minecraft 1.21.1 uses *Tag classes with .valueOf() for primitives
     */
    private fun jsToNbt(value: Any?): Tag {
        return when (value) {
            null -> CompoundTag() // Empty compound for null
            is Map<*, *> -> {
                val compound = CompoundTag()
                value.forEach { (key, v) ->
                    compound.put(key.toString(), jsToNbt(v))
                }
                compound
            }
            is List<*> -> {
                val list = ListTag()
                value.forEach { v ->
                    list.add(jsToNbt(v))
                }
                list
            }
            is String -> StringTag.valueOf(value)
            is Int -> IntTag.valueOf(value)
            is Long -> LongTag.valueOf(value)
            is Float -> FloatTag.valueOf(value)
            is Double -> DoubleTag.valueOf(value)
            is Boolean -> ByteTag.valueOf(value)
            is Byte -> ByteTag.valueOf(value)
            is Short -> ShortTag.valueOf(value)
            is Number -> IntTag.valueOf(value.toInt()) // Convert all numbers to int by default
            else -> StringTag.valueOf(value.toString())
        }
    }
}
