// RhettJS Commands API Type Definitions
// Version: 0.3.0
// Last updated: 2026-01-06

import { Caller } from './types';

/** Subcommand builder for registration */
export interface SubcommandBuilder {
    /**
     * Add required subcommand argument
     * @param name - Argument name
     * @param type - Argument type ("xyz-position" = {x, y, z}, "xz-position" = {x, z}, supports ~ notation)
     * @example
     * .argument('name', 'string')  // Required
     * .argument('pos', 'xyz-position')  // Position with ~ support: "~ ~10 ~" or "100 64 200"
     */
    argument(name: string, type: "string" | "int" | "float" | "player" | "item" | "block" | "entity" | "xyz-position" | "xz-position"): SubcommandBuilder;

    /**
     * Add optional subcommand argument with default value
     * @param name - Argument name
     * @param type - Argument type ("xyz-position" = {x, y, z}, "xz-position" = {x, z}, supports ~ notation)
     * @param defaultValue - Default value if not provided (use null for no default)
     * @example
     * .argument('size', 'int', 48)        // Optional with default 48
     * .argument('author', 'string', null)  // Optional with no default (undefined if not provided)
     */
    argument(name: string, type: "string" | "int" | "float" | "player" | "item" | "block" | "entity" | "xyz-position" | "xz-position", defaultValue: any): SubcommandBuilder;

    /**
     * Add tab completion suggestions for an argument
     * @param argName - Argument name (matches .argument() name)
     * @param provider - Function that returns suggestions (string[] or Promise<string[]>)
     * @example
     * // Static list
     * .argument('action', 'string')
     * .suggestions('action', () => ['scan', 'fix', 'fix-all'])
     *
     * // Dynamic from API
     * .argument('structure', 'string')
     * .suggestions('structure', async () => await StructureNbt.list())
     *
     * // Filtered results
     * .argument('structure', 'string')
     * .suggestions('structure', async () => {
     *   const all = await StructureNbt.list();
     *   return all.filter(s => !s.includes('/rjs-large/'));
     * })
     */
    suggestions(argName: string, provider: (() => string[] | Promise<string[]>)): SubcommandBuilder;

    /**
     * Set subcommand executor
     * @param handler - Execution handler
     */
    executes(handler: (event: { caller: Caller; args: Record<string, any>; command: string; subcommand: string }) => void | Promise<void>): SubcommandBuilder;
}

/** Command builder for registration */
export interface CommandBuilder {
    /**
     * Set command description
     * @param desc - Description text
     */
    description(desc: string): CommandBuilder;

    /**
     * Set permission requirement
     * @param perm - Permission string or function
     */
    permission(perm: string | ((caller: Caller) => boolean)): CommandBuilder;

    /**
     * Add required command argument
     * @param name - Argument name
     * @param type - Argument type ("xyz-position" = {x, y, z}, "xz-position" = {x, z}, supports ~ notation)
     * @example
     * .argument('target', 'player')  // Required
     * .argument('pos1', 'xyz-position')  // Position with ~ support: "~ ~10 ~" or "100 64 200"
     * .argument('spawn', 'xz-position')  // 2D position: "~ ~" or "100 200"
     */
    argument(name: string, type: "string" | "int" | "float" | "player" | "item" | "block" | "entity" | "xyz-position" | "xz-position"): CommandBuilder;

    /**
     * Add optional command argument with default value
     * @param name - Argument name
     * @param type - Argument type ("xyz-position" = {x, y, z}, "xz-position" = {x, z}, supports ~ notation)
     * @param defaultValue - Default value if not provided (use null for no default)
     * @example
     * .argument('count', 'int', 1)        // Optional with default 1
     * .argument('message', 'string', null) // Optional with no default (undefined if not provided)
     */
    argument(name: string, type: "string" | "int" | "float" | "player" | "item" | "block" | "entity" | "xyz-position" | "xz-position", defaultValue: any): CommandBuilder;

    /**
     * Add tab completion suggestions for an argument
     * @param argName - Argument name (matches .argument() name)
     * @param provider - Function that returns suggestions (string[] or Promise<string[]>)
     * @example
     * // Order-independent - can call after all arguments
     * .argument('action', 'string')
     * .argument('structure', 'string', null)
     * .suggestions('action', () => ['scan', 'fix'])
     * .suggestions('structure', async () => await StructureNbt.list())
     */
    suggestions(argName: string, provider: (() => string[] | Promise<string[]>)): CommandBuilder;

    /**
     * Set command executor
     * @param handler - Execution handler
     */
    executes(handler: (event: { caller: Caller; args: Record<string, any>; command: string }) => void | Promise<void>): CommandBuilder;

    /**
     * Add a subcommand
     * @param name - Subcommand name
     * @returns Subcommand builder
     */
    subcommand(name: string): SubcommandBuilder;
}

/**
 * Command registration API
 * @example
 * // Simple command
 * Commands.register('heal')
 *   .description('Heal a player')
 *   .argument('target', 'player')
 *   .executes(({ caller, args }) => {
 *     args.target.setHealth(args.target.maxHealth);
 *     caller.sendMessage(`Healed ${args.target.name}`);
 *   });
 *
 * // Command with optional arguments
 * Commands.register('give')
 *   .description('Give items to a player')
 *   .argument('item', 'item')
 *   .argument('count', 'int', 1)  // Optional, defaults to 1
 *   .executes(({ caller, args }) => {
 *     const count = args.count;  // Always present (defaults to 1)
 *     caller.sendMessage(`Giving ${count}x ${args.item}`);
 *   });
 *
 * // Subcommand with optional arguments
 * const cmd = Commands.register('structure')
 *   .description('Structure commands');
 *
 * cmd.subcommand('save')
 *   .argument('name', 'string')
 *   .argument('size', 'int', 48)          // Optional with default 48
 *   .argument('author', 'string', null)   // Optional with no default
 *   .executes(({ caller, args }) => {
 *     // args.name - always present (required)
 *     // args.size - always present (default 48)
 *     // args.author - undefined if not provided (null default)
 *     const author = args.author ?? 'Unknown';
 *     caller.sendMessage(`Saving ${args.name} (size=${args.size}, author=${author})`);
 *   });
 *
 * // Note: All required arguments must come BEFORE optional arguments
 * // This is INVALID: .argument('optional', 'int', 1).argument('required', 'string')
 */
declare namespace Commands {
    /**
     * Register a new command
     * @param name - Command name
     * @returns Command builder
     */
    function register(name: string): CommandBuilder;

    /**
     * Unregister a command
     * @param name - Command name
     */
    function unregister(name: string): void;
}

export default Commands;