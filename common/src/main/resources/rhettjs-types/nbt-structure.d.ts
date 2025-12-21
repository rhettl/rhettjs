// Minecraft 1.21.1 NBT Structure Format
// Based on: https://minecraft.wiki/w/Structure_file
// This file defines the official Minecraft structure NBT format.
// It is version-specific and should be updated when targeting new Minecraft versions.

/**
 * Block state definition in the palette.
 * Represents a unique block type and its properties.
 */
interface BlockState {
    /**
     * Block identifier (e.g., "minecraft:stone", "minecraft:oak_stairs")
     */
    Name: string;
    
    /**
     * Block state properties (e.g., {"facing": "north", "half": "bottom"})
     * Only present if the block has properties different from defaults.
     */
    Properties?: { [key: string]: string };
}

/**
 * Individual block placement within the structure.
 * References a palette entry and specifies position.
 */
interface BlockPlacement {
    /**
     * Index into the palette array.
     * Determines which block type is placed at this position.
     */
    state: number;
    
    /**
     * Position within the structure [x, y, z].
     * Coordinates are relative to structure origin (0,0,0).
     */
    pos: [number, number, number];
    
    /**
     * Block entity NBT data (optional).
     * Present for blocks like chests, signs, spawners, etc.
     * Contains block-specific data (e.g., chest inventory, sign text).
     */
    nbt?: { [key: string]: any };
}

/**
 * Entity within the structure.
 * Includes both exact position and block-aligned position.
 */
interface StructureEntity {
    /**
     * Exact entity position as doubles [x, y, z].
     * Can have fractional coordinates for precise placement.
     */
    pos: [number, number, number];
    
    /**
     * Block-aligned position as integers [x, y, z].
     * Used for block-relative positioning.
     */
    blockPos: [number, number, number];
    
    /**
     * Entity NBT data.
     * Must include 'id' field with entity type.
     */
    nbt: {
        /**
         * Entity identifier (e.g., "minecraft:painting", "minecraft:armor_stand")
         */
        id: string;
        
        /**
         * Entity-specific NBT data.
         * Varies by entity type (e.g., painting variant, armor stand pose).
         */
        [key: string]: any;
    };
}

/**
 * Complete Minecraft structure file format (1.21.1).
 * Represents the NBT structure saved by structure blocks.
 * 
 * @example Reading a structure
 * ```javascript
 * const data = Structure.read("village/houses/house_1");
 * console.log(`Size: ${data.size[0]}x${data.size[1]}x${data.size[2]}`);
 * console.log(`Blocks: ${data.blocks.length}`);
 * console.log(`Palette entries: ${data.palette.length}`);
 * ```
 * 
 * @example Modifying blocks
 * ```javascript
 * const data = Structure.read("my_structure");
 * // Change all stone to diamond blocks
 * data.palette.forEach((state, index) => {
 *     if (state.Name === "minecraft:stone") {
 *         data.palette[index] = { Name: "minecraft:diamond_block" };
 *     }
 * });
 * Structure.write("my_structure", data);
 * ```
 */
interface StructureData {
    /**
     * Minecraft data version number.
     * Used for data migration between versions.
     * For 1.21.1, this is typically 3953+.
     */
    DataVersion: number;
    
    /**
     * Structure dimensions [x, y, z].
     * Maximum size is 48x48x48 (structure block limit).
     */
    size: [number, number, number];
    
    /**
     * Block palette - array of unique block states.
     * Each block placement references an index in this array.
     * Reduces file size by avoiding duplicate block state definitions.
     */
    palette: BlockState[];
    
    /**
     * Multiple palettes (optional).
     * Used by some vanilla structures (e.g., shipwrecks) for randomization.
     * When present, one palette is randomly selected during placement.
     */
    palettes?: BlockState[][];
    
    /**
     * Block placements within the structure.
     * Each entry specifies a block type (via palette index) and position.
     * Air blocks are typically omitted to reduce file size.
     */
    blocks: BlockPlacement[];
    
    /**
     * Entities within the structure (optional).
     * Includes paintings, armor stands, item frames, etc.
     */
    entities?: StructureEntity[];
    
    /**
     * Structure author (pre-1.13 only, deprecated).
     * Name of the player who created the structure.
     * No longer used in modern versions.
     */
    author?: string;
}

