package com.rhett.rhettjs.commands

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable

/**
 * Full Brigadier command builder wrapper for JavaScript.
 * Exposes Brigadier's fluent API directly for advanced command building.
 *
 * Usage:
 * ```javascript
 * ServerEvents.command('test', builder => {
 *     builder.literal('test')
 *         .then(
 *             builder.argument('player', builder.arguments.PLAYER)
 *                 .executes(ctx => {
 *                     let player = builder.arguments.PLAYER.get(ctx, 'player');
 *                     return 1;
 *                 })
 *         );
 * });
 * ```
 */
class BrigadierCommandBuilder(
    private val commandName: String,
    private val scope: Scriptable
) {
    // Expose argument types as 'arguments' (to avoid conflict with JS 'arguments')
    @Suppress("unused")
    val arguments: ArgumentTypeWrappers = ArgumentTypeWrappers.create(scope)

    // Store the root command builder
    private var root: LiteralArgumentBuilder<CommandSourceStack>? = null

    /**
     * Create a literal argument builder (subcommand or root command).
     * JavaScript: builder.literal('commandname')
     */
    fun literal(name: String): JavaScriptLiteralBuilder {
        val literalBuilder = Commands.literal(name)

        // Store as root if it's the command name
        if (name == commandName && root == null) {
            root = literalBuilder
        }

        return JavaScriptLiteralBuilder(literalBuilder, scope)
    }

    /**
     * Create a required argument builder.
     * JavaScript: builder.argument('argname', builder.arguments.PLAYER)
     */
    fun argument(name: String, type: ArgumentTypeWrapper<*>): JavaScriptArgumentBuilder {
        val argBuilder = Commands.argument(name, type.create())
        return JavaScriptArgumentBuilder(argBuilder, scope)
    }

    /**
     * Get the built root command.
     * Returns null if literal() was never called for the command name.
     */
    fun build(): LiteralArgumentBuilder<CommandSourceStack>? {
        return root
    }
}

/**
 * Wrapper for Brigadier's LiteralArgumentBuilder that's friendly to JavaScript.
 * Exposes methods that return JavaScript-wrapped builders for chaining.
 */
class JavaScriptLiteralBuilder(
    internal val builder: LiteralArgumentBuilder<CommandSourceStack>,
    private val scope: Scriptable
) {
    /**
     * Add a child node (subcommand or argument).
     * JavaScript: .then(builder.literal('subcommand'))
     */
    fun then(child: Any): JavaScriptLiteralBuilder {
        when (child) {
            is JavaScriptLiteralBuilder -> builder.then(child.builder)
            is JavaScriptArgumentBuilder -> builder.then(child.builder)
            is LiteralArgumentBuilder<*> -> {
                @Suppress("UNCHECKED_CAST")
                builder.then(child as LiteralArgumentBuilder<CommandSourceStack>)
            }
            is RequiredArgumentBuilder<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                builder.then(child as RequiredArgumentBuilder<CommandSourceStack, *>)
            }
        }
        return this
    }

    /**
     * Set command executor.
     * JavaScript: .executes(ctx => { return 1; })
     */
    fun executes(handler: Function): JavaScriptLiteralBuilder {
        builder.executes { context ->
            try {
                val cx = Context.enter()
                try {
                    cx.optimizationLevel = -1
                    cx.languageVersion = Context.VERSION_ES6

                    // Create event loop for async operations (task(), wait(), etc.)
                    val eventLoop = com.rhett.rhettjs.threading.EventLoop(cx, scope)
                    com.rhett.rhettjs.threading.EventLoop.setCurrent(eventLoop)

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

                    // Run event loop until complete
                    com.rhett.rhettjs.RhettJSCommon.LOGGER.info("[RhettJS] Command handler returned, running event loop...")
                    eventLoop.runUntilComplete("command-handler")
                    com.rhett.rhettjs.RhettJSCommon.LOGGER.info("[RhettJS] Event loop completed")
                    com.rhett.rhettjs.threading.EventLoop.setCurrent(null)

                    when (result) {
                        is Number -> result.toInt()
                        is Boolean -> if (result) 1 else 0
                        else -> 1
                    }
                } finally {
                    Context.exit()
                }
            } catch (e: Exception) {
                com.rhett.rhettjs.RhettJSCommon.LOGGER.error("[RhettJS] Command execution error", e)
                0
            }
        }
        return this
    }

    /**
     * Set permission requirement.
     * JavaScript: .requires(2)
     */
    fun requires(level: Int): JavaScriptLiteralBuilder {
        builder.requires { it.hasPermission(level) }
        return this
    }

    /**
     * Get the underlying Brigadier builder (for registration).
     */
    fun getBuilder(): LiteralArgumentBuilder<CommandSourceStack> = builder
}

