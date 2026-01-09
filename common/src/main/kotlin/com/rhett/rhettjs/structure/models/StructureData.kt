package com.rhett.rhettjs.structure.models

import com.rhett.rhettjs.world.models.PositionedBlock
import net.minecraft.nbt.CompoundTag

/**
 * Internal model representing a captured structure.
 * This is the anti-corruption layer between Minecraft NBT structures and JavaScript.
 */
data class StructureData(
    /**
     * Size of the structure (width, height, depth)
     */
    val size: StructureSize,

    /**
     * List of blocks in the structure (relative coordinates)
     */
    val blocks: List<PositionedBlock>,

    /**
     * List of entities in the structure (relative coordinates)
     */
    val entities: List<StructureEntity> = emptyList(),

    /**
     * Structure metadata (author, description, etc.)
     */
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Structure dimensions
 */
data class StructureSize(
    val x: Int,
    val y: Int,
    val z: Int
)

/**
 * Entity in a structure with position and NBT data.
 * Represents entities stored in structure templates (paintings, armor stands, etc.)
 */
data class StructureEntity(
    /**
     * Block-relative position [x, y, z] as integers
     */
    val blockPos: List<Int>,

    /**
     * Entity position [x, y, z] as doubles (precise position)
     */
    val pos: List<Double>,

    /**
     * Entity NBT data (type, properties, etc.)
     */
    val nbt: CompoundTag
)
