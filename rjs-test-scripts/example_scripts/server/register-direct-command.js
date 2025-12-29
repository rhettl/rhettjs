// Ultra-simple test - bypass the builder entirely for now
// Let's verify basicCommand still works first

ServerEvents.basicCommand('simpletest', function(event) {
    event.sendSuccess('Basic command works!');
});

console.log('[RhettJS] Registered simple test: /simpletest');
