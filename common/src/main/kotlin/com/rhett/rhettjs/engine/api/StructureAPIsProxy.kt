package com.rhett.rhettjs.engine.api

import com.rhett.rhettjs.engine.PromiseHelpers
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject

/**
 * Structure API proxies for JavaScript scripts.
 * Provides three structure APIs:
 * - StructureNbt: Single .nbt template files
 * - LargeStructureNbt: Multi-chunk .nbt structures (stored in rjs-large/ directories)
 * - WorldgenStructure: Minecraft's worldgen structures (villages, temples, bastions, etc.)
 *
 * Relocated from: GraalEngine.kt (lines 1007-1316)
 * Date: 2026-01-12
 */
object StructureAPIsProxy {

    /**
     * Create StructureNbt API proxy for JavaScript.
     * Handles single .nbt template files.
     * All methods return Promises except for properties.
     * Delegates to StructureNbtManager for actual implementation.
     */
    fun createStructureNbtAPI(context: Context): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            // File operations (async)
            "exists" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "exists() requires a structure name")
                }
                val name = args[0].asString()
                PromiseHelpers.convertFutureToPromise<Boolean>(context, com.rhett.rhettjs.structure.StructureNbtManager.exists(name))
            },
            "list" to ProxyExecutable { args ->
                val namespace = if (args.isNotEmpty()) args[0].asString() else null
                PromiseHelpers.convertFutureToPromise<List<String>>(context, com.rhett.rhettjs.structure.StructureNbtManager.list(namespace))
            },
            "listGenerated" to ProxyExecutable { args ->
                val namespace = if (args.isNotEmpty()) args[0].asString() else null
                PromiseHelpers.convertFutureToPromise<List<String>>(context, com.rhett.rhettjs.structure.StructureNbtManager.listGenerated(namespace))
            },
            "remove" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "remove() requires a structure name")
                }
                val name = args[0].asString()
                PromiseHelpers.convertFutureToPromise<Boolean>(context, com.rhett.rhettjs.structure.StructureNbtManager.remove(name))
            },
            "load" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "load() requires a structure name")
                }
                val name = args[0].asString()
                PromiseHelpers.convertFutureToPromise(context, com.rhett.rhettjs.structure.StructureNbtManager.load(name))
            },
            "save" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "save() requires structure name and data")
                }
                val name = args[0].asString()
                val data = NBTAPI.convertGraalValueToKotlin(args[1])

                if (data !is com.rhett.rhettjs.structure.models.StructureData) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "save() requires valid StructureData object")
                }

                PromiseHelpers.convertFutureToPromise<Void>(context, com.rhett.rhettjs.structure.StructureNbtManager.save(name, data, skipBackup = false))
            },

            // Structure operations (async)
            "capture" to ProxyExecutable { args ->
                if (args.size < 3) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "capture() requires pos1, pos2, and name")
                }
                val pos1 = args[0]
                val pos2 = args[1]
                val name = args[2].asString()
                val options = if (args.size > 3) args[3] else null
                PromiseHelpers.convertFutureToPromise<Void>(context, com.rhett.rhettjs.structure.StructureNbtManager.capture(pos1, pos2, name, options))
            },
            "place" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "place() requires position and name")
                }
                val position = args[0]
                val name = args[1].asString()
                val options = if (args.size > 2) args[2] else null
                PromiseHelpers.convertFutureToPromise<Void>(context, com.rhett.rhettjs.structure.StructureNbtManager.place(position, name, options))
            },
            "getSize" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "getSize() requires a structure name")
                }
                val name = args[0].asString()
                PromiseHelpers.convertFutureToPromise<Map<String, Int>>(context, com.rhett.rhettjs.structure.StructureNbtManager.getSize(name))
            },

            // Block analysis and replacement operations (async)
            "blocksList" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "blocksList() requires a structure name")
                }
                val name = args[0].asString()
                PromiseHelpers.convertFutureToPromise<Map<String, Int>>(context, com.rhett.rhettjs.structure.StructureNbtManager.blocksList(name))
            },
            "blocksNamespaces" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "blocksNamespaces() requires a structure name")
                }
                val name = args[0].asString()
                PromiseHelpers.convertFutureToPromise<List<String>>(context, com.rhett.rhettjs.structure.StructureNbtManager.blocksNamespaces(name))
            },
            "blocksReplace" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "blocksReplace() requires structure name and replacement map")
                }
                val name = args[0].asString()
                val replacementMap = NBTAPI.convertGraalValueToKotlin(args[1]) as? Map<*, *>
                    ?: return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "replacementMap must be an object")

                @Suppress("UNCHECKED_CAST")
                val typedMap = replacementMap as Map<String, String>
                PromiseHelpers.convertFutureToPromise<Void>(context, com.rhett.rhettjs.structure.StructureNbtManager.blocksReplace(name, typedMap))
            },

            // Backup and restore operations (async)
            "listBackups" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "listBackups() requires a structure name")
                }
                val name = args[0].asString()
                PromiseHelpers.convertFutureToPromise<List<String>>(context, com.rhett.rhettjs.structure.StructureNbtManager.listBackups(name))
            },
            "restoreBackup" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "restoreBackup() requires a structure name")
                }
                val name = args[0].asString()
                val timestamp = if (args.size > 1 && !args[1].isNull) args[1].asString() else null
                PromiseHelpers.convertFutureToPromise<Void>(context, com.rhett.rhettjs.structure.StructureNbtManager.restoreBackup(name, timestamp))
            }
        ))
    }

    /**
     * Create LargeStructureNbt API proxy for JavaScript.
     * Handles multi-chunk .nbt structures stored in rjs-large/ directories.
     * All methods return Promises except for properties.
     * Delegates to LargeStructureNbtManager for actual implementation.
     */
    fun createLargeStructureNbtAPI(context: Context): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            // Structure operations (async)
            "capture" to ProxyExecutable { args ->
                if (args.size < 3) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "capture() requires pos1, pos2, and name")
                }
                val pos1 = args[0]
                val pos2 = args[1]
                val name = args[2].asString()
                val options = if (args.size > 3) args[3] else null
                PromiseHelpers.convertFutureToPromise<Void>(context, com.rhett.rhettjs.structure.LargeStructureNbtManager.capture(pos1, pos2, name, options))
            },
            "place" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "place() requires position and name")
                }
                val position = args[0]
                val name = args[1].asString()
                val options = if (args.size > 2) args[2] else null
                PromiseHelpers.convertFutureToPromise<Void>(context, com.rhett.rhettjs.structure.LargeStructureNbtManager.place(position, name, options))
            },
            "getSize" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "getSize() requires a structure name")
                }
                val name = args[0].asString()
                PromiseHelpers.convertFutureToPromise<Map<String, Int>>(context, com.rhett.rhettjs.structure.LargeStructureNbtManager.getSize(name))
            },
            "list" to ProxyExecutable { args ->
                val namespace = if (args.isNotEmpty()) args[0].asString() else null
                PromiseHelpers.convertFutureToPromise<List<String>>(context, com.rhett.rhettjs.structure.LargeStructureNbtManager.list(namespace))
            },
            "remove" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "remove() requires a structure name")
                }
                val name = args[0].asString()
                PromiseHelpers.convertFutureToPromise<Boolean>(context, com.rhett.rhettjs.structure.LargeStructureNbtManager.remove(name))
            },

            // Block operations (async)
            "blocksReplace" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "blocksReplace() requires structure name and replacement map")
                }
                val name = args[0].asString()
                val replacementMap = NBTAPI.convertGraalValueToKotlin(args[1]) as? Map<*, *>
                    ?: return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "replacementMap must be an object")

                @Suppress("UNCHECKED_CAST")
                val typedMap = replacementMap as Map<String, String>
                PromiseHelpers.convertFutureToPromise<Void>(context, com.rhett.rhettjs.structure.LargeStructureNbtManager.blocksReplace(name, typedMap))
            },

            // Backup and restore operations (async)
            "listBackups" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "listBackups() requires a structure name")
                }
                val name = args[0].asString()
                PromiseHelpers.convertFutureToPromise<List<String>>(context, com.rhett.rhettjs.structure.LargeStructureNbtManager.listBackups(name))
            },
            "restoreBackup" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "restoreBackup() requires a structure name")
                }
                val name = args[0].asString()
                val timestamp = if (args.size > 1 && !args[1].isNull) args[1].asString() else null
                PromiseHelpers.convertFutureToPromise<Void>(context, com.rhett.rhettjs.structure.LargeStructureNbtManager.restoreBackup(name, timestamp))
            }
        ))
    }

    /**
     * Create WorldgenStructure API proxy for JavaScript.
     * Handles Minecraft's worldgen structures (villages, temples, bastions, etc.).
     * All methods return Promises.
     * Delegates to WorldgenStructureManager for actual implementation.
     */
    fun createWorldgenStructureAPI(context: Context): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            "list" to ProxyExecutable { args ->
                val namespace = if (args.isNotEmpty() && !args[0].isNull) args[0].asString() else null
                PromiseHelpers.convertFutureToPromise<List<String>>(context, com.rhett.rhettjs.structure.WorldgenStructureManager.list(namespace))
            },
            "exists" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "exists() requires a structure name")
                }
                val name = args[0].asString()
                PromiseHelpers.convertFutureToPromise<Boolean>(context, com.rhett.rhettjs.structure.WorldgenStructureManager.exists(name))
            },
            "info" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "info() requires a structure name")
                }
                val name = args[0].asString()
                PromiseHelpers.convertFutureToPromise<Map<String, Any?>>(context, com.rhett.rhettjs.structure.WorldgenStructureManager.info(name))
            },
            "place" to ProxyExecutable { args ->
                if (args.size < 2) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "place() requires a structure name and options object")
                }
                val name = args[0].asString()
                val options = args[1]

                // Extract options
                val x = if (options.hasMember("x")) options.getMember("x").asInt() else 0
                val z = if (options.hasMember("z")) options.getMember("z").asInt() else 0
                val dimension = if (options.hasMember("dimension") && !options.getMember("dimension").isNull) {
                    options.getMember("dimension").asString()
                } else null
                val seed = if (options.hasMember("seed") && !options.getMember("seed").isNull) {
                    options.getMember("seed").asLong()
                } else null
                val surface = if (options.hasMember("surface") && !options.getMember("surface").isNull) {
                    options.getMember("surface").asString()
                } else null
                val rotation = if (options.hasMember("rotation") && !options.getMember("rotation").isNull) {
                    options.getMember("rotation").asString()
                } else null
                val simulateBearding = if (options.hasMember("simulateBearding") && !options.getMember("simulateBearding").isNull) {
                    options.getMember("simulateBearding").asString()
                } else null

                PromiseHelpers.convertFutureToPromise<Map<String, Any?>>(
                    context,
                    com.rhett.rhettjs.structure.WorldgenStructureManager.place(name, x, z, dimension, seed, surface, rotation, simulateBearding)
                )
            },
            "placeJigsaw" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "placeJigsaw() requires an options object")
                }
                val options = args[0]

                // Extract required options
                if (!options.hasMember("pool")) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "placeJigsaw() requires 'pool' option")
                }
                if (!options.hasMember("target")) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "placeJigsaw() requires 'target' option")
                }
                if (!options.hasMember("maxDepth")) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "placeJigsaw() requires 'maxDepth' option")
                }
                if (!options.hasMember("x") || !options.hasMember("z")) {
                    return@ProxyExecutable PromiseHelpers.createRejectedPromise(context, "placeJigsaw() requires 'x' and 'z' options")
                }

                val pool = options.getMember("pool").asString()
                val target = options.getMember("target").asString()
                val maxDepth = options.getMember("maxDepth").asInt()
                val x = options.getMember("x").asInt()
                val z = options.getMember("z").asInt()
                val dimension = if (options.hasMember("dimension") && !options.getMember("dimension").isNull) {
                    options.getMember("dimension").asString()
                } else null
                val seed = if (options.hasMember("seed") && !options.getMember("seed").isNull) {
                    options.getMember("seed").asLong()
                } else null
                val surface = if (options.hasMember("surface") && !options.getMember("surface").isNull) {
                    options.getMember("surface").asString()
                } else null
                val simulateBearding = if (options.hasMember("simulateBearding") && !options.getMember("simulateBearding").isNull) {
                    options.getMember("simulateBearding").asString()
                } else null

                PromiseHelpers.convertFutureToPromise<Map<String, Any?>>(
                    context,
                    com.rhett.rhettjs.structure.WorldgenStructureManager.placeJigsaw(pool, target, maxDepth, x, z, dimension, seed, surface, simulateBearding)
                )
            }
        ))
    }
}
