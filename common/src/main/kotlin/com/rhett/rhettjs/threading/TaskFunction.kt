package com.rhett.rhettjs.threading

import com.rhett.rhettjs.RhettJSCommon
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import java.util.concurrent.Executors

/**
 * Task function for executing JavaScript callbacks on worker threads.
 *
 * Signature: task(callback, ...args)
 * - callback: JavaScript function to execute
 * - args: Arguments to pass to callback (validated - no Java objects allowed)
 */
class TaskFunction {

    companion object {
        private val WORKER_COUNT = Runtime.getRuntime().availableProcessors().coerceAtMost(4)

        private val executor = Executors.newFixedThreadPool(
            WORKER_COUNT
        ) { runnable ->
            Thread(runnable, "RhettJS-Worker").apply {
                isDaemon = true
            }
        }
    }

    /**
     * Execute a JavaScript callback on a worker thread.
     *
     * @param scope The JavaScript scope
     * @param callback The JavaScript function to execute
     * @param args Arguments to pass to the callback
     * @param onComplete Optional callback when task completes (for testing)
     * @throws IllegalArgumentException if args contain Java objects (shallow check)
     */
    fun execute(
        scope: Scriptable,
        callback: Scriptable,
        args: Array<Any?> = emptyArray(),
        onComplete: (args: Array<Any?>) -> Unit = {}
    ) {
        // Validate arguments (shallow check only)
        validateArguments(args)

        // Submit to worker pool
        executor.submit {
            try {
                // Create new Rhino context for this worker thread
                val cx = Context.enter()
                try {
                    cx.optimizationLevel = -1
                    cx.languageVersion = Context.VERSION_ES6

                    // Execute callback with arguments
                    if (callback is Function) {
                        callback.call(cx, scope, scope, args)
                    }

                    // Notify completion (for testing)
                    onComplete(args)

                } catch (e: Exception) {
                    RhettJSCommon.LOGGER.error("[RhettJS] Error in task() callback", e)
                    // Still call onComplete even on error (for testing)
                    onComplete(args)
                } finally {
                    Context.exit()
                }
            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[RhettJS] Fatal error in task()", e)
            }
        }
    }

    /**
     * Validate that arguments don't contain Java objects (shallow check only).
     * Allows: primitives (String, Number, Boolean), Scriptable (JS objects), null
     * Rejects: Other Java objects
     */
    private fun validateArguments(args: Array<Any?>) {
        args.forEachIndexed { index, arg ->
            when (arg) {
                null -> { /* null is allowed */ }
                is String,
                is Number,
                is Boolean -> { /* primitives are allowed */ }
                is Scriptable -> { /* JS objects are allowed (shallow check) */ }
                else -> {
                    throw IllegalArgumentException(
                        "Cannot pass Java objects to worker threads. " +
                        "Argument at index $index is ${arg.javaClass.simpleName}. " +
                        "Wrap in JS object: {data: value}"
                    )
                }
            }
        }
    }
}
