package com.rhett.rhettjs.api

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.threading.WorkerPool
import com.rhett.rhettjs.threading.TickScheduler
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/**
 * Runtime API providing environment information and script lifecycle control.
 *
 * JavaScript usage:
 *   Runtime.env.MAX_WORKER_THREADS  // Number of worker threads
 *   Runtime.env.TICKS_PER_SECOND    // Always 20
 *   Runtime.env.IS_DEBUG            // Debug mode enabled
 *   Runtime.env.MOD_VERSION         // RhettJS version
 *
 *   Runtime.exit()                  // Stop script execution (cancels pending tasks)
 */
object RuntimeAPI {

    // Flag to track if exit was requested
    @Volatile
    private var exitRequested = false

    // Event loop timeout in milliseconds (default 60 seconds)
    @Volatile
    private var eventLoopTimeout = 60000L

    /**
     * Get the environment object containing runtime constants.
     * Called from JavaScript as Runtime.env
     */
    fun getEnv(): Map<String, Any> {
        return mapOf(
            "MAX_WORKER_THREADS" to WorkerPool.getWorkerCount(),
            "TICKS_PER_SECOND" to 20,
            "IS_DEBUG" to ConfigManager.isDebugEnabled(),
            "RJS_VERSION" to RhettJSCommon.RJS_VERSION
        )
    }

    /**
     * Request script execution to stop.
     * Cancels all pending scheduled tasks and prevents new ones.
     * 
     * Note: In-flight worker tasks will complete, but their callbacks won't execute.
     */
    fun exit() {
        exitRequested = true
        TickScheduler.cancelAll()
        RhettJSCommon.LOGGER.info("[RhettJS] Runtime.exit() called - script execution stopped")
    }

    /**
     * Check if exit has been requested.
     * Used internally to prevent scheduling new tasks after exit.
     */
    fun isExitRequested(): Boolean = exitRequested

    /**
     * Reset exit flag. Called when scripts are reloaded.
     */
    fun reset() {
        exitRequested = false
        eventLoopTimeout = 60000L // Reset to default
    }

    /**
     * Set the event loop timeout for the current script execution.
     * Must be called before any async operations (task/wait).
     * Default: 60000ms (60 seconds)
     *
     * WARNING: Setting this too high can cause server hangs if scripts don't complete.
     * Use with caution.
     *
     * @param timeoutMs Maximum time to wait for async operations in milliseconds
     */
    fun setEventLoopTimeout(timeoutMs: Long) {
        if (timeoutMs < 1000) {
            throw IllegalArgumentException("Event loop timeout must be at least 1000ms (1 second)")
        }
        eventLoopTimeout = timeoutMs
        RhettJSCommon.LOGGER.info("[RhettJS] Event loop timeout set to ${timeoutMs}ms")
    }

    /**
     * Get the current event loop timeout.
     */
    fun getEventLoopTimeout(): Long = eventLoopTimeout

    /**
     * Create a JavaScript-friendly Runtime object with env as a property.
     */
    fun createJSObject(scope: Scriptable): Scriptable {
        val cx = Context.getCurrentContext()
        val runtime = cx.newObject(scope)

        // Create env object with constants
        val env = cx.newObject(scope)
        ScriptableObject.putProperty(env, "MAX_WORKER_THREADS", WorkerPool.getWorkerCount())
        ScriptableObject.putProperty(env, "TICKS_PER_SECOND", 20)
        ScriptableObject.putProperty(env, "IS_DEBUG", ConfigManager.isDebugEnabled())
        ScriptableObject.putProperty(env, "RJS_VERSION", RhettJSCommon.RJS_VERSION)

        // Add env to Runtime
        ScriptableObject.putProperty(runtime, "env", env)

        // Add exit function
        val exitFunc = object : org.mozilla.javascript.BaseFunction() {
            override fun call(
                cx: Context,
                scope: Scriptable,
                thisObj: Scriptable?,
                args: Array<Any?>
            ): Any {
                exit()
                return Context.getUndefinedValue()
            }
        }
        ScriptableObject.putProperty(runtime, "exit", exitFunc)

        // Add setEventLoopTimeout function
        val setTimeoutFunc = object : org.mozilla.javascript.BaseFunction() {
            override fun call(
                cx: Context,
                scope: Scriptable,
                thisObj: Scriptable?,
                args: Array<Any?>
            ): Any {
                if (args.isEmpty()) {
                    throw org.mozilla.javascript.ScriptRuntime.typeError("setEventLoopTimeout() requires a timeout argument (number in milliseconds)")
                }
                val timeoutMs = when (val arg = args[0]) {
                    is Number -> arg.toLong()
                    else -> throw org.mozilla.javascript.ScriptRuntime.typeError("setEventLoopTimeout() argument must be a number (milliseconds)")
                }

                try {
                    setEventLoopTimeout(timeoutMs)
                } catch (e: IllegalArgumentException) {
                    // Convert Java exception to JavaScript-friendly error
                    throw org.mozilla.javascript.ScriptRuntime.typeError(e.message ?: "Invalid timeout value")
                }

                return Context.getUndefinedValue()
            }
        }
        ScriptableObject.putProperty(runtime, "setEventLoopTimeout", setTimeoutFunc)

        return runtime
    }
}

