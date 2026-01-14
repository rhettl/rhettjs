# RhettJS Development Documentation

This is an **orphan branch** containing development documentation and design documents created during Claude Code sessions. This branch is **separate from the main codebase** and is used for sharing design decisions, implementation plans, and architectural documentation between Claude sessions.

## Purpose

- **Share context between Claude sessions**: Future Claude instances can reference these documents to understand past design decisions
- **Document architectural decisions**: Keep track of why things were built a certain way
- **Implementation roadmaps**: Track features in progress and planned improvements
- **Not in main branch**: These are working documents, not user-facing documentation

## Branch Structure

```
dev-docs/
├── README.md                              ← You are here
├── CLIENT-SCRIPTS-DESIGN.md               ← Client-side script system design
├── UI-SYSTEM-DESIGN.md                    ← Original UI system research and planning
└── UI-SYSTEM-IMPLEMENTATION-SUMMARY.md    ← What was actually built
```

## Current Features

### ✅ Lightweight UI System (2026-01-14)

**Status**: Implementation complete, testing pending

RhettJS now has a JavaScript-programmable UI system inspired by Cobblemon:

- **Core Framework**: `RhettWidget`, `RhettScreen`, `UIManager`
- **Widgets**: Buttons, Labels, Panels, Images
- **JavaScript API**: `import UI from 'rhettjs/ui'`
- **TypeScript Definitions**: Full type support with JSDoc examples

**Committed**: Branch `claude/lightweight-ui-system-CN786`, commits:
- `9aac8eb` - Initial implementation
- `4e0a3d5` - Compilation fixes

**See**: `UI-SYSTEM-DESIGN.md` and `UI-SYSTEM-IMPLEMENTATION-SUMMARY.md`

### ⏳ Client Scripts System (Next)

**Status**: Design complete, implementation pending

To make the UI system actually work, RhettJS needs client-side script support:

**Problem**: UI APIs require client-side classes (`Minecraft.getInstance()`, `UIManager`) which don't exist on the server. Current `rjs/server/` scripts cannot access UI.

**Solution**: Implement `rjs/client/` scripts that run on each player's computer, similar to KubeJS's `client_scripts/`.

**See**: `CLIENT-SCRIPTS-DESIGN.md` for full implementation plan

---

## Implementation Rollout Strategy

### Phase 1: Client Script Infrastructure ⏳

**Goal**: Enable `rjs/client/` scripts to run on the client side

**Tasks**:
1. Add `CLIENT` to `ScriptCategory` enum
2. Create `ClientScriptInitializer` (client-only code)
3. Hook into Fabric/NeoForge client mod initializers
4. Add F3+T reload listener for resource reloads
5. Update `FilesystemInitializer` to create `client/` directory

**Branches**:
- `claude/client-scripts-infrastructure` (pending)

**Result**: Scripts in `rjs/client/` execute on client startup

---

### Phase 2: API Separation ⏳

**Goal**: Restrict UI API to client scripts only

**Tasks**:
1. Move `UIAPIProxy` injection to client-only context
2. Update `GraalEngine.injectBindings()` to check script category
3. Add validation: throw error if server script tries to use UI
4. Document API availability matrix (client/server/shared)

**Branches**:
- Part of `claude/client-scripts-infrastructure`

**Result**: `import UI from 'rhettjs/ui'` only works in `rjs/client/` scripts

---

### Phase 3: Testing & Examples 🎯

**Goal**: Verify UI system works in client scripts

**Tasks**:
1. Create example client script: `rjs/client/example-ui.js`
2. Test button clicks, label updates, panel rendering
3. Test F3+T reload
4. Verify multiplayer behavior (each client independent)
5. Document best practices

**Branches**:
- `claude/client-ui-examples` (pending)

**Example**:
```javascript
// rjs/client/menu.js
import UI from 'rhettjs/ui';

const screen = UI.createScreen('custom-menu');

screen.addButton({
    x: 100, y: 100,
    width: 100, height: 20,
    text: 'Click Me',
    onClick: () => console.log('Button clicked!')
});

screen.show();
```

