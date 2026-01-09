// RhettJS Common Type Definitions
// Version: 0.3.0
// Last updated: 2026-01-06

/** Position with optional dimension */
export interface Position {
    x: number;
    y: number;
    z: number;
    dimension?: string; // Default: "minecraft:overworld"
}

/** Block state information */
export interface Block {
    id: string; // e.g., "minecraft:stone"
    properties?: Record<string, string>; // e.g., { facing: "north", half: "bottom" }
}

/** Player object (wrapped) */
export interface Player {
    name: string;
    uuid: string;
    isPlayer: boolean; // Always true
    position: Position;
    health: number;
    maxHealth: number;
    foodLevel: number;
    saturation: number;
    gameMode: "survival" | "creative" | "adventure" | "spectator";
    isOp: boolean;

    setHealth(amount: number): void;
    teleport(position: Position): void;
    sendMessage(message: string): void;
    sendSuccess(message: string): void; // Green text
    sendError(message: string): void; // Red text
    sendWarning(message: string): void; // Yellow text
    sendInfo(message: string): void; // Gray text
    sendRaw(json: string): void; // Raw JSON text component
    giveItem(itemId: string, count?: number): void;
}

/** Command caller (player or console) */
export interface Caller {
    name: string; // Player name or "Server"
    isPlayer: boolean;
    // If isPlayer, includes all Player properties
    [key: string]: any;
    sendMessage(message: string): void;
    sendSuccess(message: string): void; // Green text
    sendError(message: string): void; // Red text
    sendWarning(message: string): void; // Yellow text
    sendInfo(message: string): void; // Gray text
    sendRaw(json: string): void; // Raw JSON text component
}