# RhettJS UI System Design
*Based on analysis of Cobblemon's UI architecture*

## Cobblemon Analysis Summary

### Architecture Patterns

**Widget Composition System**:
- Base interface: `CobblemonRenderable` extends Minecraft's `Renderable`
- Complex UIs built from smaller composable widgets
- Example: `DialogueScreen` contains `DialogueBox`, `DialogueNameWidget`, `DialoguePortraitWidget`, `DialogueOptionWidget`, `DialogueTimerWidget`
- Each widget handles its own rendering, input, and state

**Key Components Observed**:

1. **Summary Screen** (Pokémon status UI):
   - Modular tab system (INFO, MOVES, STATS, MARKS)
   - 3D model rendering with rotation
   - Interactive widgets: party roster, move management, nickname editing
   - Drag-and-drop reordering
   - Server sync via network packets

2. **Dialogue System**:
   - `DialogueScreen` orchestrates conversation flow
   - Speaker management (NPC portraits, names)
   - MoLang scripting integration for client-side actions
   - "Gibber" system for character-by-character sound effects
   - Timed responses with countdown widget
   - Multiple input types: text entry, selectable options
   - Scrollable text with custom scrollbar rendering

3. **Storage Widget** (PC/Inventory):
   - Grid layout management with calculated positions
   - Slot-based interaction (selection, swapping, dragging)
   - State tracking for grabbed items and selections
   - Conditional rendering (confirmation dialogs)
   - Event propagation to server

### Technical Approach

- **Layout**: Manual positioning with constants and calculated offsets
- **State**: Each widget maintains its own state + parent coordination
- **Rendering**: Layer-based (background → content → overlays)
- **Input**: Event propagation through widget hierarchy
- **Sync**: Network packets for server-side state changes
- **Text**: Advanced rendering with word wrap, scrolling, color codes

---

## RhettJS UI System Design

### Core Principles

1. **JavaScript-First**: Entire UI defined and controlled via JS API
2. **Anti-Corruption Layer**: All APIs return pure JS objects/primitives
3. **Modular & Composable**: Small building blocks for complex UIs
4. **Declarative**: Define what the UI looks like, not how to build it
5. **Event-Driven**: Callbacks for user interactions

### Architecture

#### Layer 1: Core UI Framework (Kotlin)

**Base Classes**:
```kotlin
// Base widget interface
interface RhettWidget {
    fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float)
    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean
    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean
    fun tick()
}

// Container for widgets
class RhettScreen(val id: String) : Screen {
    private val widgets: MutableList<RhettWidget>
    private val callbacks: MutableMap<String, Value> // GraalVM callbacks

    fun addWidget(widget: RhettWidget)
    fun removeWidget(id: String)
    fun invokeCallback(eventName: String, data: Any)
}

// Widget manager - singleton
object UIManager {
    private val screens: MutableMap<String, RhettScreen>

    fun createScreen(id: String): RhettScreen
    fun showScreen(id: String)
    fun hideScreen(id: String)
    fun getActiveScreen(): RhettScreen?
}
```

**Widget Types**:
```kotlin
// Simple button
class ButtonWidget(
    val id: String,
    val x: Int, val y: Int,
    val width: Int, val height: Int,
    val text: String,
    val onClick: Value? // JS callback
) : RhettWidget

// Text label
class LabelWidget(
    val id: String,
    val x: Int, val y: Int,
    val text: String,
    val color: Int
) : RhettWidget

// Image/texture
class ImageWidget(
    val id: String,
    val x: Int, val y: Int,
    val width: Int, val height: Int,
    val texture: ResourceLocation
) : RhettWidget

// Text input
class TextInputWidget(
    val id: String,
    val x: Int, val y: Int,
    val width: Int,
    val placeholder: String,
    val onChange: Value?
) : RhettWidget

// Container for layout
class PanelWidget(
    val id: String,
    val x: Int, val y: Int,
    val width: Int, val height: Int,
    val backgroundColor: Int,
    val children: MutableList<RhettWidget>
) : RhettWidget

// HUD overlay (always visible)
class HUDWidget(
    val id: String,
    val anchor: Anchor, // TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, etc.
    val offsetX: Int, val offsetY: Int,
    val children: MutableList<RhettWidget>
) : RhettWidget

enum class Anchor {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}
```

#### Layer 2: Dialogue System (Kotlin)

```kotlin
// Dialogue box for NPC conversations
class DialogueScreen(
    val speakerName: String,
    val speakerTexture: ResourceLocation?,
    val messages: List<DialoguePage>
) : Screen {
    private var currentPage: Int = 0
    private val textBox: DialogueTextBox
    private val optionsPanel: DialogueOptionsPanel?

    fun advance()
    fun selectOption(index: Int)
}

data class DialoguePage(
    val text: String,
    val options: List<DialogueOption>? = null,
    val autoAdvance: Boolean = false,
    val delayMs: Int = 0
)

data class DialogueOption(
    val text: String,
    val value: String // returned to JS callback
)

// Builder for dialogue flows
class DialogueBuilder {
    fun speaker(name: String, texture: String? = null): DialogueBuilder
    fun addPage(text: String): DialogueBuilder
    fun addPageWithOptions(text: String, options: List<DialogueOption>): DialogueBuilder
    fun onComplete(callback: Value): DialogueBuilder
    fun show(player: Player)
}
```

