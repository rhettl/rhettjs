package com.rhett.rhettjs.structure.large

import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.structure.utils.StructureNbtUtils
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.exists
import kotlin.io.path.extension

/**
 * Backup and restore operations for large structure NBT directories.
 * Unlike regular structures (single file backups), large structures backup entire directories.
 */
class LargeStructureNbtBackup(
    private val structuresPath: Path
) {

    /**
     * Backup a large structure directory before modification.
     * Creates timestamped directory backup and maintains retention policy (keeps last 5).
     *
     * @param namespace Structure namespace
     * @param baseName Structure base name (without rjs-large prefix)
     * @return true if backup created or skipped (no existing structure), false if error
     */
    fun backupLargeStructure(namespace: String, baseName: String): Boolean {
        try {
            // Check if source directory exists
            val sourceDir = structuresPath.resolve(namespace)
                .resolve(StructureNbtUtils.GENERATED_STRUCTURES_DIR)
                .resolve(StructureNbtUtils.RJS_LARGE_SUBDIR)
                .resolve(baseName)

            if (!sourceDir.exists() || !Files.isDirectory(sourceDir)) {
                ConfigManager.debug("[LargeStructureNbtBackup] No existing large structure to backup: $namespace:$baseName")
                return true // Nothing to backup, not an error
            }

            // Check if there are any .nbt files
            val nbtFiles = Files.list(sourceDir)
                .filter { it.extension == "nbt" }
                .toList()

            if (nbtFiles.isEmpty()) {
                ConfigManager.debug("[LargeStructureNbtBackup] No piece files to backup: $namespace:$baseName")
                return true
            }

            // Create backup directory with timestamp
            val backupsPath = structuresPath.parent
                .resolve("backups")
                .resolve(StructureNbtUtils.GENERATED_STRUCTURES_DIR)
                .resolve(StructureNbtUtils.RJS_LARGE_SUBDIR)

            val timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            val backupDirName = "$baseName.$timestamp"
            val backupDir = backupsPath.resolve(backupDirName)

            // Create backup directory
            Files.createDirectories(backupDir)

            // Copy all .nbt files
            nbtFiles.forEach { file ->
                val targetFile = backupDir.resolve(file.fileName)
                Files.copy(file, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }

            ConfigManager.debug("[LargeStructureNbtBackup] Backed up large structure: $namespace:$baseName → $backupDirName (${nbtFiles.size} pieces)")

            // Cleanup old backups (keep only 5 most recent)
            val allBackups = Files.list(backupsPath)
                .filter { Files.isDirectory(it) }
                .filter { it.fileName.toString().startsWith("$baseName.") }
                .sorted(Comparator.reverseOrder()) // Newest first (by name timestamp)
                .toList()

            if (allBackups.size > 5) {
                val toDelete = allBackups.drop(5)
                toDelete.forEach { oldBackup ->
                    Files.walk(oldBackup)
                        .sorted(Comparator.reverseOrder())
                        .forEach { Files.delete(it) }
                    ConfigManager.debug("[LargeStructureNbtBackup] Deleted old backup: ${oldBackup.fileName}")
                }
            }

            return true
        } catch (e: Exception) {
            ConfigManager.debug("[LargeStructureNbtBackup] Failed to backup large structure: ${e.message}")
            return false
        }
    }

    /**
     * List available directory backups for a large structure (async).
     * Returns Promise<List<String>> of backup timestamps.
     *
     * @param namespace Structure namespace
     * @param baseName Structure base name (without rjs-large prefix)
     */
    fun listBackups(namespace: String, baseName: String): CompletableFuture<List<String>> {
        val future = CompletableFuture<List<String>>()

        try {
            // Get backups directory
            val backupsPath = structuresPath.parent
                .resolve("backups")
                .resolve(StructureNbtUtils.GENERATED_STRUCTURES_DIR)
                .resolve(StructureNbtUtils.RJS_LARGE_SUBDIR)

            if (!backupsPath.exists()) {
                future.complete(emptyList())
                return future
            }

            // Find all backup directories for this structure
            val backupDirs = Files.list(backupsPath)
                .filter { Files.isDirectory(it) }
                .filter { it.fileName.toString().startsWith("$baseName.") }
                .map { dir ->
                    // Extract timestamp from directory name (e.g., "castle.2026-01-05_15-30-45" -> "2026-01-05_15-30-45")
                    dir.fileName.toString().substringAfter("$baseName.")
                }
                .sorted(Comparator.reverseOrder()) // Newest first
                .toList()

            future.complete(backupDirs)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Restore large structure from directory backup (async).
     * Returns Promise<void> after restoration.
     *
     * @param namespace Structure namespace
     * @param baseName Structure base name (without rjs-large prefix)
     * @param timestamp Optional specific backup timestamp, or null for most recent
     */
    fun restoreBackup(namespace: String, baseName: String, timestamp: String?): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        try {
            // Get backups directory
            val backupsPath = structuresPath.parent
                .resolve("backups")
                .resolve(StructureNbtUtils.GENERATED_STRUCTURES_DIR)
                .resolve(StructureNbtUtils.RJS_LARGE_SUBDIR)

            if (!backupsPath.exists()) {
                future.completeExceptionally(IllegalArgumentException("No backups found for: $namespace:$baseName"))
                return future
            }

            // Find backup directory
            val backupDir = if (timestamp != null) {
                // Use specific timestamp
                backupsPath.resolve("$baseName.$timestamp")
            } else {
                // Find most recent backup
                Files.list(backupsPath)
                    .filter { Files.isDirectory(it) }
                    .filter { it.fileName.toString().startsWith("$baseName.") }
                    .sorted(Comparator.reverseOrder()) // Newest first
                    .findFirst()
                    .orElse(null)
            }

            if (backupDir == null || !backupDir.exists()) {
                future.completeExceptionally(IllegalArgumentException("Backup not found for: $namespace:$baseName"))
                return future
            }

            // Get target directory
            val targetDir = structuresPath.resolve(namespace)
                .resolve(StructureNbtUtils.GENERATED_STRUCTURES_DIR)
                .resolve(StructureNbtUtils.RJS_LARGE_SUBDIR)
                .resolve(baseName)

            // Create target directory if doesn't exist
            if (!targetDir.exists()) {
                Files.createDirectories(targetDir)
            }

            // Delete existing files in target
            Files.list(targetDir)
                .filter { it.extension == "nbt" }
                .forEach { Files.delete(it) }

            // Copy all .nbt files from backup
            Files.list(backupDir)
                .filter { it.extension == "nbt" }
                .forEach { backupFile ->
                    val targetFile = targetDir.resolve(backupFile.fileName)
                    Files.copy(backupFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }

            ConfigManager.debug("[LargeStructureNbtBackup] Restored large structure: $namespace:$baseName from backup ${timestamp ?: "latest"}")
            future.complete(null)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }
}