---

### Phase 4: Extended Widgets 📋

**Goal**: Add more widget types for richer UIs

**Widgets to Add**:
- `TextInputWidget` - User text input fields
- `CheckboxWidget` - Toggle options
- `SliderWidget` - Numeric value selection
- `ScrollableWidget` - Scrollable content container
- `GridLayoutWidget` - Automatic grid layout
- `StackLayoutWidget` - Vertical/horizontal stacking

**Branches**:
- `claude/extended-widgets-*` (pending, one per widget type)

---

### Phase 5: HUD System 📋

**Goal**: Persistent on-screen overlays

**Features**:
- Anchor positioning (TOP_LEFT, CENTER, etc.)
- Dynamic updates without opening screens
- Always-visible elements
- Per-player HUD state

**JavaScript API**:
```javascript
import HUD from 'rhettjs/hud';

const questTracker = HUD.addText({
    anchor: 'TOP_RIGHT',
    offsetX: -10,
    offsetY: 10,
    text: 'Quest: Find 10 diamonds\n0/10 collected'
});

// Update anytime
questTracker.updateText('Quest: Find 10 diamonds\n5/10 collected');
```

**Branches**:
- `claude/hud-system` (pending)

---

### Phase 6: Dialogue System 📋

**Goal**: NPC conversations and story-driven interactions

**Features**:
- Multi-page dialogues
- Player choice options
- Speaker portraits
- Typewriter text effect
- Timed responses

**JavaScript API**:
```javascript
import Dialogue from 'rhettjs/dialogue';

Dialogue.show(playerUuid, {
    speaker: 'fishKetchun',
    speakerTexture: 'rhettjs:textures/npc/fisherman.png',
    pages: [
        { text: "Hello, I'm fishKetchun!" },
        {
            text: "What would you like?",
            options: [
                { text: 'Cod (10g)', value: 'cod' },
                { text: 'Salmon (15g)', value: 'salmon' },
                { text: 'Nothing', value: 'cancel' }
            ]
        }
    ],
    onComplete: (choices) => {
        if (choices[1] === 'cod') {
            Commands.execute(`give ${playerName} minecraft:cod`);
        }
    }
});
```

**Branches**:
- `claude/dialogue-system` (pending)

---

### Phase 7: Advanced UI Features 📋

**Goal**: Polish and advanced functionality

**Features**:
- Animations and transitions
- Sound effects integration
- Custom textures and themes
- Form validation helpers
- Tooltip system
- Drag-and-drop widgets
- Context menus

**Branches**:
- `claude/ui-animations` (pending)
- `claude/ui-sounds` (pending)
- `claude/ui-tooltips` (pending)

---

## Architecture Decisions

### UI System Design Philosophy

**Inspired by Cobblemon, adapted for RhettJS**:

| Aspect | Cobblemon | RhettJS |
|--------|-----------|---------|
| **Scripting** | MoLang | JavaScript/GraalVM |
| **API Design** | Internal Java API | External JavaScript API |
| **Type Safety** | None (MoLang) | Full TypeScript definitions |
| **Modularity** | Monolithic | ES6 modules (`import UI from 'rhettjs/ui'`) |
| **Anti-Corruption** | Exposes Minecraft types | Pure JS primitives only |
| **Positioning** | Manual pixel coords | JavaScript-programmable |

**Key Principles**:
1. **JavaScript-First**: Entire UI defined and controlled via JS
2. **Anti-Corruption Layer**: All APIs return pure JS objects/primitives
3. **Modular & Composable**: Small building blocks for complex UIs
4. **Declarative**: Define what the UI looks like, not how to build it
5. **Event-Driven**: Callbacks for user interactions

### Client vs Server vs Startup

| Category | When Runs | Access | Reload |
|----------|-----------|--------|--------|
| **Startup** | Mod init (both sides) | Limited | Requires restart |
| **Server** | Server start + datapack reload | World, Commands, Server | `/reload` |
| **Client** | Client start + resource reload | UI, Rendering, Keybinds | F3+T |

