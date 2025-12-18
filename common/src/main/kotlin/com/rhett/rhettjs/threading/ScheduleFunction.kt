package com.rhett.rhettjs.threading

import com.rhett.rhettjs.RhettJSCommon
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Schedule function for executing JavaScript callbacks on the main thread after a delay.
 *
 * Signature: schedule(ticks, callback, ...args)
 * - ticks: Number of game ticks to wait (minimum 1, clamped automatically)
 * - callback: JavaScript function to execute
 * - args: Arguments to pass to callback
 */
class ScheduleFunction {

    /**
     * Scheduled task with tick countdown.
     */
    private data class ScheduledTask(
        var ticksRemaining: Int,
        val scope: Scriptable,
        val callback: Scriptable,
        val args: Array<Any?>,
        val onComplete: (args: Array<Any?>) -> Unit
    )

    private val scheduledTasks = ConcurrentLinkedQueue<ScheduledTask>()

    /**
     * Clamp ticks to minimum of 1.
     */
    fun getClampedTicks(ticks: Int): Int {
        return ticks.coerceAtLeast(1)
    }

    /**
     * Schedule a callback to execute after the specified number of ticks.
     *
     * @param ticks Number of ticks to wait (will be clamped to minimum 1)
     * @param scope The JavaScript scope (preserved for callback execution)
     * @param callback The JavaScript function to execute
     * @param args Arguments to pass to the callback
     * @param onComplete Optional callback when scheduled task executes (for testing)
     */
    fun schedule(
        ticks: Int,
        scope: Scriptable,
        callback: Scriptable,
        args: Array<Any?> = emptyArray(),
        onComplete: (args: Array<Any?>) -> Unit = {}
    ) {
        val clampedTicks = getClampedTicks(ticks)

        val task = ScheduledTask(
            ticksRemaining = clampedTicks,
            scope = scope,
            callback = callback,
            args = args,
            onComplete = onComplete
        )

        scheduledTasks.add(task)
    }

    /**
     * Process one game tick, executing any callbacks scheduled for this tick.
     * Should be called every game tick from the main thread.
     */
    fun tick() {
        val iterator = scheduledTasks.iterator()

        while (iterator.hasNext()) {
            val task = iterator.next()

            // Decrement tick counter
            task.ticksRemaining--

            // Execute if time has come
            if (task.ticksRemaining <= 0) {
                iterator.remove()
                executeScheduledTask(task)
            }
        }
    }

    /**
     * Execute a scheduled task on the current thread (should be main thread).
     */
    private fun executeScheduledTask(task: ScheduledTask) {
        try {
            // Create Rhino context for execution
            val cx = Context.enter()
            try {
                cx.optimizationLevel = -1
                cx.languageVersion = Context.VERSION_ES6

                // Execute callback with preserved scope and arguments
                if (task.callback is Function) {
                    task.callback.call(cx, task.scope, task.scope, task.args)
                }

                // Notify completion (for testing)
                task.onComplete(task.args)

            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[RhettJS] Error in schedule() callback", e)
                // Still call onComplete even on error (for testing)
                task.onComplete(task.args)
            } finally {
                Context.exit()
            }
        } catch (e: Exception) {
            RhettJSCommon.LOGGER.error("[RhettJS] Fatal error in schedule()", e)
        }
    }
}
