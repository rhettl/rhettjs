package com.rhett.rhettjs.adapter

import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject

/**
 * Adapter for converting CommandSourceStack to pure JavaScript objects.
 *
 * Handles both player and server (console) callers.
 *
 * For player callers, delegates to PlayerAdapter.
 * For server callers, creates a minimal object with sendMessage support.
 *
 * Caller object structure:
 * ```javascript
 * {
 *   name: string,              // Player name or "Server"
 *   isPlayer: boolean,
 *
 *   // If isPlayer is true, includes all Player properties
 *   ...PlayerAdapter properties,
 *
 *   // Methods
 *   sendMessage(msg: string): void,
 *
 *   // Escape hatch
 *   source: CommandSourceStack
 * }
 * ```
 */
object CallerAdapter {

    /**
     * Convert a CommandSourceStack to a pure JavaScript object.
     *
     * If the source is a player, returns a full Player object.
     * If the source is the server/console, returns a minimal server object.
     */
    fun toJS(source: CommandSourceStack, context: Context): Value {
        // Check if caller is a player
        val entity = source.entity
        if (entity is ServerPlayer) {
            // Return full player wrapper
            return PlayerAdapter.toJS(entity, context)
        }

        // Server/console caller - return minimal object
        return context.asValue(createServerCaller(source, context))
    }

    /**
     * Create a server caller object (non-player).
     */
    private fun createServerCaller(source: CommandSourceStack, context: Context): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            "name" to "Server",
            "isPlayer" to false,

            // Send message method
            "sendMessage" to ProxyExecutable { args ->
                if (args.isEmpty()) return@ProxyExecutable null
                val message = args[0].asString()
                source.sendSuccess({ Component.literal(message) }, false)
                null
            }

            // NOTE: Removed "source" escape hatch - causes stack overflow due to circular references
        ))
    }
}
