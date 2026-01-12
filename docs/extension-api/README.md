# RhettJS Extension API - Development Specification

**Status:** Design Phase
**Created:** 2026-01-11
**Purpose:** Specification for third-party module extensions to RhettJS

---

## Overview

This directory contains the complete design specification for allowing third-party Minecraft mods to extend RhettJS by adding custom JavaScript APIs.

**Example Use Case:** A mod developer creates "RhettJS-Puppeteer" that adds mannequin/NPC APIs to JavaScript:

```javascript
// User's script using the extension
import Puppeteer from 'rhettjs/puppeteer';

const puppet = await Puppeteer.spawn({ x: 100, y: 64, z: 100 });
Puppeteer.control(puppet.id).move({ x: 110, y: 64, z: 100 });
```

---

## Document Structure

### Core Documentation

1. **[DESIGN.md](./DESIGN.md)** - Complete design specification
   - Architecture overview
   - Registration system
   - Script context system
   - Type definition handling

2. **[API_REFERENCE.md](./API_REFERENCE.md)** - API reference for extension developers
   - `RhettJSExtension.registerModule()` API
   - `ExtensionContext` and `BuiltinAPIs` reference
   - `ScriptContext` enum documentation

3. **[IMPLEMENTATION.md](./IMPLEMENTATION.md)** - Implementation guide for RhettJS core
   - Files to modify
   - Code changes needed
   - Integration points

4. **[TEMPLATE.md](./TEMPLATE.md)** - Extension template repository specification
   - Template structure
   - Setup instructions
   - Best practices

### Example Code

- **[examples/kotlin/](./examples/kotlin/)** - Kotlin extension examples
- **[examples/java/](./examples/java/)** - Java extension examples

---

## Key Design Principles

1. **Single Registration Point** - Extensions register once in common code, not per-loader
2. **Multi-Platform by Default** - Works with Architectury multi-loader pattern
3. **Access to RhettJS Internals** - Extensions can use WorldManager, adapters, helpers
4. **Context-Aware** - Control which script contexts have access to APIs
5. **Type-Safe** - Extensions provide TypeScript definitions automatically
6. **Anti-Corruption Layer** - Extensions follow same pattern as RhettJS built-ins

---

## Quick Example

```kotlin
// Extension's common code
object PuppeteerExtension {
    fun init() {
        RhettJSExtension.registerModule(
            RhettJSExtension.ModuleConfig(
                moduleName = "puppeteer",
                apiFactory = { ctx ->
                    // Access RhettJS managers and helpers
                    val worldManager = ctx.builtins.worldManager
                    ProxyObject.fromMap(mapOf(
                        "spawn" to ProxyExecutable { args -> ... }
                    ))
                },
                typeDefinitionProvider = {
                    listOf("puppeteer.d.ts" to getResourceText("/types/puppeteer.d.ts"))
                },
                availableIn = setOf(ScriptContext.SERVER, ScriptContext.COMMAND)
            )
        )
    }
}
```

---

## Implementation Status

- [ ] Design specification complete
- [ ] API interfaces defined
- [ ] Core implementation
- [ ] Extension registry
- [ ] Module resolution integration
- [ ] Type definition extraction
- [ ] Example extension
- [ ] Template repository
- [ ] Documentation

---

## Related GitHub Issues

- #1 - Refactor: Split GraalEngine.kt (prerequisite)
- TBD - Create issue for extension API implementation

---

## Next Steps

1. Review and finalize design specification
2. Create GitHub issue for implementation
3. Implement core extension system
4. Create example "Puppeteer" extension
5. Create template repository
6. Write user-facing documentation
