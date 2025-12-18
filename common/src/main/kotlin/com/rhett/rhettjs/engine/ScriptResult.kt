package com.rhett.rhettjs.engine

/**
 * Result of script execution.
 */
sealed class ScriptResult {
    data class Success(val value: Any?) : ScriptResult()
    data class Error(val message: String, val exception: Throwable) : ScriptResult()
}
