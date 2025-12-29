package com.rhett.rhettjs.events

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

/**
 * Adapter layer that converts Minecraft types to our internal event models.
 * This isolates the business logic from Minecraft's API changes.
 *
 * Following anti-corruption layer pattern from Domain-Driven Design.
 */
object BlockEventAdapter {

    /**
     * Convert Minecraft BlockPos to our BlockPosition model.
     */
    fun toBlockPosition(pos: BlockPos, level: Level): BlockPosition {
        val dimensionKey = level.dimension().location().toString()
        return BlockPosition(
            x = pos.x,
            y = pos.y,
            z = pos.z,
            dimension = dimensionKey
        )
    }

    /**
     * Convert Minecraft Player to our PlayerData model.
     */
    fun toPlayerData(player: Player): PlayerData {
        return PlayerData(
            name = player.name.string,
            uuid = player.stringUUID,
            isCreative = player.abilities.instabuild
        )
    }

    /**
     * Convert Minecraft BlockState to our BlockData model.
     */
    fun toBlockData(state: BlockState): BlockData {
        val blockId = BuiltInRegistries.BLOCK.getKey(state.block).toString()
        val properties = state.values.mapKeys { it.key.name }
            .mapValues { it.value.toString() }

        return BlockData(
            id = blockId,
            properties = properties
        )
    }

    /**
     * Convert Minecraft ItemStack to our ItemData model.
     */
    fun toItemData(stack: ItemStack): ItemData? {
        if (stack.isEmpty) return null

        val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        val displayName = try {
            stack.hoverName.string
        } catch (e: Exception) {
            null
        }

        // Extract component data (1.20.5+ uses Data Components instead of NBT)
        // We serialize components to a map for JavaScript access
        val componentMap = try {
            extractComponentData(stack)
        } catch (e: Exception) {
            null
        }

        return ItemData(
            id = itemId,
            count = stack.count,
            displayName = displayName,
            nbt = componentMap
        )
    }

    /**
     * Extract component data from ItemStack as a Map.
     * In Minecraft 1.20.5+, items use Data Components instead of NBT.
     * We extract relevant component data and present it in a map format.
     */
    private fun extractComponentData(stack: ItemStack): Map<String, Any?>? {
        if (stack.components.isEmpty) return null

        val dataMap = mutableMapOf<String, Any?>()

        // Iterate through all components and try to serialize them
        for (entry in stack.components) {
            try {
                val type = entry.type
                val value = entry.value
                val key = type.toString().substringAfter("minecraft:")
                dataMap[key] = value?.toString() ?: "null"
            } catch (e: Exception) {
                // Skip components that can't be serialized
            }
        }

        return if (dataMap.isNotEmpty()) dataMap else null
    }

    /**
     * Convert Minecraft Direction to our BlockFace enum.
     */
    fun toBlockFace(direction: Direction?): BlockFace? {
        return when (direction) {
            Direction.DOWN -> BlockFace.DOWN
            Direction.UP -> BlockFace.UP
            Direction.NORTH -> BlockFace.NORTH
            Direction.SOUTH -> BlockFace.SOUTH
            Direction.WEST -> BlockFace.WEST
            Direction.EAST -> BlockFace.EAST
            null -> null
        }
    }

    /**
     * Create a Click event from Minecraft event data.
     */
    fun createClickEvent(
        pos: BlockPos,
        level: Level,
        player: Player,
        item: ItemStack?,
        face: Direction?,
        isRightClick: Boolean
    ): BlockEventData.Click {
        val state = level.getBlockState(pos)

        return BlockEventData.Click(
            position = toBlockPosition(pos, level),
            block = toBlockData(state),
            player = toPlayerData(player),
            item = item?.let { toItemData(it) },
            face = toBlockFace(face),
            isRightClick = isRightClick
        )
    }

    /**
     * Create a Placed event from Minecraft event data.
     */
    fun createPlacedEvent(
        pos: BlockPos,
        level: Level,
        player: Player,
        placedAgainst: BlockPos,
        face: Direction?,
        item: ItemStack?
    ): BlockEventData.Placed {
        val state = level.getBlockState(pos)

        return BlockEventData.Placed(
            position = toBlockPosition(pos, level),
            block = toBlockData(state),
            player = toPlayerData(player),
            placedAgainst = toBlockPosition(placedAgainst, level),
            face = toBlockFace(face),
            item = item?.let { toItemData(it) }
        )
    }

    /**
     * Create a Broken event from Minecraft event data.
     */
    fun createBrokenEvent(
        pos: BlockPos,
        level: Level,
        player: Player,
        state: BlockState,
        drops: List<ItemStack>,
        experience: Int
    ): BlockEventData.Broken {
        return BlockEventData.Broken(
            position = toBlockPosition(pos, level),
            block = toBlockData(state),
            player = toPlayerData(player),
            drops = drops.mapNotNull { toItemData(it) },
            experience = experience
        )
    }
}
