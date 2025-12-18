package com.rhett.rhettjs.api

import com.rhett.rhettjs.threading.TaskFunction
import com.rhett.rhettjs.threading.TickScheduler
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable

/**
 * JavaScript-callable wrapper for task() function.
 *
 * Usage from JavaScript:
 *   task(callback, ...args)
 *   task.wait(ticks, callback, ...args)
 *
 * Executes callback on worker thread with optional arguments.
 * task.wait() schedules callback on main thread, then re-launches on worker thread.
 */
class TaskAPI : BaseFunction() {

    private val taskFunction = TaskFunction()

    init {
        // Add task.wait() method
        put("wait", this, TaskWaitFunction(this))
    }

    override fun call(
        cx: Context,
        scope: Scriptable,
        thisObj: Scriptable?,
        args: Array<Any?>
    ): Any? {
        // Validate arguments
        if (args.isEmpty()) {
            throw IllegalArgumentException("task() requires at least a callback function")
        }

        val callback = args[0]
        if (callback !is Scriptable) {
            throw IllegalArgumentException("First argument to task() must be a function")
        }

        // Extract callback arguments (everything after first arg)
        val callbackArgs = if (args.size > 1) {
            args.copyOfRange(1, args.size)
        } else {
            emptyArray()
        }

        // Execute on worker thread
        taskFunction.execute(scope, callback, callbackArgs)

        // task() returns undefined
        return Context.getUndefinedValue()
    }

    override fun getFunctionName(): String = "task"

    /**
     * Nested function for task.wait().
     * Schedules a delay on main thread, then re-launches callback on worker thread.
     */
    private class TaskWaitFunction(private val taskApi: TaskAPI) : BaseFunction() {

        override fun call(
            cx: Context,
            scope: Scriptable,
            thisObj: Scriptable?,
            args: Array<Any?>
        ): Any? {
            // Validate arguments
            if (args.size < 2) {
                throw IllegalArgumentException("task.wait() requires ticks and callback")
            }

            // Extract ticks
            val ticks = when (val ticksArg = args[0]) {
                is Number -> ticksArg.toInt()
                else -> throw IllegalArgumentException("First argument to task.wait() must be a number (ticks)")
            }

            // Extract callback
            val callback = args[1]
            if (callback !is Function) {
                throw IllegalArgumentException("Second argument to task.wait() must be a function")
            }

            // Extract callback arguments (everything after first two args)
            val callbackArgs = if (args.size > 2) {
                args.copyOfRange(2, args.size)
            } else {
                emptyArray()
            }

            // Schedule on main thread, then re-launch on worker thread
            TickScheduler.getScheduleFunction().schedule(
                ticks = ticks,
                scope = scope,
                callback = object : BaseFunction() {
                    override fun call(
                        cx: Context,
                        scope: Scriptable,
                        thisObj: Scriptable?,
                        args: Array<Any?>
                    ): Any? {
                        // Now on main thread - re-launch on worker thread
                        taskApi.taskFunction.execute(scope, callback, args)
                        return Context.getUndefinedValue()
                    }
                },
                args = callbackArgs
            )

            // task.wait() returns undefined
            return Context.getUndefinedValue()
        }

        override fun getFunctionName(): String = "wait"
    }
}
