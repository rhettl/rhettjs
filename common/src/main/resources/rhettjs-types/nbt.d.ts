// RhettJS NBT API Type Definitions
// Version: 0.3.0
// Last updated: 2026-01-13

declare module 'rhettjs/nbt' {
    /**
     * NBT manipulation utilities
     * @example
     * import NBT from 'rhettjs/nbt';
     *
     * const compound = NBT.compound({ display: { Name: 'Custom Item' } });
     */
    export const NBT: {
        compound(data?: Record<string, any>): any;
        list(items?: any[]): any;
        string(value: string): any;
        int(value: number): any;
        double(value: number): any;
        byte(value: number): any;

        /**
         * Get value at path
         * @param nbt - NBT data
         * @param path - Dot-separated path (e.g., "display.Name")
         */
        get(nbt: any, path: string): any;

        /**
         * Set value at path
         * @param nbt - NBT data
         * @param path - Dot-separated path
         * @param value - Value to set
         */
        set(nbt: any, path: string, value: any): any;

        /**
         * Check if path exists
         * @param nbt - NBT data
         * @param path - Dot-separated path
         */
        has(nbt: any, path: string): boolean;

        /**
         * Delete path from NBT data
         * @param nbt - NBT data
         * @param path - Dot-separated path
         * @returns Modified NBT data
         */
        remove(nbt: any, path: string): any;

        /**
         * Merge NBT data
         * @param target - Target NBT
         * @param source - Source NBT
         * @param options - Merge options
         */
        merge(target: any, source: any, options?: { deep?: boolean }): any;
    };

    export default NBT;
}