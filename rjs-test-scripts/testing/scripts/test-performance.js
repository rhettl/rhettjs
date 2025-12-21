// Performance Tests
// Tests system under load

console.log('[Perf Test] Starting performance tests...');
console.log('[Perf Test] Monitor TPS during these tests (should stay above 19)');
console.log('');

// Test 1: Many concurrent tasks
console.log('[Perf Test] Test 1: 100 concurrent tasks');
const startTime1 = Date.now();
let completed1 = 0;

const tasks = [];
for (let i = 0; i < 100; i++) {
    tasks.push(task(() => {
        // Simulate some work
        let sum = 0;
        for (let j = 0; j < 1000; j++) {
            sum += j;
        }
        return sum;
    }, i));
}

Promise.all(tasks).then(() => {
    const elapsed = Date.now() - startTime1;
    console.log(`[Perf Test] ✓ Test 1 complete: 100 tasks in ~${elapsed}ms`);
});

// Test 2: Many wait() callbacks
console.log('[Perf Test] Test 2: 500 wait callbacks');
wait(12).then(() => {
    const startTime2 = Date.now();
    const waits = [];

    for (let i = 0; i < 500; i++) {
        waits.push(wait(1));
    }

    return Promise.all(waits).then(() => {
        const elapsed = Date.now() - startTime2;
        console.log(`[Perf Test] ✓ Test 2 complete: 500 callbacks in ~${elapsed}ms`);
    });
});

// Test 3: Complex nested operations
console.log('[Perf Test] Test 3: Nested task → wait → task chains');
wait(20).then(() => {
    const startTime3 = Date.now();
    const chains = [];

    for (let i = 0; i < 20; i++) {
        const chain = task(() => {
            // Work on worker
            let result = 0;
            for (let j = 0; j < 500; j++) {
                result += Math.sqrt(j);
            }
            return result;
        }).then((result) => {
            // Return to main thread
            return wait(1).then(() => {
                // Launch another task
                return task(() => result * 2);
            });
        });
        chains.push(chain);
    }

    return Promise.all(chains).then(() => {
        const elapsed = Date.now() - startTime3;
        console.log(`[Perf Test] ✓ Test 3 complete: 20 chains in ~${elapsed}ms`);
    });
});

// Test 4: Rapid wait() calls
console.log('[Perf Test] Test 4: 1000 rapid wait() calls');
wait(40).then(() => {
    const startTime4 = Date.now();
    const waits = [];

    for (let i = 0; i < 1000; i++) {
        waits.push(wait(1));
    }

    return Promise.all(waits).then(() => wait(5)).then(() => {
        const elapsed = Date.now() - startTime4;
        console.log(`[Perf Test] ✓ Test 4 complete: 1000 waits in ~${elapsed}ms`);
    });
});

// Test 5: Long-running task (should not block game)
console.log('[Perf Test] Test 5: Long-running task (5000 iterations)');
wait(50).then(() => {
    const startTime5 = Date.now();
    console.log('[Perf Test] Starting long task on worker...');

    return task(() => {
        let sum = 0;
        for (let i = 0; i < 5000; i++) {
            for (let j = 0; j < 1000; j++) {
                sum += Math.sqrt(i * j);
            }
        }
        return sum;
    }).then((sum) => {
        const elapsed = Date.now() - startTime5;
        console.log(`[Perf Test] ✓ Test 5 complete: Long task took ~${elapsed}ms (sum: ${sum})`);
        console.log('[Perf Test] (Should be ~500-2000ms, did not block game thread)');
    });
});

// Test 6: Closure variable capture (memory test)
console.log('[Perf Test] Test 6: Closure variable capture (100 tasks)');
wait(70).then(() => {
    const startTime6 = Date.now();
    const largeArray = new Array(1000).fill(0).map((_, i) => i);
    const closureTasks = [];

    for (let i = 0; i < 100; i++) {
        const capturedValue = largeArray[i % 100];

        closureTasks.push(task(() => {
            // Each task captures closure variables
            const localCopy = largeArray.slice(0);
            return localCopy[capturedValue];
        }).then((result) => wait(1).then(() => result)));
    }

    return Promise.all(closureTasks).then(() => {
        const elapsed = Date.now() - startTime6;
        console.log(`[Perf Test] ✓ Test 6 complete: 100 tasks with closures in ~${elapsed}ms`);
    });
});

// Final summary
wait(100).then(() => {
    console.log('');
    console.log('[Perf Test] ═══════════════════════════════════');
    console.log('[Perf Test] PERFORMANCE TEST COMPLETE');
    console.log('[Perf Test] ═══════════════════════════════════');
    console.log('[Perf Test] Check results above:');
    console.log('[Perf Test] • All tasks completed?');
    console.log('[Perf Test] • Execution times reasonable?');
    console.log('[Perf Test] • TPS stayed above 19?');
    console.log('[Perf Test] • No lag spikes?');
    console.log('[Perf Test] • Memory usage stable?');
    console.log('[Perf Test] ═══════════════════════════════════');
});

console.log('[Perf Test] All performance tests queued');
console.log('[Perf Test] Tests will run over next ~100 ticks (5 seconds)');
console.log('[Perf Test] Monitor TPS (F3 debug) during execution');
