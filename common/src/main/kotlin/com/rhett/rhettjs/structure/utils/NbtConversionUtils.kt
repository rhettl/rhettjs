package com.rhett.rhettjs.structure.utils

import com.rhett.rhettjs.structure.models.StructureData
import com.rhett.rhettjs.structure.models.StructureSize
import com.rhett.rhettjs.world.models.BlockData
import com.rhett.rhettjs.world.models.PositionedBlock
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag

/**
 * Utilities for converting between Minecraft NBT format and internal models.
 * This is part of the anti-corruption layer that shields business logic from Minecraft types.
 */
object NbtConversionUtils {

    /**
     * Parse structure data from Minecraft NBT format.
     * Converts NBT → StructureData (anti-corruption shield).
     */
    fun parseStructureNBT(nbt: CompoundTag): StructureData {
        // Read size (handle both int array and list formats)
        val size = if (nbt.contains("size", 11)) {
            // TAG_INT_ARRAY (type 11) - our write format
            val sizeList = nbt.getIntArray("size")
            StructureSize(
                x = sizeList[0],
                y = sizeList[1],
                z = sizeList[2]
            )
        } else if (nbt.contains("size", 9)) {
            // TAG_LIST (type 9) - StructureTemplate.save() format
            val sizeList = nbt.getList("size", 3) // 3 = TAG_INT
            if (sizeList.size < 3) {
                throw IllegalArgumentException("Structure NBT 'size' list has ${sizeList.size} elements, expected 3")
            }
            StructureSize(
                x = sizeList.getInt(0),
                y = sizeList.getInt(1),
                z = sizeList.getInt(2)
            )
        } else {
            throw IllegalArgumentException("Structure NBT missing 'size' field")
        }

        // Read palette (block states)
        val paletteList = nbt.getList("palette", 10) // 10 = CompoundTag
        val palette = mutableListOf<BlockData>()

        for (i in 0 until paletteList.size) {
            val blockNBT = paletteList.getCompound(i)
            val blockId = blockNBT.getString("Name")

            val properties = mutableMapOf<String, String>()
            if (blockNBT.contains("Properties")) {
                val propsNBT = blockNBT.getCompound("Properties")
                propsNBT.allKeys.forEach { key ->
                    properties[key] = propsNBT.getString(key)
                }
            }

            palette.add(BlockData(name = blockId, properties = properties))
        }

        // Read blocks (positions + palette indices)
        val blocksList = nbt.getList("blocks", 10) // 10 = CompoundTag
        val blocks = mutableListOf<PositionedBlock>()

        for (i in 0 until blocksList.size) {
            val blockNBT = blocksList.getCompound(i)

            // Read position (handle both int array and list formats)
            val (posX, posY, posZ) = if (blockNBT.contains("pos", 11)) {
                // TAG_INT_ARRAY (type 11) - our write format
                val pos = blockNBT.getIntArray("pos")
                Triple(pos[0], pos[1], pos[2])
            } else if (blockNBT.contains("pos", 9)) {
                // TAG_LIST (type 9) - StructureTemplate.save() format
                val posList = blockNBT.getList("pos", 3) // 3 = TAG_INT
                Triple(posList.getInt(0), posList.getInt(1), posList.getInt(2))
            } else {
                continue // Skip blocks without valid position
            }

            val state = blockNBT.getInt("state")

            val blockData = palette.getOrNull(state)
            if (blockData != null) {
                // Convert block entity NBT to Map (anti-corruption layer)
                val blockEntityData = if (blockNBT.contains("nbt")) {
                    val nbtTag = blockNBT.getCompound("nbt")
                    convertNbtTagToMap(nbtTag) as? Map<String, Any>
                } else {
                    null
                }

                blocks.add(
                    PositionedBlock(
                        x = posX,
                        y = posY,
                        z = posZ,
                        block = blockData,
                        blockEntityData = blockEntityData
                    )
                )
            }
        }

        // Read entities
        val entities = mutableListOf<com.rhett.rhettjs.structure.models.StructureEntity>()
        if (nbt.contains("entities", 9)) { // 9 = TAG_LIST
            val entitiesList = nbt.getList("entities", 10) // 10 = CompoundTag

            for (i in 0 until entitiesList.size) {
                val entityNBT = entitiesList.getCompound(i)

                // Read blockPos (int array or list)
                val blockPos = if (entityNBT.contains("blockPos", 11)) {
                    // TAG_INT_ARRAY (type 11)
                    val arr = entityNBT.getIntArray("blockPos")
                    listOf(arr[0], arr[1], arr[2])
                } else if (entityNBT.contains("blockPos", 9)) {
                    // TAG_LIST (type 9)
                    val list = entityNBT.getList("blockPos", 3) // 3 = TAG_INT
                    listOf(list.getInt(0), list.getInt(1), list.getInt(2))
                } else {
                    continue // Skip entities without blockPos
                }

                // Read pos (double list)
                val pos = if (entityNBT.contains("pos", 9)) {
                    val posList = entityNBT.getList("pos", 6) // 6 = TAG_DOUBLE
                    listOf(posList.getDouble(0), posList.getDouble(1), posList.getDouble(2))
                } else {
                    continue // Skip entities without pos
                }

                // Read entity NBT data
                val entityData = if (entityNBT.contains("nbt")) {
                    entityNBT.getCompound("nbt")
                } else {
                    CompoundTag() // Empty NBT if not present
                }

                entities.add(
                    com.rhett.rhettjs.structure.models.StructureEntity(
                        blockPos = blockPos,
                        pos = pos,
                        nbt = entityData
                    )
                )
            }
        }

        // Read metadata (custom)
        val metadata = mutableMapOf<String, String>()
        if (nbt.contains("metadata")) {
            val metaNBT = nbt.getCompound("metadata")
            metaNBT.allKeys.forEach { key ->
                metadata[key] = metaNBT.getString(key)
            }
        }

        return StructureData(
            size = size,
            blocks = blocks,
            entities = entities,
            metadata = metadata
        )
    }

