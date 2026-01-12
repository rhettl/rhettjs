package com.rhett.rhettjs.engine.api

import com.rhett.rhettjs.api.StoreAPI
import com.rhett.rhettjs.api.NamespacedStore
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject

/**
 * Store API proxy for JavaScript scripts.
 * Provides persistent key-value storage with namespace support.
 *
 * Relocated from: GraalEngine.kt (lines 597-671)
 * Date: 2026-01-12
 */
object StoreAPIProxy {

    /**
     * Create a GraalVM proxy for StoreAPI.
     */
    fun create(): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            "namespace" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("namespace() requires a namespace name")
                }
                val namespace = args[0].asString()
                val store = StoreAPI.namespace(namespace)
                createNamespacedStoreProxy(store)
            },
            "namespaces" to ProxyExecutable { _ ->
                StoreAPI.namespaces()
            },
            "clearAll" to ProxyExecutable { _ ->
                StoreAPI.clearAll()
                null
            },
            "size" to ProxyExecutable { _ ->
                StoreAPI.size()
            }
        ))
    }

    /**
     * Create a GraalVM proxy for a NamespacedStore instance.
     */
    private fun createNamespacedStoreProxy(store: NamespacedStore): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            "set" to ProxyExecutable { args ->
                if (args.size < 2) {
                    throw IllegalArgumentException("set() requires key and value arguments")
                }
                val key = args[0].asString()
                val value = if (args[1].isNull) null else args[1]
                store.set(key, value)
                null
            },
            "get" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("get() requires a key argument")
                }
                val key = args[0].asString()
                store.get(key)
            },
            "has" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("has() requires a key argument")
                }
                val key = args[0].asString()
                store.has(key)
            },
            "delete" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("delete() requires a key argument")
                }
                val key = args[0].asString()
                store.delete(key)
            },
            "clear" to ProxyExecutable { _ ->
                store.clear()
                null
            },
            "keys" to ProxyExecutable { _ ->
                store.keys()
            },
            "size" to ProxyExecutable { _ ->
                store.size()
            },
            "entries" to ProxyExecutable { _ ->
                store.entries()
            }
        ))
    }
}
