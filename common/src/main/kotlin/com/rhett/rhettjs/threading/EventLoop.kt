package com.rhett.rhettjs.threading

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.config.ConfigManager
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single-threaded event loop for script execution.
 * 
 * All Promise microtasks run on the same Rhino Context, eliminating
 * race conditions between threads with separate microtask queues.
 * 
 * The loop processes:
 * 1. Worker task results (from WorkerPool)
 * 2. Wait timer completions (tick-based delays)
 * 3. Microtasks (.then/.finally callbacks)
 */
class EventLoop(
    private val context: Context,
    private val scope: Scriptable
) {
    /**
     * Result from a completed worker task.
     */
    data class WorkerResult(
        val resolve: Function,
        val reject: Function,
        val result: Any?,
        val error: Throwable?,
        val scope: Scriptable
    )

    /**
     * Pending wait timer.
     */
    data class WaitTimer(
        var ticksRemaining: Int,
        val resolve: Function,
        val scope: Scriptable
    )

    // Thread-safe queue for worker results
    private val workerResults = ConcurrentLinkedQueue<WorkerResult>()
    
    // Wait timers (only accessed from event loop thread)
    private val waitTimers = mutableListOf<WaitTimer>()
    
    // Flag to stop accepting new work (for Runtime.exit())
    private val accepting = AtomicBoolean(true)
    
    // Current tick count (incremented by game tick events)
    @Volatile
    private var currentTick = 0L

    /**
     * Post a worker result to be processed by the event loop.
     * Called from worker threads.
     */
    fun postWorkerResult(result: WorkerResult) {
        if (accepting.get()) {
            workerResults.add(result)
        }
    }

    /**
     * Add a wait timer to be resolved after N ticks.
     * Called from the event loop thread.
     */
    fun addWaitTimer(ticks: Int, resolve: Function, scope: Scriptable) {
        if (accepting.get()) {
            waitTimers.add(WaitTimer(ticks.coerceAtLeast(1), resolve, scope))
        }
    }

    /**
     * Process one game tick.
     * Decrements wait timers and resolves any that have elapsed.
     */
    fun tick() {
        currentTick++
        
        val iterator = waitTimers.iterator()
        while (iterator.hasNext()) {
            val timer = iterator.next()
            timer.ticksRemaining--
            
            if (timer.ticksRemaining <= 0) {
                iterator.remove()
                // Resolve the wait Promise with undefined
                try {
                    timer.resolve.call(context, timer.scope, timer.scope, arrayOf(Context.getUndefinedValue()))
                } catch (e: Exception) {
                    RhettJSCommon.LOGGER.error("[RhettJS] Error resolving wait timer", e)
                }
            }
        }
    }

    /**
     * Process all pending work until queues are empty.
     * Returns when no more work is pending.
     *
     * This method simulates game ticks at 20 TPS (50ms per tick) while waiting
     * for async work to complete. This allows wait() timers to resolve even
     * though the server thread is blocked.
     *
     * @param scriptName Script name for logging
     * @param timeout Maximum time to wait in milliseconds (default: from RuntimeAPI.getEventLoopTimeout())
     */
    fun runUntilComplete(scriptName: String, timeout: Long = com.rhett.rhettjs.api.RuntimeAPI.getEventLoopTimeout()) {
        val startTime = System.currentTimeMillis()
        var iterations = 0
        var lastTickTime = System.currentTimeMillis()
        val tickIntervalMs = 50L // 20 TPS = 50ms per tick

        while (true) {
            val now = System.currentTimeMillis()

            // Check timeout
            if (now - startTime > timeout) {
                throw RuntimeException(
                    "Script '$scriptName' timed out after ${timeout}ms. " +
                    "Pending: ${workerResults.size} worker results, ${waitTimers.size} wait timers"
                )
            }

            // Simulate game ticks at 20 TPS while waiting
            if (now - lastTickTime >= tickIntervalMs) {
                tick()
                lastTickTime = now
            }

            // Process worker results
            while (true) {
                val result = workerResults.poll() ?: break
                processWorkerResult(result)
            }

            // Process microtasks
            context.processMicrotasks()

            // Check if done
            if (workerResults.isEmpty() && waitTimers.isEmpty() && !WorkerPool.hasPendingTasks()) {
                if (iterations > 0) {
                    ConfigManager.debug("Script '$scriptName' completed after $iterations iterations")
                }
                break
            }

            iterations++
            if (iterations % 100 == 0) {
                ConfigManager.debug(
                    "Event loop: ${workerResults.size} results, ${waitTimers.size} timers, " +
                    "${WorkerPool.getPendingTaskCount()} workers"
                )
            }

            // Small delay to avoid busy-waiting
            Thread.sleep(5)
        }
    }

    /**
     * Process a single worker result by calling resolve or reject.
     */
    private fun processWorkerResult(result: WorkerResult) {
        try {
            if (result.error != null) {
                val errorObj = Context.javaToJS(result.error, result.scope)
                result.reject.call(context, result.scope, result.scope, arrayOf(errorObj))
            } else {
                result.resolve.call(context, result.scope, result.scope, arrayOf(result.result))
            }
        } catch (e: Exception) {
            RhettJSCommon.LOGGER.error("[RhettJS] Error processing worker result", e)
        }
    }

    /**
     * Check if there's any pending work.
     */
    fun hasPendingWork(): Boolean {
        return workerResults.isNotEmpty() || waitTimers.isNotEmpty() || WorkerPool.hasPendingTasks()
    }

    /**
     * Stop accepting new work and clear queues.
     * Called by Runtime.exit().
     */
    fun shutdown() {
        accepting.set(false)
        workerResults.clear()
        waitTimers.clear()
    }

    /**
     * Reset to accept work again.
     */
    fun reset() {
        accepting.set(true)
    }

    companion object {
        // Current active event loop (one per script execution)
        @Volatile
        private var current: EventLoop? = null

        /**
         * Get the current event loop.
         * Returns null if no script is executing.
         */
        fun getCurrent(): EventLoop? = current

        /**
         * Set the current event loop.
         * Called by ScriptEngine when starting script execution.
         */
        fun setCurrent(loop: EventLoop?) {
            current = loop
        }
    }
}