    /**
     * Create Minecraft NBT structure format from StructureData.
     * Converts StructureData → NBT (anti-corruption shield).
     */
    fun createStructureNBT(data: StructureData): CompoundTag {
        val nbt = CompoundTag()

        // Write size
        nbt.putIntArray("size", intArrayOf(data.size.x, data.size.y, data.size.z))

        // Build palette (unique block states)
        val palette = mutableListOf<BlockData>()
        val paletteIndices = mutableMapOf<BlockData, Int>()

        data.blocks.forEach { block ->
            if (block.block !in paletteIndices) {
                paletteIndices[block.block] = palette.size
                palette.add(block.block)
            }
        }

        // Write palette
        val paletteList = ListTag()
        palette.forEach { blockData ->
            val blockNBT = CompoundTag()
            blockNBT.putString("Name", blockData.name)

            if (blockData.properties.isNotEmpty()) {
                val propsNBT = CompoundTag()
                blockData.properties.forEach { (key, value) ->
                    propsNBT.putString(key, value)
                }
                blockNBT.put("Properties", propsNBT)
            }

            paletteList.add(blockNBT)
        }
        nbt.put("palette", paletteList)

        // Write blocks
        val blocksList = ListTag()
        data.blocks.forEach { block ->
            val blockNBT = CompoundTag()
            blockNBT.putIntArray("pos", intArrayOf(block.x, block.y, block.z))
            blockNBT.putInt("state", paletteIndices[block.block] ?: 0)

            // Write block entity data if present (convert Map to NBT)
            if (block.blockEntityData != null) {
                blockNBT.put("nbt", convertMapToNbtTag(block.blockEntityData))
            }

            blocksList.add(blockNBT)
        }
        nbt.put("blocks", blocksList)

        // Write entities
        if (data.entities.isNotEmpty()) {
            val entitiesList = ListTag()
            data.entities.forEach { entity ->
                val entityNBT = CompoundTag()

                // Write blockPos as int array
                entityNBT.putIntArray("blockPos", intArrayOf(
                    entity.blockPos[0],
                    entity.blockPos[1],
                    entity.blockPos[2]
                ))

                // Write pos as double list
                val posList = net.minecraft.nbt.ListTag()
                posList.add(net.minecraft.nbt.DoubleTag.valueOf(entity.pos[0]))
                posList.add(net.minecraft.nbt.DoubleTag.valueOf(entity.pos[1]))
                posList.add(net.minecraft.nbt.DoubleTag.valueOf(entity.pos[2]))
                entityNBT.put("pos", posList)

                // Write entity NBT data
                entityNBT.put("nbt", entity.nbt)

                entitiesList.add(entityNBT)
            }
            nbt.put("entities", entitiesList)
        }

        // Write metadata
        if (data.metadata.isNotEmpty()) {
            val metaNBT = CompoundTag()
            data.metadata.forEach { (key, value) ->
                metaNBT.putString(key, value)
            }
            nbt.put("metadata", metaNBT)
        }

        // Write data version (current MC version)
        nbt.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().dataVersion.version)

