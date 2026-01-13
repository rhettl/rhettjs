package com.rhett.rhettjs.structure.operations

import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.structure.utils.StructureNbtUtils
import java.util.concurrent.CompletableFuture

/**
 * Backup and restore operations for structure NBT files.
 * Uses NBTAPI for single-file backup management.
 */
class StructureNbtBackup(
    private val nbtApi: com.rhett.rhettjs.api.NBTAPI
) {

    /**
     * List available backups for a structure (async).
     * Returns Promise<List<String>> of backup timestamps.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun listBackups(nameWithNamespace: String): CompletableFuture<List<String>> {
        val future = CompletableFuture<List<String>>()

        try {
            val (namespace, name) = StructureNbtUtils.parseStructureName(nameWithNamespace)
            val relativePath = "$namespace/${StructureNbtUtils.GENERATED_STRUCTURES_DIR}/$name.nbt"

            // Use NBTAPI's backup listing
            val backups = nbtApi.listBackups(relativePath)
            future.complete(backups)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Restore structure from backup (async).
     * Returns Promise<void> after restoration.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param timestamp Optional specific backup timestamp (e.g., "2026-01-05_15-30-45"), or null for most recent
     */
    fun restoreBackup(nameWithNamespace: String, timestamp: String?): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        try {
            val (namespace, name) = StructureNbtUtils.parseStructureName(nameWithNamespace)
            val relativePath = "$namespace/${StructureNbtUtils.GENERATED_STRUCTURES_DIR}/$name.nbt"

            // Use NBTAPI's restore functionality
            val success = nbtApi.restoreFromBackup(relativePath, timestamp)
            if (success) {
                ConfigManager.debug("[StructureNbtBackup] Restored structure: $nameWithNamespace from backup ${timestamp ?: "latest"}")
                future.complete(null)
            } else {
                future.completeExceptionally(IllegalStateException("Failed to restore structure from backup"))
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }
}
