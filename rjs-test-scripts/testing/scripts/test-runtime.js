// Runtime API Tests (GraalVM)
// Tests Runtime.env, Runtime.exit(), and Runtime.setScriptTimeout()

console.log('[Runtime Test] Starting Runtime API tests...');
console.log('');

// Test 1: Runtime.env properties
console.log('[Runtime Test] Test 1: Runtime.env properties');
try {
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

// Test 2: Runtime.setScriptTimeout() with valid value
console.log('[Runtime Test] Test 2: Runtime.setScriptTimeout() with valid value');
try {
    Runtime.setScriptTimeout(120000); // 2 minutes
    console.log('[Runtime Test] ✓ Test 2: Timeout set to 120000ms');
} catch (e) {
    console.error('[Runtime Test] ✗ Test 2 FAILED:', e.message);
}

// Test 3: Runtime.setScriptTimeout() with too-small value (should error)
console.log('[Runtime Test] Test 3: Runtime.setScriptTimeout() validation');
try {
    Runtime.setScriptTimeout(500); // Too small
    console.log('[Runtime Test] ✗ Test 3 FAILED: Should have thrown error for timeout < 1000ms');
} catch (e) {
    if (e.message && e.message.indexOf('at least 1000ms') >= 0) {
        console.log('[Runtime Test] ✓ Test 3: Correctly rejected timeout < 1000ms');
    } else {
        console.error('[Runtime Test] ✗ Test 3 FAILED: Wrong error:', e.message);
    }
}

// Test 4: Runtime.setScriptTimeout() accepts valid large value
console.log('[Runtime Test] Test 4: Runtime.setScriptTimeout() accepts large timeout');
try {
    Runtime.setScriptTimeout(300000); // 5 minutes
    console.log('[Runtime Test] ✓ Test 4: Accepted 300000ms timeout');

    // Reset to default
    Runtime.setScriptTimeout(30000);
} catch (e) {
    console.error('[Runtime Test] ✗ Test 4 FAILED:', e.message);
}

// Test 5: Runtime.exit() function exists
console.log('[Runtime Test] Test 5: Runtime.exit() exists (not testing actual exit)');
try {
    if (typeof Runtime.exit === 'function') {
        console.log('[Runtime Test] ✓ Test 5: Runtime.exit() function exists');
        console.log('[Runtime Test] (Not calling it to avoid stopping test suite)');
    } else {
        console.log('[Runtime Test] ✗ Test 5 FAILED: Runtime.exit is not a function');
    }
} catch (e) {
    console.error('[Runtime Test] ✗ Test 5 FAILED:', e.message);
}

// Test 6: Runtime.env property types
console.log('[Runtime Test] Test 6: Runtime.env property types');
try {
    const tpsType = typeof Runtime.env.TICKS_PER_SECOND;
    const debugType = typeof Runtime.env.IS_DEBUG;
    const versionType = typeof Runtime.env.RJS_VERSION;

    console.log('[Runtime Test] TICKS_PER_SECOND type:', tpsType);
    console.log('[Runtime Test] IS_DEBUG type:', debugType);
    console.log('[Runtime Test] RJS_VERSION type:', versionType);

    if (tpsType === 'number' && debugType === 'boolean' && versionType === 'string') {
        console.log('[Runtime Test] ✓ Test 6: All property types correct');
    } else {
        console.log('[Runtime Test] ✗ Test 6 FAILED: Wrong property types');
    }
} catch (e) {
    console.error('[Runtime Test] ✗ Test 6 FAILED:', e.message);
}

// Test 7: Runtime.setScriptTimeout() requires argument
console.log('[Runtime Test] Test 7: Runtime.setScriptTimeout() requires argument');
try {
    Runtime.setScriptTimeout(); // No argument
    console.log('[Runtime Test] ✗ Test 7 FAILED: Should have thrown error for missing argument');
} catch (e) {
    if (e.message && e.message.indexOf('requires a timeout argument') >= 0) {
        console.log('[Runtime Test] ✓ Test 7: Correctly rejected missing argument');
    } else {
        console.error('[Runtime Test] ✗ Test 7 FAILED: Wrong error:', e.message);
    }
}

// Final summary
console.log('');
console.log('[Runtime Test] ═══════════════════════════════════');
console.log('[Runtime Test] RUNTIME API TEST COMPLETE');
console.log('[Runtime Test] ═══════════════════════════════════');
console.log('[Runtime Test] Tested:');
console.log('[Runtime Test] • Runtime.env constants (TICKS_PER_SECOND, IS_DEBUG, RJS_VERSION)');
console.log('[Runtime Test] • Runtime.setScriptTimeout() with valid/invalid values');
console.log('[Runtime Test] • Timeout validation (min 1000ms)');
console.log('[Runtime Test] • Property types (number, boolean, string)');
console.log('[Runtime Test] • Runtime.exit() availability');
console.log('[Runtime Test] • Argument validation');
console.log('[Runtime Test] ═══════════════════════════════════');
console.log('');
console.log('[Runtime Test] Note: Async tests (wait, task) will be added in Phase 4');
