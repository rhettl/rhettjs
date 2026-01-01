// Test World API
// Tests block operations, entity operations, and time/weather

import World from 'World';

console.log("=".repeat(50));
console.log("World API Test");
console.log("=".repeat(50));

// Test 1: Get dimension list (sync property)
console.log("\n[Test 1] Getting dimension list...");
try {
  console.log("  World.dimensions:", World.dimensions);
  console.log("  ✓ World.dimensions working (sync property)");
} catch (error) {
  console.error("  ✗ World.dimensions failed:", error.message);
}

// Test 2: Get block at position
console.log("\n[Test 2] Getting block at position...");
try {
  const block = await World.getBlock({
    x: 0,
    y: 64,
    z: 0,
    dimension: 'minecraft:overworld'
  });
  console.log("  Block at (0, 64, 0):");
  console.log("    id:", block.id);
  console.log("    position:", JSON.stringify(block.position));
  console.log("    properties:", JSON.stringify(block.properties));
  console.log("    nbt:", block.nbt ? "present" : "null");
  console.log("  ✓ World.getBlock() working");
} catch (error) {
  console.error("  ✗ World.getBlock() failed:", error.message);
}

// Test 3: Set block at position
console.log("\n[Test 3] Setting block at position...");
try {
  await World.setBlock(
    { x: 0, y: 64, z: 0, dimension: 'minecraft:overworld' },
    'minecraft:stone'
  );
  const block = await World.getBlock({
    x: 0,
    y: 64,
    z: 0,
    dimension: 'minecraft:overworld'
  });
  console.log("  Block after setBlock:", block.id);
  console.log("  ✓ World.setBlock() working");
} catch (error) {
  console.error("  ✗ World.setBlock() failed:", error.message);
}

// Test 4: Set block with properties
console.log("\n[Test 4] Setting block with properties...");
try {
  await World.setBlock(
    { x: 1, y: 64, z: 0, dimension: 'minecraft:overworld' },
    'minecraft:lever',
    { facing: 'north', powered: 'false' }
  );
  const block = await World.getBlock({
    x: 1,
    y: 64,
    z: 0,
    dimension: 'minecraft:overworld'
  });
  console.log("  Lever block properties:", JSON.stringify(block.properties));
  console.log("  ✓ World.setBlock() with properties working");
} catch (error) {
  console.error("  ✗ World.setBlock() with properties failed:", error.message);
}

// Test 5: Fill area with blocks
console.log("\n[Test 5] Filling area with blocks...");
try {
  const count = await World.fill(
    { x: 10, y: 64, z: 10, dimension: 'minecraft:overworld' },
    { x: 15, y: 69, z: 15, dimension: 'minecraft:overworld' },
    'minecraft:air'
  );
  console.log("  Blocks filled:", count);
  console.log("  ✓ World.fill() working");
} catch (error) {
  console.error("  ✗ World.fill() failed:", error.message);
}

// Test 6: Replace blocks in area
console.log("\n[Test 6] Replacing blocks in area...");
try {
  // First fill area with stone
  await World.fill(
    { x: 20, y: 64, z: 20, dimension: 'minecraft:overworld' },
    { x: 25, y: 69, z: 25, dimension: 'minecraft:overworld' },
    'minecraft:stone'
  );

  // Then replace stone with dirt
  const count = await World.replace(
    { x: 20, y: 64, z: 20, dimension: 'minecraft:overworld' },
    { x: 25, y: 69, z: 25, dimension: 'minecraft:overworld' },
    'minecraft:stone',
    'minecraft:dirt'
  );
  console.log("  Blocks replaced:", count);
  console.log("  ✓ World.replace() working");
} catch (error) {
  console.error("  ✗ World.replace() failed:", error.message);
}

// Test 7: Get all online players
console.log("\n[Test 7] Getting all online players...");
try {
  const players = await World.getPlayers();
  console.log("  Online players:", players.length);
  if (players.length > 0) {
    const player = players[0];
    console.log("  First player:");
    console.log("    name:", player.name);
    console.log("    uuid:", player.uuid);
    console.log("    position:", JSON.stringify(player.position));
    console.log("    health:", player.health);
    console.log("    maxHealth:", player.maxHealth);
    console.log("    gameMode:", player.gameMode);
  }
  console.log("  ✓ World.getPlayers() working");
} catch (error) {
  console.error("  ✗ World.getPlayers() failed:", error.message);
}

