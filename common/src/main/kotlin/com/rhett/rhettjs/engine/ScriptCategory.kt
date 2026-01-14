package com.rhett.rhettjs.engine

/**
 * Categories of scripts based on their execution context.
 */
enum class ScriptCategory(val dirName: String) {
    STARTUP("startup"),
    SERVER("server"),
    CLIENT("client"),
    UTILITY("scripts"),
    MODULES("modules")  // ES6 modules for import/export
}
