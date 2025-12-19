// Performance Tests
// Tests system under load

console.log('[Perf Test] Starting performance tests...');
console.log('[Perf Test] Monitor TPS during these tests (should stay above 19)');
console.log('');

// Test 1: Many concurrent tasks
console.log('[Perf Test] Test 1: 100 concurrent tasks');
const startTime1 = Date.now();
let completed1 = 0;

for (let i = 0; i < 100; i++) {
    task(() => {
        // Simulate some work
        let sum = 0;
        for (let j = 0; j < 1000; j++) {
            sum += j;
        }
        completed1++;
    }, i);
}

schedule(10, () => {
    const elapsed = Date.now() - startTime1;
    console.log(`[Perf Test] ✓ Test 1 complete: ${completed1}/100 tasks in ~${elapsed}ms`);
});

// Test 2: Many scheduled callbacks
console.log('[Perf Test] Test 2: 500 scheduled callbacks');
schedule(12, () => {
    const startTime2 = Date.now();
    let completed2 = 0;

    for (let i = 0; i < 500; i++) {
        schedule(1, () => {
            completed2++;
            if (completed2 === 500) {
                const elapsed = Date.now() - startTime2;
                console.log(`[Perf Test] ✓ Test 2 complete: 500 callbacks in ~${elapsed}ms`);
            }
        });
    }
});

// Test 3: Complex nested operations
console.log('[Perf Test] Test 3: Nested task → schedule → task chains');
schedule(20, () => {
    const startTime3 = Date.now();
    let completed3 = 0;

    for (let i = 0; i < 20; i++) {
        task(() => {
            // Work on worker
            let result = 0;
            for (let j = 0; j < 500; j++) {
                result += Math.sqrt(j);
            }

            // Return to main thread
            schedule(1, () => {
                // Launch another task
                task(() => {
                    completed3++;
                    if (completed3 === 20) {
                        const elapsed = Date.now() - startTime3;
                        console.log(`[Perf Test] ✓ Test 3 complete: 20 chains in ~${elapsed}ms`);
                    }
                });
            }, result);
        });
    }
});

// Test 4: Rapid schedule() calls
console.log('[Perf Test] Test 4: 1000 rapid schedule() calls');
schedule(40, () => {
    const startTime4 = Date.now();

    for (let i = 0; i < 1000; i++) {
        schedule(1, () => {
            // Empty callback, just testing scheduling overhead
        });
    }

    schedule(5, () => {
        const elapsed = Date.now() - startTime4;
        console.log(`[Perf Test] ✓ Test 4 complete: 1000 schedules in ~${elapsed}ms`);
    });
});

// Test 5: Long-running task (should not block game)
console.log('[Perf Test] Test 5: Long-running task (5000 iterations)');
schedule(50, () => {
    const startTime5 = Date.now();

    task(() => {
        console.log('[Perf Test] Starting long task on worker...');
        let sum = 0;
        for (let i = 0; i < 5000; i++) {
            for (let j = 0; j < 1000; j++) {
                sum += Math.sqrt(i * j);
            }
        }

        schedule(1, () => {
            const elapsed = Date.now() - startTime5;
            console.log(`[Perf Test] ✓ Test 5 complete: Long task took ~${elapsed}ms`);
            console.log('[Perf Test] (Should be ~500-2000ms, did not block game thread)');
        }, sum);
    });
});

// Test 6: Closure variable capture (memory test)
console.log('[Perf Test] Test 6: Closure variable capture (100 tasks)');
schedule(70, () => {
    const startTime6 = Date.now();
    let completed6 = 0;

    const largeArray = new Array(1000).fill(0).map((_, i) => i);

    for (let i = 0; i < 100; i++) {
        const capturedValue = largeArray[i % 100];

        task(() => {
            // Each task captures closure variables
            const localCopy = largeArray.slice(0);
            const result = localCopy[capturedValue];

            schedule(1, () => {
                completed6++;
                if (completed6 === 100) {
                    const elapsed = Date.now() - startTime6;
                    console.log(`[Perf Test] ✓ Test 6 complete: 100 tasks with closures in ~${elapsed}ms`);
                }
            }, result);
        });
    }
});

// Final summary
schedule(100, () => {
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

console.log('[Perf Test] All performance tests scheduled');
console.log('[Perf Test] Tests will run over next ~100 ticks (5 seconds)');
console.log('[Perf Test] Monitor TPS (F3 debug) during execution');
