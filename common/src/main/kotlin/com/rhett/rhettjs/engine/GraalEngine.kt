package com.rhett.rhettjs.engine

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.async.AsyncScheduler
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject

/**
 * Exception thrown when Runtime.exit() is called.
 * Used to gracefully terminate script execution.
 */
class ScriptExitException : RuntimeException("Script terminated via Runtime.exit()")

/**
 * GraalVM JavaScript engine management.
 * Handles context creation, bindings setup, and script execution with native async/await support.
 */
object GraalEngine {

    // Script execution timeout (mutable per-script via Runtime.setScriptTimeout)
    @Volatile
    private var scriptTimeoutMs: Long = 30000L // Default: 30 seconds

    // Scripts base directory for module resolution
    @Volatile
    private var scriptsBaseDir: java.nio.file.Path? = null

    /**
     * Set the scripts base directory (called during initialization).
     * Required for ES6 module resolution.
     */
    fun setScriptsDirectory(baseDir: java.nio.file.Path) {
        scriptsBaseDir = baseDir
        ConfigManager.debug("Set scripts base directory for module resolution: $baseDir")
    }

    /**
     * Create a new GraalVM context with ES2022 support and module resolution.
     *
     * @return A configured GraalVM Context
     */
    private fun createContext(): Context {
        val builder = Context.newBuilder("js")
            // Sandboxed - scripts only access explicitly injected APIs via bindings
            .allowExperimentalOptions(true)
            .option("js.esm-eval-returns-exports", "true")
            .option("js.ecmascript-version", "2022")  // ES2022 for modern features
            .option("js.top-level-await", "true")  // Enable top-level await

        // Enable file system access for ES6 imports
        // TODO: Module resolution currently uses actual file paths, not virtual paths
        //       This means imports are relative to the script's actual location:
        //       - startup/init.js: import X from '../modules/x.js'
        //       - scripts/util.js: import X from '../modules/x.js'
        //       Goal: Make all imports resolve from modules/ directory
        //       - startup/init.js: import X from './x.js' (resolves to modules/x.js)
        //       This requires custom module loader hooks or import transformation
        if (scriptsBaseDir != null) {
            val modulesDir = scriptsBaseDir!!.resolve("modules")
            builder.allowIO(true)
            builder.currentWorkingDirectory(modulesDir)
            ConfigManager.debug("Set import resolution base: $modulesDir (virtual URI not working yet)")
        }

        return builder.build()
    }

    /**
     * Execute a script with error handling.
     *
     * @param script The script metadata
     * @param additionalBindings Additional bindings to inject (platform-specific)
     * @return Result of execution (Success or Error)
     */
    fun executeScript(
        script: ScriptInfo,
        additionalBindings: Map<String, Any> = emptyMap()
    ): ScriptResult {
        ConfigManager.debug("Executing script: ${script.name} (category: ${script.category})")

        val context = createContext()
        return try {
            // Inject bindings based on script category
            injectBindings(context, script.category, additionalBindings)

            // Create source from file with virtual URI in modules/ for import resolution
            // TODO: Virtual URI doesn't affect import resolution in GraalVM
            //       GraalVM resolves imports relative to actual file location
            //       Need to implement custom module loader or transform imports
            val source = if (scriptsBaseDir != null) {
                val virtualUri = scriptsBaseDir!!.resolve("modules/${script.name}.js").toUri()
                Source.newBuilder("js", script.path.toFile())
                    .name(script.name)
                    .uri(virtualUri)  // Virtual path (doesn't work for imports yet)
                    .mimeType("application/javascript+module")  // Enable ES6 module parsing
                    .build()
            } else {
                Source.newBuilder("js", script.path.toFile())
                    .name(script.name)
                    .mimeType("application/javascript+module")  // Enable ES6 module parsing
                    .build()
            }

            ConfigManager.debug("Evaluating script: ${script.name}")

            // Execute script (blocks on top-level await)
            val result = context.eval(source)

            ConfigManager.debug("Script executed successfully: ${script.name}")
            ScriptResult.Success(result)

        } catch (e: PolyglotException) {
            // Check if this is a ScriptExitException wrapped by GraalVM
            if (e.isHostException && e.asHostException() is ScriptExitException) {
                ConfigManager.debug("Script terminated via Runtime.exit(): ${script.name}")
                return ScriptResult.Success(null)
            }

            val message = cleanErrorMessage(e)
            RhettJSCommon.LOGGER.error("[RhettJS] Script error in ${script.name}: $message")
            ScriptResult.Error(message, e)

        } catch (e: ScriptExitException) {
            // Graceful exit via Runtime.exit() (direct throw, not wrapped)
            ConfigManager.debug("Script terminated via Runtime.exit(): ${script.name}")
            ScriptResult.Success(null)

        } catch (e: Exception) {
            val message = e.message ?: "Unknown error"
            RhettJSCommon.LOGGER.error("[RhettJS] Unexpected error in ${script.name}: $message", e)
            ScriptResult.Error(message, e)

        } finally {
            context.close()
        }
    }

