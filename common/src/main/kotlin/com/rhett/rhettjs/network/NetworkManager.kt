package com.rhett.rhettjs.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Packet handler function type.
 * Receives packet data and context information.
 */
typealias PacketHandler = (data: Map<String, Any?>, context: PacketContext) -> Unit

/**
 * NetworkManager handles packet routing, serialization, and handler management.
 * Provides low-level UDP-style packet communication between client and server.
 *
 * Features:
 * - Channel-based routing
 * - Multiple handlers per channel
 * - Auto-namespacing
 * - JSON serialization
 * - Error handling (handler exceptions don't crash)
 */
class NetworkManager(
    private val defaultNamespace: String = "rhettjs"
) {
    private val logger = LoggerFactory.getLogger(NetworkManager::class.java)

    // Map of channel -> list of handlers
    private val handlers = mutableMapOf<String, MutableList<PacketHandler>>()

    // JSON configuration
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    // =====================================
    // Handler Registration
    // =====================================

    /**
     * Register a packet handler for a specific channel.
     * Multiple handlers can be registered to the same channel.
     *
     * @param channel Channel name (will be auto-namespaced if needed)
     * @param handler Handler function
     */
    fun registerHandler(channel: String, handler: PacketHandler) {
        val namespacedChannel = applyNamespace(channel, defaultNamespace)
        handlers.getOrPut(namespacedChannel) { mutableListOf() }.add(handler)
        logger.debug("Registered handler for channel: $namespacedChannel")
    }

    /**
     * Unregister a specific packet handler.
     *
     * @param channel Channel name
     * @param handler Handler function to remove
     */
    fun unregisterHandler(channel: String, handler: PacketHandler) {
        val namespacedChannel = applyNamespace(channel, defaultNamespace)
        handlers[namespacedChannel]?.remove(handler)

        // Remove channel entry if no handlers remain
        if (handlers[namespacedChannel]?.isEmpty() == true) {
            handlers.remove(namespacedChannel)
        }

        logger.debug("Unregistered handler for channel: $namespacedChannel")
    }

    /**
     * Clear all registered handlers.
     * Useful for testing and cleanup.
     */
    fun clearAllHandlers() {
        handlers.clear()
        logger.debug("Cleared all handlers")
    }

    /**
     * Clear all handlers for a specific channel.
     * Useful for off() without specific handler.
     *
     * @param channel Channel name
     */
    fun clearChannel(channel: String) {
        val namespacedChannel = applyNamespace(channel, defaultNamespace)
        handlers.remove(namespacedChannel)
        logger.debug("Cleared all handlers for channel: $namespacedChannel")
    }

    // =====================================
    // Packet Routing
    // =====================================

    /**
     * Route a packet to registered handlers.
     * Calls all handlers registered for the packet's channel.
     * Handler exceptions are caught and logged (don't crash other handlers).
     *
     * @param packet Packet data to route
     * @param context Packet context (sender info)
     */
    fun routePacket(packet: PacketData, context: PacketContext) {
        val channelHandlers = handlers[packet.channel] ?: emptyList()

        if (channelHandlers.isEmpty()) {
            logger.trace("No handlers registered for channel: ${packet.channel}")
            return
        }

        logger.debug("Routing packet on channel ${packet.channel} to ${channelHandlers.size} handler(s)")

        // Call each handler, catching exceptions to prevent one handler from breaking others
        for (handler in channelHandlers) {
            try {
                handler(packet.data, context)
            } catch (e: Exception) {
                logger.error("Handler threw exception on channel ${packet.channel}", e)
                // Continue to next handler
            }
        }
    }

    // =====================================
    // Namespacing
    // =====================================

    /**
     * Apply namespace to channel name if not already namespaced.
     * Channel is considered namespaced if it contains a colon.
     *
     * @param channel Raw channel name
     * @param namespace Namespace to prepend
     * @return Namespaced channel name
     * @throws IllegalArgumentException if channel contains invalid characters
     */
    fun applyNamespace(channel: String, namespace: String): String {
        // If namespace is empty, return channel as-is
        if (namespace.isEmpty()) {
            return channel
        }

        // If channel already has namespace (contains colon), return as-is
        if (channel.contains(':')) {
            // Validate the channel format
            require(PacketData.validateChannel(channel)) {
                "Invalid channel name: '$channel'"
            }
            return channel
        }

        // Validate simple channel name
        require(PacketData.validateChannel(channel)) {
            "Invalid channel name: '$channel'"
        }

        // Prepend namespace
        return "$namespace:$channel"
    }

    // =====================================
    // Serialization
    // =====================================

    /**
     * Serialize packet to JSON string.
     * Converts PacketData to JSON for network transport.
     *
     * @param packet Packet to serialize
     * @return JSON string
     */
    fun serializePacket(packet: PacketData): String {
        val rhettPacket = RhettPacket(
            channel = packet.channel,
            data = packet.data,
            timestamp = System.currentTimeMillis()
        )
        return json.encodeToString(rhettPacket)
    }

    /**
     * Deserialize JSON string to packet.
     * Converts JSON from network transport back to PacketData.
     *
     * @param jsonString JSON string
     * @return Deserialized packet
     * @throws Exception if JSON is malformed
     */
    fun deserializePacket(jsonString: String): PacketData {
        val rhettPacket = json.decodeFromString<RhettPacket>(jsonString)

        return PacketData(
            channel = rhettPacket.channel,
            data = rhettPacket.data
        )
    }

    // =====================================
    // Context Creation
    // =====================================

    /**
     * Create a packet context from sender information.
     * Converts Minecraft types to pure data structures for JavaScript.
     *
     * @param senderUuid Sender's UUID
     * @param senderName Sender's display name
     * @param position Sender's position (converted to Map for anti-corruption)
     * @param channel Channel name
     * @return Packet context
     */
    fun createContext(
        senderUuid: String,
        senderName: String,
        position: Any?,
        channel: String
    ): PacketContext {
        // Convert position to Map if it's a Position object
        val positionMap = when (position) {
            null -> null
            is Map<*, *> -> position
            else -> {
                // Attempt to extract x, y, z via reflection (for any Position-like object)
                try {
                    val posClass = position::class.java
                    val x = posClass.getDeclaredField("x").apply { isAccessible = true }.get(position)
                    val y = posClass.getDeclaredField("y").apply { isAccessible = true }.get(position)
                    val z = posClass.getDeclaredField("z").apply { isAccessible = true }.get(position)
                    mapOf("x" to x, "y" to y, "z" to z)
                } catch (e: Exception) {
                    logger.warn("Failed to convert position to map: ${e.message}")
                    null
                }
            }
        }

        return PacketContext(
            senderUuid = senderUuid,
            senderName = senderName,
            position = positionMap,
            timestamp = System.currentTimeMillis(),
            channel = channel
        )
    }

    // =====================================
    // Utility Methods
    // =====================================

    /**
     * Get list of all registered channels.
     *
     * @return List of channel names
     */
    fun getChannels(): List<String> {
        return handlers.keys.toList()
    }

    /**
     * Check if a channel has any registered handlers.
     *
     * @param channel Channel name
     * @return True if channel has handlers
     */
    fun hasChannel(channel: String): Boolean {
        val namespacedChannel = applyNamespace(channel, defaultNamespace)
        return handlers.containsKey(namespacedChannel)
    }

    /**
     * Get number of handlers registered for a channel.
     *
     * @param channel Channel name
     * @return Number of handlers
     */
    fun getHandlerCount(channel: String): Int {
        val namespacedChannel = applyNamespace(channel, defaultNamespace)
        return handlers[namespacedChannel]?.size ?: 0
    }
}
