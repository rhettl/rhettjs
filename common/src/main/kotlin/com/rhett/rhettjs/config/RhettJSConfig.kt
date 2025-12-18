package com.rhett.rhettjs.config

/**
 * Configuration for RhettJS mod.
 */
data class RhettJSConfig(
    /**
     * Master switch to enable/disable the entire mod.
     * When false, no scripts will be loaded or executed.
     */
    val enabled: Boolean = true,

    /**
     * Enable debug logging for engine operations.
     * This logs internal engine behavior like script loading, context creation, etc.
     * Does NOT affect script-level console.log() or logger calls.
     */
    val debug_logging: Boolean = false
)