    /**
     * Inject JavaScript bindings into the context based on script category.
     */
    private fun injectBindings(
        context: Context,
        category: ScriptCategory,
        additionalBindings: Map<String, Any>
    ) {
        val bindings = context.getBindings("js")

        // Inject Console API
        val console = createConsoleAPI()
        bindings.putMember("console", console)
        ConfigManager.debug("Injected Console API")

        // Inject Runtime API
        val runtime = createRuntimeAPI(context)
        bindings.putMember("Runtime", runtime)
        ConfigManager.debug("Injected Runtime API")

        // Inject wait() function for async delays
        val waitFn = createWaitFunction(context)
        bindings.putMember("wait", waitFn)
        ConfigManager.debug("Injected wait() function")

        // Inject built-in modules for import (World, Structure, Store, NBT)
        injectBuiltinModules(bindings)

        // Inject Script.* for utility scripts
        if (category == ScriptCategory.UTILITY) {
            injectScriptContext(bindings, additionalBindings)
        }

        // Inject additional bindings (platform-specific)
        additionalBindings.forEach { (name, value) ->
            // Skip Caller/Args if they were already injected as Script.*
            if (category == ScriptCategory.UTILITY && (name == "Caller" || name == "Args")) {
                return@forEach
            }
            bindings.putMember(name, value)
            ConfigManager.debug("Injected binding: $name")
        }

        val baseBindings = 7 // console, Runtime, wait, World, Structure, Store, NBT
        val scriptBindings = if (category == ScriptCategory.UTILITY) 1 else 0  // Script.*
        ConfigManager.debug("Injected ${baseBindings + scriptBindings + additionalBindings.size} bindings for category: $category")
    }

