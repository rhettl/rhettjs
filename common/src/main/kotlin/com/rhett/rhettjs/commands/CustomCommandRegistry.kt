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
import net.minecraft.server.level.ServerPlayer
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
    internal var dispatcher: CommandDispatcher<CommandSourceStack>? = null
    internal var context: Context? = null
    internal var commandBuildContext: net.minecraft.commands.CommandBuildContext? = null

    /**
     * Store a command from JavaScript.
     *
     * @param name Command name
     * @param data Command data (description, arguments, executor, permission, etc.)
     */
    fun storeCommand(name: String, data: Map<String, Any?>) {
        commands[name] = data.toMutableMap()
        ConfigManager.debug("[Commands] Stored command: /$name (executor=${data["executor"] != null}, args=${(data["arguments"] as? List<*>)?.size ?: 0}, permission=${data["permission"] != null})")

        // Update context reference if it's null (happens after GraalEngine.reset())
        if (context == null && dispatcher != null) {
            ConfigManager.debug("[Commands] Context was null, will need re-storing before execution")
        }
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
            // Skip empty commands
            if (data.isEmpty()) {
                ConfigManager.debug("[Commands] Skipping command: /$name (empty data)")
                skipCount++
                return@forEach
            }

            // Check if command has executor OR subcommands
            @Suppress("UNCHECKED_CAST")
            val subcommands = (data["subcommands"] as? Map<String, Map<String, Any?>>) ?: emptyMap()
            val hasExecutor = data["executor"] != null
            val hasSubcommands = subcommands.isNotEmpty()

            if (!hasExecutor && !hasSubcommands) {
                ConfigManager.debug("[Commands] Skipping command: /$name (no executor and no subcommands)")
                skipCount++
                return@forEach
            }

            try {
                validateCommand(data)
                ConfigManager.debug("[Commands] Building Brigadier command for: /$name")
                val command = buildCommand(name, data)
                ConfigManager.debug("[Commands] Registering with dispatcher: /$name")
                disp.register(command)
                ConfigManager.debug("[Commands] ✓ Successfully registered: /$name")
                successCount++
            } catch (e: IllegalArgumentException) {
                // Validation errors should propagate
                throw e
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
     * - Executor is present and executable (if no subcommands)
     * - Subcommands have executors (if present)
     * - Argument types are valid
     * - Permission is string or function
     *
     * @throws IllegalArgumentException if validation fails
     */
    fun validateCommand(data: Map<String, Any?>) {
        // Check for subcommands
        @Suppress("UNCHECKED_CAST")
        val subcommands = (data["subcommands"] as? Map<String, Map<String, Any?>>) ?: emptyMap()

        if (subcommands.isEmpty()) {
            // No subcommands - must have executor
            val executor = data["executor"]
            if (executor == null) {
                throw IllegalArgumentException("Command must have an executor function")
            }

            // Check executor is callable (if it's a Value)
            if (executor is Value && !executor.canExecute()) {
                throw IllegalArgumentException("Executor must be a function")
            }
        } else {
            // Has subcommands - each subcommand must have an executor
            subcommands.forEach { (subcommandName, subcommandData) ->
                val executor = subcommandData["executor"]
                if (executor == null) {
                    throw IllegalArgumentException("Subcommand '$subcommandName' must have an executor function")
                }

                if (executor is Value && !executor.canExecute()) {
                    throw IllegalArgumentException("Subcommand '$subcommandName' executor must be a function")
                }
            }
        }

        // Validate argument types for main command
        val arguments = data["arguments"] as? List<*> ?: emptyList<Any>()
        val validTypes = listOf("string", "int", "float", "player", "item", "block", "entity")

        // Check for required arguments after optional ones
        var foundOptional = false
        arguments.forEach { arg ->
            if (arg is Map<*, *>) {
                val type = arg["type"] as? String
                if (type != null && type !in validTypes) {
                    throw IllegalArgumentException("Invalid argument type: $type. Valid types: ${validTypes.joinToString(", ")}")
                }

                val isOptional = arg["optional"] as? Boolean ?: false
                if (foundOptional && !isOptional) {
                    val argName = arg["name"] as? String ?: "unknown"
                    throw IllegalArgumentException("Required argument '$argName' cannot come after optional arguments")
                }
                if (isOptional) {
                    foundOptional = true
                }
            }
        }

        // Validate argument types for subcommands
        subcommands.forEach { (subcommandName, subcommandData) ->
            @Suppress("UNCHECKED_CAST")
            val subArguments = subcommandData["arguments"] as? List<Map<String, Any?>> ?: emptyList()
            var foundOptional = false
            subArguments.forEach { arg ->
                val type = arg["type"] as? String
                if (type != null && type !in validTypes) {
                    throw IllegalArgumentException("Subcommand '$subcommandName' has invalid argument type: $type. Valid types: ${validTypes.joinToString(", ")}")
                }

                val isOptional = arg["optional"] as? Boolean ?: false
                if (foundOptional && !isOptional) {
                    val argName = arg["name"] as? String ?: "unknown"
                    throw IllegalArgumentException("Subcommand '$subcommandName': Required argument '$argName' cannot come after optional arguments")
                }
                if (isOptional) {
                    foundOptional = true
                }
            }
        }
    }

    /**
     * Convert a stored command to a Brigadier LiteralArgumentBuilder.
     */
    private fun buildCommand(
        name: String,
        data: Map<String, Any?>
    ): LiteralArgumentBuilder<CommandSourceStack> {
        val commandBuilder = Commands.literal(name)

        // Add permission check if specified (looks up dynamically for /reload support)
        val permission = data["permission"]
        if (permission != null) {
            commandBuilder.requires { source ->
                // Look up permission dynamically from registry (supports /reload)
                val currentData = getCommand(name)
                val currentPermission = currentData?.get("permission")
                ConfigManager.debug("[Commands] Requires check for /$name: permission=$currentPermission, context=${this.context != null}")
                if (currentPermission != null) {
                    val currentContext = this.context
                    if (currentContext != null) {
                        val result = checkPermission(currentPermission, source, currentContext)
                        ConfigManager.debug("[Commands] Permission result for /$name: $result")
                        result
                    } else {
                        ConfigManager.debug("[Commands] No context for /$name, allowing")
                        true // No context available, allow by default
                    }
                } else {
                    true // No permission check
                }
            }
        }

        // Check for subcommands
        @Suppress("UNCHECKED_CAST")
        val subcommands = (data["subcommands"] as? Map<String, Map<String, Any?>>) ?: emptyMap()

        if (subcommands.isNotEmpty()) {
            // Has subcommands - build subcommand tree
            ConfigManager.debug("[Commands] Building command /$name with ${subcommands.size} subcommands")
            subcommands.forEach { (subcommandName, subcommandData) ->
                val subcommandLiteral = Commands.literal(subcommandName)

                @Suppress("UNCHECKED_CAST")
                val subArguments = (subcommandData["arguments"] as? List<Map<String, Any?>>) ?: emptyList()

                if (subArguments.isEmpty()) {
                    // No arguments for subcommand
                    subcommandLiteral.executes { brigadierContext ->
                        ConfigManager.debug("[Commands] Executing subcommand: /$name $subcommandName (no args)")
                        executeSubcommand(name, subcommandName, brigadierContext, emptyList())
                    }
                } else {
                    // Has arguments for subcommand
                    val allOptional = subArguments.all { it["optional"] == true }
                    if (allOptional) {
                        // All arguments are optional - add execution point to subcommand literal itself
                        subcommandLiteral.executes { brigadierContext ->
                            ConfigManager.debug("[Commands] Executing subcommand: /$name $subcommandName (0/${subArguments.size} optional args)")
                            executeSubcommand(name, subcommandName, brigadierContext, emptyList())
                        }
                    }
                    buildSubcommandArgumentChain(subcommandLiteral, subArguments, name, subcommandName)
                }

                commandBuilder.then(subcommandLiteral)
            }
        } else {
            // No subcommands - use original command logic
            // Get arguments list
            @Suppress("UNCHECKED_CAST")
            val arguments = (data["arguments"] as? List<Map<String, Any?>>) ?: emptyList()

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
                    // Look up current context from registry (supports /reload)
                    val currentContext = this.context ?: throw IllegalStateException("GraalVM context not available")
                    executeHandler(executor, brigadierContext, emptyList(), currentContext, name, null)
                }
            } else {
                // Has arguments - build argument chain (also looks up executor dynamically)
                buildArgumentChain(commandBuilder, arguments, name)
            }
        }

        return commandBuilder
    }

    /**
     * Build a chain of arguments for a command with support for optional arguments.
     * Creates multiple execution points - one after each optional argument.
     */
    private fun buildArgumentChain(
        baseBuilder: LiteralArgumentBuilder<CommandSourceStack>,
        arguments: List<Map<String, Any?>>,
        commandName: String
    ) {
        if (arguments.isEmpty()) return

        // Find the index of the first optional argument
        val firstOptionalIndex = arguments.indexOfFirst { it["optional"] == true }
        val hasOptionals = firstOptionalIndex >= 0

        if (!hasOptionals) {
            // No optional arguments - build simple chain with single execution point
            buildSimpleArgumentChain(baseBuilder, arguments, commandName, null)
        } else {
            // Has optional arguments - build multiple execution points
            buildOptionalArgumentChain(baseBuilder, arguments, commandName, null)
        }
    }

    /**
     * Build a simple argument chain (no optional arguments).
     */
    private fun buildSimpleArgumentChain(
        baseBuilder: LiteralArgumentBuilder<CommandSourceStack>,
        arguments: List<Map<String, Any?>>,
        commandName: String,
        subcommandName: String?
    ) {
        // Build from the last argument backwards
        var currentNode: Any = Commands.argument(
            arguments.last()["name"] as String,
            mapArgumentType(arguments.last()["type"] as String)
        ).executes { brigadierContext ->
            ConfigManager.debug("[Commands] Executing: /$commandName${if (subcommandName != null) " $subcommandName" else ""} (${arguments.size} args)")
            if (subcommandName != null) {
                executeSubcommand(commandName, subcommandName, brigadierContext, arguments)
            } else {
                val commandData = getCommand(commandName) ?: throw IllegalStateException("Command $commandName not found")
                val executor = commandData["executor"] as? Value ?: throw IllegalStateException("Command $commandName has no executor")
                val currentContext = this.context ?: throw IllegalStateException("GraalVM context not available")
                executeHandler(executor, brigadierContext, arguments, currentContext, commandName, null)
            }
        }

        // Add remaining arguments in reverse order
        for (i in arguments.size - 2 downTo 0) {
            val argDef = arguments[i]
            val argBuilder = Commands.argument(argDef["name"] as String, mapArgumentType(argDef["type"] as String))

            @Suppress("UNCHECKED_CAST")
            when (currentNode) {
                is RequiredArgumentBuilder<*, *> -> {
                    argBuilder.then(currentNode as RequiredArgumentBuilder<CommandSourceStack, *>)
                }
            }

            currentNode = argBuilder
        }

        // Attach to base
        @Suppress("UNCHECKED_CAST")
        baseBuilder.then(currentNode as RequiredArgumentBuilder<CommandSourceStack, *>)
    }

    /**
     * Build argument chain with optional arguments.
     * Creates multiple execution points - one after each optional argument.
     */
    private fun buildOptionalArgumentChain(
        baseBuilder: LiteralArgumentBuilder<CommandSourceStack>,
        arguments: List<Map<String, Any?>>,
        commandName: String,
        subcommandName: String?
    ) {
        // Build execution points at each optional argument depth
        // Start from the last optional argument and work backwards

        var previousNode: RequiredArgumentBuilder<CommandSourceStack, *>? = null

        // Build from last argument to first
        for (depth in arguments.indices.reversed()) {
            val argDef = arguments[depth]
            val isOptional = argDef["optional"] == true
            val nextArgIsOptional = depth + 1 < arguments.size && arguments[depth + 1]["optional"] == true

            val argBuilder = Commands.argument(
                argDef["name"] as String,
                mapArgumentType(argDef["type"] as String)
            )

            // Add execution point if this is optional OR if next arg is optional
            // (adding execution to last required arg before first optional makes subsequent args show as [optional])
            if (isOptional || nextArgIsOptional) {
                argBuilder.executes { brigadierContext ->
                    ConfigManager.debug("[Commands] Executing: /$commandName${if (subcommandName != null) " $subcommandName" else ""} (${depth + 1}/${arguments.size} args)")
                    if (subcommandName != null) {
                        // Pass FULL arguments list so defaults can be applied to unprovided args
                        executeSubcommand(commandName, subcommandName, brigadierContext, arguments)
                    } else {
                        val commandData = getCommand(commandName) ?: throw IllegalStateException("Command $commandName not found")
                        val executor = commandData["executor"] as? Value ?: throw IllegalStateException("Command $commandName has no executor")
                        val currentContext = this.context ?: throw IllegalStateException("GraalVM context not available")
                        // Pass FULL arguments list so defaults can be applied to unprovided args
                        executeHandler(executor, brigadierContext, arguments, currentContext, commandName, null)
                    }
                }
            }

            // Link to previous node in chain
            if (previousNode != null) {
                argBuilder.then(previousNode)
            } else if (depth == arguments.lastIndex) {
                // Last argument always gets an execution point (if it didn't already get one above)
                if (!isOptional && !nextArgIsOptional) {
                    argBuilder.executes { brigadierContext ->
                        ConfigManager.debug("[Commands] Executing: /$commandName${if (subcommandName != null) " $subcommandName" else ""} (${arguments.size} args)")
                        if (subcommandName != null) {
                            executeSubcommand(commandName, subcommandName, brigadierContext, arguments)
                        } else {
                            val commandData = getCommand(commandName) ?: throw IllegalStateException("Command $commandName not found")
                            val executor = commandData["executor"] as? Value ?: throw IllegalStateException("Command $commandName has no executor")
                            val currentContext = this.context ?: throw IllegalStateException("GraalVM context not available")
                            executeHandler(executor, brigadierContext, arguments, currentContext, commandName, null)
                        }
                    }
                }
            }

            @Suppress("UNCHECKED_CAST")
            previousNode = argBuilder as RequiredArgumentBuilder<CommandSourceStack, *>
        }

        // Attach the chain to the base builder
        if (previousNode != null) {
            baseBuilder.then(previousNode)
        }
    }

    /**
     * Build a chain of arguments for a subcommand with support for optional arguments.
     */
    private fun buildSubcommandArgumentChain(
        baseBuilder: LiteralArgumentBuilder<CommandSourceStack>,
        arguments: List<Map<String, Any?>>,
        commandName: String,
        subcommandName: String
    ) {
        if (arguments.isEmpty()) return

        // Find the index of the first optional argument
        val firstOptionalIndex = arguments.indexOfFirst { it["optional"] == true }
        val hasOptionals = firstOptionalIndex >= 0

        if (!hasOptionals) {
            // No optional arguments - build simple chain
            buildSimpleArgumentChain(baseBuilder, arguments, commandName, subcommandName)
        } else {
            // Has optional arguments - build multiple execution points
            buildOptionalArgumentChain(baseBuilder, arguments, commandName, subcommandName)
        }
    }

    /**
     * Execute a subcommand by looking up its executor and calling it.
     */
    private fun executeSubcommand(
        commandName: String,
        subcommandName: String,
        brigadierContext: com.mojang.brigadier.context.CommandContext<CommandSourceStack>,
        arguments: List<Map<String, Any?>>
    ): Int {
        // Look up command and subcommand data dynamically
        val commandData = getCommand(commandName)
        if (commandData == null) {
            ConfigManager.debug("[Commands] ✗ Command /$commandName not found in registry!")
            throw IllegalStateException("Command $commandName not found in registry")
        }

        @Suppress("UNCHECKED_CAST")
        val subcommands = commandData["subcommands"] as? Map<String, Map<String, Any?>>
        if (subcommands == null) {
            ConfigManager.debug("[Commands] ✗ Command /$commandName has no subcommands!")
            throw IllegalStateException("Command $commandName has no subcommands")
        }

        val subcommandData = subcommands[subcommandName]
        if (subcommandData == null) {
            ConfigManager.debug("[Commands] ✗ Subcommand /$commandName $subcommandName not found!")
            throw IllegalStateException("Subcommand $commandName $subcommandName not found")
        }

        val executor = subcommandData["executor"] as? Value
        if (executor == null) {
            ConfigManager.debug("[Commands] ✗ Subcommand /$commandName $subcommandName has no executor!")
            throw IllegalStateException("Subcommand $commandName $subcommandName has no executor")
        }

        ConfigManager.debug("[Commands] Found executor for /$commandName $subcommandName, calling handler...")
        val currentContext = this.context ?: throw IllegalStateException("GraalVM context not available")
        return executeHandler(executor, brigadierContext, arguments, currentContext, commandName, subcommandName)
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
     * @param executor The JavaScript function to execute
     * @param brigadierContext The Brigadier command context
     * @param arguments The command arguments to extract
     * @param graalContext The GraalVM context
     * @param commandName The command name
     * @param subcommandName The subcommand name (null if not a subcommand)
     * @return Brigadier command result (1 for success, 0 for failure)
     */
    private fun executeHandler(
        executor: Value,
        brigadierContext: com.mojang.brigadier.context.CommandContext<CommandSourceStack>,
        arguments: List<Map<String, Any?>>,
        graalContext: Context,
        commandName: String,
        subcommandName: String?
    ): Int {
        try {
            ConfigManager.debug("[Commands] executeHandler called for: ${brigadierContext.input}")

            // Extract arguments with defaults for optional args
            val argsObject = extractArgumentsWithDefaults(brigadierContext, arguments, graalContext)

            // Create caller object
            val caller = CallerAdapter.toJS(brigadierContext.source, graalContext)
            ConfigManager.debug("[Commands] Created caller object: ${if (caller.isNull) "NULL" else "OK"}")

            // Create event object
            val eventMap = mutableMapOf<String, Any>(
                "caller" to caller,
                "args" to argsObject,
                "command" to commandName
            )

            // Add subcommand if present
            if (subcommandName != null) {
                eventMap["subcommand"] = subcommandName
            }

            val event = ProxyObject.fromMap(eventMap)

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

        // Debug: Log all arguments in the context
        ConfigManager.debug("[Commands] Brigadier context input: ${brigadierContext.input}")
        ConfigManager.debug("[Commands] Brigadier context nodes: ${brigadierContext.nodes.map { it.node.name }}")

        arguments.forEach { argDef ->
            val name = argDef["name"]!!
            val type = argDef["type"]!!

            try {
                ConfigManager.debug("[Commands] Extracting argument: $name (type=$type)")
                val value: Any? = when (type) {
                    "string" -> StringArgumentType.getString(brigadierContext, name)
                    "int" -> IntegerArgumentType.getInteger(brigadierContext, name)
                    "float" -> FloatArgumentType.getFloat(brigadierContext, name).toDouble()
                    "player" -> {
                        // Extract player and wrap it
                        ConfigManager.debug("[Commands] Extracting player argument: $name")
                        val player = EntityArgument.getPlayer(brigadierContext, name)
                        ConfigManager.debug("[Commands] Got player: ${player.name.string}")
                        val wrappedPlayer = PlayerAdapter.toJS(player, graalContext)
                        ConfigManager.debug("[Commands] Wrapped player into JS object")
                        wrappedPlayer
                    }
                    "item" -> {
                        // Extract ItemInput and convert to resource location string
                        val itemInput = net.minecraft.commands.arguments.item.ItemArgument.getItem(brigadierContext, name)
                        val itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(itemInput.item).toString()
                        ConfigManager.debug("[Commands] Extracted item: $itemId")
                        itemId
                    }
                    "block" -> {
                        // Extract BlockInput and convert to resource location string
                        val blockInput = net.minecraft.commands.arguments.blocks.BlockStateArgument.getBlock(brigadierContext, name)
                        val blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blockInput.state.block).toString()
                        ConfigManager.debug("[Commands] Extracted block: $blockId")
                        blockId
                    }
                    "entity" -> {
                        // Extract entity and return basic info
                        val entity = EntityArgument.getEntity(brigadierContext, name)
                        val entityType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()
                        val entityInfo = mapOf(
                            "name" to entity.name.string,
                            "type" to entityType,
                            "uuid" to entity.uuid.toString()
                        )
                        ConfigManager.debug("[Commands] Extracted entity: $entityType")
                        graalContext.asValue(entityInfo)
                    }
                    else -> null
                }

                if (value != null) {
                    argsMap[name] = value
                    ConfigManager.debug("[Commands] Added argument $name to args object")
                } else {
                    ConfigManager.debug("[Commands] Argument $name was null, skipping")
                }
            } catch (e: Exception) {
                ConfigManager.debug("[Commands] ✗ Failed to extract argument '$name': ${e.message}")
                e.printStackTrace()
            }
        }

        ConfigManager.debug("[Commands] Final args map keys: ${argsMap.keys}")

        return graalContext.asValue(ProxyObject.fromMap(argsMap))
    }

    /**
     * Extract arguments from Brigadier context with support for optional arguments and defaults.
     *
     * For optional arguments:
     * - If hasDefault=true and arg not provided: use the default value
     * - If hasDefault=false (null sentinel) and arg not provided: don't include key in map (undefined in JS)
     */
    private fun extractArgumentsWithDefaults(
        brigadierContext: com.mojang.brigadier.context.CommandContext<CommandSourceStack>,
        arguments: List<Map<String, Any?>>,
        graalContext: Context
    ): Value {
        val argsMap = mutableMapOf<String, Any?>()

        ConfigManager.debug("[Commands] Extracting arguments with defaults")
        ConfigManager.debug("[Commands] Brigadier context input: ${brigadierContext.input}")

        arguments.forEach { argDef ->
            val name = argDef["name"] as String
            val type = argDef["type"] as String
            val isOptional = argDef["optional"] as? Boolean ?: false
            val hasDefault = argDef["hasDefault"] as? Boolean ?: false
            val defaultValue = argDef["default"]

            // Try to extract the argument from Brigadier context
            val extracted = try {
                when (type) {
                    "string" -> StringArgumentType.getString(brigadierContext, name)
                    "int" -> IntegerArgumentType.getInteger(brigadierContext, name)
                    "float" -> FloatArgumentType.getFloat(brigadierContext, name).toDouble()
                    "player" -> {
                        val player = EntityArgument.getPlayer(brigadierContext, name)
                        PlayerAdapter.toJS(player, graalContext)
                    }
                    "item" -> {
                        val itemInput = net.minecraft.commands.arguments.item.ItemArgument.getItem(brigadierContext, name)
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(itemInput.item).toString()
                    }
                    "block" -> {
                        val blockInput = net.minecraft.commands.arguments.blocks.BlockStateArgument.getBlock(brigadierContext, name)
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blockInput.state.block).toString()
                    }
                    "entity" -> {
                        val entity = EntityArgument.getEntity(brigadierContext, name)
                        val entityType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()
                        graalContext.asValue(mapOf(
                            "name" to entity.name.string,
                            "type" to entityType,
                            "uuid" to entity.uuid.toString()
                        ))
                    }
                    else -> null
                }
            } catch (e: Exception) {
                // Argument not provided in context
                null
            }

            // Decide what to put in args map
            if (extracted != null) {
                // Argument was provided
                argsMap[name] = extracted
                ConfigManager.debug("[Commands] Extracted '$name' = $extracted")
            } else if (isOptional) {
                // Optional argument not provided
                if (hasDefault) {
                    // Use default value
                    argsMap[name] = defaultValue
                    ConfigManager.debug("[Commands] Using default for '$name' = $defaultValue")
                } else {
                    // No default (null sentinel) - don't include key (will be undefined in JS)
                    ConfigManager.debug("[Commands] Optional '$name' not provided, no default - key omitted")
                }
            } else {
                // Required argument not provided - this shouldn't happen if Brigadier is working correctly
                ConfigManager.debug("[Commands] ✗ Required argument '$name' not provided!")
            }
        }

        ConfigManager.debug("[Commands] Final args map keys: ${argsMap.keys}")
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
                // String permission - check if source entity is an operator
                // TODO: Integrate with proper permission system (e.g., LuckPerms)
                val entity = source.entity
                if (entity is ServerPlayer) {
                    // Check if player is an operator OR has permission level 2+ (single player)
                    val server = entity.server
                    val isOp = server?.playerList?.isOp(entity.gameProfile) ?: false
                    val hasPermLevel = source.hasPermission(2)
                    ConfigManager.debug("[Commands] Permission check for ${entity.name.string}: isOp=$isOp, permLevel2+=$hasPermLevel")
                    isOp || hasPermLevel
                } else {
                    // Non-player sources (console, command blocks) have full permissions
                    true
                }
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
                } else if (permission.isString) {
                    // String permission wrapped in Value - check if source entity is an operator
                    val entity = source.entity
                    if (entity is ServerPlayer) {
                        // Check if player is an operator OR has permission level 2+ (single player)
                        val server = entity.server
                        val isOp = server?.playerList?.isOp(entity.gameProfile) ?: false
                        val hasPermLevel = source.hasPermission(2)
                        ConfigManager.debug("[Commands] Permission check for ${entity.name.string}: isOp=$isOp, permLevel2+=$hasPermLevel")
                        isOp || hasPermLevel
                    } else {
                        // Non-player sources (console, command blocks) have full permissions
                        true
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
