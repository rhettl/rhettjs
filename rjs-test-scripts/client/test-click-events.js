/**
 * RhettJS Client Script Test: Click Events & Client-Server Communication
 *
 * Tests button click events and communication between client and server:
 * - Simple click handlers
 * - Client → Server commands
 * - Store API for complex data
 * - Multiple click event types
 *
 * HOW TO TEST:
 * 1. Ensure server command handler is loaded (test-ui-commands.js)
 * 2. Join the Minecraft world
 * 3. Press F3+T to reload client scripts
 * 4. Press 'C' key to open click test menu
 * 5. Click various buttons and observe chat feedback
 *
 * EXPECTED RESULTS:
 * - Click events should trigger immediately
 * - Commands should be sent to server and processed
 * - Chat feedback should appear for each action
 * - Store data should persist between clicks
 */

import UI from 'rhettjs/ui';
import Client from 'rhettjs/client';
import Store from 'rhettjs/store';

console.log('[Click Events Test] Loading...');

// Create test screen
const clickTestScreen = UI.createScreen('click-test');

// ====================================
// Title
// ====================================

clickTestScreen.addLabel({
    id: 'title',
    x: 20,
    y: 20,
    text: 'Click Events & Communication Test',
    color: 0xFFFF00,
    shadow: true,
    scale: 1.5
});

clickTestScreen.addLabel({
    id: 'instructions',
    x: 20,
    y: 45,
    text: 'Click buttons below to test various click handlers',
    color: 0xAAAAAA,
    shadow: true,
    scale: 1.0
});

// ====================================
// Test 1: Simple Click (Client-only)
// ====================================

clickTestScreen.addLabel({
    id: 'test1-label',
    x: 20,
    y: 80,
    text: 'Test 1: Client-Side Click',
    color: 0x00FFFF,
    shadow: true,
    scale: 1.0
});

let clickCount = 0;

clickTestScreen.addButton({
    id: 'simple-click-button',
    x: 20,
    y: 100,
    width: 200,
    height: 20,
    text: `Clicks: ${clickCount}`,
    onClick: () => {
        clickCount++;
        clickTestScreen.updateWidget('simple-click-button', {
            text: `Clicks: ${clickCount}`
        });
        Client.player.sendSuccess(`Button clicked ${clickCount} times (client-only)`);
        console.log(`[Click Test] Simple click #${clickCount}`);
    }
});

// ====================================
// Test 2: Command-based Communication
// ====================================

clickTestScreen.addLabel({
    id: 'test2-label',
    x: 20,
    y: 140,
    text: 'Test 2: Send Command to Server',
    color: 0x00FFFF,
    shadow: true,
    scale: 1.0
});

clickTestScreen.addButton({
    id: 'command-button-1',
    x: 20,
    y: 160,
    width: 200,
    height: 20,
    text: 'Send Simple Command',
    onClick: () => {
        Client.player.runCommand('uitest:simple hello');
        Client.player.sendInfo('Sent command: uitest:simple hello');
    }
});

clickTestScreen.addButton({
    id: 'command-button-2',
    x: 230,
    y: 160,
    width: 200,
    height: 20,
    text: 'Send Parameterized Command',
    onClick: () => {
        const value = Math.floor(Math.random() * 100);
        Client.player.runCommand(`uitest:param ${value}`);
        Client.player.sendInfo(`Sent command with value: ${value}`);
    }
});

// ====================================
// Test 3: Store API Communication
// ====================================

clickTestScreen.addLabel({
    id: 'test3-label',
    x: 20,
    y: 200,
    text: 'Test 3: Store API (Client → Server Data)',
    color: 0x00FFFF,
    shadow: true,
    scale: 1.0
});

clickTestScreen.addButton({
    id: 'store-write-button',
    x: 20,
    y: 220,
    width: 200,
    height: 20,
    text: 'Write to Store',
    onClick: () => {
        const data = {
            timestamp: Date.now(),
            playerUuid: Client.player.uuid,
            playerName: Client.player.name,
            action: 'button_click',
            position: Client.player.position,
            clickCount: clickCount
        };

        Store.set(`click-test:${Client.player.uuid}`, data);
        Client.player.sendSuccess('Data written to Store');
        console.log('[Click Test] Store write:', data);
    }
});

clickTestScreen.addButton({
    id: 'store-read-button',
    x: 230,
    y: 220,
    width: 200,
    height: 20,
    text: 'Read from Store',
    onClick: () => {
        const data = Store.get(`click-test:${Client.player.uuid}`);

        if (data) {
            Client.player.sendSuccess(`Store data: clicks=${data.clickCount}, time=${new Date(data.timestamp).toISOString()}`);
            console.log('[Click Test] Store read:', data);
        } else {
            Client.player.sendWarning('No data in store');
        }
    }
});

clickTestScreen.addButton({
    id: 'store-trigger-button',
    x: 440,
    y: 220,
    width: 200,
    height: 20,
    text: 'Trigger Server Read',
    onClick: () => {
        // Write data, then tell server to read it
        const data = {
            message: 'Hello from client!',
            uuid: Client.player.uuid
        };

        Store.set(`click-test:${Client.player.uuid}`, data);
        Client.player.runCommand('uitest:readstore');
        Client.player.sendInfo('Triggered server to read store');
    }
});

