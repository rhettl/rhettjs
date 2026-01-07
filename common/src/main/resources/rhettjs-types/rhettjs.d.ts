// RhettJS Core API Type Definitions (Barrel File)
// Version: 0.3.0
// Last updated: 2026-01-06
// Documentation: https://github.com/rhettjs/rhettjs

// ============================================================================
// Re-export all APIs
// ============================================================================

// Note: Runtime is global (like window or process), not exported
export { default as Store } from './store';
export { default as NBT } from './nbt';
export { default as Commands } from './commands';
export { default as Server } from './server';
export { default as World } from './world';
export { StructureNbt, LargeStructureNbt } from './structure';
export { WorldgenStructure } from './worldgen-structure';
export { default as Script } from './script';

// Re-export common types
export * from './types';

// ============================================================================
// Global Utilities
// ============================================================================

/**
 * Standard console logging
 */
declare global {
    // @ts-ignore
    namespace console {
        function log(...messages: any[]): void;
        function info(...messages: any[]): void;
        function warn(...messages: any[]): void;
        function error(...messages: any[]): void;
        function debug(...messages: any[]): void;
    }

    /**
     * Wait for N ticks before resolving (20 ticks = 1 second)
     * @param ticks - Number of ticks to wait
     * @returns Promise that resolves after delay
     * @example
     * await wait(20); // Wait 1 second
     * console.log('Done!');
     */
    function wait(ticks: number): Promise<void>;
}

// ============================================================================
// Re-export Runtime global
// ============================================================================

import './runtime'; // Load global Runtime declaration

// ============================================================================
// Ambient Module Declarations (for ES6 import support)
// ============================================================================

declare module 'rhettjs' {
    export { default as Store } from './store';
    export { default as NBT } from './nbt';
    export { default as Commands } from './commands';
    export { default as Server } from './server';
    export { default as World } from './world';
    export { StructureNbt, LargeStructureNbt } from './structure';
    export { WorldgenStructure } from './worldgen-structure';
    export { default as Script } from './script';
    export * from './types';
}

// Legacy module support for Runtime (even though it's global)
declare module 'rhettjs/runtime' {
    const Runtime: typeof globalThis.Runtime;
    export default Runtime;
}

declare module 'rhettjs/store' {
    export { default } from './store';
}

declare module 'rhettjs/nbt' {
    export { default } from './nbt';
}

declare module 'rhettjs/commands' {
    export { default } from './commands';
}

declare module 'rhettjs/server' {
    export { default } from './server';
}

declare module 'rhettjs/world' {
    export { default } from './world';
}

declare module 'rhettjs/structure' {
    export { StructureNbt, LargeStructureNbt } from './structure';
}

declare module 'rhettjs/script' {
    export { default } from './script';
}

declare module 'rhettjs/worldgen-structure' {
    export { WorldgenStructure } from './worldgen-structure';
}

// Legacy bare module support (for backward compatibility)
declare module 'Runtime' {
    const Runtime: typeof globalThis.Runtime;
    export default Runtime;
}

declare module 'Store' {
    export { default } from './store';
}

declare module 'NBT' {
    export { default } from './nbt';
}

declare module 'Commands' {
    export { default } from './commands';
}

declare module 'Server' {
    export { default } from './server';
}

declare module 'World' {
    export { default } from './world';
}

declare module 'Structure' {
    export { default } from './structure';
}

declare module 'StructureNbt' {
    export { StructureNbt } from './structure';
}

declare module 'LargeStructureNbt' {
    export { LargeStructureNbt } from './structure';
}

declare module 'Script' {
    export { default } from './script';
}

declare module 'WorldgenStructure' {
    export { WorldgenStructure } from './worldgen-structure';
}