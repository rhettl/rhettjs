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
 * // Player caller (isPlayer: true)
 * {
 *   name: string,              // Player name
 *   isPlayer: true,
 *   position: {
 *     x: number,
 *     y: number,
 *     z: number,
 *     dimension: string        // Access via caller.position.dimension
 *   },
 *   ...PlayerAdapter properties,
 *   sendMessage(msg: string): void
 * }
 *
 * // Server caller (isPlayer: false)
 * {
 *   name: "Server",
 *   isPlayer: false,
 *   dimension: string,         // Access via caller.dimension
 *   sendMessage(msg: string): void
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
        // Get the dimension the command was executed in
        val dimension = try {
            source.level.dimension().location().toString()
        } catch (e: Exception) {
            "minecraft:overworld" // Fallback to overworld if level not available
        }

        return ProxyObject.fromMap(mapOf(
            "name" to "Server",
            "isPlayer" to false,
            "dimension" to dimension,

            // Send message methods
            "sendMessage" to ProxyExecutable { args ->
                if (args.isEmpty()) return@ProxyExecutable null
                val message = args[0].asString()
                source.sendSuccess({ Component.literal(message) }, false)
                null
            },

            "sendSuccess" to ProxyExecutable { args ->
                if (args.isEmpty()) return@ProxyExecutable null
                val message = args[0].asString()
                source.sendSuccess({ Component.literal("§a$message") }, false)
                null
            },

            "sendError" to ProxyExecutable { args ->
                if (args.isEmpty()) return@ProxyExecutable null
                val message = args[0].asString()
                source.sendFailure(Component.literal("§c$message"))
                null
            },

            "sendWarning" to ProxyExecutable { args ->
                if (args.isEmpty()) return@ProxyExecutable null
                val message = args[0].asString()
                source.sendSuccess({ Component.literal("§e$message") }, false)
                null
            },

            "sendInfo" to ProxyExecutable { args ->
                if (args.isEmpty()) return@ProxyExecutable null
                val message = args[0].asString()
                source.sendSuccess({ Component.literal("§7$message") }, false)
                null
            },

            "sendRaw" to ProxyExecutable { args ->
                if (args.isEmpty()) return@ProxyExecutable null
                val json = args[0].asString()
                try {
                    val component = Component.Serializer.fromJson(json, source.registryAccess())
                    if (component != null) {
                        source.sendSuccess({ component }, false)
                    }
                } catch (e: Exception) {
                    source.sendFailure(Component.literal("§c[RhettJS] Failed to parse JSON: ${e.message}"))
                }
                null
            }

            // NOTE: Removed "source" escape hatch - causes stack overflow due to circular references
        ))
    }
}
