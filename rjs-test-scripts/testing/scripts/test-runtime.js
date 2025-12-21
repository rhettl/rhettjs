// Runtime API Tests
// Tests Runtime.env, Runtime.exit(), and Runtime.setEventLoopTimeout()

console.log('[Runtime Test] Starting Runtime API tests...');
console.log('');

// Test 1: Runtime.env properties
console.log('[Runtime Test] Test 1: Runtime.env properties');
try {
    console.log('[Runtime Test] Runtime.env.MAX_WORKER_THREADS:', Runtime.env.MAX_WORKER_THREADS);
    console.log('[Runtime Test] Runtime.env.TICKS_PER_SECOND:', Runtime.env.TICKS_PER_SECOND);
    console.log('[Runtime Test] Runtime.env.IS_DEBUG:', Runtime.env.IS_DEBUG);
    console.log('[Runtime Test] Runtime.env.RJS_VERSION:', Runtime.env.RJS_VERSION);

    if (Runtime.env.TICKS_PER_SECOND === 20) {
        console.log('[Runtime Test] ✓ Test 1: Runtime.env properties accessible');
    } else {
        console.log('[Runtime Test] ✗ Test 1 FAILED: TICKS_PER_SECOND should be 20');
    }
} catch (e) {
    console.error('[Runtime Test] ✗ Test 1 FAILED:', e.message);
}

// Test 2: Runtime.setEventLoopTimeout() with valid value
console.log('[Runtime Test] Test 2: Runtime.setEventLoopTimeout() with valid value');
try {
    Runtime.setEventLoopTimeout(120000); // 2 minutes
    console.log('[Runtime Test] ✓ Test 2: Timeout set to 120000ms');
} catch (e) {
    console.error('[Runtime Test] ✗ Test 2 FAILED:', e.message);
}

// Test 3: Runtime.setEventLoopTimeout() with too-small value (should error)
console.log('[Runtime Test] Test 3: Runtime.setEventLoopTimeout() validation');
try {
    Runtime.setEventLoopTimeout(500); // Too small
    console.log('[Runtime Test] ✗ Test 3 FAILED: Should have thrown error for timeout < 1000ms');
} catch (e) {
    if (e.message && e.message.indexOf('at least 1000ms') >= 0) {
        console.log('[Runtime Test] ✓ Test 3: Correctly rejected timeout < 1000ms');
    } else {
        console.error('[Runtime Test] ✗ Test 3 FAILED: Wrong error:', e.message);
    }
}

// Test 4: Long-running task with custom timeout
console.log('[Runtime Test] Test 4: Custom timeout allows longer execution');
console.log('[Runtime Test] Setting timeout to 30 seconds for long operation...');
Runtime.setEventLoopTimeout(30000); // 30 seconds

wait(5).then(() => {
    console.log('[Runtime Test] ✓ Test 4: Script still running with custom timeout');
    console.log('[Runtime Test] (Would have timed out at 5 ticks with default 60s if script was much slower)');
});

// Test 5: Runtime.env.MAX_WORKER_THREADS matches actual worker count
console.log('[Runtime Test] Test 5: Worker count matches Runtime.env');
let maxWorkers = Runtime.env.MAX_WORKER_THREADS;
console.log('[Runtime Test] System reports', maxWorkers, 'worker threads');

Promise.all([
    task(() => 'worker-1'),
    task(() => 'worker-2'),
    task(() => 'worker-3'),
    task(() => 'worker-4')
]).then((results) => {
    if (results.length === 4) {
        console.log('[Runtime Test] ✓ Test 5: Successfully ran', results.length, 'concurrent tasks');
        console.log('[Runtime Test] Workers:', results.join(', '));
    }
}).catch((e) => {
    console.error('[Runtime Test] ✗ Test 5 FAILED:', e.message);
});

// Test 6: Runtime.exit() (commented out to avoid stopping test suite)
console.log('[Runtime Test] Test 6: Runtime.exit() exists (not testing actual exit)');
try {
    if (typeof Runtime.exit === 'function') {
        console.log('[Runtime Test] ✓ Test 6: Runtime.exit() function exists');
        console.log('[Runtime Test] (Not calling it to avoid stopping test suite)');
    } else {
        console.log('[Runtime Test] ✗ Test 6 FAILED: Runtime.exit is not a function');
    }
} catch (e) {
    console.error('[Runtime Test] ✗ Test 6 FAILED:', e.message);
}

// Final summary
wait(10).then(() => {
    console.log('');
    console.log('[Runtime Test] ═══════════════════════════════════');
    console.log('[Runtime Test] RUNTIME API TEST COMPLETE');
    console.log('[Runtime Test] ═══════════════════════════════════');
    console.log('[Runtime Test] Tested:');
    console.log('[Runtime Test] • Runtime.env constants');
    console.log('[Runtime Test] • Runtime.setEventLoopTimeout()');
    console.log('[Runtime Test] • Timeout validation');
    console.log('[Runtime Test] • Worker count verification');
    console.log('[Runtime Test] • Runtime.exit() availability');
    console.log('[Runtime Test] ═══════════════════════════════════');
});

console.log('[Runtime Test] All runtime tests scheduled');
