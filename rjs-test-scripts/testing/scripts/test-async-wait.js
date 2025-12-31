// Async/Await wait() Function Test
// Tests the wait() function and async/await support

console.log("=".repeat(60));
console.log("ASYNC/AWAIT WAIT() FUNCTION TEST");
console.log("=".repeat(60));
console.log("");

// Test 1: Simple wait
console.log("[Test 1] Simple wait(5) - waiting 5 ticks...");
const start1 = Date.now();
await wait(5);
const elapsed1 = Date.now() - start1;
console.log(`[Test 1] ✓ Completed after ~${elapsed1}ms (expected ~250ms)`);
console.log("");

// Test 2: Multiple sequential waits
console.log("[Test 2] Sequential waits - wait(10) then wait(10)...");
const start2 = Date.now();
await wait(10);
console.log("[Test 2] First wait completed");
await wait(10);
const elapsed2 = Date.now() - start2;
console.log(`[Test 2] ✓ Completed after ~${elapsed2}ms (expected ~1000ms)`);
console.log("");

// Test 3: Wait in a loop
console.log("[Test 3] Wait in a loop - 3 iterations with wait(3)...");
for (let i = 1; i <= 3; i++) {
    console.log(`[Test 3] Iteration ${i} - waiting...`);
    await wait(3);
    console.log(`[Test 3] Iteration ${i} - complete`);
}
console.log("[Test 3] ✓ Loop completed");
console.log("");

// Test 4: Async function with wait
console.log("[Test 4] Async function with wait...");
async function delayedGreeting(name) {
    console.log(`[Test 4] Starting greeting for ${name}...`);
    await wait(5);
    return `Hello, ${name}!`;
}

const greeting = await delayedGreeting("World");
console.log(`[Test 4] ✓ ${greeting}`);
console.log("");

// Test 5: Promise.all with multiple waits
console.log("[Test 5] Promise.all with parallel waits...");
const start5 = Date.now();
await Promise.all([
    wait(10),
    wait(10),
    wait(10)
]);
const elapsed5 = Date.now() - start5;
console.log(`[Test 5] ✓ All waits completed in ~${elapsed5}ms (expected ~500ms, not 1500ms)`);
console.log("");

// Test 6: Error handling - negative ticks
console.log("[Test 6] Error handling - wait(-1) should throw...");
try {
    await wait(-1);
    console.error("[Test 6] ✗ FAILED: Should have thrown error");
} catch (e) {
    console.log(`[Test 6] ✓ Correctly threw error: ${e.message}`);
}
console.log("");

// Test 7: Error handling - zero ticks
console.log("[Test 7] Error handling - wait(0) should throw...");
try {
    await wait(0);
    console.error("[Test 7] ✗ FAILED: Should have thrown error");
} catch (e) {
    console.log(`[Test 7] ✓ Correctly threw error: ${e.message}`);
}
console.log("");

// Test 8: Error handling - no argument
console.log("[Test 8] Error handling - wait() with no argument should throw...");
try {
    await wait();
    console.error("[Test 8] ✗ FAILED: Should have thrown error");
} catch (e) {
    console.log(`[Test 8] ✓ Correctly threw error: ${e.message}`);
}
console.log("");

// Test 9: Long wait (1 second = 20 ticks)
console.log("[Test 9] Long wait - wait(20) for 1 second...");
const start9 = Date.now();
await wait(20);
const elapsed9 = Date.now() - start9;
console.log(`[Test 9] ✓ Completed after ~${elapsed9}ms (expected ~1000ms)`);
console.log("");

console.log("=".repeat(60));
console.log("ALL ASYNC/AWAIT TESTS COMPLETED SUCCESSFULLY!");
console.log("=".repeat(60));
