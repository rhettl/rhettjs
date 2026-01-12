# RhettJS Extension API Reference

**Audience:** Third-party extension developers
**Version:** 1.0 Draft

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [RhettJSExtension](#rhettjsextension)
3. [ModuleConfig](#moduleconfig)
4. [ExtensionContext](#extensioncontext)
5. [BuiltinAPIs](#builtinapis)
6. [ScriptContext](#scriptcontext)
7. [Type Definition Guidelines](#type-definition-guidelines)
8. [Best Practices](#best-practices)

---

## Quick Start

### Minimal Extension

```kotlin
import com.rhett.rhettjs.extension.RhettJSExtension
import com.rhett.rhettjs.extension.ScriptContext
import org.graalvm.polyglot.proxy.ProxyObject
import org.graalvm.polyglot.proxy.ProxyExecutable

object MyExtension {
    fun init() {
        RhettJSExtension.registerModule(
            RhettJSExtension.ModuleConfig(
                moduleName = "mymod",
                apiFactory = { ctx ->
                    ProxyObject.fromMap(mapOf(
                        "hello" to ProxyExecutable { args ->
                            "Hello from MyMod!"
                        }
                    ))
                },
                typeDefinitionProvider = {
                    listOf("mymod.d.ts" to """
                        declare module 'rhettjs/mymod' {
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

### Gradle Dependencies

```kotlin
// build.gradle.kts (common module)
dependencies {
    modCompileOnly("com.rhett:rhettjs-common:${rhettjs_version}")

    // GraalVM for ProxyObject/ProxyExecutable
    compileOnly("org.graalvm.polyglot:polyglot:24.1.1")
    compileOnly("org.graalvm.polyglot:js:24.1.1")
}
```

---

## RhettJSExtension

**Package:** `com.rhett.rhettjs.extension`

### Methods

#### `registerModule(config: ModuleConfig)`

Registers a new JavaScript module with RhettJS.

**Parameters:**
- `config: ModuleConfig` - Configuration for the module

**Throws:**
- `IllegalArgumentException` - If module name is invalid or already registered

**Example:**
```kotlin
RhettJSExtension.registerModule(
    RhettJSExtension.ModuleConfig(
        moduleName = "example",
        apiFactory = { ctx -> createExampleAPI(ctx) }
    )
)
```

**When to call:** During your mod's initialization, before RhettJS script system initializes.

---

## ModuleConfig

**Package:** `com.rhett.rhettjs.extension`

Configuration for a registered module.

### Constructor Parameters

```kotlin
data class ModuleConfig(
    val moduleName: String,
    val apiFactory: (ExtensionContext) -> ProxyObject,
    val typeDefinitionProvider: (() -> List<Pair<String, String>>)? = null,
    val availableIn: Set<ScriptContext> = setOf(ScriptContext.ALL),
    val priority: Int = 0
)
```

### Fields

#### `moduleName: String`

**Required**

The name used for imports in JavaScript. Users will import as `rhettjs/{moduleName}`.

**Example:**
```kotlin
moduleName = "puppeteer"
// JavaScript: import Puppeteer from 'rhettjs/puppeteer';
```

**Rules:**
- Must be lowercase alphanumeric with optional hyphens
- Cannot conflict with built-in modules (World, Commands, etc.)
- Should match your mod name for clarity

---

#### `apiFactory: (ExtensionContext) -> ProxyObject`

**Required**

Factory function that creates your API's ProxyObject.

**Parameters:**
- `ctx: ExtensionContext` - Provides access to GraalVM context, server, and RhettJS internals

**Returns:**
- `ProxyObject` - Your API as a GraalVM ProxyObject

**Example:**
```kotlin
apiFactory = { ctx ->
    ProxyObject.fromMap(mapOf(
        "spawn" to ProxyExecutable { args ->
            // Implementation using ctx
            val pos = args[0]
            spawnEntity(pos)
        },
        "config" to ctx.config.debug // Access RhettJS config
    ))
}
```

**Called when:** Each time scripts are loaded/reloaded.

---

#### `typeDefinitionProvider: (() -> List<Pair<String, String>>)?`

**Optional** (default: `null`)

Function that provides TypeScript type definitions.

**Returns:**
- `List<Pair<String, String>>` - List of (filename, content) pairs

**Example:**
```kotlin
typeDefinitionProvider = {
    listOf(
        "puppeteer.d.ts" to javaClass.getResourceAsStream("/types/puppeteer.d.ts")!!.readText(),
        "types.d.ts" to javaClass.getResourceAsStream("/types/types.d.ts")!!.readText()
    )
}
```

**Auto-namespacing:** Files are written to `__types/{moduleName}/` automatically:
- Input: `"puppeteer.d.ts"`
- Output: `__types/puppeteer/puppeteer.d.ts`

**Subdirectories:** You can include subdirectories in filenames:
```kotlin
"controllers/bot.d.ts" to content
// Written to: __types/puppeteer/controllers/bot.d.ts
```

---

#### `availableIn: Set<ScriptContext>`

**Optional** (default: `setOf(ScriptContext.ALL)`)

Which script execution contexts have access to this module.

**Example:**
```kotlin
// Only available in server/ and scripts/ (needs world loaded)
availableIn = setOf(ScriptContext.SERVER, ScriptContext.COMMAND)

// Available everywhere
availableIn = setOf(ScriptContext.ALL)

// Only in startup scripts
availableIn = setOf(ScriptContext.STARTUP)
```

**Behavior:** If a script tries to import your module from an unavailable context, the import fails with a clear error.

See [ScriptContext](#scriptcontext) for available contexts.

---

#### `priority: Int`

**Optional** (default: `0`)

Initialization priority. Higher values initialize earlier.

**Use case:** If your extension depends on another extension, use priority to control order.

**Example:**
```kotlin
// MyExtension depends on BaseExtension
BaseExtension.priority = 10
MyExtension.priority = 5  // Loads after BaseExtension
```

**Note:** Most extensions don't need to set this.

---

## ExtensionContext

**Package:** `com.rhett.rhettjs.extension`

Context provided to your `apiFactory` function.

### Fields

```kotlin
data class ExtensionContext(
    val graalContext: Context,
    val server: MinecraftServer,
    val config: RhettJSConfig,
    val builtins: BuiltinAPIs,
    val scriptContext: ScriptContext
)
```

#### `graalContext: Context`

GraalVM JavaScript context. Use for:
- Converting values: `graalContext.asValue(obj)`
- Evaluating JavaScript: `graalContext.eval("js", "...")`
- Creating promises: `builtins.convertFutureToPromise(graalContext, future)`

#### `server: MinecraftServer`

Minecraft server instance. Use for:
- Accessing levels: `server.getLevel(Level.OVERWORLD)`
- Server operations
- Retrieving players: `server.playerList.players`

#### `config: RhettJSConfig`

RhettJS configuration. Available fields:
- `config.debug: Boolean` - Debug mode enabled?

**Example:**
```kotlin
apiFactory = { ctx ->
    ProxyObject.fromMap(mapOf(
        "debugEnabled" to ctx.config.debug
    ))
}
```

#### `builtins: BuiltinAPIs`

Access to RhettJS managers, adapters, and helpers. See [BuiltinAPIs](#builtinapis).

#### `scriptContext: ScriptContext`

Current execution context (STARTUP, SERVER, COMMAND, etc.). Usually not needed - use `availableIn` instead.

---

## BuiltinAPIs

**Package:** `com.rhett.rhettjs.extension`

Provides access to RhettJS internals.

### Managers

#### `worldManager: WorldManager`

World operations (async, server tick-based).

**Key methods:**
```kotlin
// Get level
fun getOverworld(): ServerLevel
fun getLevel(dimensionKey: ResourceKey<Level>): ServerLevel?

// Async operations
fun getBlocksInRegion(level: ServerLevel, region: Region): CompletableFuture<List<PositionedBlock>>
fun setBlocksInRegion(level: ServerLevel, region: Region, blocks: List<PositionedBlock>): CompletableFuture<Int>
```

**Example:**
```kotlin
val future = ctx.builtins.worldManager.getBlocksInRegion(level, region)
ctx.builtins.convertFutureToPromise(ctx.graalContext, future)
```

---

#### `structureManager: StructureNbtManager`

Structure file operations (.nbt files).

**Key methods:**
```kotlin
fun save(name: String, structureData: Map<String, Any>): CompletableFuture<Boolean>
fun load(name: String): CompletableFuture<Map<String, Any>?>
fun list(): List<String>
```

---

#### `largeStructureManager: LargeStructureNbtManager`

Large structure operations (chunked, for >48k blocks).

**Similar API to structureManager but for larger structures.**

---

#### `worldgenStructureManager: WorldgenStructureManager`

Worldgen structure operations (vanilla structure format).

---

### Adapters

#### `playerAdapter: PlayerAdapter`

Converts `ServerPlayer` to JavaScript objects.

**Methods:**
```kotlin
fun toJS(player: ServerPlayer, context: Context): Value
```

**Example:**
```kotlin
val playerJS = ctx.builtins.playerAdapter.toJS(serverPlayer, ctx.graalContext)
// Returns: {name, uuid, health, position, sendMessage(), teleport(), ...}
```

---

#### `callerAdapter: CallerAdapter`

Converts `CommandSourceStack` to JavaScript objects.

**Methods:**
```kotlin
fun toJS(source: CommandSourceStack, context: Context): Value
```

---

#### `worldAdapter: WorldAdapter`

Converts Minecraft world types to JavaScript objects.

**Key methods:**
```kotlin
fun convertBlockState(blockState: BlockState): BlockData
fun convertNbtToMap(tag: CompoundTag): Map<String, Any>
```

---

### Helper Functions

#### `convertFutureToPromise`

```kotlin
val convertFutureToPromise: (Context, CompletableFuture<*>) -> Value
```

Converts Java `CompletableFuture` to JavaScript `Promise`.

**Example:**
```kotlin
"spawn" to ProxyExecutable { args ->
    val future = spawnEntityAsync(pos)
    ctx.builtins.convertFutureToPromise(ctx.graalContext, future)
}
```

**JavaScript usage:**
```javascript
const result = await MyMod.spawn(pos);
```

---

#### `createPositionObject`

```kotlin
val createPositionObject: (Vec3) -> ProxyObject
val createPositionFromBlockPos: (BlockPos) -> ProxyObject
```

Creates JavaScript `{x, y, z}` objects from Minecraft position types.

**Example:**
```kotlin
"getPosition" to ProxyExecutable { _ ->
    ctx.builtins.createPositionObject(entity.position())
}
```

**JavaScript result:**
```javascript
{x: 100.5, y: 64.0, z: 200.5}
```

---

#### `formatValue`

```kotlin
val formatValue: (Value) -> String
```

Formats GraalVM `Value` for logging (handles objects, arrays, etc.).

**Example:**
```kotlin
val formatted = ctx.builtins.formatValue(jsValue)
logger.info("Received: $formatted")
```

---

#### `createProxyObject`

```kotlin
val createProxyObject: (Map<String, Any>) -> ProxyObject
```

Creates a `ProxyObject` from a Kotlin `Map`.

**Example:**
```kotlin
val obj = ctx.builtins.createProxyObject(mapOf(
    "name" to "Steve",
    "health" to 20.0
))
```

**Equivalent to:**
```kotlin
ProxyObject.fromMap(mapOf(...))
```

---

## ScriptContext

**Package:** `com.rhett.rhettjs.extension`

Execution context enum.

```kotlin
enum class ScriptContext {
    ALL,      // Available everywhere
    STARTUP,  // rjs/startup/ - Server starting, before world loads
    SERVER,   // rjs/server/ - Datapack registration time
    COMMAND,  // rjs/scripts/ - Command execution (/rjs run)
    CLIENT    // rjs/client/ - Client-side (future)
}
```

### Context Details

| Context | Directory | World Available? | When It Runs |
|---------|-----------|------------------|--------------|
| `ALL` | All | Varies | All contexts |
| `STARTUP` | `rjs/startup/` | ❌ No | Server starting, dimensions being registered |
| `SERVER` | `rjs/server/` | ✅ Yes | Datapack registration, event handlers |
| `COMMAND` | `rjs/scripts/` | ✅ Yes | Command-invoked via `/rjs run` |
| `CLIENT` | `rjs/client/` | N/A | Client-side (future feature) |

### Choosing Contexts

**If your API needs world access:**
```kotlin
availableIn = setOf(ScriptContext.SERVER, ScriptContext.COMMAND)
```

**If your API is pure utility (no world/server needed):**
```kotlin
availableIn = setOf(ScriptContext.ALL)
```

**If your API registers dimensions:**
```kotlin
availableIn = setOf(ScriptContext.STARTUP)
```

---

## Type Definition Guidelines

### Basic Structure

```typescript
declare module 'rhettjs/yourmod' {
    // Import shared types from RhettJS
    import { Position, Player } from 'rhettjs/types';

    // Define your interfaces
    export interface YourType {
        readonly id: string;
        readonly position: Position;
    }

    // Export functions
    export function yourFunction(pos: Position): Promise<YourType>;
}
```

### Best Practices

1. **Import shared types:**
```typescript
import { Position, Player, BlockData } from 'rhettjs/types';
```

2. **Use JSDoc for documentation:**
```typescript
/**
 * Spawns a puppet at the given position.
 * @param pos - The position to spawn at
 * @param options - Optional spawn configuration
 * @returns Promise resolving to the spawned puppet
 */
export function spawn(pos: Position, options?: SpawnOptions): Promise<Puppet>;
```

3. **Mark readonly properties:**
```typescript
export interface Puppet {
    readonly id: string;      // Can't be modified
    readonly name: string;
    position: Position;       // Mutable (if it can change)
}
```

4. **Provide barrel exports for convenience:**
```typescript
// index.d.ts
declare module 'YourMod' {
    export * from 'rhettjs/yourmod';
}
```

5. **Use `Promise<T>` for async operations:**
```typescript
export function asyncOperation(): Promise<Result>;
```

---

## Best Practices

### Anti-Corruption Layer

**Always convert Minecraft types to JavaScript primitives/objects:**

❌ **Bad:**
```kotlin
"getPlayer" to ProxyExecutable { args ->
    serverPlayer  // Returns Java object!
}
```

✅ **Good:**
```kotlin
"getPlayer" to ProxyExecutable { args ->
    ctx.builtins.playerAdapter.toJS(serverPlayer, ctx.graalContext)
}
```

### Async Operations

**Use CompletableFuture + convertFutureToPromise for async operations:**

```kotlin
"grabStructure" to ProxyExecutable { args ->
    val future = CompletableFuture.supplyAsync {
        // Long-running operation
        performExpensiveOperation()
    }
    ctx.builtins.convertFutureToPromise(ctx.graalContext, future)
}
```

### Error Handling

**Throw exceptions with clear messages:**

```kotlin
"spawn" to ProxyExecutable { args ->
    if (args.isEmpty()) {
        throw IllegalArgumentException("spawn() requires a position argument")
    }
    // ...
}
```

**JavaScript sees:**
```javascript
try {
    MyMod.spawn();
} catch (e) {
    console.error(e.message); // "spawn() requires a position argument"
}
```

### Resource Management

**Clean up resources on context reset:**

RhettJS may reset the GraalVM context on `/reload`. If your extension holds resources:

```kotlin
// Register cleanup callback (future API)
ctx.onContextReset {
    yourManager.cleanup()
}
```

*(Note: This API is planned but not yet implemented)*

### Testing

**Test your extension across contexts:**

```javascript
// Test in rjs/startup/
import MyMod from 'rhettjs/mymod';
MyMod.someFunction();

// Test in rjs/server/
import MyMod from 'rhettjs/mymod';
MyMod.someFunction();

// Test in rjs/scripts/
import MyMod from 'rhettjs/mymod';
MyMod.someFunction();
```

---

## Complete Example

See [examples/kotlin/PuppeteerExtension.kt](./examples/kotlin/PuppeteerExtension.kt) for a full working example.

---

## Further Reading

- [DESIGN.md](./DESIGN.md) - Complete design specification
- [IMPLEMENTATION.md](./IMPLEMENTATION.md) - Implementation guide for RhettJS core developers
- [TEMPLATE.md](./TEMPLATE.md) - Extension template repository structure

---

**End of API Reference**