/**
 * Wrapper for Brigadier's RequiredArgumentBuilder that's friendly to JavaScript.
 */
class JavaScriptArgumentBuilder(
    internal val builder: RequiredArgumentBuilder<CommandSourceStack, *>,
    private val scope: Scriptable
) {
    /**
     * Add a child node.
     * JavaScript: .then(builder.argument('next', type))
     */
    fun then(child: Any): JavaScriptArgumentBuilder {
        when (child) {
            is JavaScriptLiteralBuilder -> builder.then(child.builder)
            is JavaScriptArgumentBuilder -> builder.then(child.builder)
            is LiteralArgumentBuilder<*> -> {
                @Suppress("UNCHECKED_CAST")
                builder.then(child as LiteralArgumentBuilder<CommandSourceStack>)
            }
            is RequiredArgumentBuilder<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                builder.then(child as RequiredArgumentBuilder<CommandSourceStack, *>)
            }
        }
        return this
    }

    /**
     * Set command executor.
     */
    fun executes(handler: Function): JavaScriptArgumentBuilder {
        builder.executes { context ->
            try {
                val cx = Context.enter()
                try {
                    cx.optimizationLevel = -1
                    cx.languageVersion = Context.VERSION_ES6

                    // Create event loop for async operations (task(), wait(), etc.)
                    val eventLoop = com.rhett.rhettjs.threading.EventLoop(cx, scope)
                    com.rhett.rhettjs.threading.EventLoop.setCurrent(eventLoop)

                    // Inject Command API
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

                    // Run event loop until complete
                    com.rhett.rhettjs.RhettJSCommon.LOGGER.info("[RhettJS] Command handler returned (arg builder), running event loop...")
                    eventLoop.runUntilComplete("command-handler")
                    com.rhett.rhettjs.RhettJSCommon.LOGGER.info("[RhettJS] Event loop completed (arg builder)")
                    com.rhett.rhettjs.threading.EventLoop.setCurrent(null)

                    when (result) {
                        is Number -> result.toInt()
                        is Boolean -> if (result) 1 else 0
                        else -> 1
                    }
                } finally {
                    Context.exit()
                }
            } catch (e: Exception) {
                com.rhett.rhettjs.RhettJSCommon.LOGGER.error("[RhettJS] Command execution error", e)
                0
            }
        }
        return this
    }

    /**
     * Add custom suggestions.
     * JavaScript: .suggests((ctx, builder) => { return ['suggestion1', 'suggestion2']; })
     */
    fun suggests(handler: Function): JavaScriptArgumentBuilder {
        builder.suggests { context, suggestionsBuilder ->
            try {
                val cx = Context.enter()
                try {
                    cx.optimizationLevel = -1
                    cx.languageVersion = Context.VERSION_ES6

                    val wrappedContext = context.wrapForJavaScript(scope)
                    val wrappedBuilder = Context.javaToJS(suggestionsBuilder, scope)

                    val result = handler.call(cx, scope, scope, arrayOf(wrappedContext, wrappedBuilder))

                    // If result is an array of strings, add them as suggestions
                    if (result is org.mozilla.javascript.NativeArray) {
                        for (i in 0 until result.length) {
                            val suggestion = result.get(i.toLong())
                            if (suggestion is String) {
                                suggestionsBuilder.suggest(suggestion)
                            }
                        }
                    }

                    suggestionsBuilder.buildFuture()
                } finally {
                    Context.exit()
                }
            } catch (e: Exception) {
                com.rhett.rhettjs.RhettJSCommon.LOGGER.error("[RhettJS] Suggestion error", e)
                suggestionsBuilder.buildFuture()
            }
        }
        return this
    }

    /**
     * Set permission requirement.
     */
    fun requires(level: Int): JavaScriptArgumentBuilder {
        builder.requires { it.hasPermission(level) }
        return this
    }
}
