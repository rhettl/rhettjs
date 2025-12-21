package com.rhett.rhettjs.api

import com.rhett.rhettjs.threading.ThreadSafeAPI
import com.rhett.rhettjs.threading.WorkerPool
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptRuntime

/**
 * JavaScript-callable wrapper for task() function.
 *
 * Usage from JavaScript:
 *   task(fn, ...args) → Promise
 *
 * Executes function on worker thread. Returns Promise that resolves
 * on the server thread when the worker completes.
 */
class TaskAPI : BaseFunction() {

    override fun call(
        cx: Context,
        scope: Scriptable,
        thisObj: Scriptable?,
        args: Array<Any?>
    ): Any? {
        // Validate arguments
        if (args.isEmpty()) {
            throw ScriptRuntime.typeError("task() requires at least a callback function")
        }

        val callback = args[0]
        if (callback !is Function) {
            throw ScriptRuntime.typeError("First argument to task() must be a function")
        }

        // Extract callback arguments (everything after first arg)
        val callbackArgs = if (args.size > 1) {
            args.copyOfRange(1, args.size)
        } else {
            emptyArray()
        }

        // Validate arguments (no Java objects allowed)
        validateArguments(callbackArgs)

        // Check if we're already on a worker thread
        val threadName = Thread.currentThread().name
        val isWorkerThread = threadName.contains("RhettJS-Worker") || threadName.contains("pool")

        if (isWorkerThread) {
            // Already on worker thread - execute immediately and return resolved Promise
            return executeImmediately(cx, scope, callback, callbackArgs)
        } else {
            // On main thread - spawn worker and return Promise
            return createPromise(cx, scope, callback, callbackArgs)
        }
    }

    override fun getFunctionName(): String = "task"

    /**
     * Execute callback immediately on current thread and return resolved Promise.
     * Used when task() is called from within a worker thread (nested task).
     */
    private fun executeImmediately(
        cx: Context,
        scope: Scriptable,
        callback: Function,
        callbackArgs: Array<Any?>
    ): Scriptable {
        return try {
            val result = callback.call(cx, scope, scope, callbackArgs)
            val promiseResolve = cx.evaluateString(scope, "Promise.resolve", "resolve", 1, null) as Function
            promiseResolve.call(cx, scope, scope, arrayOf(result)) as Scriptable
        } catch (e: Exception) {
            val promiseReject = cx.evaluateString(scope, "Promise.reject", "reject", 1, null) as Function
            val errorObj = Context.javaToJS(e, scope)
            promiseReject.call(cx, scope, scope, arrayOf(errorObj)) as Scriptable
        }
    }

    /**
     * Create a Promise that resolves when the worker task completes.
     */
    private fun createPromise(
        cx: Context,
        scope: Scriptable,
        callback: Function,
        callbackArgs: Array<Any?>
    ): Scriptable {
        // Create a container object to hold resolve/reject functions
        val container = cx.newObject(scope)

        // Create a Promise using JavaScript's Promise constructor
        val promiseScript = """
            (function(container) {
                return new Promise(function(resolve, reject) {
                    container.resolve = resolve;
                    container.reject = reject;
                });
            })
        """.trimIndent()

        val promiseFactory = cx.evaluateString(scope, promiseScript, "task-promise-factory", 1, null) as Function
        val promise = promiseFactory.call(cx, scope, scope, arrayOf(container)) as Scriptable

        // Get the resolve and reject functions from the container
        val resolve = container.get("resolve", container) as Function
        val reject = container.get("reject", container) as Function

        // Auto-discover thread-safe APIs from scope
        val threadSafeAPIs = getThreadSafeAPIs(scope)

        // Submit to worker pool
        WorkerPool.submit(callback, callbackArgs, resolve, reject, scope, threadSafeAPIs)

        return promise
    }

    /**
     * Auto-discover thread-safe APIs from scope.
     */
    private fun getThreadSafeAPIs(scope: Scriptable): Map<String, Any> {
        val apis = mutableMapOf<String, Any>()
        var current: Scriptable? = scope

        while (current != null) {
            for (id in current.ids) {
                val name = id.toString()
                if (apis.containsKey(name)) continue

                try {
                    val value = current.get(name, current)
                    if (value != Scriptable.NOT_FOUND && value is ThreadSafeAPI) {
                        apis[name] = value
                    }
                } catch (_: Exception) {
                    // Skip properties that can't be read
                }
            }
            current = current.parentScope
        }

        return apis
    }

    /**
     * Validate that arguments don't contain Java objects.
     */
    private fun validateArguments(args: Array<Any?>) {
        for ((index, arg) in args.withIndex()) {
            val isValid = when (arg) {
                null -> true
                is String, is Number, is Boolean -> true
                is Scriptable -> true
                org.mozilla.javascript.Undefined.instance -> true  // Allow Rhino's Undefined
                else -> false
            }

            if (!isValid) {
                throw ScriptRuntime.typeError(
                    "Argument at index $index is a Java object (${arg!!.javaClass.simpleName}). " +
                    "task() arguments must be primitives or JavaScript objects."
                )
            }
        }
    }
}
