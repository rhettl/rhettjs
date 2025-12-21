package com.rhett.rhettjs.api

import com.rhett.rhettjs.threading.EventLoop
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptRuntime

/**
 * JavaScript-callable wait() function.
 *
 * Usage from JavaScript:
 *   wait(ticks) → Promise
 *
 * Returns a Promise that resolves after the specified number of game ticks.
 * Only works on the server thread (not in workers).
 *
 * 20 ticks = 1 second
 *
 * WORKER THREAD LIMITATION:
 * Workers cannot use wait() because they don't have access to the game tick counter.
 * If a worker needs to delay execution, it should:
 * 1. Complete its work and return the result
 * 2. Use .thenWait(ticks) on the returned Promise to delay on the server thread
 * 3. Then continue processing in a .then() callback
 *
 * Example:
 *   task(() => {
 *       // Worker: Do CPU-heavy work
 *       return processData(data);
 *   })
 *   .thenWait(20)  // Server thread: Wait 1 second
 *   .then(result => {
 *       // Server thread: Can now access player/world APIs
 *       player.sendMessage("Result: " + result);
 *   });
 */
class WaitAPI : BaseFunction() {

    override fun call(
        cx: Context,
        scope: Scriptable,
        thisObj: Scriptable?,
        args: Array<Any?>
    ): Any {
        // Validate arguments
        if (args.isEmpty()) {
            throw ScriptRuntime.typeError("wait() requires a ticks argument (number)")
        }

        val ticks = when (val ticksArg = args[0]) {
            is Number -> ticksArg.toInt()
            else -> throw ScriptRuntime.typeError("wait() argument must be a number (ticks)")
        }

        // Check if we're on a worker thread
        val threadName = Thread.currentThread().name
        if (threadName.contains("RhettJS-Worker") || threadName.contains("pool")) {
            throw IllegalStateException("wait() cannot be called from a worker thread. Use it on the server thread only.")
        }

        // Get the current event loop
        val eventLoop = EventLoop.getCurrent()
            ?: throw IllegalStateException("wait() can only be called during script execution")

        // Create a Promise that will be resolved by the event loop
        val container = cx.newObject(scope)

        val promiseScript = """
            (function(container) {
                return new Promise(function(resolve, reject) {
                    container.resolve = resolve;
                    container.reject = reject;
                });
            })
        """.trimIndent()

        val promiseFactory = cx.evaluateString(scope, promiseScript, "wait-promise-factory", 1, null) as Function
        val promise = promiseFactory.call(cx, scope, scope, arrayOf(container)) as Scriptable

        val resolve = container.get("resolve", container) as Function

        // Add wait timer to event loop
        eventLoop.addWaitTimer(ticks, resolve, scope)

        return promise
    }

    override fun getFunctionName(): String = "wait"
}

