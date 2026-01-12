package com.rhett.rhettjs.engine.api

import com.rhett.rhettjs.async.AsyncScheduler
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.proxy.ProxyExecutable

/**
 * Wait function API for JavaScript scripts.
 * Provides the global wait() function for tick-based delays.
 *
 * Relocated from: GraalEngine.kt (lines 541-594)
 * Date: 2026-01-12
 */
object WaitFunctionAPI {

    /**
     * Create the wait() function for tick-based delays.
     * Returns a Promise that resolves after the specified number of ticks.
     *
     * @param context The GraalVM context
     * @return ProxyExecutable that creates a Promise-based delay
     */
    fun create(context: Context): ProxyExecutable {
        return ProxyExecutable { args ->
            if (args.isEmpty()) {
                throw IllegalArgumentException("wait() requires a ticks argument (number of game ticks)")
            }

            val ticks = when {
                args[0].isNumber -> args[0].asInt()
                else -> throw IllegalArgumentException("wait() argument must be a number (ticks)")
            }

            if (ticks <= 0) {
                throw IllegalArgumentException("wait() ticks must be positive, got: $ticks")
            }

            // Schedule the delay and get a CompletableFuture
            val future = AsyncScheduler.scheduleWait(ticks)

            // Create a JavaScript Promise that resolves when the CompletableFuture completes
            // We need to evaluate JavaScript code to create a proper Promise object
            val promiseCode = """
                new Promise((resolve, reject) => {
                    // The resolve/reject functions will be called from Kotlin
                    globalThis.__waitResolve = resolve;
                    globalThis.__waitReject = reject;
                })
            """
            val promise = context.eval("js", promiseCode)

            // Get the resolve and reject functions
            val resolve = context.getBindings("js").getMember("__waitResolve")
            val reject = context.getBindings("js").getMember("__waitReject")

            // Clean up the global references
            context.getBindings("js").removeMember("__waitResolve")
            context.getBindings("js").removeMember("__waitReject")

            // When the future completes, schedule the promise resolution on the next tick
            // This prevents ConcurrentModificationException if JS code calls wait() again
            future.whenComplete { _, throwable ->
                // Schedule the callback to run on the next server tick
                // This ensures we're not executing JS during timer iteration
                AsyncScheduler.scheduleCallback {
                    if (throwable != null) {
                        reject.execute(throwable.message)
                    } else {
                        resolve.execute()
                    }
                }
            }

            promise
        }
    }
}
