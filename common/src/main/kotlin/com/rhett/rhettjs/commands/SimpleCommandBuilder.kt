package com.rhett.rhettjs.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.rhett.rhettjs.RhettJSCommon
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable

/**
 * Simplified command builder using imperative API instead of method chaining.
 * This avoids Rhino 1.8.1's object wrapping issues.
 *
 * Usage:
 * ```javascript
 * ServerEvents.command('test', function(cmd) {
 *   cmd.addArgument('player', cmd.types.PLAYER);
 *   cmd.addArgument('amount', cmd.types.INTEGER);
 *   cmd.setExecutor(function(context) {
 *     let player = cmd.types.PLAYER.get(context, 'player');
 *     let amount = cmd.types.INTEGER.get(context, 'amount');
 *     return 1;
 *   });
 * });
 * ```
 */
class SimpleCommandBuilder(
    private val commandName: String,
    private val scope: Scriptable
) {
    private val rootBuilder = Commands.literal(commandName)
    private var currentBuilder: Any = rootBuilder
    private val argumentChain = mutableListOf<RequiredArgumentBuilder<CommandSourceStack, *>>()

    // Expose argument types
    @Suppress("unused")
    val types: ArgumentTypeWrappers = ArgumentTypeWrappers.create(scope)

    /**
     * Add a typed argument to the command.
     */
    fun addArgument(name: String, type: ArgumentTypeWrapper<*>) {
        val argBuilder = Commands.argument(name, type.create())
        argumentChain.add(argBuilder)
    }

    /**
     * Set the command executor.
     */
    fun setExecutor(handler: Function) {
        // Create the executor
        val executor = { context: com.mojang.brigadier.context.CommandContext<CommandSourceStack> ->
            try {
                val cx = Context.enter()
                try {
                    cx.optimizationLevel = -1
                    cx.languageVersion = Context.VERSION_ES6

                    // Inject Command API if not already present
                    if (!scope.has("Command", scope)) {
                        val source = context.source
                        val player = source.entity as? net.minecraft.server.level.ServerPlayer
                        val commandAPI = com.rhett.rhettjs.api.CommandAPI(source.server, player)
                        val commandWrapper = com.rhett.rhettjs.api.CommandAPIWrapper(commandAPI, source.server)
                        commandWrapper.parentScope = scope
                        scope.put("Command", scope, commandWrapper)
                    }

                    val wrappedContext = context.wrapForJavaScript(scope)
                    val result = handler.call(cx, scope, scope, arrayOf(wrappedContext))
                    cx.processMicrotasks()

                    when (result) {
                        is Number -> result.toInt()
                        is Boolean -> if (result) 1 else 0
                        else -> 1
                    }
                } finally {
                    Context.exit()
                }
            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[RhettJS] Command execution error", e)
                0
            }
        }

        // Build the chain: attach executor to last argument, then chain backwards to root
        if (argumentChain.isEmpty()) {
            // No arguments, attach executor directly to root
            rootBuilder.executes(executor)
        } else {
            // Attach executor to the last argument
            val lastArg = argumentChain.last()
            lastArg.executes(executor)

            // Chain arguments together from first to last
            var prevBuilder: com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, *> = rootBuilder
            for (argBuilder in argumentChain) {
                @Suppress("UNCHECKED_CAST")
                (prevBuilder as com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, *>).then(argBuilder)
                prevBuilder = argBuilder
            }
        }
    }

    /**
     * Set permission level required for the command.
     */
    fun requiresPermission(level: Int) {
        rootBuilder.requires { it.hasPermission(level) }
    }

    /**
     * Build the final command.
     */
    fun build(): LiteralArgumentBuilder<CommandSourceStack> {
        return rootBuilder
    }
}
