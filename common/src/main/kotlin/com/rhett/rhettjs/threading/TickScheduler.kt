package com.rhett.rhettjs.threading

import com.rhett.rhettjs.async.AsyncScheduler

/**
 * Singleton manager for tick processing with GraalVM AsyncScheduler.
 *
 * This object:
 * 1. Exposes tick() method for game loop integration
 * 2. Ticks the AsyncScheduler to process wait timers
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
        AsyncScheduler.tick()
    }

    /**
     * Cancel all pending work in the async scheduler.
     * Called during script shutdown or server stop.
     */
    fun cancelAll() {
        AsyncScheduler.clear()
    }

    /**
     * Reset to accept work again.
     * Called on script reload.
     */
    fun reset() {
        AsyncScheduler.clear()
    }
}
