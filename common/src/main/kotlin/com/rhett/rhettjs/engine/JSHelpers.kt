package com.rhett.rhettjs.engine

import com.rhett.rhettjs.config.ConfigManager
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value

/**
 * JavaScript helper functions pre-compiled to avoid classloader issues.
 * These helpers are cached and reused for NBT operations.
 *
 * Relocated from: GraalEngine.kt (lines 46-327)
 * Date: 2026-01-12
 */
object JSHelpers {

    // Pre-compiled JavaScript helper functions (cached to avoid classloader issues)
    @Volatile
    private var jsNBTSetHelper: Value? = null
    @Volatile
    private var jsNBTDeleteHelper: Value? = null
    @Volatile
    private var jsNBTMergeShallowHelper: Value? = null
    @Volatile
    private var jsNBTMergeDeepHelper: Value? = null
    @Volatile
    private var jsUndefinedValue: Value? = null

    /**
     * Clear cached helpers (called on engine reset).
     */
    fun clearHelpers() {
        jsNBTSetHelper = null
        jsNBTDeleteHelper = null
        jsNBTMergeShallowHelper = null
        jsNBTMergeDeepHelper = null
        jsUndefinedValue = null
    }

    /**
     * Check if helpers are initialized.
     */
    fun areHelpersInitialized(): Boolean {
        return jsNBTSetHelper != null && jsNBTDeleteHelper != null
    }

    /**
     * Get JavaScript undefined value (cached).
     */
    fun getUndefinedValue(): Value {
        return jsUndefinedValue ?: throw IllegalStateException("JavaScript helpers not initialized")
    }

    /**
     * Initialize pre-compiled JavaScript helper functions.
     * This avoids classloader issues when calling context.eval() from within running scripts.
     */
    fun initializeHelpers(context: Context) {
        // NBT.set() helper
        jsNBTSetHelper = context.eval("js", """
            (function(obj, path, value) {
                const keys = path.split('.').flatMap(k => {
                    const match = k.match(/^(.+?)\[(\d+)\]$/);
                    return match ? [match[1], parseInt(match[2])] : [k];
                });

                function deepClone(val) {
                    if (Array.isArray(val)) return [...val];
                    if (typeof val === 'object' && val !== null) return {...val};
                    return val;
                }

                function setPath(obj, keys, value) {
                    if (keys.length === 0) return value;

                    const [key, ...rest] = keys;
                    const cloned = deepClone(obj);

                    if (Array.isArray(cloned)) {
                        cloned[key] = setPath(cloned[key], rest, value);
                    } else {
                        cloned[key] = setPath(cloned[key], rest, value);
                    }

                    return cloned;
                }

                return setPath(obj, keys, value);
            })
        """.trimIndent())

        // NBT.remove() helper
        jsNBTDeleteHelper = context.eval("js", """
            (function(obj, path) {
                const keys = path.split('.').flatMap(k => {
                    const match = k.match(/^(.+?)\[(\d+)\]$/);
                    return match ? [match[1], parseInt(match[2])] : [k];
                });

                function deepClone(val) {
                    if (Array.isArray(val)) return [...val];
                    if (typeof val === 'object' && val !== null) return {...val};
                    return val;
                }

                function deletePath(obj, keys) {
                    if (keys.length === 0) return obj;
                    if (keys.length === 1) {
                        const cloned = deepClone(obj);
                        if (Array.isArray(cloned)) {
                            cloned.splice(keys[0], 1);
                        } else {
                            delete cloned[keys[0]];
                        }
                        return cloned;
                    }

                    const [key, ...rest] = keys;
                    const cloned = deepClone(obj);
                    cloned[key] = deletePath(cloned[key], rest);
                    return cloned;
                }

                return deletePath(obj, keys);
            })
        """.trimIndent())

        // NBT.merge() shallow helper
        jsNBTMergeShallowHelper = context.eval("js", """
            (function(base, updates) {
                return {...base, ...updates};
            })
        """.trimIndent())

        // NBT.merge() deep helper
        jsNBTMergeDeepHelper = context.eval("js", """
            (function(base, updates) {
                function deepMerge(target, source) {
                    const result = Array.isArray(target) ? [...target] : {...target};

                    for (const key in source) {
                        if (source.hasOwnProperty(key)) {
                            if (source[key] && typeof source[key] === 'object' && !Array.isArray(source[key])) {
                                result[key] = result[key] && typeof result[key] === 'object'
                                    ? deepMerge(result[key], source[key])
                                    : {...source[key]};
                            } else {
                                result[key] = source[key];
                            }
                        }
                    }

                    return result;
                }

                return deepMerge(base, updates);
            })
        """.trimIndent())

        // JavaScript undefined value
        jsUndefinedValue = context.eval("js", "undefined")

        ConfigManager.debug("Initialized ${4} JavaScript helper functions")
    }

    /**
     * Set value in NBT structure (immutable - returns new structure).
     * Works with GraalVM Values directly to preserve JS object types.
     */
    fun setNBTValueJS(nbtValue: Value, path: String, newValue: Value): Any {
        val helper = jsNBTSetHelper ?: throw IllegalStateException("NBT helper not initialized")
        return helper.execute(nbtValue, path, newValue)
    }

    /**
     * Delete value from NBT structure (immutable - returns new structure).
     * Works with GraalVM Values directly to preserve JS object types.
     */
    fun deleteNBTValueJS(nbtValue: Value, path: String): Any {
        val helper = jsNBTDeleteHelper ?: throw IllegalStateException("NBT helper not initialized")
        return helper.execute(nbtValue, path)
    }

    /**
     * Merge two NBT structures (immutable - returns new structure).
     * Works with GraalVM Values directly to preserve JS object types.
     *
     * @param baseValue The base NBT object
     * @param updatesValue The updates to merge in
     * @param deep If true, performs deep merge; if false, shallow merge (default)
     */
    fun mergeNBTValueJS(baseValue: Value, updatesValue: Value, deep: Boolean): Any {
        val helper = if (deep) {
            jsNBTMergeDeepHelper ?: throw IllegalStateException("NBT helper not initialized")
        } else {
            jsNBTMergeShallowHelper ?: throw IllegalStateException("NBT helper not initialized")
        }
        return helper.execute(baseValue, updatesValue)
    }
}
