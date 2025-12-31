// Hello World test for GraalVM migration
// Tests console API and basic script execution

console.log("=".repeat(50));
console.log("Hello from GraalVM!");
console.log("=".repeat(50));

// Test different console methods
console.info("Info: GraalVM ES2022 engine is running");
console.warn("Warning: This is a test warning");
console.debug("Debug: Console API working correctly");

// Test modern JavaScript features
const modernFeatures = {
    topLevelAwait: "supported",
    destructuring: "supported",
    arrowFunctions: "supported",
    templateLiterals: "supported"
};

console.log("Modern JS features:", modernFeatures);

// Test array methods
const numbers = [1, 2, 3, 4, 5];
console.log("Numbers:", numbers);
console.log("Sum:", numbers.reduce((a, b) => a + b, 0));

console.log("=".repeat(50));
console.log("MVP Test Complete!");
console.log("=".repeat(50));
