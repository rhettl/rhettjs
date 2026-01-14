// RhettJS Server API Type Definitions
// Version: 0.3.0
// Last updated: 2026-01-13

/** Available server event types */
export type ServerEventType =
    | "playerJoin"
    | "playerLeave"
    | "blockLeftClick"
    | "blockRightClick";

/** Player join event */
export interface PlayerJoinEvent {
    player: Player;
}

/** Player leave event */
export interface PlayerLeaveEvent {
    player: Player;
}

/** Block click event (cancelable) */
export interface BlockClickEvent {
    /** Full player object with all methods (sendMessage, teleport, etc.) */
    player: Player;
    position: Position;
    block: Block;
    face: "up" | "down" | "north" | "south" | "east" | "west" | null;
    item: {
        id: string;
        count: number;
        displayName: string | null;
        nbt: Record<string, any> | null;
    } | null;
    /** Cancel this event to prevent the default action */
    cancel(): void;
}

/** Server event handler */
export type ServerEventHandler = (event: any) => void | Promise<void>;

declare module 'rhettjs/server' {
    import { Player, Position, Block } from './types';

    /**
     * Server events and properties
     * @example
     * import Server from 'rhettjs/server';
     *
     * // Using event type constants
     * Server.on(Server.eventTypes.PLAYER_JOIN, (event) => {
     *   console.log(`${event.player.name} joined`);
     * });
     *
     * // Or using string literals
     * Server.on('playerJoin', (event) => {
     *   console.log(`${event.player.name} joined`);
     * });
     *
     * // Block click events
     * Server.on(Server.eventTypes.BLOCK_LEFT_CLICK, (event) => {
     *   console.log(`${event.player.name} left-clicked at ${event.position.x}, ${event.position.y}, ${event.position.z}`);
     * });
     */
    export const Server: {
        /** Event type constants for type-safe event registration */
        readonly eventTypes: {
            PLAYER_JOIN: "playerJoin";
            PLAYER_LEAVE: "playerLeave";
            BLOCK_LEFT_CLICK: "blockLeftClick";
            BLOCK_RIGHT_CLICK: "blockRightClick";
        };

        /** Current TPS (ticks per second) */
        readonly tps: number;

        /** Online players count */
        readonly players: number;

        /** Maximum players allowed */
        readonly maxPlayers: number;

        /** Server MOTD (message of the day) */
        readonly motd: string;

        /**
         * Register event handler
         * @param event - Event name (use Server.eventTypes for constants)
         * @param handler - Event handler
         * @example
         * Server.on(Server.eventTypes.PLAYER_JOIN, (event) => {
         *   console.log(`${event.player.name} joined`);
         * });
         */
        on(event: ServerEventType, handler: ServerEventHandler): void;

        /**
         * Register one-time event handler
         * @param event - Event name (use Server.eventTypes for constants)
         * @param handler - Event handler
         * @example
         * Server.once(Server.eventTypes.PLAYER_LEAVE, (event) => {
         *   console.log(`${event.player.name} left (handled once)`);
         * });
         */
        once(event: ServerEventType, handler: ServerEventHandler): void;

        /**
         * Remove event handler
         * @param event - Event name
         * @param handler - Event handler
         */
        off(event: ServerEventType, handler: ServerEventHandler): void;

        /**
         * Broadcast message to all players
         * @param message - Message text
         */
        broadcast(message: string): void;

        /**
         * Run server command
         * @param command - Command to execute
         */
        runCommand(command: string): void;
    };

    export default Server;
}