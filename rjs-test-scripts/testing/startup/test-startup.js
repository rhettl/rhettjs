// Example startup script - runs on server start
console.log('[Startup] Test startup script executing...');

// Verify StartupEvents API is available
if (typeof StartupEvents === 'undefined') {
    throw new Error('StartupEvents not available!');
}

// Register a test item handler
StartupEvents.registry('item', function(event) {
    console.log('[Startup] Item registry handler called!');
    console.log('[Startup] Event type: ' + event.type);
});

// Test that globals are available
if (typeof Utils !== 'undefined') {
    console.log('[Startup] Globals work: ' + Utils.greeting('Startup'));
}

console.log('[Startup] Startup script complete');
