package com.rhett.rhettjs.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.*
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.rhett.rhettjs.adapter.CallerAdapter
import com.rhett.rhettjs.adapter.PlayerAdapter
import com.rhett.rhettjs.config.ConfigManager
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.item.ItemArgument
import net.minecraft.commands.arguments.blocks.BlockStateArgument
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyObject

/**
 * Registry for custom commands registered via the Commands API in JavaScript.
 *
 * Handles:
 * - Storing command data from JavaScript
 * - Converting to Brigadier command builders
 * - Registering with Minecraft's command dispatcher
 * - Argument type mapping
 * - Permission checks
 * - Async handler support
 *
 * Usage pattern:
 * 1. JavaScript calls Commands.register('name').argument(...).executes(...)
 * 2. GraalEngine stores command data in CustomCommandRegistry
 * 3. After startup scripts load, registerAll() converts to Brigadier commands
 * 4. Commands are available in-game
 */
class CustomCommandRegistry {

    private val commands = mutableMapOf<String, MutableMap<String, Any?>>()
    private var dispatcher: CommandDispatcher<CommandSourceStack>? = null
    private var context: Context? = null
    private var commandBuildContext: net.minecraft.commands.CommandBuildContext? = null

    /**
     * Store a command from JavaScript.
     *
     * @param name Command name
     * @param data Command data (description, arguments, executor, permission, etc.)
     */
    fun storeCommand(name: String, data: Map<String, Any?>) {
        commands[name] = data.toMutableMap()
        ConfigManager.debug("[Commands] Stored command: /$name (executor=${data["executor"] != null}, args=${(data["arguments"] as? List<*>)?.size ?: 0}, permission=${data["permission"] != null})")
    }

    /**
     * Get a stored command by name.
     */
    fun getCommand(name: String): Map<String, Any?>? {
        return commands[name]
    }

    /**
     * Store the Brigadier dispatcher and GraalVM context for later registration.
     * Called during command registration event (before server/startup scripts run).
     *
     * @param dispatcher The Minecraft command dispatcher
     * @param context The GraalVM context for executing JS handlers
     * @param buildContext The command build context for item/block arguments
     */
    fun storeDispatcher(
        dispatcher: CommandDispatcher<CommandSourceStack>,
        context: Context,
        buildContext: net.minecraft.commands.CommandBuildContext
    ) {
        this.dispatcher = dispatcher
        this.context = context
        this.commandBuildContext = buildContext
        ConfigManager.debug("[Commands] Stored dispatcher, context, and build context for custom commands")
    }

    /**
     * Register all stored commands with the Brigadier dispatcher.
     * Called after startup scripts have completed loading.
     *
     * Uses the dispatcher and context stored via storeDispatcher().
     */
    fun registerAll() {
        val disp = dispatcher ?: run {
            ConfigManager.debug("[Commands] Cannot register commands: dispatcher not stored")
            return
        }
        val ctx = context ?: run {
            ConfigManager.debug("[Commands] Cannot register commands: context not stored")
            return
        }

        ConfigManager.debug("[Commands] registerAll() called with ${commands.size} commands in registry")
        ConfigManager.debug("[Commands] Command list: ${commands.keys.joinToString(", ") { "/$it" }}")

        var successCount = 0
        var skipCount = 0
        var failCount = 0

        commands.forEach { (name, data) ->
            // Skip empty/removed commands
            if (data.isEmpty() || data["executor"] == null) {
                ConfigManager.debug("[Commands] Skipping command: /$name (no executor)")
                skipCount++
                return@forEach
            }

            try {
                validateCommand(data)
                ConfigManager.debug("[Commands] Building Brigadier command for: /$name")
                val command = buildCommand(name, data, ctx)
                ConfigManager.debug("[Commands] Registering with dispatcher: /$name")
                disp.register(command)
                ConfigManager.debug("[Commands] ✓ Successfully registered: /$name")
                successCount++
            } catch (e: Exception) {
                ConfigManager.debug("[Commands] ✗ Failed to register /$name: ${e.message}")
                e.printStackTrace()
                failCount++
            }
        }

        ConfigManager.debug("[Commands] Registration complete: $successCount successful, $skipCount skipped, $failCount failed")
    }

    /**
     * Validate command data before storing.
     *
     * Checks:
     * - Executor is present and executable
     * - Argument types are valid
     * - Permission is string or function
     *
     * @throws IllegalArgumentException if validation fails
     */
    fun validateCommand(data: Map<String, Any?>) {
        // Check executor exists
        val executor = data["executor"]
        if (executor == null) {
            throw IllegalArgumentException("Command must have an executor function")
        }

        // Check executor is callable (if it's a Value)
        if (executor is Value && !executor.canExecute()) {
            throw IllegalArgumentException("Executor must be a function")
        }

        // Validate argument types
        val arguments = data["arguments"] as? List<*> ?: emptyList<Any>()
        val validTypes = listOf("string", "int", "float", "player", "item", "block", "entity")

        arguments.forEach { arg ->
            if (arg is Map<*, *>) {
                val type = arg["type"] as? String
                if (type != null && type !in validTypes) {
                    throw IllegalArgumentException("Invalid argument type: $type. Valid types: ${validTypes.joinToString(", ")}")
                }
            }
        }
    }

