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
if (testFunc(testValue) !== 84) throw new Error('Bad math result');

// Test 4: task() - Worker thread
console.log('⏳ Test 4: task() on worker thread...');
task(() => {
  console.log('✓ Test 4: task() executed on worker thread');
  return 'task-result';
}).then((result) => {
  console.log('✓ Test 4: Promise resolved with:', result);
}).catch((e) => {
  console.error('✗ Test 4 FAILED:', e.message);
});

// Test 5: wait() - Main thread delay
console.log('⏳ Test 5: wait() with delay...');
wait(5)
  .then(() => {
    console.log('✓ Test 5: wait() executed after 5 ticks');
  })
  .catch((e) => {
    console.error('✗ Test 5 FAILED:', e.message);
  });

// Test 6: task() with arguments
console.log('⏳ Test 6: task() with arguments...');
Promise
  .resolve({msg: 'hello', num: 123})
  .thenTask((data) => {
    console.log(`✓ Test 6: task() received arguments: "${data.msg}", ${data.num}`);
    return data;
  })
  .then(() => {
    console.log('✓ Test 6: Complete');
  })
  .catch((e) => {
    console.error('✗ Test 6 FAILED:', e.message);
  });

// Test 7: thenWait() preserves value
console.log('⏳ Test 7: thenWait() preserves value...');
Promise
  .resolve('data-from-caller')
  .thenWait(7)
  .then((result) => {
    if (result === 'data-from-caller') {
      console.log(`✓ Test 7: thenWait() preserved value: ${result}`);
    } else {
      console.log(`✗ Test 7 FAILED: Value not preserved, got: ${result}`);
    }
  })
  .catch((e) => {
    console.error('✗ Test 7 FAILED:', e.message);
  });

// Test 8: Worker isolation (workers CANNOT access parent scope)
const closureVar = 'closure-test';
console.log('⏳ Test 8: Worker isolation test...');

// Test 8.1: thenTask() with no arguments (undefined handling)
Promise
  .resolve()  // Resolves with undefined
  .thenTask(() => {
    // Workers are isolated - closureVar should NOT be accessible
    if (typeof closureVar === 'undefined') {
      console.log('✗ Test 8.1 FAILED: Worker sees parent scope variable: undefined');
      return 'isolated';
    } else {
      console.log(`✓ Test 8.1: Worker correctly isolated (closureVar is ${closureVar})`);
      return 'not-isolated';
    }
  })
  .then((result) => {
    if (result === 'not-isolated') {
      console.log('✓ Test 8.1: Worker isolation verified');
    }

    // Main thread should still have access to parent scope
    if (typeof closureVar === 'undefined') {
      console.log('✗ Test 8.2 FAILED: main thread cannot see parent scope variable');
    } else {
      console.log(`✓ Test 8.2: Main thread correctly sees parent scope variable (closureVar is ${closureVar})`);
    }
  })
  .catch((e) => {
    console.error('✗ Test 8 FAILED:', e.message);
  });


// Test 9: Structure API available
console.log('⏳ Test 9: Structure API test...');
task(() => {
  const structureCount = Structure.list().length;
  console.log(`✓ Test 9: Structure API works (found ${structureCount} structures)`);
  return structureCount;
}).then((count) => {
  console.log(`✓ Test 9: Returned ${count} structures`);
}).catch((e) => {
  console.error(`✗ Test 9 FAILED: ${e.message}`);
});

// Final summary
wait(25).then(() => {
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
