package com.rhett.rhettjs.engine

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.config.ConfigManager
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject

/**
 * GraalVM JavaScript engine management.
 * Handles context creation, bindings setup, and script execution with native async/await support.
 */
object GraalEngine {

    /**
     * Create a new GraalVM context with standard options.
     *
     * @return A configured GraalVM Context
     */
    private fun createContext(): Context {
        return Context.newBuilder("js")
            .allowAllAccess(true)  // TODO: Lock this down later
            .option("js.esm-eval-returns-exports", "true")
            .option("js.ecmascript-version", "2022")  // ES2022 for modern features
            .option("js.top-level-await", "true")  // Enable top-level await
            .build()
    }

    /**
     * Execute a script with error handling.
     *
     * @param script The script metadata
     * @param additionalBindings Additional bindings to inject (platform-specific)
     * @return Result of execution (Success or Error)
     */
    fun executeScript(
        script: ScriptInfo,
        additionalBindings: Map<String, Any> = emptyMap()
    ): ScriptResult {
        ConfigManager.debug("Executing script: ${script.name} (category: ${script.category})")

        val context = createContext()
        return try {
            // Inject bindings based on script category
            injectBindings(context, script.category, additionalBindings)

            // Create source from file
            val source = Source.newBuilder("js", script.path.toFile())
                .name(script.name)
                .build()

            ConfigManager.debug("Evaluating script: ${script.name}")

            // Execute script (blocks on top-level await)
            val result = context.eval(source)

            ConfigManager.debug("Script executed successfully: ${script.name}")
            ScriptResult.Success(result)

        } catch (e: PolyglotException) {
            val message = cleanErrorMessage(e)
            RhettJSCommon.LOGGER.error("[RhettJS] Script error in ${script.name}: $message")
            ScriptResult.Error(message, e)

        } catch (e: Exception) {
            val message = e.message ?: "Unknown error"
            RhettJSCommon.LOGGER.error("[RhettJS] Unexpected error in ${script.name}: $message", e)
            ScriptResult.Error(message, e)

        } finally {
            context.close()
        }
    }

    /**
     * Inject JavaScript bindings into the context based on script category.
     */
    private fun injectBindings(
        context: Context,
        category: ScriptCategory,
        additionalBindings: Map<String, Any>
    ) {
        val bindings = context.getBindings("js")

        // Inject Console API
        val console = createConsoleAPI()
        bindings.putMember("console", console)
        ConfigManager.debug("Injected Console API")

        // TODO: Phase 3 - Inject Runtime API
        // TODO: Phase 5 - Inject Store API
        // TODO: Phase 6 - Inject Structure API
        // TODO: Phase 7 - Inject World API

        // Inject additional bindings (Caller, Args, etc.)
        additionalBindings.forEach { (name, value) ->
            bindings.putMember(name, value)
            ConfigManager.debug("Injected binding: $name")
        }

        ConfigManager.debug("Injected ${1 + additionalBindings.size} bindings for category: $category")
    }

    /**
     * Create the Console API for JavaScript scripts.
     * Provides console.log, console.warn, console.error, console.info, console.debug
     */
    private fun createConsoleAPI(): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            "log" to ProxyExecutable { args ->
                val message = args.joinToString(" ") { formatValue(it) }
                RhettJSCommon.LOGGER.info("[RhettJS-Script] $message")
                null
            },
            "warn" to ProxyExecutable { args ->
                val message = args.joinToString(" ") { formatValue(it) }
                RhettJSCommon.LOGGER.warn("[RhettJS-Script] $message")
                null
            },
            "error" to ProxyExecutable { args ->
                val message = args.joinToString(" ") { formatValue(it) }
                RhettJSCommon.LOGGER.error("[RhettJS-Script] $message")
                null
            },
            "info" to ProxyExecutable { args ->
                val message = args.joinToString(" ") { formatValue(it) }
                RhettJSCommon.LOGGER.info("[RhettJS-Script] $message")
                null
            },
            "debug" to ProxyExecutable { args ->
                val message = args.joinToString(" ") { formatValue(it) }
                ConfigManager.debug("[Script] $message")
                null
            }
        ))
    }

    /**
     * Format a GraalVM Value for console output.
     * Handles primitives, objects, arrays, etc.
     */
    private fun formatValue(value: Value): String {
        return when {
            value.isNull -> "null"
            value.isString -> value.asString()
            value.isNumber -> value.toString()
            value.isBoolean -> value.asBoolean().toString()
            value.hasArrayElements() -> {
                val elements = (0 until value.arraySize).map { formatValue(value.getArrayElement(it)) }
                "[${elements.joinToString(", ")}]"
            }
            value.hasMembers() -> {
                val members = value.memberKeys.map { key ->
                    "$key: ${formatValue(value.getMember(key))}"
                }
                "{${members.joinToString(", ")}}"
            }
            else -> value.toString()
        }
    }

    /**
     * Clean up GraalVM error messages for better user experience.
     */
    private fun cleanErrorMessage(e: PolyglotException): String {
        val message = e.message ?: "Script error"

        // Include source location if available
        val location = if (e.sourceLocation != null) {
            val loc = e.sourceLocation
            "\n  at ${loc.source.name}:${loc.startLine}:${loc.startColumn}"
        } else {
            ""
        }

        return "$message$location"
    }
}