package com.rhett.rhettjs.engine

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.commands.CustomCommandRegistry
import com.rhett.rhettjs.engine.api.*
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.io.FileSystem
import org.graalvm.polyglot.io.IOAccess

/**
 * Exception thrown when Runtime.exit() is called.
 * Used to gracefully terminate script execution.
 */
class ScriptExitException : RuntimeException("Script terminated via Runtime.exit()")

/**
 * GraalVM JavaScript engine management.
 * Handles context creation, bindings setup, and script execution with native async/await support.
 *
 * ## IMPORTANT NOTES FOR FUTURE DEVELOPERS:
 *
 * ### Where to Add New Code:
 * - **New JavaScript API proxies** → Add in `engine/api/*APIProxy.kt` files, NOT here
 *   - Each API should be in its own file (e.g., WorldAPIProxy.kt, StoreAPIProxy.kt)
 *   - Follow the pattern: object with a `create()` method that returns ProxyObject
 *   - Add the API to `injectBuiltinModules()` below
 *
 * - **JavaScript helper functions** → Add to `JSHelpers.kt`
 *   - Pre-compiled JS functions to avoid classloader issues
 *   - NBT operations, utility functions, etc.
 *
 * - **Promise/Future conversion utilities** → Add to `PromiseHelpers.kt`
 *   - Async bridge between Kotlin CompletableFuture and JS Promises
 *
 * - **Script context injection (Script.*)** → Add to `ScriptContext.kt`
 *   - Script.caller, Script.args, Script.argv logic
 *
 * ### This File Should Only Contain:
 * - GraalVM context creation and lifecycle management
 * - Script execution logic and error handling
 * - Module injection orchestration (calling the API proxies)
 * - Reset/initialization coordination
 *
 * ### Refactoring History:
 * - 2026-01-12: Refactored from 2169 lines → ~400 lines
 * - Extracted 10 API proxies to engine/api/ directory
 * - Extracted helpers to JSHelpers.kt, PromiseHelpers.kt, ScriptContext.kt
 */
object GraalEngine {

    // Scripts base directory for module resolution
    @Volatile
    private var scriptsBaseDir: java.nio.file.Path? = null

    // Shared GraalVM context (created once, reused for all scripts)
    @Volatile
    private var sharedContext: Context? = null

    // Custom command registry for Commands API
    private val commandRegistry = CustomCommandRegistry()

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
        JSHelpers.clearHelpers()

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
                JSHelpers.initializeHelpers(newCtx)

                // Inject core APIs that should always be available
                val bindings = newCtx.getBindings("js")

                // Console API
                val console = ConsoleAPI.create()
                bindings.putMember("console", console)

                // Runtime API
                val runtime = RuntimeAPI.create(newCtx)
                bindings.putMember("Runtime", runtime)

                // wait() function
                val waitFn = WaitFunctionAPI.create(newCtx)
                bindings.putMember("wait", waitFn)

                // Inject built-in API modules (World, Structure, Store, NBT, Server, Commands)
                injectBuiltinModules(bindings, newCtx)

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
        if (!JSHelpers.areHelpersInitialized()) {
            JSHelpers.initializeHelpers(ctx)
            ConfigManager.debug("Re-initialized helpers after reset")
        }

        return ctx
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
            val source = if (scriptsBaseDir != null) {
                val virtualUri = scriptsBaseDir!!.resolve("modules/${script.name}.js").toUri()
                Source.newBuilder("js", script.path.toFile())
                    .name(script.name)
                    .uri(virtualUri)  // Virtual path
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
            ScriptContext.injectScriptContext(bindings, context, additionalBindings)
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
     * Inject built-in API modules that can be imported.
     * These modules are available via: import World from 'World'
     * Each API is stored directly on globalThis as __builtin_<Name> for virtual module access.
     *
     * NOTE TO FUTURE DEVELOPERS: When adding a new JavaScript API:
     * 1. Create a new file in engine/api/ (e.g., MyNewAPIProxy.kt)
     * 2. Implement a `create()` method that returns ProxyObject
     * 3. Add it to this function below
     * 4. Update the TypeScript definitions in rhettjs-types/
     */
    private fun injectBuiltinModules(bindings: Value, context: Context) {
        // Create all API bindings using extracted proxy classes
        val worldAPI = WorldAPIProxy.create(context)
        val structureNbtAPI = StructureAPIsProxy.createStructureNbtAPI(context)
        val largeStructureNbtAPI = StructureAPIsProxy.createLargeStructureNbtAPI(context)
        val worldgenStructureAPI = StructureAPIsProxy.createWorldgenStructureAPI(context)
        val nbtAPI = NBTAPI.create()
        val storeAPI = StoreAPIProxy.create()
        val serverAPI = ServerAPIProxy.create()
        val commandsAPI = CommandsAPIProxy.create(commandRegistry, ::getOrCreateContext)

        // Put each API directly on globalThis for virtual module access
        bindings.putMember("__builtin_World", worldAPI)
        bindings.putMember("__builtin_StructureNbt", structureNbtAPI)
        bindings.putMember("__builtin_LargeStructureNbt", largeStructureNbtAPI)
        bindings.putMember("__builtin_WorldgenStructure", worldgenStructureAPI)
        bindings.putMember("__builtin_Store", storeAPI)
        bindings.putMember("__builtin_NBT", nbtAPI)
        bindings.putMember("__builtin_Server", serverAPI)
        bindings.putMember("__builtin_Commands", commandsAPI)

        ConfigManager.debug("Injected built-in modules (all APIs ready)")
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
