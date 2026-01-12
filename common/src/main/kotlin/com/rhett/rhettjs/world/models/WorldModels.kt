package com.rhett.rhettjs.world.models

/**
 * Internal models for world data.
 * These are pure data types with no dependencies on Minecraft types.
 * Shields business logic from external framework changes.
 */

/**
 * 3D region defined by two corners.
 * Coordinates are normalized (min/max).
 */
data class Region(
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int
) {
    val sizeX: Int get() = maxX - minX + 1
    val sizeY: Int get() = maxY - minY + 1
    val sizeZ: Int get() = maxZ - minZ + 1

    companion object {
        /**
         * Create a region from two arbitrary corners.
         * Automatically normalizes to min/max.
         */
        fun fromCorners(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int): Region {
            return Region(
                minX = minOf(x1, x2),
                minY = minOf(y1, y2),
                minZ = minOf(z1, z2),
                maxX = maxOf(x1, x2),
                maxY = maxOf(y1, y2),
                maxZ = maxOf(z1, z2)
            )
        }
    }
}

/**
 * Block data without Minecraft types.
 * Pure representation of a block's identity and state.
 */
data class BlockData(
    val name: String,  // e.g., "minecraft:stone"
    val properties: Map<String, String> = emptyMap()  // e.g., {"facing": "north"}
)

/**
 * Block with its world position.
 * blockEntityData is always pure Map - no Minecraft types (anti-corruption layer).
 */
data class PositionedBlock(
    val x: Int,
    val y: Int,
    val z: Int,
    val block: BlockData,
    val blockEntityData: Map<String, Any>? = null
)

/**
 * Entity with its world position.
 */
data class PositionedEntity(
    val x: Double,
    val y: Double,
    val z: Double,
    val blockX: Int,
    val blockY: Int,
    val blockZ: Int,
    val entityData: Map<String, Any>  // NBT data including entity ID
)

/**
 * Grid coordinate for large structure pieces.
 * Y coordinate reserved for future vertical splitting (currently always 0).
 */
data class GridCoordinate(
    val x: Int,
    val y: Int = 0,
    val z: Int
) {
    fun toFilename(): String = "$x.$y.$z.nbt"
}

/**
 * Metadata for a large structure.
 */
data class LargeStructureMetadata(
    val name: String,
    val pieceSizeX: Int,
    val pieceSizeZ: Int,
    val pieceSizeY: Int? = null,  // Optional Y splitting (null = no vertical split)
    val gridSizeX: Int,
    val gridSizeZ: Int,
    val gridSizeY: Int = 1,  // Defaults to 1 (no vertical splitting)
    val totalSizeX: Int,
    val totalSizeY: Int,
    val totalSizeZ: Int,
    val requiredMods: List<String> = emptyList()
) {
    fun toJson(): Map<String, Any> {
        val pieceSizeMap = mutableMapOf(
            "x" to pieceSizeX,
            "z" to pieceSizeZ
        )
        if (pieceSizeY != null) {
            pieceSizeMap["y"] = pieceSizeY
        }

        val gridDescription = if (gridSizeY > 1) {
            "${gridSizeX}x${gridSizeY}x${gridSizeZ} grid"
        } else {
            "${gridSizeX}x${gridSizeZ} grid"
        }

        return mapOf(
            "description" to "Large structure: ${totalSizeX}x${totalSizeY}x${totalSizeZ} ($gridDescription)",
            "piece_size" to pieceSizeMap,
            "requires" to requiredMods
        )
    }
}
