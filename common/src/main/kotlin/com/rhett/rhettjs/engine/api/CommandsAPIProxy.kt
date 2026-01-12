package com.rhett.rhettjs.engine.api

import com.rhett.rhettjs.commands.CustomCommandRegistry
import com.rhett.rhettjs.config.ConfigManager
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject

/**
 * Commands API proxy for JavaScript scripts.
 * Provides fluent builder API for command registration with Brigadier integration.
 *
 * Relocated from: GraalEngine.kt (lines 1622-1955)
 * Date: 2026-01-12
 */
object CommandsAPIProxy {

    /**
     * Create Commands API proxy for JavaScript.
     * Provides fluent builder API for command registration with Brigadier integration.
     */
    fun create(commandRegistry: CustomCommandRegistry, getOrCreateContext: () -> Context): ProxyObject {
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
                    commandRegistry.storeCommand(commandName, commandData)

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

                    // Store provider function keyed by argument name
                    suggestions[argName] = provider
                    commandRegistry.storeCommand(commandName, commandData)

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
                    commandRegistry.storeCommand(commandName, commandData)

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
                    commandRegistry.storeCommand(name, commandData)
                    createCommandBuilder(name)
                },

                "permission" to ProxyExecutable { args ->
                    if (args.isEmpty()) {
                        throw IllegalArgumentException("permission() requires a permission string or function")
                    }
                    commandData["permission"] = args[0]
                    commandRegistry.storeCommand(name, commandData)
                    createCommandBuilder(name)
                },

                "argument" to ProxyExecutable { args ->
                    if (args.size < 2) {
                        throw IllegalArgumentException("argument() requires name and type")
                    }
                    val argName = args[0].asString()
                    val argType = args[1].asString()

                    val isOptional = args.size >= 3
                    val hasDefault = isOptional && !args[2].isNull

                    val defaultValue = if (hasDefault) {
                        when (argType) {
                            "string" -> args[2].asString()
                            "int" -> args[2].asInt()
                            "float" -> args[2].asDouble()
                            else -> args[2]
                        }
                    } else {
                        null
                    }

                    val validTypes = listOf("string", "int", "float", "player", "item", "block", "entity", "xyz-position", "xz-position")
                    if (argType !in validTypes) {
                        throw IllegalArgumentException("Invalid argument type: $argType. Valid types: ${validTypes.joinToString(", ")}")
                    }

                    @Suppress("UNCHECKED_CAST")
                    val arguments = commandData["arguments"] as MutableList<MutableMap<String, Any?>>

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
                    commandRegistry.storeCommand(name, commandData)

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

                    @Suppress("UNCHECKED_CAST")
                    val suggestions = commandData.getOrPut("suggestions") {
                        mutableMapOf<String, Value>()
                    } as MutableMap<String, Value>

                    suggestions[argName] = provider
                    commandRegistry.storeCommand(name, commandData)

                    ConfigManager.debug("[Commands] Added suggestions for command argument: $name.$argName")

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
                    commandRegistry.storeCommand(name, commandData)

                    val currentContext = getOrCreateContext()
                    if (commandRegistry.context == null) {
                        ConfigManager.debug("[Commands] Updating registry context reference after reset")
                        val dispatcher = commandRegistry.dispatcher
                        val buildContext = commandRegistry.commandBuildContext
                        if (dispatcher != null && buildContext != null) {
                            commandRegistry.storeDispatcher(dispatcher, currentContext, buildContext)
                        }
                    }

                    ConfigManager.debug("Registered command: $name with ${(commandData["arguments"] as List<*>).size} arguments")

                    createCommandBuilder(name)
                },

                "subcommand" to ProxyExecutable { args ->
                    if (args.isEmpty()) {
                        throw IllegalArgumentException("subcommand() requires a subcommand name")
                    }
                    val subcommandName = args[0].asString()
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
                val emptyData = mutableMapOf<String, Any?>()
                commandRegistry.storeCommand(name, emptyData)
                ConfigManager.debug("Unregistered command: $name")
                null
            }
        ))
    }
}
