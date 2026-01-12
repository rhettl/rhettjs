package com.rhett.rhettjs.engine.api

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.config.ConfigManager
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject

/**
 * Console API for JavaScript scripts.
 * Provides console.log, console.warn, console.error, console.info, console.debug
 *
 * Relocated from: GraalEngine.kt (lines 441-493)
 * Date: 2026-01-12
 */
object ConsoleAPI {

    /**
     * Create the Console API for JavaScript scripts.
     * Provides console.log, console.warn, console.error, console.info, console.debug
     */
    fun create(): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            "log" to ProxyExecutable { args ->
                val message = args.joinToString(" ") { formatValue(it) }
                RhettJSCommon.LOGGER.info("[RhettJS-Script] $message")
                null
            },
            "warn" to ProxyExecutable { args ->
                val message = args.joinToString(" ") { formatValue(it) }
                RhettJSCommon.LOGGER.warn("[RhettJS-Script] $message")
                null
            },
            "error" to ProxyExecutable { args ->
                val message = args.joinToString(" ") { formatValue(it) }
                RhettJSCommon.LOGGER.error("[RhettJS-Script] $message")
                null
            },
            "info" to ProxyExecutable { args ->
                val message = args.joinToString(" ") { formatValue(it) }
                RhettJSCommon.LOGGER.info("[RhettJS-Script] $message")
                null
            },
            "debug" to ProxyExecutable { args ->
                val message = args.joinToString(" ") { formatValue(it) }
                ConfigManager.debug("[Script] $message")
                null
            }
        ))
    }

    /**
     * Format a GraalVM Value for console output.
     * Handles primitives, objects, arrays, etc.
     */
    private fun formatValue(value: Value): String {
        return when {
            value.isNull -> "null"
            value.isString -> value.asString()
            value.isNumber -> value.toString()
            value.isBoolean -> value.asBoolean().toString()
            value.hasArrayElements() -> {
                val elements = (0 until value.arraySize).map { formatValue(value.getArrayElement(it)) }
                "[${elements.joinToString(", ")}]"
            }
            value.hasMembers() -> {
                val members = value.memberKeys.map { key ->
                    "$key: ${formatValue(value.getMember(key))}"
                }
                "{${members.joinToString(", ")}}"
            }
            else -> value.toString()
        }
    }
}