// ====================================
// Test 4: Hybrid Pattern (Store + Command)
// ====================================

clickTestScreen.addLabel({
    id: 'test4-label',
    x: 20,
    y: 260,
    text: 'Test 4: Hybrid Pattern (Complex Data + Command Trigger)',
    color: 0x00FFFF,
    shadow: true,
    scale: 1.0
});

clickTestScreen.addButton({
    id: 'hybrid-button',
    x: 20,
    y: 280,
    width: 300,
    height: 20,
    text: 'Send Complex Action',
    onClick: () => {
        // Complex data structure
        const actionData = {
            type: 'purchase',
            items: [
                { id: 'diamond_sword', quantity: 1, enchantments: ['sharpness:5'] },
                { id: 'diamond', quantity: 32 }
            ],
            cost: 500,
            currency: 'emerald',
            timestamp: Date.now()
        };

        // Store complex data
        Store.set(`action:${Client.player.uuid}`, actionData);

        // Send simple trigger command
        Client.player.runCommand('uitest:process-action');

        Client.player.sendSuccess('Complex action sent to server');
        console.log('[Click Test] Hybrid pattern:', actionData);
    }
});

// ====================================
// Test 5: Rapid Clicks (Stress Test)
// ====================================

clickTestScreen.addLabel({
    id: 'test5-label',
    x: 20,
    y: 320,
    text: 'Test 5: Rapid Click Handling',
    color: 0x00FFFF,
    shadow: true,
    scale: 1.0
});

let rapidClickCount = 0;
let lastClickTime = 0;

clickTestScreen.addLabel({
    id: 'rapid-click-stats',
    x: 20,
    y: 360,
    text: 'Clicks: 0 | Last interval: 0ms',
    color: 0xFFFFFF,
    shadow: true,
    scale: 0.9
});

clickTestScreen.addButton({
    id: 'rapid-click-button',
    x: 20,
    y: 340,
    width: 250,
    height: 20,
    text: 'Click Me Rapidly!',
    onClick: () => {
        rapidClickCount++;
        const now = Date.now();
        const interval = now - lastClickTime;
        lastClickTime = now;

        clickTestScreen.updateWidget('rapid-click-stats', {
            text: `Clicks: ${rapidClickCount} | Last interval: ${interval}ms`
        });

        // Log to server every 10 clicks
        if (rapidClickCount % 10 === 0) {
            Client.player.runCommand(`uitest:rapid ${rapidClickCount}`);
        }
    }
});

clickTestScreen.addButton({
    id: 'reset-rapid-button',
    x: 280,
    y: 340,
    width: 100,
    height: 20,
    text: 'Reset',
    onClick: () => {
        rapidClickCount = 0;
        lastClickTime = 0;
        clickTestScreen.updateWidget('rapid-click-stats', {
            text: 'Clicks: 0 | Last interval: 0ms'
        });
        Client.player.sendInfo('Rapid click counter reset');
    }
});

// ====================================
// Test 6: Click State Changes
// ====================================

clickTestScreen.addLabel({
    id: 'test6-label',
    x: 450,
    y: 80,
    text: 'Test 6: Dynamic Button States',
    color: 0x00FFFF,
    shadow: true,
    scale: 1.0
});

let toggleState = false;

clickTestScreen.addButton({
    id: 'toggle-button',
    x: 450,
    y: 100,
    width: 200,
    height: 20,
    text: 'State: OFF',
    onClick: () => {
        toggleState = !toggleState;
        clickTestScreen.updateWidget('toggle-button', {
            text: `State: ${toggleState ? 'ON' : 'OFF'}`
        });

        const color = toggleState ? 0x00FF00 : 0xFF0000;
        clickTestScreen.updateWidget('toggle-indicator', { color });

        Client.player.sendInfo(`Toggle state: ${toggleState ? 'ON' : 'OFF'}`);
    }
});

clickTestScreen.addLabel({
    id: 'toggle-indicator',
    x: 660,
    y: 105,
    text: '●',
    color: 0xFF0000,
    shadow: false,
    scale: 2.0
});

// ====================================
// Close button
// ====================================

clickTestScreen.addButton({
    id: 'close-button',
    x: 450,
    y: 340,
    width: 150,
    height: 20,
    text: 'Close Test',
    onClick: () => {
        clickTestScreen.hide();
        Client.player.sendInfo('Click test closed');
        console.log('[Click Test] Screen closed');
    }
});

// ====================================
// Keybind to open test screen
// ====================================

Client.on(Client.eventTypes.KEY_PRESS, (event) => {
    // Press 'C' key to open click test
    if (event.key === 'C' && event.action === 1) {  // 1 = press
        if (!clickTestScreen.isVisible()) {
            clickTestScreen.show();
            Client.player.sendSuccess('Click Events Test opened! (Press ESC to close)');
            console.log('[Click Test] Screen shown');
        } else {
            clickTestScreen.hide();
            Client.player.sendInfo('Click test closed');
            console.log('[Click Test] Screen hidden');
        }
    }
});

console.log('[Click Events Test] Loaded! Press "C" key to open test screen.');
console.log('[Click Events Test] Make sure server handler (test-ui-commands.js) is loaded!');
