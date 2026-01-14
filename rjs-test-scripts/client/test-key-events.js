/**
 * RhettJS Client Script Test: Key Events
 *
 * Tests keyboard input handling on the client:
 * - Key press detection
 * - Key release detection
 * - Key repeat detection
 * - Modifier keys (Shift, Ctrl, Alt)
 * - Multiple key combinations
 *
 * HOW TO TEST:
 * 1. Join the Minecraft world
 * 2. Press F3+T to reload client scripts
 * 3. Press 'K' key to open key test screen
 * 4. Press various keys and observe the event log
 *
 * EXPECTED RESULTS:
 * - All key presses should be detected and logged
 * - Action types (press/release/repeat) should be correct
 * - Modifier keys should be detected
 * - Event data should match key pressed
 */

import UI from 'rhettjs/ui';
import Client from 'rhettjs/client';

console.log('[Key Events Test] Loading...');

// Create test screen
const keyTestScreen = UI.createScreen('key-test');

// ====================================
// Title
// ====================================

keyTestScreen.addLabel({
    id: 'title',
    x: 20,
    y: 20,
    text: 'Keyboard Events Test',
    color: 0xFFFF00,
    shadow: true,
    scale: 1.5
});

keyTestScreen.addLabel({
    id: 'instructions',
    x: 20,
    y: 45,
    text: 'Press keys to test event detection (some keys may be blocked by Minecraft)',
    color: 0xAAAAAA,
    shadow: true,
    scale: 0.9
});

// ====================================
// Current Key Display
// ====================================

keyTestScreen.addPanel({
    id: 'current-key-panel',
    x: 20,
    y: 70,
    width: 600,
    height: 80,
    backgroundColor: 0x80000000,
    borderColor: 0xFF00FF00,
    borderWidth: 2
});

keyTestScreen.addLabel({
    id: 'current-key-label',
    x: 30,
    y: 80,
    text: 'Last Key: (none)',
    color: 0xFFFFFF,
    shadow: true,
    scale: 1.2
});

keyTestScreen.addLabel({
    id: 'current-action-label',
    x: 30,
    y: 105,
    text: 'Action: (none)',
    color: 0xAAAAAA,
    shadow: true,
    scale: 1.0
});

keyTestScreen.addLabel({
    id: 'current-modifiers-label',
    x: 30,
    y: 125,
    text: 'Modifiers: (none)',
    color: 0xAAAAAA,
    shadow: true,
    scale: 1.0
});

// ====================================
// Key Event Log
// ====================================

keyTestScreen.addLabel({
    id: 'log-header',
    x: 20,
    y: 160,
    text: 'Recent Key Events:',
    color: 0x00FFFF,
    shadow: true,
    scale: 1.0
});

const MAX_LOG_ENTRIES = 12;
const keyLog = [];

// Create log labels
for (let i = 0; i < MAX_LOG_ENTRIES; i++) {
    keyTestScreen.addLabel({
        id: `log-${i}`,
        x: 30,
        y: 180 + (i * 15),
        text: '',
        color: 0xCCCCCC,
        shadow: false,
        scale: 0.8
    });
}

function addLogEntry(text) {
    keyLog.unshift(text);
    if (keyLog.length > MAX_LOG_ENTRIES) {
        keyLog.pop();
    }

    // Update log labels
    for (let i = 0; i < MAX_LOG_ENTRIES; i++) {
        keyTestScreen.updateWidget(`log-${i}`, {
            text: keyLog[i] || ''
        });
    }
}

// ====================================
// Key Statistics
// ====================================

keyTestScreen.addPanel({
    id: 'stats-panel',
    x: 450,
    y: 160,
    width: 250,
    height: 200,
    backgroundColor: 0x80000000,
    borderColor: 0xFFFFFFFF,
    borderWidth: 1
});

keyTestScreen.addLabel({
    id: 'stats-title',
    x: 460,
    y: 170,
    text: 'Statistics',
    color: 0xFFFF00,
    shadow: true,
    scale: 1.0
});

const stats = {
    totalPresses: 0,
    totalReleases: 0,
    totalRepeats: 0,
    mostPressed: {},
    withModifiers: 0
};

keyTestScreen.addLabel({
    id: 'stats-presses',
    x: 460,
    y: 190,
    text: 'Presses: 0',
    color: 0x00FF00,
    shadow: false,
    scale: 0.9
});

keyTestScreen.addLabel({
    id: 'stats-releases',
    x: 460,
    y: 205,
    text: 'Releases: 0',
    color: 0xFF8800,
    shadow: false,
    scale: 0.9
});

keyTestScreen.addLabel({
    id: 'stats-repeats',
    x: 460,
    y: 220,
    text: 'Repeats: 0',
    color: 0x8888FF,
    shadow: false,
    scale: 0.9
});

keyTestScreen.addLabel({
    id: 'stats-modifiers',
    x: 460,
    y: 235,
    text: 'With Modifiers: 0',
    color: 0xFF00FF,
    shadow: false,
    scale: 0.9
});

keyTestScreen.addLabel({
    id: 'stats-most-pressed',
    x: 460,
    y: 260,
    text: 'Most Pressed: (none)',
    color: 0xFFFFFF,
    shadow: false,
    scale: 0.9
});