    /**
     * Convert a stored command to a Brigadier LiteralArgumentBuilder.
     */
    private fun buildCommand(
        name: String,
        data: Map<String, Any?>,
        context: Context
    ): LiteralArgumentBuilder<CommandSourceStack> {
        val commandBuilder = Commands.literal(name)

        // Add permission check if specified (looks up dynamically for /reload support)
        val permission = data["permission"]
        if (permission != null) {
            commandBuilder.requires { source ->
                // Look up permission dynamically from registry (supports /reload)
                val currentData = getCommand(name)
                val currentPermission = currentData?.get("permission")
                if (currentPermission != null) {
                    checkPermission(currentPermission, source, context)
                } else {
                    true // No permission check
                }
            }
        }

        // Get arguments list
        @Suppress("UNCHECKED_CAST")
        val arguments = (data["arguments"] as? List<Map<String, String>>) ?: emptyList()

        // Build command tree with arguments
        if (arguments.isEmpty()) {
            // No arguments - executor looks up dynamically for /reload support
            commandBuilder.executes { brigadierContext ->
                ConfigManager.debug("[Commands] Executing command: /$name (no args)")
                val commandData = getCommand(name)
                if (commandData == null) {
                    ConfigManager.debug("[Commands] ✗ Command /$name not found in registry!")
                    throw IllegalStateException("Command $name not found in registry")
                }
                val executor = commandData["executor"] as? Value
                if (executor == null) {
                    ConfigManager.debug("[Commands] ✗ Command /$name has no executor!")
                    throw IllegalStateException("Command $name has no executor")
                }
                ConfigManager.debug("[Commands] Found executor for /$name, calling handler...")
                executeHandler(executor, brigadierContext, emptyList(), context)
            }
        } else {
            // Has arguments - build argument chain (also looks up executor dynamically)
            buildArgumentChain(commandBuilder, arguments, name, context)
        }

        return commandBuilder
    }

    /**
     * Build a chain of arguments for a command.
     */
    private fun buildArgumentChain(
        baseBuilder: LiteralArgumentBuilder<CommandSourceStack>,
        arguments: List<Map<String, String>>,
        commandName: String,
        context: Context
    ) {
        // Build from the last argument backwards
        var currentNode: Any = Commands.argument(
            arguments.last()["name"]!!,
            mapArgumentType(arguments.last()["type"]!!)
        ).executes { brigadierContext ->
            // Look up executor dynamically from registry (supports /reload)
            ConfigManager.debug("[Commands] Executing command: /$commandName (${arguments.size} args)")
            val commandData = getCommand(commandName)
            if (commandData == null) {
                ConfigManager.debug("[Commands] ✗ Command /$commandName not found in registry!")
                throw IllegalStateException("Command $commandName not found in registry")
            }
            val executor = commandData["executor"] as? Value
            if (executor == null) {
                ConfigManager.debug("[Commands] ✗ Command /$commandName has no executor!")
                throw IllegalStateException("Command $commandName has no executor")
            }
            ConfigManager.debug("[Commands] Found executor for /$commandName, calling handler...")
            executeHandler(executor, brigadierContext, arguments, context)
        }

        // Add remaining arguments in reverse order
        for (i in arguments.size - 2 downTo 0) {
            val argDef = arguments[i]
            val argBuilder = Commands.argument(argDef["name"]!!, mapArgumentType(argDef["type"]!!))

            @Suppress("UNCHECKED_CAST")
            when (currentNode) {
                is RequiredArgumentBuilder<*, *> -> {
                    argBuilder.then(currentNode as RequiredArgumentBuilder<CommandSourceStack, *>)
                }
            }

            currentNode = argBuilder
        }

        // Attach to base command
        @Suppress("UNCHECKED_CAST")
        baseBuilder.then(currentNode as RequiredArgumentBuilder<CommandSourceStack, *>)
    }

    /**
     * Map JavaScript argument type string to Brigadier ArgumentType.
     *
     * Valid types: string, int, float, player, item, block, entity
     */
    private fun mapArgumentType(type: String): ArgumentType<*> {
        return when (type) {
            "string" -> StringArgumentType.greedyString()
            "int" -> IntegerArgumentType.integer()
            "float" -> FloatArgumentType.floatArg()
            "player" -> EntityArgument.player()
            "item" -> {
                val buildCtx = commandBuildContext
                    ?: throw IllegalStateException("CommandBuildContext not available for item argument type")
                ItemArgument.item(buildCtx)
            }
            "block" -> {
                val buildCtx = commandBuildContext
                    ?: throw IllegalStateException("CommandBuildContext not available for block argument type")
                BlockStateArgument.block(buildCtx)
            }
            "entity" -> EntityArgument.entity()
            else -> throw IllegalArgumentException("Unknown argument type: $type")
        }
    }

