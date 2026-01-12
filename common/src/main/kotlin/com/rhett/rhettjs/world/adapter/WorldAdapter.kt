package com.rhett.rhettjs.world.adapter

import com.rhett.rhettjs.world.models.*
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks

/**
 * Adapter for accessing Minecraft world data.
 * Isolates all Minecraft-specific code and converts to internal models.
 *
 * This is the anti-corruption layer - all Minecraft types stop here.
 * Business logic receives only pure models.
 *
 * Handles chunk loading automatically - chunks are loaded temporarily
 * at structure level (blocks accessible, no simulation/ticking).
 */
class WorldAdapter(private val server: MinecraftServer) {

    // Chunk loader instances per level (created on-demand)
    private val chunkLoaders = mutableMapOf<ServerLevel, ChunkLoader>()

    /**
     * Get a ServerLevel by dimension name.
     * Returns null if dimension doesn't exist.
     */
    fun getLevel(dimensionName: String): ServerLevel? {
        val dimensionKey = when (dimensionName.lowercase()) {
            "overworld", "minecraft:overworld" -> net.minecraft.world.level.Level.OVERWORLD
            "the_nether", "nether", "minecraft:the_nether" -> net.minecraft.world.level.Level.NETHER
            "the_end", "end", "minecraft:the_end" -> net.minecraft.world.level.Level.END
            else -> {
                // Try parsing as resource location
                try {
                    net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse(dimensionName)
                    )
                } catch (e: Exception) {
                    return null
                }
            }
        }

