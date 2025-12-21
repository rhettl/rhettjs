package com.rhett.rhettjs.api

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.threading.ThreadSafeAPI

/**
 * Logger API for JavaScript scripts.
 * Available in all contexts (main thread, workers).
 *
 * Thread-safe: Only performs logging operations.
 */
class LoggerAPI : ThreadSafeAPI {
    fun info(message: String) {
        RhettJSCommon.LOGGER.info("[RhettJS] $message")
    }

    fun warn(message: String) {
        RhettJSCommon.LOGGER.warn("[RhettJS] $message")
    }

    // Explicit overloads for JavaScript/Rhino visibility
    fun error(message: String) {
        RhettJSCommon.LOGGER.error("[RhettJS] $message")
    }

    fun error(message: String, error: Any?) {
        if (error != null && error is Throwable) {
            RhettJSCommon.LOGGER.error("[RhettJS] $message", error)
        } else {
            RhettJSCommon.LOGGER.error("[RhettJS] $message")
        }
    }

    fun debug(message: String) {
        if (ConfigManager.isDebugEnabled()) {
            RhettJSCommon.LOGGER.info("[RhettJS] [DEBUG] $message")
        }
    }
}
