package com.rhett.rhettjs.threading

/**
 * Singleton manager for the schedule() function's tick processing.
 *
 * This object:
 * 1. Provides a single ScheduleFunction instance for all scripts
 * 2. Exposes tick() method for game loop integration
 * 3. Allows platform-specific code to call tick() each game tick
 */
object TickScheduler {

    private val scheduleFunction = ScheduleFunction()

    /**
     * Get the ScheduleFunction instance for injection into JavaScript.
     * All scripts share this single instance to coordinate scheduled tasks.
     */
    fun getScheduleFunction(): ScheduleFunction = scheduleFunction

    /**
     * Process one game tick.
     * Must be called once per game tick from the main thread.
     *
     * Platform-specific code should hook this into:
     * - Fabric: ServerTickEvents.END_SERVER_TICK
     * - NeoForge: TickEvent.ServerTickEvent (Phase.END)
     */
    fun tick() {
        scheduleFunction.tick()
    }

    /**
     * Get the number of currently scheduled tasks (for debugging/monitoring).
     */
    fun getScheduledTaskCount(): Int {
        // Would need to expose this from ScheduleFunction
        // For now, return 0 (can add later if needed)
        return 0
    }
}