        return server.getLevel(dimensionKey)
    }

    /**
     * Scan a region and return all non-air blocks.
     * Returns pure models, no Minecraft types.
     *
     * Automatically loads required chunks during scan.
     */
    fun getBlocksInRegion(level: ServerLevel, region: Region): List<PositionedBlock> {
        // Get or create chunk loader for this level
        val chunkLoader = chunkLoaders.getOrPut(level) { ChunkLoader(level) }

        // Calculate chunk bounds
        val chunkBounds = chunkLoader.getChunkBounds(
            region.minX, region.maxX,
            region.minZ, region.maxZ
        )

        // Load chunks and scan blocks
        return chunkLoader.withLoadedChunks(
            chunkBounds.minChunkX, chunkBounds.maxChunkX,
            chunkBounds.minChunkZ, chunkBounds.maxChunkZ
        ) {
            scanBlocks(level, region)
        }
    }

    /**
     * Internal: Actually scan blocks (assumes chunks are loaded).
     */
    private fun scanBlocks(level: ServerLevel, region: Region): List<PositionedBlock> {
        val blocks = mutableListOf<PositionedBlock>()

        for (x in region.minX..region.maxX) {
            for (y in region.minY..region.maxY) {
                for (z in region.minZ..region.maxZ) {
                    val pos = BlockPos(x, y, z)
                    val blockState = level.getBlockState(pos)

                    // Skip air blocks
                    if (blockState.`is`(Blocks.AIR)) {
                        continue
                    }

                    // Convert to internal model
                    val blockData = convertBlockState(blockState)

                    // Check for block entity data
                    val blockEntityData = level.getBlockEntity(pos)?.let { blockEntity ->
                        convertBlockEntityToMap(blockEntity)
                    }

                    blocks.add(
                        PositionedBlock(
                            x = x,
                            y = y,
                            z = z,
                            block = blockData,
                            blockEntityData = blockEntityData
                        )
                    )
                }
            }
        }

        return blocks
    }

    /**
     * Get all entities in a region.
     * Returns pure models, no Minecraft types.
     *
     * Automatically loads required chunks during scan.
     */
    fun getEntitiesInRegion(level: ServerLevel, region: Region): List<PositionedEntity> {
        // Get or create chunk loader for this level
        val chunkLoader = chunkLoaders.getOrPut(level) { ChunkLoader(level) }

        // Calculate chunk bounds
        val chunkBounds = chunkLoader.getChunkBounds(
            region.minX, region.maxX,
            region.minZ, region.maxZ
        )

        // Load chunks and scan entities
        return chunkLoader.withLoadedChunks(
            chunkBounds.minChunkX, chunkBounds.maxChunkX,
            chunkBounds.minChunkZ, chunkBounds.maxChunkZ
        ) {
            scanEntities(level, region)
        }
    }

    /**
     * Internal: Actually scan entities (assumes chunks are loaded).
     */
    private fun scanEntities(level: ServerLevel, region: Region): List<PositionedEntity> {
        val entities = mutableListOf<PositionedEntity>()

        // TODO: Implement entity scanning
        // level.getEntities(...) and convert to PositionedEntity models
        // This requires AABB (axis-aligned bounding box) and entity NBT conversion

        return entities
    }

    /**
     * Write blocks to a region in the world.
     * Automatically loads required chunks during write.
     *
     * @param level The world to write to
     * @param blocks List of blocks to place
     * @param updateNeighbors If true, trigger neighbor block updates (default: true)
     * @param mode Placement mode: "replace" (replace all), "keep_air" (skip air in structure), "overlay" (only place in air)
     */
    fun setBlocksInRegion(
        level: ServerLevel,
        blocks: List<PositionedBlock>,
        updateNeighbors: Boolean = true,
        mode: String = "replace"
    ) {
        if (blocks.isEmpty()) return

        // Calculate region bounds from blocks
        val minX = blocks.minOf { it.x }
        val maxX = blocks.maxOf { it.x }
        val minZ = blocks.minOf { it.z }
        val maxZ = blocks.maxOf { it.z }

        // Get or create chunk loader for this level
        val chunkLoader = chunkLoaders.getOrPut(level) { ChunkLoader(level) }

        // Calculate chunk bounds
        val chunkBounds = chunkLoader.getChunkBounds(minX, maxX, minZ, maxZ)

        // Load chunks and write blocks
        chunkLoader.withLoadedChunks(
            chunkBounds.minChunkX, chunkBounds.maxChunkX,
            chunkBounds.minChunkZ, chunkBounds.maxChunkZ
        ) {
            writeBlocks(level, blocks, updateNeighbors, mode)
        }
    }

    /**
     * Internal: Actually write blocks (assumes chunks are loaded).
     */
    private fun writeBlocks(
        level: ServerLevel,
        blocks: List<PositionedBlock>,
        updateNeighbors: Boolean,
        mode: String
    ) {
        blocks.forEach { positioned ->
            val pos = BlockPos(positioned.x, positioned.y, positioned.z)

            // Convert BlockData -> Minecraft BlockState
            val blockState = convertToBlockState(positioned.block)

            // Apply placement mode filtering
            val shouldPlace = when (mode) {
                "keep_air" -> {
                    // Skip if structure block is air (don't replace world blocks with air)
                    !blockState.isAir
                }
                "overlay" -> {
                    // Only place if world block is air (only fill air spaces)
                    val existingState = level.getBlockState(pos)
                    existingState.isAir
                }
                else -> true // "replace" mode - always place
            }

            if (!shouldPlace) return@forEach

            // Place block
            val flags = if (updateNeighbors) {
                // Trigger neighbor updates
                net.minecraft.world.level.block.Block.UPDATE_ALL
            } else {
                // Skip neighbor updates for performance
                net.minecraft.world.level.block.Block.UPDATE_CLIENTS
            }

            level.setBlock(pos, blockState, flags)

            // Handle block entity data if present (always Map, convert to NBT)
            positioned.blockEntityData?.let { nbtData ->
                val blockEntity = level.getBlockEntity(pos)
                if (blockEntity != null) {
                    // Convert Map to CompoundTag (anti-corruption layer enforced)
                    val nbtTag = convertMapToNbt(nbtData)

                    // Load NBT into block entity
                    blockEntity.loadWithComponents(nbtTag, level.registryAccess())
                }
            }
        }
    }

    /**
     * Convert internal BlockData to Minecraft BlockState.
     * Reverse of convertBlockState().
     */
    private fun convertToBlockState(blockData: BlockData): net.minecraft.world.level.block.state.BlockState {
        // Parse block name to get registry key
        val blockId = try {
            net.minecraft.resources.ResourceLocation.parse(blockData.name)
        } catch (e: Exception) {
            // Fallback to stone if invalid
            com.rhett.rhettjs.config.ConfigManager.debug(
                "[WorldAdapter] Invalid block name: ${blockData.name}, using stone"
            )
            net.minecraft.resources.ResourceLocation.parse("minecraft:stone")
        }

        // Get block from registry
        val block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(blockId)
            ?: net.minecraft.world.level.block.Blocks.STONE

        // Start with default state
        var blockState = block.defaultBlockState()

        // Apply properties if present
        blockData.properties.forEach { (propName, propValue) ->
            try {
                // Find property by name
                val property = blockState.properties.firstOrNull { it.name == propName }

                if (property != null) {
                    // Parse value and apply to state
                    @Suppress("UNCHECKED_CAST")
                    property.getValue(propValue).ifPresent { value ->
                        blockState = blockState.setValue(
                            property as net.minecraft.world.level.block.state.properties.Property<Comparable<Any>>,
                            value as Comparable<Any>
                        )
                    }
                }
            } catch (e: Exception) {
                com.rhett.rhettjs.config.ConfigManager.debug(
                    "[WorldAdapter] Failed to apply property $propName=$propValue to ${blockData.name}: ${e.message}"
                )
            }
        }

        return blockState
    }

    /**
     * Convert Map<String, Any> to Minecraft NBT CompoundTag.
     * Reverse of convertNbtToMap().
     */
    private fun convertMapToNbt(map: Map<String, Any>): net.minecraft.nbt.CompoundTag {
        val compound = net.minecraft.nbt.CompoundTag()

        map.forEach { (key, value) ->
            when (value) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    compound.put(key, convertMapToNbt(value as Map<String, Any>))
                }
                is List<*> -> {
                    val list = net.minecraft.nbt.ListTag()
                    value.forEach { item ->
                        when (item) {
                            is Map<*, *> -> {
                                @Suppress("UNCHECKED_CAST")
                                list.add(convertMapToNbt(item as Map<String, Any>))
                            }
                            is Number -> list.add(net.minecraft.nbt.IntTag.valueOf(item.toInt()))
                            is String -> list.add(net.minecraft.nbt.StringTag.valueOf(item))
                            else -> {} // Skip unknown types
                        }
                    }
                    compound.put(key, list)
                }
                is String -> compound.putString(key, value)
                is Int -> compound.putInt(key, value)
                is Long -> compound.putLong(key, value)
                is Float -> compound.putFloat(key, value)
                is Double -> compound.putDouble(key, value)
                is Boolean -> compound.putBoolean(key, value)
                is Byte -> compound.putByte(key, value)
                is Short -> compound.putShort(key, value)
                else -> {} // Skip unknown types
            }
        }

        return compound
    }

    /**
     * List all available structure resources matching a path prefix.
     * Scans all resource sources: generated, datapacks, and mods.
     *
     * @param pathPrefix Path prefix to search (e.g., "structures")
     * @param filter Predicate to filter resources
     * @return List of resource locations as (namespace, path) pairs
     */
    fun listResources(
        pathPrefix: String,
        filter: (namespace: String, path: String) -> Boolean
    ): List<Pair<String, String>> {
        val resourceManager = server.resourceManager

        // Use ResourceManager to find all resources
        val resources = resourceManager.listResources(pathPrefix) { location ->
            filter(location.namespace, location.path)
        }

        // Convert Minecraft ResourceLocation to pure (namespace, path) pairs
        return resources.keys.map { location ->
            Pair(location.namespace, location.path)
        }
    }

    /**
     * Convert Minecraft BlockState to internal BlockData model.
     * Extracts block name and properties.
     */
    private fun convertBlockState(blockState: net.minecraft.world.level.block.state.BlockState): BlockData {
        // Get block registry name (e.g., "minecraft:stone", "terralith:volcanic_rock")
        val blockRegistryName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blockState.block)
        val blockName = if (blockRegistryName != null) {
            blockRegistryName.toString()
        } else {
            "minecraft:air"
        }

        // Extract block state properties
        val properties = mutableMapOf<String, String>()

        // Extract all properties from the block state
        // blockState.values returns Map<Property<?>, Comparable<?>>
        blockState.values.forEach { (property, value) ->
            properties[property.name] = value.toString()
        }

        return BlockData(
            name = blockName,
            properties = properties
        )
    }

    /**
     * Convert BlockEntity to Map<String, Any> for structure NBT.
     * Keeps Minecraft NBT types isolated to adapter layer.
     */
    private fun convertBlockEntityToMap(
        blockEntity: net.minecraft.world.level.block.entity.BlockEntity
    ): Map<String, Any>? {
        try {
            // Get the level's registry access for NBT serialization
            val registryAccess = blockEntity.level?.registryAccess()
                ?: return null

            // Save BlockEntity to NBT CompoundTag
            val nbtTag = blockEntity.saveWithoutMetadata(registryAccess)

            // Convert NBT to Map using our conversion function
            return convertNbtToMap(nbtTag) as? Map<String, Any>
        } catch (e: Exception) {
            // Log and return null if conversion fails
            com.rhett.rhettjs.config.ConfigManager.debug(
                "[WorldAdapter] Failed to convert block entity at ${blockEntity.blockPos}: ${e.message}"
            )
            return null
        }
    }

    /**
     * Convert Minecraft NBT Tag to Map<String, Any>.
     * Mirrors NBTAPI.nbtToJs logic to keep adapter self-contained.
     * Internal for use by WorldManager.
     */
    internal fun convertNbtToMap(tag: net.minecraft.nbt.Tag): Any? {
        return when (tag) {
            is net.minecraft.nbt.CompoundTag -> {
                val map = mutableMapOf<String, Any?>()
                for (key in tag.allKeys) {
                    map[key] = convertNbtToMap(tag.get(key)!!)
                }
                map
            }
            is net.minecraft.nbt.ListTag -> {
                val list = mutableListOf<Any?>()
                for (i in 0 until tag.size) {
                    list.add(convertNbtToMap(tag[i]))
                }
                list
            }
            is net.minecraft.nbt.StringTag -> tag.asString
            is net.minecraft.nbt.IntTag -> tag.asInt
            is net.minecraft.nbt.ByteTag -> tag.asByte.toInt()
            is net.minecraft.nbt.ShortTag -> tag.asShort.toInt()
            is net.minecraft.nbt.LongTag -> tag.asLong
            is net.minecraft.nbt.FloatTag -> tag.asFloat
            is net.minecraft.nbt.DoubleTag -> tag.asDouble
            is net.minecraft.nbt.ByteArrayTag -> tag.asByteArray.toList()
            is net.minecraft.nbt.IntArrayTag -> tag.asIntArray.toList()
            is net.minecraft.nbt.LongArrayTag -> tag.asLongArray.toList()
            is net.minecraft.nbt.EndTag -> null
            else -> tag.asString
        }
    }
}
