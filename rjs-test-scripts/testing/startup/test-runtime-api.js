// Runtime API Smoke Test
// Quick test to verify Runtime API is working

console.log("=".repeat(50));
console.log("Runtime API Smoke Test");
console.log("=".repeat(50));

// Test Runtime.env properties
console.log("\nRuntime Environment:");
console.log("  Version:", Runtime.env.RJS_VERSION);
console.log("  TPS:", Runtime.env.TICKS_PER_SECOND);
console.log("  Debug Mode:", Runtime.env.IS_DEBUG);

// Test Runtime.setScriptTimeout()
console.log("\nTesting Runtime.setScriptTimeout():");
try {
    Runtime.setScriptTimeout(120000);
    console.log("  ✓ Successfully set timeout to 120s");
} catch (e) {
    console.error("  ✗ Failed:", e.message);
}

// Test validation
console.log("\nTesting timeout validation:");
try {
    Runtime.setScriptTimeout(500); // Should fail
    console.error("  ✗ Should have rejected 500ms");
} catch (e) {
    console.log("  ✓ Correctly rejected 500ms:", e.message);
}

// Test Runtime.exit() exists
console.log("\nRuntime.exit() availability:");
if (typeof Runtime.exit === 'function') {
    console.log("  ✓ Runtime.exit() is available");
} else {
    console.error("  ✗ Runtime.exit() not found");
}

console.log("\n" + "=".repeat(50));
console.log("Runtime API Test Complete!");
console.log("=".repeat(50));
