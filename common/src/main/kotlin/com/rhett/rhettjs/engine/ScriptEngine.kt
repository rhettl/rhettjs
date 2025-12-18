package com.rhett.rhettjs.engine

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.api.ConsoleAPI
import com.rhett.rhettjs.api.LoggerAPI
import com.rhett.rhettjs.api.TaskAPI
import com.rhett.rhettjs.api.ScheduleAPI
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.events.StartupEventsAPI
import com.rhett.rhettjs.events.ServerEventsAPI
import com.rhett.rhettjs.threading.TickScheduler
import org.mozilla.javascript.*

/**
 * Core Rhino JavaScript engine management.
 * Handles context creation, scope setup, and script execution.
 */
object ScriptEngine {

    /**
     * Clean up Rhino error messages by removing Java object references.
     * Replaces patterns like "org.mozilla.javascript.Undefined@57b72838" with "undefined"
     */
    private fun cleanErrorMessage(message: String): String {
        return message
            .replace(Regex("org\\.mozilla\\.javascript\\.Undefined@[0-9a-f]+"), "undefined")
            .replace(Regex("org\\.mozilla\\.javascript\\.[A-Za-z]+@[0-9a-f]+"), "<object>")
            .replace(" is not a function, it is undefined", " is not a function")
    }

    /**
     * Extract JavaScript-relevant stack trace from Rhino error.
     * Filters out Java internals and shows only script locations.
     */
    private fun getJavaScriptStackTrace(e: RhinoException): String {
        return try {
            // Get the script stack trace (JavaScript calls only)
            val scriptStack = e.scriptStackTrace
            if (scriptStack.isNullOrBlank()) {
                // Fallback to source location
                val fileName = e.sourceName() ?: "unknown"
                val lineNumber = e.lineNumber()
                "  at $fileName:$lineNumber"
            } else {
                // Format the script stack trace
                scriptStack.lines()
                    .filter { it.isNotBlank() }
                    .joinToString("\n") { "  $it" }
            }
        } catch (ex: Exception) {
            // Fallback if stack trace extraction fails
            "  at ${e.sourceName() ?: "unknown"}:${e.lineNumber()}"
        }
    }

    /**
     * Execute a script with error handling.
     *
     * @param script The script metadata
     * @param additionalApis Additional APIs to inject (platform-specific)
     * @return Result of execution (Success or Error)
     */
    fun executeScript(
        script: ScriptInfo,
        additionalApis: Map<String, Any> = emptyMap()
    ): ScriptResult {
        ConfigManager.debug("Executing script: ${script.name} (category: ${script.category})")

        val context = Context.enter()
        return try {
            // Use interpreted mode for better error messages during development
            context.optimizationLevel = -1
            // Enable ES6 features (const, let, arrow functions, template literals, etc.)
            context.languageVersion = Context.VERSION_ES6
            ConfigManager.debug("Created Rhino context with optimization level: ${context.optimizationLevel}, ES version: ES6")

            val scope = createScope(script.category, additionalApis)
            ConfigManager.debug("Created scope with ${additionalApis.size} additional APIs")

            ConfigManager.debug("Reading script file: ${script.path}")
            val scriptContent = script.path.toFile().readText()
            ConfigManager.debugLazy { "Script length: ${scriptContent.length} characters" }

            val result = context.evaluateString(
                scope,
                scriptContent,
                script.name,
                1,
                null
            )

            ConfigManager.debug("Script executed successfully: ${script.name}")
            ScriptResult.Success(result)

        } catch (e: EcmaError) {
            val cleanedMessage = cleanErrorMessage(e.message ?: "Script error")
            val jsStack = getJavaScriptStackTrace(e)
            RhettJSCommon.LOGGER.error("[RhettJS] Script error in ${script.name}: $cleanedMessage\n$jsStack")
            ScriptResult.Error(cleanedMessage, e)

        } catch (e: EvaluatorException) {
            val cleanedMessage = cleanErrorMessage(e.message ?: "Syntax error")
            val jsStack = getJavaScriptStackTrace(e)
            RhettJSCommon.LOGGER.error("[RhettJS] Syntax error in ${script.name}: $cleanedMessage\n$jsStack")
            ScriptResult.Error(cleanedMessage, e)

        } catch (e: Exception) {
            val cleanedMessage = cleanErrorMessage(e.message ?: "Unknown error")
            RhettJSCommon.LOGGER.error("[RhettJS] Unexpected error in ${script.name}: $cleanedMessage", e)
            ScriptResult.Error(cleanedMessage, e)

        } finally {
            Context.exit()
        }
    }

    /**
     * Create a JavaScript scope with appropriate APIs based on script category.
     *
     * @param category The script category (determines available APIs)
     * @param additionalApis Additional APIs to inject (platform-specific)
     * @return A configured Scriptable scope
     */
    fun createScope(
        category: ScriptCategory,
        additionalApis: Map<String, Any> = emptyMap()
    ): Scriptable {
        ConfigManager.debug("Creating scope for category: $category")

        val cx = Context.getCurrentContext()
        val scope = cx.initStandardObjects()

        // Universal APIs available in all contexts
        // Use javaToJS to properly wrap Kotlin objects for JavaScript access
        scope.put("console", scope, Context.javaToJS(ConsoleAPI(), scope))
        scope.put("logger", scope, Context.javaToJS(LoggerAPI(), scope))
        ConfigManager.debug("Injected universal APIs: console, logger")

        // Phase 3: Threading APIs - task() and schedule()
        scope.put("task", scope, TaskAPI())
        scope.put("schedule", scope, ScheduleAPI(TickScheduler.getScheduleFunction()))
        ConfigManager.debug("Injected threading APIs: task, schedule")

        // Add platform-specific APIs
        additionalApis.forEach { (name, api) ->
            // If the API is already a ScriptableObject, inject it directly
            // Otherwise, wrap it with Context.javaToJS()
            val wrappedApi = if (api is ScriptableObject) {
                api.setParentScope(scope)
                api
            } else {
                Context.javaToJS(api, scope)
            }
            scope.put(name, scope, wrappedApi)
            ConfigManager.debug("Injected additional API: $name (${api.javaClass.simpleName})")
        }

        // Inject global libraries (from rhettjs/globals/)
        GlobalsLoader.injectGlobals(scope)
        ConfigManager.debug("Injected globals from GlobalsLoader")

        // Category-specific APIs
        when (category) {
            ScriptCategory.STARTUP -> {
                // Phase 2: StartupEvents API for registering items, blocks, etc.
                scope.put("StartupEvents", scope, Context.javaToJS(StartupEventsAPI, scope))
                ConfigManager.debug("Injected StartupEvents API")
            }

            ScriptCategory.SERVER, ScriptCategory.UTILITY -> {
                // Phase 2: ServerEvents API for runtime event handlers
                scope.put("ServerEvents", scope, Context.javaToJS(ServerEventsAPI, scope))
                ConfigManager.debug("Injected ServerEvents API")
                // Future: World, Player APIs will be added here
            }
        }

        // Remove dangerous Java access
        removeDangerousGlobals(scope)
        ConfigManager.debug("Removed dangerous globals (Java package access)")

        return scope
    }

    /**
     * Remove dangerous Java package access from the scope.
     */
    private fun removeDangerousGlobals(scope: Scriptable) {
        val removedGlobals = listOf(
            "Packages", "java", "javax", "org", "com", "net", "edu", "System", "Runtime"
        )
        removedGlobals.forEach { scope.delete(it) }
    }
}
