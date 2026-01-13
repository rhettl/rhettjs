package com.rhett.rhettjs.structure.large

import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.structure.StructureNbtManager
import com.rhett.rhettjs.structure.utils.StructureNbtUtils
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtIo
import net.minecraft.server.MinecraftServer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors
import kotlin.io.path.*

/**
 * Core operations for large structure NBT files.
 * Handles list, remove, getSize, blocksReplace, and metadata management.
 */
class LargeStructureNbtCore(
    private val server: MinecraftServer,
    private val structuresPath: Path,
    private val backupOps: LargeStructureNbtBackup
) {

    /**
     * List large structures from resource system (async).
     * Uses StructureTemplateManager to find rjs-large structures from all sources.
     * Returns Promise<string[]> where each name is in format "namespace:name".
     *
     * @param namespaceFilter Optional namespace filter (null = all namespaces)
     */
    fun list(namespaceFilter: String?): CompletableFuture<List<String>> {
        val future = CompletableFuture<List<String>>()

        try {
            // Execute on main thread to access StructureTemplateManager
            server.execute {
                try {
                    // List all structures, filter for rjs-large paths
                    // Convert Java Stream to Kotlin list first
                    val allTemplates = server.structureManager.listTemplates()
                        .collect(Collectors.toList())

                    val largeStructures = allTemplates
                        .filter { loc ->
                            // Filter by namespace if specified
                            (namespaceFilter == null || loc.namespace == namespaceFilter) &&
                            // Only include rjs-large pieces
                            loc.path.startsWith("${StructureNbtUtils.RJS_LARGE_SUBDIR}/")
                        }
                        .mapNotNull { loc ->
                            // Extract structure name from path (e.g., "rjs-large/castle/0_0_0" -> "castle")
                            val pathParts = loc.path.split("/")
                            if (pathParts.size >= 2) {
                                val structureName = pathParts[1]
                                "${loc.namespace}:$structureName"
                            } else {
                                null
                            }
                        }
                        .distinct()  // Each structure appears once, even though it has multiple pieces
                        .sorted()

                    ConfigManager.debug("[LargeStructureNbtCore] Listed ${largeStructures.size} large structures from resource system")
                    future.complete(largeStructures)
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Remove a large structure (all pieces) (async).
     * Returns Promise<boolean> (true if removed, false if didn't exist).
     *
     * @param namespace Structure namespace
     * @param baseName Structure base name (without rjs-large prefix)
     */
    fun remove(namespace: String, baseName: String): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()

        try {
            // Get large structure directory
            val largeDir = structuresPath.resolve(namespace)
                .resolve(StructureNbtUtils.GENERATED_STRUCTURES_DIR)
                .resolve(StructureNbtUtils.RJS_LARGE_SUBDIR)
                .resolve(baseName)

            if (!largeDir.exists() || !Files.isDirectory(largeDir)) {
                future.complete(false)
                return future
            }

            // Backup before deletion
            backupOps.backupLargeStructure(namespace, baseName)

            // Delete all files in the directory
            Files.walk(largeDir).use { paths ->
                paths.sorted(Comparator.reverseOrder())
                    .forEach { Files.delete(it) }
            }

            ConfigManager.debug("[LargeStructureNbtCore] Removed large structure: $namespace:$baseName")
            future.complete(true)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Get the size of a large structure (async).
     * Returns Promise<{x, y, z}>.
     *
     * @param namespace Structure namespace
     * @param baseName Structure base name (without rjs-large prefix)
     */
    fun getSize(namespace: String, baseName: String): CompletableFuture<Map<String, Int>> {
        val future = CompletableFuture<Map<String, Int>>()

        try {
            // Verify this is a large structure
            val largeDir = structuresPath.resolve(namespace)
                .resolve(StructureNbtUtils.GENERATED_STRUCTURES_DIR)
                .resolve(StructureNbtUtils.RJS_LARGE_SUBDIR)
                .resolve(baseName)

            if (!largeDir.exists() || !Files.isDirectory(largeDir)) {
                future.completeExceptionally(IllegalArgumentException("Large structure not found: $namespace:$baseName"))
                return future
            }

            // Load origin piece to get piece size
            val originLoadFuture = StructureNbtManager.load("$namespace:${StructureNbtUtils.RJS_LARGE_SUBDIR}/$baseName/0_0_0")

            originLoadFuture.whenComplete { originData, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                try {
                    val pieceSizeX = originData.size.x
                    val pieceSizeY = originData.size.y
                    val pieceSizeZ = originData.size.z

                    // Find max grid coordinates
                    var maxGridX = 0
                    var maxGridY = 0
                    var maxGridZ = 0

                    Files.list(largeDir).use { files ->
                        files.filter { it.isRegularFile() && it.extension == "nbt" }
                            .forEach { file ->
                                val parts = file.nameWithoutExtension.split("_")
                                if (parts.size == 3) {
                                    maxGridX = maxOf(maxGridX, parts[0].toInt())
                                    maxGridY = maxOf(maxGridY, parts[1].toInt())
                                    maxGridZ = maxOf(maxGridZ, parts[2].toInt())
                                }
                            }
                    }

                    // Load max piece to get remainder size
                    val maxPieceLoadFuture = StructureNbtManager.load("$namespace:${StructureNbtUtils.RJS_LARGE_SUBDIR}/$baseName/${maxGridX}_${maxGridY}_${maxGridZ}")

                    maxPieceLoadFuture.whenComplete { maxPieceData, maxThrowable ->
                        if (maxThrowable != null) {
                            future.completeExceptionally(maxThrowable)
                        } else {
                            // Calculate total size
                            val totalX = maxGridX * pieceSizeX + maxPieceData.size.x
                            val totalY = maxGridY * pieceSizeY + maxPieceData.size.y
                            val totalZ = maxGridZ * pieceSizeZ + maxPieceData.size.z

                            future.complete(mapOf("x" to totalX, "y" to totalY, "z" to totalZ))
                        }
                    }

                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            }

        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Replace blocks in all pieces of a large structure (async).
     * Also updates metadata.requires[] in 0_0_0.nbt with new namespace requirements.
     * Returns Promise<void> after saving all modified pieces (with backup).
     *
     * @param namespace Structure namespace
     * @param baseName Structure base name (without rjs-large prefix)
     * @param replacementMap Map of oldBlockId → newBlockId
     */
    fun blocksReplace(namespace: String, baseName: String, replacementMap: Map<String, String>): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        try {
            // Get large structure directory
            val largeDir = structuresPath.resolve(namespace)
                .resolve(StructureNbtUtils.GENERATED_STRUCTURES_DIR)
                .resolve(StructureNbtUtils.RJS_LARGE_SUBDIR)
                .resolve(baseName)

            if (!largeDir.exists() || !Files.isDirectory(largeDir)) {
                future.completeExceptionally(IllegalArgumentException("Large structure not found: $namespace:$baseName"))
                return future
            }

            // Backup before modification
            backupOps.backupLargeStructure(namespace, baseName)

            // Find all piece files
            val pieceFiles = Files.list(largeDir)
                .filter { it.isRegularFile() && it.extension == "nbt" }
                .map { it.nameWithoutExtension }
                .toList()

            if (pieceFiles.isEmpty()) {
                future.completeExceptionally(IllegalArgumentException("Large structure has no pieces: $namespace:$baseName"))
                return future
            }

            // Replace blocks in each piece (delegate to StructureNbtManager)
            val replaceFutures = pieceFiles.map { pieceName ->
                val pieceFullName = "$namespace:${StructureNbtUtils.RJS_LARGE_SUBDIR}/$baseName/$pieceName"
                StructureNbtManager.blocksReplace(pieceFullName, replacementMap)
            }

            // Wait for all replacements to complete
            CompletableFuture.allOf(*replaceFutures.toTypedArray()).whenComplete { _, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                // Update metadata.requires[] in 0_0_0.nbt
                try {
                    updateLargeStructureMetadata(namespace, baseName)
                    ConfigManager.debug("[LargeStructureNbtCore] Replaced blocks in large structure: $namespace:$baseName (${pieceFiles.size} pieces, ${replacementMap.size} mappings)")
                    future.complete(null)
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Update metadata.requires[] in 0_0_0.nbt with current namespace requirements.
     * Scans all pieces to find unique namespaces (excluding "minecraft").
     *
     * @param namespace Structure namespace
     * @param baseName Structure base name (without rjs-large prefix)
     */
    fun updateLargeStructureMetadata(namespace: String, baseName: String) {
        val originPath = StructureNbtUtils.getStructurePath(
            structuresPath,
            namespace,
            "${StructureNbtUtils.RJS_LARGE_SUBDIR}/$baseName/0_0_0"
        )

        if (originPath == null || !originPath.exists()) {
            ConfigManager.debug("[LargeStructureNbtCore] Warning: 0_0_0.nbt not found for large structure")
            return
        }

        // Load 0_0_0.nbt
        val originNBT = NbtIo.readCompressed(originPath, net.minecraft.nbt.NbtAccounter.unlimitedHeap())

        // Scan all pieces to collect required namespaces
        val largeDir = structuresPath.resolve(namespace)
            .resolve(StructureNbtUtils.GENERATED_STRUCTURES_DIR)
            .resolve(StructureNbtUtils.RJS_LARGE_SUBDIR)
            .resolve(baseName)
        val allNamespaces = mutableSetOf<String>()

        if (largeDir.exists()) {
            Files.list(largeDir)
                .filter { it.extension == "nbt" }
                .forEach { pieceFile ->
                    try {
                        val pieceNBT = NbtIo.readCompressed(pieceFile, net.minecraft.nbt.NbtAccounter.unlimitedHeap())
                        val palette = pieceNBT.getList("palette", 10) // 10 = CompoundTag

                        for (i in 0 until palette.size) {
                            val blockTag = palette.getCompound(i)
                            val blockName = blockTag.getString("Name")
                            val blockNamespace = blockName.substringBefore(':')
                            if (blockNamespace != "minecraft") {
                                allNamespaces.add(blockNamespace)
                            }
                        }
                    } catch (e: Exception) {
                        ConfigManager.debug("[LargeStructureNbtCore] Warning: Failed to read piece ${pieceFile.fileName}: ${e.message}")
                    }
                }
        }

        // Update metadata
        val metadata = if (originNBT.contains("metadata")) {
            originNBT.getCompound("metadata")
        } else {
            CompoundTag()
        }

        // Create requires list
        val requiresList = ListTag()
        allNamespaces.sorted().forEach { ns ->
            requiresList.add(net.minecraft.nbt.StringTag.valueOf(ns))
        }
        metadata.put("requires", requiresList)
        originNBT.put("metadata", metadata)

        // Write back (skip backup since we already backed up the directory)
        NbtIo.writeCompressed(originNBT, originPath)

        ConfigManager.debug("[LargeStructureNbtCore] Updated metadata.requires[] in 0_0_0.nbt: ${allNamespaces.sorted()}")
    }
}
