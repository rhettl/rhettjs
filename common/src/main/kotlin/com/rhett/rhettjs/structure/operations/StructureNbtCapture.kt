package com.rhett.rhettjs.structure.operations

import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.structure.models.StructureData
import com.rhett.rhettjs.structure.models.StructureSize
import com.rhett.rhettjs.structure.utils.NbtConversionUtils
import com.rhett.rhettjs.world.models.BlockData
import com.rhett.rhettjs.world.models.PositionedBlock
import net.minecraft.server.MinecraftServer
import org.graalvm.polyglot.Value
import java.util.concurrent.CompletableFuture

/**
 * Capture and placement operations for structure NBT files.
 * Handles converting between world blocks and structure data.
 */
class StructureNbtCapture(
    private val server: MinecraftServer,
    private val coreOps: StructureNbtCore
) {

    /**
     * Capture a region and convert to structure data (async).
     * Returns Promise<void> after saving structure file.
     *
     * @param pos1 First corner position {x, y, z, dimension?}
     * @param pos2 Second corner position {x, y, z, dimension?}
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param options Optional options {author?: string, description?: string}
     * @param skipBackup If true, skip automatic backup (used for large structure pieces)
     */
    fun capture(
        pos1: Value,
        pos2: Value,
        nameWithNamespace: String,
        options: Value?,
        skipBackup: Boolean = false
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

            // Get dimension from pos1 unless options.dimension is specified
            val dimension = if (options != null && options.hasMember("dimension")) {
                options.getMember("dimension").asString()
            } else if (pos1.hasMember("dimension")) {
                pos1.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            // Extract metadata from options
            val metadata = mutableMapOf<String, String>()
            if (options != null) {
                if (options.hasMember("author")) {
                    metadata["author"] = options.getMember("author").asString()
                }
                if (options.hasMember("description")) {
                    metadata["description"] = options.getMember("description").asString()
                }
            }

            // Execute capture on main thread
            server.execute {
                try {
                    val worldAdapter = com.rhett.rhettjs.world.WorldManager
                    val level = worldAdapter.getLevel(dimension)

                    if (level == null) {
                        future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dimension"))
                        return@execute
                    }

                    // Normalize coordinates (min to max)
                    val minX = minOf(x1, x2)
                    val minY = minOf(y1, y2)
                    val minZ = minOf(z1, z2)
                    val maxX = maxOf(x1, x2)
                    val maxY = maxOf(y1, y2)
                    val maxZ = maxOf(z1, z2)

                    val sizeX = maxX - minX + 1
                    val sizeY = maxY - minY + 1
                    val sizeZ = maxZ - minZ + 1

                    // Capture blocks in the region
                    val blocks = mutableListOf<PositionedBlock>()

                    for (x in minX..maxX) {
                        for (y in minY..maxY) {
                            for (z in minZ..maxZ) {
                                val blockPos = net.minecraft.core.BlockPos(x, y, z)
                                val blockState = level.getBlockState(blockPos)

                                // Convert to BlockData model
                                val blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                    .getKey(blockState.block)?.toString() ?: "minecraft:air"

                                val properties = mutableMapOf<String, String>()
                                blockState.values.forEach { (property, value) ->
                                    properties[property.name] = value.toString()
                                }

                                // Get block entity data if present
                                val blockEntity = level.getBlockEntity(blockPos)
                                val blockEntityData = if (blockEntity != null) {
                                    val nbtTag = blockEntity.saveWithoutMetadata(level.registryAccess())
                                    @Suppress("UNCHECKED_CAST")
                                    NbtConversionUtils.convertNbtTagToMap(nbtTag) as? Map<String, Any>
                                } else {
                                    null
                                }

                                // Store as relative position
                                blocks.add(
                                    PositionedBlock(
                                        x = x - minX,
                                        y = y - minY,
                                        z = z - minZ,
                                        block = BlockData(name = blockId, properties = properties),
                                        blockEntityData = blockEntityData
                                    )
                                )
                            }
                        }
                    }

                    // Create structure data
                    val structureData = StructureData(
                        size = StructureSize(sizeX, sizeY, sizeZ),
                        blocks = blocks,
                        metadata = metadata
                    )

                    // Save structure to file
                    val saveFuture = coreOps.save(nameWithNamespace, structureData, skipBackup)
                    saveFuture.whenComplete { _, throwable ->
                        if (throwable != null) {
                            future.completeExceptionally(throwable)
                        } else {
                            ConfigManager.debug("[StructureNbtCapture] Captured structure: $nameWithNamespace (${blocks.size} blocks, backup=${!skipBackup})")
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

        return future
    }

    /**
     * Place a structure at a position (async).
     * Returns Promise<void> after placing all blocks.
     *
     * @param position Position to place structure {x, y, z, dimension?}
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param options Optional options {rotation?: 0|90|180|270, centered?: boolean}
     */
    fun place(position: Value, nameWithNamespace: String, options: Value?): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        try {
            // Extract position from JS
            val x = position.getMember("x").asInt()
            val y = position.getMember("y").asInt()
            val z = position.getMember("z").asInt()

            // Get dimension from position unless options.dimension is specified
            val dimension = if (options != null && options.hasMember("dimension")) {
                options.getMember("dimension").asString()
            } else if (position.hasMember("dimension")) {
                position.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            // Extract options
            val rotation = if (options != null && options.hasMember("rotation")) {
                options.getMember("rotation").asInt()
            } else {
                0
            }

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

            // Load structure first
            val loadFuture = coreOps.load(nameWithNamespace)

            loadFuture.whenComplete { structureData, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                // Execute placement on main thread
                server.execute {
                    try {
                        val worldAdapter = com.rhett.rhettjs.world.WorldManager
                        val level = worldAdapter.getLevel(dimension)

                        if (level == null) {
                            future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dimension"))
                            return@execute
                        }

                        // Calculate base position (apply centering if requested)
                        val baseX = if (centered) x - structureData.size.x / 2 else x
                        val baseY = y
                        val baseZ = if (centered) z - structureData.size.z / 2 else z

                        // Apply rotation to blocks if needed
                        val rotatedBlocks = if (rotation == 0) {
                            // No rotation - use blocks as-is with base offset
                            structureData.blocks.map { block ->
                                PositionedBlock(
                                    x = baseX + block.x,
                                    y = baseY + block.y,
                                    z = baseZ + block.z,
                                    block = block.block,
                                    blockEntityData = block.blockEntityData
                                )
                            }
                        } else {
                            // Apply rotation using RotationHelper
                            val rotationHelper = com.rhett.rhettjs.world.logic.RotationHelper

                            // For single structure placement, we don't use grid positioning
                            // We rotate each block around the structure's origin (0,0,0)
                            val blockStateRotator = rotationHelper.createBlockStateRotator(rotation)

                            structureData.blocks.map { block ->
                                // Rotate position manually for single-block rotation
                                val (rotatedX, rotatedZ) = when (rotation) {
                                    90 -> Pair(-block.z, block.x)
                                    180 -> Pair(-block.x, -block.z)
                                    270 -> Pair(block.z, -block.x)
                                    else -> Pair(block.x, block.z)
                                }

                                // Rotate block state properties
                                val rotatedBlockData = blockStateRotator(block.block)

                                PositionedBlock(
                                    x = baseX + rotatedX,
                                    y = baseY + block.y,
                                    z = baseZ + rotatedZ,
                                    block = rotatedBlockData,
                                    blockEntityData = block.blockEntityData
                                )
                            }
                        }

                        // Place blocks using world adapter
                        val adapter = com.rhett.rhettjs.world.adapter.WorldAdapter(server)
                        adapter.setBlocksInRegion(level, rotatedBlocks, updateNeighbors = true, mode = mode)

                        ConfigManager.debug("[StructureNbtCapture] Placed structure: $nameWithNamespace (${rotatedBlocks.size} blocks, rotation=$rotation, mode=$mode)")
                        future.complete(null)

                    } catch (e: Exception) {
                        future.completeExceptionally(e)
                    }
                }
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }
}
