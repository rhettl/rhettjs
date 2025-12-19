// Phase 3 Test: Threading System (task and schedule)
// Tests worker thread execution and main thread scheduling

console.log('[Threading Test] Starting threading tests...');

// Test 1: Basic task() execution
console.log('[Threading Test] Test 1: Basic task() on worker thread');
task(() => {
    console.log('[Threading Test] ✓ Task executed on worker thread');
});

// Test 2: task() with arguments
console.log('[Threading Test] Test 2: Task with arguments');
task((name, value) => {
    console.log('[Threading Test] ✓ Received arguments:', name, '=', value);
}, 'testArg', 42);

// Test 3: Basic schedule() execution
console.log('[Threading Test] Test 3: Schedule on main thread');
schedule(1, () => {
    console.log('[Threading Test] ✓ Scheduled callback executed after 1 tick');
});

// Test 4: schedule() with longer delay
console.log('[Threading Test] Test 4: Schedule with 20 tick delay (1 second)');
schedule(20, () => {
    console.log('[Threading Test] ✓ Scheduled callback executed after 20 ticks');
});

// Test 5: schedule() with arguments
console.log('[Threading Test] Test 5: Schedule with arguments');
schedule(1, (result) => {
    console.log('[Threading Test] ✓ Schedule received argument:', result);
}, 'test-result');

// Test 6: task() + schedule() combo
console.log('[Threading Test] Test 6: Task + Schedule combo');
task(() => {
    console.log('[Threading Test] Step 1: On worker thread');

    // Simulate heavy work
    const result = 'processed-data';

    schedule(1, (data) => {
        console.log('[Threading Test] ✓ Step 2: Back on main thread with result:', data);
    }, result);
});

// Test 7: Multiple tasks in parallel
console.log('[Threading Test] Test 7: Multiple parallel tasks');
for (let i = 1; i <= 3; i++) {
    task((index) => {
        console.log('[Threading Test] ✓ Parallel task', index, 'executing');
    }, i);
}

// Test 8: Multiple scheduled callbacks
console.log('[Threading Test] Test 8: Multiple scheduled callbacks');
schedule(5, () => console.log('[Threading Test] ✓ Callback at tick 5'));
schedule(10, () => console.log('[Threading Test] ✓ Callback at tick 10'));
schedule(15, () => console.log('[Threading Test] ✓ Callback at tick 15'));

// Test 9: Tick clamping (0 should become 1)
console.log('[Threading Test] Test 9: Tick clamping (0 ticks)');
schedule(0, () => {
    console.log('[Threading Test] ✓ Schedule(0) executed on next tick');
});

// Test 10: Tick clamping (negative should become 1)
console.log('[Threading Test] Test 10: Tick clamping (-5 ticks)');
schedule(-5, () => {
    console.log('[Threading Test] ✓ Schedule(-5) executed on next tick');
});

// Test 11: Scope preservation in schedule()
console.log('[Threading Test] Test 11: Scope preservation');
const testVar = 'scope-test-value';
schedule(1, () => {
    console.log('[Threading Test] ✓ Scope preserved, testVar =', testVar);
});

// Test 12: Error handling in task()
console.log('[Threading Test] Test 12: Error handling in task()');
task(() => {
    console.log('[Threading Test] Before error...');
    throw new Error('Intentional test error in task()');
    // This line should not execute
    console.error('[Threading Test] ✗ This should not print');
});

// Test 13: Error handling in schedule()
console.log('[Threading Test] Test 13: Error handling in schedule()');
schedule(1, () => {
    console.log('[Threading Test] Before error...');
    throw new Error('Intentional test error in schedule()');
    // This line should not execute
    console.error('[Threading Test] ✗ This should not print');
});

// Test 14: Nested task() calls
console.log('[Threading Test] Test 14: Nested task calls');
task(() => {
    console.log('[Threading Test] ✓ Outer task executing');

    task(() => {
        console.log('[Threading Test] ✓ Inner task executing');
    });
});

// Test 15: Complex workflow simulation
console.log('[Threading Test] Test 15: Complex workflow simulation');
task(() => {
    console.log('[Threading Test] Workflow: Step 1 - Processing on worker');

    schedule(5, () => {
        console.log('[Threading Test] Workflow: Step 2 - Back on main after 5 ticks');

        task(() => {
            console.log('[Threading Test] Workflow: Step 3 - Second worker task');

            schedule(5, () => {
                console.log('[Threading Test] ✓ Workflow: Complete!');
            });
        });
    });
});

// Test 16: task.wait() - pause and resume on worker thread
console.log('[Threading Test] Test 16: task.wait() for chunked processing');
task(() => {
    console.log('[Threading Test] Step 1: Processing chunk 1 on worker thread');
    const chunk1Result = 'chunk-1-data';

    // Wait 10 ticks, then continue on worker thread
    task.wait(10, (data) => {
        console.log('[Threading Test] Step 2: Resumed after 10 ticks, processing chunk 2');
        console.log('[Threading Test] ✓ Received data from chunk 1:', data);
        console.log('[Threading Test] ✓ Still on worker thread for chunk 2');
    }, chunk1Result);
});

// Test 17: task.wait() with zero ticks
console.log('[Threading Test] Test 17: task.wait(0) clamping');
task(() => {
    console.log('[Threading Test] On worker, about to wait 0 ticks');

    task.wait(0, () => {
        console.log('[Threading Test] ✓ task.wait(0) resumed after 1 tick (clamped)');
    });
});

// Test 18: Multiple task.wait() in sequence
console.log('[Threading Test] Test 18: Sequential task.wait() calls');
task(() => {
    console.log('[Threading Test] Sequence: Step 1');

    task.wait(5, () => {
        console.log('[Threading Test] Sequence: Step 2 (after 5 ticks)');

        task.wait(5, () => {
            console.log('[Threading Test] Sequence: Step 3 (after 10 ticks total)');
            console.log('[Threading Test] ✓ Sequential waits complete');
        });
    });
});

console.log('[Threading Test] All threading tests initiated');
console.log('[Threading Test] Watch for results over next few ticks...');
