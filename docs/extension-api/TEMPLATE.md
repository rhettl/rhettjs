# RhettJS Extension Template Repository

**Repository Name:** `rhettjs-extension-template`
**Purpose:** Starter template for creating RhettJS extensions
**Target Audience:** Third-party mod developers

---

## Overview

The template repository provides a complete, working example of a RhettJS extension with:
- Multi-platform support (Fabric + NeoForge via Architectury)
- Example JavaScript API
- TypeScript definitions
- Gradle configuration
- Documentation
- Ready to use - just click "Use this template"

---

## Repository Structure

```
rhettjs-extension-template/
├── .github/
│   └── workflows/
│       └── build.yml                    # CI workflow
├── common/
│   └── src/main/
│       ├── kotlin/com/example/examplemod/
│       │   ├── ExampleMod.kt           # Main extension registration
│       │   └── ExampleAPI.kt           # Example API implementation
│       └── resources/
│           └── types/
│               ├── example.d.ts        # Main type definitions
│               └── types.d.ts          # Shared types
├── fabric/
│   └── src/main/
│       ├── kotlin/com/example/examplemod/
│       │   └── ExampleModFabric.kt     # Fabric entrypoint (3 lines)
│       └── resources/
│           └── fabric.mod.json         # Fabric mod metadata
├── neoforge/
│   └── src/main/
│       ├── kotlin/com/example/examplemod/
│       │   └── ExampleModNeoForge.kt   # NeoForge entrypoint (3 lines)
│       └── resources/
│           └── neoforge.mods.toml      # NeoForge mod metadata
├── gradle/
│   └── wrapper/                         # Gradle wrapper
├── .gitignore
├── build.gradle.kts                     # Root build script
├── gradle.properties                    # Version properties
├── settings.gradle.kts                  # Multi-project setup
├── stonecutter.gradle.kts              # Architectury configuration
├── LICENSE
└── README.md                            # Template instructions
```

---

## File Contents

### `README.md`

```markdown
# RhettJS Extension Template

This template provides everything you need to create a RhettJS extension mod that works with both Fabric and NeoForge.

## Quick Start

1. **Click "Use this template"** to create your own repository
2. **Clone your new repository**
3. **Find and replace** the following:
   - `com.example.examplemod` → Your package name (e.g., `com.yourname.yourmod`)
   - `example-mod` → Your mod ID (e.g., `awesome-puppets`)
   - `Example Mod` → Your mod name (e.g., `Awesome Puppets`)
   - `example` → Your module name for JavaScript imports (e.g., `puppets`)
4. **Update `gradle.properties`** with your mod version and details
5. **Implement your API** in `ExampleAPI.kt`
6. **Update type definitions** in `resources/types/`
7. **Build:** `./gradlew build`
8. **Test:** Copy JARs to Minecraft mods folder

## What This Template Provides

- ✅ Multi-platform support (Fabric + NeoForge)
- ✅ RhettJS extension registration
- ✅ Example JavaScript API
- ✅ TypeScript definitions
- ✅ Gradle build configuration
- ✅ GitHub Actions CI

## Project Structure

- `common/` - Shared code (your main API implementation)
- `fabric/` - Fabric-specific entrypoint (just calls common code)
- `neoforge/` - NeoForge-specific entrypoint (just calls common code)

## Usage in JavaScript

```javascript
import Example from 'rhettjs/example';

Example.hello("World");
```

## Documentation

See RhettJS extension documentation at:
- API Reference: https://github.com/rhettl/rhettjs/blob/main/dev-docs/extension-api/API_REFERENCE.md
- Design Doc: https://github.com/rhettl/rhettjs/blob/main/dev-docs/extension-api/DESIGN.md

## License

[Your License Here]
```

---

### `common/src/main/kotlin/.../ExampleMod.kt`

