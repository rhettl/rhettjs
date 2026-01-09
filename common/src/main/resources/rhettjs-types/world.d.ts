// RhettJS World API Type Definitions
// Version: 0.3.0
// Last updated: 2026-01-06

import { Position, Block, Player } from './types';

/**
 * Bounding box for exclusion zones
 */
export interface BoundingBox {
    min: Position;
    max: Position;
}

/**
 * Options for World.fill()
 */
export interface FillOptions {
    /** Array of bounding boxes to exclude from filling */
    exclude?: BoundingBox[];
}

/**
 * Dimension height bounds
 */
export interface DimensionBounds {
    /** Lowest Y coordinate in dimension (e.g., -64) */
    minY: number;
    /** Highest Y coordinate in dimension (e.g., 320) */
    maxY: number;
    /** Lowest buildable Y coordinate */
    minBuildHeight: number;
    /** Highest buildable Y coordinate */
    maxBuildHeight: number;
}

/**
 * Filled block bounds in a region
 */
export interface FilledBounds {
    /** Lowest Y coordinate with non-air blocks */
    minY: number;
    /** Highest Y coordinate with non-air blocks */
    maxY: number;
}

/**
 * World manipulation and queries (all async)
 * @example
 * const block = await World.getBlock({ x: 100, y: 64, z: 200 });
 * console.log(`Block: ${block.id}`);
 */
declare namespace World {
    /** List of dimension identifiers */
    const dimensions: string[];

    /**
     * Get block at position
     * @param position - Block position
     * @returns Block data
     */
    function getBlock(position: Position): Promise<Block>;

    /**
     * Get block entity data at position
     * Returns null if no block entity exists at the position
     * @param position - Block position
     * @returns Block entity NBT data or null
     * @example
     * // Read lectern book and page
     * const lectern = await World.getBlockEntity({ x: 100, y: 64, z: 200 });
     * if (lectern) {
     *   console.log(`Selected page: ${lectern.Page}`);
     *   const book = JSON.parse(lectern.Book?.tag?.pages?.[lectern.Page] || '""');
     *   console.log(`Page content: ${book}`);
     * }
     *
     * // Read sign text
     * const sign = await World.getBlockEntity({ x: 101, y: 64, z: 200 });
     * if (sign) {
     *   const line1 = JSON.parse(sign.front_text?.messages?.[0] || '""');
     *   console.log(`Sign line 1: ${line1}`);
     * }
     */
    function getBlockEntity(position: Position): Promise<Record<string, any> | null>;

    /**
     * Set block at position
     * @param position - Block position
     * @param blockId - Block identifier (e.g., "minecraft:stone")
     * @param properties - Block properties
     */
    function setBlock(position: Position, blockId: string, properties?: Record<string, string>): Promise<void>;

    /**
     * Fill region with blocks
     * @param pos1 - First corner
     * @param pos2 - Second corner
     * @param blockId - Block identifier
     * @param options - Optional fill options (exclusion zones)
     * @returns Number of blocks placed
     * @example
     * // Fill with exclusion zones
     * await World.fill(
     *   { x: -50, y: -64, z: -50 },
     *   { x: 50, y: 320, z: 50 },
     *   "minecraft:air",
     *   {
     *     exclude: [
     *       { min: {x: -5, y: 60, z: -5}, max: {x: 5, y: 100, z: 5} }
     *     ]
     *   }
     * );
     */
    function fill(pos1: Position, pos2: Position, blockId: string, options?: FillOptions): Promise<number>;

    /**
     * Replace blocks in region matching filter
     * @param pos1 - First corner
     * @param pos2 - Second corner
     * @param filter - Block ID or predicate to match
     * @param replacement - Block ID to replace with
     * @returns Number of blocks replaced
     */
    function replace(pos1: Position, pos2: Position, filter: string, replacement: string): Promise<number>;

    /**
     * Get entities within radius of position
     * @param position - Center position
     * @param radius - Search radius
     * @returns Array of entity objects
     */
    function getEntities(position: Position, radius: number): Promise<any[]>;

