// RhettJS WorldgenStructure API Type Definitions
// Version: 0.3.0
// Last updated: 2026-01-06

/**
 * Options for WorldgenStructure.place()
 */
interface WorldgenStructurePlaceOptions {
    /** X coordinate (center of structure) */
    x: number;

    /** Z coordinate (center of structure) */
    z: number;

    /** Dimension to place in (default: current dimension or overworld) */
    dimension?: string;

    /** Seed for randomization (default: random) */
    seed?: number;

    /**
     * Surface mode for height calculation:
     * - "heightmap" (default): Use vanilla heightmap
     * - "scan": Scan actual blocks to find surface (for custom platforms)
     * - "fixed:Y": Use fixed Y level (e.g., "fixed:63")
     * - "rigid": Force all pieces to use start Y
     */
    surface?: "heightmap" | "scan" | `fixed:${number}` | "rigid";

    /**
     * Rotation override (default: determined by seed)
     * - "none": No rotation
     * - "clockwise_90" or "90": 90 degrees clockwise
     * - "180": 180 degrees
     * - "counterclockwise_90" or "270": 90 degrees counter-clockwise
     */
    rotation?: "none" | "clockwise_90" | "90" | "180" | "counterclockwise_90" | "270";
}

/**
 * Result from WorldgenStructure.place()
 */
interface WorldgenStructurePlaceResult {
    /** Whether placement succeeded */
    success: boolean;

    /** Error message if failed */
    error?: string;

    /** Seed used for randomization */
    seed?: number;

    /** Rotation applied */
    rotation?: "none" | "clockwise_90" | "clockwise_180" | "counterclockwise_90";

    /** Number of pieces placed */
    pieceCount?: number;

    /** Bounding box of placed structure */
    boundingBox?: {
        min: { x: number; y: number; z: number };
        max: { x: number; y: number; z: number };
    };
}

/**
 * Options for WorldgenStructure.placeJigsaw()
 */
interface WorldgenStructurePlaceJigsawOptions {
    /** Template pool name (e.g., "minecraft:village/plains/town_centers") */
    pool: string;

    /** Target jigsaw identifier (e.g., "minecraft:bottom") */
    target: string;

    /** Maximum jigsaw depth (1-20) */
    maxDepth: number;

    /** X coordinate */
    x: number;

    /** Z coordinate */
    z: number;

    /** Dimension to place in (default: overworld) */
    dimension?: string;

    /** Seed for randomization (default: random) */
    seed?: number;

    /** Surface mode (same as place()) */
    surface?: "heightmap" | "scan" | `fixed:${number}`;
}

/**
 * Result from WorldgenStructure.placeJigsaw()
 */
interface WorldgenStructurePlaceJigsawResult {
    /** Whether placement succeeded */
    success: boolean;

    /** Error message if failed */
    error?: string;

    /** Pool used */
    pool?: string;

    /** Target used */
    target?: string;

    /** Max depth used */
    maxDepth?: number;

    /** Position placed at */
    position?: { x: number; z: number };
}

/**
 * Information about a worldgen structure.
 * Returned by WorldgenStructure.info()
 */
interface WorldgenStructureInfo {
    /** Full name with namespace (e.g., "minecraft:village_plains") */
    name: string;

    /** Structure type (e.g., "minecraft:jigsaw", "minecraft:buried_treasure") */
    type: string;

    /** Biome tag if structure uses a tag (e.g., "#minecraft:has_structure/village_plains") */
    biomesTag?: string;

    /** List of individual biomes (if available) */
    biomes?: string[];

    /** Terrain adaptation mode */
    terrainAdaptation: "none" | "beard_thin" | "beard_box" | "bury" | "encapsulate";

    /** Generation step */
    step: "raw_generation" | "lakes" | "local_modifications" | "underground_structures" |
          "surface_structures" | "strongholds" | "underground_ores" | "underground_decoration" |
          "fluid_springs" | "vegetal_decoration" | "top_layer_modification";

    /** Spawn override configuration (if any) */
    spawnOverrides?: Record<string, { boundingBox: string }>;

    /** Whether this is a jigsaw-based structure (like villages, bastions, etc.) */
    isJigsaw: boolean;
}

