package com.rhett.rhettjs.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Packet data payload sent over the network.
 * Represents a message on a specific channel with arbitrary data.
 *
 * @property channel Channel name (must be valid: alphanumeric, underscore, hyphen, max one colon)
 * @property data Arbitrary data payload (must be JSON-serializable)
 * @throws IllegalArgumentException if channel name is invalid
 */
data class PacketData(
    val channel: String,
    val data: Map<String, Any?>
) {
    init {
        require(validateChannel(channel)) {
            "Invalid channel name: '$channel'. Must be alphanumeric with optional single colon for namespace."
        }
    }

    companion object {
        /**
         * Validate channel name format.
         * Valid: alphanumeric, underscore, hyphen, optional single colon for namespace
         * Invalid: spaces, special characters (except underscore/hyphen), multiple colons
         *
         * @param channel Channel name to validate
         * @return True if valid, false otherwise
         */
        fun validateChannel(channel: String): Boolean {
            if (channel.isEmpty()) return false

            // Check for multiple colons (max one for namespace)
            if (channel.count { it == ':' } > 1) return false

            // Valid characters: alphanumeric, underscore, hyphen, single colon
            val validPattern = Regex("^[a-zA-Z0-9_-]+(:([a-zA-Z0-9_-]+))?$")
            return validPattern.matches(channel)
        }
    }
}

/**
 * Context information for a received packet.
 * Contains sender metadata and packet routing information.
 *
 * @property senderUuid Sender's UUID
 * @property senderName Sender's display name
 * @property position Sender's position (null for server-sent packets)
 * @property timestamp Unix timestamp when packet was created
 * @property channel Channel the packet was sent on
 */
data class PacketContext(
    val senderUuid: String,
    val senderName: String,
    val position: Any?,
    val timestamp: Long,
    val channel: String
)

/**
 * Serializable packet for network transport.
 * Used for JSON serialization with kotlinx.serialization.
 *
 * @property channel Channel name
 * @property data Packet payload as JsonElement (for flexible serialization)
 * @property timestamp Unix timestamp
 */
@Serializable
data class RhettPacket(
    val channel: String,
    val data: Map<String, @Serializable(with = JsonElementSerializer::class) JsonElement> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)
