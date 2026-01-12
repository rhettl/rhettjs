package com.rhett.rhettjs.engine

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.async.AsyncScheduler
import com.rhett.rhettjs.config.ConfigManager
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import java.util.concurrent.CompletableFuture

/**
 * Helper utilities for converting CompletableFuture to JavaScript Promises.
 * Bridges Java async operations to JS Promise-based APIs.
 *
 * Relocated from: GraalEngine.kt (lines 1480-1545)
 * Date: 2026-01-12
 */
object PromiseHelpers {

    /**
     * Convert CompletableFuture to JavaScript Promise.
     * Helper to bridge Java async operations to JS Promises.
     *
     * IMPORTANT: The future completion may happen on a different thread (e.g., server thread),
     * but GraalVM contexts are single-threaded. We use AsyncScheduler to schedule the
     * promise resolution back onto the next tick, which runs on a thread that can access
     * the context safely.
     */
    fun <T> convertFutureToPromise(context: Context, future: CompletableFuture<T>): Value {
        // Generate unique ID for this promise to avoid collisions
        val promiseId = "_rjs_promise_${System.nanoTime()}_${(Math.random() * 1000000).toInt()}"

        // Create a Promise and store resolve/reject with unique names
        val promiseCode = """
            new Promise((resolve, reject) => {
                globalThis.${promiseId}_resolve = resolve;
                globalThis.${promiseId}_reject = reject;
            })
        """
        val promise = context.eval("js", promiseCode)

        // Get resolve/reject functions
        val resolve = context.getBindings("js").getMember("${promiseId}_resolve")
        val reject = context.getBindings("js").getMember("${promiseId}_reject")

        // When future completes, schedule the promise resolution on the next tick
        // This ensures we're not trying to access the GraalVM context from the wrong thread
        future.whenComplete { result, throwable ->
            // Schedule promise resolution on next tick to avoid multi-threaded access
            AsyncScheduler.scheduleCallback {
                // Enter context for multi-threaded access
                context.enter()
                try {
                    if (throwable != null) {
                        val errorMsg = throwable.cause?.message ?: throwable.message ?: "Unknown error"
                        ConfigManager.debug("[Promise] Rejecting with error: $errorMsg")
                        reject.execute(errorMsg)
                    } else {
                        ConfigManager.debug("[Promise] Resolving with result: $result")
                        // Convert result to GraalVM Value to ensure proper type conversion
                        // (e.g., Kotlin List -> JS Array, Kotlin Map -> JS Object)
                        val jsResult = context.asValue(result)
                        resolve.execute(jsResult)
                    }
                } catch (e: Exception) {
                    ConfigManager.debug("[Promise] Error during promise resolution: ${e.message}")
                    try {
                        reject.execute("Promise resolution error: ${e.message}")
                    } catch (e2: Exception) {
                        RhettJSCommon.LOGGER.error("[Promise] Failed to reject promise", e2)
                    }
                } finally {
                    // Clean up globals after promise settles
                    try {
                        context.getBindings("js").removeMember("${promiseId}_resolve")
                        context.getBindings("js").removeMember("${promiseId}_reject")
                    } catch (e: Exception) {
                        // Ignore cleanup errors
                    }
                    // Leave context for multi-threaded access
                    context.leave()
                }
            }
        }

        return promise
    }

    /**
     * Create a rejected Promise with error message.
     */
    fun createRejectedPromise(context: Context, message: String): Value {
        return context.eval("js", "Promise.reject(new Error('$message'))")
    }
}
