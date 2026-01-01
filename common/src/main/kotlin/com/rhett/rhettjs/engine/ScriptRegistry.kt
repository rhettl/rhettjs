package com.rhett.rhettjs.engine

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

/**
 * Registry for discovered scripts.
 * Handles script discovery, validation, and inventory management.
 */
object ScriptRegistry {
    private val scripts = ConcurrentHashMap<String, ScriptInfo>()
    private var scanned = false

    /**
     * Scan the base directory for scripts and validate them.
     *
     * @param baseDir The base directory to scan (e.g., <minecraft>/rjs/)
     */
    fun scan(baseDir: Path) {
        ConfigManager.debug("Starting script scan in: $baseDir")
        scripts.clear()
        scanned = true

        ScriptCategory.values().forEach { category ->
            val dir = baseDir.resolve(category.dirName)
            ConfigManager.debug("Scanning category: ${category.dirName} at $dir")

            if (!dir.exists()) {
                RhettJSCommon.LOGGER.warn("[RhettJS] Directory not found: ${category.dirName}")
                ensureDirectoryExists(dir)
                return@forEach
            }

            scanDirectory(dir, category, baseDir)
        }

        RhettJSCommon.LOGGER.info("[RhettJS] Found ${scripts.size} scripts")
        ConfigManager.debugLazy { "Script inventory: ${scripts.keys.joinToString()}" }
    }

    /**
     * Ensure a directory exists, creating it if necessary.
     */
    private fun ensureDirectoryExists(dir: Path) {
        if (!dir.exists()) {
            Files.createDirectories(dir)
            RhettJSCommon.LOGGER.info("[RhettJS] Created directory: ${dir.fileName}")
        }
    }

    /**
     * Recursively scan a directory for .js files.
     */
    private fun scanDirectory(dir: Path, category: ScriptCategory, baseDir: Path) {
        ConfigManager.debug("Walking directory tree: $dir")

        Files.walk(dir)
            .filter { it.extension == "js" }
            .forEach { file ->
                val relativePath = baseDir.relativize(file)
                val fullName = relativePath.toString().removeSuffix(".js")

                // For utility scripts (scripts/), strip the category prefix
                // Since /rjs run ONLY runs utility scripts, showing "scripts/abc" is redundant
                val name = if (category == ScriptCategory.UTILITY) {
                    fullName.removePrefix("${category.dirName}/")
                } else {
                    fullName
                }

                ConfigManager.debug("Found script file: $name at $file")

                val status = validateScript(file, category)

                scripts[name] = ScriptInfo(
                    name = name,
                    path = file,
                    category = category,
                    lastModified = Files.getLastModifiedTime(file).toMillis(),
                    status = status
                )

                when (status) {
                    ScriptStatus.LOADED -> {
                        RhettJSCommon.LOGGER.info("[RhettJS]   ✓ $name")
                        ConfigManager.debug("Script validated successfully: $name")
                    }
                    ScriptStatus.ERROR -> {
                        RhettJSCommon.LOGGER.warn("[RhettJS]   ✗ $name (syntax error)")
                        ConfigManager.debug("Script validation failed: $name")
                    }
                    else -> {}
                }
            }
    }

    /**
     * Validate a script's syntax without executing it.
     * Uses GraalVM's parser to check for syntax errors only.
     *
     * @param file The script file to validate
     * @param category The script category
     * @return The status of the script (LOADED or ERROR)
     */
    private fun validateScript(file: Path, category: ScriptCategory): ScriptStatus {
        // Skip validation for MODULES - they're only imported, not executed directly
        // Module syntax will be validated when imported by other scripts
        if (category == ScriptCategory.MODULES) {
            ConfigManager.debug("Skipping validation for module: ${file.fileName}")
            return ScriptStatus.LOADED
        }

        ConfigManager.debug("Validating syntax for: ${file.fileName}")

        return try {
            // Create source and let GraalVM parse it
            // This will throw PolyglotException with isSyntaxError=true for syntax errors
            // Use ES6 module MIME type to support import/export syntax
            val source = org.graalvm.polyglot.Source.newBuilder("js", file.toFile())
                .name(file.fileName.toString())
                .mimeType("application/javascript+module")
                .build()

            // Just building the source is not enough - we need to parse it
            // Use a minimal sandboxed context for syntax validation only
            org.graalvm.polyglot.Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .option("engine.WarnInterpreterOnly", "false")
                .build().use { context ->
                    // Parse validates syntax without executing
                    context.parse(source)
                }

            ConfigManager.debug("Syntax validation passed for: ${file.fileName}")
            ScriptStatus.LOADED
        } catch (e: org.graalvm.polyglot.PolyglotException) {
            if (e.isSyntaxError) {
                ConfigManager.debug("Syntax error in ${file.fileName}: ${e.message}")
                ScriptStatus.ERROR
            } else {
                // Other errors (e.g., reference errors) - treat as loaded, will fail at runtime
                ConfigManager.debug("Non-syntax error during validation: ${e.message}")
                ScriptStatus.LOADED
            }
        } catch (e: Exception) {
            ConfigManager.debug("Unexpected error validating ${file.fileName}: ${e.message}")
            ScriptStatus.ERROR
        }
    }

    /**
     * Get a script by name, optionally filtered by category.
     *
     * @param name The script name
     * @param category Optional category filter
     * @return The script info, or null if not found
     */
    fun getScript(name: String, category: ScriptCategory? = null): ScriptInfo? {
        return if (category != null) {
            scripts.values.firstOrNull { it.name == name && it.category == category }
        } else {
            scripts[name]
        }
    }

    /**
     * Get all scripts in a category.
     *
     * @param category The category to filter by
     * @return List of scripts in the category
     */
    fun getScripts(category: ScriptCategory): List<ScriptInfo> {
        return scripts.values.filter { it.category == category }
    }

    /**
     * Get all scripts.
     *
     * @return All registered scripts
     */
    fun getAllScripts(): List<ScriptInfo> {
        return scripts.values.toList()
    }

    /**
     * Mark a script as failed.
     *
     * @param name The script name
     * @param error The error that occurred
     */
    fun markFailed(name: String, error: Throwable) {
        scripts[name]?.let {
            scripts[name] = it.copy(status = ScriptStatus.ERROR)
        }
    }

    /**
     * Get all scripts that failed to load.
     *
     * @param category Optional category filter
     * @return List of failed scripts
     */
    fun getFailedScripts(category: ScriptCategory? = null): List<ScriptInfo> {
        return scripts.values.filter {
            it.status == ScriptStatus.ERROR &&
                    (category == null || it.category == category)
        }
    }

    /**
     * Clear the registry.
     */
    fun clear() {
        scripts.clear()
        scanned = false
    }

    /**
     * Check if the registry has been scanned.
     *
     * @return true if scan() has been called
     */
    fun hasScanned(): Boolean = scanned
}
