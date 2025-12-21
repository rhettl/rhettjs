# RhettJS Testing Scripts

Comprehensive test suite for RhettJS event-loop model implementation.

## Quick Start

Run the smoke test first to verify basic functionality:
```
/rjs run smoke-test
```

## Test Suite Overview

### Core Functionality Tests

#### `smoke-test.js` - Quick Validation
Fast sanity check of all core features (runs in ~1.5 seconds):
- ✓ Test 1: Console logging
- ✓ Test 2: Logger API
- ✓ Test 3: Variables and functions
- ✓ Test 4: task() returns Promise
- ✓ Test 5: wait() delays execution
- ✓ Test 6: .thenTask() chaining
- ✓ Test 7: .thenWait() preserves values
- ✓ Test 8.1: Worker isolation (worker cannot see parent scope)
- ✓ Test 8.2: Main thread scope (main thread retains parent scope access)
- ✓ Test 9: Structure API availability

**Usage:** `/rjs run smoke-test`

#### `test-errors.js` - Error Handling
Tests clean error reporting and recovery:
- Error in task()
- ReferenceError in wait()
- TypeError in task()
- Error recovery (subsequent code runs)
- Nested errors
- Promise.reject handling
- Error in .then() callback
- Promise .catch() handlers

**Usage:** `/rjs run test-errors`

#### `test-runtime.js` - Runtime API
Tests Runtime environment and controls:
- Runtime.env properties (MAX_WORKER_THREADS, TICKS_PER_SECOND, IS_DEBUG, RJS_VERSION)
- Runtime.setEventLoopTimeout() with valid value
- Runtime.setEventLoopTimeout() validation (rejects < 1000ms)
- Custom timeout allows longer execution
- Worker count verification
- Runtime.exit() availability

**Usage:** `/rjs run test-runtime`

#### `test-promise-polyfills.js` - Promise Extensions
Tests Promise polyfills from globals/14-concurrency-helper.js:
- Promise.all (all resolve, one rejects, empty array)
- Promise.race (first wins, rejection)
- Promise.allSettled (mixed results)
- Promise.sequence (sequential execution)
- Nested Promise.all
- Integration with task() and wait()

**Usage:** `/rjs run test-promise-polyfills`

#### `10-sec.js` - Long Timer Test
Simple 10-second timer test for wait() verification:
```javascript
wait(200).then(() => console.log('timer called'))
```

**Usage:** `/rjs run 10-sec`

### Advanced Tests (Requires Server Context)

These tests require running in `server_scripts/` context with world access.

#### `../server/test-threading.js` - Threading Features
Comprehensive test of 20 threading scenarios:
- Sequential task execution
- Parallel task execution with Promise.all
- Task with arguments
- Task returning values
- Nested tasks (synchronous on same thread)
- Error handling in tasks
- Wait timers
- Task + wait chaining
- thenTask() chaining
- thenWait() chaining
- Promise.all with tasks
- Promise.race with tasks
- Sequential processing
- Data passing to workers
- Worker isolation
- Error recovery
- Mixed sync/async operations
- Complex workflow patterns

**Usage:** `/rjs server test-threading` (from in-game command)

#### `../server/test-combined-workflow.js` - Real-World Workflows
Demonstrates realistic usage patterns:
1. Parallel pool scanning with Promise.all
2. Sequential processing with progress updates
3. Find structures with entities
4. Analyze entity distribution
5. Concurrent reads with aggregation
6. Error recovery in workflow
7. Data passing to workers

**Usage:** `/rjs server test-combined-workflow`

#### `../server/test-painting-fixer.js` - Production Example
Real-world use case: Fix painting Y-offsets in structure files:
- Parallel processing of multiple pools
- Structure.list(), read(), write()
- Structure.nbt.filter() and .some()
- Progress reporting
- Statistics aggregation
- Automatic backup creation

**Usage:** `/rjs server test-painting-fixer`

#### `../server/test-structure.js` - Structure API
Tests Structure API operations:
1. List all structures
2. List structures by pool
3. Read structure data
4. Read with .nbt extension normalization
5. Structure.nbt.forEach()
6. Structure.nbt.filter() for paintings
7. Structure.nbt.find() first entity
8. Structure.nbt.some() check for entities
9. Write structure (creates backup)
10. Filter and process pool structures

**Usage:** `/rjs server test-structure`

## Test Categories

