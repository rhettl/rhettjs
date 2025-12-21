package com.rhett.rhettjs

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Common initialization class for RhettJS mod.
 * Platform-specific modules (Fabric/NeoForge) call this during initialization.
 */
object RhettJSCommon {
    const val MOD_ID = "rhettjs"
    const val RJS_VERSION = "0.1.0"  // TODO: Read from build properties

    @JvmField
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    @JvmStatic
    fun init() {
        LOGGER.info("RhettJS initializing...")
    }
}