    /**
     * Spawn entity at position
     * @param position - Spawn position
     * @param entityId - Entity type ID (e.g., "minecraft:zombie")
     * @returns Spawned entity object
     */
    function spawnEntity(position: Position, entityId: string): Promise<any>;

    /**
     * Get all online players
     * @returns Array of player objects
     */
    function getPlayers(): Promise<Player[]>;

    /**
     * Get player by name or UUID
     * @param nameOrUuid - Player name or UUID
     * @returns Player object or null
     */
    function getPlayer(nameOrUuid: string): Promise<Player | null>;

    /**
     * Get world time
     * @param dimension - Dimension identifier (optional)
     * @returns Time in ticks
     */
    function getTime(dimension?: string): Promise<number>;

    /**
     * Set world time
     * @param time - Time in ticks
     * @param dimension - Dimension identifier (optional)
     */
    function setTime(time: number, dimension?: string): Promise<void>;

    /**
     * Get weather
     * @param dimension - Dimension identifier (optional)
     * @returns Weather type
     */
    function getWeather(dimension?: string): Promise<"clear" | "rain" | "thunder">;

    /**
     * Set weather
     * @param weather - Weather type
     * @param dimension - Dimension identifier (optional)
     */
    function setWeather(weather: "clear" | "rain" | "thunder", dimension?: string): Promise<void>;

    /**
     * Get dimension height bounds (absolute world limits)
     * @param dimension - Dimension identifier (optional, defaults to overworld)
     * @returns Dimension bounds object with min/max Y coordinates
     * @example
     * const bounds = await World.getDimensionBounds();
     * console.log(`World: ${bounds.minY} to ${bounds.maxY}`);
     * // World: -64 to 320
     */
    function getDimensionBounds(dimension?: string): Promise<DimensionBounds>;

    /**
     * Get vertical bounds of non-air blocks in a horizontal region
     * Scans the region to find the lowest and highest Y coordinates with blocks.
     * Returns null if the entire region is empty (all air).
     * @param pos1 - First corner (x/z used, y ignored)
     * @param pos2 - Second corner (x/z used, y ignored)
     * @param dimension - Dimension identifier (optional, defaults to overworld)
     * @returns Filled bounds object with min/max Y of blocks, or null if all air
     * @example
     * const bounds = await World.getFilledBounds(
     *   { x: -50, z: -50 },
     *   { x: 50, z: 50 }
     * );
     * if (bounds) {
     *   console.log(`Blocks from Y ${bounds.minY} to ${bounds.maxY}`);
     *   // Only clear the vertical range with blocks
     *   await World.fill(
     *     { x: -50, y: bounds.minY, z: -50 },
     *     { x: 50, y: bounds.maxY, z: 50 },
     *     "minecraft:air"
     *   );
     * }
     */
    function getFilledBounds(pos1: Position, pos2: Position, dimension?: string): Promise<FilledBounds | null>;

    /**
     * Remove all entities in a region (without dropping items)
     * Removes entities directly without killing them, so no item drops.
     * Useful for cleaning up after structure placement.
     * @param pos1 - First corner
     * @param pos2 - Second corner
     * @param options - Optional filter options
     * @returns Number of entities removed
     * @example
     * // Remove all entities except players
     * const removed = await World.removeEntities(
     *   { x: -50, y: -64, z: -50 },
     *   { x: 50, y: 320, z: 50 },
     *   { excludePlayers: true }
     * );
     * console.log(`Removed ${removed} entities`);
     *
     * @example
     * // Remove only specific entity types
     * await World.removeEntities(
     *   pos1, pos2,
     *   { types: ["minecraft:villager", "minecraft:item"] }
     * );
     */
    function removeEntities(pos1: Position, pos2: Position, options?: {
        excludePlayers?: boolean;
        types?: string[];  // If provided, only remove these entity types
        dimension?: string;
    }): Promise<number>;
}

export default World;