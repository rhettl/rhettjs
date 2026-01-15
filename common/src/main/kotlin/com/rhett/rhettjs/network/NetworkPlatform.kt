package com.rhett.rhettjs.network

/**
 * Platform-specific network operations.
 * Implemented by Fabric and NeoForge handlers.
 *
 * Uses service locator pattern to avoid tight coupling between common and platform modules.
 */
interface NetworkPlatform {
    /**
     * Send packet to server (CLIENT ONLY).
     * @param packet Packet to send
     */
    fun sendToServer(packet: PacketData)

    /**
     * Send packet to specific client (SERVER ONLY).
     * @param playerUuid Player's UUID
     * @param packet Packet to send
     */
    fun sendToClient(playerUuid: String, packet: PacketData)

    /**
     * Broadcast packet to all clients (SERVER ONLY).
     * @param packet Packet to send
     */
    fun broadcast(packet: PacketData)

    /**
     * Broadcast packet to clients in range (SERVER ONLY).
     * @param position Center position (x, y, z)
     * @param range Range in blocks
     * @param packet Packet to send
     */
    fun broadcastInRange(position: Map<String, Double>, range: Double, packet: PacketData)

    companion object {
        @Volatile
        private var instance: NetworkPlatform? = null

        /**
         * Register the platform-specific implementation.
         * Called by Fabric/NeoForge during initialization.
         */
        fun register(platform: NetworkPlatform) {
            instance = platform
        }

        /**
         * Get the current platform implementation.
         * @throws IllegalStateException if no platform is registered
         */
        fun get(): NetworkPlatform {
            return instance ?: throw IllegalStateException(
                "NetworkPlatform not initialized. Did the mod initialize correctly?"
            )
        }

        /**
         * Check if a platform is registered.
         */
        fun isRegistered(): Boolean = instance != null
    }
}
