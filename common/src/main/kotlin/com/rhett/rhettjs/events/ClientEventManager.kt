package com.rhett.rhettjs.events

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.adapter.PlayerAdapter
import com.rhett.rhettjs.config.ConfigManager
import net.minecraft.client.player.LocalPlayer
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager for Client API event system.
 *
 * This is the anti-corruption layer between Minecraft client events and JavaScript.
 * It stores event handlers registered from JavaScript and provides methods
 * to trigger those handlers from Minecraft platform code (Fabric/NeoForge).
 *
 * Design principles:
 * - No Minecraft types exposed to JavaScript
 * - All objects wrapped using adapters (PlayerAdapter for client player)
 * - Thread-safe handler storage
 * - Async handler support
 */
object ClientEventManager {

    /**
     * Available event types as constants.
     * Exposed to JavaScript via Client.eventTypes.
     */
    object EventTypes {
        const val KEY_PRESS = "keyPress"
        const val TICK = "tick"
        const val CHAT_SEND = "chatSend"
    }

    /**
     * Get all event type names as a map for JavaScript consumption.
     */
    fun getEventTypes(): Map<String, String> = mapOf(
        "KEY_PRESS" to EventTypes.KEY_PRESS,
        "TICK" to EventTypes.TICK,
        "CHAT_SEND" to EventTypes.CHAT_SEND
    )

    // Event handler storage
    private val eventHandlers = ConcurrentHashMap<String, MutableList<Value>>()
    private val oneTimeHandlers = ConcurrentHashMap<String, MutableList<Value>>()

    // Reference to client player (LocalPlayer on client JVM)
    @Volatile
    private var clientPlayer: LocalPlayer? = null

    // Reference to GraalVM context for event execution
    @Volatile
    private var graalContext: Context? = null

    // Mock player for testing (when no real client player available)
    @Volatile
    private var mockPlayer: Value? = null

    /**
     * Set the client player reference.
     * Called during client initialization.
     */
    fun setPlayer(player: LocalPlayer) {
        clientPlayer = player
        ConfigManager.debug("[ClientEventManager] Client player reference set: ${player.name.string}")
    }

    /**
     * Set the GraalVM context reference.
     * Called when GraalEngine initializes the context.
     */
    fun setContext(context: Context) {
        graalContext = context
        ConfigManager.debug("[ClientEventManager] GraalVM context reference set")
    }

    /**
     * Set a mock player for testing.
     * Used in unit tests where no real Minecraft client exists.
     */
    fun setMockPlayer(player: Value) {
        mockPlayer = player
        ConfigManager.debug("[ClientEventManager] Mock player set for testing")
    }

    /**
     * Get the current client player as a JavaScript object.
     * Returns mock player for testing if no real player is available.
     */
    fun getClientPlayer(): Value {
        val context = graalContext ?: throw IllegalStateException("GraalVM context not set")

        // If mock player is set (testing), return it
        mockPlayer?.let { return it }

        // If real client player is available, wrap it
        val player = clientPlayer
        if (player != null) {
            // For CLIENT context, we need to adapt the LocalPlayer (not ServerPlayer)
            // We'll create a similar adapter structure to PlayerAdapter but for LocalPlayer
            return createClientPlayerObject(player, context)
        }

        // No player available - return mock for testing
        return createMockPlayer(context)
    }

