package com.rhett.rhettjs.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.api.CallerAPI
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.engine.ScriptCategory
import com.rhett.rhettjs.engine.GraalEngine
import com.rhett.rhettjs.engine.ScriptRegistry
import com.rhett.rhettjs.engine.ScriptResult
import com.rhett.rhettjs.engine.ScriptStatus
import com.rhett.rhettjs.engine.ScriptSystemInitializer
// TODO: Re-implement ServerScriptManager for GraalVM
// import com.rhett.rhettjs.engine.ServerScriptManager
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

        // Create Args array - GraalVM will auto-convert to JS array
        val argsArray = args.toTypedArray()

        // Execute async to avoid blocking the game tick
        CompletableFuture.supplyAsync {
            try {
                GraalEngine.executeScript(script, mapOf(
                    "Caller" to callerAPI,
                    "Command" to commandAPI,  // GraalVM auto-wraps
                    "Args" to argsArray
                ))
            } catch (e: Exception) {
                // Log full error for debugging
                RhettJSCommon.LOGGER.error("[RhettJS] Unexpected error running script", e)

                // Extract user-friendly error message
                val userMessage = extractUserFriendlyError(e)
                ScriptResult.Error(userMessage, e)
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

            // TODO: Re-execute server scripts for GraalVM
            // val scriptsDir = ScriptSystemInitializer.getScriptsDirectory(source.server.serverDirectory)
            // ServerScriptManager.createAndLoad(scriptsDir)

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
     * TODO: Implement globals loading in GraalVM
     */
    private fun globalsCommand(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source

        // TODO: Implement globals loading for GraalVM
        source.sendSuccess({ Component.literal("§6=== RhettJS Globals ===") }, false)
        source.sendSuccess({ Component.literal("§e[RhettJS] Globals loading not yet implemented in GraalVM migration") }, false)
        source.sendSuccess({ Component.literal("§7Will be added in Phase 6") }, false)

        return 1
    }

    /**
     * Handle /rjs probe command.
     * Introspects available RhettJS APIs and generates TypeScript definitions.
     * TODO: Implement TypeScript generation for GraalVM
     */
    private fun probeCommand(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source

        source.sendSuccess({ Component.literal("§6=== RhettJS API Probe ===") }, false)
        source.sendSuccess({ Component.literal("§e[RhettJS] TypeScript generation not yet implemented in GraalVM migration") }, false)
        source.sendSuccess({ Component.literal("§7TypeGenerator has been archived to dev-docs/removed-features/") }, false)
        source.sendSuccess({ Component.literal("§7Will be reimplemented in a future phase") }, false)

        return 1
    }

    /**
     * Extract a user-friendly error message from an exception.
     * Unwraps nested exceptions to find the root cause and formats it cleanly.
     */
    private fun extractUserFriendlyError(exception: Exception): String {
        // Find the root cause by unwrapping exceptions
        var cause: Throwable = exception
        while (cause.cause != null && cause.cause !== cause) {
            cause = cause.cause!!
        }

        // Handle common error types with friendly messages
        return when {
            // GraalVM classloader conflict
            cause.message?.contains("already loaded in another classloader") == true -> {
                "GraalVM context conflict. Try restarting the server or use a fresh world."
            }

            // Script syntax errors
            cause.message?.contains("SyntaxError") == true -> {
                // Extract just the meaningful part of syntax errors
                cause.message?.lines()?.firstOrNull { it.contains("SyntaxError") }
                    ?.substringAfter("SyntaxError: ")
                    ?: "Script syntax error"
            }

            // Module not found
            cause.message?.contains("Cannot find module") == true -> {
                cause.message?.lines()?.firstOrNull { it.contains("Cannot find module") }
                    ?: "Module not found"
            }

            // Script execution errors
            exception is org.graalvm.polyglot.PolyglotException -> {
                exception.message?.substringBefore("\n") ?: "Script execution error"
            }

            // Generic errors with clean messages
            cause.message != null && !cause.message!!.contains("java.") -> {
                cause.message!!
            }

            // Fallback
            else -> {
                "Script execution failed. Check server logs for details."
            }
        }
    }

}
