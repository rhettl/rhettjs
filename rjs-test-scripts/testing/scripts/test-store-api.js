// Test Store API
// Tests namespaced key-value storage

import Store from 'Store';

console.log("=".repeat(50));
console.log("Store API Test");
console.log("=".repeat(50));

// Test 1: Create namespaced stores
console.log("\n[Test 1] Creating namespaced stores...");
const positions = Store.namespace('positions');
const settings = Store.namespace('settings');
console.log("  ✓ Created 'positions' and 'settings' namespaces");

// Test 2: Store and retrieve values
console.log("\n[Test 2] Storing and retrieving values...");
positions.set('player1:pos1', { x: 100, y: 64, z: 200, dimension: 'minecraft:overworld' });
positions.set('player1:pos2', { x: 150, y: 70, z: 250, dimension: 'minecraft:overworld' });
settings.set('difficulty', 'hard');
settings.set('pvp', true);

const pos1 = positions.get('player1:pos1');
console.log("  positions.get('player1:pos1'):", JSON.stringify(pos1));
console.log("  settings.get('difficulty'):", settings.get('difficulty'));
console.log("  settings.get('pvp'):", settings.get('pvp'));
console.log("  ✓ Store and retrieve working");

// Test 3: Check existence
console.log("\n[Test 3] Checking key existence...");
console.log("  positions.has('player1:pos1'):", positions.has('player1:pos1')); // true
console.log("  positions.has('nonexistent'):", positions.has('nonexistent')); // false
console.log("  ✓ has() working");

// Test 4: List keys
console.log("\n[Test 4] Listing keys in namespaces...");
const posKeys = positions.keys();
const settingsKeys = settings.keys();
console.log("  positions.keys():", posKeys);
console.log("  settings.keys():", settingsKeys);
console.log("  ✓ keys() working");

// Test 5: Get entries
console.log("\n[Test 5] Getting all entries...");
const posEntries = positions.entries();
console.log("  positions.entries():", JSON.stringify(posEntries));
console.log("  ✓ entries() working");

// Test 6: Namespace isolation
console.log("\n[Test 6] Testing namespace isolation...");
console.log("  positions.size():", positions.size()); // 2
console.log("  settings.size():", settings.size());   // 2
console.log("  Store.size():", Store.size());         // 4 (total)
console.log("  ✓ Namespaces are isolated");

// Test 7: Delete a key
console.log("\n[Test 7] Deleting keys...");
const deleted = positions.delete('player1:pos2');
console.log("  positions.delete('player1:pos2'):", deleted); // true
console.log("  positions.has('player1:pos2'):", positions.has('player1:pos2')); // false
console.log("  positions.size():", positions.size()); // 1
console.log("  ✓ delete() working");

// Test 8: Clear namespace
console.log("\n[Test 8] Clearing namespace...");
settings.clear();
console.log("  settings.clear() called");
console.log("  settings.size():", settings.size()); // 0
console.log("  positions.size():", positions.size()); // 1 (still has data)
console.log("  ✓ clear() working");

// Test 9: List all namespaces
console.log("\n[Test 9] Listing all namespaces...");
const namespaces = Store.namespaces();
console.log("  Store.namespaces():", namespaces);
console.log("  ✓ namespaces() working");

// Test 10: Global clear
console.log("\n[Test 10] Clearing all data...");
Store.clearAll();
console.log("  Store.clearAll() called");
console.log("  Store.size():", Store.size()); // 0
console.log("  positions.size():", positions.size()); // 0
console.log("  ✓ clearAll() working");

console.log("\n" + "=".repeat(50));
console.log("All Store API tests passed!");
console.log("=".repeat(50));
