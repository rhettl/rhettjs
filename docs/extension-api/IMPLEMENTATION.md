# RhettJS Extension API - Implementation Guide

**Audience:** RhettJS core developers
**Version:** 1.0 Draft

---

## Table of Contents

1. [Overview](#overview)
2. [Files to Create](#files-to-create)
3. [Files to Modify](#files-to-modify)
4. [Implementation Steps](#implementation-steps)
5. [Testing Strategy](#testing-strategy)
6. [Migration Path](#migration-path)

---

## Overview

This document describes how to implement the extension API system in RhettJS core.

**Prerequisites:**
- Issue #1 (Split GraalEngine.kt) should be completed first for cleaner integration
- Understanding of GraalVM ProxyObject system
- Understanding of RhettJSFileSystem virtual module generation

**Estimated effort:** 2-3 days

---

## Files to Create

### 1. `RhettJSExtension.kt`

**Location:** `common/src/main/kotlin/com/rhett/rhettjs/extension/RhettJSExtension.kt`

**Purpose:** Public API for extension registration

```kotlin
package com.rhett.rhettjs.extension

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.proxy.ProxyObject
import net.minecraft.server.MinecraftServer
import java.util.concurrent.CompletableFuture

/**
 * Public API for RhettJS extensions.
 */
object RhettJSExtension {

    /**
     * Register a new JavaScript module.
     *
     * @param config Module configuration
     * @throws IllegalArgumentException if module name is invalid or already registered
     */
    fun registerModule(config: ModuleConfig) {
        ExtensionRegistry.register(config)
    }

    /**
     * Configuration for a registered module.
     */
    data class ModuleConfig(
        /** Module name for imports (e.g., "puppeteer" -> "rhettjs/puppeteer") */
        val moduleName: String,

        /** Factory to create the API proxy */
        val apiFactory: (ExtensionContext) -> ProxyObject,

        /**
         * Provider for type definitions. Returns list of (filename, content) pairs.
         * Files will be written to __types/{moduleName}/
         */
        val typeDefinitionProvider: (() -> List<Pair<String, String>>)? = null,

        /** Which script contexts this module is available in */
        val availableIn: Set<ScriptContext> = setOf(ScriptContext.ALL),

        /** Priority for initialization order (higher = earlier) */
        val priority: Int = 0
    )

    /**
     * Context provided to extensions when creating their API.
     */
    data class ExtensionContext(
        val graalContext: Context,
        val server: MinecraftServer,
        val config: RhettJSConfig,
        val builtins: BuiltinAPIs,
        val scriptContext: ScriptContext
    )

    /**
     * Access to RhettJS built-in APIs and utilities.
     */
    data class BuiltinAPIs(
        // Managers
        val worldManager: com.rhett.rhettjs.world.WorldManager,
        val structureManager: com.rhett.rhettjs.structure.StructureNbtManager,
        val largeStructureManager: com.rhett.rhettjs.structure.LargeStructureNbtManager,
        val worldgenStructureManager: com.rhett.rhettjs.structure.WorldgenStructureManager,

        // Adapters
        val playerAdapter: com.rhett.rhettjs.adapter.PlayerAdapter,
        val callerAdapter: com.rhett.rhettjs.adapter.CallerAdapter,
        val worldAdapter: com.rhett.rhettjs.adapter.WorldAdapter,

        // Helper functions
        val convertFutureToPromise: (Context, CompletableFuture<*>) -> org.graalvm.polyglot.Value,
        val createPositionObject: (net.minecraft.world.phys.Vec3) -> ProxyObject,
        val createPositionFromBlockPos: (net.minecraft.core.BlockPos) -> ProxyObject,
        val formatValue: (org.graalvm.polyglot.Value) -> String,
        val createProxyObject: (Map<String, Any>) -> ProxyObject
    )

    /**
     * RhettJS configuration access.
     */
    data class RhettJSConfig(
        val debug: Boolean
    )
}
```

---

### 2. `ScriptContext.kt`

**Location:** `common/src/main/kotlin/com/rhett/rhettjs/extension/ScriptContext.kt`

**Purpose:** Execution context enum for extensions

```kotlin
package com.rhett.rhettjs.extension

import com.rhett.rhettjs.engine.ScriptCategory

/**
 * Script execution contexts.
 * Controls which APIs are available in which execution environments.
 */
enum class ScriptContext {
    /** Available in all contexts */
    ALL,

    /** rjs/startup/ - Server starting, before world loads */
    STARTUP,

    /** rjs/server/ - Datapack registration time */
    SERVER,

    /** rjs/scripts/ - Command execution (/rjs run) */
    COMMAND,

    /** rjs/client/ - Client-side execution (future) */
    CLIENT,

    ;

    companion object {
        /**
         * Convert ScriptCategory to ScriptContext.
         */
        fun fromCategory(category: ScriptCategory): ScriptContext = when(category) {
            ScriptCategory.STARTUP -> STARTUP
            ScriptCategory.SERVER -> SERVER
            ScriptCategory.UTILITY -> COMMAND
            ScriptCategory.MODULES -> ALL
        }
    }
}
```

---

### 3. `ExtensionRegistry.kt`

**Location:** `common/src/main/kotlin/com/rhett/rhettjs/extension/ExtensionRegistry.kt`

**Purpose:** Internal registry for managing extensions

```kotlin
package com.rhett.rhettjs.extension

import com.rhett.rhettjs.RhettJSCommon
import java.util.concurrent.ConcurrentHashMap

/**
 * Internal registry for RhettJS extensions.
 */
object ExtensionRegistry {

    private val modules = ConcurrentHashMap<String, ModuleRegistration>()

    data class ModuleRegistration(
        val config: RhettJSExtension.ModuleConfig,
        val fullModuleName: String  // "rhettjs/{moduleName}"
    )

    /**
     * Register a module.
     *
     * @throws IllegalArgumentException if module name is invalid or already registered
     */
    fun register(config: RhettJSExtension.ModuleConfig) {
        validateModuleName(config.moduleName)

        val fullName = "rhettjs/${config.moduleName}"

        if (modules.containsKey(fullName)) {
            throw IllegalArgumentException("Module already registered: $fullName")
        }

        val registration = ModuleRegistration(config, fullName)
        modules[fullName] = registration

        // Also register bare specifier alias
        modules[config.moduleName] = registration

        RhettJSCommon.LOGGER.info("[RhettJS] Registered extension module: $fullName")
    }

    /**
     * Get a module by name (supports both "rhettjs/name" and "name").
     */
    fun getModule(name: String): ModuleRegistration? = modules[name]

    /**
     * Get all registered modules (deduplicated).
     */
    fun getAllModules(): Collection<ModuleRegistration> =
        modules.values.distinctBy { it.fullModuleName }

    /**
     * Get modules available in the given context.
     */
    fun getModulesForContext(context: ScriptContext): List<ModuleRegistration> =
        getAllModules().filter {
            it.config.availableIn.contains(ScriptContext.ALL) ||
            it.config.availableIn.contains(context)
        }.sortedByDescending { it.config.priority }

    /**
     * Validate module name format.
     */
    private fun validateModuleName(name: String) {
        if (!name.matches(Regex("^[a-z0-9-]+$"))) {
            throw IllegalArgumentException(
                "Invalid module name: '$name' (must be lowercase alphanumeric with optional hyphens)"
            )
        }

        // Check for conflicts with built-in modules
        val builtinModules = setOf(
            "world", "commands", "server", "store", "nbt",
            "structure", "structurenbt", "largestructurenbt",
            "worldgenstructure", "runtime", "script"
        )

        if (builtinModules.contains(name.lowercase())) {
            throw IllegalArgumentException(
                "Module name conflicts with built-in module: $name"
            )
        }
    }

    /**
     * Clear all registrations (used for testing/reload).
     */
    fun clear() {
        modules.clear()
    }
}
```

---

## Files to Modify

### 1. `RhettJSFileSystem.kt`

**Location:** `common/src/main/kotlin/com/rhett/rhettjs/engine/RhettJSFileSystem.kt`

**Changes:**

#### A. Import ExtensionRegistry

```kotlin
import com.rhett.rhettjs.extension.ExtensionRegistry
```

#### B. Modify `checkAccess()` (around line 205)

**Before:**
```kotlin
override fun checkAccess(path: Path, modes: MutableSet<out AccessMode>, vararg linkOptions: LinkOption) {
    val pathString = path.toString()

    if (pathString.startsWith("/modules/") && pathString.endsWith(".mjs")) {
        val moduleName = pathString.removePrefix("/modules/").removeSuffix(".mjs")

        // Check built-in modules
        if (BUILT_IN_MODULES.contains(moduleName)) {
            return
        }

        // Check submodules
        // ...
    }
    // ...
}
```

**After:**
```kotlin
override fun checkAccess(path: Path, modes: MutableSet<out AccessMode>, vararg linkOptions: LinkOption) {
    val pathString = path.toString()

    if (pathString.startsWith("/modules/") && pathString.endsWith(".mjs")) {
        val moduleName = pathString.removePrefix("/modules/").removeSuffix(".mjs")

        // Check built-in modules
        if (BUILT_IN_MODULES.contains(moduleName)) {
            return
        }

        // NEW: Check extension modules
        if (ExtensionRegistry.getModule(moduleName) != null) {
            return
        }

        // Check submodules
        // ...
    }
    // ...
}
```

#### C. Modify `newByteChannel()` (around line 271)

**Add extension module generation:**

```kotlin
private fun generateModuleContent(moduleName: String): ByteArray {
    // NEW: Check if it's an extension module
    val registration = ExtensionRegistry.getModule(moduleName)
    if (registration != null) {
        return """
            const api = globalThis.__extension_${registration.config.moduleName};
            export default api;
        """.trimIndent().toByteArray()
    }

    // Existing built-in module generation
    // ...
}
```

---

### 2. `GraalEngine.kt`

**Location:** `common/src/main/kotlin/com/rhett/rhettjs/engine/GraalEngine.kt`

**Changes:**

#### A. Import ExtensionRegistry and ScriptContext

```kotlin
import com.rhett.rhettjs.extension.ExtensionRegistry
import com.rhett.rhettjs.extension.RhettJSExtension
import com.rhett.rhettjs.extension.ScriptContext
```

#### B. Modify `injectBindings()` (around line 401)

**Add ScriptContext parameter:**

```kotlin
private fun injectBindings(
    context: Context,
    category: ScriptCategory,
    additionalBindings: Map<String, Any>
) {
    val bindings = context.getBindings("js")
    val scriptContext = ScriptContext.fromCategory(category)

    // Console, Runtime, wait(), and built-in modules are already injected

    // NEW: Inject extension APIs
    injectExtensionAPIs(bindings, context, scriptContext)

    // Inject Script.* for utility scripts
    if (category == ScriptCategory.UTILITY) {
        injectScriptContext(bindings, context, additionalBindings)
    }
    // ...
}
```

#### C. Add `injectExtensionAPIs()` method

```kotlin
/**
 * Inject extension APIs for the given script context.
 */
private fun injectExtensionAPIs(bindings: Value, context: Context, scriptContext: ScriptContext) {
    val extensionContext = createExtensionContext(context, scriptContext)

    ExtensionRegistry.getModulesForContext(scriptContext).forEach { registration ->
        try {
            val api = registration.config.apiFactory(extensionContext)
            val moduleName = registration.config.moduleName
            bindings.putMember("__extension_$moduleName", api)

            ConfigManager.debug("Injected extension API: $moduleName")
        } catch (e: Exception) {
            RhettJSCommon.LOGGER.error(
                "[RhettJS] Failed to inject extension API: ${registration.config.moduleName}",
                e
            )
        }
    }
}

/**
 * Create ExtensionContext for extensions.
 */
private fun createExtensionContext(context: Context, scriptContext: ScriptContext): RhettJSExtension.ExtensionContext {
    return RhettJSExtension.ExtensionContext(
        graalContext = context,
        server = com.rhett.rhettjs.world.WorldManager.getServer()
            ?: throw IllegalStateException("Server not initialized"),
        config = RhettJSExtension.RhettJSConfig(
            debug = ConfigManager.config.debug
        ),
        builtins = createBuiltinAPIs(context),
        scriptContext = scriptContext
    )
}

/**
 * Create BuiltinAPIs for extensions.
 */
private fun createBuiltinAPIs(context: Context): RhettJSExtension.BuiltinAPIs {
    return RhettJSExtension.BuiltinAPIs(
        // Managers
        worldManager = com.rhett.rhettjs.world.WorldManager,
        structureManager = com.rhett.rhettjs.structure.StructureNbtManager,
        largeStructureManager = com.rhett.rhettjs.structure.LargeStructureNbtManager,
        worldgenStructureManager = com.rhett.rhettjs.structure.WorldgenStructureManager,

        // Adapters
        playerAdapter = com.rhett.rhettjs.adapter.PlayerAdapter,
        callerAdapter = com.rhett.rhettjs.adapter.CallerAdapter,
        worldAdapter = com.rhett.rhettjs.adapter.WorldAdapter,

        // Helper functions (reference existing methods)
        convertFutureToPromise = ::convertFutureToPromise,
        createPositionObject = ::createPositionObject,
        createPositionFromBlockPos = { pos -> createPositionObject(Vec3.atCenterOf(pos)) },
        formatValue = ::formatValue,
        createProxyObject = { map -> ProxyObject.fromMap(map) }
    )
}
```

---

### 3. `FilesystemInitializer.kt`

**Location:** `common/src/main/kotlin/com/rhett/rhettjs/config/FilesystemInitializer.kt`

**Changes:**

#### A. Import ExtensionRegistry

```kotlin
import com.rhett.rhettjs.extension.ExtensionRegistry
```

#### B. Modify `extractTypeDefinitions()` (around line 74)

**Add extension type extraction:**

```kotlin
fun extractTypeDefinitions(resourceAccess: ResourceAccess) {
    val scriptsDir = getScriptsDirectory()
    val typesDir = scriptsDir.resolve("__types")

    // Create __types directory
    if (!typesDir.exists()) {
        Files.createDirectories(typesDir)
        RhettJSCommon.LOGGER.info("[RhettJS] Created __types directory")
    }

    // Extract built-in type definitions (existing code)
    val typeFiles = listOf(
        "rhettjs.d.ts", "types.d.ts", "runtime.d.ts",
        // ... rest of built-in types
    )

    typeFiles.forEach { filename ->
        // ... existing extraction code
    }

    // NEW: Extract extension type definitions
    ExtensionRegistry.getAllModules().forEach { registration ->
        registration.config.typeDefinitionProvider?.let { provider ->
            try {
                provider().forEach { (filename, content) ->
                    // Auto-namespace under module name
                    val moduleDir = typesDir.resolve(registration.config.moduleName)
                    Files.createDirectories(moduleDir)

                    val typeFile = moduleDir.resolve(filename)
                    Files.createDirectories(typeFile.parent)
                    typeFile.writeText(content)

                    RhettJSCommon.LOGGER.info(
                        "[RhettJS] Extracted type definition: ${registration.config.moduleName}/$filename"
                    )
                }
            } catch (e: Exception) {
                RhettJSCommon.LOGGER.warn(
                    "[RhettJS] Failed to extract types for ${registration.config.moduleName}: ${e.message}"
                )
            }
        }
    }
}
```

---

### 4. `ScriptSystemInitializer.kt`

**Location:** `common/src/main/kotlin/com/rhett/rhettjs/engine/ScriptSystemInitializer.kt`

**Changes:**

#### A. Add note about extension registration timing

**Add comment in `initializeStartupScripts()` (line 33):**

```kotlin
/**
 * Initialize startup and server scripts during mod initialization.
 * This runs early, BEFORE command registration and datapack load.
 *
 * NOTE: Extensions should register BEFORE this method is called
 * (during their mod initialization).
 *
 * STARTUP scripts: Early initialization (dimensions via rjs/data/ datapack JSON)
 * SERVER scripts: Event handlers, command registration (also re-executed on /reload)
 */
fun initializeStartupScripts() {
    // ... existing code
}
```

No code changes needed - extensions register before this runs naturally.

---

## Implementation Steps

### Phase 1: Core Infrastructure (Day 1)

1. **Create new files:**
   - [ ] `RhettJSExtension.kt`
   - [ ] `ScriptContext.kt`
   - [ ] `ExtensionRegistry.kt`

2. **Test compilation:**
   ```bash
   ./gradlew :common:build
   ```

### Phase 2: Integration (Day 2)

3. **Modify RhettJSFileSystem.kt:**
   - [ ] Add extension module access check
   - [ ] Add extension module content generation

4. **Modify GraalEngine.kt:**
   - [ ] Add `injectExtensionAPIs()`
   - [ ] Add `createExtensionContext()`
   - [ ] Add `createBuiltinAPIs()`
   - [ ] Modify `injectBindings()` to call extension injection

5. **Modify FilesystemInitializer.kt:**
   - [ ] Add extension type extraction

6. **Test compilation:**
   ```bash
   ./gradlew build
   ```

### Phase 3: Testing (Day 2-3)

7. **Create test extension:**
   - [ ] Create simple test extension in test code
   - [ ] Test module registration
   - [ ] Test API injection
   - [ ] Test type extraction

8. **Create example extension:**
   - [ ] Create "rhettjs-example-extension" mod
   - [ ] Test with actual Minecraft

9. **Test edge cases:**
   - [ ] Invalid module names
   - [ ] Duplicate registrations
   - [ ] Extension throws exception
   - [ ] Context filtering

### Phase 4: Documentation (Day 3)

10. **Create extension template repository**
11. **Write user-facing documentation**
12. **Update main README.md**

---

## Testing Strategy

### Unit Tests

Create `ExtensionRegistryTest.kt`:

```kotlin
class ExtensionRegistryTest {

    @BeforeEach
    fun setup() {
        ExtensionRegistry.clear()
    }

    @Test
    fun `register module successfully`() {
        val config = RhettJSExtension.ModuleConfig(
            moduleName = "test",
            apiFactory = { ProxyObject.fromMap(emptyMap()) }
        )

        ExtensionRegistry.register(config)

        assertNotNull(ExtensionRegistry.getModule("rhettjs/test"))
        assertNotNull(ExtensionRegistry.getModule("test"))
    }

    @Test
    fun `reject invalid module name`() {
        val config = RhettJSExtension.ModuleConfig(
            moduleName = "Test-Name",  // Uppercase not allowed
            apiFactory = { ProxyObject.fromMap(emptyMap()) }
        )

        assertThrows<IllegalArgumentException> {
            ExtensionRegistry.register(config)
        }
    }

    @Test
    fun `reject duplicate registration`() {
        val config = RhettJSExtension.ModuleConfig(
            moduleName = "test",
            apiFactory = { ProxyObject.fromMap(emptyMap()) }
        )

        ExtensionRegistry.register(config)

        assertThrows<IllegalArgumentException> {
            ExtensionRegistry.register(config)
        }
    }

    @Test
    fun `filter by script context`() {
        ExtensionRegistry.register(
            RhettJSExtension.ModuleConfig(
                moduleName = "startup-only",
                apiFactory = { ProxyObject.fromMap(emptyMap()) },
                availableIn = setOf(ScriptContext.STARTUP)
            )
        )

        ExtensionRegistry.register(
            RhettJSExtension.ModuleConfig(
                moduleName = "server-only",
                apiFactory = { ProxyObject.fromMap(emptyMap()) },
                availableIn = setOf(ScriptContext.SERVER)
            )
        )

        val startupModules = ExtensionRegistry.getModulesForContext(ScriptContext.STARTUP)
        assertEquals(1, startupModules.size)
        assertEquals("startup-only", startupModules[0].config.moduleName)

        val serverModules = ExtensionRegistry.getModulesForContext(ScriptContext.SERVER)
        assertEquals(1, serverModules.size)
        assertEquals("server-only", serverModules[0].config.moduleName)
    }
}
```

### Integration Test

Create test extension in `common/src/test/kotlin`:

```kotlin
object TestExtension {
    fun register() {
        RhettJSExtension.registerModule(
            RhettJSExtension.ModuleConfig(
                moduleName = "test-extension",
                apiFactory = { ctx ->
                    ProxyObject.fromMap(mapOf(
                        "hello" to ProxyExecutable { args ->
                            "Hello from test extension!"
                        },
                        "useWorldManager" to ProxyExecutable { _ ->
                            // Test that builtins are accessible
                            val manager = ctx.builtins.worldManager
                            "WorldManager accessible"
                        }
                    ))
                },
                typeDefinitionProvider = {
                    listOf("test.d.ts" to """
                        declare module 'rhettjs/test-extension' {
                            export function hello(): string;
                        }
                    """.trimIndent())
                },
                availableIn = setOf(ScriptContext.ALL)
            )
        )
    }
}
```

Test script (`rjs-test-scripts/testing/scripts/test-extension.js`):

```javascript
import TestExt from 'rhettjs/test-extension';

console.log(TestExt.hello());
console.log(TestExt.useWorldManager());
```

---

## Migration Path

### Existing Built-in APIs

**No migration needed** - built-in APIs continue to work as-is. Extension system runs in parallel.

### Future Refactoring

Consider migrating built-in APIs to use the same pattern (optional):

```kotlin
// Could refactor built-in APIs to use extension system internally
object BuiltinModules {
    fun registerAll() {
        RhettJSExtension.registerModule(
            RhettJSExtension.ModuleConfig(
                moduleName = "world",
                apiFactory = ::createWorldAPIProxy,
                // ...
            )
        )
        // ... other built-ins
    }
}
```

**Benefits:**
- Consistent code paths
- Easier testing
- Dogfooding the extension API

**Drawbacks:**
- Large refactor
- No immediate benefit

**Recommendation:** Keep built-ins separate initially, consider unification later.

---

## Error Handling

### Extension Initialization Failures

Extensions that fail to initialize should:
1. Log an error
2. Not crash RhettJS
3. Not be available for imports

```kotlin
try {
    val api = registration.config.apiFactory(extensionContext)
    bindings.putMember("__extension_$moduleName", api)
} catch (e: Exception) {
    RhettJSCommon.LOGGER.error(
        "[RhettJS] Failed to inject extension API: ${moduleName}. Extension will be unavailable.",
        e
    )
    // Don't rethrow - isolate failure
}
```

### Invalid Module Names

Validate early in `ExtensionRegistry.register()`:

```kotlin
if (!name.matches(Regex("^[a-z0-9-]+$"))) {
    throw IllegalArgumentException("Invalid module name: '$name'")
}
```

### Context Mismatches

When a script tries to import an unavailable extension:

```javascript
// In rjs/startup/, trying to import a COMMAND-only module
import Puppeteer from 'rhettjs/puppeteer';
// Error: Module 'rhettjs/puppeteer' is not available in STARTUP context
```

Implement in `RhettJSFileSystem.checkAccess()`:

```kotlin
val registration = ExtensionRegistry.getModule(moduleName)
if (registration != null) {
    val currentContext = getCurrentScriptContext() // Get from thread-local or context
    if (!registration.config.availableIn.contains(currentContext)) {
        throw IOException(
            "Module '${registration.fullModuleName}' is not available in $currentContext context"
        )
    }
    return
}
```

---

## Performance Considerations

### Registration Time

- Extensions register during mod initialization (happens once)
- No performance impact on script execution

### API Injection

- Happens once per context creation
- Negligible overhead (simple map lookup + function call)

### Module Resolution

- Virtual module generation is fast (string concatenation)
- Extension lookup is O(1) (HashMap)

**No performance concerns expected.**

---

## Security Considerations

### Trust Model

- Extensions are Minecraft mods (trusted code)
- No sandboxing needed
- Same trust level as RhettJS itself

### Namespace Isolation

- Extensions are auto-namespaced (`__types/{moduleName}/`)
- Prevents type definition collisions
- Prevents module name collisions

### Built-in Protection

- Extension module names cannot conflict with built-ins
- Validated in `ExtensionRegistry.validateModuleName()`

---

## Future Enhancements

### 1. Context Reset Callbacks

Allow extensions to cleanup on `/reload`:

```kotlin
data class ExtensionContext(
    // ... existing fields
    val onContextReset: ((()->Unit)) -> Unit
)
```

### 2. Inter-Extension Dependencies

Allow extensions to depend on other extensions:

```kotlin
data class ModuleConfig(
    // ... existing fields
    val dependencies: List<String> = emptyList()
)
```

Resolve dependencies via priority sorting.

### 3. Versioning

Add API version field:

```kotlin
data class ModuleConfig(
    // ... existing fields
    val apiVersion: String = "1.0.0"
)
```

Check compatibility with RhettJS version.

---

## Completion Checklist

- [ ] All files created
- [ ] All files modified
- [ ] Unit tests passing
- [ ] Integration test passing
- [ ] Example extension works in-game
- [ ] Documentation complete
- [ ] Template repository created
- [ ] Code reviewed
- [ ] Merged to main

---

**End of Implementation Guide**
