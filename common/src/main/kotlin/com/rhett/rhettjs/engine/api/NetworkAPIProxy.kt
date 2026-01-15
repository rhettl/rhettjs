package com.rhett.rhettjs.engine.api

import com.rhett.rhettjs.network.NetworkManager
import com.rhett.rhettjs.network.PacketContext
import com.rhett.rhettjs.network.PacketData
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject

/**
 * Network API proxy for JavaScript scripts.
 * Provides low-level packet communication (UDP-style).
 *
 * This is the JavaScript-facing API for the Network layer.
 * Handles packet registration, routing, and serialization.
 */
object NetworkAPIProxy {

    private val networkManager = NetworkManager()

    /**
     * Create the Network API proxy for JavaScript.
     * Exposes channel-based packet communication.
     *
     * @param context GraalVM context
     * @return ProxyObject for JavaScript
     */
    fun create(context: Context): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            // =====================================
            // Handler Registration
            // =====================================

            "on" to ProxyExecutable { args ->
                if (args.size < 2) {
                    throw IllegalArgumentException("on() requires channel and handler")
                }
                val channel = args[0].asString()
                val handler = args[1]

                registerHandler(channel, handler)
                null
            },

            "off" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("off() requires at least a channel name")
                }
                val channel = args[0].asString()

                if (args.size == 1) {
                    // Clear all handlers for channel
                    clearChannelHandlers(channel)
                } else {
                    // Remove specific handler
                    val handler = args[1]
                    unregisterHandler(channel, handler)
                }
                null
            },

            // =====================================
            // Packet Sending (CLIENT)
            // =====================================

            "sendToServer" to ProxyExecutable { args ->
                if (args.size < 2) {
                    throw IllegalArgumentException("sendToServer() requires channel and data")
                }
                val channel = args[0].asString()
                val data = args[1]

                sendToServer(channel, data)
                null
            },

            // =====================================
            // Packet Sending (SERVER)
            // =====================================

            "sendToClient" to ProxyExecutable { args ->
                if (args.size < 3) {
                    throw IllegalArgumentException("sendToClient() requires playerUuid, channel, and data")
                }
                val playerUuid = args[0].asString()
                val channel = args[1].asString()
                val data = args[2]

                sendToClient(playerUuid, channel, data)
                null
            },

            "broadcast" to ProxyExecutable { args ->
                if (args.size < 2) {
                    throw IllegalArgumentException("broadcast() requires channel and data")
                }
                val channel = args[0].asString()
                val data = args[1]

                broadcast(channel, data)
                null
            },

            "broadcastInRange" to ProxyExecutable { args ->
                if (args.size < 4) {
                    throw IllegalArgumentException("broadcastInRange() requires position, range, channel, and data")
                }
                val position = args[0]
                val range = args[1].asDouble()
                val channel = args[2].asString()
                val data = args[3]

                broadcastInRange(position, range, channel, data)
                null
            },

            // =====================================
            // Utility Methods
            // =====================================

            "getChannels" to ProxyExecutable { _ ->
                val channels = networkManager.getChannels()
                context.asValue(channels)
            },

            "hasChannel" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("hasChannel() requires channel name")
                }
                val channel = args[0].asString()
                networkManager.hasChannel(channel)
            },

            "clearAll" to ProxyExecutable { _ ->
                networkManager.clearAllHandlers()
                null
            }
        ))
    }

    // =====================================
    // Internal Handler Management
    // =====================================

    // Map to track JS handlers (needed for off() to work)
    private val jsHandlers = mutableMapOf<String, MutableList<Pair<Value, (Map<String, Any?>, PacketContext) -> Unit>>>()

    /**
     * Register a JavaScript packet handler.
     * Wraps the JS function in a Kotlin handler.
     */
    private fun registerHandler(channel: String, jsHandler: Value) {
        // Create Kotlin handler that calls JS function
        val kotlinHandler: (Map<String, Any?>, PacketContext) -> Unit = { data, context ->
            try {
                // Convert context to JS-friendly object
                val contextObj = ProxyObject.fromMap(mapOf(
                    "senderUuid" to context.senderUuid,
                    "senderName" to context.senderName,
                    "position" to context.position,
                    "timestamp" to context.timestamp,
                    "channel" to context.channel
                ))

                // Call JS handler
                jsHandler.execute(data, contextObj)
            } catch (e: Exception) {
                throw RuntimeException("JavaScript handler threw exception on channel $channel", e)
            }
        }

        // Track JS handler for later removal
        jsHandlers.getOrPut(channel) { mutableListOf() }.add(jsHandler to kotlinHandler)

        // Register with network manager
        networkManager.registerHandler(channel, kotlinHandler)
    }

    /**
     * Unregister a specific JavaScript handler.
     */
    private fun unregisterHandler(channel: String, jsHandler: Value) {
        val handlers = jsHandlers[channel] ?: return

        // Find the matching handler
        val entry = handlers.find { it.first == jsHandler }
        if (entry != null) {
            // Remove from network manager
            networkManager.unregisterHandler(channel, entry.second)
            // Remove from tracking
            handlers.remove(entry)
        }
    }

    /**
     * Clear all handlers for a channel.
     */
    private fun clearChannelHandlers(channel: String) {
        jsHandlers.remove(channel)
        // Note: NetworkManager doesn't have a per-channel clear yet
        // For now, we'll need to unregister each handler individually
        // TODO: Add clearChannel() to NetworkManager
    }

    // =====================================
    // Packet Sending (to be implemented with platform-specific code)
    // =====================================

    /**
     * Send packet to server (CLIENT ONLY).
     * This will be implemented in platform-specific code (Fabric/NeoForge).
     */
    private fun sendToServer(channel: String, data: Value) {
        val dataMap = valueToMap(data)
        val packet = PacketData(channel, dataMap)

        // TODO: Platform-specific implementation
        // This needs to send actual network packet via Fabric/NeoForge networking
        throw UnsupportedOperationException("sendToServer() not yet implemented - requires platform-specific networking")
    }

    /**
     * Send packet to specific client (SERVER ONLY).
     */
    private fun sendToClient(playerUuid: String, channel: String, data: Value) {
        val dataMap = valueToMap(data)
        val packet = PacketData(channel, dataMap)

        // TODO: Platform-specific implementation
        throw UnsupportedOperationException("sendToClient() not yet implemented - requires platform-specific networking")
    }

    /**
     * Broadcast packet to all clients (SERVER ONLY).
     */
    private fun broadcast(channel: String, data: Value) {
        val dataMap = valueToMap(data)
        val packet = PacketData(channel, dataMap)

        // TODO: Platform-specific implementation
        throw UnsupportedOperationException("broadcast() not yet implemented - requires platform-specific networking")
    }

    /**
     * Broadcast packet to clients in range (SERVER ONLY).
     */
    private fun broadcastInRange(position: Value, range: Double, channel: String, data: Value) {
        val dataMap = valueToMap(data)
        val packet = PacketData(channel, dataMap)

        // Extract position
        val posMap = if (position.hasMembers()) {
            mapOf(
                "x" to position.getMember("x").asDouble(),
                "y" to position.getMember("y").asDouble(),
                "z" to position.getMember("z").asDouble()
            )
        } else {
            throw IllegalArgumentException("position must have x, y, z members")
        }

        // TODO: Platform-specific implementation
        throw UnsupportedOperationException("broadcastInRange() not yet implemented - requires platform-specific networking")
    }

    // =====================================
    // Utility Methods for Packet Handling
    // =====================================

    /**
     * Route a received packet to registered handlers.
     * Called by platform-specific packet handlers.
     */
    fun routePacket(packet: PacketData, context: PacketContext) {
        networkManager.routePacket(packet, context)
    }

    /**
     * Get the NetworkManager instance (for platform-specific code).
     */
    fun getManager(): NetworkManager = networkManager

    // =====================================
    // Value Conversion Helpers
    // =====================================

    /**
     * Convert GraalVM Value to Kotlin Map.
     * Recursively handles nested objects and arrays.
     */
    private fun valueToMap(value: Value): Map<String, Any?> {
        if (!value.hasMembers()) {
            throw IllegalArgumentException("Expected object with members, got: ${value}")
        }

        val result = mutableMapOf<String, Any?>()
        for (key in value.memberKeys) {
            result[key] = valueToAny(value.getMember(key))
        }
        return result
    }

    /**
     * Convert GraalVM Value to Kotlin Any.
     * Handles primitives, objects, arrays, null.
     */
    private fun valueToAny(value: Value): Any? {
        return when {
            value.isNull -> null
            value.isBoolean -> value.asBoolean()
            value.isNumber -> {
                // Try to preserve number type
                when {
                    value.fitsInInt() -> value.asInt()
                    value.fitsInLong() -> value.asLong()
                    value.fitsInDouble() -> value.asDouble()
                    else -> value.asDouble()
                }
            }
            value.isString -> value.asString()
            value.hasArrayElements() -> {
                // Convert array to list
                (0 until value.arraySize).map { i ->
                    valueToAny(value.getArrayElement(i))
                }
            }
            value.hasMembers() -> {
                // Convert object to map
                value.memberKeys.associateWith { key ->
                    valueToAny(value.getMember(key))
                }
            }
            else -> value.toString()
        }
    }
}
