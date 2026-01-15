# RhettJS Client Scripts - UI & Event Testing

This directory contains client-side JavaScript test scripts for the RhettJS UI system and client event handling.

## What are Client Scripts?

Client scripts run **only on the client side** (player's computer) and have access to:
- UI system (screens, widgets, rendering)
- Client events (keyboard, mouse, client tick)
- Client player information
- Client-side rendering and display

They **cannot** directly access server-side APIs like World, Commands registration, or server events.

## Test Scripts

### 1. `test-ui-display.js`

**Purpose**: Tests all UI widget types and rendering capabilities.

**How to Test**:
1. Join the world
2. Press **F3+T** to reload client scripts
3. Press **U** key to open the test screen
4. Press **ESC** to close

**What to Verify**:
- ✓ Labels display with correct colors, shadows, and scales
- ✓ Panels show backgrounds and borders correctly
- ✓ Buttons render properly (enabled/disabled states)
- ✓ Images display textures (if available)
- ✓ Dynamic updates work (counter button)
- ✓ All widgets are positioned correctly
- ✓ Close button hides the screen

**Expected Output**:
```
[UI Display Test] Loading...
[UI Display Test] Loaded! Press "U" key to open test screen.
[UI Display Test] Screen shown
```

---

### 2. `test-click-events.js`

**Purpose**: Tests button click events and client→server communication.

**How to Test**:
1. Ensure `server/test-ui-commands.js` is loaded (auto-loads on server start)
2. Join the world
3. Press **F3+T** to reload client scripts
4. Press **C** key to open the test screen
5. Click various buttons
6. Observe chat feedback

**What to Verify**:
- ✓ Client-side clicks work immediately (click counter)
- ✓ Commands are sent to server and processed
- ✓ Store API reads/writes work
- ✓ Hybrid pattern (Store + Command) works
- ✓ Rapid clicking is handled correctly
- ✓ Dynamic button states update (toggle button)

**Expected Output**:
```
[Click Events Test] Loading...
[Click Events Test] Loaded! Press "C" key to open test screen.
[Click Events Test] Make sure server handler (test-ui-commands.js) is loaded!
[Click Events Test] Screen shown
```

**Server Console Output**:
```
[UI Test] Simple command from Player: hello
[UI Test] Param command from Player: 42 → 84
[UI Test] Store read from Player: {message: "Hello from client!"}
```

---

### 3. `test-key-events.js`

**Purpose**: Tests keyboard input detection and event handling.

**How to Test**:
1. Join the world
2. Press **F3+T** to reload client scripts
3. Press **K** key to open the test screen
4. Press various keys and observe the event log
5. Try modifier keys (Shift, Ctrl, Alt)

**What to Verify**:
- ✓ Key presses are detected and logged
- ✓ Key releases are detected (action = 0)
- ✓ Key repeats are detected (action = 2, when holding key)
- ✓ Modifier keys (Shift, Ctrl, Alt) are detected correctly
- ✓ Statistics update correctly
- ✓ Send stats to server command works

**Expected Output**:
```
[Key Events Test] Loading...
[Key Events Test] Loaded! Press "K" key to open test screen.
[Key Events Test] Screen shown
[Key Test] PRESS: A (scan=30, mods=None)
[Key Test] RELEASE: A (scan=30, mods=None)
[Key Test] PRESS: B (scan=48, mods=SHIFT)
```

**Notes**:
- Some keys may be blocked by Minecraft (e.g., E for inventory, T for chat)
- Function keys (F1-F12) may have system bindings
- Best keys to test: Letters (A-Z), numbers (0-9), arrow keys

---

## Client-Server Communication

These test scripts demonstrate three communication patterns:

### Pattern 1: Commands (Simple)
```javascript
// CLIENT: Send command
Client.player.runCommand('uitest:simple hello');

// SERVER: Handle command
Commands.register('uitest:simple', (caller, args) => {
    caller.sendSuccess(`Received: ${args[0]}`);
});
```

### Pattern 2: Store API (Data Sharing)
```javascript
// CLIENT: Write data
Store.set(`data:${Client.player.uuid}`, { value: 42 });

// SERVER: Read data
const data = Store.get(`data:${player.uuid}`);
```

### Pattern 3: Hybrid (Recommended for Complex Actions)
```javascript
// CLIENT: Store complex data + trigger command
Store.set(`action:${Client.player.uuid}`, complexData);
Client.player.runCommand('uitest:process-action');

// SERVER: Retrieve and process
const data = Store.get(`action:${caller.uuid}`);
// Process data...
Store.delete(`action:${caller.uuid}`);
```

See `dev-docs/CLIENT-SERVER-COMMUNICATION.md` for detailed patterns.

---

## Keybinds

| Key | Action |
|-----|--------|
| **U** | Toggle UI Display Test screen |
| **C** | Toggle Click Events Test screen |
| **K** | Open Key Events Test screen |
| **ESC** | Close any open screen (standard Minecraft) |
| **F3+T** | Reload all client scripts (standard Minecraft) |

---

## Troubleshooting

### Client scripts not loading
- Check console for errors: `[ClientScriptInitializer]` messages
- Verify scripts are in `rjs-test-scripts/client/` directory
- Try F3+T to force reload

### Commands not working
- Ensure server script `server/test-ui-commands.js` is loaded
- Check server console for `[UI Test Commands]` message
- Verify command syntax (see server script for available commands)

### UI not displaying
- Check if screen is actually shown: `screen.isVisible()`
- Verify widget positions are within screen bounds
- Check console for UI-related errors
- Try different screen resolution

### Key events not firing
- Some keys are blocked by Minecraft (inventory, chat, etc.)
- Modifier detection may vary by OS/keyboard
- Check console for `[Key Test]` debug messages

---

## Testing Checklist

Use this checklist for complete testing:

### UI Display Test
- [ ] Screen opens with 'U' key
- [ ] Title and subtitle display correctly
- [ ] All color labels show correct colors
- [ ] Panels have backgrounds and borders
- [ ] Buttons are visible (enabled/disabled states)
- [ ] Dynamic counter button updates on click
- [ ] Close button works

### Click Events Test
- [ ] Screen opens with 'C' key
- [ ] Simple click counter increments
- [ ] Commands are sent to server (check chat feedback)
- [ ] Store write/read/trigger buttons work
- [ ] Hybrid pattern button sends complex data
- [ ] Rapid click test handles fast clicking
- [ ] Toggle button changes state
- [ ] Close button works

### Key Events Test
- [ ] Screen opens with 'K' key
- [ ] Current key display updates on key press
- [ ] Action type shows PRESS/RELEASE/REPEAT correctly
- [ ] Modifiers (Shift, Ctrl, Alt) are detected
- [ ] Event log shows recent key events
- [ ] Statistics update correctly
- [ ] Clear log button works
- [ ] Reset stats button works
- [ ] Send stats to server button works

---

## Next Steps

After verifying these tests work:

1. **Explore Communication Patterns**: Try implementing your own client-server interactions
2. **Create Custom UIs**: Build menus, HUDs, or dialogue systems
3. **Advanced Key Handling**: Implement custom keybinds for your mod features
4. **Client Events**: Use tick events for animations or periodic updates

---

## Related Documentation

- `dev-docs/CLIENT-SERVER-COMMUNICATION.md` - Communication patterns guide
- `dev-docs/CLIENT-SCRIPTS-DESIGN.md` - Client scripts architecture
- `dev-docs/UI-SYSTEM-DESIGN.md` - UI system design (if exists)
- TypeScript definitions: `__types/rhettjs.d.ts` - Full API reference

---

## Feedback

If you encounter issues or have suggestions for these tests:
1. Check the console for error messages
2. Verify script syntax and imports
3. Test in a clean world without other mods
4. Report issues with relevant console output
