// Promise Polyfill Tests
// Tests Promise.all, Promise.race, Promise.allSettled, Promise.sequence
// These polyfills are provided by globals/14-concurrency-helper.js

console.log('[Promise Test] Starting Promise polyfill tests...');
console.log('');

// Test 1: Promise.all - all resolve
console.log('[Promise Test] Test 1: Promise.all with all resolving');
Promise.all([
    Promise.resolve(1),
    Promise.resolve(2),
    Promise.resolve(3)
]).then((results) => {
    if (results.length === 3 && results[0] === 1 && results[1] === 2 && results[2] === 3) {
        console.log('[Promise Test] ✓ Test 1: Promise.all resolved with:', results);
    } else {
        console.log('[Promise Test] ✗ Test 1 FAILED: Unexpected results:', results);
    }
}).catch((e) => {
    console.error('[Promise Test] ✗ Test 1 FAILED:', e.message);
});

// Test 2: Promise.all - one rejects
console.log('[Promise Test] Test 2: Promise.all with one rejection');
wait(2).then(() => {
    return Promise.all([
        Promise.resolve(1),
        Promise.reject(new Error('test rejection')),
        Promise.resolve(3)
    ]);
}).then(() => {
    console.log('[Promise Test] ✗ Test 2 FAILED: Should have rejected');
}).catch((e) => {
    if (e.message === 'test rejection') {
        console.log('[Promise Test] ✓ Test 2: Promise.all correctly rejected with:', e.message);
    } else {
        console.log('[Promise Test] ✗ Test 2 FAILED: Wrong error:', e.message);
    }
});

// Test 3: Promise.all with task()
console.log('[Promise Test] Test 3: Promise.all with task() calls');
wait(3).then(() => {
    return Promise.all([
        task(() => 'worker-result-1'),
        task(() => 'worker-result-2'),
        task(() => 'worker-result-3')
    ]);
}).then((results) => {
    if (results.length === 3 && results[0] === 'worker-result-1') {
        console.log('[Promise Test] ✓ Test 3: Promise.all with tasks:', results);
    } else {
        console.log('[Promise Test] ✗ Test 3 FAILED:', results);
    }
}).catch((e) => {
    console.error('[Promise Test] ✗ Test 3 FAILED:', e.message);
});

// Test 4: Promise.race - first wins
console.log('[Promise Test] Test 4: Promise.race first resolver wins');
wait(5).then(() => {
    return Promise.race([
        wait(5).then(() => 'slow'),
        wait(1).then(() => 'fast'),
        wait(10).then(() => 'slower')
    ]);
}).then((result) => {
    if (result === 'fast') {
        console.log('[Promise Test] ✓ Test 4: Promise.race returned fastest:', result);
    } else {
        console.log('[Promise Test] ✗ Test 4 FAILED: Expected "fast", got:', result);
    }
}).catch((e) => {
    console.error('[Promise Test] ✗ Test 4 FAILED:', e.message);
});

// Test 5: Promise.race - first rejection wins
console.log('[Promise Test] Test 5: Promise.race first rejection wins');
wait(12).then(() => {
    return Promise.race([
        wait(5).then(() => 'slow-resolve'),
        Promise.reject(new Error('immediate-reject'))
    ]);
}).then(() => {
    console.log('[Promise Test] ✗ Test 5 FAILED: Should have rejected');
}).catch((e) => {
    if (e.message === 'immediate-reject') {
        console.log('[Promise Test] ✓ Test 5: Promise.race rejected with:', e.message);
    } else {
        console.log('[Promise Test] ✗ Test 5 FAILED: Wrong error:', e.message);
    }
});

// Test 6: Promise.allSettled - mixed results
console.log('[Promise Test] Test 6: Promise.allSettled with mixed results');
wait(13).then(() => {
    return Promise.allSettled([
        Promise.resolve('success-1'),
        Promise.reject(new Error('failure-1')),
        Promise.resolve('success-2'),
        Promise.reject(new Error('failure-2'))
    ]);
}).then((results) => {
    let fulfilled = results.filter(r => r.status === 'fulfilled').length;
    let rejected = results.filter(r => r.status === 'rejected').length;

    if (fulfilled === 2 && rejected === 2) {
        console.log('[Promise Test] ✓ Test 6: Promise.allSettled returned', fulfilled, 'fulfilled,', rejected, 'rejected');
        console.log('[Promise Test] Results:', results.map(r => r.status).join(', '));
    } else {
        console.log('[Promise Test] ✗ Test 6 FAILED: Expected 2 fulfilled, 2 rejected');
    }
}).catch((e) => {
    console.error('[Promise Test] ✗ Test 6 FAILED:', e.message);
});

// Test 7: Promise.sequence - sequential execution
console.log('[Promise Test] Test 7: Promise.sequence executes in order');
let executionOrder = [];
wait(15).then(() => {
    return Promise.sequence([
        () => { executionOrder.push(1); return Promise.resolve('first'); },
        () => { executionOrder.push(2); return Promise.resolve('second'); },
        () => { executionOrder.push(3); return Promise.resolve('third'); }
    ]);
}).then((results) => {
    if (executionOrder.join(',') === '1,2,3' && results.length === 3) {
        console.log('[Promise Test] ✓ Test 7: Promise.sequence executed in order:', executionOrder);
        console.log('[Promise Test] Results:', results);
    } else {
        console.log('[Promise Test] ✗ Test 7 FAILED: Order was:', executionOrder);
    }
}).catch((e) => {
    console.error('[Promise Test] ✗ Test 7 FAILED:', e.message);
});

// Test 8: Empty Promise.all
console.log('[Promise Test] Test 8: Promise.all with empty array');
wait(16).then(() => {
    return Promise.all([]);
}).then((results) => {
    if (results.length === 0) {
        console.log('[Promise Test] ✓ Test 8: Promise.all([]) resolved with empty array');
    } else {
        console.log('[Promise Test] ✗ Test 8 FAILED: Expected empty array');
    }
}).catch((e) => {
    console.error('[Promise Test] ✗ Test 8 FAILED:', e.message);
});

// Test 9: Nested Promise.all
console.log('[Promise Test] Test 9: Nested Promise.all');
wait(17).then(() => {
    return Promise.all([
        Promise.all([task(() => 'a'), task(() => 'b')]),
        Promise.all([task(() => 'c'), task(() => 'd')])
    ]);
}).then((results) => {
    if (results.length === 2 && results[0].length === 2 && results[1].length === 2) {
        console.log('[Promise Test] ✓ Test 9: Nested Promise.all:', results);
    } else {
        console.log('[Promise Test] ✗ Test 9 FAILED:', results);
    }
}).catch((e) => {
    console.error('[Promise Test] ✗ Test 9 FAILED:', e.message);
});

// Final summary
wait(20).then(() => {
    console.log('');
    console.log('[Promise Test] ═══════════════════════════════════');
    console.log('[Promise Test] PROMISE POLYFILL TEST COMPLETE');
    console.log('[Promise Test] ═══════════════════════════════════');
    console.log('[Promise Test] Tested:');
    console.log('[Promise Test] • Promise.all (resolve, reject, empty)');
    console.log('[Promise Test] • Promise.race (resolve, reject)');
    console.log('[Promise Test] • Promise.allSettled (mixed results)');
    console.log('[Promise Test] • Promise.sequence (sequential execution)');
    console.log('[Promise Test] • Nested Promise combinations');
    console.log('[Promise Test] • Integration with task() and wait()');
    console.log('[Promise Test] ═══════════════════════════════════');
});

console.log('[Promise Test] All Promise polyfill tests scheduled');
