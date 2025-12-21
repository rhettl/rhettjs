package com.rhett.rhettjs.threading

/**
 * Singleton manager for tick processing.
 *
 * This object:
 * 1. Exposes tick() method for game loop integration
 * 2. Ticks the current EventLoop to process wait timers
 * 3. Allows platform-specific code to call tick() each game tick
 */
object TickScheduler {

    /**
     * Process one game tick.
     * Must be called once per game tick from the main thread.
     *
     * Platform-specific code should hook this into:
     * - Fabric: ServerTickEvents.END_SERVER_TICK
     * - NeoForge: TickEvent.ServerTickEvent (Phase.END)
     */
    fun tick() {
        // Tick the current event loop (if any script is executing)
        EventLoop.getCurrent()?.tick()
    }

    /**
     * Cancel all pending work in the current event loop.
     * Called by Runtime.exit() to stop script execution.
     */
    fun cancelAll() {
        EventLoop.getCurrent()?.shutdown()
    }

    /**
     * Reset to accept work again.
     * Called on script reload.
     */
    fun reset() {
        EventLoop.getCurrent()?.reset()
    }
}
