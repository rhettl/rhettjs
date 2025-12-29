package com.rhett.rhettjs.events

/**
 * Position data for block events.
 * Pure data class isolated from Minecraft types.
 */
data class BlockPosition(
    val x: Int,
    val y: Int,
    val z: Int,
    val dimension: String
)

/**
 * Player data for block events.
 * Minimal interface to avoid coupling to Minecraft's Player type.
 */
data class PlayerData(
    val name: String,
    val uuid: String,
    val isCreative: Boolean
)

/**
 * Block state data.
 * Represents a block type and its properties.
 */
data class BlockData(
    val id: String,
    val properties: Map<String, String> = emptyMap()
)

/**
 * Item stack data for events.
 */
data class ItemData(
    val id: String,
    val count: Int,
    val displayName: String?,
    val nbt: Map<String, Any?>?
)

/**
 * Face/direction enum for block interactions.
 */
enum class BlockFace {
    DOWN, UP, NORTH, SOUTH, WEST, EAST
}

/**
 * Base event data for all block events.
 * Contains common properties shared across block event types.
 */
sealed class BlockEventData {
    abstract val position: BlockPosition
    abstract val block: BlockData
    abstract val player: PlayerData?

    /**
     * Event data for block click events (left or right click).
     */
    data class Click(
        override val position: BlockPosition,
        override val block: BlockData,
        override val player: PlayerData,
        val item: ItemData?,
        val face: BlockFace?,
        val isRightClick: Boolean
    ) : BlockEventData()

    /**
     * Event data for block placement events.
     */
    data class Placed(
        override val position: BlockPosition,
        override val block: BlockData,
        override val player: PlayerData,
        val placedAgainst: BlockPosition,
        val face: BlockFace?,
        val item: ItemData?
    ) : BlockEventData()

    /**
     * Event data for block breaking events.
     */
    data class Broken(
        override val position: BlockPosition,
        override val block: BlockData,
        override val player: PlayerData,
        val drops: List<ItemData>,
        val experience: Int
    ) : BlockEventData()
}