// Smoke Test - Quick validation of all core features
// Run this first to verify basic functionality

console.log('🔥 SMOKE TEST STARTING');
console.log('══════════════════════════════════════════');
console.log('');

// Test 1: Console
console.log('✓ Test 1: console.log works');

// Test 2: Logger
logger.info('✓ Test 2: logger.info works');

// Test 3: Basic variables and functions
const testValue = 42;
const testFunc = (x) => x * 2;
console.log(`✓ Test 3: Variables and functions work (${testFunc(testValue)} = 84)`);

// Test 4: task() - Worker thread
console.log('⏳ Test 4: task() on worker thread...');
task(() => {
    console.log('✓ Test 4: task() executed on worker thread');
});

// Test 5: schedule() - Main thread delay
console.log('⏳ Test 5: schedule() with delay...');
schedule(5, () => {
    console.log('✓ Test 5: schedule() executed after 5 ticks');
});

// Test 6: task() with arguments
console.log('⏳ Test 6: task() with arguments...');
task((msg, num) => {
    console.log(`✓ Test 6: task() received arguments: "${msg}", ${num}`);
}, 'hello', 123);

// Test 7: schedule() with arguments
schedule(7, (result) => {
    console.log(`✓ Test 7: schedule() received argument: ${result}`);
}, 'data-from-caller');

// Test 8: Closure variables
const closureVar = 'closure-test';
schedule(9, () => {
    task(() => {
        console.log(`✓ Test 8: Closure variable accessible: ${closureVar}`);
    });
});

// Test 9: Structure API available
schedule(11, () => {
    try {
        const structureCount = Structure.list().length;
        console.log(`✓ Test 9: Structure API works (found ${structureCount} structures)`);
    } catch (e) {
        console.error(`✗ Test 9 FAILED: ${e.message}`);
    }
});

// Test 10: Nested task/schedule
schedule(13, () => {
    task(() => {
        schedule(2, () => {
            console.log('✓ Test 10: Nested task → schedule works');
        });
    });
});

// Test 11: task.wait()
schedule(16, () => {
    task(() => {
        console.log('⏳ Test 11: task.wait() - part 1 on worker...');
        task.wait(5, () => {
            console.log('✓ Test 11: task.wait() resumed after 5 ticks');
        });
    });
});

// Final summary
schedule(25, () => {
    console.log('');
    console.log('══════════════════════════════════════════');
    console.log('🔥 SMOKE TEST COMPLETE');
    console.log('══════════════════════════════════════════');
    console.log('Check above for any ✗ FAILED messages');
    console.log('All ✓ marks indicate passing tests');
    console.log('');
    console.log('If all tests passed:');
    console.log('  → Core functionality is working');
    console.log('  → Ready for detailed testing');
    console.log('');
    console.log('Next steps:');
    console.log('  /rjs run test-errors     - Error handling');
    console.log('  /rjs run test-performance - Load testing');
    console.log('  /rjs run test-structure   - Structure API');
    console.log('══════════════════════════════════════════');
});

console.log('');
console.log('🔥 Smoke test scheduled (will complete in ~1.5 seconds)');
console.log('══════════════════════════════════════════');
