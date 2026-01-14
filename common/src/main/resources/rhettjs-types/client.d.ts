// RhettJS Client API Type Definitions
// Version: 0.3.0
// Last updated: 2026-01-13

import { Position } from './types';

/** Available client event types */
export type ClientEventType =
    | "keyPress"
    | "tick"
    | "chatSend";

/** Key press event */
export interface KeyPressEvent {
    /** Key name (e.g., "R", "SPACE", "ESCAPE") */
    key: string;

    /** Platform-specific scancode */
    scanCode: number;

    /** Action: 0=release, 1=press, 2=repeat */
    action: 0 | 1 | 2;

    /** Modifier bitmask (shift, ctrl, alt, etc.) */
    modifiers: number;
}

/** Client tick event */
export interface TickEvent {
    /** Current client tick count */
    tick: number;
}

/** Chat send event (cancelable) */
export interface ChatSendEvent {
    /** Message being sent */
    message: string;

    /** Cancel sending this message */
    cancel(): void;
}

/** Client player object */
export interface ClientPlayer {
    /** Player name */
    readonly name: string;

    /** Player UUID */
    readonly uuid: string;

    /** Current health */
    readonly health: number;

    /** Maximum health */
    readonly maxHealth: number;

    /** Food level (0-20) */
    readonly foodLevel: number;

    /** Saturation level */
    readonly saturation: number;

    /** Game mode */
    readonly gameMode: "survival" | "creative" | "adventure" | "spectator";

    /** Current position */
    readonly position: Position;

    /** Current rotation */
    readonly rotation: {
        yaw: number;
        pitch: number;
    };

    /**
     * Send message to player (client-side display)
     * @param message - Message text
     */
    sendMessage(message: string): void;

    /**
     * Send success message (green text)
     * @param message - Message text
     */
    sendSuccess(message: string): void;

    /**
     * Send error message (red text)
     * @param message - Message text
     */
    sendError(message: string): void;

    /**
     * Send warning message (yellow text)
     * @param message - Message text
     */
    sendWarning(message: string): void;

    /**
     * Send info message (gray text)
     * @param message - Message text
     */
    sendInfo(message: string): void;

    /**
     * Run command as this player
     * Sends command packet to server (client-side execution)
     * @param command - Command string (without leading /)
     * @example
     * Client.player.runCommand('gamemode creative');
     * Client.player.runCommand('tp 0 100 0');
     */
    runCommand(command: string): void;
}

/** Client event handler */
export type ClientEventHandler = (event: any) => void | Promise<void>;

declare module 'rhettjs/client' {
    /**
     * Client API - Access to current player and client-side events
     *
     * Available only in CLIENT execution context (CLIENT scripts).
     * Provides access to the local Minecraft player and client-side events
     * like keyboard input, ticks, and chat messages.
     *
     * @example
     * import Client from 'rhettjs/client';
     *
     * // Access current player
     * console.log(`Hello, ${Client.player.name}!`);
     * console.log(`Position: ${Client.player.position.x}, ${Client.player.position.y}, ${Client.player.position.z}`);
     *
     * @example
     * // Send messages to player
     * Client.player.sendSuccess('Welcome to RhettJS!');
     * Client.player.sendError('Something went wrong');
     *
     * @example
     * // Run commands as player
     * Client.player.runCommand('gamemode creative');
     *
     * @example
     * // Listen for key presses
     * Client.on(Client.eventTypes.KEY_PRESS, (event) => {
     *     if (event.key === 'G' && event.action === 1) {
     *         Client.player.sendInfo('You pressed G!');
     *     }
     * });
     *
     * @example
     * // Client tick events
     * let tickCount = 0;
     * Client.on(Client.eventTypes.TICK, (event) => {
     *     tickCount++;
     *     if (tickCount % 100 === 0) {
     *         console.log(`${tickCount} ticks elapsed`);
     *     }
     * });
     *
     * @example
     * // Chat message filtering
     * Client.on(Client.eventTypes.CHAT_SEND, (event) => {
     *     if (event.message.includes('secret')) {
     *         event.cancel();
     *         Client.player.sendWarning('Secret word blocked!');
     *     }
     * });
     */
    export const Client: {
        /** Current client player (readonly, live) */
        readonly player: ClientPlayer;

        /** Event type constants for type-safe event registration */
        readonly eventTypes: {
            KEY_PRESS: "keyPress";
            TICK: "tick";
            CHAT_SEND: "chatSend";
        };

        /**
         * Register event handler
         * @param event - Event name (use Client.eventTypes for constants)
         * @param handler - Event handler
         * @example
         * Client.on(Client.eventTypes.KEY_PRESS, (event) => {
         *     console.log(`Key pressed: ${event.key}`);
         * });
         */
        on(event: ClientEventType, handler: ClientEventHandler): void;

        /**
         * Register one-time event handler
         * @param event - Event name (use Client.eventTypes for constants)
         * @param handler - Event handler
         * @example
         * Client.once(Client.eventTypes.TICK, (event) => {
         *     console.log('First tick received');
         * });
         */
        once(event: ClientEventType, handler: ClientEventHandler): void;

        /**
         * Remove event handler
         * @param event - Event name
         * @param handler - Event handler
         */
        off(event: ClientEventType, handler: ClientEventHandler): void;
    };

    export default Client;
}