#### Layer 3: JavaScript API

**UI API** (`rhettjs/ui.d.ts`):
```typescript
declare module 'rhettjs/ui' {
    export interface UIScreen {
        readonly id: string;

        // Widget management
        addButton(options: ButtonOptions): string; // returns widget ID
        addLabel(options: LabelOptions): string;
        addImage(options: ImageOptions): string;
        addTextInput(options: TextInputOptions): string;
        addPanel(options: PanelOptions): string;

        removeWidget(widgetId: string): void;
        updateWidget(widgetId: string, updates: Partial<WidgetOptions>): void;

        // Screen lifecycle
        show(): void;
        hide(): void;
        isVisible(): boolean;

        // Events
        on(event: 'shown' | 'hidden' | 'tick', callback: () => void): void;
    }

    export interface ButtonOptions {
        id?: string;
        x: number;
        y: number;
        width: number;
        height: number;
        text: string;
        onClick?: () => void;
        texture?: string; // optional custom texture
    }

    export interface LabelOptions {
        id?: string;
        x: number;
        y: number;
        text: string;
        color?: number; // hex color
        shadow?: boolean;
    }

    export interface ImageOptions {
        id?: string;
        x: number;
        y: number;
        width: number;
        height: number;
        texture: string; // resource location
    }

    export interface TextInputOptions {
        id?: string;
        x: number;
        y: number;
        width: number;
        placeholder?: string;
        maxLength?: number;
        onChange?: (text: string) => void;
    }

    export interface PanelOptions {
        id?: string;
        x: number;
        y: number;
        width: number;
        height: number;
        backgroundColor?: number;
        borderColor?: number;
        children?: string[]; // widget IDs
    }

    // Main UI namespace
    export const UI: {
        createScreen(id: string): UIScreen;
        getScreen(id: string): UIScreen | undefined;
        removeScreen(id: string): void;
    };

    export default UI;
}
```

**Dialogue API** (`rhettjs/dialogue.d.ts`):
```typescript
declare module 'rhettjs/dialogue' {
    export interface DialoguePage {
        text: string;
        options?: DialogueOption[];
        autoAdvance?: boolean;
        delayMs?: number;
    }

    export interface DialogueOption {
        text: string;
        value: string;
    }

    export interface DialogueOptions {
        speaker: string;
        speakerTexture?: string; // resource location or player UUID
        pages: DialoguePage[];
        onComplete?: (selectedOptions: string[]) => void;
    }

    export const Dialogue: {
        // Show dialogue to specific player
        show(playerUuid: string, options: DialogueOptions): void;

        // Show to all online players
        showAll(options: DialogueOptions): void;

        // Close active dialogue for player
        close(playerUuid: string): void;
    };

    export default Dialogue;
}
```

**HUD API** (`rhettjs/hud.d.ts`):
```typescript
declare module 'rhettjs/hud' {
    export type Anchor =
        | 'TOP_LEFT' | 'TOP_CENTER' | 'TOP_RIGHT'
        | 'CENTER_LEFT' | 'CENTER' | 'CENTER_RIGHT'
        | 'BOTTOM_LEFT' | 'BOTTOM_CENTER' | 'BOTTOM_RIGHT';

    export interface HUDElement {
        readonly id: string;

        updateText(text: string): void;
        updateTexture(texture: string): void;
        setVisible(visible: boolean): void;
        remove(): void;
    }

    export interface HUDTextOptions {
        id?: string;
        anchor: Anchor;
        offsetX?: number;
        offsetY?: number;
        text: string;
        color?: number;
        scale?: number;
    }

    export interface HUDImageOptions {
        id?: string;
        anchor: Anchor;
        offsetX?: number;
        offsetY?: number;
        texture: string;
        width: number;
        height: number;
    }

    export const HUD: {
        // Add persistent HUD elements
        addText(options: HUDTextOptions): HUDElement;
        addImage(options: HUDImageOptions): HUDElement;

        // Get existing element
        getElement(id: string): HUDElement | undefined;

        // Remove element
        removeElement(id: string): void;

        // Clear all HUD elements
        clear(): void;
    };

    export default HUD;
}
```

### Usage Examples

