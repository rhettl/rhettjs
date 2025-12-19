package com.rhett.rhettjs.api

import com.rhett.rhettjs.threading.ThreadSafeAPI
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component

/**
 * Caller API for sending messages to whoever invoked the script.
 *
 * Available in utility scripts run via /rjs run.
 * The caller can be a player, console, command block, or any entity.
 * Provides chat/console message functionality.
 *
 * Thread-safe: Can be called from worker threads (schedules message to main thread).
 */
class CallerAPI(private val source: CommandSourceStack) : ThreadSafeAPI {

    /**
     * Send a message to the caller.
     * Appears in chat for players, console for server.
     *
     * @param message The message to send
     */
    fun sendMessage(message: String) {
        // Send on main thread to be safe
        source.server.execute {
            source.sendSuccess({ Component.literal(message) }, false)
        }
    }

    /**
     * Send a success message (green color).
     *
     * @param message The message to send
     */
    fun sendSuccess(message: String) {
        source.server.execute {
            source.sendSuccess({ Component.literal("§a$message") }, false)
        }
    }

    /**
     * Send an error message (red color).
     *
     * @param message The message to send
     */
    fun sendError(message: String) {
        source.server.execute {
            source.sendFailure(Component.literal("§c$message"))
        }
    }

    /**
     * Send a warning message (yellow color).
     *
     * @param message The message to send
     */
    fun sendWarning(message: String) {
        source.server.execute {
            source.sendSuccess({ Component.literal("§e$message") }, false)
        }
    }

    /**
     * Send an info message (gray color).
     *
     * @param message The message to send
     */
    fun sendInfo(message: String) {
        source.server.execute {
            source.sendSuccess({ Component.literal("§7$message") }, false)
        }
    }

    /**
     * Send a raw tellraw JSON component.
     *
     * @param json The JSON string representing a text component
     */
    fun sendRaw(json: String) {
        source.server.execute {
            try {
                // In 1.21.1, fromJson requires RegistryAccess for components with registry data
                val component = Component.Serializer.fromJson(json, source.registryAccess())
                if (component != null) {
                    source.sendSuccess({ component }, false)
                } else {
                    source.sendFailure(Component.literal("§c[RhettJS] Invalid JSON component"))
                }
            } catch (e: Exception) {
                source.sendFailure(Component.literal("§c[RhettJS] Failed to parse JSON: ${e.message}"))
            }
        }
    }


    /**
     * Get the caller's name.
     *
     * @return The caller's name (player name or "Server")
     */
    fun getName(): String {
        return source.displayName.string
    }

    /**
     * Check if the caller is a player (vs console/command block).
     *
     * @return true if caller is a player
     */
    fun isPlayer(): Boolean {
        return source.entity != null
    }
}
