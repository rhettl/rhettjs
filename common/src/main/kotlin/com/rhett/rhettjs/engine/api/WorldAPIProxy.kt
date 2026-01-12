package com.rhett.rhettjs.engine.api

import com.rhett.rhettjs.engine.PromiseHelpers
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject

/**
 * World API proxy for JavaScript scripts.
 * Provides block, entity, player, time/weather operations.
 * All methods return Promises except for the dimensions property.
 *
 * Relocated from: GraalEngine.kt (lines 1323-1469)
 * Date: 2026-01-12
 */
object WorldAPIProxy {

    /**
     * Create World API proxy for JavaScript.
     * All methods return Promises except for the dimensions property.
     * Delegates to WorldManager for actual implementation.
     */
    fun create(context: Context): ProxyObject {
        // Create methods map
        val methods = mapOf(
            // Block operations (async) - delegate to WorldManager
            "getBlock" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "getBlock() requires a position")
                }
                PromiseHelpers.convertFutureToPromise<Value>(context, com.rhett.rhettjs.world.WorldManager.getBlock(args[0]))
            },
            "getBlockEntity" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "getBlockEntity() requires a position")
                }
                PromiseHelpers.convertFutureToPromise<Value?>(context, com.rhett.rhettjs.world.WorldManager.getBlockEntity(args[0]))
            },
            "setBlock" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "setBlock() requires position and blockId")
                }
                val position = args[0]
                val blockId = args[1].asString()
                val properties = if (args.size > 2) args[2] else null
                PromiseHelpers.convertFutureToPromise<Void>(context, com.rhett.rhettjs.world.WorldManager.setBlock(position, blockId, properties))
            },
            "fill" to ProxyExecutable { args ->
                if (args.size < 3) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "fill() requires pos1, pos2, and blockId")
                }
                val pos1 = args[0]
                val pos2 = args[1]
                val blockId = args[2].asString()
                val options = if (args.size > 3) args[3] else null
                PromiseHelpers.convertFutureToPromise<Int>(context, com.rhett.rhettjs.world.WorldManager.fill(pos1, pos2, blockId, options))
            },
            "replace" to ProxyExecutable { args ->
                if (args.size < 4) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "replace() requires pos1, pos2, filter, and replacement")
                }
                // TODO: Implement replace operation in WorldManager
                PromiseHelpers.createRejectedPromise(context, "World.replace() not yet implemented")
            },

            // Entity operations (async) - delegate to WorldManager
            "getEntities" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "getEntities() requires position and radius")
                }
                val position = args[0]
                val radius = args[1].asDouble()
                PromiseHelpers.convertFutureToPromise<List<Value>>(context, com.rhett.rhettjs.world.WorldManager.getEntities(position, radius))
            },
            "spawnEntity" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "spawnEntity() requires position and entityId")
                }
                val position = args[0]
                val entityId = args[1].asString()
                val nbt = if (args.size > 2) args[2] else null
                PromiseHelpers.convertFutureToPromise<Value>(context, com.rhett.rhettjs.world.WorldManager.spawnEntity(position, entityId, nbt))
            },

            // Player operations (async) - delegate to WorldManager
            "getPlayers" to ProxyExecutable { args ->
                PromiseHelpers.convertFutureToPromise<List<Value>>(context, com.rhett.rhettjs.world.WorldManager.getPlayers())
            },
            "getPlayer" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "getPlayer() requires name or UUID")
                }
                val nameOrUuid = args[0].asString()
                PromiseHelpers.convertFutureToPromise<Value?>(context, com.rhett.rhettjs.world.WorldManager.getPlayer(nameOrUuid))
            },

            // Time/Weather operations (async) - delegate to WorldManager
            "getTime" to ProxyExecutable { args ->
                val dimension = if (args.isNotEmpty()) args[0].asString() else null
                PromiseHelpers.convertFutureToPromise<Long>(context, com.rhett.rhettjs.world.WorldManager.getTime(dimension))
            },
            "setTime" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "setTime() requires time value")
                }
                val time = args[0].asLong()
                val dimension = if (args.size > 1) args[1].asString() else null
                PromiseHelpers.convertFutureToPromise<Void>(context, com.rhett.rhettjs.world.WorldManager.setTime(time, dimension))
            },
            "getWeather" to ProxyExecutable { args ->
                val dimension = if (args.isNotEmpty()) args[0].asString() else null
                PromiseHelpers.convertFutureToPromise<String>(context, com.rhett.rhettjs.world.WorldManager.getWeather(dimension))
            },
            "setWeather" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "setWeather() requires weather type")
                }
                val weather = args[0].asString()
                val dimension = if (args.size > 1) args[1].asString() else null
                PromiseHelpers.convertFutureToPromise<Void>(context, com.rhett.rhettjs.world.WorldManager.setWeather(weather, dimension))
            },

            // Dimension bounds queries
            "getDimensionBounds" to ProxyExecutable { args ->
                val dimension = if (args.isNotEmpty()) args[0].asString() else null
                PromiseHelpers.convertFutureToPromise<Value>(context, com.rhett.rhettjs.world.WorldManager.getDimensionBounds(dimension))
            },
            "getFilledBounds" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "getFilledBounds() requires pos1 and pos2")
                }
                val pos1 = args[0]
                val pos2 = args[1]
                val dimension = if (args.size > 2) args[2].asString() else null
                PromiseHelpers.convertFutureToPromise<Value?>(context, com.rhett.rhettjs.world.WorldManager.getFilledBounds(pos1, pos2, dimension))
            },
            "removeEntities" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "removeEntities() requires pos1 and pos2")
                }
                val pos1 = args[0]
                val pos2 = args[1]
                val options = if (args.size > 2) args[2] else null
                PromiseHelpers.convertFutureToPromise<Int>(context, com.rhett.rhettjs.world.WorldManager.removeEntities(pos1, pos2, options))
            }
        )

        // Return a custom ProxyObject that dynamically fetches dimensions
        return object : ProxyObject {
            override fun getMember(key: String?): Any? {
                return when (key) {
                    "dimensions" -> com.rhett.rhettjs.world.WorldManager.getDimensions()
                    else -> methods[key]
                }
            }

            override fun getMemberKeys(): Any = (methods.keys + "dimensions").toTypedArray()

            override fun hasMember(key: String?): Boolean {
                return key == "dimensions" || methods.containsKey(key)
            }

            override fun putMember(key: String?, value: Value?) {
                // Read-only proxy
            }
        }
    }
}
