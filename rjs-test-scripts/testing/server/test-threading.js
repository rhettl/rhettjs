// Phase 3 Test: Threading System (task and wait)
// Tests worker thread execution and tick-based delays
//
// New API:
//   task(fn, ...args) → Promise (runs fn on worker thread)
//   wait(ticks) → Promise (resolves after N ticks)
//   promise.thenTask(fn) → Promise (chain to worker)
//   promise.thenWait(ticks) → Promise (chain with delay)

console.log('[Threading Test] Starting threading tests...');

// Test 1: Basic task() execution
console.log('[Threading Test] Test 1: Basic task() on worker thread');
task(() => {
    console.log('[Threading Test] ✓ Task executed on worker thread');
    return 'task-1-done';
}).then(() => {
    console.log('[Threading Test] ✓ Task 1 promise resolved');
});

// Test 2: task() with arguments
console.log('[Threading Test] Test 2: Task with arguments');
task((name, value) => {
    console.log('[Threading Test] ✓ Received arguments:', name, '=', value);
    return name + ':' + value;
}, 'testArg', 42).then((result) => {
    console.log('[Threading Test] ✓ Task 2 returned:', result);
});

// Test 3: Basic wait() execution
console.log('[Threading Test] Test 3: wait() for tick delay');
wait(1).then(() => {
    console.log('[Threading Test] ✓ wait(1) resolved after 1 tick');
});

// Test 4: wait() with longer delay
console.log('[Threading Test] Test 4: wait() with 20 tick delay (1 second)');
wait(20).then(() => {
    console.log('[Threading Test] ✓ wait(20) resolved after 20 ticks');
});

// Test 5: task() + wait() combo using thenWait()
console.log('[Threading Test] Test 5: Task + thenWait combo');
task(() => {
    console.log('[Threading Test] Step 1: On worker thread');
    return 'processed-data';
}).thenWait(1).then((data) => {
    console.log('[Threading Test] ✓ Step 2: After 1 tick with result:', data);
});

// Test 6: Multiple tasks in parallel
console.log('[Threading Test] Test 6: Multiple parallel tasks');
for (let i = 1; i <= 3; i++) {
    task((index) => {
        console.log('[Threading Test] ✓ Parallel task', index, 'executing');
        return index;
    }, i);
}

// Test 7: Multiple wait() calls
console.log('[Threading Test] Test 7: Multiple wait() calls');
wait(5).then(() => console.log('[Threading Test] ✓ Callback at tick 5'));
wait(10).then(() => console.log('[Threading Test] ✓ Callback at tick 10'));
wait(15).then(() => console.log('[Threading Test] ✓ Callback at tick 15'));

// Test 8: Tick clamping (0 should become 1)
console.log('[Threading Test] Test 8: Tick clamping (0 ticks)');
wait(0).then(() => {
    console.log('[Threading Test] ✓ wait(0) executed on next tick');
});

// Test 9: Tick clamping (negative should become 1)
console.log('[Threading Test] Test 9: Tick clamping (-5 ticks)');
wait(-5).then(() => {
    console.log('[Threading Test] ✓ wait(-5) executed on next tick');
});

// Test 10: Scope preservation in wait()
console.log('[Threading Test] Test 10: Scope preservation');
const testVar = 'scope-test-value';
wait(1).then(() => {
    console.log('[Threading Test] ✓ Scope preserved, testVar =', testVar);
});

// Test 11: Error handling in task()
console.log('[Threading Test] Test 11: Error handling in task()');
task(() => {
    console.log('[Threading Test] Before error...');
    throw new Error('Intentional test error in task()');
}).catch((error) => {
    console.log('[Threading Test] ✓ Caught error in task():', error.message);
});

// Test 12: Chained thenTask() calls
console.log('[Threading Test] Test 12: Chained thenTask() calls');
task(() => {
    console.log('[Threading Test] ✓ Outer task executing');
    return 'outer-result';
}).thenTask((result) => {
    console.log('[Threading Test] ✓ Inner thenTask executing with:', result);
    return 'inner-result';
}).then((finalResult) => {
    console.log('[Threading Test] ✓ Final result:', finalResult);
});

