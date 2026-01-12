package com.rhett.rhettjs.engine.api

import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.engine.JSHelpers
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject

/**
 * NBT API for JavaScript scripts.
 * Provides helper functions for creating and manipulating NBT data structures.
 *
 * Relocated from: GraalEngine.kt (lines 677-999)
 * Date: 2026-01-12
 */
object NBTAPI {

    /**
     * Create NBT API proxy for JavaScript.
     * Provides helper functions for creating and manipulating NBT data structures.
     */
    fun create(): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            // NBT constructors - just return the values as-is (no conversion needed for pure JS)
            "compound" to ProxyExecutable { args ->
                if (args.isEmpty()) emptyMap<String, Any>() else args[0]
            },
            "list" to ProxyExecutable { args ->
                if (args.isEmpty()) emptyList<Any>() else args[0]
            },
            "string" to ProxyExecutable { args ->
                if (args.isEmpty()) "" else args[0]
            },
            "int" to ProxyExecutable { args ->
                if (args.isEmpty()) 0 else args[0]
            },
            "double" to ProxyExecutable { args ->
                if (args.isEmpty()) 0.0 else args[0]
            },
            "byte" to ProxyExecutable { args ->
                if (args.isEmpty()) 0 else args[0]
            },

            // NBT query methods - get/set/has/delete with path support
            "get" to ProxyExecutable { args ->
                if (args.size < 2) return@ProxyExecutable null
                try {
                    val nbt = convertGraalValueToKotlin(args[0])
                    val path = if (args[1].isString) args[1].asString() else args[1].toString()
                    getNBTValue(nbt, path)
                } catch (e: Exception) {
                    ConfigManager.debug("NBT.get error: ${e.message}")
                    throw IllegalArgumentException("NBT.get requires (nbt, path): ${e.message}")
                }
            },
            "set" to ProxyExecutable { args ->
                if (args.size < 3) throw IllegalArgumentException("set() requires nbt, path, and value")
                try {
                    val path = if (args[1].isString) args[1].asString() else args[1].toString()
                    // Work with GraalVM Values directly to preserve JS object types
                    JSHelpers.setNBTValueJS(args[0], path, args[2])
                } catch (e: Exception) {
                    ConfigManager.debug("NBT.set error: ${e.message}")
                    throw IllegalArgumentException("NBT.set requires (nbt, path, value): ${e.message}")
                }
            },
            "has" to ProxyExecutable { args ->
                if (args.size < 2) return@ProxyExecutable false
                try {
                    val nbt = convertGraalValueToKotlin(args[0])
                    val path = if (args[1].isString) args[1].asString() else args[1].toString()
                    hasNBTValue(nbt, path)
                } catch (e: Exception) {
                    false
                }
            },
            "remove" to ProxyExecutable { args ->
                if (args.size < 2) throw IllegalArgumentException("remove() requires nbt and path")
                try {
                    val path = if (args[1].isString) args[1].asString() else args[1].toString()
                    // Work with GraalVM Values directly to preserve JS object types
                    JSHelpers.deleteNBTValueJS(args[0], path)
                } catch (e: Exception) {
                    ConfigManager.debug("NBT.remove error: ${e.message}")
                    throw IllegalArgumentException("NBT.remove requires (nbt, path): ${e.message}")
                }
            },
            "merge" to ProxyExecutable { args ->
                if (args.size < 2) throw IllegalArgumentException("merge() requires base and updates")
                try {
                    // Optional deep parameter (args[2]), defaults to false (shallow merge)
                    val deep = if (args.size >= 3 && args[2].isBoolean) args[2].asBoolean() else false
                    JSHelpers.mergeNBTValueJS(args[0], args[1], deep)
                } catch (e: Exception) {
                    ConfigManager.debug("NBT.merge error: ${e.message}")
                    throw IllegalArgumentException("NBT.merge requires (base, updates[, deep]): ${e.message}")
                }
            }
        ))
    }

    /**
     * Get value from NBT structure using path notation (e.g., "tag.Damage" or "tag.Enchantments[0].id")
     */
    private fun getNBTValue(nbt: Any?, path: String): Any? {
        if (nbt == null) return null

        val parts = parsePath(path)
        var current: Any? = nbt

        for (part in parts) {
            when (part) {
                is PathKey -> {
                    if (current is Map<*, *>) {
                        current = current[part.key]
                    } else {
                        return null
                    }
                }
                is PathIndex -> {
                    if (current is List<*>) {
                        if (part.index < current.size) {
                            current = current[part.index]
                        } else {
                            return null
                        }
                    } else {
                        return null
                    }
                }
            }
        }

        return current
    }

    /**
     * Check if path exists in NBT structure
     */
    private fun hasNBTValue(nbt: Any?, path: String): Boolean {
        return getNBTValue(nbt, path) != null
    }

    /**
     * Parse path notation into parts (e.g., "tag.Damage" -> [PathKey("tag"), PathKey("Damage")])
     */
    private fun parsePath(path: String): List<PathPart> {
        val parts = mutableListOf<PathPart>()
        var current = ""
        var i = 0

        while (i < path.length) {
            when (path[i]) {
                '.' -> {
                    if (current.isNotEmpty()) {
                        parts.add(PathKey(current))
                        current = ""
                    }
                }
                '[' -> {
                    if (current.isNotEmpty()) {
                        parts.add(PathKey(current))
                        current = ""
                    }
                    // Parse array index
                    val endBracket = path.indexOf(']', i)
                    if (endBracket != -1) {
                        val indexStr = path.substring(i + 1, endBracket)
                        parts.add(PathIndex(indexStr.toInt()))
                        i = endBracket
                    }
                }
                else -> {
                    current += path[i]
                }
            }
            i++
        }

        if (current.isNotEmpty()) {
            parts.add(PathKey(current))
        }

        return parts
    }

    private sealed class PathPart
    private data class PathKey(val key: String) : PathPart()
    private data class PathIndex(val index: Int) : PathPart()

    /**
     * Convert GraalVM Value to Kotlin types.
     * Internal visibility for use by StructureAPIsProxy.
     */
    internal fun convertGraalValueToKotlin(value: Value): Any? {
        return when {
            value.isNull -> null
            value.isBoolean -> value.asBoolean()
            value.isString -> value.asString()
            value.isNumber -> {
                when {
                    value.fitsInInt() -> value.asInt()
                    value.fitsInLong() -> value.asLong()
                    value.fitsInDouble() -> value.asDouble()
                    else -> value.asDouble()
                }
            }
            value.hasArrayElements() -> {
                (0 until value.arraySize).map { i ->
                    convertGraalValueToKotlin(value.getArrayElement(i))
                }
            }
            value.hasMembers() -> {
                val map = mutableMapOf<String, Any?>()
                value.memberKeys.forEach { key ->
                    map[key] = convertGraalValueToKotlin(value.getMember(key))
                }
                map
            }
            else -> value.asString()
        }
    }
}
