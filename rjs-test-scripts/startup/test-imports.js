// Test ES6 Module Imports
// Tests built-in APIs and user modules
// NOTE: Imports are currently relative to script location (not modules/)

console.log("=".repeat(50));
console.log("ES6 Module Import Test");
console.log("=".repeat(50));

// Test 1: Import built-in APIs (no .js = built-in)
console.log("\n[Test 1] Importing built-in APIs...");
import World from 'World';
import Structure from 'Structure';
import Store from 'Store';
import NBT from 'NBT';

console.log("  World:", World.toString());
console.log("  Structure:", Structure.toString());
console.log("  Store:", Store.toString());
console.log("  NBT:", NBT.toString());
console.log("  ✓ Built-in imports successful");

// Test 2: Import user module (relative path from startup/ to modules/)
console.log("\n[Test 2] Importing user module...");
import { add, multiply, PI } from '../modules/math-utils.js';

console.log("  add(5, 3) =", add(5, 3));
console.log("  multiply(4, 7) =", multiply(4, 7));
console.log("  PI =", PI);
console.log("  ✓ User module imports successful");

// Test 3: Import default export
console.log("\n[Test 3] Importing default export...");
import MathUtils from '../modules/math-utils.js';

console.log("  MathUtils.add(10, 20) =", MathUtils.add(10, 20));
console.log("  ✓ Default export import successful");

console.log("\n" + "=".repeat(50));
console.log("All import tests passed!");
console.log("=".repeat(50));