// Test 13: Complex workflow simulation
console.log('[Threading Test] Test 13: Complex workflow simulation');
task(() => {
    console.log('[Threading Test] Workflow: Step 1 - Processing on worker');
    return 'step1-data';
})
.thenWait(5)
.then((data) => {
    console.log('[Threading Test] Workflow: Step 2 - After 5 ticks with:', data);
    return task(() => {
        console.log('[Threading Test] Workflow: Step 3 - Second worker task');
        return 'step3-data';
    });
})
.thenWait(5)
.then(() => {
    console.log('[Threading Test] ✓ Workflow: Complete!');
});

// Test 14: Sequential wait() using chaining
console.log('[Threading Test] Test 14: Sequential wait() calls');
wait(5).then(() => {
    console.log('[Threading Test] Sequence: Step 1 (after 5 ticks)');
    return wait(5);
}).then(() => {
    console.log('[Threading Test] Sequence: Step 2 (after 10 ticks total)');
    console.log('[Threading Test] ✓ Sequential waits complete');
});

// Test 15: task() returns a Promise with value
console.log('[Threading Test] Test 15: task() returns Promise with value');
task(() => {
    console.log('[Threading Test] Worker thread executing...');
    return 42;
}).then((result) => {
    if (result === 42) {
        console.log('[Threading Test] ✓ Promise resolved with correct value:', result);
    } else {
        console.error('[Threading Test] ✗ Promise resolved with wrong value:', result);
    }
});

// Test 16: Nested task() calls (promise flattening)
console.log('[Threading Test] Test 16: Nested task() calls');
function doWorkWithTask(value) {
    return task(() => {
        console.log('[Threading Test] Inner task executing with value:', value);
        return value * 2;
    });
}

doWorkWithTask(10).then((result) => {
    if (result === 20) {
        console.log('[Threading Test] ✓ Nested task from main thread works:', result);
    } else {
        console.error('[Threading Test] ✗ Nested task from main thread failed:', result);
    }
});

// Test 17: Promise.all with multiple tasks
console.log('[Threading Test] Test 17: Promise.all with tasks');
Promise.all([
    task(() => { return 1; }),
    task(() => { return 2; }),
    task(() => { return 3; })
]).then((results) => {
    const sum = results.reduce((a, b) => a + b, 0);
    if (sum === 6) {
        console.log('[Threading Test] ✓ Promise.all resolved with:', results);
    } else {
        console.error('[Threading Test] ✗ Promise.all failed:', results);
    }
});

// Test 18: Promise error handling
console.log('[Threading Test] Test 18: Promise error handling');
task(() => {
    throw new Error('Test error from worker');
}).catch((error) => {
    if (error.message && error.message.indexOf('Test error') >= 0) {
        console.log('[Threading Test] ✓ Promise rejected with error:', error.message);
    } else {
        console.error('[Threading Test] ✗ Promise rejected with unexpected error:', error);
    }
});

// Test 19: thenWait() preserves value
console.log('[Threading Test] Test 19: thenWait() preserves value');
task(() => {
    return 'preserved-value';
}).thenWait(3).then((value) => {
    if (value === 'preserved-value') {
        console.log('[Threading Test] ✓ thenWait() preserved value:', value);
    } else {
        console.error('[Threading Test] ✗ thenWait() lost value:', value);
    }
});

// Test 20: Long chain of operations
console.log('[Threading Test] Test 20: Long chain of operations');
task(() => 1)
    .then(n => n + 1)
    .thenTask(n => n * 2)
    .thenWait(1)
    .then(n => {
        if (n === 4) {
            console.log('[Threading Test] ✓ Long chain result:', n);
        } else {
            console.error('[Threading Test] ✗ Long chain failed:', n);
        }
    });

console.log('[Threading Test] All threading tests initiated');
console.log('[Threading Test] Watch for results over next few ticks...');