### By API Surface
- **Console/Logger**: smoke-test
- **Threading (task/wait)**: smoke-test, test-threading, test-errors
- **Promise Extensions**: test-promise-polyfills, test-threading
- **Runtime API**: test-runtime
- **Structure API**: test-structure, test-combined-workflow, test-painting-fixer
- **Error Handling**: test-errors

### By Execution Context
- **scripts/ (UTILITY)**: All scripts in `/testing/scripts/`
- **server/ (SERVER)**: All scripts in `/testing/server/`
- **startup/ (STARTUP)**: Tests in `/testing/startup/`

## Expected Behavior

### Worker Isolation
Workers have **NO** access to parent scope. Data must be passed as arguments:
```javascript
// ❌ WRONG - closureVar not accessible in worker
const closureVar = 'test';
task(() => {
    console.log(closureVar); // undefined
});

// ✓ CORRECT - pass data explicitly
const data = 'test';
task((value) => {
    console.log(value); // 'test'
}, data);
```

### Promise Chaining
```javascript
// Sequential: task → wait → task
task(() => 'step1')
    .thenWait(20)  // Wait 1 second
    .thenTask((result) => result + '-step2')
    .then((final) => console.log(final)); // 'step1-step2'

// Parallel: multiple tasks at once
Promise.all([
    task(() => 'a'),
    task(() => 'b'),
    task(() => 'c')
]).then((results) => console.log(results)); // ['a', 'b', 'c']
```

### Error Handling
All async operations return Promises - use `.catch()`:
```javascript
task(() => {
    throw new Error('oops');
}).catch((e) => {
    console.error('Caught:', e.message);
});
```

## Troubleshooting

### Test 8 (Worker Isolation) Fails
If Test 8 shows "Worker sees parent scope", worker isolation is broken. Workers should NOT have access to `closureVar` from parent scope.

### Timeout Errors
If tests timeout:
1. Check event loop is processing: `EventLoop.runUntilComplete()` called?
2. Increase timeout: `Runtime.setEventLoopTimeout(120000)` at top of script
3. Check for deadlocks: workers waiting on each other

### Structure API Tests Fail
Structure API requires injection during platform initialization. If tests show "Structure is not defined":
1. Verify Structure API is injected in platform init code
2. Check structure directories exist and are readable
3. Run tests from correct context (server_scripts/)

## Development Workflow

1. **Make changes** to RhettJS core
2. **Run smoke test** to verify basics: `/rjs run smoke-test`
3. **Run specific tests** for affected areas
4. **Run full suite** before committing:
   ```
   /rjs run smoke-test
   /rjs run test-errors
   /rjs run test-runtime
   /rjs run test-promise-polyfills
   /rjs server test-threading
   /rjs server test-structure
   ```

## Adding New Tests

1. Create file in appropriate directory:
   - `/testing/scripts/` for UTILITY context tests
   - `/testing/server/` for SERVER context tests
   - `/testing/startup/` for STARTUP context tests

2. Follow naming convention: `test-<feature>.js`

3. Use clear test structure:
   ```javascript
   console.log('[Test Name] Test N: Description');
   // test code
   .then(() => {
       console.log('[Test Name] ✓ Test N: Success message');
   }).catch((e) => {
       console.error('[Test Name] ✗ Test N FAILED:', e.message);
   });
   ```

4. Add to this README under appropriate category

5. Test your test! Verify it catches regressions.

## CI/CD Integration

These tests can be run in CI via headless Minecraft server:
```bash
# Start server
./gradlew runServer &
SERVER_PID=$!

# Wait for server ready
sleep 30

# Run tests via RCON
rcon-cli "/rjs run smoke-test"
rcon-cli "/rjs run test-errors"
# ... etc

# Check logs for failures
grep "FAILED" logs/latest.log && exit 1

# Cleanup
kill $SERVER_PID
```

## Performance Benchmarks

Expected execution times (on modern hardware):
- `smoke-test.js`: ~1.5 seconds
- `test-errors.js`: ~0.6 seconds
- `test-runtime.js`: ~0.5 seconds
- `test-promise-polyfills.js`: ~1 second
- `test-threading.js`: ~7 seconds
- `test-structure.js`: ~2 seconds (depends on structure count)

If tests run significantly slower, investigate:
- Worker pool initialization
- Event loop microtask processing
- Structure I/O performance
- Timer resolution

## Support

For issues or questions:
- Check dev-docs/SPEC_pt*.md for architectural details
- Check dev-docs/event-loop-model.md for concurrency model
- Open issue on GitHub with test output
