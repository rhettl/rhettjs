// RhettJS Store API Type Definitions
// Version: 0.3.0
// Last updated: 2026-01-13

/** Namespaced store for organizing related data */
export interface NamespacedStore {
    /** Store a value */
    set(key: string, value: any): void;
    /** Retrieve a value */
    get(key: string): any | null;
    /** Check if key exists */
    has(key: string): boolean;
    /** Delete a key */
    delete(key: string): boolean;
    /** Clear all keys in this namespace */
    clear(): void;
    /** Get all keys */
    keys(): string[];
    /** Get number of items */
    size(): number;
    /** Get all entries as object */
    entries(): Record<string, any>;
}

declare module 'rhettjs/store' {
    /**
     * Ephemeral key-value store (persists until server restart)
     * @example
     * import Store from 'rhettjs/store';
     *
     * const positions = Store.namespace('positions');
     * positions.set('pos1', { x: 100, y: 64, z: 200 });
     */
    export const Store: {
        /**
         * Create or get a namespaced store
         * @param namespace - Namespace identifier
         * @returns Namespaced store instance
         */
        namespace(namespace: string): NamespacedStore;

        /** Get all namespace names */
        namespaces(): string[];

        /** Clear all data across all namespaces */
        clearAll(): void;

        /** Total items across all namespaces */
        size(): number;
    };

    export default Store;
}