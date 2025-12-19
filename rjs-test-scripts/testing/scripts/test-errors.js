// Error Handling Tests
// Tests clean error reporting and recovery

console.log('[Error Test] Starting error handling tests...');
console.log('[Error Test] These tests intentionally trigger errors to verify clean error messages');
console.log('');

// Test 1: JavaScript Error in task()
console.log('[Error Test] Test 1: JavaScript Error in task()');
task(() => {
    console.log('[Error Test] About to throw error in task()...');
    throw new Error('Test error in task() - should show clean JavaScript trace');
});

// Test 2: ReferenceError in schedule()
console.log('[Error Test] Test 2: ReferenceError in schedule()');
schedule(2, () => {
    console.log('[Error Test] About to access undefined variable...');
    console.log(undefinedVariable); // ReferenceError
});

// Test 3: TypeError in task()
console.log('[Error Test] Test 3: TypeError in task()');
schedule(4, () => {
    task(() => {
        console.log('[Error Test] About to call non-function...');
        const notAFunction = 42;
        notAFunction(); // TypeError
    });
});

// Test 4: Error recovery - subsequent code should still run
console.log('[Error Test] Test 4: Error recovery test');
schedule(6, () => {
    console.log('[Error Test] ✓ This callback should run despite previous errors');
    console.log('[Error Test] If you see this, error recovery works!');
});

// Test 5: Nested errors
console.log('[Error Test] Test 5: Nested task/schedule error');
schedule(8, () => {
    task(() => {
        schedule(2, () => {
            console.log('[Error Test] In nested callback, about to error...');
            throw new Error('Nested error - should show clean trace');
        });
    });
});

// Test 6: Verify subsequent scripts work
schedule(10, () => {
    console.log('');
    console.log('[Error Test] ═══════════════════════════════════');
    console.log('[Error Test] ERROR HANDLING TEST COMPLETE');
    console.log('[Error Test] ═══════════════════════════════════');
    console.log('[Error Test] Check logs above for:');
    console.log('[Error Test] • Clean JavaScript error messages');
    console.log('[Error Test] • No Java stack traces');
    console.log('[Error Test] • Proper error types (Error, ReferenceError, TypeError)');
    console.log('[Error Test] • File:line information');
    console.log('[Error Test] • Recovery after errors');
    console.log('[Error Test] ═══════════════════════════════════');
});

console.log('[Error Test] All error tests scheduled');
console.log('[Error Test] Watch for errors over next 10 ticks (0.5 seconds)');