    /**
     * Extract arguments from Brigadier context and execute JS handler.
     *
     * @return Brigadier command result (1 for success, 0 for failure)
     */
    private fun executeHandler(
        executor: Value,
        brigadierContext: com.mojang.brigadier.context.CommandContext<CommandSourceStack>,
        arguments: List<Map<String, String>>,
        graalContext: Context
    ): Int {
        try {
            ConfigManager.debug("[Commands] executeHandler called for: ${brigadierContext.input}")

            // Extract arguments
            val argsObject = extractArguments(brigadierContext, arguments, graalContext)

            // Create caller object
            val caller = CallerAdapter.toJS(brigadierContext.source, graalContext)
            ConfigManager.debug("[Commands] Created caller object: ${if (caller.isNull) "NULL" else "OK"}")

            // Create event object
            val event = ProxyObject.fromMap(mapOf(
                "caller" to caller,
                "args" to argsObject,
                "command" to brigadierContext.input.substringAfter("/").substringBefore(" ")
                // NOTE: Removed "source" - causes stack overflow
            ))

            // Execute handler
            ConfigManager.debug("[Commands] Calling executor.execute() with event object...")
            val result = executor.execute(graalContext.asValue(event))
            ConfigManager.debug("[Commands] Executor returned, checking result...")

            // Handle async (Promise) results
            if (result.canExecute() && result.hasMember("then")) {
                // It's a Promise - we can't wait for it in Brigadier
                // Just return success and let it complete async
                ConfigManager.debug("[Commands] Command returned a Promise - executing asynchronously")
                return 1
            }

            ConfigManager.debug("[Commands] ✓ Command executed successfully")
            return 1 // Success
        } catch (e: Exception) {
            ConfigManager.debug("[Commands] ✗ Command execution error: ${e.message}")
            e.printStackTrace()
            return 0 // Failure
        }
    }

    /**
     * Extract arguments from Brigadier context and build args object for JavaScript.
     */
    private fun extractArguments(
        brigadierContext: com.mojang.brigadier.context.CommandContext<CommandSourceStack>,
        arguments: List<Map<String, String>>,
        graalContext: Context
    ): Value {
        val argsMap = mutableMapOf<String, Any?>()

        arguments.forEach { argDef ->
            val name = argDef["name"]!!
            val type = argDef["type"]!!

            try {
                val value: Any? = when (type) {
                    "string" -> StringArgumentType.getString(brigadierContext, name)
                    "int" -> IntegerArgumentType.getInteger(brigadierContext, name)
                    "float" -> FloatArgumentType.getFloat(brigadierContext, name)
                    "player" -> {
                        // Extract player and wrap it
                        val player = EntityArgument.getPlayer(brigadierContext, name)
                        PlayerAdapter.toJS(player, graalContext)
                    }
                    "item" -> {
                        // For now, return item ID as string
                        // TODO: Proper item handling
                        brigadierContext.getArgument(name, String::class.java)
                    }
                    "block" -> {
                        // For now, return block ID as string
                        // TODO: Proper block handling
                        brigadierContext.getArgument(name, String::class.java)
                    }
                    "entity" -> {
                        // Return entity selector as string for now
                        // TODO: Proper entity handling
                        brigadierContext.getArgument(name, String::class.java)
                    }
                    else -> null
                }

                if (value != null) {
                    argsMap[name] = value
                }
            } catch (e: Exception) {
                ConfigManager.debug("Failed to extract argument '$name': ${e.message}")
            }
        }

        return graalContext.asValue(ProxyObject.fromMap(argsMap))
    }

    /**
     * Check if caller has permission to execute command.
     */
    private fun checkPermission(
        permission: Any?,
        source: CommandSourceStack,
        context: Context
    ): Boolean {
        return when (permission) {
            is String -> {
                // String permission - check op level or permission system
                // For now, just check op level 2+
                source.hasPermission(2)
            }
            is Value -> {
                if (permission.canExecute()) {
                    // Function permission - call it with caller object
                    try {
                        val caller = CallerAdapter.toJS(source, context)
                        val result = permission.execute(caller)
                        result.asBoolean()
                    } catch (e: Exception) {
                        false
                    }
                } else {
                    false
                }
            }
            else -> true // No permission check
        }
    }

    /**
     * Clear all stored commands.
     */
    fun clear() {
        val count = commands.size
        val names = commands.keys.joinToString(", ") { "/$it" }
        commands.clear()
        ConfigManager.debug("[Commands] Cleared $count commands from registry: $names")
    }

    /**
     * Get all command names.
     */
    fun getCommandNames(): Set<String> = commands.keys
}
