package com.rhett.rhettjs.events

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.commands.SimpleCommandBuilder
import net.minecraft.commands.CommandSourceStack
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.util.concurrent.ConcurrentHashMap

/**
 * API for registering runtime event handlers.
 * Available in server/ and scripts/ contexts.
 *
 * Handles in-game events like item use, commands, etc.
 */
object ServerEventsAPI {

    /**
     * Handler entry with optional block filter.
     */
    private data class BlockHandler(
        val handler: Function,
        val blockFilter: String? = null  // null means all blocks
    )

    private val handlers = ConcurrentHashMap<String, MutableList<Function>>()
    private val blockHandlers = ConcurrentHashMap<String, MutableList<BlockHandler>>()
    private val commandRegistrations = ConcurrentHashMap<String, Function>()  // For basic command registration (1 handler per command)
    private val fullCommandRegistrations = ConcurrentHashMap<String, LiteralArgumentBuilder<CommandSourceStack>>()  // For full Brigadier commands

    /**
     * Register a handler for item use events.
     *
     * @param handler JavaScript function to call when item is used
     */
    fun itemUse(handler: Any) {
        if (handler !is Function) {
            throw IllegalArgumentException("Handler must be a function")
        }

        handlers.getOrPut("itemUse") { mutableListOf() }.add(handler)
        RhettJSCommon.LOGGER.info("[RhettJS] Registered itemUse handler")
    }

    /**
     * Register a basic command handler with simple string argument parsing.
     * This is for simple commands that take all arguments as a greedy string.
     *
     * Use ServerEvents.command() for full typed arguments with autocomplete.
     *
     * @param commandName The command name (without leading slash)
     * @param handler JavaScript function to call when command is executed
     */
    fun basicCommand(commandName: String, handler: Any) {
        if (handler !is Function) {
            throw IllegalArgumentException("Handler must be a function")
        }

        if (commandName.contains(" ")) {
            throw IllegalArgumentException("Command name cannot contain spaces: $commandName")
        }

        if (commandRegistrations.containsKey(commandName)) {
            RhettJSCommon.LOGGER.warn("[RhettJS] Overwriting existing command handler for: $commandName")
        }

        commandRegistrations[commandName] = handler
        RhettJSCommon.LOGGER.info("[RhettJS] Registered basic command: /$commandName")
    }

    /**
     * Register a full Brigadier command with typed arguments and autocomplete.
     * This provides complete control over command structure, argument types, and suggestions.
     *
     * Use ServerEvents.basicCommand() for simple commands with string parsing.
     *
     * Example:
     * ```javascript
     * ServerEvents.command('give', function(cmd) {
     *   cmd.addArgument('player', cmd.types.PLAYER);
     *   cmd.addArgument('item', cmd.types.STRING);
     *   cmd.setExecutor(function(ctx) {
     *     let player = cmd.types.PLAYER.get(ctx, 'player');
     *     let item = cmd.types.STRING.get(ctx, 'item');
     *     // ... give item to player
     *     return 1;
     *   });
     * });
     * ```
     *
     * @param commandName The command name (without leading slash)
     * @param builderFunction JavaScript function that receives a CommandBuilder and defines the command structure
     */
    fun command(commandName: String, builderFunction: Any) {
        if (builderFunction !is Function) {
            throw IllegalArgumentException("Builder must be a function")
        }

        if (commandName.contains(" ")) {
            throw IllegalArgumentException("Command name cannot contain spaces: $commandName")
        }

        if (fullCommandRegistrations.containsKey(commandName)) {
            RhettJSCommon.LOGGER.warn("[RhettJS] Overwriting existing full command for: $commandName")
        }

        try {
            // Create a JavaScript context to call the builder function
            Context.enter().use { cx ->
                cx.optimizationLevel = -1
                cx.languageVersion = Context.VERSION_ES6

                // Get the current scope (should be set by the caller)
                val scope = builderFunction.parentScope
                    ?: throw IllegalStateException("Builder function has no parent scope")

                // Create BrigadierCommandBuilder instance (full Brigadier API)
                val builder = com.rhett.rhettjs.commands.BrigadierCommandBuilder(commandName, scope)

                // Wrap the builder for JavaScript access
                val wrappedBuilder = Context.javaToJS(builder, scope)

                // Call the builder function with the wrapped BrigadierCommandBuilder
                builderFunction.call(cx, scope, scope, arrayOf(wrappedBuilder))

                // Process any microtasks
                cx.processMicrotasks()

                // Get the built command
                val builtCommand = builder.build()
                if (builtCommand == null) {
                    throw IllegalStateException("Command '$commandName' was not properly built. Did you forget to call builder.literal('$commandName')?")
                }

                RhettJSCommon.LOGGER.info("[RhettJS] Built command structure for: /$commandName")

                // Store it
                fullCommandRegistrations[commandName] = builtCommand
                RhettJSCommon.LOGGER.info("[RhettJS] Stored full command: /$commandName (total: ${fullCommandRegistrations.size})")
            }
        } catch (e: Exception) {
            RhettJSCommon.LOGGER.error("[RhettJS] Failed to register command: /$commandName", e)
            throw e
        }
    }

