# RhettJS Extension API - Design Specification

**Version:** 1.0 Draft
**Last Updated:** 2026-01-11

---

## Table of Contents

1. [Problem Statement](#problem-statement)
2. [Goals](#goals)
3. [Architecture Overview](#architecture-overview)
4. [Registration System](#registration-system)
5. [Script Context System](#script-context-system)
6. [Extension Context & Built-in APIs](#extension-context--built-in-apis)
7. [Type Definition System](#type-definition-system)
8. [Module Resolution](#module-resolution)
9. [Multi-Platform Support](#multi-platform-support)
10. [Examples](#examples)

---

## Problem Statement

Third-party mod developers want to extend RhettJS by adding custom JavaScript APIs. For example:

- **RhettJS-Puppeteer**: Adds mannequin/NPC control APIs
- **RhettJS-WorldEdit**: Adds world editing tools
- **RhettJS-Combat**: Adds combat simulation APIs

Currently, RhettJS has **zero extensibility** - all APIs are hardcoded:
- `GraalEngine.kt:1962-1984` - All APIs hardcoded in `injectBuiltinModules()`
- `RhettJSFileSystem.kt:39-59` - Module names in hardcoded `BUILT_IN_MODULES` set
- `FilesystemInitializer.kt:78-121` - TypeScript files in hardcoded list

Third-party mods cannot add new JavaScript APIs without fragile Mixins or ASM.

---

## Goals

### Primary Goals

1. **Simple Registration** - One function call to register a new JavaScript module
2. **Multi-Platform** - Works with Architectury (Fabric + NeoForge from single codebase)
3. **Access to Internals** - Extensions can use RhettJS's managers, adapters, and helpers
4. **Context-Aware** - Control which execution contexts have access to APIs
5. **Type-Safe** - Extensions provide TypeScript definitions that integrate seamlessly

### Secondary Goals

6. **Template Repository** - Provide starter template to encourage ecosystem growth
7. **Anti-Corruption Pattern** - Guide extensions to follow same best practices as RhettJS
8. **Failure Isolation** - One extension failing shouldn't crash RhettJS or other extensions

### Non-Goals

- **Hot-reload** - Extensions register once at mod initialization (not plugin-style hot reload)
- **Sandboxing** - Extensions are trusted code (they're Minecraft mods)
- **Version Management** - No dependency resolution between extensions (rely on mod loader)

---

## Architecture Overview

### Current RhettJS Architecture

```
JavaScript
    ↓
GraalEngine (API Proxies)
    ↓
Managers (WorldManager, StructureNbtManager)
    ↓
Adapters (WorldAdapter - Anti-Corruption Layer)
    ↓
Business Logic (StructureBuilder, pure functions)
    ↓
Models (Pure data classes)
```

### Extension Integration Points

Extensions integrate at the **GraalEngine** level, same as built-in APIs:

```
JavaScript
    ↓
┌─────────────────────────────┐
│ GraalEngine                 │
│  - Built-in APIs (World,    │
│    Commands, etc.)          │
│  - Extension APIs           │ ← NEW: Extension registration
│    (Puppeteer, etc.)        │
└─────────────────────────────┘
    ↓
Extension can use:
  - WorldManager, StructureNbtManager
  - WorldAdapter, PlayerAdapter
  - convertFutureToPromise(), helpers
```

**Key Insight:** Extensions use the same layer as RhettJS built-ins, with access to all internal utilities.

---

## Registration System

### Registration API

Extensions register by calling a single function:

```kotlin
RhettJSExtension.registerModule(
    RhettJSExtension.ModuleConfig(
        moduleName: String,
        apiFactory: (ExtensionContext) -> ProxyObject,
        typeDefinitionProvider: (() -> List<Pair<String, String>>)?,
        availableIn: Set<ScriptContext>,
        priority: Int
    )
)
```

### ModuleConfig Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `moduleName` | `String` | Module name for imports (e.g., `"puppeteer"` → `import from 'rhettjs/puppeteer'`) |
| `apiFactory` | `(ExtensionContext) -> ProxyObject` | Factory function to create the API proxy object |
| `typeDefinitionProvider` | `(() -> List<Pair<String, String>>)?` | Optional function providing TypeScript definitions as (filename, content) pairs |
| `availableIn` | `Set<ScriptContext>` | Which script execution contexts this API is available in (default: `ALL`) |
| `priority` | `Int` | Initialization priority - higher values initialize first (default: 0) |

### Type Definition Provider Details

The `typeDefinitionProvider` returns a list of `(filename, content)` pairs:

```kotlin
typeDefinitionProvider = {
    listOf(
        "puppeteer.d.ts" to javaClass.getResourceAsStream("/types/puppeteer.d.ts")!!.readText(),
        "types.d.ts" to javaClass.getResourceAsStream("/types/types.d.ts")!!.readText(),
        "controllers/bot.d.ts" to javaClass.getResourceAsStream("/types/controllers/bot.d.ts")!!.readText()
    )
}
```

**Auto-namespacing:** Files are automatically written to `__types/{moduleName}/` to prevent collisions:
- `__types/puppeteer/puppeteer.d.ts`
- `__types/puppeteer/types.d.ts`
- `__types/puppeteer/controllers/bot.d.ts`

**Flexibility:** Extensions can:
- Read from JAR resources (shown above)
- Generate definitions programmatically
- Include subdirectories in filenames

---

## Script Context System

### ScriptContext Enum

```kotlin
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
        fun fromCategory(category: ScriptCategory): ScriptContext = when(category) {
            ScriptCategory.STARTUP -> STARTUP
            ScriptCategory.SERVER -> SERVER
            ScriptCategory.UTILITY -> COMMAND
            ScriptCategory.MODULES -> ALL
        }
    }
}
```

### Context Mapping

| Directory | ScriptCategory | ScriptContext | World Available? | Notes |
|-----------|---------------|---------------|------------------|-------|
| `rjs/startup/` | STARTUP | STARTUP | ❌ No | Server starting, dimensions being registered |
| `rjs/server/` | SERVER | SERVER | ✅ Yes | Datapack registration, event handlers |
| `rjs/scripts/` | UTILITY | COMMAND | ✅ Yes | Command-invoked, has caller context |
| `rjs/client/` | N/A | CLIENT | N/A | Future: client-side execution |

### Built-in API Availability

```kotlin
// Runtime API - available everywhere (no server/world needed)
Runtime.availableIn = setOf(ScriptContext.ALL)

// Store API - available on server
Store.availableIn = setOf(ScriptContext.STARTUP, ScriptContext.SERVER, ScriptContext.COMMAND)

// World API - needs world loaded
World.availableIn = setOf(ScriptContext.SERVER, ScriptContext.COMMAND)

// Script API - only in command context (provides caller, argv)
Script.availableIn = setOf(ScriptContext.COMMAND)
```

### Extension Context Usage

Extensions declare which contexts they're available in:

```kotlin
// Puppeteer needs world loaded
availableIn = setOf(ScriptContext.SERVER, ScriptContext.COMMAND)

// Pure utility library - works everywhere
availableIn = setOf(ScriptContext.ALL)

// Dimension generator - only startup
availableIn = setOf(ScriptContext.STARTUP)
```

**Behavior:** If a script tries to import a module not available in its context, the import fails with a clear error message.

---

## Extension Context & Built-in APIs

### ExtensionContext

When an extension's `apiFactory` is called, it receives an `ExtensionContext`:

```kotlin
data class ExtensionContext(
    /** GraalVM context for JavaScript interop */
    val graalContext: Context,

    /** Minecraft server instance */
    val server: MinecraftServer,

    /** RhettJS configuration (debug mode, etc.) */
    val config: RhettJSConfig,

    /** Access to RhettJS built-in managers, adapters, and helpers */
    val builtins: BuiltinAPIs,

    /** Current script execution context */
    val scriptContext: ScriptContext
)
```

### BuiltinAPIs

Extensions have full access to RhettJS internals:

```kotlin
data class BuiltinAPIs(
    // === Managers ===
    /** World operations (async, server tick-based) */
    val worldManager: WorldManager,

    /** Structure file operations */
    val structureManager: StructureNbtManager,

    /** Large structure operations (chunked) */
    val largeStructureManager: LargeStructureNbtManager,

    /** Worldgen structure operations */
    val worldgenStructureManager: WorldgenStructureManager,

    // === Adapters (Anti-Corruption Layer) ===
    /** Convert ServerPlayer → JS object */
    val playerAdapter: PlayerAdapter,

    /** Convert CommandSourceStack → JS object */
    val callerAdapter: CallerAdapter,

    /** Convert Minecraft world types → JS objects */
    val worldAdapter: WorldAdapter,

    // === Helper Functions ===
    /** Convert CompletableFuture<T> → JavaScript Promise */
    val convertFutureToPromise: (Context, CompletableFuture<*>) -> Value,

    /** Convert Vec3 → JS {x, y, z} object */
    val createPositionObject: (Vec3) -> ProxyObject,

    /** Convert BlockPos → JS {x, y, z} object */
    val createPositionFromBlockPos: (BlockPos) -> ProxyObject,

    /** Format Value for logging (handles objects, arrays, etc.) */
    val formatValue: (Value) -> String,

    /** Create ProxyObject from Map */
    val createProxyObject: (Map<String, Any>) -> ProxyObject
)
```

### Usage Example

```kotlin
private fun createPuppeteerAPI(ctx: ExtensionContext): ProxyObject {
    val puppetManager = PuppeteerManager(ctx.server)

    return ProxyObject.fromMap(mapOf(
        "spawn" to ProxyExecutable { args ->
            // Use adapter to convert position
            val pos = args[0] // JS object {x, y, z}

            // Use manager for async world operations
            val future = puppetManager.spawnPuppet(pos)

            // Use helper to convert to Promise
            ctx.builtins.convertFutureToPromise(ctx.graalContext, future)
        },

        "list" to ProxyExecutable { _ ->
            // Use adapter to convert entities
            val puppets = puppetManager.getAllPuppets()
            puppets.map { puppet ->
                // Convert to JS using adapter pattern
                ctx.builtins.createProxyObject(mapOf(
                    "id" to puppet.uuid.toString(),
                    "name" to puppet.name,
                    "position" to ctx.builtins.createPositionObject(puppet.position())
                ))
            }
        }
    ))
}
```

---

## Type Definition System

### Type Definition Flow

1. **Extension provides .d.ts content** via `typeDefinitionProvider`
2. **RhettJS extracts to `__types/{moduleName}/`** during filesystem initialization
3. **User's IDE discovers types** automatically (or via reference directive)
4. **User gets autocomplete** for extension APIs

### Example Type Definition

```typescript
// puppeteer.d.ts
declare module 'rhettjs/puppeteer' {
    import { Position } from 'rhettjs/types';

    export interface Puppet {
        readonly id: string;
        readonly name: string;
        readonly position: Position;
    }

    export interface PuppetController {
        move(position: Position): Promise<void>;
        rotate(yaw: number, pitch: number): void;
        setSkin(texture: string): void;
        remove(): void;
    }

    export function spawn(
        position: Position,
        options?: { name?: string; skin?: string }
    ): Promise<Puppet>;

    export function control(id: string): PuppetController | null;
    export function list(): Puppet[];
}
```

### Barrel Export Support

Extensions can provide a barrel export for convenience:

```typescript
// index.d.ts
declare module 'Puppeteer' {
    export * from 'rhettjs/puppeteer';
}
```

Allows both import styles:
```javascript
import Puppeteer from 'rhettjs/puppeteer'; // Recommended
import Puppeteer from 'Puppeteer';         // Legacy style
```

---

## Module Resolution

### Current Module Resolution

RhettJS uses a custom `RhettJSFileSystem` that intercepts module imports:

**File:** `common/src/main/kotlin/com/rhett/rhettjs/engine/RhettJSFileSystem.kt`

**Key components:**
- `BUILT_IN_MODULES` set (line 40) - Hardcoded module names
- `checkAccess()` (line 205) - Validates module access
- `newByteChannel()` (line 271) - Generates virtual module content

### Extension Module Resolution

**Changes needed:**

1. **Check extension registry** in `checkAccess()`:
```kotlin
if (ExtensionRegistry.getModule(moduleName) != null) {
    return // Allow access
}
```

2. **Generate virtual module** in `newByteChannel()`:
```kotlin
val registration = ExtensionRegistry.getModule(moduleName)
if (registration != null) {
    return """
        const api = globalThis.__extension_${registration.config.moduleName};
        export default api;
    """.trimIndent().toByteArray()
}
```

3. **Inject API binding** in `GraalEngine.injectBuiltinModules()`:
```kotlin
ExtensionRegistry.getModulesForContext(scriptContext).forEach { registration ->
    val api = registration.config.apiFactory(extensionContext)
    bindings.putMember("__extension_${registration.config.moduleName}", api)
}
```

### Import Resolution Flow

```
User script: import Puppeteer from 'rhettjs/puppeteer'
    ↓
RhettJSFileSystem.checkAccess("/modules/rhettjs/puppeteer.mjs")
    ↓
ExtensionRegistry.getModule("rhettjs/puppeteer") → found
    ↓
RhettJSFileSystem.newByteChannel() generates:
    const api = globalThis.__extension_puppeteer;
    export default api;
    ↓
GraalVM executes generated module
    ↓
Returns globalThis.__extension_puppeteer (injected by GraalEngine)
```

---

## Multi-Platform Support

### Problem

Extensions should work with both Fabric and NeoForge without duplicating registration logic.

### Solution

**Extensions register ONCE in common code:**

```kotlin
// common/src/main/kotlin/.../PuppeteerMod.kt
object PuppeteerMod {
    fun init() {
        RhettJSExtension.registerModule(/* config */)
    }
}
```

**Loader-specific code is minimal boilerplate:**

**Fabric:**
```kotlin
// fabric/src/main/kotlin/.../PuppeteerFabric.kt
class PuppeteerFabric : ModInitializer {
    override fun onInitialize() {
        PuppeteerMod.init()
    }
}
```

**NeoForge:**
```kotlin
// neoforge/src/main/kotlin/.../PuppeteerNeoForge.kt
class PuppeteerNeoForge {
    init {
        PuppeteerMod.init()
    }
}
```

**Timeline:**
1. Minecraft loads mods (extension mod + RhettJS)
2. Extension's mod init calls `PuppeteerMod.init()`
3. `PuppeteerMod.init()` registers with RhettJS via `registerModule()`
4. RhettJS's `ScriptSystemInitializer.initializeStartupScripts()` runs
5. RhettJS injects all registered modules (built-in + extensions)

**No discovery mechanism needed** - static registration via direct function calls.

---

## Examples

### Minimal Extension

```kotlin
object MinimalExtension {
    fun init() {
        RhettJSExtension.registerModule(
            RhettJSExtension.ModuleConfig(
                moduleName = "example",
                apiFactory = { ctx ->
                    ProxyObject.fromMap(mapOf(
                        "hello" to ProxyExecutable { args ->
                            val name = args.getOrNull(0)?.asString() ?: "World"
                            "Hello, $name!"
                        }
                    ))
                },
                typeDefinitionProvider = {
                    listOf("example.d.ts" to """
                        declare module 'rhettjs/example' {
                            export function hello(name?: string): string;
                        }
                    """.trimIndent())
                },
                availableIn = setOf(ScriptContext.ALL)
            )
        )
    }
}
```

**JavaScript usage:**
```javascript
import Example from 'rhettjs/example';
console.log(Example.hello("RhettJS")); // "Hello, RhettJS!"
```

### Full Puppeteer Extension

See [examples/kotlin/PuppeteerExtension.kt](./examples/kotlin/PuppeteerExtension.kt) for complete example.

---

## Implementation Checklist

### Phase 1: Core Infrastructure
- [ ] Create `RhettJSExtension.kt` with registration API
- [ ] Create `ExtensionRegistry.kt` for module tracking
- [ ] Create `ExtensionContext` and `BuiltinAPIs` data classes
- [ ] Create `ScriptContext` enum

### Phase 2: Integration
- [ ] Modify `RhettJSFileSystem.kt` to recognize extension modules
- [ ] Modify `GraalEngine.kt` to inject extension APIs
- [ ] Modify `FilesystemInitializer.kt` to extract extension .d.ts files
- [ ] Add `ScriptContext` parameter to execution paths

### Phase 3: Testing
- [ ] Create example extension (Puppeteer or similar)
- [ ] Test multi-context availability
- [ ] Test type definition extraction
- [ ] Test module resolution
- [ ] Test error handling (extension fails to load)

### Phase 4: Documentation & Templates
- [ ] Create extension template repository
- [ ] Write extension developer guide
- [ ] Write API reference documentation
- [ ] Create example extension tutorial

---

## Related Issues

- #1 - Refactor: Split GraalEngine.kt (prerequisite for clean extension integration)
- TBD - Create dedicated issue for extension API implementation

---

## Appendix: Alternative Designs Considered

### Auto-Discovery via ServiceLoader

**Rejected because:**
- Requires `META-INF/services/` files (extra boilerplate)
- Not standard in Minecraft modding ecosystem
- Harder to debug than explicit registration

### Platform-Specific Registration

**Rejected because:**
- Forces extensions to duplicate logic for Fabric + NeoForge
- Goes against multi-platform philosophy
- More work for extension developers

### Plugin-Style Hot Reload

**Rejected because:**
- Adds significant complexity (classloader isolation, dependency graphs)
- Not needed for Minecraft mod use case
- Mods already handle loading/unloading via mod loader

---

**End of Design Specification**