```kotlin
package com.example.examplemod

import com.rhett.rhettjs.extension.RhettJSExtension
import com.rhett.rhettjs.extension.ScriptContext

/**
 * Main extension registration.
 * This is called from both Fabric and NeoForge entrypoints.
 */
object ExampleMod {

    const val MOD_ID = "example-mod"

    fun init() {
        // Register your JavaScript module with RhettJS
        RhettJSExtension.registerModule(
            RhettJSExtension.ModuleConfig(
                // Module name - users will import as "rhettjs/example"
                moduleName = "example",

                // Factory to create your API
                apiFactory = { ctx -> ExampleAPI(ctx).toProxyObject() },

                // TypeScript definitions
                typeDefinitionProvider = {
                    listOf(
                        "example.d.ts" to loadResource("/types/example.d.ts"),
                        "types.d.ts" to loadResource("/types/types.d.ts")
                    )
                },

                // Which script contexts can access this API
                availableIn = setOf(
                    ScriptContext.SERVER,
                    ScriptContext.COMMAND
                ),

                // Priority (leave at 0 unless you depend on another extension)
                priority = 0
            )
        )
    }

    private fun loadResource(path: String): String {
        return ExampleMod::class.java.getResourceAsStream(path)?.bufferedReader()?.readText()
            ?: throw IllegalStateException("Resource not found: $path")
    }
}
```

---

### `common/src/main/kotlin/.../ExampleAPI.kt`

```kotlin
package com.example.examplemod

import com.rhett.rhettjs.extension.RhettJSExtension
import org.graalvm.polyglot.proxy.ProxyObject
import org.graalvm.polyglot.proxy.ProxyExecutable

/**
 * Example JavaScript API implementation.
 */
class ExampleAPI(private val ctx: RhettJSExtension.ExtensionContext) {

    /**
     * Convert this API to a GraalVM ProxyObject for JavaScript.
     */
    fun toProxyObject(): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            // Simple function
            "hello" to ProxyExecutable { args ->
                val name = args.getOrNull(0)?.asString() ?: "World"
                "Hello, $name from Example Mod!"
            },

            // Function using RhettJS internals
            "getWorldInfo" to ProxyExecutable { _ ->
                val overworld = ctx.builtins.worldManager.getOverworld()
                ctx.builtins.createProxyObject(mapOf(
                    "dimension" to "overworld",
                    "time" to overworld.dayTime
                ))
            },

            // Async function (returns Promise)
            "delayedGreeting" to ProxyExecutable { args ->
                val name = args.getOrNull(0)?.asString() ?: "World"

                val future = java.util.concurrent.CompletableFuture.supplyAsync {
                    Thread.sleep(1000)
                    "Delayed hello, $name!"
                }

                ctx.builtins.convertFutureToPromise(ctx.graalContext, future)
            },

            // Property access to config
            "debugMode" to ctx.config.debug
        ))
    }
}
```

---

### `common/src/main/resources/types/example.d.ts`

```typescript
/**
 * Example RhettJS Extension API
 */
declare module 'rhettjs/example' {
    import { Position } from 'rhettjs/types';

    /**
     * World information object
     */
    export interface WorldInfo {
        readonly dimension: string;
        readonly time: number;
    }

    /**
     * Say hello to someone.
     * @param name - Name to greet (default: "World")
     * @returns Greeting message
     */
    export function hello(name?: string): string;

    /**
     * Get information about the current world.
     * @returns World information
     */
    export function getWorldInfo(): WorldInfo;

    /**
     * Say hello with a 1-second delay.
     * @param name - Name to greet (default: "World")
     * @returns Promise resolving to greeting message
     */
    export function delayedGreeting(name?: string): Promise<string>;

    /**
     * Whether RhettJS debug mode is enabled.
     */
    export const debugMode: boolean;
}

/**
 * Barrel export for convenience (allows "import Example from 'Example'")
 */
declare module 'Example' {
    export * from 'rhettjs/example';
}
```

---

### `fabric/src/main/kotlin/.../ExampleModFabric.kt`

```kotlin
package com.example.examplemod

import net.fabricmc.api.ModInitializer

/**
 * Fabric entrypoint.
 * Just calls the common initialization code.
 */
class ExampleModFabric : ModInitializer {
    override fun onInitialize() {
        ExampleMod.init()
    }
}
```

---

### `fabric/src/main/resources/fabric.mod.json`