    /**
     * Get all handlers for a specific event type.
     *
     * @param eventType The event type
     * @return List of registered handlers
     */
    fun getHandlers(eventType: String): List<Any> {
        return handlers[eventType]?.toList() ?: emptyList()
    }

    /**
     * Get all registered basic command handlers (for command registration).
     *
     * @return Map of command names to handlers
     */
    fun getCommandHandlers(): Map<String, Function> {
        return commandRegistrations.toMap()
    }

    /**
     * Get all registered full Brigadier commands.
     *
     * @return Map of command names to LiteralArgumentBuilders
     */
    fun getFullCommandHandlers(): Map<String, LiteralArgumentBuilder<CommandSourceStack>> {
        return fullCommandRegistrations.toMap()
    }

    /**
     * Trigger item use event.
     *
     * @param scope The JavaScript scope to execute in
     */
    fun triggerItemUse(scope: Scriptable) {
        triggerEvent("itemUse", scope)
    }


    /**
     * Internal method to trigger generic events.
     */
    private fun triggerEvent(eventType: String, scope: Scriptable) {
        val eventHandlers = handlers[eventType] ?: return

        RhettJSCommon.LOGGER.info("[RhettJS] Triggering ${eventHandlers.size} handlers for: $eventType")

        eventHandlers.forEach { handler ->
            try {
                val cx = Context.getCurrentContext()

                // Create event object
                val event = cx.newObject(scope)
                ScriptableObject.putProperty(event, "type", eventType)

                // Call handler
                handler.call(cx, scope, scope, arrayOf(event))

                // Process microtasks (Promise .then() callbacks)
                cx.processMicrotasks()

            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[RhettJS] Error in $eventType handler", e)
                // Continue with other handlers
            }
        }
    }

    // ============================================================================
    // Block Events API
    // ============================================================================

    /**
     * Register a handler for block right-click events.
     *
     * Example:
     * ```javascript
     * ServerEvents.blockRightClicked('minecraft:stone', function(event) {
     *     console.log(event.player.name + ' right clicked stone at ' + event.position.x);
     * });
     * ```
     *
     * @param blockFilter Optional block ID filter (e.g., "minecraft:stone"). If not provided, triggers for all blocks.
     * @param handler JavaScript function to call when block is right-clicked
     */
    @JvmOverloads
    fun blockRightClicked(blockFilter: Any? = null, handler: Any) {
        registerBlockHandler("blockRightClicked", blockFilter, handler)
    }

    /**
     * Register a handler for block left-click events.
     *
     * @param blockFilter Optional block ID filter (e.g., "minecraft:stone"). If not provided, triggers for all blocks.
     * @param handler JavaScript function to call when block is left-clicked
     */
    @JvmOverloads
    fun blockLeftClicked(blockFilter: Any? = null, handler: Any) {
        registerBlockHandler("blockLeftClicked", blockFilter, handler)
    }

    /**
     * Register a handler for block placement events.
     *
     * @param blockFilter Optional block ID filter (e.g., "minecraft:stone"). If not provided, triggers for all blocks.
     * @param handler JavaScript function to call when block is placed
     */
    @JvmOverloads
    fun blockPlaced(blockFilter: Any? = null, handler: Any) {
        registerBlockHandler("blockPlaced", blockFilter, handler)
    }

