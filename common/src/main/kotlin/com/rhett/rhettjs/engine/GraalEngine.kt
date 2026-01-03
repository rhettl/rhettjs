package com.rhett.rhettjs.engine

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.async.AsyncScheduler
import com.rhett.rhettjs.api.StoreAPI
import com.rhett.rhettjs.api.NamespacedStore
import com.rhett.rhettjs.commands.CustomCommandRegistry
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.io.FileSystem
import org.graalvm.polyglot.io.IOAccess
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

    // Shared GraalVM context (created once, reused for all scripts)
    @Volatile
    private var sharedContext: Context? = null

    // Custom command registry for Commands API
    private val commandRegistry = CustomCommandRegistry()

    // Pre-compiled JavaScript helper functions (cached to avoid classloader issues)
    @Volatile
    private var jsNBTSetHelper: Value? = null
    @Volatile
    private var jsNBTDeleteHelper: Value? = null
    @Volatile
    private var jsNBTMergeShallowHelper: Value? = null
    @Volatile
    private var jsNBTMergeDeepHelper: Value? = null
    @Volatile
    private var jsUndefinedValue: Value? = null

    /**
     * Set the scripts base directory (called during initialization).
     * Required for ES6 module resolution.
     */
    fun setScriptsDirectory(baseDir: java.nio.file.Path) {
        scriptsBaseDir = baseDir
        ConfigManager.debug("Set scripts base directory for module resolution: $baseDir")
    }

    /**
     * Reset the GraalVM engine (called on reload).
     * Closes the existing context and clears cached state.
     */
    fun reset() {
        sharedContext?.close()
        sharedContext = null
        jsNBTSetHelper = null
        jsNBTDeleteHelper = null
        jsNBTMergeShallowHelper = null
        jsNBTMergeDeepHelper = null
        jsUndefinedValue = null
        commandRegistry.clear()
        ConfigManager.debug("GraalVM engine reset")
    }

    /**
     * Get the custom command registry for platform integration.
     * Used by ScriptSystemInitializer to register commands with Brigadier after startup scripts load.
     */
    fun getCommandRegistry(): CustomCommandRegistry = commandRegistry

    /**
     * Store the command dispatcher for later command registration.
     * Called during command registration event (before server/startup scripts run).
     *
     * @param dispatcher The Minecraft command dispatcher
     * @param buildContext The command build context for item/block arguments
     */
    fun storeCommandDispatcher(
        dispatcher: com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>,
        buildContext: net.minecraft.commands.CommandBuildContext
    ) {
        val context = getOrCreateContext()
        commandRegistry.storeDispatcher(dispatcher, context, buildContext)
        ConfigManager.debug("Stored command dispatcher, GraalVM context, and build context")
    }

    /**
     * Create a new GraalVM context with ES2022 support and custom module resolution.
     * Uses RhettJSFileSystem to enable bare specifier imports for built-in APIs.
     *
     * @return A configured GraalVM Context
     */
    private fun createContext(): Context {
        val builder = Context.newBuilder("js")
            // Allow all access for development (TODO: lock down for production)
            .allowAllAccess(true)
            .allowExperimentalOptions(true)
            .option("js.esm-eval-returns-exports", "true")
            .option("js.ecmascript-version", "2022")  // ES2022 for modern features
            .option("js.top-level-await", "true")  // Enable top-level await
            .option("engine.WarnInterpreterOnly", "false")  // Suppress JVMCI warning

        // Set up custom FileSystem for module resolution
        // This enables bare specifier imports like: import World from 'World'
        if (scriptsBaseDir != null) {
            val modulesDir = scriptsBaseDir!!.resolve("modules").toAbsolutePath()

            // Get default FileSystem and wrap it with our custom implementation
            val defaultFS = FileSystem.newDefaultFileSystem()
            val customFS = RhettJSFileSystem(defaultFS)

            // Create IOAccess with custom FileSystem
            val ioAccess = IOAccess.newBuilder()
                .fileSystem(customFS)
                .build()

            builder.allowIO(ioAccess)
            builder.currentWorkingDirectory(modulesDir)

            ConfigManager.debug("Set up custom FileSystem for module resolution")
            ConfigManager.debug("Working directory: $modulesDir")
        }

        return builder.build()
    }

    /**
     * Get or create the shared GraalVM context.
     * Creates the context on first use, then reuses it for all subsequent scripts.
     */
    private fun getOrCreateContext(): Context {
        return sharedContext ?: synchronized(this) {
            sharedContext ?: createContext().also { ctx ->
                sharedContext = ctx
                initializeJSHelpers(ctx)
                ConfigManager.debug("Created shared GraalVM context with pre-compiled helpers")
            }
        }
    }

    /**
     * Initialize pre-compiled JavaScript helper functions.
     * This avoids classloader issues when calling context.eval() from within running scripts.
     */
    private fun initializeJSHelpers(context: Context) {
        // NBT.set() helper
        jsNBTSetHelper = context.eval("js", """
            (function(obj, path, value) {
                const keys = path.split('.').flatMap(k => {
                    const match = k.match(/^(.+?)\[(\d+)\]$/);
                    return match ? [match[1], parseInt(match[2])] : [k];
                });

                function deepClone(val) {
                    if (Array.isArray(val)) return [...val];
                    if (typeof val === 'object' && val !== null) return {...val};
                    return val;
                }

                function setPath(obj, keys, value) {
                    if (keys.length === 0) return value;

                    const [key, ...rest] = keys;
                    const cloned = deepClone(obj);

                    if (Array.isArray(cloned)) {
                        cloned[key] = setPath(cloned[key], rest, value);
                    } else {
                        cloned[key] = setPath(cloned[key], rest, value);
                    }

                    return cloned;
                }

                return setPath(obj, keys, value);
            })
        """.trimIndent())

        // NBT.delete() helper
        jsNBTDeleteHelper = context.eval("js", """
            (function(obj, path) {
                const keys = path.split('.').flatMap(k => {
                    const match = k.match(/^(.+?)\[(\d+)\]$/);
                    return match ? [match[1], parseInt(match[2])] : [k];
                });

                function deepClone(val) {
                    if (Array.isArray(val)) return [...val];
                    if (typeof val === 'object' && val !== null) return {...val};
                    return val;
                }

                function deletePath(obj, keys) {
                    if (keys.length === 0) return obj;
                    if (keys.length === 1) {
                        const cloned = deepClone(obj);
                        if (Array.isArray(cloned)) {
                            cloned.splice(keys[0], 1);
                        } else {
                            delete cloned[keys[0]];
                        }
                        return cloned;
                    }

                    const [key, ...rest] = keys;
                    const cloned = deepClone(obj);
                    cloned[key] = deletePath(cloned[key], rest);
                    return cloned;
                }

                return deletePath(obj, keys);
            })
        """.trimIndent())

        // NBT.merge() shallow helper
        jsNBTMergeShallowHelper = context.eval("js", """
            (function(base, updates) {
                return {...base, ...updates};
            })
        """.trimIndent())

        // NBT.merge() deep helper
        jsNBTMergeDeepHelper = context.eval("js", """
            (function(base, updates) {
                function deepMerge(target, source) {
                    const result = Array.isArray(target) ? [...target] : {...target};

                    for (const key in source) {
                        if (source.hasOwnProperty(key)) {
                            if (source[key] && typeof source[key] === 'object' && !Array.isArray(source[key])) {
                                result[key] = result[key] && typeof result[key] === 'object'
                                    ? deepMerge(result[key], source[key])
                                    : {...source[key]};
                            } else {
                                result[key] = source[key];
                            }
                        }
                    }

                    return result;
                }

                return deepMerge(base, updates);
            })
        """.trimIndent())

        // JavaScript undefined value
        jsUndefinedValue = context.eval("js", "undefined")

        ConfigManager.debug("Initialized ${4} JavaScript helper functions")
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

        val context = getOrCreateContext()
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
                    .cached(false)  // Disable caching so scripts can be reloaded
                    .build()
            } else {
                Source.newBuilder("js", script.path.toFile())
                    .name(script.name)
                    .mimeType("application/javascript+module")  // Enable ES6 module parsing
                    .cached(false)  // Disable caching so scripts can be reloaded
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

        // Inject Script.* for utility scripts (or remove if not utility)
        if (category == ScriptCategory.UTILITY) {
            injectScriptContext(bindings, additionalBindings)
        } else {
            // Remove Script binding if it exists from previous executions
            if (bindings.hasMember("Script")) {
                bindings.removeMember("Script")
                ConfigManager.debug("Removed Script binding for non-utility script")
            }
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

        val baseBindings = 9 // console, Runtime, wait, World, Structure, Store, NBT, Server, Commands
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
     * Create a GraalVM proxy for StoreAPI.
     */
    private fun createStoreAPIProxy(): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            "namespace" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("namespace() requires a namespace name")
                }
                val namespace = args[0].asString()
                val store = StoreAPI.namespace(namespace)
                createNamespacedStoreProxy(store)
            },
            "namespaces" to ProxyExecutable { _ ->
                StoreAPI.namespaces()
            },
            "clearAll" to ProxyExecutable { _ ->
                StoreAPI.clearAll()
                null
            },
            "size" to ProxyExecutable { _ ->
                StoreAPI.size()
            }
        ))
    }

    /**
     * Create a GraalVM proxy for a NamespacedStore instance.
     */
    private fun createNamespacedStoreProxy(store: NamespacedStore): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            "set" to ProxyExecutable { args ->
                if (args.size < 2) {
                    throw IllegalArgumentException("set() requires key and value arguments")
                }
                val key = args[0].asString()
                val value = if (args[1].isNull) null else args[1]
                store.set(key, value)
                null
            },
            "get" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("get() requires a key argument")
                }
                val key = args[0].asString()
                store.get(key)
            },
            "has" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("has() requires a key argument")
                }
                val key = args[0].asString()
                store.has(key)
            },
            "delete" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("delete() requires a key argument")
                }
                val key = args[0].asString()
                store.delete(key)
            },
            "clear" to ProxyExecutable { _ ->
                store.clear()
                null
            },
            "keys" to ProxyExecutable { _ ->
                store.keys()
            },
            "size" to ProxyExecutable { _ ->
                store.size()
            },
            "entries" to ProxyExecutable { _ ->
                store.entries()
            }
        ))
    }

    /**
     * Create NBT API proxy for JavaScript.
     * Provides helper functions for creating and manipulating NBT data structures.
     */
    private fun createNBTAPIProxy(): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            // NBT constructors - just return the values as-is (no conversion needed for pure JS)
            "compound" to ProxyExecutable { args ->
                if (args.isEmpty()) emptyMap<String, Any>() else args[0]
            },
            "list" to ProxyExecutable { args ->
                if (args.isEmpty()) emptyList<Any>() else args[0]
            },
            "string" to ProxyExecutable { args ->
                if (args.isEmpty()) "" else args[0]
            },
            "int" to ProxyExecutable { args ->
                if (args.isEmpty()) 0 else args[0]
            },
            "double" to ProxyExecutable { args ->
                if (args.isEmpty()) 0.0 else args[0]
            },
            "byte" to ProxyExecutable { args ->
                if (args.isEmpty()) 0 else args[0]
            },

            // NBT query methods - get/set/has/delete with path support
            "get" to ProxyExecutable { args ->
                if (args.size < 2) return@ProxyExecutable null
                try {
                    val nbt = convertGraalValueToKotlin(args[0])
                    val path = if (args[1].isString) args[1].asString() else args[1].toString()
                    getNBTValue(nbt, path)
                } catch (e: Exception) {
                    ConfigManager.debug("NBT.get error: ${e.message}")
                    throw IllegalArgumentException("NBT.get requires (nbt, path): ${e.message}")
                }
            },
            "set" to ProxyExecutable { args ->
                if (args.size < 3) throw IllegalArgumentException("set() requires nbt, path, and value")
                try {
                    val path = if (args[1].isString) args[1].asString() else args[1].toString()
                    // Work with GraalVM Values directly to preserve JS object types
                    setNBTValueJS(args[0], path, args[2])
                } catch (e: Exception) {
                    ConfigManager.debug("NBT.set error: ${e.message}")
                    throw IllegalArgumentException("NBT.set requires (nbt, path, value): ${e.message}")
                }
            },
            "has" to ProxyExecutable { args ->
                if (args.size < 2) return@ProxyExecutable false
                try {
                    val nbt = convertGraalValueToKotlin(args[0])
                    val path = if (args[1].isString) args[1].asString() else args[1].toString()
                    hasNBTValue(nbt, path)
                } catch (e: Exception) {
                    false
                }
            },
            "delete" to ProxyExecutable { args ->
                if (args.size < 2) throw IllegalArgumentException("delete() requires nbt and path")
                try {
                    val path = if (args[1].isString) args[1].asString() else args[1].toString()
                    // Work with GraalVM Values directly to preserve JS object types
                    deleteNBTValueJS(args[0], path)
                } catch (e: Exception) {
                    ConfigManager.debug("NBT.delete error: ${e.message}")
                    throw IllegalArgumentException("NBT.delete requires (nbt, path): ${e.message}")
                }
            },
            "merge" to ProxyExecutable { args ->
                if (args.size < 2) throw IllegalArgumentException("merge() requires base and updates")
                try {
                    // Optional deep parameter (args[2]), defaults to false (shallow merge)
                    val deep = if (args.size >= 3 && args[2].isBoolean) args[2].asBoolean() else false
                    mergeNBTValueJS(args[0], args[1], deep)
                } catch (e: Exception) {
                    ConfigManager.debug("NBT.merge error: ${e.message}")
                    throw IllegalArgumentException("NBT.merge requires (base, updates[, deep]): ${e.message}")
                }
            }
        ))
    }

    /**
     * Set value in NBT structure (immutable - returns new structure).
     * Works with GraalVM Values directly to preserve JS object types.
     */
    private fun setNBTValueJS(nbtValue: Value, path: String, newValue: Value): Any {
        // Use pre-compiled helper to avoid classloader issues
        val helper = jsNBTSetHelper ?: throw IllegalStateException("NBT helper not initialized")
        return helper.execute(nbtValue, path, newValue)
    }

    /**
     * Delete value from NBT structure (immutable - returns new structure).
     * Works with GraalVM Values directly to preserve JS object types.
     */
    private fun deleteNBTValueJS(nbtValue: Value, path: String): Any {
        // Use pre-compiled helper to avoid classloader issues
        val helper = jsNBTDeleteHelper ?: throw IllegalStateException("NBT helper not initialized")
        return helper.execute(nbtValue, path)
    }

    /**
     * Merge two NBT structures (immutable - returns new structure).
     * Works with GraalVM Values directly to preserve JS object types.
     *
     * @param baseValue The base NBT object
     * @param updatesValue The updates to merge in
     * @param deep If true, performs deep merge; if false, shallow merge (default)
     */
    private fun mergeNBTValueJS(baseValue: Value, updatesValue: Value, deep: Boolean): Any {
        // Use pre-compiled helper to avoid classloader issues
        val helper = if (deep) {
            jsNBTMergeDeepHelper ?: throw IllegalStateException("NBT helper not initialized")
        } else {
            jsNBTMergeShallowHelper ?: throw IllegalStateException("NBT helper not initialized")
        }
        return helper.execute(baseValue, updatesValue)
    }

    /**
     * Get value from NBT structure using path notation (e.g., "tag.Damage" or "tag.Enchantments[0].id")
     */
    private fun getNBTValue(nbt: Any?, path: String): Any? {
        if (nbt == null) return null

        val parts = parsePath(path)
        var current: Any? = nbt

        for (part in parts) {
            when (part) {
                is PathKey -> {
                    if (current is Map<*, *>) {
                        current = current[part.key]
                    } else {
                        return null
                    }
                }
                is PathIndex -> {
                    if (current is List<*>) {
                        if (part.index < current.size) {
                            current = current[part.index]
                        } else {
                            return null
                        }
                    } else {
                        return null
                    }
                }
            }
        }

        return current
    }

    /**
     * Set value in NBT structure (immutable - returns new structure)
     */
    private fun setNBTValue(nbt: Any?, path: String, value: Any?): Any? {
        if (nbt == null) return null

        val parts = parsePath(path)
        return setNBTValueRecursive(nbt, parts, 0, value)
    }

    private fun setNBTValueRecursive(current: Any?, parts: List<PathPart>, index: Int, value: Any?): Any? {
        if (index >= parts.size) return value

        val part = parts[index]

        return when (part) {
            is PathKey -> {
                if (current is Map<*, *>) {
                    val mutableCopy = current.toMutableMap()
                    mutableCopy[part.key] = setNBTValueRecursive(current[part.key], parts, index + 1, value)
                    mutableCopy
                } else {
                    current
                }
            }
            is PathIndex -> {
                if (current is List<*>) {
                    val mutableCopy = current.toMutableList()
                    if (part.index < mutableCopy.size) {
                        mutableCopy[part.index] = setNBTValueRecursive(current[part.index], parts, index + 1, value)
                    }
                    mutableCopy
                } else {
                    current
                }
            }
        }
    }

    /**
     * Check if path exists in NBT structure
     */
    private fun hasNBTValue(nbt: Any?, path: String): Boolean {
        return getNBTValue(nbt, path) != null
    }

    /**
     * Delete value from NBT structure (immutable - returns new structure)
     */
    private fun deleteNBTValue(nbt: Any?, path: String): Any? {
        if (nbt == null) return null

        val parts = parsePath(path)
        return deleteNBTValueRecursive(nbt, parts, 0)
    }

    private fun deleteNBTValueRecursive(current: Any?, parts: List<PathPart>, index: Int): Any? {
        if (current == null || index >= parts.size) return current

        val part = parts[index]
        val isLast = index == parts.size - 1

        return when (part) {
            is PathKey -> {
                if (current is Map<*, *>) {
                    val mutableCopy = current.toMutableMap()
                    if (isLast) {
                        mutableCopy.remove(part.key)
                    } else {
                        mutableCopy[part.key] = deleteNBTValueRecursive(current[part.key], parts, index + 1)
                    }
                    mutableCopy
                } else {
                    current
                }
            }
            is PathIndex -> {
                if (current is List<*>) {
                    val mutableCopy = current.toMutableList()
                    if (isLast && part.index < mutableCopy.size) {
                        mutableCopy.removeAt(part.index)
                    } else if (part.index < mutableCopy.size) {
                        mutableCopy[part.index] = deleteNBTValueRecursive(current[part.index], parts, index + 1)
                    }
                    mutableCopy
                } else {
                    current
                }
            }
        }
    }

    /**
     * Parse path notation into parts (e.g., "tag.Damage" -> [PathKey("tag"), PathKey("Damage")])
     */
    private fun parsePath(path: String): List<PathPart> {
        val parts = mutableListOf<PathPart>()
        var current = ""
        var i = 0

        while (i < path.length) {
            when (path[i]) {
                '.' -> {
                    if (current.isNotEmpty()) {
                        parts.add(PathKey(current))
                        current = ""
                    }
                }
                '[' -> {
                    if (current.isNotEmpty()) {
                        parts.add(PathKey(current))
                        current = ""
                    }
                    // Parse array index
                    val endBracket = path.indexOf(']', i)
                    if (endBracket != -1) {
                        val indexStr = path.substring(i + 1, endBracket)
                        parts.add(PathIndex(indexStr.toInt()))
                        i = endBracket
                    }
                }
                else -> {
                    current += path[i]
                }
            }
            i++
        }

        if (current.isNotEmpty()) {
            parts.add(PathKey(current))
        }

        return parts
    }

    private sealed class PathPart
    private data class PathKey(val key: String) : PathPart()
    private data class PathIndex(val index: Int) : PathPart()

    /**
     * Convert GraalVM Value to Kotlin types
     */
    private fun convertGraalValueToKotlin(value: Value): Any? {
        return when {
            value.isNull -> null
            value.isBoolean -> value.asBoolean()
            value.isString -> value.asString()
            value.isNumber -> {
                when {
                    value.fitsInInt() -> value.asInt()
                    value.fitsInLong() -> value.asLong()
                    value.fitsInDouble() -> value.asDouble()
                    else -> value.asDouble()
                }
            }
            value.hasArrayElements() -> {
                (0 until value.arraySize).map { i ->
                    convertGraalValueToKotlin(value.getArrayElement(i))
                }
            }
            value.hasMembers() -> {
                val map = mutableMapOf<String, Any?>()
                value.memberKeys.forEach { key ->
                    map[key] = convertGraalValueToKotlin(value.getMember(key))
                }
                map
            }
            else -> value.asString()
        }
    }

    /**
     * Create Structure API proxy for JavaScript.
     * All methods return Promises (async file I/O and world operations).
     *
     * Since we're in a ProxyExecutable context, we can't safely eval new code.
     * Instead, we use a simpler approach: return objects that GraalVM will convert to Promises.
     */
    private fun createStructureAPIProxy(): ProxyObject {
        val context = getOrCreateContext()

        // Create Promise helper functions once and cache them
        val promiseResolve = context.eval("js", "(value) => Promise.resolve(value)")
        val promiseReject = context.eval("js", "(msg) => Promise.reject(new Error(msg))")

        return ProxyObject.fromMap(mapOf(
            "load" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    promiseReject.execute("load() requires a structure name")
                } else {
                    // TODO: Implement actual file loading
                    promiseReject.execute("Structure.load() not yet implemented")
                }
            },
            "save" to ProxyExecutable { args ->
                if (args.size < 2) {
                    promiseReject.execute("save() requires name and data")
                } else {
                    // TODO: Implement actual file saving
                    promiseResolve.execute(null)
                }
            },
            "delete" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    promiseReject.execute("delete() requires a structure name")
                } else {
                    // TODO: Implement actual file deletion
                    promiseResolve.execute(false)
                }
            },
            "exists" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    promiseReject.execute("exists() requires a structure name")
                } else {
                    // TODO: Implement actual file check
                    promiseResolve.execute(false)
                }
            },
            "list" to ProxyExecutable { args ->
                // Optional pool parameter
                // TODO: Implement actual file listing
                promiseResolve.execute(emptyList<String>())
            },
            "place" to ProxyExecutable { args ->
                if (args.size < 2) {
                    promiseReject.execute("place() requires name and position")
                } else {
                    // Optional rotation parameter (args[2])
                    // TODO: Implement actual structure placement
                    promiseResolve.execute(null)
                }
            },
            "capture" to ProxyExecutable { args ->
                if (args.size < 3) {
                    promiseReject.execute("capture() requires name, pos1, and pos2")
                } else {
                    // TODO: Implement actual structure capture
                    promiseResolve.execute(null)
                }
            }
        ))
    }

    /**
     * Create World API proxy for JavaScript.
     * All methods return Promises except for the dimensions property.
     */
    private fun createWorldAPIProxy(): ProxyObject {
        val context = getOrCreateContext()

        // Create Promise helper functions
        val promiseResolve = context.eval("js", "(value) => Promise.resolve(value)")
        val promiseReject = context.eval("js", "(msg) => Promise.reject(new Error(msg))")

        return ProxyObject.fromMap(mapOf(
            // Sync property: dimensions list
            "dimensions" to listOf("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),

            // Block operations (async)
            "getBlock" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    promiseReject.execute("getBlock() requires a position")
                } else {
                    // TODO: Implement actual block query
                    promiseReject.execute("World.getBlock() not yet implemented")
                }
            },
            "setBlock" to ProxyExecutable { args ->
                if (args.size < 2) {
                    promiseReject.execute("setBlock() requires position and blockId")
                } else {
                    // Optional properties parameter (args[2])
                    // TODO: Implement actual block placement
                    promiseResolve.execute(null)
                }
            },
            "fill" to ProxyExecutable { args ->
                if (args.size < 3) {
                    promiseReject.execute("fill() requires pos1, pos2, and blockId")
                } else {
                    // TODO: Implement actual fill operation
                    promiseResolve.execute(0)
                }
            },
            "replace" to ProxyExecutable { args ->
                if (args.size < 4) {
                    promiseReject.execute("replace() requires pos1, pos2, filter, and replacement")
                } else {
                    // TODO: Implement actual replace operation
                    promiseResolve.execute(0)
                }
            },

            // Entity operations (async)
            "getEntities" to ProxyExecutable { args ->
                if (args.size < 2) {
                    promiseReject.execute("getEntities() requires position and radius")
                } else {
                    // TODO: Implement actual entity query
                    promiseResolve.execute(emptyList<Any>())
                }
            },
            "spawnEntity" to ProxyExecutable { args ->
                if (args.size < 2) {
                    promiseReject.execute("spawnEntity() requires position and entityId")
                } else {
                    // Optional NBT parameter (args[2])
                    // TODO: Implement actual entity spawning
                    promiseReject.execute("World.spawnEntity() not yet implemented")
                }
            },

            // Player operations (async)
            "getPlayers" to ProxyExecutable { args ->
                // TODO: Implement actual player list
                promiseResolve.execute(emptyList<Any>())
            },
            "getPlayer" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    promiseReject.execute("getPlayer() requires name or UUID")
                } else {
                    // TODO: Implement actual player query
                    promiseResolve.execute(null)
                }
            },

            // Time/Weather operations (async)
            "getTime" to ProxyExecutable { args ->
                // Optional dimension parameter (args[0])
                // TODO: Implement actual time query
                promiseResolve.execute(0)
            },
            "setTime" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    promiseReject.execute("setTime() requires time value")
                } else {
                    // Optional dimension parameter (args[1])
                    // TODO: Implement actual time setting
                    promiseResolve.execute(null)
                }
            },
            "getWeather" to ProxyExecutable { args ->
                // Optional dimension parameter (args[0])
                // TODO: Implement actual weather query
                promiseResolve.execute("clear")
            },
            "setWeather" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    promiseReject.execute("setWeather() requires weather type")
                } else {
                    // Optional dimension parameter (args[1])
                    // TODO: Implement actual weather setting
                    promiseResolve.execute(null)
                }
            }
        ))
    }

    /**
     * Create Server API proxy for JavaScript.
     * Provides event system (on/off/once), server properties, and broadcast methods.
     */
    private fun createServerAPIProxy(): ProxyObject {
        // Event handler storage (in-memory for now)
        val eventHandlers = mutableMapOf<String, MutableList<Value>>()
        val oneTimeHandlers = mutableMapOf<String, MutableList<Value>>()

        return ProxyObject.fromMap(mapOf(
            // Event registration
            "on" to ProxyExecutable { args ->
                if (args.size < 2) {
                    throw IllegalArgumentException("on() requires event name and handler function")
                }
                val event = args[0].asString()
                val handler = args[1]

                if (!handler.canExecute()) {
                    throw IllegalArgumentException("Second argument to on() must be a function")
                }

                eventHandlers.getOrPut(event) { mutableListOf() }.add(handler)
                ConfigManager.debug("Registered handler for event: $event")
                null
            },

            "off" to ProxyExecutable { args ->
                if (args.size < 2) {
                    throw IllegalArgumentException("off() requires event name and handler function")
                }
                val event = args[0].asString()
                val handler = args[1]

                eventHandlers[event]?.remove(handler)
                oneTimeHandlers[event]?.remove(handler)
                ConfigManager.debug("Unregistered handler for event: $event")
                null
            },

            "once" to ProxyExecutable { args ->
                if (args.size < 2) {
                    throw IllegalArgumentException("once() requires event name and handler function")
                }
                val event = args[0].asString()
                val handler = args[1]

                if (!handler.canExecute()) {
                    throw IllegalArgumentException("Second argument to once() must be a function")
                }

                oneTimeHandlers.getOrPut(event) { mutableListOf() }.add(handler)
                ConfigManager.debug("Registered one-time handler for event: $event")
                null
            },

            // Server properties (placeholder values)
            "tps" to 20.0,
            "players" to emptyList<Any>(),
            "maxPlayers" to 20,
            "motd" to "A Minecraft Server",

            // Server methods
            "broadcast" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("broadcast() requires a message")
                }
                val message = args[0].asString()
                // TODO: Implement actual broadcast
                ConfigManager.debug("Broadcast: $message")
                null
            },

            "runCommand" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("runCommand() requires a command string")
                }
                val command = args[0].asString()
                // TODO: Implement actual command execution
                ConfigManager.debug("Run command: $command")
                null
            }
        ))
    }

    /**
     * Create Commands API proxy for JavaScript.
     * Provides fluent builder API for command registration with Brigadier integration.
     */
    private fun createCommandsAPIProxy(): ProxyObject {
        /**
         * Create a command builder that chains methods.
         */
        fun createCommandBuilder(name: String): ProxyObject {
            // Get existing command data or create new
            val commandData = commandRegistry.getCommand(name)?.toMutableMap() ?: mutableMapOf(
                "name" to name,
                "description" to null,
                "permission" to null,
                "arguments" to mutableListOf<Map<String, String>>(),
                "executor" to null
            )

            // Store in registry
            commandRegistry.storeCommand(name, commandData)

            return ProxyObject.fromMap(mapOf(
                "description" to ProxyExecutable { args ->
                    if (args.isEmpty()) {
                        throw IllegalArgumentException("description() requires a description string")
                    }
                    commandData["description"] = args[0].asString()
                    commandRegistry.storeCommand(name, commandData)  // Persist changes
                    // Return self for chaining
                    createCommandBuilder(name)
                },

                "permission" to ProxyExecutable { args ->
                    if (args.isEmpty()) {
                        throw IllegalArgumentException("permission() requires a permission string or function")
                    }
                    // Store the permission (string or function)
                    commandData["permission"] = args[0]
                    commandRegistry.storeCommand(name, commandData)  // Persist changes
                    // Return self for chaining
                    createCommandBuilder(name)
                },

                "argument" to ProxyExecutable { args ->
                    if (args.size < 2) {
                        throw IllegalArgumentException("argument() requires name and type")
                    }
                    val argName = args[0].asString()
                    val argType = args[1].asString()

                    // Validate argument type
                    val validTypes = listOf("string", "int", "float", "player", "item", "block", "entity")
                    if (argType !in validTypes) {
                        throw IllegalArgumentException("Invalid argument type: $argType. Valid types: ${validTypes.joinToString(", ")}")
                    }

                    @Suppress("UNCHECKED_CAST")
                    val arguments = commandData["arguments"] as MutableList<Map<String, String>>
                    arguments.add(mapOf("name" to argName, "type" to argType))
                    commandRegistry.storeCommand(name, commandData)  // Persist changes

                    // Return self for chaining
                    createCommandBuilder(name)
                },

                "executes" to ProxyExecutable { args ->
                    if (args.isEmpty()) {
                        throw IllegalArgumentException("executes() requires a handler function")
                    }
                    val handler = args[0]

                    if (!handler.canExecute()) {
                        throw IllegalArgumentException("executes() argument must be a function")
                    }

                    commandData["executor"] = handler
                    commandRegistry.storeCommand(name, commandData)  // Persist changes - THIS IS CRITICAL!
                    ConfigManager.debug("Registered command: $name with ${(commandData["arguments"] as List<*>).size} arguments")

                    // Return self for chaining (though typically executes() is the last call)
                    createCommandBuilder(name)
                }
            ))
        }

        return ProxyObject.fromMap(mapOf(
            "register" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("register() requires a command name")
                }
                val name = args[0].asString()
                createCommandBuilder(name)
            },

            "unregister" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("unregister() requires a command name")
                }
                val name = args[0].asString()
                // Remove from registry by storing null/empty
                val emptyData = mutableMapOf<String, Any?>()
                commandRegistry.storeCommand(name, emptyData)
                ConfigManager.debug("Unregistered command: $name")
                null
            }
        ))
    }

    /**
     * Inject built-in API modules that can be imported.
     * These modules are available via: import World from 'World'
     * Each API is stored directly on globalThis as __builtin_<Name> for virtual module access.
     */
    private fun injectBuiltinModules(bindings: Value) {
        // Create all API bindings
        val worldAPI = createWorldAPIProxy()
        val structureAPI = createStructureAPIProxy()
        val nbtAPI = createNBTAPIProxy()
        val storeAPI = createStoreAPIProxy()
        val serverAPI = createServerAPIProxy()
        val commandsAPI = createCommandsAPIProxy()

        // Put each API directly on globalThis for virtual module access
        bindings.putMember("__builtin_World", worldAPI)
        bindings.putMember("__builtin_Structure", structureAPI)
        bindings.putMember("__builtin_Store", storeAPI)
        bindings.putMember("__builtin_NBT", nbtAPI)
        bindings.putMember("__builtin_Server", serverAPI)
        bindings.putMember("__builtin_Commands", commandsAPI)

        ConfigManager.debug("Injected built-in modules (all APIs ready with placeholder implementations)")
    }

    /**
     * Inject Script.* context for utility scripts (rjs/scripts/).
     * Provides Script.caller, Script.args, and Script.argv for command-invoked scripts.
     */
    private fun injectScriptContext(bindings: Value, additionalBindings: Map<String, Any>) {
        // Extract Caller and Args from additionalBindings if provided
        val caller = additionalBindings["Caller"]
        val args = additionalBindings["Args"]

        if (caller != null || args != null) {
            val scriptContext = mutableMapOf<String, Any?>()
            if (caller != null) scriptContext["caller"] = caller
            if (args != null) scriptContext["args"] = args

            // Parse args into Script.argv if Args is provided
            if (args != null) {
                scriptContext["argv"] = createArgvProxy(args)
            }

            val scriptProxy = ProxyObject.fromMap(scriptContext)
            bindings.putMember("Script", scriptProxy)
            ConfigManager.debug("Injected Script.caller, Script.args, and Script.argv")
        }
    }

    /**
     * Create Script.argv proxy with argument parsing.
     * Parses command-line arguments into positional args and flags.
     *
     * @param args The raw arguments (can be List or Array)
     * @return ProxyObject with get(), hasFlag(), getAll(), and raw property
     */
    private fun createArgvProxy(args: Any): ProxyObject {
        // Convert args to list of strings
        @Suppress("UNCHECKED_CAST")
        val argsList = when (args) {
            is List<*> -> args.map { it.toString() }
            is Array<*> -> args.map { it.toString() }
            else -> emptyList()
        }

        // Parse arguments into positional args and flags
        val positionalArgs = mutableListOf<String>()
        val flags = mutableSetOf<String>()

        for (arg in argsList) {
            when {
                arg.startsWith("--") -> {
                    // Long flag: --verbose -> "verbose"
                    flags.add(arg.substring(2))
                }
                arg.startsWith("-") && arg.length > 1 -> {
                    // Short flag: -v -> "v"
                    flags.add(arg.substring(1))
                }
                else -> {
                    // Positional argument
                    positionalArgs.add(arg)
                }
            }
        }

        ConfigManager.debug("Parsed argv: ${positionalArgs.size} positional args, ${flags.size} flags")

        // Use pre-compiled undefined value to avoid classloader issues
        val undefined = jsUndefinedValue ?: throw IllegalStateException("JavaScript helpers not initialized")

        return ProxyObject.fromMap(mapOf(
            // get(index) - Get positional argument by index
            "get" to ProxyExecutable { params ->
                if (params.isEmpty()) {
                    throw IllegalArgumentException("get() requires an index argument")
                }
                val index = when {
                    params[0].isNumber -> params[0].asInt()
                    else -> throw IllegalArgumentException("get() index must be a number")
                }

                if (index >= 0 && index < positionalArgs.size) {
                    positionalArgs[index]
                } else {
                    undefined // Return JavaScript undefined for out of bounds
                }
            },

            // hasFlag(flag) - Check if a flag exists
            "hasFlag" to ProxyExecutable { params ->
                if (params.isEmpty()) {
                    throw IllegalArgumentException("hasFlag() requires a flag name")
                }
                val flag = params[0].asString()
                flags.contains(flag)
            },

            // getAll() - Get all positional arguments as array
            "getAll" to ProxyExecutable { _ ->
                positionalArgs.toList()
            },

            // raw - The original args array (read-only property)
            "raw" to argsList
        ))
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