/**
 * WorldgenStructure API - Access Minecraft's worldgen structure definitions
 *
 * This API provides read access to worldgen structures like villages, temples,
 * bastions, etc. These are the JSON-defined structures in data/worldgen/structure/
 * that control natural world generation.
 *
 * For .nbt template files, use StructureNbt instead.
 *
 * @example
 * // List all vanilla structures
 * const structures = await WorldgenStructure.list("minecraft");
 * console.log(structures); // ["minecraft:village_plains", "minecraft:bastion_remnant", ...]
 *
 * @example
 * // Get info about a specific structure
 * const info = await WorldgenStructure.info("minecraft:village_plains");
 * console.log(info.type);     // "minecraft:jigsaw"
 * console.log(info.isJigsaw); // true
 * console.log(info.step);     // "surface_structures"
 */
declare namespace WorldgenStructure {
    /**
     * List available worldgen structures
     * @param namespace - Optional namespace filter (e.g., "minecraft")
     * @returns Array of structure names in format "namespace:name"
     * @example
     * const all = await WorldgenStructure.list();
     * const vanilla = await WorldgenStructure.list("minecraft");
     */
    function list(namespace?: string): Promise<string[]>;

    /**
     * Check if a worldgen structure exists
     * @param name - Structure name in format "[namespace:]name"
     * @returns True if the structure exists
     * @example
     * if (await WorldgenStructure.exists("minecraft:village_plains")) {
     *   console.log("Village plains structure exists!");
     * }
     */
    function exists(name: string): Promise<boolean>;

    /**
     * Get detailed information about a worldgen structure
     * @param name - Structure name in format "[namespace:]name"
     * @returns Structure information including type, biomes, terrain adaptation, etc.
     * @throws If structure not found
     * @example
     * const info = await WorldgenStructure.info("minecraft:village_plains");
     * console.log(`Type: ${info.type}`);
     * console.log(`Biomes: ${info.biomesTag || info.biomes?.join(", ")}`);
     * console.log(`Is Jigsaw: ${info.isJigsaw}`);
     */
    function info(name: string): Promise<WorldgenStructureInfo>;

    /**
     * Place a worldgen structure at a position
     *
     * Uses vanilla-like placement with optional custom surface detection
     * for placing on custom platforms.
     *
     * @param name - Structure name in format "[namespace:]name"
     * @param options - Placement options (position, seed, surface mode, rotation)
     * @returns Placement result with success status, bounding box, piece count
     *
     * @example
     * // Basic placement with random seed
     * const result = await WorldgenStructure.place("minecraft:village_plains", {
     *     x: 0,
     *     z: 0
     * });
     *
     * @example
     * // Placement on custom platform with specific seed
     * const result = await WorldgenStructure.place("minecraft:village_plains", {
     *     x: 0,
     *     z: 0,
     *     dimension: "rhettjs:structure_test",
     *     seed: 12345,
     *     surface: "scan"  // Scan platform for actual surface
     * });
     *
     * @example
     * // Placement with fixed rotation
     * const result = await WorldgenStructure.place("minecraft:bastion_remnant", {
     *     x: 100,
     *     z: 200,
     *     rotation: "clockwise_90"
     * });
     */
    function place(name: string, options: WorldgenStructurePlaceOptions): Promise<WorldgenStructurePlaceResult>;

    /**
     * Place a jigsaw structure from a template pool
     *
     * Similar to /place jigsaw command - places from a specific pool
     * with controlled depth. Useful for testing individual structure parts.
     *
     * @param options - Jigsaw placement options
     * @returns Placement result
     *
     * @example
     * // Place village center pieces
     * const result = await WorldgenStructure.placeJigsaw({
     *     pool: "minecraft:village/plains/town_centers",
     *     target: "minecraft:bottom",
     *     maxDepth: 7,
     *     x: 0,
     *     z: 0,
     *     surface: "scan"
     * });
     *
     * @example
     * // Place bastion pieces with limited depth
     * const result = await WorldgenStructure.placeJigsaw({
     *     pool: "minecraft:bastion/starts",
     *     target: "minecraft:bottom",
     *     maxDepth: 3,
     *     x: 0,
     *     z: 0
     * });
     */
    function placeJigsaw(options: WorldgenStructurePlaceJigsawOptions): Promise<WorldgenStructurePlaceJigsawResult>;
}

export { WorldgenStructure };
export type {
    WorldgenStructureInfo,
    WorldgenStructurePlaceOptions,
    WorldgenStructurePlaceResult,
    WorldgenStructurePlaceJigsawOptions,
    WorldgenStructurePlaceJigsawResult
};
export default WorldgenStructure;