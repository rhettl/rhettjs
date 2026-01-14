// RhettJS Script API Type Definitions
// Version: 0.3.0
// Last updated: 2026-01-13

declare module 'rhettjs/script' {
    import { Caller } from './types';

    /**
     * Script execution context for utility scripts
     * @example
     * import Script from 'rhettjs/script';
     *
     * // Executed as: /rjs run myscript player1 -x=100 --name=Steve -abc
     * console.log(Script.caller.name);  // Player who ran the command
     * Script.argv.get('x')      // 100
     * Script.argv.get('name')   // "Steve"
     * Script.argv.get('a')      // true
     * Script.argv.get(0)        // "player1"
     */
    export const Script: {
        /**
         * The caller who executed /rjs run
         * Contains information about the command source (player, console, command block, etc.)
         */
        readonly caller: Caller;

        /**
         * Raw arguments array passed to the script
         */
        readonly args: string[];

        readonly argv: {
            /**
             * Get flag value by name or positional argument by index
             * @param flagOrIndex - Flag name or position index
             * @returns Value (string, number, boolean, or undefined)
             */
            get(flagOrIndex: string | number): string | number | boolean | undefined;

            /**
             * Get all positional arguments (non-flag)
             * @returns Array of positional arguments
             */
            getAll(): string[];

            /**
             * Check if flag exists
             * @param flag - Flag name
             * @returns True if flag present
             */
            hasFlag(flag: string): boolean;

            /** Raw arguments array */
            readonly raw: string[];
        };
    };

    export default Script;
}