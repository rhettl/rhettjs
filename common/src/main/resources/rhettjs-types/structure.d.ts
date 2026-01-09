// RhettJS Structure API Type Definitions
// Version: 0.3.0
// Last updated: 2026-01-06

import { Position } from './types';

/** Options for structure capture */
export interface CaptureOptions {
    author?: string;
    description?: string;
    dimension?: string;
}

/** Options for structure placement */
export interface PlaceOptions {
    rotation?: 0 | 90 | 180 | 270;
    centered?: boolean;
    dimension?: string;
    /**
     * Placement mode controlling block replacement behavior:
     * - "replace" (default): Replace all blocks at target location
     * - "keep_air": Don't place air blocks from structure (preserves existing blocks where structure has air)
     * - "overlay": Only place blocks where target location is air (fills empty space only)
     */
    mode?: "replace" | "keep_air" | "overlay";
    /**
     * Vertical alignment for structure placement:
     * - "bottom" (default): Align structure bottom to Y position
     * - "top": Align structure top to Y position
     * - "center": Center structure vertically at Y position
     * - number: Offset Y position by this amount (e.g., 5 = place 5 blocks higher, -5 = place 5 blocks lower)
     * @example
     * { vAlign: "bottom" }  // Structure bottom at Y
     * { vAlign: "top" }     // Structure top at Y
     * { vAlign: "center" }  // Structure center at Y
     * { vAlign: 5 }         // Offset +5 blocks
     * { vAlign: -10 }       // Offset -10 blocks
     */
    vAlign?: "bottom" | "top" | "center" | number;
}

/** Entity in structure NBT data */
export interface StructureEntity {
    /** Block-relative position as integers */
    blockPos: [number, number, number];
    /** Entity position as doubles (precise position) */
    pos: [number, number, number];
    /** Entity NBT data (type, properties, etc.) */
    nbt: {
        /** Entity type ID (e.g., "minecraft:painting", "minecraft:armor_stand") */
        id: string;
        /** Additional entity-specific NBT data */
        [key: string]: any;
    };
}

/** Structure data format returned by load() */
export interface StructureData {
    /** Structure size {x, y, z} */
    size: {
        x: number;
        y: number;
        z: number;
    };
    /** List of blocks in the structure */
    blocks: Array<{
        x: number;
        y: number;
        z: number;
        block: {
            name: string;
            properties: Record<string, string>;
        };
        blockEntityData?: any;
    }>;
    /** List of entities in the structure */
    entities: StructureEntity[];
    /** Structure metadata (author, description, etc.) */
    metadata: Record<string, string>;
}

/** Options for large structure capture */
export interface CaptureLargeOptions extends CaptureOptions {
    pieceSize?: { x: number; y: number; z: number }; // Default: 48x48x48
}

/**
 * StructureNbt API - Handles single .nbt template files
 * Static, deterministic structure placement (exact block-by-block copies)
 */
declare namespace StructureNbt {
    /**
     * Load structure data from global resources
     * Searches entire resource system: generated/, datapacks/, mod resources (in priority order)
     * Provides low-level access to structure NBT for custom manipulation.
     * @param name - Structure name in format "[namespace:]name"
     * @returns Structure data with blocks, size, metadata
     * @example
     * const data = await StructureNbt.load('minecraft:village/plains/houses/plains_small_house_1');
     * console.log(`Structure size: ${data.size.x}x${data.size.y}x${data.size.z}`);
     * console.log(`Contains ${data.blocks.length} blocks`);
     */
    function load(name: string): Promise<StructureData>;

    /**
     * Save structure data to owned location (generated/<namespace>/structures/)
     * Low-level write for custom structure manipulation.
     * Automatically creates backup before overwriting existing files.
     * @param name - Structure name in format "[namespace:]name"
     * @param data - Structure data to save
     * @example
     * const data = await StructureNbt.load('test:house');
     * // ... modify data.blocks, data.size, etc. ...
     * await StructureNbt.save('test:house', data);
     */
    function save(name: string, data: StructureData): Promise<void>;