    /**
     * Register a handler for block breaking events.
     *
     * @param blockFilter Optional block ID filter (e.g., "minecraft:stone"). If not provided, triggers for all blocks.
     * @param handler JavaScript function to call when block is broken
     */
    @JvmOverloads
    fun blockBroken(blockFilter: Any? = null, handler: Any) {
        registerBlockHandler("blockBroken", blockFilter, handler)
    }

    /**
     * Internal helper to register block event handlers with optional filtering.
     */
    private fun registerBlockHandler(eventType: String, blockFilter: Any?, handler: Any) {
        if (handler !is Function) {
            throw IllegalArgumentException("Handler must be a function")
        }

        // Handle both overload styles:
        // 1. blockRightClicked(handler) - no filter
        // 2. blockRightClicked('minecraft:stone', handler) - with filter
        val filter: String? = when {
            blockFilter == null -> null
            blockFilter is String -> blockFilter
            blockFilter is Function -> {
                // User passed function as first arg, meaning no filter
                // In this case, 'handler' is actually unused and blockFilter is the real handler
                blockHandlers.getOrPut(eventType) { mutableListOf() }
                    .add(BlockHandler(blockFilter, null))
                RhettJSCommon.LOGGER.info("[RhettJS] Registered $eventType handler (all blocks)")
                return
            }
            else -> throw IllegalArgumentException("Block filter must be a string or null")
        }

        blockHandlers.getOrPut(eventType) { mutableListOf() }
            .add(BlockHandler(handler as Function, filter))

        val filterDesc = filter?.let { " for $it" } ?: " (all blocks)"
        RhettJSCommon.LOGGER.info("[RhettJS] Registered $eventType handler$filterDesc")
    }

    /**
     * Trigger a block event with the given event data.
     * Calls all matching handlers based on block filters.
     *
     * @param eventType The event type (e.g., "blockRightClicked")
     * @param scope The JavaScript scope to execute in
     * @param eventData The block event data
     * @param playerObject The actual Minecraft player object (for messaging)
     */
    fun triggerBlockEvent(
        eventType: String,
        scope: Scriptable,
        eventData: BlockEventData,
        playerObject: net.minecraft.world.entity.player.Player? = null
    ): Boolean {
        val eventHandlers = blockHandlers[eventType] ?: return false

        // Filter handlers that match the block type
        val matchingHandlers = eventHandlers.filter { handler ->
            handler.blockFilter == null || handler.blockFilter == eventData.block.id
        }

        if (matchingHandlers.isEmpty()) return false

        RhettJSCommon.LOGGER.debug("[RhettJS] Triggering ${matchingHandlers.size} handlers for: $eventType (block: ${eventData.block.id})")

        var cancelled = false

        matchingHandlers.forEach { blockHandler ->
            try {
                val cx = Context.getCurrentContext()

                // Convert BlockEventData to JavaScript object with messaging capability
                val event = convertBlockEventToJS(cx, scope, eventData, playerObject)

                // Call handler
                blockHandler.handler.call(cx, scope, scope, arrayOf(event))

                // Check if event was cancelled
                if (event is org.mozilla.javascript.Scriptable) {
                    val cancelledProp = org.mozilla.javascript.ScriptableObject.getProperty(event, "cancelled")
                    if (cancelledProp is Boolean && cancelledProp) {
                        cancelled = true
                    }
                }

                // Process microtasks (Promise .then() callbacks)
                cx.processMicrotasks()

            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[RhettJS] Error in $eventType handler", e)
                // Continue with other handlers
            }
        }

        return cancelled
    }

