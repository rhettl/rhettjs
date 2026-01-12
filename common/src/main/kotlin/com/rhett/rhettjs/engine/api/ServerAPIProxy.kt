package com.rhett.rhettjs.engine.api

import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject

/**
 * Server API proxy for JavaScript scripts.
 * Provides event system (on/off/once), server properties, and broadcast methods.
 *
 * Relocated from: GraalEngine.kt (lines 1552-1616)
 * Date: 2026-01-12
 */
object ServerAPIProxy {

    /**
     * Create Server API proxy for JavaScript.
     * Provides event system (on/off/once), server properties, and broadcast methods.
     * Delegates to ServerEventManager for actual implementation.
     */
    fun create(): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            // Event types enumeration
            "eventTypes" to ProxyObject.fromMap(com.rhett.rhettjs.events.ServerEventManager.getEventTypes()),

            // Event registration - delegate to ServerEventManager
            "on" to ProxyExecutable { args ->
                if (args.size < 2) {
                    throw IllegalArgumentException("on() requires event name and handler function")
                }
                val event = args[0].asString()
                val handler = args[1]

                com.rhett.rhettjs.events.ServerEventManager.on(event, handler)
                null
            },

            "off" to ProxyExecutable { args ->
                if (args.size < 2) {
                    throw IllegalArgumentException("off() requires event name and handler function")
                }
                val event = args[0].asString()
                val handler = args[1]

                com.rhett.rhettjs.events.ServerEventManager.off(event, handler)
                null
            },

            "once" to ProxyExecutable { args ->
                if (args.size < 2) {
                    throw IllegalArgumentException("once() requires event name and handler function")
                }
                val event = args[0].asString()
                val handler = args[1]

                com.rhett.rhettjs.events.ServerEventManager.once(event, handler)
                null
            },

            // Server properties - delegate to ServerEventManager for real values
            "tps" to com.rhett.rhettjs.events.ServerEventManager.getServerTPS(),
            "players" to com.rhett.rhettjs.events.ServerEventManager.getOnlinePlayers(),
            "maxPlayers" to com.rhett.rhettjs.events.ServerEventManager.getMaxPlayers(),
            "motd" to com.rhett.rhettjs.events.ServerEventManager.getMOTD(),

            // Server methods - delegate to ServerEventManager
            "broadcast" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("broadcast() requires a message")
                }
                val message = args[0].asString()
                com.rhett.rhettjs.events.ServerEventManager.broadcast(message)
                null
            },

            "runCommand" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("runCommand() requires a command string")
                }
                val command = args[0].asString()
                com.rhett.rhettjs.events.ServerEventManager.runCommand(command)
                null
            }
        ))
    }
}
