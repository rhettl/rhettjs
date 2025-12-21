package com.rhett.rhettjs.threading

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.util.ErrorHandler
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function as RhinoFunction
import org.mozilla.javascript.Scriptable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Worker thread pool for CPU-heavy JavaScript tasks.
 *
 * Workers execute functions in isolated Rhino Contexts with no access
 * to parent scope. Results are posted to the EventLoop for resolution.
 */
object WorkerPool {

    private val WORKER_COUNT = Runtime.getRuntime().availableProcessors().coerceAtMost(4)

    private val executor = Executors.newFixedThreadPool(WORKER_COUNT) { runnable ->
        Thread(runnable, "RhettJS-Worker").apply {
            isDaemon = true
        }
    }

    // Track in-flight tasks
    private val pendingTasks = AtomicInteger(0)

    /**
     * Get the number of worker threads.
     */
    fun getWorkerCount(): Int = WORKER_COUNT

    /**
     * Get the number of pending tasks.
     */
    fun getPendingTaskCount(): Int = pendingTasks.get()

    /**
     * Check if there are pending tasks.
     */
    fun hasPendingTasks(): Boolean = pendingTasks.get() > 0

    /**
     * Submit a task to the worker pool.
     *
     * @param callback The JavaScript function to execute
     * @param args Arguments to pass to the callback
     * @param resolve Promise resolve function
     * @param reject Promise reject function
     * @param scope The scope for Promise resolution
     * @param threadSafeAPIs APIs to inject into worker scope
     */
    fun submit(
        callback: RhinoFunction,
        args: Array<Any?>,
        resolve: RhinoFunction,
        reject: RhinoFunction,
        scope: Scriptable,
        threadSafeAPIs: Map<String, Any>
    ) {
        pendingTasks.incrementAndGet()
        
        executor.submit {
            var result: Any? = null
            var error: Throwable? = null

            try {
                val cx = Context.enter()
                try {
                    cx.optimizationLevel = -1
                    cx.languageVersion = Context.VERSION_ES6

                    // Create isolated worker scope
                    val workerScope = cx.initStandardObjects()

                    // Inject thread-safe APIs
                    threadSafeAPIs.forEach { (name, api) ->
                        workerScope.put(name, workerScope, api)
                    }

                    // Inject Runtime API
                    val runtime = com.rhett.rhettjs.api.RuntimeAPI.createJSObject(workerScope)
                    workerScope.put("Runtime", workerScope, runtime)

                    // Execute callback in worker context
                    // Workers run in isolated Context with separate globals and limited APIs
                    // Note: Callback retains closure scope (can access parent variables)
                    //
                    // IMPORTANT: Avoid sharing mutable state between workers and main thread!
                    // Workers run concurrently - modifying shared objects causes race conditions.
                    //
                    // Recommended pattern: Pass data as arguments, return results
                    result = callback.call(cx, workerScope, workerScope, args)
                    
                } catch (e: Exception) {
                    error = e
                    ErrorHandler.logScriptError("task() callback", e)
                } finally {
                    Context.exit()
                }
            } catch (e: Exception) {
                error = e
                RhettJSCommon.LOGGER.error("[RhettJS] Fatal error in worker", e)
            } finally {
                pendingTasks.decrementAndGet()
            }
            
            // Post result to event loop
            val eventLoop = EventLoop.getCurrent()
            if (eventLoop != null) {
                eventLoop.postWorkerResult(
                    EventLoop.WorkerResult(
                        resolve = resolve,
                        reject = reject,
                        result = result,
                        error = error,
                        scope = scope
                    )
                )
            } else {
                RhettJSCommon.LOGGER.warn("[RhettJS] No event loop to post worker result")
            }
        }
    }

    /**
     * Shutdown the worker pool.
     */
    fun shutdown() {
        executor.shutdown()
    }
}

