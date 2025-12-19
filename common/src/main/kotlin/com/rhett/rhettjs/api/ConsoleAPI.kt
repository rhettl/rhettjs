package com.rhett.rhettjs.api

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.threading.ThreadSafeAPI
import org.mozilla.javascript.*

/**
 * Console API for JavaScript scripts.
 * Available in all contexts (main thread, workers).
 *
 * Thread-safe: Only performs logging operations.
 */
class ConsoleAPI : ThreadSafeAPI {
    fun log(vararg args: Any?) {
        val message = args.joinToString(" ") { formatValue(it) }
        RhettJSCommon.LOGGER.info("[RhettJS] $message")
    }

    fun warn(vararg args: Any?) {
        val message = args.joinToString(" ") { formatValue(it) }
        RhettJSCommon.LOGGER.warn("[RhettJS] $message")
    }

    fun error(vararg args: Any?) {
        val message = args.joinToString(" ") { formatValue(it) }
        RhettJSCommon.LOGGER.error("[RhettJS] $message")
    }

    /**
     * Format a value for console output.
     * Handles Rhino JavaScript types (arrays, objects, etc.) properly.
     */
    private fun formatValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is Undefined -> "undefined"
            is NativeArray -> {
                // Convert JavaScript array to string representation
                val length = value.length
                val elements = (0 until length).map { index ->
                    val element = value.get(index.toInt(), value)
                    if (element == Scriptable.NOT_FOUND || element == UniqueTag.NOT_FOUND) {
                        "undefined"
                    } else {
                        formatValue(element)
                    }
                }
                "[${elements.joinToString(", ")}]"
            }
            is NativeObject -> {
                // Convert JavaScript object to string representation
                val ids = value.ids
                val entries = ids.mapNotNull { id ->
                    val key = id.toString()
                    val propValue = value.get(key, value)
                    if (propValue != Scriptable.NOT_FOUND && propValue != UniqueTag.NOT_FOUND) {
                        "$key: ${formatValue(propValue)}"
                    } else {
                        null
                    }
                }
                "{${entries.joinToString(", ")}}"
            }
            is Scriptable -> {
                // Other Rhino scriptable objects
                value.toString()
            }
            is String -> value
            else -> value.toString()
        }
    }
}
