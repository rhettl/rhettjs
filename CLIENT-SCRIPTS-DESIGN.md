# Client Scripts for RhettJS

**Date**: 2026-01-14
**Status**: Investigation Complete, Implementation Pending

## Question: Client-Side vs Server-Side

### ❓ Original Question

> Does the UI run as a `rjs/server/*.js` script or do we need to implement it as a client script?

### ✅ Answer

**UI operations MUST run in client scripts**, not server scripts. Here's why:

| Aspect | Server Scripts (`rjs/server/`) | Client Scripts (`rjs/client/`) |
|--------|-------------------------------|--------------------------------|
| **Execution** | Server JVM only | Client JVM only |
| **Access** | Server APIs, world state, commands | Client APIs, UI, rendering, keybinds |
| **Multiplayer** | Runs once on server | Runs separately on each client |
| **UI Operations** | ❌ Cannot access UI (UIManager, Minecraft.getInstance()) | ✅ Can access UI system |

**Example - What works where**:

```javascript
// ❌ BAD: This won't work in rjs/server/
import UI from 'rhettjs/ui';
const screen = UI.createScreen('menu');  // ERROR: UIManager not available on server!
screen.show();
```

```javascript
// ✅ GOOD: This works in rjs/client/
import UI from 'rhettjs/ui';
const screen = UI.createScreen('menu');  // ✓ Works - client-side only
screen.show();
```

### 🚨 Current Problem

**RhettJS does NOT have client script support yet!** We need to implement it.

---

## KubeJS Client Scripts Research

