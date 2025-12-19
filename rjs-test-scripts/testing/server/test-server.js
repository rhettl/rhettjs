// Example server script - runs on server start
console.log('[Server] Test server script executing...');

// Verify ServerEvents API is available
if (typeof ServerEvents === 'undefined') {
    throw new Error('ServerEvents not available!');
}

// Register event handlers
ServerEvents.itemUse(function(event) {
    console.log('[Server] Item use handler triggered!');
});

ServerEvents.command('testcmd', function(event) {
    console.log('[Server] Test command handler triggered!');
    console.log('[Server] Command: ' + event.command);
});

// Test that globals are available
if (typeof Utils !== 'undefined') { // this will be false
    console.log('[Server] Globals work: ' + Utils.greeting('Server'));
}

console.log('[Server] Server script complete - registered 2 handlers');