    /**
     * Check if structure exists
     * @param name - Structure name in format "[namespace:]name"
     * @returns True if exists
     */
    function exists(name: string): Promise<boolean>;

    /**
     * List structures
     * @param namespace - Optional namespace filter
     * @returns Array of structure names
     */
    function list(namespace?: string): Promise<string[]>;

    /**
     * List structures from world/generated/ only (excludes datapacks and mods)
     * Only returns structures created/saved by your scripts, not vanilla or mod resources
     * @param namespace - Optional namespace filter
     * @returns Array of structure names from generated/ directory only
     * @example
     * // List all your custom structures
     * const myStructures = await StructureNbt.listGenerated();
     *
     * // List only your 'minecraft' namespace structures
     * const minecraftStructures = await StructureNbt.listGenerated('minecraft');
     */
    function listGenerated(namespace?: string): Promise<string[]>;

    /**
     * Delete structure
     * @param name - Structure name
     * @returns True if deleted
     */
    function remove(name: string): Promise<boolean>;

    /**
     * Capture region as structure
     * @param pos1 - First corner
     * @param pos2 - Second corner
     * @param name - Structure name
     * @param options - Optional metadata
     */
    function capture(pos1: Position, pos2: Position, name: string, options?: CaptureOptions): Promise<void>;

    /**
     * Place structure at position
     * @param position - Placement position
     * @param name - Structure name
     * @param options - Placement options
     */
    function place(position: Position, name: string, options?: PlaceOptions): Promise<void>;

    /**
     * Get structure size
     * @param name - Structure name
     * @returns Size dimensions
     * @example
     * const size = await StructureNbt.getSize('test:house');
     * console.log(`${size.x}x${size.y}x${size.z}`);
     */
    function getSize(name: string): Promise<{ x: number; y: number; z: number }>;

    /**
     * List all unique blocks in a structure with their counts
     * @param name - Structure name
     * @returns Map of blockId → count (e.g., {"minecraft:stone": 450, "terralith:stone": 23})
     * @example
     * const blocks = await StructureNbt.blocksList('test:house');
     * console.log(`Contains ${Object.keys(blocks).length} unique blocks`);
     */
    function blocksList(name: string): Promise<Record<string, number>>;

    /**
     * Extract unique mod namespaces from structure blocks
     * @param name - Structure name
     * @returns Array of unique namespaces (e.g., ["minecraft", "terralith"])
     * @example
     * const namespaces = await StructureNbt.blocksNamespaces('test:house');
     * console.log(`Requires mods: ${namespaces.filter(ns => ns !== 'minecraft').join(', ')}`);
     */
    function blocksNamespaces(name: string): Promise<string[]>;

    /**
     * Replace blocks in a structure according to replacement map
     * Creates automatic backup before modification
     * @param name - Structure name
     * @param replacementMap - Map of oldBlockId → newBlockId
     * @example
     * // Convert terralith to vanilla
     * await StructureNbt.blocksReplace('test:house', {
     *   'terralith:stone_wall': 'minecraft:cobblestone_wall',
     *   'terralith:oak_planks': 'minecraft:oak_planks'
     * });
     */
    function blocksReplace(name: string, replacementMap: Record<string, string>): Promise<void>;

    /**
     * List available backups for a structure
     * Returns timestamps in descending order (newest first)
     * @param name - Structure name
     * @returns Array of backup timestamps (e.g., ["2026-01-05_15-30-45", "2026-01-05_14-20-30"])
     * @example
     * const backups = await StructureNbt.listBackups('test:house');
     * console.log(`${backups.length} backups available`);
     */
    function listBackups(name: string): Promise<string[]>;