    /**
     * Create the Console API for JavaScript scripts.
     * Provides console.log, console.warn, console.error, console.info, console.debug
     */
    private fun createConsoleAPI(): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            "log" to ProxyExecutable { args ->
                val message = args.joinToString(" ") { formatValue(it) }
                RhettJSCommon.LOGGER.info("[RhettJS-Script] $message")
                null
            },
            "warn" to ProxyExecutable { args ->
                val message = args.joinToString(" ") { formatValue(it) }
                RhettJSCommon.LOGGER.warn("[RhettJS-Script] $message")
                null
            },
            "error" to ProxyExecutable { args ->
                val message = args.joinToString(" ") { formatValue(it) }
                RhettJSCommon.LOGGER.error("[RhettJS-Script] $message")
                null
            },
            "info" to ProxyExecutable { args ->
                val message = args.joinToString(" ") { formatValue(it) }
                RhettJSCommon.LOGGER.info("[RhettJS-Script] $message")
                null
            },
            "debug" to ProxyExecutable { args ->
                val message = args.joinToString(" ") { formatValue(it) }
                ConfigManager.debug("[Script] $message")
                null
            }
        ))
    }

    /**
     * Format a GraalVM Value for console output.
     * Handles primitives, objects, arrays, etc.
     */
    private fun formatValue(value: Value): String {
        return when {
            value.isNull -> "null"
            value.isString -> value.asString()
            value.isNumber -> value.toString()
            value.isBoolean -> value.asBoolean().toString()
            value.hasArrayElements() -> {
                val elements = (0 until value.arraySize).map { formatValue(value.getArrayElement(it)) }
                "[${elements.joinToString(", ")}]"
            }
            value.hasMembers() -> {
                val members = value.memberKeys.map { key ->
                    "$key: ${formatValue(value.getMember(key))}"
                }
                "{${members.joinToString(", ")}}"
            }
            else -> value.toString()
        }
    }

    /**
     * Create the Runtime API for JavaScript scripts.
     * Provides Runtime.env properties and Runtime.exit() / Runtime.setScriptTimeout() functions
     */
    private fun createRuntimeAPI(context: Context): ProxyObject {
        // Create Runtime.env object with environment constants
        val env = ProxyObject.fromMap(mapOf(
            "TICKS_PER_SECOND" to 20,
            "IS_DEBUG" to ConfigManager.isDebugEnabled(),
            "RJS_VERSION" to RhettJSCommon.RJS_VERSION
        ))

        // Create Runtime object with env and functions
        return ProxyObject.fromMap(mapOf(
            "env" to env,
            "exit" to ProxyExecutable { _ ->
                throw ScriptExitException()
            },
            "setScriptTimeout" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("setScriptTimeout() requires a timeout argument (number in milliseconds)")
                }

                val timeoutMs = when {
                    args[0].isNumber -> args[0].asLong()
                    else -> throw IllegalArgumentException("setScriptTimeout() argument must be a number (milliseconds)")
                }

                if (timeoutMs < 1000L) {
                    throw IllegalArgumentException("Script timeout must be at least 1000ms (1 second), got ${timeoutMs}ms")
                }

                scriptTimeoutMs = timeoutMs
                RhettJSCommon.LOGGER.info("[RhettJS] Script timeout set to ${timeoutMs}ms")
                null
            }
        ))
    }

    /**
     * Create the wait() function for tick-based delays.
     * Returns a Promise that resolves after the specified number of ticks.
     *
     * @param context The GraalVM context
     * @return ProxyExecutable that creates a Promise-based delay
     */
    private fun createWaitFunction(context: Context): ProxyExecutable {
        return ProxyExecutable { args ->
            if (args.isEmpty()) {
                throw IllegalArgumentException("wait() requires a ticks argument (number of game ticks)")
            }

            val ticks = when {
                args[0].isNumber -> args[0].asInt()
                else -> throw IllegalArgumentException("wait() argument must be a number (ticks)")
            }

            if (ticks <= 0) {
                throw IllegalArgumentException("wait() ticks must be positive, got: $ticks")
            }

            // Schedule the delay and get a CompletableFuture
            val future = AsyncScheduler.scheduleWait(ticks)

            // Convert CompletableFuture to a JS Promise
            // GraalVM automatically converts CompletableFuture to Promise when returned
            context.asValue(future)
        }
    }

    /**
     * Inject built-in API modules that can be imported.
     * These modules are available via: import World from 'World'
     */
    private fun injectBuiltinModules(bindings: Value) {
        // TODO: Phase 5-7 - Implement actual APIs
        // For now, create placeholder objects so imports don't fail

        val worldPlaceholder = ProxyObject.fromMap(mapOf(
            "__placeholder" to true,
            "toString" to ProxyExecutable { _ -> "World API (not yet implemented)" }
        ))

        val structurePlaceholder = ProxyObject.fromMap(mapOf(
            "__placeholder" to true,
            "toString" to ProxyExecutable { _ -> "Structure API (not yet implemented)" }
        ))

        val storePlaceholder = ProxyObject.fromMap(mapOf(
            "__placeholder" to true,
            "toString" to ProxyExecutable { _ -> "Store API (not yet implemented)" }
        ))

        val nbtPlaceholder = ProxyObject.fromMap(mapOf(
            "__placeholder" to true,
            "toString" to ProxyExecutable { _ -> "NBT API (not yet implemented)" }
        ))

        bindings.putMember("World", worldPlaceholder)
        bindings.putMember("Structure", structurePlaceholder)
        bindings.putMember("Store", storePlaceholder)
        bindings.putMember("NBT", nbtPlaceholder)

        ConfigManager.debug("Injected built-in module placeholders (World, Structure, Store, NBT)")
    }

    /**
     * Inject Script.* context for utility scripts (rjs/scripts/).
     * Provides Script.caller and Script.args for command-invoked scripts.
     */
    private fun injectScriptContext(bindings: Value, additionalBindings: Map<String, Any>) {
        // Extract Caller and Args from additionalBindings if provided
        val caller = additionalBindings["Caller"]
        val args = additionalBindings["Args"]

        if (caller != null || args != null) {
            val scriptContext = mutableMapOf<String, Any?>()
            if (caller != null) scriptContext["caller"] = caller
            if (args != null) scriptContext["args"] = args

            val scriptProxy = ProxyObject.fromMap(scriptContext)
            bindings.putMember("Script", scriptProxy)
            ConfigManager.debug("Injected Script.caller and Script.args")
        }
    }

    /**
     * Clean up GraalVM error messages for better user experience.
     */
    private fun cleanErrorMessage(e: PolyglotException): String {
        val message = e.message ?: "Script error"

        // Include source location if available
        val location = if (e.sourceLocation != null) {
            val loc = e.sourceLocation
            "\n  at ${loc.source.name}:${loc.startLine}:${loc.startColumn}"
        } else {
            ""
        }

        return "$message$location"
    }
}