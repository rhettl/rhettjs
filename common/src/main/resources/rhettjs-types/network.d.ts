// RhettJS Network API Type Definitions
// Version: 0.4.0
// Last updated: 2026-01-15
// Phase 1: Low-level packet communication (UDP-style)

/**
 * Packet data payload
 * Pure JavaScript objects - no Java/Kotlin types
 */
export interface PacketData {
    /** Channel name (auto-namespaced if no colon) */
    channel: string;
    /** Arbitrary data payload (must be JSON-serializable) */
    data: Record<string, any>;
}

/**
 * Position in 3D space
 */
export interface Position {
    x: number;
    y: number;
    z: number;
}

/**
 * Packet context (sender information)
 * Provided to packet handlers
 */
export interface PacketContext {
    /** Sender's UUID */
    senderUuid: string;
    /** Sender's display name */
    senderName: string;
    /** Sender's position (null if server-sent) */
    position: Position | null;
    /** Timestamp when packet was created */
    timestamp: number;
    /** Channel the packet was sent on */
    channel: string;
}

/**
 * Packet handler function
 * @param data - Packet payload
 * @param context - Sender context information
 */
export type PacketHandler = (data: Record<string, any>, context: PacketContext) => void;

declare module 'rhettjs/network' {
    /**
     * Low-level packet communication API (UDP-style)
     *
     * Provides event-driven packet send/receive between client and server.
     * All communication is channel-based and fire-and-forget (no response expected).
     *
     * For request/response patterns, use the RPC API instead.
     *
     * @example
     * // CLIENT: Send packet to server
     * import Network from 'rhettjs/network';
     *
     * Network.sendToServer('mymod:custom', {
     *     action: 'purchase',
     *     item: 'diamond_sword'
     * });
     *
     * @example
     * // SERVER: Listen for packets
     * import Network from 'rhettjs/network';
     *
     * Network.on('mymod:custom', (data, context) => {
     *     console.log(`${context.senderName} sent:`, data);
     *
     *     // Send response back to client
     *     Network.sendToClient(context.senderUuid, 'mymod:response', {
     *         success: true
     *     });
     * });
     */
    export const Network: {
        // =====================================
        // Handler Registration (Both Sides)
        // =====================================

        /**
         * Register a packet handler for a specific channel
         *
         * Multiple handlers can be registered to the same channel.
         * Channel names are auto-namespaced if they don't contain a colon.
         *
         * @param channel - Channel name (e.g., 'custom' or 'mymod:custom')
         * @param handler - Handler function
         *
         * @example
         * Network.on('trade_request', (data, context) => {
         *     console.log(`Trade request from ${context.senderName}:`, data);
         * });
         *
         * @example
         * // With explicit namespace
         * Network.on('mymod:custom', (data, context) => {
         *     // Handle packet
         * });
         */
        on(channel: string, handler: PacketHandler): void;

        /**
         * Unregister a specific packet handler
         *
         * @param channel - Channel name
         * @param handler - Handler function to remove
         *
         * @example
         * const handler = (data, ctx) => console.log(data);
         * Network.on('test', handler);
         * Network.off('test', handler); // Removes this specific handler
         */
        off(channel: string, handler: PacketHandler): void;

        /**
         * Unregister all handlers for a channel
         *
         * @param channel - Channel name
         *
         * @example
         * Network.off('mymod:custom'); // Removes all handlers
         */
        off(channel: string): void;

        // =====================================
        // Packet Sending (Client Side)
        // =====================================

        /**
         * Send packet to server (CLIENT ONLY)
         *
         * Sends arbitrary data to the server. No response is expected.
         * For request/response patterns, use RPC API instead.
         *
         * @param channel - Channel name
         * @param data - Packet payload (must be JSON-serializable)
         *
         * @throws Error if called on server side
         * @throws Error if data cannot be serialized
         *
         * @example
         * // Send simple action
         * Network.sendToServer('shop:open', {});
         *
         * @example
         * // Send complex data
         * Network.sendToServer('trade:propose', {
         *     offeredItems: ['diamond', 'iron_ingot'],
         *     requestedItems: ['emerald'],
         *     quantity: 5
         * });
         */
        sendToServer(channel: string, data: Record<string, any>): void;

        // =====================================
        // Packet Sending (Server Side)
        // =====================================

        /**
         * Send packet to specific client (SERVER ONLY)
         *
         * Sends data to a single client identified by UUID.
         *
         * @param playerUuid - Target player's UUID
         * @param channel - Channel name
         * @param data - Packet payload
         *
         * @throws Error if called on client side
         * @throws Error if player is not online
         *
         * @example
         * Network.sendToClient(player.uuid, 'notification', {
         *     message: 'You received a gift!'
         * });
         */
        sendToClient(playerUuid: string, channel: string, data: Record<string, any>): void;

        /**
         * Broadcast packet to all connected clients (SERVER ONLY)
         *
         * Sends same packet to every player on the server.
         *
         * @param channel - Channel name
         * @param data - Packet payload
         *
         * @throws Error if called on client side
         *
         * @example
         * Network.broadcast('announcement', {
         *     message: 'Server restarting in 5 minutes'
         * });
         *
         * @example
         * Network.broadcast('boss:spawned', {
         *     type: 'wither',
         *     position: { x: 100, y: 64, z: 200 }
         * });
         */
        broadcast(channel: string, data: Record<string, any>): void;

        /**
         * Broadcast packet to clients within range (SERVER ONLY)
         *
         * Sends packet only to players within specified distance of a position.
         * Useful for local effects (explosions, sounds, particles).
         *
         * @param position - Center position
         * @param range - Radius in blocks
         * @param channel - Channel name
         * @param data - Packet payload
         *
         * @throws Error if called on client side
         *
         * @example
         * Network.broadcastInRange(
         *     { x: 100, y: 64, z: 200 },
         *     64,
         *     'explosion',
         *     { power: 10 }
         * );
         */
        broadcastInRange(
            position: Position,
            range: number,
            channel: string,
            data: Record<string, any>
        ): void;

        // =====================================
        // Utility Methods
        // =====================================

        /**
         * Get list of registered channels
         *
         * @returns Array of channel names
         *
         * @example
         * const channels = Network.getChannels();
         * console.log('Listening on:', channels);
         */
        getChannels(): string[];

        /**
         * Check if a handler is registered for a channel
         *
         * @param channel - Channel name
         * @returns True if at least one handler is registered
         *
         * @example
         * if (Network.hasChannel('mymod:custom')) {
         *     console.log('Channel is registered');
         * }
         */
        hasChannel(channel: string): boolean;

        /**
         * Clear all registered handlers (debugging/testing)
         *
         * @example
         * Network.clearAll(); // Remove all handlers
         */
        clearAll(): void;
    };

    export default Network;
}

// Legacy bare module support
declare module 'Network' {
    export { default } from 'rhettjs/network';
}
