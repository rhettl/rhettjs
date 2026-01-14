/**
 * RhettJS Client Script Test: UI Element Display
 *
 * Tests all UI widget types to ensure they render correctly:
 * - Labels (text, colors, shadows, scales)
 * - Buttons (textures, text, enabled/disabled)
 * - Images (textures, tinting)
 * - Panels (backgrounds, borders)
 *
 * HOW TO TEST:
 * 1. Join the Minecraft world
 * 2. Press F3+T to reload client scripts
 * 3. Look for console message confirming UI test loaded
 * 4. Use the test keybind or command to show the test screen
 *
 * EXPECTED RESULTS:
 * - All widgets should be visible and positioned correctly
 * - Labels should display with different colors and sizes
 * - Buttons should have proper textures and states
 * - Images should display textures correctly
 * - Panels should show backgrounds and borders
 */

import UI from 'rhettjs/ui';
import Client from 'rhettjs/client';

console.log('[UI Display Test] Loading...');

// Create test screen
const testScreen = UI.createScreen('ui-display-test');

// ====================================
// Test 1: Labels with various styles
// ====================================

testScreen.addLabel({
    id: 'title',
    x: 20,
    y: 20,
    text: 'RhettJS UI Display Test',
    color: 0xFFFFFF,
    shadow: true,
    scale: 2.0
});

testScreen.addLabel({
    id: 'subtitle',
    x: 20,
    y: 50,
    text: 'Testing all widget types...',
    color: 0xAAAAAA,
    shadow: true,
    scale: 1.0
});

// Color labels
const colors = [
    { name: 'White', color: 0xFFFFFF },
    { name: 'Red', color: 0xFF0000 },
    { name: 'Green', color: 0x00FF00 },
    { name: 'Blue', color: 0x0000FF },
    { name: 'Yellow', color: 0xFFFF00 },
    { name: 'Purple', color: 0xFF00FF },
    { name: 'Cyan', color: 0x00FFFF }
];

colors.forEach((colorDef, index) => {
    testScreen.addLabel({
        id: `color-label-${index}`,
        x: 20,
        y: 80 + (index * 20),
        text: `${colorDef.name} Label (${colorDef.color.toString(16).toUpperCase()})`,
        color: colorDef.color,
        shadow: true,
        scale: 1.0
    });
});

// ====================================
// Test 2: Panels (backgrounds/borders)
// ====================================

testScreen.addPanel({
    id: 'panel-1',
    x: 250,
    y: 20,
    width: 200,
    height: 100,
    backgroundColor: 0x80000000,  // Semi-transparent black
    borderColor: 0xFFFFFFFF,
    borderWidth: 2
});

testScreen.addLabel({
    id: 'panel-1-label',
    x: 260,
    y: 60,
    text: 'Panel with border',
    color: 0xFFFFFF,
    shadow: false,
    scale: 1.0
});

testScreen.addPanel({
    id: 'panel-2',
    x: 250,
    y: 140,
    width: 200,
    height: 80,
    backgroundColor: 0x80FF0000,  // Semi-transparent red
    borderColor: 0xFFFFFF00,      // Yellow border
    borderWidth: 3
});

testScreen.addLabel({
    id: 'panel-2-label',
    x: 260,
    y: 170,
    text: 'Colored panel',
    color: 0xFFFFFF,
    shadow: true,
    scale: 1.0
});

// ====================================
// Test 3: Buttons (various states)
// ====================================

testScreen.addButton({
    id: 'button-enabled',
    x: 480,
    y: 20,
    width: 150,
    height: 20,
    text: 'Enabled Button',
    onClick: () => {
        Client.player.sendSuccess('Button clicked!');
    }
});

testScreen.addButton({
    id: 'button-disabled',
    x: 480,
    y: 50,
    width: 150,
    height: 20,
    text: 'Disabled Button',
    onClick: () => {
        // Should not fire
        Client.player.sendError('This should not happen!');
    }
});

// Disable the disabled button
testScreen.updateWidget('button-disabled', { enabled: false });

testScreen.addButton({
    id: 'button-long-text',
    x: 480,
    y: 80,
    width: 150,
    height: 20,
    text: 'Very Long Button Text That Might Overflow',
    onClick: () => {
        Client.player.sendInfo('Long text button clicked');
    }
});

// ====================================
// Test 4: Images (if textures exist)
// ====================================

// Note: These textures may not exist in vanilla Minecraft
// This is to test the rendering system, not specific textures
testScreen.addLabel({
    id: 'image-section-label',
    x: 20,
    y: 240,
    text: 'Image Widgets (textures may not load):',
    color: 0xFFFF00,
    shadow: true,
    scale: 1.0
});

// Try rendering a Minecraft GUI texture (should exist)
testScreen.addImage({
    id: 'test-image-1',
    x: 20,
    y: 270,
    width: 32,
    height: 32,
    texture: 'minecraft:textures/gui/widgets.png',
    u: 0,
    v: 0,
    textureWidth: 256,
    textureHeight: 256
});

testScreen.addLabel({
    id: 'image-1-label',
    x: 60,
    y: 280,
    text: 'widgets.png texture',
    color: 0xFFFFFF,
    shadow: true,
    scale: 0.8
});

// ====================================
// Test 5: Dynamic updates
// ====================================

testScreen.addPanel({
    id: 'dynamic-panel',
    x: 480,
    y: 120,
    width: 200,
    height: 100,
    backgroundColor: 0x80000000,
    borderColor: 0xFF00FF00,
    borderWidth: 2
});

testScreen.addLabel({
    id: 'dynamic-label',
    x: 490,
    y: 130,
    text: 'Dynamic content:',
    color: 0xFFFFFF,
    shadow: true,
    scale: 1.0
});

let updateCounter = 0;
testScreen.addLabel({
    id: 'counter-label',
    x: 490,
    y: 150,
    text: `Updates: ${updateCounter}`,
    color: 0x00FF00,
    shadow: true,
    scale: 1.5
});

testScreen.addButton({
    id: 'update-button',
    x: 490,
    y: 180,
    width: 180,
    height: 20,
    text: 'Update Counter',
    onClick: () => {
        updateCounter++;
        testScreen.updateWidget('counter-label', {
            text: `Updates: ${updateCounter}`
        });
        Client.player.sendSuccess(`Counter: ${updateCounter}`);
    }
});

// ====================================
// Test 6: Close button
// ====================================

testScreen.addButton({
    id: 'close-button',
    x: 20,
    y: 320,
    width: 100,
    height: 20,
    text: 'Close Test UI',
    onClick: () => {
        testScreen.hide();
        Client.player.sendInfo('UI test screen closed');
    }
});

// ====================================
// Show the screen on key press
// ====================================

Client.on(Client.eventTypes.KEY_PRESS, (event) => {
    // Press 'U' key to open UI test
    if (event.key === 'U' && event.action === 1) {  // 1 = press
        if (!testScreen.isVisible()) {
            testScreen.show();
            Client.player.sendSuccess('UI Display Test screen opened! (Press ESC to close)');
            console.log('[UI Display Test] Screen shown');
        } else {
            testScreen.hide();
            Client.player.sendInfo('UI Display Test screen closed');
            console.log('[UI Display Test] Screen hidden');
        }
    }
});

console.log('[UI Display Test] Loaded! Press "U" key to open test screen.');
