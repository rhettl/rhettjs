package com.rhett.rhettjs.structure.large

import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.structure.StructureNbtManager
import com.rhett.rhettjs.structure.utils.StructureNbtUtils
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtIo
import net.minecraft.server.MinecraftServer
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors
import kotlin.io.path.exists
import kotlin.io.path.extension

/**
 * Capture and placement operations for large structure NBT files.
 * Handles grid splitting for capture and grid reconstruction for placement.
 */
class LargeStructureNbtCapture(
    private val server: MinecraftServer,
    private val structuresPath: Path,
    private val graalContext: Context?,
    private val backupOps: LargeStructureNbtBackup
) {

    /**
     * Capture a large region split into multiple piece files (async).
     * Returns Promise<void> after saving all piece files.
     *
     * Large structures are stored in: generated/<namespace>/structures/rjs-large/<name>/X_Y_Z.nbt
     * The 0_0_0.nbt file contains metadata.requires[] with required mod namespaces.
     *
     * @param pos1 First corner position {x, y, z, dimension?}
     * @param pos2 Second corner position {x, y, z, dimension?}
     * @param namespace Structure namespace
     * @param baseName Structure base name (without rjs-large prefix)
     * @param options Optional options {pieceSize?: {x,y,z}, author?: string, description?: string}
     */
    fun capture(
        pos1: Value,
        pos2: Value,
        namespace: String,
        baseName: String,
        options: Value?
    ): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        try {
            // Extract positions from JS
            val x1 = pos1.getMember("x").asInt()
            val y1 = pos1.getMember("y").asInt()
            val z1 = pos1.getMember("z").asInt()
            val x2 = pos2.getMember("x").asInt()
            val y2 = pos2.getMember("y").asInt()
            val z2 = pos2.getMember("z").asInt()

            // Get dimension
            val dimension = if (options != null && options.hasMember("dimension")) {
                options.getMember("dimension").asString()
            } else if (pos1.hasMember("dimension")) {
                pos1.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            // Get piece size (default 48x48x48)
            val pieceSizeX = if (options != null && options.hasMember("pieceSize")) {
                val ps = options.getMember("pieceSize")
                if (ps.hasMember("x")) ps.getMember("x").asInt() else 48
            } else 48

            val pieceSizeY = if (options != null && options.hasMember("pieceSize")) {
                val ps = options.getMember("pieceSize")
                if (ps.hasMember("y")) ps.getMember("y").asInt() else 48
            } else 48

            val pieceSizeZ = if (options != null && options.hasMember("pieceSize")) {
                val ps = options.getMember("pieceSize")
                if (ps.hasMember("z")) ps.getMember("z").asInt() else 48
            } else 48

            // Normalize coordinates (min to max)
            val minX = minOf(x1, x2)
            val minY = minOf(y1, y2)
            val minZ = minOf(z1, z2)
            val maxX = maxOf(x1, x2)
            val maxY = maxOf(y1, y2)
            val maxZ = maxOf(z1, z2)

            val totalSizeX = maxX - minX + 1
            val totalSizeY = maxY - minY + 1
            val totalSizeZ = maxZ - minZ + 1

            // Calculate grid dimensions
            val gridMaxX = (totalSizeX - 1) / pieceSizeX
            val gridMaxY = (totalSizeY - 1) / pieceSizeY
            val gridMaxZ = (totalSizeZ - 1) / pieceSizeZ

            ConfigManager.debug("[LargeStructureNbtCapture] Capturing large structure: $namespace:$baseName")
            ConfigManager.debug("[LargeStructureNbtCapture] Region: ${totalSizeX}x${totalSizeY}x${totalSizeZ}")
            ConfigManager.debug("[LargeStructureNbtCapture] Piece size: ${pieceSizeX}x${pieceSizeY}x${pieceSizeZ}")
            ConfigManager.debug("[LargeStructureNbtCapture] Grid: ${gridMaxX + 1}x${gridMaxY + 1}x${gridMaxZ + 1} pieces")

            // Validate structure name
            StructureNbtUtils.validateStructureName(baseName)

            // Backup existing large structure (if it exists)
            backupOps.backupLargeStructure(namespace, baseName)

            // Clean up old pieces in write directory
            val largeStructDir = structuresPath.resolve(namespace)
                .resolve(StructureNbtUtils.GENERATED_STRUCTURES_DIR)
                .resolve(StructureNbtUtils.RJS_LARGE_SUBDIR)
                .resolve(baseName)

            if (largeStructDir.exists() && Files.isDirectory(largeStructDir)) {
                // Delete all existing .nbt files in the directory
                Files.list(largeStructDir)
                    .filter { it.extension == "nbt" }
                    .forEach { Files.delete(it) }
                ConfigManager.debug("[LargeStructureNbtCapture] Cleared write directory for: $namespace:$baseName")
            }

            // Capture each piece
            val captureFutures = mutableListOf<CompletableFuture<Void>>()

            for (gridX in 0..gridMaxX) {
                for (gridY in 0..gridMaxY) {
                    for (gridZ in 0..gridMaxZ) {
                        // Calculate piece bounds
                        val pieceMinX = minX + (gridX * pieceSizeX)
                        val pieceMinY = minY + (gridY * pieceSizeY)
                        val pieceMinZ = minZ + (gridZ * pieceSizeZ)

                        val pieceMaxX = minOf(pieceMinX + pieceSizeX - 1, maxX)
                        val pieceMaxY = minOf(pieceMinY + pieceSizeY - 1, maxY)
                        val pieceMaxZ = minOf(pieceMinZ + pieceSizeZ - 1, maxZ)

                        // Create piece name: rjs-large/<name>/X_Y_Z
                        val pieceName = "$namespace:${StructureNbtUtils.RJS_LARGE_SUBDIR}/$baseName/${gridX}_${gridY}_${gridZ}"

                        // Create position values for capture
                        val context = graalContext
                        if (context != null) {
                            context.enter()
                            try {
                                val piecePos1 = context.eval("js", "({x: $pieceMinX, y: $pieceMinY, z: $pieceMinZ, dimension: '$dimension'})")
                                val piecePos2 = context.eval("js", "({x: $pieceMaxX, y: $pieceMaxY, z: $pieceMaxZ, dimension: '$dimension'})")

                                // Capture this piece (delegate to StructureNbtManager, skip backup since we backed up the whole directory)
                                val pieceFuture = StructureNbtManager.capture(piecePos1, piecePos2, pieceName, options, skipBackup = true)

                                // Add to futures list
                                captureFutures.add(pieceFuture)

                            } finally {
                                context.leave()
                            }
                        }
                    }
                }
            }

            // Wait for all pieces to complete
            CompletableFuture.allOf(*captureFutures.toTypedArray()).whenComplete { _, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                // Now update 0_0_0.nbt with requires[] metadata
                try {
                    val originPath = StructureNbtUtils.getStructurePath(
                        structuresPath,
                        namespace,
                        "${StructureNbtUtils.RJS_LARGE_SUBDIR}/$baseName/0_0_0"
                    )

                    if (originPath != null && originPath.exists()) {
                        // Load 0_0_0.nbt
                        val originNBT = NbtIo.readCompressed(originPath, net.minecraft.nbt.NbtAccounter.unlimitedHeap())

                        // Add empty requires array (will be populated by updateLargeStructureMetadata)
                        val metadata = if (originNBT.contains("metadata")) {
                            originNBT.getCompound("metadata")
                        } else {
                            CompoundTag()
                        }

                        val requiresList = ListTag()
                        metadata.put("requires", requiresList)
                        originNBT.put("metadata", metadata)

                        // Write back
                        NbtIo.writeCompressed(originNBT, originPath)

                        ConfigManager.debug("[LargeStructureNbtCapture] Large structure captured: $namespace:$baseName")
                        future.complete(null)
                    } else {
                        future.completeExceptionally(IllegalStateException("Origin piece 0_0_0 not found"))
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
     * Place a large structure (async).
     * Returns Promise<void> after placing all pieces.
     *
     * @param position Position to place structure {x, y, z, dimension?}
     * @param namespace Structure namespace
     * @param baseName Structure base name (without rjs-large prefix)
     * @param options Optional options {rotation?: 0|90|180|270, centered?: boolean, mode?: string}
     */
    fun place(
        position: Value,
        namespace: String,
        baseName: String,
        options: Value?
    ): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        try {
            // Extract position from JS
            val x = position.getMember("x").asInt()
            val y = position.getMember("y").asInt()
            val z = position.getMember("z").asInt()

            // Get dimension
            val dimension = if (options != null && options.hasMember("dimension")) {
                options.getMember("dimension").asString()
            } else if (position.hasMember("dimension")) {
                position.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            // Get rotation (default 0)
            val rotation = if (options != null && options.hasMember("rotation")) {
                options.getMember("rotation").asInt()
            } else {
                0
            }

            // Get centered flag (default false)
            val centered = if (options != null && options.hasMember("centered")) {
                options.getMember("centered").asBoolean()
            } else {
                false
            }

            val mode = if (options != null && options.hasMember("mode")) {
                options.getMember("mode").asString()
            } else {
                "replace"
            }

            // Find all pieces using StructureTemplateManager (searches all resource sources)
            server.execute {
                try {
                    val allTemplates = server.structureManager.listTemplates()
                        .collect(Collectors.toList())

                    ConfigManager.debug("[LargeStructureNbtCapture] === Debugging placeLarge for $namespace:$baseName ===")
                    ConfigManager.debug("[LargeStructureNbtCapture] Looking for paths starting with: ${StructureNbtUtils.RJS_LARGE_SUBDIR}/$baseName/")

                    // Filter for pieces belonging to this large structure
                    val pieceFiles = allTemplates
                        .filter { loc ->
                            loc.namespace == namespace &&
                            loc.path.startsWith("${StructureNbtUtils.RJS_LARGE_SUBDIR}/$baseName/")
                        }
                        .mapNotNull { loc ->
                            // Extract piece name (e.g., "rjs-large/castle/0_0_0" -> "0_0_0")
                            val pathParts = loc.path.split("/")
                            if (pathParts.size >= 3) pathParts[2] else null
                        }
                        .toList()

                    ConfigManager.debug("[LargeStructureNbtCapture] Found ${pieceFiles.size} pieces")

                    if (pieceFiles.isEmpty()) {
                        future.completeExceptionally(IllegalArgumentException("Large structure not found or has no pieces: $namespace:$baseName"))
                        return@execute
                    }

                    continueWithPlacement(namespace, baseName, pieceFiles, x, y, z, dimension, rotation, centered, mode, future)
                } catch (e: Exception) {
                    ConfigManager.debug("[LargeStructureNbtCapture] Exception during piece discovery: ${e.message}")
                    e.printStackTrace()
                    future.completeExceptionally(e)
                }
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Continue with large structure placement after discovering pieces.
     * Separated to handle async discovery from StructureTemplateManager.
     */
    private fun continueWithPlacement(
        namespace: String,
        baseName: String,
        pieceFiles: List<String>,
        x: Int,
        y: Int,
        z: Int,
        dimension: String,
        rotation: Int,
        centered: Boolean,
        mode: String,
        future: CompletableFuture<Void>
    ) {
        try {
            // Load 0_0_0 to get piece size
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

                    // Calculate total size if centered is needed
                    val totalSize = if (centered) {
                        // Find max grid coordinates
                        var maxGridX = 0
                        var maxGridY = 0
                        var maxGridZ = 0

                        pieceFiles.forEach { pieceName ->
                            val parts = pieceName.split("_")
                            if (parts.size == 3) {
                                maxGridX = maxOf(maxGridX, parts[0].toInt())
                                maxGridY = maxOf(maxGridY, parts[1].toInt())
                                maxGridZ = maxOf(maxGridZ, parts[2].toInt())
                            }
                        }

                        // Load max piece to get remainder size
                        val maxPieceLoadFuture = StructureNbtManager.load("$namespace:${StructureNbtUtils.RJS_LARGE_SUBDIR}/$baseName/${maxGridX}_${maxGridY}_${maxGridZ}")
                        val maxPieceSize = maxPieceLoadFuture.get() // Blocking wait - TODO: make this async
                        intArrayOf(
                            maxGridX * pieceSizeX + maxPieceSize.size.x,
                            maxGridY * pieceSizeY + maxPieceSize.size.y,
                            maxGridZ * pieceSizeZ + maxPieceSize.size.z
                        )
                    } else {
                        null
                    }

                    // Calculate base position (apply centering if requested)
                    val baseX = if (centered && totalSize != null) x - totalSize[0] / 2 else x
                    val baseY = y
                    val baseZ = if (centered && totalSize != null) z - totalSize[2] / 2 else z

                    // Create position calculator using RotationHelper
                    val posCalc = com.rhett.rhettjs.world.logic.RotationHelper.createPositionCalculator(
                        startPos = intArrayOf(baseX, baseY, baseZ),
                        rotation = rotation,
                        pieceSize = intArrayOf(pieceSizeX, pieceSizeY, pieceSizeZ)
                    )

                    // Place each piece
                    val placeFutures = mutableListOf<CompletableFuture<Void>>()

                    pieceFiles.forEach { pieceName ->
                        val parts = pieceName.split("_")
                        if (parts.size == 3) {
                            val gridX = parts[0].toInt()
                            val gridY = parts[1].toInt()
                            val gridZ = parts[2].toInt()

                            // Calculate world position for this piece
                            val worldPos = posCalc(intArrayOf(gridX, gridY, gridZ))

                            // Create position value for place()
                            val context = graalContext
                            if (context != null) {
                                context.enter()
                                try {
                                    val piecePos = context.eval("js", "({x: ${worldPos[0]}, y: ${worldPos[1]}, z: ${worldPos[2]}, dimension: '$dimension'})")
                                    val pieceOptions = if (rotation != 0 || mode != "replace") {
                                        val optionsObj = mutableMapOf<String, Any>()
                                        if (rotation != 0) optionsObj["rotation"] = rotation
                                        if (mode != "replace") optionsObj["mode"] = mode
                                        val optionsJson = optionsObj.entries.joinToString(", ") { (k, v) ->
                                            if (v is String) "\"$k\": \"$v\"" else "\"$k\": $v"
                                        }
                                        context.eval("js", "({$optionsJson})")
                                    } else {
                                        null
                                    }

                                    // Place this piece (delegate to StructureNbtManager)
                                    val pieceFuture = StructureNbtManager.place(piecePos, "$namespace:${StructureNbtUtils.RJS_LARGE_SUBDIR}/$baseName/$pieceName", pieceOptions)
                                    placeFutures.add(pieceFuture)

                                } finally {
                                    context.leave()
                                }
                            }
                        }
                    }

                    // Wait for all pieces to be placed
                    CompletableFuture.allOf(*placeFutures.toTypedArray()).whenComplete { _, placeThrowable ->
                        if (placeThrowable != null) {
                            future.completeExceptionally(placeThrowable)
                        } else {
                            ConfigManager.debug("[LargeStructureNbtCapture] Placed large structure: $namespace:$baseName (${pieceFiles.size} pieces, rotation=$rotation)")
                            future.complete(null)
                        }
                    }

                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }
    }
}