```json
{
  "schemaVersion": 1,
  "id": "example-mod",
  "version": "${version}",
  "name": "Example Mod",
  "description": "An example RhettJS extension",
  "authors": ["Your Name"],
  "contact": {
    "homepage": "https://github.com/yourname/example-mod",
    "sources": "https://github.com/yourname/example-mod"
  },
  "license": "MIT",
  "icon": "icon.png",
  "environment": "*",
  "entrypoints": {
    "main": [
      {
        "adapter": "kotlin",
        "value": "com.example.examplemod.ExampleModFabric"
      }
    ]
  },
  "mixins": [],
  "depends": {
    "fabricloader": ">=0.16.0",
    "fabric-language-kotlin": ">=1.12.0",
    "minecraft": "~1.21.1",
    "rhettjs": ">=0.1.0"
  }
}
```

---

### `neoforge/src/main/kotlin/.../ExampleModNeoForge.kt`

```kotlin
package com.example.examplemod

/**
 * NeoForge entrypoint.
 * Just calls the common initialization code.
 */
class ExampleModNeoForge {
    init {
        ExampleMod.init()
    }
}
```

---

### `neoforge/src/main/resources/neoforge.mods.toml`

```toml
modLoader = "kotlinforforge"
loaderVersion = "[5,)"
license = "MIT"

[[mods]]
modId = "example-mod"
version = "${version}"
displayName = "Example Mod"
description = "An example RhettJS extension"
logoFile = "icon.png"
credits = "Created using RhettJS Extension Template"
authors = "Your Name"

[[dependencies.example-mod]]
modId = "rhettjs"
type = "required"
versionRange = "[0.1.0,)"
ordering = "AFTER"
side = "BOTH"
```

---

### `gradle.properties`

```properties
# Mod Info
mod_version=1.0.0
maven_group=com.example
archives_base_name=example-mod

# Minecraft
minecraft_version=1.21.1

# Dependencies
rhettjs_version=0.1.0
fabric_loader_version=0.16.0
fabric_api_version=0.100.0+1.21.1
fabric_kotlin_version=1.12.0+kotlin.2.0.20
neoforge_version=21.1.0

# Gradle
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
```

---

### `build.gradle.kts`

```kotlin
plugins {
    kotlin("jvm") version "2.0.20"
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("dev.architectury.loom") version "1.7-SNAPSHOT" apply false
}

architectury {
    minecraft = providers.gradleProperty("minecraft_version").get()
}

subprojects {
    apply(plugin = "dev.architectury.loom")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        // TODO: Add RhettJS maven repository when published
    }

    dependencies {
        "minecraft"("com.mojang:minecraft:${property("minecraft_version")}")
        "mappings"("net.fabricmc:yarn:${property("minecraft_version")}+build.latest:v2")
    }
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "architectury-plugin")

    version = property("mod_version")!!
    group = property("maven_group")!!

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    java {
        withSourcesJar()
    }
}
```

---

## Setup Instructions for Template Users

### Step 1: Use Template

1. Go to `rhettjs-extension-template` on GitHub
2. Click "Use this template" → "Create a new repository"
3. Name your repository (e.g., `rhettjs-puppeteer`)
4. Clone your new repository

### Step 2: Customize

Replace these strings throughout the project:

| Find | Replace With | Example |
|------|-------------|---------|
| `com.example.examplemod` | Your package | `com.yourname.puppeteer` |
| `example-mod` | Your mod ID | `rhettjs-puppeteer` |
| `Example Mod` | Your mod name | `RhettJS Puppeteer` |
| `example` | JavaScript module name | `puppeteer` |
| `ExampleMod` | Your main class | `PuppeteerMod` |
| `ExampleAPI` | Your API class | `PuppeteerAPI` |

Update `gradle.properties`:
```properties
mod_version=0.1.0
maven_group=com.yourname
archives_base_name=rhettjs-puppeteer
```

### Step 3: Implement Your API

1. **Edit `ExampleAPI.kt`** (rename to `YourAPI.kt`)
   - Add your functions
   - Use `ctx.builtins.*` to access RhettJS internals
   - Convert Minecraft types to JS (anti-corruption layer)

