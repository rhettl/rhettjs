/**
 * Test worker closure scope access
 */

console.log('[Worker Test] Starting worker tests...');

// Test 1: Workers can access closure variables
const MULTIPLIER = 10;
task((x) => x * MULTIPLIER, 10).then((result) => {
    if (result === 100) {
        console.log('[Worker Test] ✓ Test 1: Closure variable accessible');
    } else {
        console.log('[Worker Test] ✗ Test 1 FAILED: Expected 100, got', result);
    }
}).catch((e) => {
    console.error('[Worker Test] ✗ Test 1 FAILED:', e.message);
});

// Test 2: Workers can use closure functions
const add = (a, b) => a + b;
const format = (x) => `Result: ${x}`;

wait(5).then(() => {
    return task(() => format(add(1, 2)));
}).then((result) => {
    if (result === 'Result: 3') {
        console.log('[Worker Test] ✓ Test 2: Closure functions work:', result);
    } else {
        console.log('[Worker Test] ✗ Test 2 FAILED: Expected "Result: 3", got', result);
    }
}).catch((e) => {
    console.error('[Worker Test] ✗ Test 2 FAILED:', e.message);
});

// Test 3: Complex closure with nested function references
wait(10).then(() => {
    const foo = 'bar';
    const getFoo = () => foo;

    return task(() => getFoo());
}).then((result) => {
    if (result === 'bar') {
        console.log('[Worker Test] ✓ Test 3: Nested closure references work:', result);
    } else {
        console.log('[Worker Test] ✗ Test 3 FAILED: Expected "bar", got', result);
    }
}).catch((e) => {
    console.error('[Worker Test] ✗ Test 3 FAILED:', e.message);
});

// Test 4: Workers have separate Context but share closure scope
wait(15).then(() => {
    const sharedValue = 'shared';

    return task(() => {
        // Should see closure variable
        if (typeof sharedValue === 'undefined') {
            return 'FAIL: closure not accessible';
        }
        // Should see Runtime (worker global)
        if (typeof Runtime === 'undefined') {
            return 'FAIL: Runtime not available';
        }
        return `${sharedValue},${Runtime.env}`;
    });
}).then((result) => {
    if (result.startsWith('shared,')) {
        console.log('[Worker Test] ✓ Test 4: Closure + worker globals both work');
    } else {
        console.log('[Worker Test] ✗ Test 4 FAILED:', result);
    }
}).catch((e) => {
    console.error('[Worker Test] ✗ Test 4 FAILED:', e.message);
});

// Test 5: typeof operator works (Rhino decompiler bug workaround)
wait(20).then(() => {
    const myVar = 'exists';
    return task(() => typeof myVar);
}).then((result) => {
    if (result === 'string') {
        console.log('[Worker Test] ✓ Test 5: typeof operator works');
    } else {
        console.log('[Worker Test] ✗ Test 5 FAILED: Expected "string", got', result);
    }
}).catch((e) => {
    console.error('[Worker Test] ✗ Test 5 FAILED:', e.message);
});

console.log('[Worker Test] All tests queued');
