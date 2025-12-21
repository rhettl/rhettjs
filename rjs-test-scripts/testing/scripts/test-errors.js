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
}).catch((e) => {
    console.log('[Error Test] ✓ Test 1: Caught error in Promise:', e.message);
});

// Test 2: ReferenceError in wait()
console.log('[Error Test] Test 2: ReferenceError in wait()');
wait(2).then(() => {
    console.log('[Error Test] About to access undefined variable...');
    console.log(undefinedVariable); // ReferenceError
}).catch((e) => {
    console.log('[Error Test] ✓ Test 2: Caught ReferenceError:', e.message);
});

// Test 3: TypeError in task()
console.log('[Error Test] Test 3: TypeError in task()');
wait(4).then(() => {
    return task(() => {
        console.log('[Error Test] About to call non-function...');
        const notAFunction = 42;
        notAFunction(); // TypeError
    });
}).catch((e) => {
    console.log('[Error Test] ✓ Test 3: Caught TypeError:', e.message);
});

// Test 4: Error recovery - subsequent code should still run
console.log('[Error Test] Test 4: Error recovery test');
wait(6).then(() => {
    console.log('[Error Test] ✓ Test 4: This callback ran despite previous errors');
    console.log('[Error Test] ✓ Test 4: Error recovery works!');
});

// Test 5: Nested errors
console.log('[Error Test] Test 5: Nested task/wait error');
wait(8).then(() => {
    return task(() => {
        console.log('[Error Test] In task, about to error...');
        throw new Error('Nested error - should show clean trace');
    });
}).catch((e) => {
    console.log('[Error Test] ✓ Test 5: Caught nested error:', e.message);
});

// Test 6: Promise.reject
console.log('[Error Test] Test 6: Promise.reject handling');
wait(9).then(() => {
    return Promise.reject(new Error('Explicitly rejected Promise'));
}).catch((e) => {
    console.log('[Error Test] ✓ Test 6: Caught rejected Promise:', e.message);
});

// Test 7: Error in .then() callback
console.log('[Error Test] Test 7: Error in .then() callback');
wait(10).then(() => {
    console.log('[Error Test] In .then(), about to error...');
    throw new Error('Error in .then() callback');
}).catch((e) => {
    console.log('[Error Test] ✓ Test 7: Caught error from .then():', e.message);
});

// Test 8: Verify subsequent scripts work
wait(12).then(() => {
    console.log('');
    console.log('[Error Test] ═══════════════════════════════════');
    console.log('[Error Test] ERROR HANDLING TEST COMPLETE');
    console.log('[Error Test] ═══════════════════════════════════');
    console.log('[Error Test] Check logs above for:');
    console.log('[Error Test] • Clean JavaScript error messages');
    console.log('[Error Test] • No Java stack traces');
    console.log('[Error Test] • Proper error types (Error, ReferenceError, TypeError)');
    console.log('[Error Test] • File:line information');
    console.log('[Error Test] • Recovery after errors (all ✓ marks)');
    console.log('[Error Test] • Promise .catch() handlers working');
    console.log('[Error Test] ═══════════════════════════════════');
});

console.log('[Error Test] All error tests scheduled');
console.log('[Error Test] Watch for errors over next 12 ticks (0.6 seconds)');