    /**
     * Restore structure from backup
     * @param name - Structure name
     * @param timestamp - Optional specific backup timestamp, or undefined for most recent
     * @example
     * // Restore from most recent backup
     * await StructureNbt.restoreBackup('test:house');
     *
     * // Restore from specific backup
     * await StructureNbt.restoreBackup('test:house', '2026-01-05_15-30-45');
     */
    function restoreBackup(name: string, timestamp?: string): Promise<void>;
}

/**
 * LargeStructureNbt API - Handles multi-chunk .nbt structures stored in rjs-large/ directories
 * Large structures are split into pieces (e.g., 48x48x48 chunks) for efficient handling
 */
declare namespace LargeStructureNbt {
    /**
     * Capture large region split into pieces
     * @param pos1 - First corner
     * @param pos2 - Second corner
     * @param name - Structure name
     * @param options - Capture options with piece size
     * @example
     * await LargeStructureNbt.capture(
     *   { x: 100, y: 60, z: 100 },
     *   { x: 199, y: 109, z: 149 },
     *   'test:castle',
     *   { pieceSize: { x: 48, y: 48, z: 48 } }
     * );
     */
    function capture(pos1: Position, pos2: Position, name: string, options?: CaptureLargeOptions): Promise<void>;

    /**
     * Place large multi-piece structure
     * @param position - Placement position
     * @param name - Structure name
     * @param options - Placement options
     * @example
     * await LargeStructureNbt.place(
     *   { x: 500, y: 60, z: 500 },
     *   'test:castle',
     *   { rotation: 90, centered: true }
     * );
     */
    function place(position: Position, name: string, options?: PlaceOptions): Promise<void>;

    /**
     * Get large structure size
     * @param name - Structure name
     * @returns Size dimensions
     * @example
     * const size = await LargeStructureNbt.getSize('test:castle');
     * console.log(`${size.x}x${size.y}x${size.z}`);
     */
    function getSize(name: string): Promise<{ x: number; y: number; z: number }>;

    /**
     * List large structures
     * @param namespace - Optional namespace filter
     * @returns Array of large structure names
     */
    function list(namespace?: string): Promise<string[]>;

    /**
     * Remove large structure (all pieces)
     * @param name - Structure name
     * @returns True if removed
     */
    function remove(name: string): Promise<boolean>;

    /**
     * Replace blocks in all pieces of a large structure
     * Creates automatic directory backup before modification
     * Also updates metadata.requires[] in 0_0_0.nbt with new namespace requirements
     * @param name - Large structure name
     * @param replacementMap - Map of oldBlockId → newBlockId
     * @example
     * // Convert entire large structure to vanilla
     * await LargeStructureNbt.blocksReplace('test:castle', {
     *   'terralith:stone': 'minecraft:stone',
     *   'terralith:dirt': 'minecraft:dirt'
     * });
     */
    function blocksReplace(name: string, replacementMap: Record<string, string>): Promise<void>;

    /**
     * List available directory backups for a large structure
     * Returns timestamps in descending order (newest first)
     * @param name - Large structure name
     * @returns Array of backup timestamps (e.g., ["2026-01-05_15-30-45", "2026-01-05_14-20-30"])
     * @example
     * const backups = await LargeStructureNbt.listBackups('test:castle');
     * console.log(`${backups.length} directory backups available`);
     */
    function listBackups(name: string): Promise<string[]>;

    /**
     * Restore large structure from directory backup
     * Restores all piece files from the backup directory
     * @param name - Large structure name
     * @param timestamp - Optional specific backup timestamp, or undefined for most recent
     * @example
     * // Restore from most recent backup
     * await LargeStructureNbt.restoreBackup('test:castle');
     *
     * // Restore from specific backup
     * await LargeStructureNbt.restoreBackup('test:castle', '2026-01-05_15-30-45');
     */
    function restoreBackup(name: string, timestamp?: string): Promise<void>;
}

export { StructureNbt, LargeStructureNbt };
export default StructureNbt;