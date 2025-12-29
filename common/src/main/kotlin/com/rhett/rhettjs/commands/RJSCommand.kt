package com.rhett.rhettjs.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.api.CallerAPI
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.engine.ScriptCategory
import com.rhett.rhettjs.engine.ScriptEngine
import com.rhett.rhettjs.engine.ScriptRegistry
import com.rhett.rhettjs.engine.ScriptResult
import com.rhett.rhettjs.engine.ScriptStatus
import com.rhett.rhettjs.engine.ScriptSystemInitializer
import com.rhett.rhettjs.engine.ServerScriptManager
import com.rhett.rhettjs.engine.GlobalsLoader
import com.rhett.rhettjs.engine.TypeGenerator
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture
import kotlin.io.path.exists

/**
 * Implementation of the /rjs command.
 * Provides script management and execution capabilities.
 */
object RJSCommand {

    /**
     * Register the /rjs command with the command dispatcher.
     */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("rjs")
                .requires { it.hasPermission(2) } // Requires op level 2
                .then(
                    Commands.literal("list")
                        .executes { listCommand(it, null) }
                        .then(
                            Commands.argument("category", StringArgumentType.word())
                                .suggests { _, builder ->
                                    ScriptCategory.values().forEach { builder.suggest(it.name.lowercase()) }
                                    builder.buildFuture()
                                }
                                .executes { listCommand(it, StringArgumentType.getString(it, "category")) }
                        )
                )
                .then(
                    Commands.literal("run")
                        .then(
                            Commands.argument("script", StringArgumentType.greedyString())
                                .suggests { _, builder ->
                                    ScriptRegistry.getScripts(ScriptCategory.UTILITY)
                                        .filter { it.status == ScriptStatus.LOADED }
                                        .forEach { builder.suggest(it.name) }
                                    builder.buildFuture()
                                }
                                .executes { runCommand(it, StringArgumentType.getString(it, "script")) }
                        )
                )
                .then(
                    Commands.literal("reload")
                        .executes { reloadCommand(it) }
                )
                .then(
                    Commands.literal("globals")
                        .executes { globalsCommand(it) }
                )
                .then(
                    Commands.literal("probe")
                        .executes { probeCommand(it) }
                )
        )

        // Register alias
        dispatcher.register(
            Commands.literal("rhettjs")
                .requires { it.hasPermission(2) }
                .redirect(dispatcher.getRoot().getChild("rjs"))
        )
    }

    /**
     * Handle /rjs list [category] command.
     */
    private fun listCommand(context: CommandContext<CommandSourceStack>, categoryArg: String?): Int {
        val source = context.source

        source.sendSuccess({ Component.literal("§6=== RhettJS Scripts ===") }, false)

        val categories = categoryArg?.let {
            try {
                listOf(ScriptCategory.valueOf(it.uppercase()))
            } catch (e: IllegalArgumentException) {
                source.sendFailure(Component.literal("§c[RhettJS] Unknown category: $it"))
                return 0
            }
        } ?: ScriptCategory.values().toList()

        categories.forEach { cat ->
            source.sendSuccess({ Component.literal("§7${cat.name.lowercase()}:") }, false)

            val scripts = ScriptRegistry.getScripts(cat)
            if (scripts.isEmpty()) {
                source.sendSuccess({ Component.literal("  §7(no scripts)") }, false)
            } else {
                scripts.forEach { script ->
                    val status = when (script.status) {
                        ScriptStatus.LOADED -> "§a✓"
                        ScriptStatus.ERROR -> "§c✗"
                        ScriptStatus.DISABLED -> "§7-"
                    }
                    source.sendSuccess({ Component.literal("  $status §f${script.name}") }, false)
                }
            }
        }

        return 1
    }

    /**
     * Handle /rjs run <script> [args ...] command.
     */
    private fun runCommand(context: CommandContext<CommandSourceStack>, scriptNameAndArgs: String): Int {
        val source = context.source

        // Parse script name and arguments
        // Format: "scriptname arg1 arg2 arg3"
        val parts = scriptNameAndArgs.split(Regex("\\s+"))
        val scriptName = parts[0]
        val args = parts.drop(1) // Remaining parts are arguments

        val script = ScriptRegistry.getScript(scriptName, ScriptCategory.UTILITY)

        if (script == null) {
            source.sendFailure(Component.literal("§c[RhettJS] Script not found: $scriptName"))
            return 0
        }

        if (script.status != ScriptStatus.LOADED) {
            source.sendFailure(Component.literal("§c[RhettJS] Script has errors: $scriptName"))
            source.sendFailure(Component.literal("§7Check server logs for details"))
            return 0
        }

        // Show script execution message
        val argsDisplay = if (args.isNotEmpty()) " (${args.size} arg${if (args.size == 1) "" else "s"})" else ""
        source.sendSuccess({ Component.literal("§7[RhettJS] Running $scriptName$argsDisplay...") }, true)

        // Create caller API for chat messages
        val callerAPI = CallerAPI(source)

        // Create Command API for command execution
        val player = source.player  // May be null if run from console
        val commandAPI = com.rhett.rhettjs.api.CommandAPI(source.server, player)
        val commandWrapper = com.rhett.rhettjs.api.CommandAPIWrapper(commandAPI, source.server)

        // Create Args array wrapper for JavaScript
        // ScriptEngine will convert this to a proper JavaScript array with JS strings
        val argsArray = args.toTypedArray()

        // Execute async to avoid blocking the game tick
        CompletableFuture.supplyAsync {
            try {
                ScriptEngine.executeScript(script, mapOf(
                    "Caller" to callerAPI,
                    "Command" to commandWrapper,
                    "Args" to argsArray
                ))
            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[RhettJS] Unexpected error running script", e)
                ScriptResult.Error(e.message ?: "Unknown error", e)
            }
        }.thenAccept { result ->
            // Send results back on main thread
            source.server.execute {
                when (result) {
                    is ScriptResult.Success -> {
                        source.sendSuccess({ Component.literal("§a[RhettJS] Script completed") }, true)
                    }

                    is ScriptResult.Error -> {
                        source.sendFailure(Component.literal("§c[RhettJS] Script failed"))
                        source.sendFailure(Component.literal("§c${result.message}"))
                    }
                }
            }
        }

        return 1
    }

    /**
     * Handle /rjs reload command.
     * Clears all event handlers, globals, and reloads all scripts.
     */
    private fun reloadCommand(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source

        source.sendSuccess({ Component.literal("§7[RhettJS] Reloading scripts...") }, true)

        try {
            // Clear and rescan scripts, reload globals
            ScriptSystemInitializer.reload(source.server.serverDirectory)

            // Execute server scripts (commands, events, etc.)
            val scriptsDir = ScriptSystemInitializer.getScriptsDirectory(source.server.serverDirectory)
            ServerScriptManager.createAndLoad(scriptsDir)

            source.sendSuccess({ Component.literal("§a[RhettJS] Reload complete") }, true)
            return 1

        } catch (e: Exception) {
            RhettJSCommon.LOGGER.error("[RhettJS] Failed to reload scripts", e)
            source.sendFailure(Component.literal("§c[RhettJS] Reload failed: ${e.message}"))
            return 0
        }
    }

    /**
     * Handle /rjs globals command.
     * Lists all loaded global libraries.
     */
    private fun globalsCommand(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source

        val globals = GlobalsLoader.getLoadedGlobals()

        if (globals.isEmpty()) {
            source.sendSuccess({ Component.literal("§7[RhettJS] No globals loaded") }, false)
            source.sendSuccess({ Component.literal("§7Create .js files in rjs/globals/ to define global libraries") }, false)
            return 1
        }

        source.sendSuccess({ Component.literal("§6=== RhettJS Globals ===") }, false)
        source.sendSuccess({ Component.literal("§7Loaded ${globals.size} global(s):") }, false)

        globals.forEach { globalName ->
            source.sendSuccess({ Component.literal("  §a✓ §f$globalName") }, false)
        }

        return 1
    }

    /**
     * Handle /rjs probe command.
     * Introspects available RhettJS APIs and generates TypeScript definitions.
     */
    private fun probeCommand(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source

        source.sendSuccess({ Component.literal("§6=== RhettJS API Probe ===") }, false)
        source.sendSuccess({ Component.literal("§7Generating types from code...") }, false)
        source.sendSuccess({ Component.literal("§7Probe will attempt to map your globals/ scripts,") }, false)
        source.sendSuccess({ Component.literal("§7but some patterns are imperfect and probe may be inaccurate for those.") }, false)
        source.sendSuccess({ Component.literal("") }, false)

        try {
            // Generate TypeScript definitions
            val scriptsDir = source.server.serverDirectory.resolve("rjs")
            val typesDir = scriptsDir.resolve("__types")

            val result = TypeGenerator.generate(typesDir, scriptsDir)

            when (result) {
                is TypeGenerator.GenerationResult.Success -> {
                    // Display summary
                    source.sendSuccess({ Component.literal("") }, false)
                    source.sendSuccess({ Component.literal("§a✓ Generated TypeScript definitions") }, false)
                    source.sendSuccess({ Component.literal("  §7Location: §frjs/__types/") }, false)
                    source.sendSuccess({ Component.literal("  §7Files:") }, false)
                    source.sendSuccess({ Component.literal("    §f- rhettjs.d.ts §7(${result.coreApiCount} APIs)") }, false)
                    if (result.globalsCount > 0) {
                        source.sendSuccess({ Component.literal("    §f- rhettjs-globals.d.ts §7(${result.globalsCount} globals)") }, false)
                    }
                    source.sendSuccess({ Component.literal("    §f- README.md §7(setup instructions)") }, false)
                    source.sendSuccess({ Component.literal("    §f- jsconfig.json.template §7(VSCode config)") }, false)

                    source.sendSuccess({ Component.literal("") }, false)
                    source.sendSuccess({ Component.literal("§7See §frjs/__types/README.md §7for IDE setup instructions") }, false)

                    return 1
                }

                is TypeGenerator.GenerationResult.Error -> {
                    source.sendFailure(Component.literal("§c[RhettJS] Failed to generate types: ${result.message}"))
                    RhettJSCommon.LOGGER.error("[RhettJS] Type generation failed", result.exception)
                    return 0
                }
            }

        } catch (e: Exception) {
            source.sendFailure(Component.literal("§c[RhettJS] Failed to generate types: ${e.message}"))
            RhettJSCommon.LOGGER.error("[RhettJS] Type generation failed", e)
            return 0
        }
    }

}
