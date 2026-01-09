package com.rhett.rhettjs.async

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.config.ConfigManager
import java.util.concurrent.CompletableFuture

/**
 * AsyncScheduler for tick-based delays in GraalVM scripts.
 * Replaces the old EventLoop system with a simpler tick-based timer system.
 *
 * This scheduler allows JavaScript code to use `await wait(ticks)` to pause
 * execution for a specific number of game ticks. All timers are processed
 * on the main server thread during each tick.
 */
object AsyncScheduler {

    /**
     * A timer that counts down ticks and completes a future when it reaches zero.
     */
    private data class TickTimer(
        var ticksRemaining: Int,
        val future: CompletableFuture<Unit>
    )

    // List of active timers (modified only on server thread)
    private val activeTimers = mutableListOf<TickTimer>()

    // Queue of callbacks to execute after timer processing
    private val pendingCallbacks = mutableListOf<() -> Unit>()

    /**
     * Schedule a delay that will complete after the specified number of ticks.
     *
     * @param ticks Number of game ticks to wait (1 tick = 50ms, 20 ticks = 1 second)
     * @return CompletableFuture that completes after the delay
     */
    fun scheduleWait(ticks: Int): CompletableFuture<Unit> {
        require(ticks > 0) { "Ticks must be positive, got: $ticks" }

        val future = CompletableFuture<Unit>()
        val timer = TickTimer(ticks, future)

        synchronized(activeTimers) {
            activeTimers.add(timer)
        }

        ConfigManager.debug("Scheduled wait for $ticks ticks (${activeTimers.size} active timers)")
        return future
    }

    /**
     * Schedule a callback to run after timer processing completes.
     * This prevents ConcurrentModificationException when callbacks trigger new wait() calls.
     */
    fun scheduleCallback(callback: () -> Unit) {
        synchronized(pendingCallbacks) {
            pendingCallbacks.add(callback)
        }
    }

    /**
     * Process all active timers. Should be called once per server tick.
     * Decrements all timer counters and completes futures that reach zero.
     */
    fun tick() {
        // Process timers
        if (activeTimers.isNotEmpty()) {
            synchronized(activeTimers) {
                val iterator = activeTimers.iterator()
                var completedCount = 0

                while (iterator.hasNext()) {
                    val timer = iterator.next()
                    timer.ticksRemaining--

                    if (timer.ticksRemaining <= 0) {
                        iterator.remove()
                        timer.future.complete(Unit)
                        completedCount++
                    }
                }

                if (completedCount > 0) {
                    ConfigManager.debug("Completed $completedCount timer(s), ${activeTimers.size} remaining")
                }
            }
        }

        // Execute pending callbacks after timer processing
        if (pendingCallbacks.isNotEmpty()) {
            val callbacks = synchronized(pendingCallbacks) {
                val copy = pendingCallbacks.toList()
                pendingCallbacks.clear()
                copy
            }

            // Execute callbacks outside synchronized block
            callbacks.forEach { callback ->
                try {
                    callback()
                } catch (e: Exception) {
                    RhettJSCommon.LOGGER.error("[RhettJS] Error executing wait() callback", e)
                }
            }
        }
    }

    /**
     * Clear all pending timers and callbacks. Used during server shutdown or script reload.
     */
    fun clear() {
        synchronized(activeTimers) {
            val count = activeTimers.size
            if (count > 0) {
                RhettJSCommon.LOGGER.info("[RhettJS] Clearing $count pending timer(s)")

                // Complete all futures exceptionally to avoid hanging promises
                activeTimers.forEach { timer ->
                    timer.future.completeExceptionally(
                        InterruptedException("AsyncScheduler cleared during shutdown/reload")
                    )
                }

                activeTimers.clear()
            }
        }

        synchronized(pendingCallbacks) {
            pendingCallbacks.clear()
        }
    }

    /**
     * Get the number of active timers (for debugging/monitoring).
     */
    fun getActiveTimerCount(): Int {
        synchronized(activeTimers) {
            return activeTimers.size
        }
    }

    /**
     * Immediately execute all pending callbacks without waiting for the next tick.
     * This is useful for synchronous contexts that need to resolve Promises immediately,
     * such as command tab completion.
     *
     * CAUTION: This should only be called from the server thread or in contexts where
     * it's safe to execute callbacks synchronously.
     */
    fun flushCallbacks() {
        if (pendingCallbacks.isNotEmpty()) {
            val callbacks = synchronized(pendingCallbacks) {
                val copy = pendingCallbacks.toList()
                pendingCallbacks.clear()
                copy
            }

            ConfigManager.debug("[AsyncScheduler] Flushing ${callbacks.size} pending callback(s)")

            // Execute callbacks
            callbacks.forEach { callback ->
                try {
                    callback()
                } catch (e: Exception) {
                    RhettJSCommon.LOGGER.error("[RhettJS] Error executing flushed callback", e)
                }
            }
        }
    }
}
