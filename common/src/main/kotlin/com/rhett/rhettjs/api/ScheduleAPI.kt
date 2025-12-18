package com.rhett.rhettjs.api

import com.rhett.rhettjs.threading.ScheduleFunction
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable

/**
 * JavaScript-callable wrapper for schedule() function.
 *
 * Usage from JavaScript:
 *   schedule(ticks, callback, ...args)
 *
 * Schedules callback to execute on main thread after specified ticks.
 * Ticks are automatically clamped to minimum of 1.
 */
class ScheduleAPI(
    internal val scheduleFunction: ScheduleFunction
) : BaseFunction() {

    override fun call(
        cx: Context,
        scope: Scriptable,
        thisObj: Scriptable?,
        args: Array<Any?>
    ): Any? {
        // Validate arguments
        if (args.size < 2) {
            throw IllegalArgumentException("schedule() requires at least ticks and callback: schedule(ticks, callback, ...args)")
        }

        // Extract ticks (first argument)
        val ticks = when (val ticksArg = args[0]) {
            is Number -> ticksArg.toInt()
            else -> throw IllegalArgumentException("First argument to schedule() must be a number (ticks)")
        }

        // Extract callback (second argument)
        val callback = args[1]
        if (callback !is Scriptable) {
            throw IllegalArgumentException("Second argument to schedule() must be a function")
        }

        // Extract callback arguments (everything after first two args)
        val callbackArgs = if (args.size > 2) {
            args.copyOfRange(2, args.size)
        } else {
            emptyArray()
        }

        // Schedule on main thread
        scheduleFunction.schedule(ticks, scope, callback, callbackArgs)

        // schedule() returns undefined
        return Context.getUndefinedValue()
    }

    override fun getFunctionName(): String = "schedule"
}
