package com.rhett.rhettjs.engine

import java.nio.file.Path

/**
 * Metadata about a discovered script.
 */
data class ScriptInfo(
    val name: String,
    val path: Path,
    val category: ScriptCategory,
    val lastModified: Long,
    val status: ScriptStatus
)