### API Availability Matrix

| API | Client Scripts | Server Scripts | Startup Scripts |
|-----|----------------|----------------|-----------------|
| `Runtime` | ✅ | ✅ | ✅ |
| `Store` | ✅ | ✅ | ✅ |
| `NBT` | ✅ | ✅ | ✅ |
| `Script` | ✅ | ✅ | ✅ |
| `Commands` | ❌ | ✅ | ❌ |
| `Server` | ❌ | ✅ | ❌ |
| `World` | ❌ | ✅ | ❌ |
| `UI` | ✅ | ❌ | ❌ |
| `HUD` | ✅ | ❌ | ❌ |
| `Dialogue` | ✅ | ❌ | ❌ |
| `Keybind` | ✅ | ❌ | ❌ |
| `Tooltip` | ✅ | ❌ | ❌ |

---

## Documents

### UI-SYSTEM-DESIGN.md

**Created**: 2026-01-14
**Purpose**: Original research and architectural design for the UI system

**Contents**:
- Analysis of Cobblemon's UI implementation
- RhettJS UI system architecture
- Widget design patterns
- JavaScript API specifications
- Usage examples
- Implementation phases

**Key Sections**:
- Cobblemon Analysis Summary
- RhettJS UI System Design
- Core Principles
- Architecture (Layers 1-3)
- JavaScript API (UI, Dialogue, HUD)
- Usage Examples
- Implementation Priority

---

### UI-SYSTEM-IMPLEMENTATION-SUMMARY.md

**Created**: 2026-01-14
**Purpose**: What was actually implemented vs. designed

**Contents**:
- Implementation overview
- Files created/modified
- Usage examples
- Testing status
- Commit details
- Future phases

**Key Sections**:
- What Was Built (Kotlin classes, JS API, TypeScript defs)
- Architecture Decisions (what we kept from Cobblemon, what we changed)
- Usage Examples
- File Changes
- Testing Status
- Next Steps

---

### CLIENT-SCRIPTS-DESIGN.md

**Created**: 2026-01-14
**Purpose**: Design for client-side script support

**Contents**:
- Why UI needs client scripts
- KubeJS research and examples
- RhettJS implementation plan
- API separation strategy
- Reload mechanism (F3+T)

**Key Sections**:
- Question: Client-Side vs Server-Side (with answer!)
- KubeJS Client Scripts Research
- RhettJS Implementation Plan (Phases 1-4)
- Architecture Decisions
- Implementation Checklist
- Future Client APIs
- Recommended Next Steps

---

## Contributing to This Branch

### For Future Claude Sessions

When starting a new session:

1. **Read relevant docs**: Check this README and related design docs
2. **Continue from where left off**: See "Current Features" section above
3. **Update this README**: When completing a phase, move it from ⏳ to ✅
4. **Add new docs**: Create new `.md` files for new features/designs
5. **Keep it clean**: Delete obsolete documents, archive completed investigations

### Adding New Documents

**Good candidates**:
- Design documents for new features
- Investigation summaries
- Implementation roadmaps
- Architecture decision records
- Cross-session TODO lists

**Not for this branch**:
- User-facing documentation (goes in `main` branch `docs/`)
- Code (stays in feature branches)
- Build artifacts
- Test results

### Updating Rollout Strategy

When starting a new phase:

```markdown
### Phase N: Feature Name ✅ or ⏳ or 📋

**Status**: [Design complete | In progress | Planning]

**Goal**: Brief description

**Tasks**:
- [ ] Task 1
- [x] Task 2 (completed)
- [ ] Task 3

**Branches**:
- `claude/feature-name-xyz` (merged)
- `claude/feature-name-abc` (in progress)

**Result**: What this phase achieves
```

Legend:
- ✅ Complete
- ⏳ In progress
- 📋 Planned

---

## Questions?

This branch is for Claude Code development context. For user-facing documentation, see the `main` branch `docs/` folder.

For questions about specific features, read the relevant design document above.

---

**Last Updated**: 2026-01-14 by Claude (Session: claude/lightweight-ui-system-CN786)
