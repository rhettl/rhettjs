/**
 * RhettJS Puppeteer Extension - TypeScript Definitions
 *
 * Provides APIs for spawning and controlling puppet entities (mannequins/NPCs).
 */
declare module 'rhettjs/puppeteer' {
    import { Position } from 'rhettjs/types';

    /**
     * Puppet entity data.
     */
    export interface Puppet {
        /** Unique identifier */
        readonly id: string;

        /** Display name */
        readonly name: string;

        /** Current position in the world */
        readonly position: Position;
    }

    /**
     * Options for spawning a puppet.
     */
    export interface SpawnOptions {
        /** Custom name for the puppet (default: "Puppet") */
        name?: string;

        /** Skin texture path (default: steve skin) */
        skin?: string;
    }

    /**
     * Controller for manipulating a spawned puppet.
     */
    export interface PuppetController {
        /**
         * Move the puppet to a new position.
         * @param position - Target position
         * @returns Promise that resolves when movement completes
         */
        move(position: Position): Promise<void>;

        /**
         * Rotate the puppet.
         * @param yaw - Horizontal rotation (0-360)
         * @param pitch - Vertical rotation (-90 to 90)
         */
        rotate(yaw: number, pitch: number): void;

        /**
         * Set the puppet's skin texture.
         * @param texture - Path to texture (e.g., "minecraft:textures/entity/steve.png")
         */
        setSkin(texture: string): void;

        /**
         * Set the puppet's display name.
         * @param name - New name
         */
        setName(name: string): void;

        /**
         * Get the puppet's current position.
         * @returns Current position
         */
        getPosition(): Position;

        /**
         * Remove this puppet from the world.
         */
        remove(): void;
    }

    /**
     * Spawn a puppet at the given position.
     *
     * @param position - Where to spawn the puppet
     * @param options - Optional spawn configuration
     * @returns Promise resolving to the spawned puppet's data
     *
     * @example
     * ```javascript
     * const puppet = await Puppeteer.spawn(
     *     { x: 100, y: 64, z: 200 },
     *     { name: "Steve", skin: "minecraft:textures/entity/steve.png" }
     * );
     * console.log(`Spawned puppet: ${puppet.name} at ${puppet.position.x}, ${puppet.position.y}, ${puppet.position.z}`);
     * ```
     */
    export function spawn(position: Position, options?: SpawnOptions): Promise<Puppet>;

    /**
     * Get a controller for an existing puppet.
     *
     * @param id - Puppet's unique ID
     * @returns Controller for the puppet, or null if not found
     *
     * @example
     * ```javascript
     * const controller = Puppeteer.control(puppet.id);
     * if (controller) {
     *     await controller.move({ x: 110, y: 64, z: 200 });
     *     controller.rotate(90, 0);
     * }
     * ```
     */
    export function control(id: string): PuppetController | null;

    /**
     * List all active puppets.
     *
     * @returns Array of all puppet data
     *
     * @example
     * ```javascript
     * const puppets = Puppeteer.list();
     * console.log(`Active puppets: ${puppets.length}`);
     * puppets.forEach(p => console.log(`- ${p.name} (${p.id})`));
     * ```
     */
    export function list(): Puppet[];

    /**
     * Remove a puppet by ID.
     *
     * @param id - Puppet's unique ID
     *
     * @example
     * ```javascript
     * Puppeteer.remove(puppet.id);
     * ```
     */
    export function remove(id: string): void;

    /**
     * Remove all puppets.
     *
     * @example
     * ```javascript
     * Puppeteer.removeAll();
     * console.log("All puppets removed");
     * ```
     */
    export function removeAll(): void;

    /**
     * Whether RhettJS debug mode is enabled.
     * Useful for conditional logging in extensions.
     */
    export const debugMode: boolean;
}

/**
 * Barrel export for legacy import style.
 * Allows: import Puppeteer from 'Puppeteer';
 */
declare module 'Puppeteer' {
    export * from 'rhettjs/puppeteer';
}
