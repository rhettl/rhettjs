package com.rhett.rhettjs.engine

/**
 * Categories of scripts based on their execution context.
 */
enum class ScriptCategory(val dirName: String) {
    STARTUP("startup"),
    SERVER("server"),
    UTILITY("scripts")
}
