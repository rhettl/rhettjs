// RhettJS Events API Type Definitions
// Version: 0.4.0
// Last updated: 2026-01-15
// Phase 3: Pub/Sub event system
// Status: Not yet implemented (definitions only)

/**
 * Event handler function
 * @param data - Event payload
 */
export type EventHandler<TData = any> = (data: TData) => void;

/**
 * Server-side event handler
 * @param player - Player who triggered the event
 * @param data - Event payload
 */
export type ServerEventHandler<TData = any> = (
    player: { uuid: string; name: string; position: { x: number; y: number; z: number } },
    data: TData
) => void;

declare module 'rhettjs/events' {
    /**
     * Event Bus API (Pub/Sub pattern)
     *
     * Provides event-driven communication with multiple subscribers.
     * Built on top of Network API.
     *
     * Unlike Network API:
     * - Events can have multiple subscribers
     * - More semantic naming (emit/broadcast vs send)
     * - Built for notifications, not raw packet control
     *
     * ⚠️ Phase 3: Not yet implemented
     *
     * @example
     * // CLIENT: Listen for server events
     * import Events from 'rhettjs/events';
     *
     * Events.on('boss:spawned', (data) => {
     *     UI.showWarning(`A ${data.type} has spawned!`);
     * });
     *
     * @example
     * // SERVER: Broadcast event
     * import Events from 'rhettjs/events';
     *
     * Events.broadcast('boss:spawned', {
     *     type: 'wither',
     *     position: { x: 100, y: 64, z: 200 }
     * });
     */
    export const Events: {
        // =====================================
        // Event Subscription (Both Sides)
        // =====================================

        /**
         * Subscribe to an event
         *
         * Multiple handlers can subscribe to the same event.
         * Event names are auto-namespaced if they don't contain a colon.
         *
         * @param event - Event name (e.g., 'game:start' or 'boss:spawned')
         * @param handler - Event handler function
         *
         * @example
         * Events.on('game:round_start', (data) => {
         *     console.log(`Round ${data.round} starting!`);
         * });
         *
         * @example
         * // Multiple handlers for same event
         * Events.on('player:achievement', (data) => {
         *     console.log('Achievement unlocked!', data.name);
         * });
         * Events.on('player:achievement', (data) => {
         *     playSound('achievement');
         * });
         */
        on<TData = any>(event: string, handler: EventHandler<TData>): void;

        /**
         * Subscribe to an event (fires once then auto-unsubscribes)
         *
         * @param event - Event name
         * @param handler - Event handler function
         *
         * @example
         * Events.once('game:end', (data) => {
         *     console.log('Game ended, final score:', data.score);
         * });
         */
        once<TData = any>(event: string, handler: EventHandler<TData>): void;

        /**
         * Unsubscribe from an event
         *
         * @param event - Event name
         * @param handler - Handler to remove (optional, removes all if not provided)
         *
         * @example
         * const handler = (data) => console.log(data);
         * Events.on('test', handler);
         * Events.off('test', handler); // Remove specific handler
         *
         * @example
         * Events.off('test'); // Remove all handlers for event
         */
        off<TData = any>(event: string, handler?: EventHandler<TData>): void;

        // =====================================
        // Event Emission (Client Side)
        // =====================================

        /**
         * Emit event to server (CLIENT ONLY)
         *
         * Triggers event handlers on the server.
         *
         * @param event - Event name
         * @param data - Event payload
         *
         * @example
         * Events.emit('player:achievement', {
         *     name: 'first_kill',
         *     timestamp: Date.now()
         * });
         */
        emit<TData = any>(event: string, data: TData): void;

        // =====================================
        // Event Broadcasting (Server Side)
        // =====================================

        /**
         * Broadcast event to all clients (SERVER ONLY)
         *
         * Triggers event handlers on all connected clients.
         *
         * @param event - Event name
         * @param data - Event payload
         *
         * @example
         * Events.broadcast('announcement', {
         *     message: 'Server maintenance in 5 minutes'
         * });
         *
         * @example
         * Events.broadcast('game:state_change', {
         *     state: 'ROUND_START',
         *     round: 5
         * });
         */
        broadcast<TData = any>(event: string, data: TData): void;

        /**
         * Send event to specific player (SERVER ONLY)
         *
         * Triggers event handlers on a single client.
         *
         * @param playerUuid - Target player's UUID
         * @param event - Event name
         * @param data - Event payload
         *
         * @example
         * Events.sendToPlayer(uuid, 'notification', {
         *     message: 'You received mail!',
         *     type: 'info'
         * });
         */
        sendToPlayer<TData = any>(playerUuid: string, event: string, data: TData): void;

        /**
         * Broadcast event to players in range (SERVER ONLY)
         *
         * Triggers event handlers on clients within range of a position.
         *
         * @param position - Center position
         * @param range - Radius in blocks
         * @param event - Event name
         * @param data - Event payload
         *
         * @example
         * Events.broadcastInRange(
         *     { x: 100, y: 64, z: 200 },
         *     64,
         *     'explosion',
         *     { power: 10, type: 'TNT' }
         * );
         */
        broadcastInRange<TData = any>(
            position: { x: number; y: number; z: number },
            range: number,
            event: string,
            data: TData
        ): void;

        // =====================================
        // Server Event Subscription
        // =====================================

        /**
         * Subscribe to client events (SERVER ONLY)
         *
         * Listen for events emitted from clients.
         *
         * @param event - Event name
         * @param handler - Event handler (receives player info and data)
         *
         * @example
         * // On server
         * Events.on('client:achievement', (player, data) => {
         *     console.log(`${player.name} got achievement: ${data.name}`);
         *     // Broadcast to all players
         *     Events.broadcast('player:achievement', {
         *         playerName: player.name,
         *         achievement: data.name
         *     });
         * });
         */
        onClient<TData = any>(event: string, handler: ServerEventHandler<TData>): void;

        // =====================================
        // Utility Methods
        // =====================================

        /**
         * Get list of subscribed events
         *
         * @returns Array of event names
         *
         * @example
         * const events = Events.getEvents();
         * console.log('Listening to:', events);
         */
        getEvents(): string[];

        /**
         * Check if any handlers are subscribed to an event
         *
         * @param event - Event name
         * @returns True if at least one handler is subscribed
         *
         * @example
         * if (Events.hasEvent('game:start')) {
         *     console.log('Game start event has subscribers');
         * }
         */
        hasEvent(event: string): boolean;

        /**
         * Get number of handlers for an event
         *
         * @param event - Event name
         * @returns Number of handlers
         *
         * @example
         * const count = Events.getHandlerCount('player:achievement');
         * console.log(`${count} handlers listening`);
         */
        getHandlerCount(event: string): number;

        /**
         * Clear all event handlers (debugging/testing)
         *
         * @example
         * Events.clearAll();
         */
        clearAll(): void;
    };

    export default Events;
}

// Legacy bare module support
declare module 'Events' {
    export { default } from 'rhettjs/events';
}
