package com.rhett.rhettjs.engine

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

/**
 * Maps Java/Kotlin types to TypeScript type strings.
 */
object TypeMapper {

    /**
     * Convert a Java/Kotlin Type to a TypeScript type string.
     */
    fun toTypeScript(type: Type): String {
        return when {
            // Handle parameterized types (List<T>, Map<K,V>, etc.)
            type is ParameterizedType -> {
                val rawType = type.rawType as Class<*>
                val typeArgs = type.actualTypeArguments

                when {
                    // List<T> or Collection<T> -> T[]
                    List::class.java.isAssignableFrom(rawType) ||
                    Collection::class.java.isAssignableFrom(rawType) -> {
                        val elementType = if (typeArgs.isNotEmpty()) {
                            toTypeScript(typeArgs[0])
                        } else {
                            "any"
                        }
                        "${elementType}[]"
                    }

                    // Map<K, V> -> Record<K, V> or { [key: K]: V }
                    Map::class.java.isAssignableFrom(rawType) -> {
                        val keyType = if (typeArgs.size > 0) toTypeScript(typeArgs[0]) else "string"
                        val valueType = if (typeArgs.size > 1) toTypeScript(typeArgs[1]) else "any"
                        "Record<$keyType, $valueType>"
                    }

                    // Array<T> -> T[]
                    rawType.isArray -> {
                        val componentType = rawType.componentType
                        "${toTypeScript(componentType)}[]"
                    }

                    // Fallback: GenericType<T1, T2>
                    else -> {
                        val typeName = rawType.simpleName
                        val typeArgString = typeArgs.joinToString(", ") { toTypeScript(it) }
                        "$typeName<$typeArgString>"
                    }
                }
            }

            // Handle wildcard types (? extends T, ? super T)
            type is WildcardType -> {
                val upperBounds = type.upperBounds
                if (upperBounds.isNotEmpty() && upperBounds[0] != Any::class.java) {
                    toTypeScript(upperBounds[0])
                } else {
                    "any"
                }
            }

            // Handle Class types
            type is Class<*> -> {
                when {
                    // Primitives and common types
                    type == String::class.java -> "string"
                    type == Char::class.java || type == Character::class.java -> "string"
                    type == Int::class.java || type == Integer::class.java -> "number"
                    type == Long::class.java || type == java.lang.Long::class.java -> "number"
                    type == Float::class.java || type == java.lang.Float::class.java -> "number"
                    type == Double::class.java || type == java.lang.Double::class.java -> "number"
                    type == Short::class.java || type == java.lang.Short::class.java -> "number"
                    type == Byte::class.java || type == java.lang.Byte::class.java -> "number"
                    type == Boolean::class.java || type == java.lang.Boolean::class.java -> "boolean"
                    type == Void::class.java || type == Void.TYPE || type == Unit::class.java -> "void"

                    // Arrays
                    type.isArray -> {
                        val componentType = type.componentType
                        "${toTypeScript(componentType)}[]"
                    }

                    // Kotlin function types
                    type.name.startsWith("kotlin.jvm.functions.Function") -> {
                        // Extract arity from Function0, Function1, Function2, etc.
                        val arityMatch = Regex("Function(\\d+)").find(type.name)
                        if (arityMatch != null) {
                            val arity = arityMatch.groupValues[1].toInt()
                            val params = (0 until arity).joinToString(", ") { "arg$it: any" }
                            "($params) => any"
                        } else {
                            "(...args: any[]) => any"
                        }
                    }

                    // List/Collection types (non-parameterized fallback)
                    List::class.java.isAssignableFrom(type) ||
                    Collection::class.java.isAssignableFrom(type) -> "any[]"

                    // Map types (non-parameterized fallback)
                    Map::class.java.isAssignableFrom(type) -> "Record<string, any>"

                    // Object/Any
                    type == Any::class.java || type == Object::class.java -> "any"

                    // Unknown types: use class name
                    else -> type.simpleName ?: "any"
                }
            }

            // Fallback for unknown types
            else -> "any"
        }
    }

    /**
     * Check if a type is nullable (Kotlin's T?).
     * In Java reflection, this is harder to detect, so we use heuristics.
     */
    fun isNullable(type: Type): Boolean {
        // In Java reflection, we can't reliably detect Kotlin nullability
        // We'll assume reference types are nullable by default
        return when (type) {
            is Class<*> -> !type.isPrimitive
            else -> true
        }
    }

    /**
     * Convert a Type to TypeScript, handling nullability.
     */
    fun toTypeScriptWithNullability(type: Type): String {
        val tsType = toTypeScript(type)
        return if (isNullable(type) && tsType != "any" && tsType != "void") {
            "$tsType | null"
        } else {
            tsType
        }
    }
}
