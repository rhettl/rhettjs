package com.rhett.rhettjs.events

import com.rhett.rhettjs.RhettJSCommon
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

    private val handlers = ConcurrentHashMap<String, MutableList<Function>>()
    private val commandHandlers = ConcurrentHashMap<String, MutableList<Function>>()

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
     * Register a handler for a specific command.
     *
     * @param commandName The command name to handle
     * @param handler JavaScript function to call when command is executed
     */
    fun command(commandName: String, handler: Any) {
        if (handler !is Function) {
            throw IllegalArgumentException("Handler must be a function")
        }

        commandHandlers.getOrPut(commandName) { mutableListOf() }.add(handler)
        RhettJSCommon.LOGGER.info("[RhettJS] Registered command handler for: $commandName")
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
     * Get all handlers for a specific command.
     *
     * @param commandName The command name
     * @return List of registered handlers
     */
    fun getCommandHandlers(commandName: String): List<Any> {
        return commandHandlers[commandName]?.toList() ?: emptyList()
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
     * Trigger command event.
     *
     * @param commandName The command that was executed
     * @param scope The JavaScript scope to execute in
     */
    fun triggerCommand(commandName: String, scope: Scriptable) {
        val cmdHandlers = commandHandlers[commandName] ?: return

        RhettJSCommon.LOGGER.info("[RhettJS] Triggering ${cmdHandlers.size} handlers for command: $commandName")

        cmdHandlers.forEach { handler ->
            try {
                val cx = Context.getCurrentContext()

                // Create event object
                val event = cx.newObject(scope)
                ScriptableObject.putProperty(event, "command", commandName)

                // Call handler
                handler.call(cx, scope, scope, arrayOf(event))

                // Process microtasks (Promise .then() callbacks)
                cx.processMicrotasks()

            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[RhettJS] Error in command handler for $commandName", e)
                // Continue with other handlers
            }
        }
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

    /**
     * Clear all registered handlers.
     * Used for testing and reload scenarios.
     */
    fun clear() {
        handlers.clear()
        commandHandlers.clear()
    }
}