        return nbt
    }

    /**
     * Convert StructureData to JS-friendly Map format.
     * This format can be passed to NBTAPI.write() which will convert it to NBT.
     */
    fun structureDataToJsMap(data: StructureData): Map<String, Any> {
        val map = mutableMapOf<String, Any>()

        // Write size
        map["size"] = listOf(data.size.x, data.size.y, data.size.z)

        // Build palette (unique block states)
        val palette = mutableListOf<BlockData>()
        val paletteIndices = mutableMapOf<BlockData, Int>()

        data.blocks.forEach { block ->
            if (block.block !in paletteIndices) {
                paletteIndices[block.block] = palette.size
                palette.add(block.block)
            }
        }

        // Write palette
        val paletteList = mutableListOf<Map<String, Any>>()
        palette.forEach { blockData ->
            val blockMap = mutableMapOf<String, Any>()
            blockMap["Name"] = blockData.name

            if (blockData.properties.isNotEmpty()) {
                blockMap["Properties"] = blockData.properties.toMap()
            }

            paletteList.add(blockMap)
        }
        map["palette"] = paletteList

        // Write blocks
        val blocksList = mutableListOf<Map<String, Any>>()
        data.blocks.forEach { block ->
            val blockMap = mutableMapOf<String, Any>()
            blockMap["pos"] = listOf(block.x, block.y, block.z)
            blockMap["state"] = paletteIndices[block.block] ?: 0

            // Write block entity data if present
            // Note: NBTAPI will handle CompoundTag → Map conversion if needed
            if (block.blockEntityData != null) {
                blockMap["nbt"] = block.blockEntityData as Any
            }

            blocksList.add(blockMap)
        }
        map["blocks"] = blocksList

        // Write entities
        if (data.entities.isNotEmpty()) {
            val entitiesList = mutableListOf<Map<String, Any>>()
            data.entities.forEach { entity ->
                val entityMap = mutableMapOf<String, Any>()
                entityMap["blockPos"] = entity.blockPos
                entityMap["pos"] = entity.pos
                entityMap["nbt"] = entity.nbt
                entitiesList.add(entityMap)
            }
            map["entities"] = entitiesList
        }

        // Write metadata
        if (data.metadata.isNotEmpty()) {
            map["metadata"] = data.metadata.toMap()
        }

        // Write data version (current MC version)
        map["DataVersion"] = net.minecraft.SharedConstants.getCurrentVersion().dataVersion.version

        return map
    }

    /**
     * Convert Minecraft NBT Tag to Map<String, Any> (anti-corruption layer).
     * Mirrors WorldAdapter.convertNbtToMap() to avoid coupling.
     */
    fun convertNbtTagToMap(tag: net.minecraft.nbt.Tag): Any? {
        return when (tag) {
            is net.minecraft.nbt.CompoundTag -> {
                val map = mutableMapOf<String, Any?>()
                for (key in tag.allKeys) {
                    map[key] = convertNbtTagToMap(tag.get(key)!!)
                }
                map
            }
            is net.minecraft.nbt.ListTag -> {
                val list = mutableListOf<Any?>()
                for (i in 0 until tag.size) {
                    list.add(convertNbtTagToMap(tag[i]))
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

    /**
     * Convert Map<String, Any> to Minecraft NBT CompoundTag (anti-corruption layer).
     * Mirrors WorldAdapter.convertMapToNbt() to avoid coupling.
     */
    fun convertMapToNbtTag(map: Map<String, Any>): net.minecraft.nbt.CompoundTag {
        val compound = net.minecraft.nbt.CompoundTag()

        map.forEach { (key, value) ->
            when (value) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    compound.put(key, convertMapToNbtTag(value as Map<String, Any>))
                }
                is List<*> -> {
                    val list = net.minecraft.nbt.ListTag()
                    value.forEach { item ->
                        when (item) {
                            is Map<*, *> -> {
                                @Suppress("UNCHECKED_CAST")
                                list.add(convertMapToNbtTag(item as Map<String, Any>))
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
}