    /**
     * Convert BlockEventData to a JavaScript object with messaging capabilities.
     * If playerObject is provided, adds sendMessage/sendSuccess/etc methods (like Caller API).
     */
    private fun convertBlockEventToJS(
        cx: Context,
        scope: Scriptable,
        eventData: BlockEventData,
        playerObject: net.minecraft.world.entity.player.Player?
    ): Scriptable {
        val event = cx.newObject(scope)

        // Add position data
        val position = cx.newObject(scope)
        ScriptableObject.putProperty(position, "x", eventData.position.x)
        ScriptableObject.putProperty(position, "y", eventData.position.y)
        ScriptableObject.putProperty(position, "z", eventData.position.z)
        ScriptableObject.putProperty(position, "dimension", eventData.position.dimension)
        ScriptableObject.putProperty(event, "position", position)

        // Add block data
        val block = cx.newObject(scope)
        ScriptableObject.putProperty(block, "id", eventData.block.id)
        ScriptableObject.putProperty(block, "properties", Context.javaToJS(eventData.block.properties, scope))
        ScriptableObject.putProperty(event, "block", block)

        // Add player data (if present)
        eventData.player?.let { playerData ->
            val playerDataObj = cx.newObject(scope)
            ScriptableObject.putProperty(playerDataObj, "name", playerData.name)
            ScriptableObject.putProperty(playerDataObj, "uuid", playerData.uuid)
            ScriptableObject.putProperty(playerDataObj, "isCreative", playerData.isCreative)
            ScriptableObject.putProperty(event, "playerData", playerDataObj)

            // Add the actual player object if available
            // This gives access to the full ServerPlayer API
            if (playerObject != null) {
                ScriptableObject.putProperty(event, "player", Context.javaToJS(playerObject, scope))
            }
        }

        // Add event-specific data
        when (eventData) {
            is BlockEventData.Click -> {
                ScriptableObject.putProperty(event, "isRightClick", eventData.isRightClick)
                eventData.item?.let { itemData ->
                    val item = cx.newObject(scope)
                    ScriptableObject.putProperty(item, "id", itemData.id)
                    ScriptableObject.putProperty(item, "count", itemData.count)
                    itemData.displayName?.let { ScriptableObject.putProperty(item, "displayName", it) }
                    itemData.nbt?.let { ScriptableObject.putProperty(item, "nbt", Context.javaToJS(it, scope)) }
                    ScriptableObject.putProperty(event, "item", item)
                }
                eventData.face?.let { ScriptableObject.putProperty(event, "face", it.name) }
            }
            is BlockEventData.Placed -> {
                val placedAgainst = cx.newObject(scope)
                ScriptableObject.putProperty(placedAgainst, "x", eventData.placedAgainst.x)
                ScriptableObject.putProperty(placedAgainst, "y", eventData.placedAgainst.y)
                ScriptableObject.putProperty(placedAgainst, "z", eventData.placedAgainst.z)
                ScriptableObject.putProperty(event, "placedAgainst", placedAgainst)
                eventData.face?.let { ScriptableObject.putProperty(event, "face", it.name) }
                eventData.item?.let { itemData ->
                    val item = cx.newObject(scope)
                    ScriptableObject.putProperty(item, "id", itemData.id)
                    ScriptableObject.putProperty(item, "count", itemData.count)
                    itemData.displayName?.let { ScriptableObject.putProperty(item, "displayName", it) }
                    itemData.nbt?.let { ScriptableObject.putProperty(item, "nbt", Context.javaToJS(it, scope)) }
                    ScriptableObject.putProperty(event, "item", item)
                }
            }
            is BlockEventData.Broken -> {
                ScriptableObject.putProperty(event, "experience", eventData.experience)
                val drops = cx.newArray(scope, eventData.drops.size)
                eventData.drops.forEachIndexed { index, itemData ->
                    val item = cx.newObject(scope)
                    ScriptableObject.putProperty(item, "id", itemData.id)
                    ScriptableObject.putProperty(item, "count", itemData.count)
                    drops.put(index, drops, item)
                }
                ScriptableObject.putProperty(event, "drops", drops)
            }
        }

        // Add messaging methods if player object is available
        // This makes the event compatible with MessageBuffer and provides Caller-like API
        playerObject?.let { player ->
            val server = player.server

            // sendMessage() - regular message
            val sendMessageFunc = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: Context,
                    scope: Scriptable,
                    thisObj: Scriptable?,
                    args: Array<Any?>
                ): Any {
                    if (args.isEmpty()) return Context.getUndefinedValue()
                    val message = args[0].toString()
                    server?.execute {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message))
                    }
                    return Context.getUndefinedValue()
                }
            }
            ScriptableObject.putProperty(event, "sendMessage", sendMessageFunc)

            // sendSuccess() - green message
            val sendSuccessFunc = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: Context,
                    scope: Scriptable,
                    thisObj: Scriptable?,
                    args: Array<Any?>
                ): Any {
                    if (args.isEmpty()) return Context.getUndefinedValue()
                    val message = args[0].toString()
                    server?.execute {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a$message"))
                    }
                    return Context.getUndefinedValue()
                }
            }
            ScriptableObject.putProperty(event, "sendSuccess", sendSuccessFunc)

            // sendError() - red message
            val sendErrorFunc = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: Context,
                    scope: Scriptable,
                    thisObj: Scriptable?,
                    args: Array<Any?>
                ): Any {
                    if (args.isEmpty()) return Context.getUndefinedValue()
                    val message = args[0].toString()
                    server?.execute {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c$message"))
                    }
                    return Context.getUndefinedValue()
                }
            }
            ScriptableObject.putProperty(event, "sendError", sendErrorFunc)

            // sendWarning() - yellow message
            val sendWarningFunc = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: Context,
                    scope: Scriptable,
                    thisObj: Scriptable?,
                    args: Array<Any?>
                ): Any {
                    if (args.isEmpty()) return Context.getUndefinedValue()
                    val message = args[0].toString()
                    server?.execute {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e$message"))
                    }
                    return Context.getUndefinedValue()
                }
            }
            ScriptableObject.putProperty(event, "sendWarning", sendWarningFunc)

            // sendInfo() - gray message
            val sendInfoFunc = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: Context,
                    scope: Scriptable,
                    thisObj: Scriptable?,
                    args: Array<Any?>
                ): Any {
                    if (args.isEmpty()) return Context.getUndefinedValue()
                    val message = args[0].toString()
                    server?.execute {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7$message"))
                    }
                    return Context.getUndefinedValue()
                }
            }
            ScriptableObject.putProperty(event, "sendInfo", sendInfoFunc)

            // sendRaw() - raw JSON component
            val sendRawFunc = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: Context,
                    scope: Scriptable,
                    thisObj: Scriptable?,
                    args: Array<Any?>
                ): Any {
                    if (args.isEmpty()) return Context.getUndefinedValue()
                    val json = args[0].toString()
                    server?.execute {
                        try {
                            val component = net.minecraft.network.chat.Component.Serializer.fromJson(
                                json,
                                player.level().registryAccess()
                            )
                            if (component != null) {
                                player.sendSystemMessage(component)
                            }
                        } catch (e: Exception) {
                            player.sendSystemMessage(
                                net.minecraft.network.chat.Component.literal("§c[RhettJS] Failed to parse JSON: ${e.message}")
                            )
                        }
                    }
                    return Context.getUndefinedValue()
                }
            }
            ScriptableObject.putProperty(event, "sendRaw", sendRawFunc)

            // isPlayer() - always returns true since we have a player object
            val isPlayerFunc = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: Context,
                    scope: Scriptable,
                    thisObj: Scriptable?,
                    args: Array<Any?>
                ): Any {
                    return true
                }
            }
            ScriptableObject.putProperty(event, "isPlayer", isPlayerFunc)
        }

        // Add cancellation support
        ScriptableObject.putProperty(event, "cancelled", false)

        val cancelFunc = object : org.mozilla.javascript.BaseFunction() {
            override fun call(
                cx: Context,
                scope: Scriptable,
                thisObj: Scriptable?,
                args: Array<Any?>
            ): Any {
                ScriptableObject.putProperty(event, "cancelled", true)
                return Context.getUndefinedValue()
            }
        }
        ScriptableObject.putProperty(event, "cancel", cancelFunc)

        return event
    }

    /**
     * Clear all registered handlers.
     * Used for testing and reload scenarios.
     */
    fun clear() {
        handlers.clear()
        blockHandlers.clear()
        commandRegistrations.clear()
        fullCommandRegistrations.clear()
    }
}