2. **Edit `types/example.d.ts`** (rename to `yourmod.d.ts`)
   - Define TypeScript interfaces
   - Add JSDoc comments
   - Export your API functions

3. **Update module name** in `ExampleMod.kt`:
   ```kotlin
   moduleName = "yourmod"
   ```

### Step 4: Build

```bash
./gradlew build
```

Outputs:
- `fabric/build/libs/yourmod-fabric-0.1.0.jar`
- `neoforge/build/libs/yourmod-neoforge-0.1.0.jar`

### Step 5: Test

1. Copy JARs to `.minecraft/mods/`
2. Ensure RhettJS is also installed
3. Create test script in `rjs/scripts/test.js`:
   ```javascript
   import YourMod from 'rhettjs/yourmod';
   console.log(YourMod.hello("World"));
   ```
4. Run: `/rjs run test`

---

## Advanced Features

### Using RhettJS Managers

```kotlin
"spawnEntity" to ProxyExecutable { args ->
    val worldManager = ctx.builtins.worldManager
    val level = worldManager.getOverworld()

    // Async operation
    val future = CompletableFuture.supplyAsync {
        // Spawn entity logic
        "Entity spawned"
    }

    ctx.builtins.convertFutureToPromise(ctx.graalContext, future)
}
```

### Using Adapters

```kotlin
"getPlayer" to ProxyExecutable { args ->
    val playerName = args[0].asString()
    val player = ctx.server.playerList.getPlayerByName(playerName)

    if (player != null) {
        // Convert to JS using adapter
        ctx.builtins.playerAdapter.toJS(player, ctx.graalContext)
    } else {
        null
    }
}
```

### Multiple Type Files

```kotlin
typeDefinitionProvider = {
    listOf(
        "puppeteer.d.ts" to loadResource("/types/puppeteer.d.ts"),
        "types.d.ts" to loadResource("/types/types.d.ts"),
        "controllers/bot.d.ts" to loadResource("/types/controllers/bot.d.ts")
    )
}
```

Files written to:
- `__types/puppeteer/puppeteer.d.ts`
- `__types/puppeteer/types.d.ts`
- `__types/puppeteer/controllers/bot.d.ts`

---

## Publishing

### CurseForge / Modrinth

1. Build: `./gradlew build`
2. Upload both JARs:
   - `yourmod-fabric-x.x.x.jar`
   - `yourmod-neoforge-x.x.x.jar`
3. Mark RhettJS as required dependency

### Maven

Configure publishing in `build.gradle.kts`:

```kotlin
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.property("archives_base_name").toString()
            version = project.version.toString()

            from(components["java"])
        }
    }
}
```

---

## Troubleshooting

### "Module not found"

**Problem:** JavaScript import fails
**Solution:** Check module name matches `moduleName` in registration

### "Extension API failed to inject"

**Problem:** Error during extension initialization
**Solution:** Check logs for exception, ensure RhettJS is installed

### "Types not found in IDE"

**Problem:** No autocomplete
**Solution:**
1. Ensure `.d.ts` files are in `resources/types/`
2. Check they're being loaded in `typeDefinitionProvider`
3. Run game once to extract types to `__types/`

### Build fails with "unresolved reference"

**Problem:** Can't find RhettJS classes
**Solution:**
1. Ensure RhettJS dependency is added to `build.gradle.kts`
2. Check RhettJS version matches
3. Refresh Gradle: `./gradlew --refresh-dependencies`

---

## Example Extensions

### Minimal Extension (Hello World)

See template default implementation.

### Complex Extension (Puppeteer)

See [examples/kotlin/PuppeteerExtension.kt](../examples/kotlin/PuppeteerExtension.kt).

---

## Support

- **RhettJS Documentation:** https://github.com/rhettl/rhettjs
- **Extension API Reference:** https://github.com/rhettl/rhettjs/blob/main/dev-docs/extension-api/API_REFERENCE.md
- **Issues:** https://github.com/rhettl/rhettjs/issues

---

**End of Template Documentation**
