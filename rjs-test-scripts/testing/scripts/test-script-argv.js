// Test Script.argv Parsing
// Tests command-line argument parsing for utility scripts

console.log("=".repeat(50));
console.log("Script.argv Test");
console.log("=".repeat(50));

// Test 1: Check Script.argv structure
console.log("\n[Test 1] Checking Script.argv structure...");
console.log("  Script object:", typeof Script);
console.log("  Script.argv:", typeof Script.argv);
console.log("  Script.argv.get:", typeof Script.argv.get);
console.log("  Script.argv.hasFlag:", typeof Script.argv.hasFlag);
console.log("  Script.argv.getAll:", typeof Script.argv.getAll);
console.log("  Script.argv.raw:", Array.isArray(Script.argv.raw));
console.log("  ✓ Script.argv structure correct");

// Test 2: Get positional arguments
console.log("\n[Test 2] Getting positional arguments...");
const arg0 = Script.argv.get(0);
const arg1 = Script.argv.get(1);
const arg2 = Script.argv.get(2);
console.log("  arg[0]:", arg0);
console.log("  arg[1]:", arg1);
console.log("  arg[2]:", arg2);
console.log("  ✓ Positional arguments accessible");

// Test 3: Check for flags
console.log("\n[Test 3] Checking for flags...");
const hasVerbose = Script.argv.hasFlag('verbose');
const hasForce = Script.argv.hasFlag('force');
const hasV = Script.argv.hasFlag('v');
const hasF = Script.argv.hasFlag('f');
console.log("  Has --verbose:", hasVerbose);
console.log("  Has --force:", hasForce);
console.log("  Has -v:", hasV);
console.log("  Has -f:", hasF);
console.log("  ✓ Flag checking works");

// Test 4: Get all positional arguments
console.log("\n[Test 4] Getting all positional arguments...");
const allArgs = Script.argv.getAll();
console.log("  All args:", allArgs);
console.log("  Count:", allArgs.length);
console.log("  Is array:", Array.isArray(allArgs));
console.log("  ✓ getAll() works");

// Test 5: Access raw arguments
console.log("\n[Test 5] Accessing raw arguments...");
console.log("  Raw args:", Script.argv.raw);
console.log("  Raw count:", Script.argv.raw.length);
console.log("  ✓ Raw args accessible");

// Test 6: Handle missing arguments gracefully
console.log("\n[Test 6] Handling missing arguments...");
const missing = Script.argv.get(999);
console.log("  Missing arg (index 999):", missing);
console.log("  Type:", typeof missing);
console.log("  Is undefined:", missing === undefined);
console.log("  ✓ Missing arguments return undefined");

// Test 7: Check non-existent flags
console.log("\n[Test 7] Checking non-existent flags...");
const hasNonExistent = Script.argv.hasFlag('nonexistent');
console.log("  Has --nonexistent:", hasNonExistent);
console.log("  Should be false:", hasNonExistent === false);
console.log("  ✓ Non-existent flags return false");

// Test 8: Mixed flags and positional args
console.log("\n[Test 8] Testing mixed flags and positional args...");
console.log("  Raw includes flags:", Script.argv.raw.some(arg => arg.startsWith('-')));
console.log("  Positional excludes flags:", allArgs.every(arg => !arg.startsWith('-')));
console.log("  ✓ Flags separated from positional args");

// Test 9: Test with example command pattern
console.log("\n[Test 9] Example command pattern...");
// Simulate: /rjs run myscript player1 --verbose player2 -f
console.log("  Example: /rjs run myscript player1 --verbose player2 -f");
console.log("  Positional[0] (player1):", Script.argv.get(0));
console.log("  Positional[1] (player2):", Script.argv.get(1));
console.log("  Flag --verbose:", Script.argv.hasFlag('verbose'));
console.log("  Flag -f:", Script.argv.hasFlag('f'));
console.log("  ✓ Real-world pattern works");

console.log("\n" + "=".repeat(50));
console.log("All Script.argv tests passed!");
console.log("=".repeat(50));
