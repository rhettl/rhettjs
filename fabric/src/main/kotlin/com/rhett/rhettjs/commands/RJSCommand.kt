package com.rhett.rhettjs.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.engine.ScriptCategory
import com.rhett.rhettjs.engine.ScriptEngine
import com.rhett.rhettjs.engine.ScriptRegistry
import com.rhett.rhettjs.engine.ScriptResult
import com.rhett.rhettjs.engine.ScriptStatus
import com.rhett.rhettjs.engine.GlobalsLoader
import com.rhett.rhettjs.events.StartupEventsAPI
import com.rhett.rhettjs.events.ServerEventsAPI
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

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
     * Handle /rjs run <script> command.
     */
    private fun runCommand(context: CommandContext<CommandSourceStack>, scriptName: String): Int {
        val source = context.source

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

        source.sendSuccess({ Component.literal("§7[RhettJS] Running $scriptName...") }, true)

        // Execute async to avoid blocking the game tick
        // Phase 1 MVP: No NBT API yet, just console/logger for testing
        val apis = emptyMap<String, Any>()

        CompletableFuture.supplyAsync {
            try {
                ScriptEngine.executeScript(script, apis)
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
            val scriptsDir = source.server.serverDirectory.resolve("rjs")

            // Phase 2: Clear all event handlers and globals
            RhettJSCommon.LOGGER.info("[RhettJS] Clearing event handlers...")
            StartupEventsAPI.clear()
            ServerEventsAPI.clear()
            GlobalsLoader.clear()

            // Rescan all scripts
            RhettJSCommon.LOGGER.info("[RhettJS] Rescanning scripts...")
            ScriptRegistry.scan(scriptsDir)

            // Reload globals
            RhettJSCommon.LOGGER.info("[RhettJS] Reloading globals...")
            GlobalsLoader.reload(scriptsDir)

            // Re-execute startup scripts
            val startupScripts = ScriptRegistry.getScripts(ScriptCategory.STARTUP)
            if (startupScripts.isNotEmpty()) {
                RhettJSCommon.LOGGER.info("[RhettJS] Executing ${startupScripts.size} startup scripts...")
                startupScripts.forEach { script ->
                    try {
                        ScriptEngine.executeScript(script)
                    } catch (e: Exception) {
                        RhettJSCommon.LOGGER.error("[RhettJS] Failed to execute startup script: ${script.name}", e)
                    }
                }
            }

            // Reload server scripts (re-register event handlers)
            val serverScripts = ScriptRegistry.getScripts(ScriptCategory.SERVER)
            if (serverScripts.isNotEmpty()) {
                RhettJSCommon.LOGGER.info("[RhettJS] Loading ${serverScripts.size} server scripts...")
                serverScripts.forEach { script ->
                    try {
                        ScriptEngine.executeScript(script)
                    } catch (e: Exception) {
                        RhettJSCommon.LOGGER.error("[RhettJS] Failed to load server script: ${script.name}", e)
                    }
                }
            }

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
}
