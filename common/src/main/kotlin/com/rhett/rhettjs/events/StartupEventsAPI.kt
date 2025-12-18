package com.rhett.rhettjs.events

import com.rhett.rhettjs.RhettJSCommon
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.util.concurrent.ConcurrentHashMap

/**
 * API for registering handlers during mod initialization.
 * Available in startup/ scripts only.
 *
 * Allows registration of items, blocks, and other game objects.
 */
object StartupEventsAPI {

    private val handlers = ConcurrentHashMap<String, MutableList<Function>>()

    private val supportedTypes = setOf("item", "block")

    /**
     * Register a handler for a specific registry type.
     *
     * @param type The registry type (e.g., "item", "block")
     * @param handler JavaScript function to call
     */
    fun registry(type: String, handler: Any) {
        if (type !in supportedTypes) {
            throw IllegalArgumentException("Unsupported registry type: $type. Supported: ${supportedTypes.joinToString()}")
        }

        if (handler !is Function) {
            throw IllegalArgumentException("Handler must be a function")
        }

        handlers.getOrPut(type) { mutableListOf() }.add(handler)
        RhettJSCommon.LOGGER.info("[RhettJS] Registered startup handler for: $type")
    }

    /**
     * Get all handlers registered for a specific type.
     *
     * @param type The registry type
     * @return List of registered handlers
     */
    fun getHandlers(type: String): List<Any> {
        return handlers[type]?.toList() ?: emptyList()
    }

    /**
     * Execute all registered handlers for a specific registry type.
     *
     * @param type The registry type
     * @param scope The JavaScript scope to execute in
     */
    fun executeRegistrations(type: String, scope: Scriptable) {
        val typeHandlers = handlers[type] ?: return

        RhettJSCommon.LOGGER.info("[RhettJS] Executing ${typeHandlers.size} startup handlers for: $type")

        typeHandlers.forEach { handler ->
            try {
                val cx = Context.getCurrentContext()

                // Create event object
                val event = cx.newObject(scope)
                ScriptableObject.putProperty(event, "type", type)

                // Call handler with event
                handler.call(cx, scope, scope, arrayOf(event))

            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[RhettJS] Error in startup handler for $type", e)
                // Continue with other handlers
            }
        }
    }

    /**
     * Get list of supported registry types.
     *
     * @return List of supported types
     */
    fun getSupportedTypes(): List<String> {
        return supportedTypes.toList()
    }

    /**
     * Clear all registered handlers.
     * Used for testing and reload scenarios.
     */
    fun clear() {
        handlers.clear()
    }
}
