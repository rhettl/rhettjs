package com.rhett.rhettjs.engine

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.api.ConsoleAPI
import com.rhett.rhettjs.api.LoggerAPI
import org.mozilla.javascript.Context
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.readText

/**
 * Loads and manages global JavaScript libraries from rhettjs/globals/ directory (.js files)
 *
 * Global scripts use the IIFE pattern for encapsulation and can depend on each other
 * via alphabetical load order. They have access to console and logger APIs.
 */
object GlobalsLoader {

    private var globalScope: Scriptable? = null

    private val reservedNames = setOf(
        "console", "logger", "nbt", "world", "player", "server",
        "task", "schedule", "StartupEvents", "ServerEvents", "globals"
    )

    /**
     * Clean up Rhino error messages by removing Java object references.
     */
    private fun cleanErrorMessage(message: String): String {
        return message
            .replace(Regex("org\\.mozilla\\.javascript\\.Undefined@[0-9a-f]+"), "undefined")
            .replace(Regex("org\\.mozilla\\.javascript\\.[A-Za-z]+@[0-9a-f]+"), "<object>")
            .replace(" is not a function, it is undefined", " is not a function")
    }

    /**
     * Extract JavaScript-relevant stack trace from Rhino error.
     */
    private fun getJavaScriptStackTrace(e: RhinoException): String {
        return try {
            val scriptStack = e.scriptStackTrace
            if (scriptStack.isNullOrBlank()) {
                val fileName = e.sourceName() ?: "unknown"
                val lineNumber = e.lineNumber()
                "  at $fileName:$lineNumber"
            } else {
                scriptStack.lines()
                    .filter { it.isNotBlank() }
                    .joinToString("\n") { "  $it" }
            }
        } catch (ex: Exception) {
            "  at ${e.sourceName() ?: "unknown"}:${e.lineNumber()}"
        }
    }

    /**
     * Load or reload all global scripts from the globals directory.
     *
     * @param scriptsDir The base scripts directory (e.g., <minecraft>/rjs/)
     */
    fun reload(scriptsDir: Path) {
        RhettJSCommon.LOGGER.info("[RhettJS] Loading global scripts...")

        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()

            // Add base APIs that globals can depend on
            scope.put("console", scope, Context.javaToJS(ConsoleAPI(), scope))
            scope.put("logger", scope, Context.javaToJS(LoggerAPI(), scope))

            // Load globals directory
            val globalsDir = scriptsDir.resolve("globals")
            if (!globalsDir.exists()) {
                RhettJSCommon.LOGGER.info("[RhettJS] No globals directory, skipping")
                globalScope = scope
                return
            }

            val globalFiles = Files.walk(globalsDir, 1)
                .filter { it.extension == "js" }
                .sorted() // Alphabetical order (numeric prefixes for dependencies)
                .toList()

            if (globalFiles.isEmpty()) {
                RhettJSCommon.LOGGER.info("[RhettJS] No global scripts found")
                globalScope = scope
                return
            }

            // Track what gets added
            val beforeIds = scope.ids.map { it.toString() }.toSet()

            // Execute each global file
            globalFiles.forEach { file ->
                try {
                    RhettJSCommon.LOGGER.info("[RhettJS]   Loading: ${file.fileName}")

                    cx.evaluateString(
                        scope,
                        file.readText(),
                        "globals/${file.fileName}",
                        1,
                        null
                    )

                    RhettJSCommon.LOGGER.info("[RhettJS]   ✓ ${file.fileName}")
                } catch (e: RhinoException) {
                    val cleanedMessage = cleanErrorMessage(e.message ?: "Error")
                    val jsStack = getJavaScriptStackTrace(e)
                    RhettJSCommon.LOGGER.error("[RhettJS]   ✗ ${file.fileName}: $cleanedMessage\n$jsStack")
                    // Continue with other files
                } catch (e: Exception) {
                    RhettJSCommon.LOGGER.error("[RhettJS]   ✗ ${file.fileName}: ${e.message}", e)
                    // Continue with other files
                }
            }

            // Report what was added
            val afterIds = scope.ids.map { it.toString() }.toSet()
            val added = afterIds - beforeIds - setOf("console", "logger")

            if (added.isNotEmpty()) {
                val formattedGlobals = added.sorted().map { name ->
                    val value = scope.get(name, scope)
                    if (value is Undefined || value == Undefined.instance) {
                        // Grey italic formatting: \u001B[2m (dim) + \u001B[3m (italic)
                        "$name\u001B[2m\u001B[3m (undefined)\u001B[0m"
                    } else {
                        name
                    }
                }
                RhettJSCommon.LOGGER.info("[RhettJS] Globals: ${formattedGlobals.joinToString(", ")}")
            }

            // Warn about reserved names
            validateGlobalNames(added)

            globalScope = scope
            RhettJSCommon.LOGGER.info("[RhettJS] Loaded ${globalFiles.size} global scripts")

        } finally {
            Context.exit()
        }
    }

    /**
     * Inject loaded globals into a script scope.
     * Does not inject console/logger (those are added separately by ScriptEngine).
     * Also injects a 'globals' meta-object containing all globals and base APIs.
     *
     * @param scope The JavaScript scope to inject globals into
     */
    fun injectGlobals(scope: Scriptable) {
        val globalsSource = globalScope ?: return
        val cx = Context.getCurrentContext()

        // Create meta-object to hold all globals
        val globalsObject = cx.newObject(scope)

        globalsSource.ids.forEach { id ->
            val key = id.toString()
            val value = globalsSource.get(key, globalsSource)

            if (value != Scriptable.NOT_FOUND) {
                // Update parent scope for functions so they can access runtime variables
                if (value is Scriptable) {
                    value.parentScope = scope
                }

                // Add to globals meta-object (includes console, logger, etc.)
                globalsObject.put(key, globalsObject, value)

                // Skip base APIs for direct injection (added separately by ScriptEngine)
                if (key !in setOf("console", "logger", "nbt")) {
                    scope.put(key, scope, value)
                }
            }
        }

        // Inject the meta-object
        scope.put("globals", scope, globalsObject)
    }

    /**
     * Validate global names and warn if they conflict with reserved names.
     */
    private fun validateGlobalNames(names: Set<String>) {
        val conflicts = names.intersect(reservedNames)
        if (conflicts.isNotEmpty()) {
            RhettJSCommon.LOGGER.warn(
                "[RhettJS] Globals defined reserved names: ${conflicts.joinToString(", ")}"
            )
            RhettJSCommon.LOGGER.warn("[RhettJS] These will be overridden by built-in APIs")
        }
    }

    /**
     * Get list of loaded global names (excluding console/logger).
     *
     * @return List of global variable names that were loaded
     */
    fun getLoadedGlobals(): List<String> {
        val globals = globalScope ?: return emptyList()
        return globals.ids
            .map { it.toString() }
            .filter { it !in setOf("console", "logger", "nbt") }
            .sorted()
    }

    /**
     * Clear all loaded globals.
     * Used for testing and reload scenarios.
     */
    fun clear() {
        globalScope = null
    }
}
