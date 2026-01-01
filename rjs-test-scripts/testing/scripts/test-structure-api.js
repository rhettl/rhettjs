// Test Structure API
// Tests structure save/load and world operations

import Structure from 'Structure';

console.log("=".repeat(50));
console.log("Structure API Test");
console.log("=".repeat(50));

// Test 1: Capture a structure from the world
console.log("\n[Test 1] Capturing structure from world...");
try {
  await Structure.capture(
    'test-structure',
    { x: 0, y: 60, z: 0, dimension: 'minecraft:overworld' },
    { x: 5, y: 65, z: 5, dimension: 'minecraft:overworld' }
  );
  console.log("  ✓ Structure.capture() working");
} catch (error) {
  console.error("  ✗ Structure.capture() failed:", error.message);
}

// Test 2: Check if structure exists
console.log("\n[Test 2] Checking if structure exists...");
try {
  const exists = await Structure.exists('test-structure');
  console.log("  Structure.exists('test-structure'):", exists);
  const notExists = await Structure.exists('nonexistent-structure');
  console.log("  Structure.exists('nonexistent-structure'):", notExists);
  console.log("  ✓ Structure.exists() working");
} catch (error) {
  console.error("  ✗ Structure.exists() failed:", error.message);
}

// Test 3: Load structure data
console.log("\n[Test 3] Loading structure data...");
try {
  const data = await Structure.load('test-structure');
  console.log("  Structure data keys:", Object.keys(data));
  console.log("  Structure size:", JSON.stringify(data.size));
  console.log("  ✓ Structure.load() working");
} catch (error) {
  console.error("  ✗ Structure.load() failed:", error.message);
}

// Test 4: Place structure in world
console.log("\n[Test 4] Placing structure in world...");
try {
  await Structure.place(
    'test-structure',
    { x: 100, y: 64, z: 100, dimension: 'minecraft:overworld' },
    0  // No rotation
  );
  console.log("  ✓ Structure.place() working (no rotation)");
} catch (error) {
  console.error("  ✗ Structure.place() failed:", error.message);
}

// Test 5: Place structure with rotation
console.log("\n[Test 5] Placing structure with rotation...");
try {
  await Structure.place(
    'test-structure',
    { x: 110, y: 64, z: 100, dimension: 'minecraft:overworld' },
    90  // 90 degree rotation
  );
  console.log("  ✓ Structure.place() with rotation working");
} catch (error) {
  console.error("  ✗ Structure.place() with rotation failed:", error.message);
}

// Test 6: List all structures
console.log("\n[Test 6] Listing all structures...");
try {
  const structures = await Structure.list();
  console.log("  Total structures:", structures.length);
  console.log("  First few structures:", structures.slice(0, 5));
  console.log("  ✓ Structure.list() working");
} catch (error) {
  console.error("  ✗ Structure.list() failed:", error.message);
}

// Test 7: List structures in a specific pool
console.log("\n[Test 7] Listing structures in pool...");
try {
  const poolStructures = await Structure.list('village');
  console.log("  Structures in 'village' pool:", poolStructures.length);
  console.log("  ✓ Structure.list(pool) working");
} catch (error) {
  console.error("  ✗ Structure.list(pool) failed:", error.message);
}

// Test 8: Save custom structure data
console.log("\n[Test 8] Saving custom structure data...");
try {
  const customData = {
    size: [5, 5, 5],
    blocks: [
      { pos: [0, 0, 0], state: 0 },
      { pos: [1, 0, 0], state: 1 }
    ],
    palette: [
      { Name: 'minecraft:stone' },
      { Name: 'minecraft:dirt' }
    ]
  };
  await Structure.save('custom-structure', customData);
  console.log("  ✓ Structure.save() working");
} catch (error) {
  console.error("  ✗ Structure.save() failed:", error.message);
}

// Test 9: Load custom structure data
console.log("\n[Test 9] Loading custom structure data...");
try {
  const loadedData = await Structure.load('custom-structure');
  console.log("  Custom structure size:", JSON.stringify(loadedData.size));
  console.log("  Custom structure blocks:", loadedData.blocks.length);
  console.log("  ✓ Structure.load() working for custom data");
} catch (error) {
  console.error("  ✗ Structure.load() failed:", error.message);
}

// Test 10: Delete structure
console.log("\n[Test 10] Deleting structure...");
try {
  const deleted = await Structure.delete('custom-structure');
  console.log("  Structure.delete('custom-structure'):", deleted);
  const stillExists = await Structure.exists('custom-structure');
  console.log("  Still exists after delete:", stillExists);
  console.log("  ✓ Structure.delete() working");
} catch (error) {
  console.error("  ✗ Structure.delete() failed:", error.message);
}

// Cleanup: Delete test structure
console.log("\n[Cleanup] Deleting test structures...");
try {
  await Structure.delete('test-structure');
  console.log("  ✓ Cleanup complete");
} catch (error) {
  console.error("  ✗ Cleanup failed:", error.message);
}

console.log("\n" + "=".repeat(50));
console.log("All Structure API tests completed!");
console.log("=".repeat(50));
