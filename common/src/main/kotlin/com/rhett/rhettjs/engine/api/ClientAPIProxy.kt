package com.rhett.rhettjs.engine.api

import com.rhett.rhettjs.events.ClientEventManager
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject

/**
 * Client API proxy for JavaScript scripts.
 * Provides client player access, event system (on/off/once), and client-side operations.
 *
 * Pattern: Follows ServerAPIProxy design
 * Date: 2026-01-13
 */
object ClientAPIProxy {

    /**
     * Create Client API proxy for JavaScript.
     * Provides client player access and event system (on/off/once).
     * Delegates to ClientEventManager for actual implementation.
     */
    fun create(context: Context): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            // Event types enumeration
            "eventTypes" to ProxyObject.fromMap(ClientEventManager.getEventTypes()),

            // Client player (readonly, live)
            "player" to ClientEventManager.getClientPlayer(),

            // Event registration - delegate to ClientEventManager
            "on" to ProxyExecutable { args ->
                if (args.size < 2) {
                    throw IllegalArgumentException("on() requires event name and handler function")
                }
                val event = args[0].asString()
                val handler = args[1]

                ClientEventManager.on(event, handler)
                null
            },

            "off" to ProxyExecutable { args ->
                if (args.size < 2) {
                    throw IllegalArgumentException("off() requires event name and handler function")
                }
                val event = args[0].asString()
                val handler = args[1]

                ClientEventManager.off(event, handler)
                null
            },

            "once" to ProxyExecutable { args ->
                if (args.size < 2) {
                    throw IllegalArgumentException("once() requires event name and handler function")
                }
                val event = args[0].asString()
                val handler = args[1]

                ClientEventManager.once(event, handler)
                null
            }
        ))
    }
}