function updateStats() {
    keyTestScreen.updateWidget('stats-presses', {
        text: `Presses: ${stats.totalPresses}`
    });
    keyTestScreen.updateWidget('stats-releases', {
        text: `Releases: ${stats.totalReleases}`
    });
    keyTestScreen.updateWidget('stats-repeats', {
        text: `Repeats: ${stats.totalRepeats}`
    });
    keyTestScreen.updateWidget('stats-modifiers', {
        text: `With Modifiers: ${stats.withModifiers}`
    });

    // Find most pressed key
    let mostKey = '(none)';
    let mostCount = 0;
    for (const [key, count] of Object.entries(stats.mostPressed)) {
        if (count > mostCount) {
            mostCount = count;
            mostKey = key;
        }
    }
    keyTestScreen.updateWidget('stats-most-pressed', {
        text: `Most Pressed: ${mostKey} (${mostCount})`
    });
}

// ====================================
// Control Buttons
// ====================================

keyTestScreen.addButton({
    id: 'clear-log-button',
    x: 20,
    y: 370,
    width: 150,
    height: 20,
    text: 'Clear Log',
    onClick: () => {
        keyLog.length = 0;
        for (let i = 0; i < MAX_LOG_ENTRIES; i++) {
            keyTestScreen.updateWidget(`log-${i}`, { text: '' });
        }
        Client.player.sendInfo('Key log cleared');
    }
});

keyTestScreen.addButton({
    id: 'reset-stats-button',
    x: 180,
    y: 370,
    width: 150,
    height: 20,
    text: 'Reset Statistics',
    onClick: () => {
        stats.totalPresses = 0;
        stats.totalReleases = 0;
        stats.totalRepeats = 0;
        stats.mostPressed = {};
        stats.withModifiers = 0;
        updateStats();
        Client.player.sendInfo('Statistics reset');
    }
});

keyTestScreen.addButton({
    id: 'test-server-command',
    x: 340,
    y: 370,
    width: 200,
    height: 20,
    text: 'Send Key Stats to Server',
    onClick: () => {
        Client.player.runCommand(`uitest:keystats ${stats.totalPresses} ${stats.totalReleases}`);
        Client.player.sendSuccess('Key statistics sent to server');
    }
});

keyTestScreen.addButton({
    id: 'close-button',
    x: 550,
    y: 370,
    width: 150,
    height: 20,
    text: 'Close Test',
    onClick: () => {
        keyTestScreen.hide();
        Client.player.sendInfo('Key test closed');
        console.log('[Key Test] Screen closed');
    }
});

// ====================================
// Key Event Handler
// ====================================

function getActionName(action) {
    switch (action) {
        case 0: return 'RELEASE';
        case 1: return 'PRESS';
        case 2: return 'REPEAT';
        default: return `UNKNOWN(${action})`;
    }
}

function getModifierNames(modifiers) {
    const mods = [];
    if (modifiers & 0x0001) mods.push('SHIFT');
    if (modifiers & 0x0002) mods.push('CTRL');
    if (modifiers & 0x0004) mods.push('ALT');
    if (modifiers & 0x0008) mods.push('SUPER');
    return mods.length > 0 ? mods.join(' + ') : 'None';
}

Client.on(Client.eventTypes.KEY_PRESS, (event) => {
    // Skip if key test screen is not visible
    if (!keyTestScreen.isVisible()) {
        return;
    }

    const { key, scanCode, action, modifiers } = event;
    const actionName = getActionName(action);
    const modifierNames = getModifierNames(modifiers);

    // Update current key display
    keyTestScreen.updateWidget('current-key-label', {
        text: `Last Key: ${key} (scanCode: ${scanCode})`
    });

    const actionColor = action === 1 ? 0x00FF00 : (action === 0 ? 0xFF8800 : 0x8888FF);
    keyTestScreen.updateWidget('current-action-label', {
        text: `Action: ${actionName}`,
        color: actionColor
    });

    keyTestScreen.updateWidget('current-modifiers-label', {
        text: `Modifiers: ${modifierNames}`
    });

    // Add to log
    const timestamp = new Date().toLocaleTimeString();
    const logEntry = `[${timestamp}] ${key} - ${actionName} ${modifiers ? `(${modifierNames})` : ''}`;
    addLogEntry(logEntry);

    // Update statistics
    if (action === 1) {
        stats.totalPresses++;
        stats.mostPressed[key] = (stats.mostPressed[key] || 0) + 1;
    } else if (action === 0) {
        stats.totalReleases++;
    } else if (action === 2) {
        stats.totalRepeats++;
    }

    if (modifiers !== 0) {
        stats.withModifiers++;
    }

    updateStats();

    // Log to console for debugging
    console.log(`[Key Test] ${actionName}: ${key} (scan=${scanCode}, mods=${modifierNames})`);
});

// ====================================
// Keybind to open test screen
// ====================================

Client.on(Client.eventTypes.KEY_PRESS, (event) => {
    // Press 'K' key to toggle key test (only when screen is not visible)
    if (event.key === 'K' && event.action === 1 && !keyTestScreen.isVisible()) {
        keyTestScreen.show();
        Client.player.sendSuccess('Key Events Test opened! (Press ESC to close)');
        console.log('[Key Test] Screen shown');

        // Clear log and stats for new session
        keyLog.length = 0;
        stats.totalPresses = 0;
        stats.totalReleases = 0;
        stats.totalRepeats = 0;
        stats.mostPressed = {};
        stats.withModifiers = 0;

        for (let i = 0; i < MAX_LOG_ENTRIES; i++) {
            keyTestScreen.updateWidget(`log-${i}`, { text: '' });
        }
        updateStats();
    }
});

console.log('[Key Events Test] Loaded! Press "K" key to open test screen.');
