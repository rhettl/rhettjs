package com.rhett.rhettjs.engine

/**
 * Execution context determines which APIs are available to scripts.
 *
 * - SERVER: Server-side scripts with access to World, Server, Commands + universal APIs
 * - CLIENT: Client-side scripts with access to UI + universal APIs
 * - UNIVERSAL: Scripts with access only to universal APIs (Store, NBT, Runtime, Console, StructureNbt)
 */
enum class ExecutionContext {
    SERVER,    // World, Server, Commands, WorldgenStructure + universal
    CLIENT,    // UI + universal
    UNIVERSAL  // Store, NBT, StructureNbt, LargeStructureNbt, Runtime, Console, wait()
}

/**
 * Extension property to get the execution context for a script category.
 * Determines which APIs will be available when scripts of this category are executed.
 */
val ScriptCategory.executionContext: ExecutionContext
    get() = when (this) {
        ScriptCategory.SERVER -> ExecutionContext.SERVER
        ScriptCategory.CLIENT -> ExecutionContext.CLIENT
        ScriptCategory.STARTUP -> ExecutionContext.UNIVERSAL
        ScriptCategory.UTILITY -> ExecutionContext.UNIVERSAL
        ScriptCategory.MODULES -> ExecutionContext.UNIVERSAL
    }