// Test 8: Get specific player
console.log("\n[Test 8] Getting specific player...");
try {
  const players = await World.getPlayers();
  if (players.length > 0) {
    const targetName = players[0].name;
    const player = await World.getPlayer(targetName);
    console.log("  Found player:", player ? player.name : "null");
    console.log("  ✓ World.getPlayer() working");
  } else {
    console.log("  (Skipped - no players online)");
  }
} catch (error) {
  console.error("  ✗ World.getPlayer() failed:", error.message);
}

// Test 9: Spawn entity
console.log("\n[Test 9] Spawning entity...");
try {
  const entity = await World.spawnEntity(
    { x: 0, y: 65, z: 0, dimension: 'minecraft:overworld' },
    'minecraft:zombie'
  );
  console.log("  Spawned entity:");
  console.log("    id:", entity.id);
  console.log("    uuid:", entity.uuid);
  console.log("    position:", JSON.stringify(entity.position));
  console.log("    health:", entity.health);
  console.log("    isAlive:", entity.isAlive);
  console.log("  ✓ World.spawnEntity() working");

  // Clean up - remove the entity
  entity.remove();
  console.log("  ✓ Entity.remove() working");
} catch (error) {
  console.error("  ✗ World.spawnEntity() failed:", error.message);
}

// Test 10: Get entities in radius
console.log("\n[Test 10] Getting entities in radius...");
try {
  const entities = await World.getEntities(
    { x: 0, y: 64, z: 0, dimension: 'minecraft:overworld' },
    50  // 50 block radius
  );
  console.log("  Entities found:", entities.length);
  if (entities.length > 0) {
    console.log("  First entity:");
    console.log("    id:", entities[0].id);
    console.log("    position:", JSON.stringify(entities[0].position));
  }
  console.log("  ✓ World.getEntities() working");
} catch (error) {
  console.error("  ✗ World.getEntities() failed:", error.message);
}

// Test 11: Get world time
console.log("\n[Test 11] Getting world time...");
try {
  const time = await World.getTime();
  console.log("  Current time:", time);
  console.log("  ✓ World.getTime() working");
} catch (error) {
  console.error("  ✗ World.getTime() failed:", error.message);
}

// Test 12: Set world time
console.log("\n[Test 12] Setting world time...");
try {
  const oldTime = await World.getTime();
  await World.setTime(6000); // Set to noon
  const newTime = await World.getTime();
  console.log("  Old time:", oldTime);
  console.log("  New time:", newTime);
  console.log("  ✓ World.setTime() working");
} catch (error) {
  console.error("  ✗ World.setTime() failed:", error.message);
}

// Test 13: Get weather
console.log("\n[Test 13] Getting weather...");
try {
  const weather = await World.getWeather();
  console.log("  Current weather:", weather);
  console.log("  ✓ World.getWeather() working");
} catch (error) {
  console.error("  ✗ World.getWeather() failed:", error.message);
}

// Test 14: Set weather
console.log("\n[Test 14] Setting weather...");
try {
  await World.setWeather('clear');
  const weather = await World.getWeather();
  console.log("  Weather after setWeather('clear'):", weather);
  console.log("  ✓ World.setWeather() working");
} catch (error) {
  console.error("  ✗ World.setWeather() failed:", error.message);
}

// Test 15: Player properties are live views
console.log("\n[Test 15] Testing live player properties...");
try {
  const players = await World.getPlayers();
  if (players.length > 0) {
    const player = players[0];
    console.log("  Initial health:", player.health);
    player.setHealth(10);
    console.log("  Health after setHealth(10):", player.health);
    player.setHealth(player.maxHealth);
    console.log("  Health after setHealth(maxHealth):", player.health);
    console.log("  ✓ Live player properties working");
  } else {
    console.log("  (Skipped - no players online)");
  }
} catch (error) {
  console.error("  ✗ Live player properties failed:", error.message);
}

console.log("\n" + "=".repeat(50));
console.log("All World API tests completed!");
console.log("=".repeat(50));
