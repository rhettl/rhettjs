/**
 * RhettJS Server Script: UI Test Command Handlers
 *
 * Server-side command handlers for client UI tests.
 * Demonstrates client → server communication patterns.
 *
 * Commands registered:
 * - uitest:simple <message> - Simple command test
 * - uitest:param <value> - Parameterized command test
 * - uitest:readstore - Read from Store API
 * - uitest:process-action - Process complex action from Store
 * - uitest:rapid <count> - Rapid click logging
 * - uitest:keystats <presses> <releases> - Key statistics
 *
 * HOW TO USE:
 * 1. This script auto-loads on server start
 * 2. Commands are called from client scripts (test-click-events.js, test-key-events.js)
 * 3. Server processes commands and sends feedback to player
 */

import Commands from 'rhettjs/commands';
import Store from 'rhettjs/store';

console.log('[UI Test Commands] Registering server-side command handlers...');

// ====================================
// Test 1: Simple Command
// ====================================

Commands.register('uitest:simple', (caller, args) => {
    const message = args[0] || '(no message)';

    caller.sendSuccess(`Server received simple command: "${message}"`);
    console.log(`[UI Test] Simple command from ${caller.name}: ${message}`);
});

// ====================================
// Test 2: Parameterized Command
// ====================================

Commands.register('uitest:param', (caller, args) => {
    if (args.length === 0) {
        caller.sendError('Missing parameter');
        return;
    }

    const value = parseInt(args[0]);

    if (isNaN(value)) {
        caller.sendError(`Invalid parameter: ${args[0]}`);
        return;
    }

    // Process the value
    const result = value * 2;

    caller.sendSuccess(`Server processed: ${value} → ${result}`);
    console.log(`[UI Test] Param command from ${caller.name}: ${value} → ${result}`);
});

// ====================================
// Test 3: Read from Store API
// ====================================

Commands.register('uitest:readstore', (caller) => {
    const data = Store.get(`click-test:${caller.uuid}`);

    if (!data) {
        caller.sendWarning('No data found in store for your player');
        console.log(`[UI Test] Store read from ${caller.name}: no data`);
        return;
    }

    caller.sendSuccess(`Server read from store: message="${data.message}"`);
    console.log(`[UI Test] Store read from ${caller.name}:`, data);

    // Clean up
    Store.delete(`click-test:${caller.uuid}`);
});

// ====================================
// Test 4: Process Complex Action
// ====================================

Commands.register('uitest:process-action', (caller) => {
    const actionData = Store.get(`action:${caller.uuid}`);

    if (!actionData) {
        caller.sendError('No action data found');
        return;
    }

    const { type, items, cost, currency, timestamp } = actionData;

    // Validate action
    if (type !== 'purchase') {
        caller.sendError(`Unknown action type: ${type}`);
        Store.delete(`action:${caller.uuid}`);
        return;
    }

    // Process purchase
    const itemNames = items.map(item => `${item.quantity}x ${item.id}`).join(', ');
    const processingTime = Date.now() - timestamp;

    caller.sendSuccess(`Purchase processed: ${itemNames} for ${cost} ${currency}`);
    caller.sendInfo(`Processing time: ${processingTime}ms`);

    console.log(`[UI Test] Complex action from ${caller.name}:`, actionData);
    console.log(`[UI Test] Processing time: ${processingTime}ms`);

    // Clean up
    Store.delete(`action:${caller.uuid}`);
});

// ====================================
// Test 5: Rapid Click Logging
// ====================================

Commands.register('uitest:rapid', (caller, args) => {
    const count = parseInt(args[0]) || 0;

    caller.sendInfo(`Server logged rapid click milestone: ${count}`);
    console.log(`[UI Test] Rapid click from ${caller.name}: ${count}`);

    // Achievement-like feedback
    if (count === 100) {
        caller.sendSuccess('Achievement: 100 clicks!');
    } else if (count === 500) {
        caller.sendSuccess('Achievement: 500 clicks! You\'re persistent!');
    } else if (count === 1000) {
        caller.sendSuccess('Achievement: 1000 clicks! Absolute madness!');
    }
});

// ====================================
// Test 6: Key Statistics
// ====================================

Commands.register('uitest:keystats', (caller, args) => {
    if (args.length < 2) {
        caller.sendError('Missing statistics');
        return;
    }

    const presses = parseInt(args[0]);
    const releases = parseInt(args[1]);

    caller.sendSuccess(`Server received key stats: ${presses} presses, ${releases} releases`);
    console.log(`[UI Test] Key stats from ${caller.name}: presses=${presses}, releases=${releases}`);

    // Calculate press/release ratio
    const ratio = releases > 0 ? (presses / releases).toFixed(2) : 'N/A';
    caller.sendInfo(`Press/Release ratio: ${ratio}`);
});

// ====================================
// Test 7: UI Test Status
// ====================================

Commands.register('uitest:status', (caller) => {
    caller.sendSuccess('UI Test Commands are active!');
    caller.sendInfo('Available commands:');
    caller.sendInfo('  - uitest:simple <message>');
    caller.sendInfo('  - uitest:param <value>');
    caller.sendInfo('  - uitest:readstore');
    caller.sendInfo('  - uitest:process-action');
    caller.sendInfo('  - uitest:rapid <count>');
    caller.sendInfo('  - uitest:keystats <presses> <releases>');
    caller.sendInfo('  - uitest:status (this command)');

    console.log(`[UI Test] Status check from ${caller.name}`);
});

// ====================================
// Test 8: Echo Test (for debugging)
// ====================================

Commands.register('uitest:echo', (caller, args) => {
    const message = args.join(' ');
    caller.sendSuccess(`Echo: ${message}`);
    console.log(`[UI Test] Echo from ${caller.name}: ${message}`);
});

console.log('[UI Test Commands] Registered successfully!');
console.log('[UI Test Commands] Commands available: simple, param, readstore, process-action, rapid, keystats, status, echo');
