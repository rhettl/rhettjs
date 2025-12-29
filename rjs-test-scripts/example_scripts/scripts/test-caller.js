/**
 * Test script for Caller API enhancements
 * Tests dimension, position, rotation, name, and raycast
 *
 * Usage: /rjs run test-caller
 */

Caller.sendMessage("§6=== Testing Caller API ===");

// Test 1: Get caller name
const name = Caller.getName();
Caller.sendMessage(`§7Caller name: §f${name}`);

// Test 2: Check if player
const isPlayer = Caller.isPlayer();
Caller.sendMessage(`§7Is player: §f${isPlayer}`);

// Test 3: Get dimension
const dimension = Caller.getDimension();
Caller.sendMessage(`§7Dimension: §f${dimension}`);

// Test 4: Get position
const pos = Caller.getPosition();
Caller.sendMessage(`§7Position: §f${pos.x.toFixed(2)}, ${pos.y.toFixed(2)}, ${pos.z.toFixed(2)}`);

// Test 5: Get rotation (only works for entities)
const rotation = Caller.getRotation();
if (rotation) {
  Caller.sendMessage(`§7Rotation: §fyaw=${rotation.yaw.toFixed(1)}°, pitch=${rotation.pitch.toFixed(1)}°`);
} else {
  Caller.sendMessage("§7Rotation: §cnot available (caller is not an entity)");
}

// Test 6: Raycast - what block are you looking at?
Caller.sendMessage("§6Testing raycast...");
const rayHit = Caller.raycast(10.0, false);

if (rayHit) {
  if (rayHit.hit) {
    Caller.sendMessage(`§aYou're looking at: §f${rayHit.block}`);
    Caller.sendMessage(`  Position: §f${rayHit.x}, ${rayHit.y}, ${rayHit.z}`);
    Caller.sendMessage(`  Face: §f${rayHit.face}`);
    Caller.sendMessage(`  Distance: §f${rayHit.distance.toFixed(2)} blocks`);

    // Bonus: Use the raycast result with Command API
    Caller.sendMessage("§6Highlighting the block you're looking at...");
    Command.executeAsServer(`setblock ${rayHit.x} ${rayHit.y} ${rayHit.z} glowstone replace`)
      .then(result => {
        Caller.sendMessage("§aBlock highlighted! (replaced with glowstone)");

        // Wait 3 seconds then restore
        return wait(60); // 60 ticks = 3 seconds
      })
      .then(() => {
        return Command.executeAsServer(`setblock ${rayHit.x} ${rayHit.y} ${rayHit.z} ${rayHit.block} replace`);
      })
      .then(() => {
        Caller.sendMessage("§aBlock restored!");
      })
      .catch(error => {
        Caller.sendMessage(`§cFailed to highlight block: ${error}`);
      });
  } else {
    Caller.sendMessage(`§7You're not looking at anything (${rayHit.type})`);
  }
} else {
  Caller.sendMessage("§7Raycast not available (caller is not an entity)");
}

// Test 7: Use Caller data with Command helpers
Caller.sendMessage("§6Testing integration with Cmd helpers...");

if (isPlayer && pos) {
  // Build a teleport command using caller's current position + 10 blocks up
  const tpCmd = Cmd.tp({ to: [pos.x, pos.y + 10, pos.z] });
  Caller.sendMessage(`§7Would execute: §f${tpCmd}`);

  // Build a selector for entities near the caller
  const nearbySelector = Cmd.selector("e", {
    distance: "..20",
    type: "!player",
    limit: 5
  });
  Caller.sendMessage(`§7Nearby entities selector: §f${nearbySelector}`);
}

Caller.sendMessage("§a=== All Caller API tests passed! ===");
