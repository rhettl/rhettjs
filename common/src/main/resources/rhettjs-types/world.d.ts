// RhettJS World API Type Definitions
// Version: 0.3.0
// Last updated: 2026-01-13

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
 * import World from 'rhettjs/world';
 * const block = await World.getBlock({ x: 100, y: 64, z: 200 });
 * console.log(`Block: ${block.id}`);
 */
declare module 'rhettjs/world' {
    export const World: {
        /** List of dimension identifiers */
        readonly dimensions: string[];

        /**
         * Get block at position
         * @param position - Block position
         * @returns Block data
         */
        getBlock(position: Position): Promise<Block>;

        /**
         * Get block entity data at position
         * Returns null if no block entity exists at the position
         * @param position - Block position
         * @returns Block entity NBT data or null
         */
        getBlockEntity(position: Position): Promise<Record<string, any> | null>;

        /**
         * Set block at position
         * @param position - Block position
         * @param blockId - Block identifier (e.g., "minecraft:stone")
         * @param properties - Block properties
         */
        setBlock(position: Position, blockId: string, properties?: Record<string, string>): Promise<void>;

        /**
         * Fill region with blocks
         * @param pos1 - First corner
         * @param pos2 - Second corner
         * @param blockId - Block identifier
         * @param options - Optional fill options (exclusion zones)
         * @returns Number of blocks placed
         */
        fill(pos1: Position, pos2: Position, blockId: string, options?: FillOptions): Promise<number>;

        /**
         * Replace blocks in region matching filter
         * @param pos1 - First corner
         * @param pos2 - Second corner
         * @param filter - Block ID or predicate to match
         * @param replacement - Block ID to replace with
         * @returns Number of blocks replaced
         */
        replace(pos1: Position, pos2: Position, filter: string, replacement: string): Promise<number>;

        /**
         * Get entities within radius of position
         * @param position - Center position
         * @param radius - Search radius
         * @returns Array of entity objects
         */
        getEntities(position: Position, radius: number): Promise<any[]>;

        /**
         * Spawn entity at position
         * @param position - Spawn position
         * @param entityId - Entity type ID (e.g., "minecraft:zombie")
         * @returns Spawned entity object
         */
        spawnEntity(position: Position, entityId: string): Promise<any>;

        /**
         * Get all online players
         * @returns Array of player objects
         */
        getPlayers(): Promise<Player[]>;

        /**
         * Get player by name or UUID
         * @param nameOrUuid - Player name or UUID
         * @returns Player object or null
         */
        getPlayer(nameOrUuid: string): Promise<Player | null>;

        /**
         * Get world time
         * @param dimension - Dimension identifier (optional)
         * @returns Time in ticks
         */
        getTime(dimension?: string): Promise<number>;

        /**
         * Set world time
         * @param time - Time in ticks
         * @param dimension - Dimension identifier (optional)
         */
        setTime(time: number, dimension?: string): Promise<void>;

        /**
         * Get weather
         * @param dimension - Dimension identifier (optional)
         * @returns Weather type
         */
        getWeather(dimension?: string): Promise<"clear" | "rain" | "thunder">;

        /**
         * Set weather
         * @param weather - Weather type
         * @param dimension - Dimension identifier (optional)
         */
        setWeather(weather: "clear" | "rain" | "thunder", dimension?: string): Promise<void>;

        /**
         * Get dimension height bounds (absolute world limits)
         * @param dimension - Dimension identifier (optional, defaults to overworld)
         * @returns Dimension bounds object with min/max Y coordinates
         */
        getDimensionBounds(dimension?: string): Promise<DimensionBounds>;

        /**
         * Get vertical bounds of non-air blocks in a horizontal region
         * @param pos1 - First corner (x/z used, y ignored)
         * @param pos2 - Second corner (x/z used, y ignored)
         * @param dimension - Dimension identifier (optional, defaults to overworld)
         * @returns Filled bounds object with min/max Y of blocks, or null if all air
         */
        getFilledBounds(pos1: Position, pos2: Position, dimension?: string): Promise<FilledBounds | null>;

        /**
         * Remove all entities in a region (without dropping items)
         * @param pos1 - First corner
         * @param pos2 - Second corner
         * @param options - Optional filter options
         * @returns Number of entities removed
         */
        removeEntities(pos1: Position, pos2: Position, options?: {
            excludePlayers?: boolean;
            types?: string[];
            dimension?: string;
        }): Promise<number>;
    };

    export default World;
}
