// Module Import Test (Testing Directory)
// Verify imports work from testing/ directory too

console.log("=".repeat(50));
console.log("Module Import Test (Testing Dir)");
console.log("=".repeat(50));

// Import from testing/modules/ (relative path from startup/ to modules/)
import { formatMessage, logTest } from '../modules/test-helper.js';

console.log("\n" + formatMessage("TEST", "Testing module imports from testing/"));

logTest("Import from testing/modules/", true);
logTest("formatMessage() works", formatMessage("FOO", "bar") === "[FOO] bar");

console.log("\n" + "=".repeat(50));
console.log("Testing directory imports work!");
console.log("=".repeat(50));