#### Example 1: Simple Button UI
```javascript
import UI from 'rhettjs/ui';

const screen = UI.createScreen('settings-menu');

screen.addPanel({
    id: 'background',
    x: 50,
    y: 50,
    width: 200,
    height: 150,
    backgroundColor: 0x333333
});

screen.addLabel({
    id: 'title',
    x: 100,
    y: 60,
    text: 'Settings',
    color: 0xFFFFFF
});

screen.addButton({
    id: 'toggle-pvp',
    x: 75,
    y: 100,
    width: 150,
    height: 20,
    text: 'Toggle PVP',
    onClick: () => {
        Commands.execute('gamerule pvp toggle');
        screen.hide();
    }
});

screen.addButton({
    id: 'close',
    x: 75,
    y: 130,
    width: 150,
    height: 20,
    text: 'Close',
    onClick: () => screen.hide()
});

// Show to player
screen.show();
```

#### Example 2: NPC Dialogue
```javascript
import Dialogue from 'rhettjs/dialogue';

const player = World.getPlayer('Steve');

Dialogue.show(player.uuid, {
    speaker: 'fishKetchun',
    speakerTexture: 'rhettjs:textures/npc/fisherman.png',
    pages: [
        {
            text: "Hello, I'm fishKetchun! Would you like to buy some fish?"
        },
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
        const purchase = choices[1]; // Second page choice
        if (purchase === 'cod') {
            Commands.execute(`give ${player.name} minecraft:cod`);
        } else if (purchase === 'salmon') {
            Commands.execute(`give ${player.name} minecraft:salmon`);
        }
    }
});
```

#### Example 3: HUD Overlay
```javascript
import HUD from 'rhettjs/hud';

// Add quest tracker
const questText = HUD.addText({
    id: 'quest-tracker',
    anchor: 'TOP_RIGHT',
    offsetX: -10,
    offsetY: 10,
    text: 'Quest: Find 10 diamonds\n0/10 collected',
    color: 0xFFD700,
    scale: 0.8
});

// Update as player progresses
let diamondsFound = 0;
setInterval(() => {
    questText.updateText(`Quest: Find 10 diamonds\n${diamondsFound}/10 collected`);
}, 1000);

// Add health/status indicator
HUD.addImage({
    id: 'status-icon',
    anchor: 'TOP_LEFT',
    offsetX: 10,
    offsetY: 10,
    texture: 'rhettjs:textures/gui/status_healthy.png',
    width: 16,
    height: 16
});
```

### Implementation Priority

**Phase 1: Core UI Framework**
- [ ] `RhettScreen` and `RhettWidget` base classes
- [ ] `UIManager` singleton
- [ ] Basic widgets: `ButtonWidget`, `LabelWidget`, `PanelWidget`
- [ ] JavaScript API bindings for `UI` namespace
- [ ] TypeScript definitions

**Phase 2: Extended Widgets**
- [ ] `ImageWidget` with texture support
- [ ] `TextInputWidget` for user input
- [ ] Layout helpers (grid, stack, flex-like)

**Phase 3: HUD System**
- [ ] `HUDWidget` with anchor positioning
- [ ] Persistent HUD rendering
- [ ] JavaScript API for HUD management

**Phase 4: Dialogue System**
- [ ] `DialogueScreen` implementation
- [ ] `DialogueTextBox` with scrolling
- [ ] `DialogueOptionsPanel` for choices
- [ ] Speaker portraits
- [ ] JavaScript API for dialogue flows

**Phase 5: Advanced Features**
- [ ] Animations and transitions
- [ ] Sound effects integration
- [ ] Custom textures and themes
- [ ] Form validation helpers
- [ ] Tooltip system

---

## Design Decisions

### Why Not Copy Cobblemon Directly?

1. **Complexity**: Cobblemon's system is tightly coupled to Pokémon mechanics
2. **MoLang**: We use JavaScript/GraalVM, not MoLang
3. **Manual Layouts**: Their manual positioning works for fixed UIs, but we want flexible, programmable layouts
4. **Java Objects**: They expose Minecraft types; we need anti-corruption layer

### Our Advantages

1. **JavaScript-Native**: Entire UI programmable from scripts
2. **Simpler**: Focus on essential widgets, not comprehensive toolkit
3. **Modular**: Import only what you need (`rhettjs/ui`, `rhettjs/dialogue`, `rhettjs/hud`)
4. **Type-Safe**: Full TypeScript definitions from day one

### Inspired Elements from Cobblemon

1. **Widget Composition**: Build complex UIs from simple pieces ✅
2. **Event Callbacks**: User interactions trigger JS functions ✅
3. **Dialogue Flow**: Multi-page conversations with options ✅
4. **Scrollable Text**: Long content with custom scrollbars ✅
5. **Screen Management**: Create, show, hide screens programmatically ✅

### Novel RhettJS Features

1. **HUD Anchoring**: Responsive positioning (Cobblemon uses fixed coords)
2. **Declarative API**: Define UI structure in pure JS objects
3. **Module System**: Import UI, Dialogue, HUD separately
4. **Anti-Corruption**: Zero Minecraft types in JS (pure primitives/objects)