    /**
     * Create a JavaScript player object from LocalPlayer.
     * Similar to PlayerAdapter but for client-side LocalPlayer.
     */
    private fun createClientPlayerObject(player: LocalPlayer, context: Context): Value {
        val playerProxy = ProxyObject.fromMap(mapOf(
            // Properties (read live from player)
            "name" to player.name.string,
            "uuid" to player.stringUUID,
            "health" to player.health.toDouble(),
            "maxHealth" to player.maxHealth.toDouble(),
            "foodLevel" to player.foodData.foodLevel,
            "saturation" to player.foodData.saturationLevel.toDouble(),
            "gameMode" to gameTypeToString(player),
            "position" to createPositionObject(player),
            "rotation" to createRotationObject(player),

            // Methods - all are client-side only
            "sendMessage" to ProxyExecutable { args ->
                if (args.isEmpty()) return@ProxyExecutable null
                val message = args[0].asString()
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), false)
                null
            },

            "sendSuccess" to ProxyExecutable { args ->
                if (args.isEmpty()) return@ProxyExecutable null
                val message = args[0].asString()
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a$message"), false)
                null
            },

            "sendError" to ProxyExecutable { args ->
                if (args.isEmpty()) return@ProxyExecutable null
                val message = args[0].asString()
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c$message"), false)
                null
            },

            "sendWarning" to ProxyExecutable { args ->
                if (args.isEmpty()) return@ProxyExecutable null
                val message = args[0].asString()
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e$message"), false)
                null
            },

            "sendInfo" to ProxyExecutable { args ->
                if (args.isEmpty()) return@ProxyExecutable null
                val message = args[0].asString()
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§7$message"), false)
                null
            },

            "runCommand" to ProxyExecutable { args ->
                if (args.isEmpty()) return@ProxyExecutable null
                val command = args[0].asString()
                // Client-side: send command packet to server
                player.connection.sendCommand(command)
                null
            }
        ))

        return context.asValue(playerProxy)
    }

    /**
     * Create mock player object for testing (when no Minecraft client available).
     */
    private fun createMockPlayer(context: Context): Value {
        val mockProxy = ProxyObject.fromMap(mapOf(
            "name" to "TestPlayer",
            "uuid" to "00000000-0000-0000-0000-000000000000",
            "health" to 20.0,
            "maxHealth" to 20.0,
            "foodLevel" to 20,
            "saturation" to 5.0,
            "gameMode" to "creative",
            "position" to ProxyObject.fromMap(mapOf(
                "x" to 0.0,
                "y" to 64.0,
                "z" to 0.0,
                "dimension" to "minecraft:overworld"
            )),
            "rotation" to ProxyObject.fromMap(mapOf(
                "yaw" to 0.0f,
                "pitch" to 0.0f
            )),

            // Mock methods (no-op)
            "sendMessage" to ProxyExecutable { args ->
                ConfigManager.debug("[ClientEventManager] Mock player.sendMessage: ${args[0].asString()}")
                null
            },
            "sendSuccess" to ProxyExecutable { args ->
                ConfigManager.debug("[ClientEventManager] Mock player.sendSuccess: ${args[0].asString()}")
                null
            },
            "sendError" to ProxyExecutable { args ->
                ConfigManager.debug("[ClientEventManager] Mock player.sendError: ${args[0].asString()}")
                null
            },
            "sendWarning" to ProxyExecutable { args ->
                ConfigManager.debug("[ClientEventManager] Mock player.sendWarning: ${args[0].asString()}")
                null
            },
            "sendInfo" to ProxyExecutable { args ->
                ConfigManager.debug("[ClientEventManager] Mock player.sendInfo: ${args[0].asString()}")
                null
            },
            "runCommand" to ProxyExecutable { args ->
                ConfigManager.debug("[ClientEventManager] Mock player.runCommand: ${args[0].asString()}")
                null
            }
        ))

        return context.asValue(mockProxy)
    }

    /**
     * Helper: Create position object from LocalPlayer.
     */
    private fun createPositionObject(player: LocalPlayer): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            "x" to player.x,
            "y" to player.y,
            "z" to player.z,
            "dimension" to player.level().dimension().location().toString()
        ))
    }

    /**
     * Helper: Create rotation object from LocalPlayer.
     */
    private fun createRotationObject(player: LocalPlayer): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            "yaw" to player.yRot.toDouble(),
            "pitch" to player.xRot.toDouble()
        ))
    }

    /**
     * Helper: Get game mode string from LocalPlayer.
     */
    private fun gameTypeToString(player: LocalPlayer): String {
        // LocalPlayer doesn't have direct gameMode access, use abilities
        return when {
            player.abilities.instabuild && player.abilities.flying -> "creative"
            player.abilities.invulnerable -> "spectator"
            !player.abilities.mayBuild -> "adventure"
            else -> "survival"
        }
    }

    /**
     * Register an event handler from JavaScript.
     *
     * @param event The event name (e.g., "keyPress")
     * @param handler The JavaScript function to call
     */
    fun on(event: String, handler: Value) {
        if (!handler.canExecute()) {
            throw IllegalArgumentException("Handler must be a function")
        }

        eventHandlers.getOrPut(event) { mutableListOf() }.add(handler)
        ConfigManager.debug("[ClientEventManager] Registered handler for event: $event")
    }

    /**
     * Register a one-time event handler from JavaScript.
     * The handler will be removed after first execution.
     *
     * @param event The event name
     * @param handler The JavaScript function to call
     */
    fun once(event: String, handler: Value) {
        if (!handler.canExecute()) {
            throw IllegalArgumentException("Handler must be a function")
        }

        oneTimeHandlers.getOrPut(event) { mutableListOf() }.add(handler)
        ConfigManager.debug("[ClientEventManager] Registered one-time handler for event: $event")
    }

    /**
     * Unregister an event handler from JavaScript.
     *
     * @param event The event name
     * @param handler The JavaScript function to remove
     */
    fun off(event: String, handler: Value) {
        eventHandlers[event]?.remove(handler)
        oneTimeHandlers[event]?.remove(handler)
        ConfigManager.debug("[ClientEventManager] Unregistered handler for event: $event")
    }

    /**
     * Trigger a key press event.
     * Called from platform code when a key is pressed.
     *
     * @param key Key name
     * @param scanCode Platform-specific scancode
     * @param action 0=release, 1=press, 2=repeat
     * @param modifiers Modifier bitmask
     */
    fun triggerKeyPress(key: String, scanCode: Int, action: Int, modifiers: Int) {
        val context = graalContext ?: run {
            RhettJSCommon.LOGGER.warn("[ClientEventManager] Cannot trigger keyPress: GraalVM context not available")
            return
        }

        val eventData = ProxyObject.fromMap(mapOf(
            "key" to key,
            "scanCode" to scanCode,
            "action" to action,
            "modifiers" to modifiers
        ))

        triggerEvent(EventTypes.KEY_PRESS, context.asValue(eventData))
    }

    /**
     * Trigger a client tick event.
     * Called from platform code on each client tick.
     *
     * @param tick Current tick count
     */
    fun triggerTick(tick: Long) {
        val context = graalContext ?: return

        val eventData = ProxyObject.fromMap(mapOf(
            "tick" to tick
        ))

        triggerEvent(EventTypes.TICK, context.asValue(eventData))
    }

    /**
     * Trigger a chat send event.
     * Called from platform code when player sends a chat message.
     *
     * @param message The message being sent
     * @param cancelCallback Callback to cancel the message
     */
    fun triggerChatSend(message: String, cancelCallback: () -> Unit) {
        val context = graalContext ?: run {
            RhettJSCommon.LOGGER.warn("[ClientEventManager] Cannot trigger chatSend: GraalVM context not available")
            return
        }

        var cancelled = false
        val eventData = ProxyObject.fromMap(mapOf(
            "message" to message,
            "cancel" to ProxyExecutable {
                cancelled = true
                null
            }
        ))

        triggerEvent(EventTypes.CHAT_SEND, context.asValue(eventData))

        if (cancelled) {
            cancelCallback()
        }
    }

    /**
     * Trigger an event by calling all registered handlers.
     * Handles both regular and one-time handlers.
     * Supports async handlers (Promise-returning functions).
     */
    private fun triggerEvent(eventName: String, eventData: Value) {
        // Get all handlers for this event
        val regularHandlers = eventHandlers[eventName]?.toList() ?: emptyList()
        val onceHandlers = oneTimeHandlers[eventName]?.toList() ?: emptyList()

        // Remove one-time handlers
        oneTimeHandlers[eventName]?.clear()

        // Execute all handlers
        val allHandlers = regularHandlers + onceHandlers
        for (handler in allHandlers) {
            try {
                val result = handler.execute(eventData)

                // If handler returns a Promise, handle it
                if (result != null && result.hasMembers() && result.hasMember("then")) {
                    // Async handler - attach error handler
                    val catchMethod = result.getMember("catch")
                    if (catchMethod != null && catchMethod.canExecute()) {
                        catchMethod.execute(ProxyExecutable { args ->
                            val error = if (args.isNotEmpty()) args[0] else null
                            RhettJSCommon.LOGGER.error("[ClientEventManager] Async event handler error for $eventName: $error")
                            null
                        })
                    }
                }
            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[ClientEventManager] Event handler error for $eventName", e)
            }
        }
    }

    /**
     * Reset all event handlers.
     * Called when GraalVM context is reset (e.g., F3+T reload).
     */
    fun reset() {
        eventHandlers.clear()
        oneTimeHandlers.clear()
        mockPlayer = null
        ConfigManager.debug("[ClientEventManager] Reset complete")
    }
}
