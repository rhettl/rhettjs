package com.rhett.rhettjs.engine.api

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.engine.ScriptExitException
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject

/**
 * Runtime API for JavaScript scripts.
 * Provides Runtime.env properties and Runtime.exit() / Runtime.setScriptTimeout() functions
 *
 * Relocated from: GraalEngine.kt (lines 498-532)
 * Date: 2026-01-12
 */
object RuntimeAPI {

    // Script execution timeout (mutable per-script via Runtime.setScriptTimeout)
    @Volatile
    var scriptTimeoutMs: Long = 30000L // Default: 30 seconds

    /**
     * Create the Runtime API for JavaScript scripts.
     * Provides Runtime.env properties and Runtime.exit() / Runtime.setScriptTimeout() functions
     */
    fun create(context: Context): ProxyObject {
        // Create Runtime.env object with environment constants
        val env = ProxyObject.fromMap(mapOf(
            "TICKS_PER_SECOND" to 20,
            "IS_DEBUG" to ConfigManager.isDebugEnabled(),
            "RJS_VERSION" to RhettJSCommon.RJS_VERSION
        ))

        // Create Runtime object with env and functions
        return ProxyObject.fromMap(mapOf(
            "env" to env,
            "exit" to ProxyExecutable { _ ->
                throw ScriptExitException()
            },
            "setScriptTimeout" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("setScriptTimeout() requires a timeout argument (number in milliseconds)")
                }

                val timeoutMs = when {
                    args[0].isNumber -> args[0].asLong()
                    else -> throw IllegalArgumentException("setScriptTimeout() argument must be a number (milliseconds)")
                }

                if (timeoutMs < 1000L) {
                    throw IllegalArgumentException("Script timeout must be at least 1000ms (1 second), got ${timeoutMs}ms")
                }

                scriptTimeoutMs = timeoutMs
                RhettJSCommon.LOGGER.info("[RhettJS] Script timeout set to ${timeoutMs}ms")
                null
            }
        ))
    }
}