Based on research from [KubeJS documentation](https://kubejs.com/wiki/folder-structure/client-scripts) and [GitHub examples](https://github.com/KubeJScriptHub/KubeJS-Template):

### Folder Structure

KubeJS uses this structure:
```
.minecraft/kubejs/
├── client_scripts/   ← Client-side scripts (UI, tooltips, keybinds)
├── server_scripts/   ← Server-side scripts (recipes, loot, commands)
└── startup_scripts/  ← Startup scripts (runs before server/client)
```

### What Client Scripts Can Do

From [KubeJS wiki](https://wiki.latvian.dev/books/kubejs/page/list-of-events):

1. **UI & Rendering**:
   - Custom screens
   - HUD overlays
   - Tooltips

2. **JEI/REI Integration**:
   - Add/remove items from JEI
   - Custom recipe categories
   - Hide/show recipe types

3. **Client Events**:
   - `client.tick` - Every client tick
   - `client.paused` - When game pauses
   - `client.logged_in` - When player joins server
   - `client.logged_out` - When player leaves

4. **Keybinds**:
   - Register custom keybindings
   - Handle key press events

5. **Tooltips**:
   - Modify item tooltips dynamically
   - Add/remove tooltip lines

### Reloading

- **F3 + T**: Reloads all client scripts (triggers resource reload)
- **Command**: `/kubejs reload client_scripts`

### Example Client Script

From [GitHub examples](https://github.com/Nycto97/kubejs-scripts):

```javascript
// kubejs/client_scripts/tooltips.js
ItemEvents.tooltip(event => {
    event.add('minecraft:diamond', 'This is a shiny diamond!');
});

// kubejs/client_scripts/jei.js
JEIEvents.hideItems(event => {
    event.hide('minecraft:bedrock');
});
```

---

## RhettJS Implementation Plan

### Current State

RhettJS has:
```
rjs/
├── startup/   ← ScriptCategory.STARTUP
├── server/    ← ScriptCategory.SERVER
├── scripts/   ← ScriptCategory.UTILITY
└── modules/   ← ScriptCategory.MODULES
```

No client script support!

### Proposed Implementation

#### Phase 1: Add Client Script Category

**1. Update `ScriptCategory.kt`**:
```kotlin
enum class ScriptCategory(val dirName: String) {
    STARTUP("startup"),
    SERVER("server"),
    CLIENT("client"),  // ← NEW
    UTILITY("scripts"),
    MODULES("modules")
}
```

**2. Create `ClientScriptInitializer.kt`** (client-only):
```kotlin
// fabric/src/main/kotlin/com/rhett/rhettjs/client/ClientScriptInitializer.kt
object ClientScriptInitializer {
    fun initializeClientScripts() {
        val scriptsDir = getClientScriptsDirectory()
        ScriptRegistry.scan(scriptsDir)
        executeClientScripts()
    }

    fun executeClientScripts() {
        val clientScripts = ScriptRegistry.getScripts(ScriptCategory.CLIENT)
        clientScripts.forEach { script ->
            GraalEngine.executeScript(script, category = ScriptCategory.CLIENT)
        }
    }
}
```

**3. Hook into Fabric/NeoForge client initialization**:

*Fabric*:
```kotlin
// fabric/src/main/kotlin/com/rhett/rhettjs/client/RhettJSClient.kt
class RhettJSClient : ClientModInitializer {
    override fun onInitializeClient() {
        ClientScriptInitializer.initializeClientScripts()

        // Register F3+T reload listener
        ResourceManagerHelper.registerReloadListener(
            ClientScriptReloadListener()
        )
    }
}
```

*NeoForge*:
```kotlin
// neoforge/src/main/kotlin/com/rhett/rhettjs/client/RhettJSClientNeoForge.kt
@EventBusSubscriber(bus = Bus.MOD, value = [Dist.CLIENT])
object RhettJSClientNeoForge {
    @SubscribeEvent
    fun onClientSetup(event: FMLClientSetupEvent) {
        ClientScriptInitializer.initializeClientScripts()
    }
}
```

#### Phase 2: Resource Reload Support (F3+T)

**Create `ClientScriptReloadListener.kt`**:
```kotlin
class ClientScriptReloadListener : ResourceReloadListener {
    override fun reload(
        preparationBarrier: PreparableReloadListener.PreparationBarrier,
        resourceManager: ResourceManager,
        profiler: ProfilerFiller,
        executor: Executor
    ): CompletableFuture<Void> {
        return preparationBarrier.wait(Unit).thenRunAsync({
            RhettJSCommon.LOGGER.info("[RhettJS] Reloading client scripts (F3+T)...")
            GraalEngine.reset()  // Clear context
            ClientScriptInitializer.executeClientScripts()
        }, executor)
    }
}
```

#### Phase 3: Client-Only API Access

**Update `GraalEngine.kt`** to inject client APIs only when executing client scripts:

```kotlin
private fun injectBindings(context: Context, category: ScriptCategory, additionalBindings: Map<String, Any>) {
    val bindings = context.getBindings("js")

    // Always available
    injectBuiltinModules(bindings, context)
    injectRuntimeAPI(bindings)

    when (category) {
        ScriptCategory.CLIENT -> {
            // Client-only APIs
            val uiAPI = UIAPIProxy.create(context)
            bindings.putMember("__builtin_UI", uiAPI)
            // Future: HUD, Keybind, Tooltip APIs
        }
        ScriptCategory.SERVER -> {
            // Server-only APIs (already exist)
            // World, Commands, etc.
        }
        ScriptCategory.STARTUP -> {
            // Shared APIs
        }
        else -> {}
    }
}
```

#### Phase 4: Update FilesystemInitializer

**Add client directory creation**:
```kotlin
fun initialize(scriptsDir: Path) {
    // ... existing code ...

    // Create client_scripts directory
    val clientDir = scriptsDir.resolve("client")
    if (!clientDir.exists()) {
        Files.createDirectories(clientDir)
        Files.writeString(
            clientDir.resolve("example.js"),
            """
            // RhettJS Client Script Example
            // This script runs only on the client side
            // Reloadable with F3+T or /rjs reload client

            import UI from 'rhettjs/ui';

            console.log('Client script loaded!');

            // Example: Create a menu screen
            const menu = UI.createScreen('example-menu');
            menu.addLabel({
                x: 100,
                y: 100,
                text: 'Hello from client script!',
                color: 0xFFFFFF
            });
            """.trimIndent()
        )
    }
}
```

---

## Architecture Decisions

### Why Separate Client Scripts?

1. **Security**: Server scripts can't access client-only classes (UIManager, rendering)
2. **Multiplayer**: Client scripts run independently on each player's computer
3. **Reloading**: F3+T reloads client scripts without affecting server
4. **Clarity**: Developers know where UI code belongs

### Client vs Server vs Startup

| Category | When Runs | Access | Reload |
|----------|-----------|--------|--------|
| **Startup** | Mod init (both sides) | Limited | Requires restart |
| **Server** | Server start + datapack reload | World, Commands, Server | `/reload` |
| **Client** | Client start + resource reload | UI, Rendering, Keybinds | F3+T |

### Example Use Cases

**Client Scripts**:
- Custom UIs and menus
- HUD overlays
- Tooltips
- Keybindings
- Client-side visual effects
- JEI/REI integration

**Server Scripts**:
- Commands
- Game rules
- World generation
- Recipes
- Loot tables
- Server events

**Startup Scripts**:
- Dimension registration
- Block/item registration (future)
- Global configuration

---

## Implementation Checklist

### Core Infrastructure
- [ ] Add `CLIENT` to `ScriptCategory` enum
- [ ] Create `common/src/client/` directory for client-only code
- [ ] Create `ClientScriptInitializer.kt` (common, but client-only)
- [ ] Update `ScriptSystemInitializer` to skip client scripts on server

### Platform-Specific Hooks
- [ ] Fabric: Create `RhettJSClient.kt` client mod initializer
- [ ] Fabric: Register resource reload listener
- [ ] NeoForge: Create client-only event subscriber
- [ ] NeoForge: Register resource reload listener

### API Separation
- [ ] Move `UIAPIProxy` injection to client-only context
- [ ] Add API category checking in `GraalEngine.injectBindings()`
- [ ] Document which APIs are client/server/shared

### Reload Support
- [ ] Implement `ClientScriptReloadListener`
- [ ] Add `/rjs reload client` command
- [ ] Test F3+T reload

### Documentation
- [ ] Update `CLAUDE.md` with client script info
- [ ] Create example client scripts
- [ ] Document API availability matrix

---

## Future Client APIs

Once client script support is added, we can implement:

1. **HUD API** (from UI design document):
   ```javascript
   import HUD from 'rhettjs/hud';

   HUD.addText({
       anchor: 'TOP_RIGHT',
       offsetX: -10,
       offsetY: 10,
       text: 'Custom HUD'
   });
   ```

2. **Keybind API**:
   ```javascript
   import Keybind from 'rhettjs/keybind';

   Keybind.register('open_menu', 'key.keyboard.m', () => {
       UI.getScreen('menu')?.show();
   });
   ```

3. **Tooltip API**:
   ```javascript
   import Tooltip from 'rhettjs/tooltip';

   Tooltip.modify('minecraft:diamond', (tooltip) => {
       tooltip.add('§bShiny!');
   });
   ```

4. **Dialogue API** (from UI design):
   ```javascript
   import Dialogue from 'rhettjs/dialogue';

   Dialogue.show(playerUuid, {
       speaker: 'NPC',
       pages: [
           { text: 'Hello, traveler!' },
           {
               text: 'What do you need?',
               options: [
                   { text: 'Quest', value: 'quest' },
                   { text: 'Shop', value: 'shop' }
               ]
           }
       ]
   });
   ```

---

## Recommended Next Steps

1. **Immediate**: Implement basic client script loading
   - Add CLIENT category
   - Create client initializer
   - Hook into client mod entry points

2. **Short-term**: Add reload support
   - F3+T listener
   - `/rjs reload client` command

3. **Medium-term**: Move UI API to client-only
   - Test UI system in client scripts
   - Create example client UIs

4. **Long-term**: Add more client APIs
   - HUD
   - Keybinds
   - Tooltips
   - Dialogue

---

## References

- [KubeJS Client Scripts Documentation](https://kubejs.com/wiki/folder-structure/client-scripts)
- [KubeJS Events List](https://wiki.latvian.dev/books/kubejs/page/list-of-events)
- [KubeJS Template Repository](https://github.com/KubeJScriptHub/KubeJS-Template)
- [KubeJS Community Scripts](https://github.com/topics/kubejs-scripts)
