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
import java.util.concurrent.CompletableFuture

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
     *
     * Note: Closing and recreating the context within the same JVM session
     * should work because the native libraries are already loaded.
     * We only get "Native Library already loaded" errors if we try to reload
     * them in a different classloader (which doesn't happen here).
     */
    fun reset() {
        // Close and recreate context to pick up any config changes
        sharedContext?.close()
        sharedContext = null

        // Clear cached helpers (will be re-initialized on next script execution)
        jsNBTSetHelper = null
        jsNBTDeleteHelper = null
        jsNBTMergeShallowHelper = null
        jsNBTMergeDeepHelper = null
        jsUndefinedValue = null

        // Clear command registry and context reference
        commandRegistry.clear()
        commandRegistry.context = null

        // Reset managers (they will get new context references when context is recreated)
        com.rhett.rhettjs.events.ServerEventManager.reset()
        com.rhett.rhettjs.world.WorldManager.reset()
        com.rhett.rhettjs.structure.StructureNbtManager.reset()
        com.rhett.rhettjs.structure.LargeStructureNbtManager.reset()
        com.rhett.rhettjs.structure.WorldgenStructureManager.reset()

        ConfigManager.debug("GraalVM engine reset (context closed, will be recreated)")
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

            // Enable multi-threading for World API (server thread callbacks)
            .allowCreateThread(true)
            .option("js.shared-array-buffer", "true")

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
     * Re-initializes helpers if they were cleared by reset().
     *
     * Internal visibility for testing: APITypeValidationTest introspects bindings to validate types.
     */
    internal fun getOrCreateContext(): Context {
        val ctx = sharedContext ?: synchronized(this) {
            sharedContext ?: createContext().also { newCtx ->
                sharedContext = newCtx
                initializeJSHelpers(newCtx)

                // Inject core APIs that should always be available
                val bindings = newCtx.getBindings("js")

                // Console API
                val console = createConsoleAPI()
                bindings.putMember("console", console)

                // Runtime API
                val runtime = createRuntimeAPI(newCtx)
                bindings.putMember("Runtime", runtime)

                // wait() function
                val waitFn = createWaitFunction(newCtx)
                bindings.putMember("wait", waitFn)

                // Inject built-in API modules (World, Structure, Store, NBT, Server, Commands)
                injectBuiltinModules(bindings)

                // Set context reference in managers
                com.rhett.rhettjs.events.ServerEventManager.setContext(newCtx)
                com.rhett.rhettjs.world.WorldManager.setContext(newCtx)
                com.rhett.rhettjs.structure.StructureNbtManager.setContext(newCtx)
                com.rhett.rhettjs.structure.LargeStructureNbtManager.setContext(newCtx)
                com.rhett.rhettjs.structure.WorldgenStructureManager.setContext(newCtx)
                ConfigManager.debug("Created shared GraalVM context with pre-compiled helpers and built-in APIs")
            }
        }

        // Re-initialize helpers if they were cleared by reset()
        if (jsNBTSetHelper == null || jsNBTDeleteHelper == null) {
            initializeJSHelpers(ctx)
            ConfigManager.debug("Re-initialized helpers after reset")
        }

        return ctx
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

        // NBT.remove() helper
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

        // Console, Runtime, wait(), and built-in modules are already injected
        // during context initialization in getOrCreateContext()

        // Inject Script.* for utility scripts (or remove if not utility)
        if (category == ScriptCategory.UTILITY) {
            injectScriptContext(bindings, context, additionalBindings)
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

            // Create a JavaScript Promise that resolves when the CompletableFuture completes
            // We need to evaluate JavaScript code to create a proper Promise object
            val promiseCode = """
                new Promise((resolve, reject) => {
                    // The resolve/reject functions will be called from Kotlin
                    globalThis.__waitResolve = resolve;
                    globalThis.__waitReject = reject;
                })
            """
            val promise = context.eval("js", promiseCode)

            // Get the resolve and reject functions
            val resolve = context.getBindings("js").getMember("__waitResolve")
            val reject = context.getBindings("js").getMember("__waitReject")

            // Clean up the global references
            context.getBindings("js").removeMember("__waitResolve")
            context.getBindings("js").removeMember("__waitReject")

            // When the future completes, schedule the promise resolution on the next tick
            // This prevents ConcurrentModificationException if JS code calls wait() again
            future.whenComplete { _, throwable ->
                // Schedule the callback to run on the next server tick
                // This ensures we're not executing JS during timer iteration
                AsyncScheduler.scheduleCallback {
                    if (throwable != null) {
                        reject.execute(throwable.message)
                    } else {
                        resolve.execute()
                    }
                }
            }

            promise
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
            "remove" to ProxyExecutable { args ->
                if (args.size < 2) throw IllegalArgumentException("remove() requires nbt and path")
                try {
                    val path = if (args[1].isString) args[1].asString() else args[1].toString()
                    // Work with GraalVM Values directly to preserve JS object types
                    deleteNBTValueJS(args[0], path)
                } catch (e: Exception) {
                    ConfigManager.debug("NBT.remove error: ${e.message}")
                    throw IllegalArgumentException("NBT.remove requires (nbt, path): ${e.message}")
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
     * Create StructureNbt API proxy for JavaScript.
     * Handles single .nbt template files.
     * All methods return Promises except for properties.
     * Delegates to StructureNbtManager for actual implementation.
     */
    private fun createStructureNbtAPIProxy(): ProxyObject {
        val context = getOrCreateContext()

        return ProxyObject.fromMap(mapOf(
            // File operations (async)
            "exists" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "exists() requires a structure name")
                }
                val name = args[0].asString()
                convertFutureToPromise<Boolean>(context, com.rhett.rhettjs.structure.StructureNbtManager.exists(name))
            },
            "list" to ProxyExecutable { args ->
                val namespace = if (args.isNotEmpty()) args[0].asString() else null
                convertFutureToPromise<List<String>>(context, com.rhett.rhettjs.structure.StructureNbtManager.list(namespace))
            },
            "listGenerated" to ProxyExecutable { args ->
                val namespace = if (args.isNotEmpty()) args[0].asString() else null
                convertFutureToPromise<List<String>>(context, com.rhett.rhettjs.structure.StructureNbtManager.listGenerated(namespace))
            },
            "remove" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "remove() requires a structure name")
                }
                val name = args[0].asString()
                convertFutureToPromise<Boolean>(context, com.rhett.rhettjs.structure.StructureNbtManager.remove(name))
            },
            "load" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "load() requires a structure name")
                }
                val name = args[0].asString()
                convertFutureToPromise(context, com.rhett.rhettjs.structure.StructureNbtManager.load(name))
            },
            "save" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable createRejectedPromise(context, "save() requires structure name and data")
                }
                val name = args[0].asString()
                val data = convertGraalValueToKotlin(args[1])

                // Convert to StructureData
                if (data !is com.rhett.rhettjs.structure.models.StructureData) {
                    return@ProxyExecutable createRejectedPromise(context, "save() requires valid StructureData object")
                }

                convertFutureToPromise<Void>(context, com.rhett.rhettjs.structure.StructureNbtManager.save(name, data, skipBackup = false))
            },

            // Structure operations (async)
            "capture" to ProxyExecutable { args ->
                if (args.size < 3) {
                    return@ProxyExecutable createRejectedPromise(context, "capture() requires pos1, pos2, and name")
                }
                val pos1 = args[0]
                val pos2 = args[1]
                val name = args[2].asString()
                val options = if (args.size > 3) args[3] else null
                convertFutureToPromise<Void>(context, com.rhett.rhettjs.structure.StructureNbtManager.capture(pos1, pos2, name, options))
            },
            "place" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable createRejectedPromise(context, "place() requires position and name")
                }
                val position = args[0]
                val name = args[1].asString()
                val options = if (args.size > 2) args[2] else null
                convertFutureToPromise<Void>(context, com.rhett.rhettjs.structure.StructureNbtManager.place(position, name, options))
            },
            "getSize" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "getSize() requires a structure name")
                }
                val name = args[0].asString()
                convertFutureToPromise<Map<String, Int>>(context, com.rhett.rhettjs.structure.StructureNbtManager.getSize(name))
            },

            // Block analysis and replacement operations (async)
            "blocksList" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "blocksList() requires a structure name")
                }
                val name = args[0].asString()
                convertFutureToPromise<Map<String, Int>>(context, com.rhett.rhettjs.structure.StructureNbtManager.blocksList(name))
            },
            "blocksNamespaces" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "blocksNamespaces() requires a structure name")
                }
                val name = args[0].asString()
                convertFutureToPromise<List<String>>(context, com.rhett.rhettjs.structure.StructureNbtManager.blocksNamespaces(name))
            },
            "blocksReplace" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable createRejectedPromise(context, "blocksReplace() requires structure name and replacement map")
                }
                val name = args[0].asString()
                val replacementMap = convertGraalValueToKotlin(args[1]) as? Map<*, *>
                    ?: return@ProxyExecutable createRejectedPromise(context, "replacementMap must be an object")

                @Suppress("UNCHECKED_CAST")
                val typedMap = replacementMap as Map<String, String>
                convertFutureToPromise<Void>(context, com.rhett.rhettjs.structure.StructureNbtManager.blocksReplace(name, typedMap))
            },

            // Backup and restore operations (async)
            "listBackups" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "listBackups() requires a structure name")
                }
                val name = args[0].asString()
                convertFutureToPromise<List<String>>(context, com.rhett.rhettjs.structure.StructureNbtManager.listBackups(name))
            },
            "restoreBackup" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "restoreBackup() requires a structure name")
                }
                val name = args[0].asString()
                val timestamp = if (args.size > 1 && !args[1].isNull) args[1].asString() else null
                convertFutureToPromise<Void>(context, com.rhett.rhettjs.structure.StructureNbtManager.restoreBackup(name, timestamp))
            }
        ))
    }

    /**
     * Create LargeStructureNbt API proxy for JavaScript.
     * Handles multi-chunk .nbt structures stored in rjs-large/ directories.
     * All methods return Promises except for properties.
     * Delegates to LargeStructureNbtManager for actual implementation.
     */
    private fun createLargeStructureNbtAPIProxy(): ProxyObject {
        val context = getOrCreateContext()

        return ProxyObject.fromMap(mapOf(
            // Structure operations (async)
            "capture" to ProxyExecutable { args ->
                if (args.size < 3) {
                    return@ProxyExecutable createRejectedPromise(context, "capture() requires pos1, pos2, and name")
                }
                val pos1 = args[0]
                val pos2 = args[1]
                val name = args[2].asString()
                val options = if (args.size > 3) args[3] else null
                convertFutureToPromise<Void>(context, com.rhett.rhettjs.structure.LargeStructureNbtManager.capture(pos1, pos2, name, options))
            },
            "place" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable createRejectedPromise(context, "place() requires position and name")
                }
                val position = args[0]
                val name = args[1].asString()
                val options = if (args.size > 2) args[2] else null
                convertFutureToPromise<Void>(context, com.rhett.rhettjs.structure.LargeStructureNbtManager.place(position, name, options))
            },
            "getSize" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "getSize() requires a structure name")
                }
                val name = args[0].asString()
                convertFutureToPromise<Map<String, Int>>(context, com.rhett.rhettjs.structure.LargeStructureNbtManager.getSize(name))
            },
            "list" to ProxyExecutable { args ->
                val namespace = if (args.isNotEmpty()) args[0].asString() else null
                convertFutureToPromise<List<String>>(context, com.rhett.rhettjs.structure.LargeStructureNbtManager.list(namespace))
            },
            "remove" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "remove() requires a structure name")
                }
                val name = args[0].asString()
                convertFutureToPromise<Boolean>(context, com.rhett.rhettjs.structure.LargeStructureNbtManager.remove(name))
            },

            // Block operations (async)
            "blocksReplace" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable createRejectedPromise(context, "blocksReplace() requires structure name and replacement map")
                }
                val name = args[0].asString()
                val replacementMap = convertGraalValueToKotlin(args[1]) as? Map<*, *>
                    ?: return@ProxyExecutable createRejectedPromise(context, "replacementMap must be an object")

                @Suppress("UNCHECKED_CAST")
                val typedMap = replacementMap as Map<String, String>
                convertFutureToPromise<Void>(context, com.rhett.rhettjs.structure.LargeStructureNbtManager.blocksReplace(name, typedMap))
            },

            // Backup and restore operations (async)
            "listBackups" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "listBackups() requires a structure name")
                }
                val name = args[0].asString()
                convertFutureToPromise<List<String>>(context, com.rhett.rhettjs.structure.LargeStructureNbtManager.listBackups(name))
            },
            "restoreBackup" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "restoreBackup() requires a structure name")
                }
                val name = args[0].asString()
                val timestamp = if (args.size > 1 && !args[1].isNull) args[1].asString() else null
                convertFutureToPromise<Void>(context, com.rhett.rhettjs.structure.LargeStructureNbtManager.restoreBackup(name, timestamp))
            }
        ))
    }

    /**
     * Create WorldgenStructure API proxy for JavaScript.
     * Handles Minecraft's worldgen structures (villages, temples, bastions, etc.).
     * All methods return Promises.
     * Delegates to WorldgenStructureManager for actual implementation.
     */
    private fun createWorldgenStructureAPIProxy(): ProxyObject {
        val context = getOrCreateContext()

        return ProxyObject.fromMap(mapOf(
            "list" to ProxyExecutable { args ->
                val namespace = if (args.isNotEmpty() && !args[0].isNull) args[0].asString() else null
                convertFutureToPromise<List<String>>(context, com.rhett.rhettjs.structure.WorldgenStructureManager.list(namespace))
            },
            "exists" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "exists() requires a structure name")
                }
                val name = args[0].asString()
                convertFutureToPromise<Boolean>(context, com.rhett.rhettjs.structure.WorldgenStructureManager.exists(name))
            },
            "info" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "info() requires a structure name")
                }
                val name = args[0].asString()
                convertFutureToPromise<Map<String, Any?>>(context, com.rhett.rhettjs.structure.WorldgenStructureManager.info(name))
            },
            "place" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable createRejectedPromise(context, "place() requires a structure name and options object")
                }
                val name = args[0].asString()
                val options = args[1]

                // Extract options
                val x = if (options.hasMember("x")) options.getMember("x").asInt() else 0
                val z = if (options.hasMember("z")) options.getMember("z").asInt() else 0
                val dimension = if (options.hasMember("dimension") && !options.getMember("dimension").isNull) {
                    options.getMember("dimension").asString()
                } else null
                val seed = if (options.hasMember("seed") && !options.getMember("seed").isNull) {
                    options.getMember("seed").asLong()
                } else null
                val surface = if (options.hasMember("surface") && !options.getMember("surface").isNull) {
                    options.getMember("surface").asString()
                } else null
                val rotation = if (options.hasMember("rotation") && !options.getMember("rotation").isNull) {
                    options.getMember("rotation").asString()
                } else null

                convertFutureToPromise<Map<String, Any?>>(
                    context,
                    com.rhett.rhettjs.structure.WorldgenStructureManager.place(name, x, z, dimension, seed, surface, rotation)
                )
            },
            "placeJigsaw" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "placeJigsaw() requires an options object")
                }
                val options = args[0]

                // Extract required options
                if (!options.hasMember("pool")) {
                    return@ProxyExecutable createRejectedPromise(context, "placeJigsaw() requires 'pool' option")
                }
                if (!options.hasMember("target")) {
                    return@ProxyExecutable createRejectedPromise(context, "placeJigsaw() requires 'target' option")
                }
                if (!options.hasMember("maxDepth")) {
                    return@ProxyExecutable createRejectedPromise(context, "placeJigsaw() requires 'maxDepth' option")
                }
                if (!options.hasMember("x") || !options.hasMember("z")) {
                    return@ProxyExecutable createRejectedPromise(context, "placeJigsaw() requires 'x' and 'z' options")
                }

                val pool = options.getMember("pool").asString()
                val target = options.getMember("target").asString()
                val maxDepth = options.getMember("maxDepth").asInt()
                val x = options.getMember("x").asInt()
                val z = options.getMember("z").asInt()
                val dimension = if (options.hasMember("dimension") && !options.getMember("dimension").isNull) {
                    options.getMember("dimension").asString()
                } else null
                val seed = if (options.hasMember("seed") && !options.getMember("seed").isNull) {
                    options.getMember("seed").asLong()
                } else null
                val surface = if (options.hasMember("surface") && !options.getMember("surface").isNull) {
                    options.getMember("surface").asString()
                } else null

                convertFutureToPromise<Map<String, Any?>>(
                    context,
                    com.rhett.rhettjs.structure.WorldgenStructureManager.placeJigsaw(pool, target, maxDepth, x, z, dimension, seed, surface)
                )
            }
        ))
    }

    /**
     * Create World API proxy for JavaScript.
     * All methods return Promises except for the dimensions property.
     * Delegates to WorldManager for actual implementation.
     */
    private fun createWorldAPIProxy(): ProxyObject {
        val context = getOrCreateContext()

        // Create methods map
        val methods = mapOf(
            // Block operations (async) - delegate to WorldManager
            "getBlock" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "getBlock() requires a position")
                }
                convertFutureToPromise<Value>(context, com.rhett.rhettjs.world.WorldManager.getBlock(args[0]))
            },
            "getBlockEntity" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "getBlockEntity() requires a position")
                }
                convertFutureToPromise<Value?>(context, com.rhett.rhettjs.world.WorldManager.getBlockEntity(args[0]))
            },
            "setBlock" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable createRejectedPromise(context, "setBlock() requires position and blockId")
                }
                val position = args[0]
                val blockId = args[1].asString()
                val properties = if (args.size > 2) args[2] else null
                convertFutureToPromise<Void>(context, com.rhett.rhettjs.world.WorldManager.setBlock(position, blockId, properties))
            },
            "fill" to ProxyExecutable { args ->
                if (args.size < 3) {
                    return@ProxyExecutable createRejectedPromise(context, "fill() requires pos1, pos2, and blockId")
                }
                val pos1 = args[0]
                val pos2 = args[1]
                val blockId = args[2].asString()
                val options = if (args.size > 3) args[3] else null
                convertFutureToPromise<Int>(context, com.rhett.rhettjs.world.WorldManager.fill(pos1, pos2, blockId, options))
            },
            "replace" to ProxyExecutable { args ->
                if (args.size < 4) {
                    return@ProxyExecutable createRejectedPromise(context, "replace() requires pos1, pos2, filter, and replacement")
                }
                // TODO: Implement replace operation in WorldManager
                createRejectedPromise(context, "World.replace() not yet implemented")
            },

            // Entity operations (async) - delegate to WorldManager
            "getEntities" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable createRejectedPromise(context, "getEntities() requires position and radius")
                }
                val position = args[0]
                val radius = args[1].asDouble()
                convertFutureToPromise<List<Value>>(context, com.rhett.rhettjs.world.WorldManager.getEntities(position, radius))
            },
            "spawnEntity" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable createRejectedPromise(context, "spawnEntity() requires position and entityId")
                }
                val position = args[0]
                val entityId = args[1].asString()
                val nbt = if (args.size > 2) args[2] else null
                convertFutureToPromise<Value>(context, com.rhett.rhettjs.world.WorldManager.spawnEntity(position, entityId, nbt))
            },

            // Player operations (async) - delegate to WorldManager
            "getPlayers" to ProxyExecutable { args ->
                convertFutureToPromise<List<Value>>(context, com.rhett.rhettjs.world.WorldManager.getPlayers())
            },
            "getPlayer" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "getPlayer() requires name or UUID")
                }
                val nameOrUuid = args[0].asString()
                convertFutureToPromise<Value?>(context, com.rhett.rhettjs.world.WorldManager.getPlayer(nameOrUuid))
            },

            // Time/Weather operations (async) - delegate to WorldManager
            "getTime" to ProxyExecutable { args ->
                val dimension = if (args.isNotEmpty()) args[0].asString() else null
                convertFutureToPromise<Long>(context, com.rhett.rhettjs.world.WorldManager.getTime(dimension))
            },
            "setTime" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "setTime() requires time value")
                }
                val time = args[0].asLong()
                val dimension = if (args.size > 1) args[1].asString() else null
                convertFutureToPromise<Void>(context, com.rhett.rhettjs.world.WorldManager.setTime(time, dimension))
            },
            "getWeather" to ProxyExecutable { args ->
                val dimension = if (args.isNotEmpty()) args[0].asString() else null
                convertFutureToPromise<String>(context, com.rhett.rhettjs.world.WorldManager.getWeather(dimension))
            },
            "setWeather" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable createRejectedPromise(context, "setWeather() requires weather type")
                }
                val weather = args[0].asString()
                val dimension = if (args.size > 1) args[1].asString() else null
                convertFutureToPromise<Void>(context, com.rhett.rhettjs.world.WorldManager.setWeather(weather, dimension))
            },

            // Dimension bounds queries
            "getDimensionBounds" to ProxyExecutable { args ->
                val dimension = if (args.isNotEmpty()) args[0].asString() else null
                convertFutureToPromise<Value>(context, com.rhett.rhettjs.world.WorldManager.getDimensionBounds(dimension))
            },
            "getFilledBounds" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable createRejectedPromise(context, "getFilledBounds() requires pos1 and pos2")
                }
                val pos1 = args[0]
                val pos2 = args[1]
                val dimension = if (args.size > 2) args[2].asString() else null
                convertFutureToPromise<Value?>(context, com.rhett.rhettjs.world.WorldManager.getFilledBounds(pos1, pos2, dimension))
            },
            "removeEntities" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable createRejectedPromise(context, "removeEntities() requires pos1 and pos2")
                }
                val pos1 = args[0]
                val pos2 = args[1]
                val options = if (args.size > 2) args[2] else null
                convertFutureToPromise<Int>(context, com.rhett.rhettjs.world.WorldManager.removeEntities(pos1, pos2, options))
            }
        )

        // Return a custom ProxyObject that dynamically fetches dimensions
        return object : ProxyObject {
            override fun getMember(key: String?): Any? {
                return when (key) {
                    "dimensions" -> com.rhett.rhettjs.world.WorldManager.getDimensions()
                    else -> methods[key]
                }
            }

            override fun getMemberKeys(): Any = (methods.keys + "dimensions").toTypedArray()

            override fun hasMember(key: String?): Boolean {
                return key == "dimensions" || methods.containsKey(key)
            }

            override fun putMember(key: String?, value: Value?) {
                // Read-only proxy
            }
        }
    }

    /**
     * Convert CompletableFuture to JavaScript Promise.
     * Helper to bridge Java async operations to JS Promises.
     *
     * IMPORTANT: The future completion may happen on a different thread (e.g., server thread),
     * but GraalVM contexts are single-threaded. We use AsyncScheduler to schedule the
     * promise resolution back onto the next tick, which runs on a thread that can access
     * the context safely.
     */
    private fun <T> convertFutureToPromise(context: Context, future: CompletableFuture<T>): Value {
        // Generate unique ID for this promise to avoid collisions
        val promiseId = "_rjs_promise_${System.nanoTime()}_${(Math.random() * 1000000).toInt()}"

        // Create a Promise and store resolve/reject with unique names
        val promiseCode = """
            new Promise((resolve, reject) => {
                globalThis.${promiseId}_resolve = resolve;
                globalThis.${promiseId}_reject = reject;
            })
        """
        val promise = context.eval("js", promiseCode)

        // Get resolve/reject functions
        val resolve = context.getBindings("js").getMember("${promiseId}_resolve")
        val reject = context.getBindings("js").getMember("${promiseId}_reject")

        // When future completes, schedule the promise resolution on the next tick
        // This ensures we're not trying to access the GraalVM context from the wrong thread
        future.whenComplete { result, throwable ->
            // Schedule promise resolution on next tick to avoid multi-threaded access
            com.rhett.rhettjs.async.AsyncScheduler.scheduleCallback {
                // Enter context for multi-threaded access
                context.enter()
                try {
                    if (throwable != null) {
                        val errorMsg = throwable.cause?.message ?: throwable.message ?: "Unknown error"
                        ConfigManager.debug("[Promise] Rejecting with error: $errorMsg")
                        reject.execute(errorMsg)
                    } else {
                        ConfigManager.debug("[Promise] Resolving with result: $result")
                        // Convert result to GraalVM Value to ensure proper type conversion
                        // (e.g., Kotlin List -> JS Array, Kotlin Map -> JS Object)
                        val jsResult = context.asValue(result)
                        resolve.execute(jsResult)
                    }
                } catch (e: Exception) {
                    ConfigManager.debug("[Promise] Error during promise resolution: ${e.message}")
                    try {
                        reject.execute("Promise resolution error: ${e.message}")
                    } catch (e2: Exception) {
                        RhettJSCommon.LOGGER.error("[Promise] Failed to reject promise", e2)
                    }
                } finally {
                    // Clean up globals after promise settles
                    try {
                        context.getBindings("js").removeMember("${promiseId}_resolve")
                        context.getBindings("js").removeMember("${promiseId}_reject")
                    } catch (e: Exception) {
                        // Ignore cleanup errors
                    }
                    // Leave context for multi-threaded access
                    context.leave()
                }
            }
        }

        return promise
    }

    /**
     * Create a rejected Promise with error message.
     */
    private fun createRejectedPromise(context: Context, message: String): Value {
        return context.eval("js", "Promise.reject(new Error('$message'))")
    }

    /**
     * Create Server API proxy for JavaScript.
     * Provides event system (on/off/once), server properties, and broadcast methods.
     * Delegates to ServerEventManager for actual implementation.
     */
    private fun createServerAPIProxy(): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            // Event types enumeration
            "eventTypes" to ProxyObject.fromMap(com.rhett.rhettjs.events.ServerEventManager.getEventTypes()),

            // Event registration - delegate to ServerEventManager
            "on" to ProxyExecutable { args ->
                if (args.size < 2) {
                    throw IllegalArgumentException("on() requires event name and handler function")
                }
                val event = args[0].asString()
                val handler = args[1]

                com.rhett.rhettjs.events.ServerEventManager.on(event, handler)
                null
            },

            "off" to ProxyExecutable { args ->
                if (args.size < 2) {
                    throw IllegalArgumentException("off() requires event name and handler function")
                }
                val event = args[0].asString()
                val handler = args[1]

                com.rhett.rhettjs.events.ServerEventManager.off(event, handler)
                null
            },

            "once" to ProxyExecutable { args ->
                if (args.size < 2) {
                    throw IllegalArgumentException("once() requires event name and handler function")
                }
                val event = args[0].asString()
                val handler = args[1]

                com.rhett.rhettjs.events.ServerEventManager.once(event, handler)
                null
            },

            // Server properties - delegate to ServerEventManager for real values
            "tps" to com.rhett.rhettjs.events.ServerEventManager.getServerTPS(),
            "players" to com.rhett.rhettjs.events.ServerEventManager.getOnlinePlayers(),
            "maxPlayers" to com.rhett.rhettjs.events.ServerEventManager.getMaxPlayers(),
            "motd" to com.rhett.rhettjs.events.ServerEventManager.getMOTD(),

            // Server methods - delegate to ServerEventManager
            "broadcast" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("broadcast() requires a message")
                }
                val message = args[0].asString()
                com.rhett.rhettjs.events.ServerEventManager.broadcast(message)
                null
            },

            "runCommand" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("runCommand() requires a command string")
                }
                val command = args[0].asString()
                com.rhett.rhettjs.events.ServerEventManager.runCommand(command)
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
         * Create a subcommand builder for a specific subcommand.
         */
        fun createSubcommandBuilder(commandName: String, subcommandName: String): ProxyObject {
            // Get command data
            val commandData = commandRegistry.getCommand(commandName)?.toMutableMap() ?: mutableMapOf(
                "name" to commandName,
                "description" to null,
                "permission" to null,
                "arguments" to mutableListOf<Map<String, String>>(),
                "executor" to null,
                "subcommands" to mutableMapOf<String, MutableMap<String, Any?>>()
            )

            // Get or create subcommands map
            @Suppress("UNCHECKED_CAST")
            val subcommands = commandData.getOrPut("subcommands") {
                mutableMapOf<String, MutableMap<String, Any?>>()
            } as MutableMap<String, MutableMap<String, Any?>>

            // Get or create this subcommand's data
            val subcommandData = subcommands.getOrPut(subcommandName) {
                mutableMapOf(
                    "name" to subcommandName,
                    "arguments" to mutableListOf<Map<String, String>>(),
                    "executor" to null
                )
            }

            // Store changes
            commandRegistry.storeCommand(commandName, commandData)

            return ProxyObject.fromMap(mapOf(
                "argument" to ProxyExecutable { args ->
                    if (args.size < 2) {
                        throw IllegalArgumentException("argument() requires name and type")
                    }
                    val argName = args[0].asString()
                    val argType = args[1].asString()

                    // Check if optional (3rd parameter provided)
                    val isOptional = args.size >= 3
                    val hasDefault = isOptional && !args[2].isNull

                    // Unwrap the default value based on the argument type
                    val defaultValue = if (hasDefault) {
                        when (argType) {
                            "string" -> args[2].asString()
                            "int" -> args[2].asInt()
                            "float" -> args[2].asDouble()
                            else -> args[2] // For complex types, keep as Value
                        }
                    } else {
                        null
                    }

                    // Validate argument type
                    val validTypes = listOf("string", "int", "float", "player", "item", "block", "entity", "xyz-position", "xz-position")
                    if (argType !in validTypes) {
                        throw IllegalArgumentException("Invalid argument type: $argType. Valid types: ${validTypes.joinToString(", ")}")
                    }

                    @Suppress("UNCHECKED_CAST")
                    val arguments = subcommandData["arguments"] as MutableList<MutableMap<String, Any?>>

                    // Validate: no required args after optional args
                    // This is stricter than JavaScript but necessary for Brigadier command structure
                    if (!isOptional && arguments.any { it["optional"] == true }) {
                        throw IllegalArgumentException(
                            "Cannot add required argument '$argName' after optional arguments. " +
                            "In Brigadier commands, all required arguments must come before optional arguments. " +
                            "Reorder your .argument() calls to put required arguments first."
                        )
                    }

                    arguments.add(mutableMapOf(
                        "name" to argName,
                        "type" to argType,
                        "optional" to isOptional,
                        "hasDefault" to hasDefault,
                        "default" to defaultValue
                    ))
                    commandRegistry.storeCommand(commandName, commandData)  // Persist changes

                    // Return self for chaining
                    createSubcommandBuilder(commandName, subcommandName)
                },

                "suggestions" to ProxyExecutable { args ->
                    if (args.size < 2) {
                        throw IllegalArgumentException("suggestions() requires argName and provider function")
                    }
                    val argName = args[0].asString()
                    val provider = args[1]

                    if (!provider.canExecute()) {
                        throw IllegalArgumentException("suggestions() provider must be a function")
                    }

                    // Get or create suggestions map
                    @Suppress("UNCHECKED_CAST")
                    val suggestions = subcommandData.getOrPut("suggestions") {
                        mutableMapOf<String, Value>()
                    } as MutableMap<String, Value>

                    // Store provider function keyed by argument name (order-independent)
                    suggestions[argName] = provider
                    commandRegistry.storeCommand(commandName, commandData)  // Persist changes

                    ConfigManager.debug("[Commands] Added suggestions for subcommand argument: $commandName $subcommandName.$argName")

                    // Return self for chaining
                    createSubcommandBuilder(commandName, subcommandName)
                },

                "executes" to ProxyExecutable { args ->
                    if (args.isEmpty()) {
                        throw IllegalArgumentException("executes() requires a handler function")
                    }
                    val handler = args[0]

                    if (!handler.canExecute()) {
                        throw IllegalArgumentException("executes() argument must be a function")
                    }

                    subcommandData["executor"] = handler
                    commandRegistry.storeCommand(commandName, commandData)  // Persist changes

                    // Update registry's context reference to current context
                    val currentContext = getOrCreateContext()
                    if (commandRegistry.context == null) {
                        ConfigManager.debug("[Commands] Updating registry context reference after reset")
                        val dispatcher = commandRegistry.dispatcher
                        val buildContext = commandRegistry.commandBuildContext
                        if (dispatcher != null && buildContext != null) {
                            commandRegistry.storeDispatcher(dispatcher, currentContext, buildContext)
                        }
                    }

                    ConfigManager.debug("Registered subcommand: $commandName $subcommandName with ${(subcommandData["arguments"] as List<*>).size} arguments")

                    // Return self for chaining
                    createSubcommandBuilder(commandName, subcommandName)
                }
            ))
        }

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
                "executor" to null,
                "subcommands" to mutableMapOf<String, MutableMap<String, Any?>>()
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

                    // Check if optional (3rd parameter provided)
                    val isOptional = args.size >= 3
                    val hasDefault = isOptional && !args[2].isNull

                    // Unwrap the default value based on the argument type
                    val defaultValue = if (hasDefault) {
                        when (argType) {
                            "string" -> args[2].asString()
                            "int" -> args[2].asInt()
                            "float" -> args[2].asDouble()
                            else -> args[2] // For complex types, keep as Value
                        }
                    } else {
                        null
                    }

                    // Validate argument type
                    val validTypes = listOf("string", "int", "float", "player", "item", "block", "entity", "xyz-position", "xz-position")
                    if (argType !in validTypes) {
                        throw IllegalArgumentException("Invalid argument type: $argType. Valid types: ${validTypes.joinToString(", ")}")
                    }

                    @Suppress("UNCHECKED_CAST")
                    val arguments = commandData["arguments"] as MutableList<MutableMap<String, Any?>>

                    // Validate: no required args after optional args
                    // This is stricter than JavaScript but necessary for Brigadier command structure
                    if (!isOptional && arguments.any { it["optional"] == true }) {
                        throw IllegalArgumentException(
                            "Cannot add required argument '$argName' after optional arguments. " +
                            "In Brigadier commands, all required arguments must come before optional arguments. " +
                            "Reorder your .argument() calls to put required arguments first."
                        )
                    }

                    arguments.add(mutableMapOf(
                        "name" to argName,
                        "type" to argType,
                        "optional" to isOptional,
                        "hasDefault" to hasDefault,
                        "default" to defaultValue
                    ))
                    commandRegistry.storeCommand(name, commandData)  // Persist changes

                    // Return self for chaining
                    createCommandBuilder(name)
                },

                "suggestions" to ProxyExecutable { args ->
                    if (args.size < 2) {
                        throw IllegalArgumentException("suggestions() requires argName and provider function")
                    }
                    val argName = args[0].asString()
                    val provider = args[1]

                    if (!provider.canExecute()) {
                        throw IllegalArgumentException("suggestions() provider must be a function")
                    }

                    // Get or create suggestions map
                    @Suppress("UNCHECKED_CAST")
                    val suggestions = commandData.getOrPut("suggestions") {
                        mutableMapOf<String, Value>()
                    } as MutableMap<String, Value>

                    // Store provider function keyed by argument name (order-independent)
                    suggestions[argName] = provider
                    commandRegistry.storeCommand(name, commandData)  // Persist changes

                    ConfigManager.debug("[Commands] Added suggestions for command argument: $name.$argName")

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

                    // Update registry's context reference to current context
                    // This is needed after GraalEngine.reset() which invalidates the old context
                    val currentContext = getOrCreateContext()
                    if (commandRegistry.context == null) {
                        ConfigManager.debug("[Commands] Updating registry context reference after reset")
                        // Re-store dispatcher with new context
                        val dispatcher = commandRegistry.dispatcher
                        val buildContext = commandRegistry.commandBuildContext
                        if (dispatcher != null && buildContext != null) {
                            commandRegistry.storeDispatcher(dispatcher, currentContext, buildContext)
                        }
                    }

                    ConfigManager.debug("Registered command: $name with ${(commandData["arguments"] as List<*>).size} arguments")

                    // Return self for chaining (though typically executes() is the last call)
                    createCommandBuilder(name)
                },

                "subcommand" to ProxyExecutable { args ->
                    if (args.isEmpty()) {
                        throw IllegalArgumentException("subcommand() requires a subcommand name")
                    }
                    val subcommandName = args[0].asString()
                    // Return subcommand builder
                    createSubcommandBuilder(name, subcommandName)
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
        val structureNbtAPI = createStructureNbtAPIProxy()
        val largeStructureNbtAPI = createLargeStructureNbtAPIProxy()
        val worldgenStructureAPI = createWorldgenStructureAPIProxy()
        val nbtAPI = createNBTAPIProxy()
        val storeAPI = createStoreAPIProxy()
        val serverAPI = createServerAPIProxy()
        val commandsAPI = createCommandsAPIProxy()

        // Put each API directly on globalThis for virtual module access
        bindings.putMember("__builtin_World", worldAPI)
        bindings.putMember("__builtin_StructureNbt", structureNbtAPI)
        bindings.putMember("__builtin_LargeStructureNbt", largeStructureNbtAPI)
        bindings.putMember("__builtin_WorldgenStructure", worldgenStructureAPI)
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
    private fun injectScriptContext(bindings: Value, context: Context, additionalBindings: Map<String, Any>) {
        // Extract Caller and Args from additionalBindings if provided
        val caller = additionalBindings["Caller"]
        val args = additionalBindings["Args"]

        if (caller != null || args != null) {
            val scriptContext = mutableMapOf<String, Any?>()

            // Convert CallerAPI to JavaScript object using CallerAdapter
            if (caller != null) {
                val callerJS = if (caller is com.rhett.rhettjs.api.CallerAPI) {
                    // Use CallerAdapter to convert to proper JS object with properties
                    com.rhett.rhettjs.adapter.CallerAdapter.toJS(caller.source, context)
                } else {
                    // Fallback if already converted
                    context.asValue(caller)
                }
                scriptContext["caller"] = callerJS
            }

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
     * Parses command-line arguments into positional args and flags with values.
     *
     * Supports:
     * - Positional args: arg1 arg2
     * - Boolean flags: -abc (a=true, b=true, c=true), --verbose (verbose=true)
     * - Flags with values: -a=1 -b=2, --name=value, --name="quoted value"
     *
     * @param args The raw arguments (can be List or Array)
     * @return ProxyObject with get(index), get(name), hasFlag(), getAll(), and raw property
     */
    private fun createArgvProxy(args: Any): ProxyObject {
        // Convert args to list of strings
        @Suppress("UNCHECKED_CAST")
        val argsList = when (args) {
            is List<*> -> args.map { it.toString() }
            is Array<*> -> args.map { it.toString() }
            else -> emptyList()
        }

        // Parse arguments into positional args and named flags
        val positionalArgs = mutableListOf<String>()
        val namedFlags = mutableMapOf<String, Any>() // flag name -> value (true or string/number)

        for (arg in argsList) {
            when {
                arg.startsWith("--") -> {
                    // Long flag: --verbose or --name=value
                    val flagPart = arg.substring(2)
                    if ('=' in flagPart) {
                        val (name, value) = flagPart.split('=', limit = 2)
                        namedFlags[name] = parseValue(value)
                    } else {
                        namedFlags[flagPart] = true
                    }
                }
                arg.startsWith("-") && arg.length > 1 -> {
                    // Short flag: -v or -abc (multi-char boolean) or -a=value
                    val flagPart = arg.substring(1)
                    if ('=' in flagPart) {
                        // Single flag with value: -a=123
                        val (name, value) = flagPart.split('=', limit = 2)
                        namedFlags[name] = parseValue(value)
                    } else {
                        // Multi-char boolean flags: -abc -> a=true, b=true, c=true
                        flagPart.forEach { char ->
                            namedFlags[char.toString()] = true
                        }
                    }
                }
                else -> {
                    // Positional argument
                    positionalArgs.add(arg)
                }
            }
        }

        ConfigManager.debug("Parsed argv: ${positionalArgs.size} positional args, ${namedFlags.size} named flags")

        // Use pre-compiled undefined value to avoid classloader issues
        val undefined = jsUndefinedValue ?: throw IllegalStateException("JavaScript helpers not initialized")

        return ProxyObject.fromMap(mapOf(
            // get(indexOrName) - Get positional argument by index OR named flag by name
            "get" to ProxyExecutable { params ->
                if (params.isEmpty()) {
                    throw IllegalArgumentException("get() requires an index or name argument")
                }

                when {
                    params[0].isNumber -> {
                        // Get positional arg by index
                        val index = params[0].asInt()
                        if (index >= 0 && index < positionalArgs.size) {
                            positionalArgs[index]
                        } else {
                            undefined
                        }
                    }
                    params[0].isString -> {
                        // Get named flag by name
                        val name = params[0].asString()
                        namedFlags[name] ?: undefined
                    }
                    else -> undefined
                }
            },

            // hasFlag(flag) - Check if a flag exists (backward compatibility)
            "hasFlag" to ProxyExecutable { params ->
                if (params.isEmpty()) {
                    throw IllegalArgumentException("hasFlag() requires a flag name")
                }
                val flag = params[0].asString()
                namedFlags.containsKey(flag)
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
     * Parse a flag value string into appropriate type.
     * - Quoted strings: "hello" or 'hello' -> string
     * - Numbers: 123 -> int, 3.14 -> double
     * - Otherwise: string
     */
    private fun parseValue(value: String): Any {
        // Remove quotes if present
        val trimmed = value.trim()
        val unquoted = when {
            (trimmed.startsWith('"') && trimmed.endsWith('"')) ||
            (trimmed.startsWith('\'') && trimmed.endsWith('\'')) ->
                trimmed.substring(1, trimmed.length - 1)
            else -> trimmed
        }

        // Try to parse as number
        return unquoted.toIntOrNull()
            ?: unquoted.toDoubleOrNull()
            ?: unquoted
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