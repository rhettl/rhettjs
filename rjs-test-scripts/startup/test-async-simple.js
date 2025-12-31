// Simple Async/Await Test
// Quick test to verify wait() works

console.log("=".repeat(50));
console.log("Simple Async/Await Test");
console.log("=".repeat(50));

console.log("Starting...");
console.log("Waiting 5 ticks...");

await wait(5);

console.log("✓ Wait completed!");
console.log("Testing sequential waits...");

await wait(3);
console.log("✓ First wait done");

await wait(3);
console.log("✓ Second wait done");

console.log("\nAsync/await with wait() is working!");
console.log("=".repeat(50));
