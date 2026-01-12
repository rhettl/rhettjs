package com.rhett.rhettjs.engine

import com.rhett.rhettjs.config.ConfigManager
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject

/**
 * Script context injection utilities.
 * Handles Script.caller, Script.args, and Script.argv for utility scripts.
 *
 * Relocated from: GraalEngine.kt (lines 1990-2151)
 * Date: 2026-01-12
 */
object ScriptContext {

    /**
     * Inject Script.* context for utility scripts (rjs/scripts/).
     * Provides Script.caller, Script.args, and Script.argv for command-invoked scripts.
     */
    fun injectScriptContext(bindings: Value, context: Context, additionalBindings: Map<String, Any>) {
        // Extract Caller and Args from additionalBindings if provided
        val caller = additionalBindings["Caller"]
        val args = additionalBindings["Args"]

        if (caller != null || args != null) {
            val scriptContext = mutableMapOf<String, Any?>()

            // Convert CallerAPI to JavaScript object using CallerAdapter
            if (caller != null) {
                val callerJS = if (caller is com.rhett.rhettjs.api.CallerAPI) {
                    // Use CallerAdapter to convert to proper JS object with properties
                    com.rhett.rhettjs.adapter.CallerAdapter.toJS(caller.source, context)
                } else {
                    // Fallback if already converted
                    context.asValue(caller)
                }
                scriptContext["caller"] = callerJS
            }

            if (args != null) scriptContext["args"] = args

            // Parse args into Script.argv if Args is provided
            if (args != null) {
                scriptContext["argv"] = createArgvProxy(args)
            }

            val scriptProxy = ProxyObject.fromMap(scriptContext)
            bindings.putMember("Script", scriptProxy)
            ConfigManager.debug("Injected Script.caller, Script.args, and Script.argv")
        }
    }

    /**
     * Create Script.argv proxy with argument parsing.
     * Parses command-line arguments into positional args and flags with values.
     *
     * Supports:
     * - Positional args: arg1 arg2
     * - Boolean flags: -abc (a=true, b=true, c=true), --verbose (verbose=true)
     * - Flags with values: -a=1 -b=2, --name=value, --name="quoted value"
     *
     * @param args The raw arguments (can be List or Array)
     * @return ProxyObject with get(index), get(name), hasFlag(), getAll(), and raw property
     */
    private fun createArgvProxy(args: Any): ProxyObject {
        // Convert args to list of strings
        @Suppress("UNCHECKED_CAST")
        val argsList = when (args) {
            is List<*> -> args.map { it.toString() }
            is Array<*> -> args.map { it.toString() }
            else -> emptyList()
        }

        // Parse arguments into positional args and named flags
        val positionalArgs = mutableListOf<String>()
        val namedFlags = mutableMapOf<String, Any>() // flag name -> value (true or string/number)

        for (arg in argsList) {
            when {
                arg.startsWith("--") -> {
                    // Long flag: --verbose or --name=value
                    val flagPart = arg.substring(2)
                    if ('=' in flagPart) {
                        val (name, value) = flagPart.split('=', limit = 2)
                        namedFlags[name] = parseValue(value)
                    } else {
                        namedFlags[flagPart] = true
                    }
                }
                arg.startsWith("-") && arg.length > 1 -> {
                    // Short flag: -v or -abc (multi-char boolean) or -a=value
                    val flagPart = arg.substring(1)
                    if ('=' in flagPart) {
                        // Single flag with value: -a=123
                        val (name, value) = flagPart.split('=', limit = 2)
                        namedFlags[name] = parseValue(value)
                    } else {
                        // Multi-char boolean flags: -abc -> a=true, b=true, c=true
                        flagPart.forEach { char ->
                            namedFlags[char.toString()] = true
                        }
                    }
                }
                else -> {
                    // Positional argument
                    positionalArgs.add(arg)
                }
            }
        }

        ConfigManager.debug("Parsed argv: ${positionalArgs.size} positional args, ${namedFlags.size} named flags")

        // Use pre-compiled undefined value to avoid classloader issues
        val undefined = JSHelpers.getUndefinedValue()

        return ProxyObject.fromMap(mapOf(
            // get(indexOrName) - Get positional argument by index OR named flag by name
            "get" to ProxyExecutable { params ->
                if (params.isEmpty()) {
                    throw IllegalArgumentException("get() requires an index or name argument")
                }

                when {
                    params[0].isNumber -> {
                        // Get positional arg by index
                        val index = params[0].asInt()
                        if (index >= 0 && index < positionalArgs.size) {
                            positionalArgs[index]
                        } else {
                            undefined
                        }
                    }
                    params[0].isString -> {
                        // Get named flag by name
                        val name = params[0].asString()
                        namedFlags[name] ?: undefined
                    }
                    else -> undefined
                }
            },

            // hasFlag(flag) - Check if a flag exists (backward compatibility)
            "hasFlag" to ProxyExecutable { params ->
                if (params.isEmpty()) {
                    throw IllegalArgumentException("hasFlag() requires a flag name")
                }
                val flag = params[0].asString()
                namedFlags.containsKey(flag)
            },

            // getAll() - Get all positional arguments as array
            "getAll" to ProxyExecutable { _ ->
                positionalArgs.toList()
            },

            // raw - The original args array (read-only property)
            "raw" to argsList
        ))
    }

    /**
     * Parse a flag value string into appropriate type.
     * - Quoted strings: "hello" or 'hello' -> string
     * - Numbers: 123 -> int, 3.14 -> double
     * - Otherwise: string
     */
    private fun parseValue(value: String): Any {
        // Remove quotes if present
        val trimmed = value.trim()
        val unquoted = when {
            (trimmed.startsWith('"') && trimmed.endsWith('"')) ||
            (trimmed.startsWith('\'') && trimmed.endsWith('\'')) ->
                trimmed.substring(1, trimmed.length - 1)
            else -> trimmed
        }

        // Try to parse as number
        return unquoted.toIntOrNull()
            ?: unquoted.toDoubleOrNull()
            ?: unquoted
    }
}
