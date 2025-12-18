package com.rhett.rhettjs.api

import com.rhett.rhettjs.RhettJSCommon

/**
 * Console API for JavaScript scripts.
 * Available in all contexts (main thread, workers).
 */
class ConsoleAPI {
    fun log(vararg args: Any?) {
        val message = args.joinToString(" ")
        RhettJSCommon.LOGGER.info("[RhettJS] $message")
    }

    fun warn(vararg args: Any?) {
        val message = args.joinToString(" ")
        RhettJSCommon.LOGGER.warn("[RhettJS] $message")
    }

    fun error(vararg args: Any?) {
        val message = args.joinToString(" ")
        RhettJSCommon.LOGGER.error("[RhettJS] $message")
    }
}
