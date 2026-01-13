package com.rhett.rhettjs.structure.operations

import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.structure.models.StructureData
import com.rhett.rhettjs.world.models.BlockData
import java.util.concurrent.CompletableFuture

/**
 * Block analysis and replacement operations for structure NBT files.
 * Provides utilities for querying and modifying blocks within structures.
 */
class StructureNbtBlocks(
    private val coreOps: StructureNbtCore
) {

    /**
     * List all unique blocks in a structure with their counts (async).
     * Returns Promise<Map<String, Int>> of blockId → count.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun blocksList(nameWithNamespace: String): CompletableFuture<Map<String, Int>> {
        val future = CompletableFuture<Map<String, Int>>()

        try {
            val loadFuture = coreOps.load(nameWithNamespace)
            loadFuture.whenComplete { structureData, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                // Count blocks by ID
                val blockCounts = mutableMapOf<String, Int>()
                structureData.blocks.forEach { block ->
                    val blockId = block.block.name
                    blockCounts[blockId] = (blockCounts[blockId] ?: 0) + 1
                }

                future.complete(blockCounts.toSortedMap())
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Extract unique mod namespaces from structure blocks (async).
     * Returns Promise<List<String>> of unique namespaces (e.g., ["minecraft", "terralith"]).
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun blocksNamespaces(nameWithNamespace: String): CompletableFuture<List<String>> {
        val future = CompletableFuture<List<String>>()

        try {
            val loadFuture = coreOps.load(nameWithNamespace)
            loadFuture.whenComplete { structureData, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                // Extract unique namespaces
                val namespaces = structureData.blocks
                    .map { block -> block.block.name.substringBefore(':') }
                    .toSet()
                    .sorted()

                future.complete(namespaces)
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Replace blocks in a structure according to replacement map (async).
     * Returns Promise<void> after saving modified structure (with backup).
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param replacementMap Map of oldBlockId → newBlockId (e.g., {"terralith:stone": "minecraft:stone"})
     */
    fun blocksReplace(nameWithNamespace: String, replacementMap: Map<String, String>): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        try {
            val loadFuture = coreOps.load(nameWithNamespace)
            loadFuture.whenComplete { structureData, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                // Replace blocks
                val newBlocks = structureData.blocks.map { block ->
                    val oldId = block.block.name
                    val newId = replacementMap[oldId]

                    if (newId != null) {
                        // Replace with new block ID
                        block.copy(
                            block = BlockData(name = newId, properties = block.block.properties)
                        )
                    } else {
                        // Keep original
                        block
                    }
                }

                val newStructureData = structureData.copy(blocks = newBlocks)

                // Save modified structure (with backup)
                val saveFuture = coreOps.save(nameWithNamespace, newStructureData, skipBackup = false)
                saveFuture.whenComplete { _, saveThrowable ->
                    if (saveThrowable != null) {
                        future.completeExceptionally(saveThrowable)
                    } else {
                        ConfigManager.debug("[StructureNbtBlocks] Replaced blocks in: $nameWithNamespace (${replacementMap.size} mappings)")
                        future.complete(null)
                    }
                }
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }
}
