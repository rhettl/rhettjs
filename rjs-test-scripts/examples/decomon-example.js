import { decomon, DecomonManager } from '../modules/decomon.js';

// ============================================
// DECOMON USAGE EXAMPLES
// ============================================
// This file shows how to use the Decomon system both
// programmatically and through chat commands

// Note: Commands are automatically registered by server/decomon.js
// which is loaded at startup. No manual registration needed!

console.log("[Decomon] Loading example script...");
console.log("[Decomon] Commands are available via /decomon help");

// ===========================================
// PROGRAMMATIC USAGE EXAMPLES
// ===========================================

/**
 * Example 1: Spawn a decorative Pikachu at a specific location
 */
export function spawnPikachu(x, y, z) {
  const entityUuid = decomon.spawn(
    { x, y, z },
    {
      species: "pikachu",
      level: 50,
      shiny: false,
      scale: 100
    }
  );

  console.log(`Spawned Pikachu with UUID: ${entityUuid}`);
  return entityUuid;
}

/**
 * Example 2: Spawn a shiny Charizard and configure it
 */
export function spawnGiantShinyCharizard(x, y, z) {
  // Spawn the entity
  const entityUuid = decomon.spawn(
    { x, y, z },
    {
      species: "charizard",
      level: 100,
      shiny: true,
      scale: 200  // 2x size
    }
  );

  // Configure it further
  decomon.rename(entityUuid, "Guardian Dragon");
  decomon.setPose(entityUuid, "fly");
  decomon.toggleInvulnerable(entityUuid);  // Make invulnerable
  decomon.toggleWandering(entityUuid);     // Disable wandering

  console.log("Spawned giant shiny Charizard!");
  return entityUuid;
}

/**
 * Example 3: Spawn multiple decorative Eevee in a circle
 */
export function spawnEeveeCircle(centerX, centerY, centerZ, radius = 5, count = 8) {
  const entities = [];

  for (let i = 0; i < count; i++) {
    const angle = (i / count) * Math.PI * 2;
    const x = centerX + Math.cos(angle) * radius;
    const z = centerZ + Math.sin(angle) * radius;

    const entityUuid = decomon.spawn(
      { x, y: centerY, z },
      {
        species: "eevee",
        level: 30,
        shiny: Math.random() < 0.3  // 30% chance of shiny
      }
    );

    // Make them all sit
    decomon.setPose(entityUuid, "sit");
    decomon.toggleWandering(entityUuid);  // Disable wandering

    entities.push(entityUuid);
  }

  console.log(`Spawned ${count} Eevee in a circle!`);
  return entities;
}

/**
 * Example 4: Create a village guard setup
 */
export function createVillageGuards(positions) {
  const guards = [];

  positions.forEach((pos, index) => {
    const species = ["lucario", "arcanine", "houndoom", "manectric"][index % 4];

    const entityUuid = decomon.spawn(
      pos,
      {
        species,
        level: 70,
        scale: 120  // Slightly larger
      }
    );

    decomon.rename(entityUuid, "Village Guard");
    decomon.setPose(entityUuid, "stand");
    decomon.toggleWandering(entityUuid);  // Stationary
    decomon.toggleInvulnerable(entityUuid);  // Invulnerable

    guards.push(entityUuid);
  });

  console.log(`Created ${guards.length} village guards!`);
  return guards;
}

/**
 * Example 5: Create a decoration scene (pond with water types)
 */
export function createPondScene(centerX, centerY, centerZ) {
  const scene = [];

  // Center: Big Gyarados
  const gyarados = decomon.spawn(
    { x: centerX, y: centerY, z: centerZ },
    { species: "gyarados", level: 80, scale: 150 }
  );
  decomon.setPose(gyarados, "swim");
  decomon.toggleWandering(gyarados);
  scene.push(gyarados);

  // Surrounding: Magikarp jumping
  const offsets = [
    { x: 3, z: 0 }, { x: -3, z: 0 },
    { x: 0, z: 3 }, { x: 0, z: -3 }
  ];

  offsets.forEach(offset => {
    const magikarp = decomon.spawn(
      { x: centerX + offset.x, y: centerY, z: centerZ + offset.z },
      { species: "magikarp", level: 10, scale: 80 }
    );
    decomon.setPose(magikarp, "float");
    decomon.toggleWandering(magikarp);
    scene.push(magikarp);
  });

  console.log("Created pond scene!");
  return scene;
}

/**
 * Example 6: Get info about all decorative Cobblemon
 */
export function listAllDecomon() {
  const all = decomon.listAll();

  console.log(`\n=== Decorative Cobblemon (${all.length}) ===`);
  all.forEach((entity, index) => {
    console.log(`${index + 1}. ${entity.species} (Lv${entity.level})`);
    console.log(`   Shiny: ${entity.shiny}`);
    console.log(`   Scale: ${entity.scale}%`);
    console.log(`   Pose: ${entity.pose}`);
    if (entity.customName) {
      console.log(`   Name: "${entity.customName}"`);
    }
  });

  return all;
}

/**
 * Example 7: Cleanup - remove all decorative Cobblemon
 */
export function removeAllDecomon() {
  const all = decomon.listAll();
  let removed = 0;

  all.forEach(entity => {
    try {
      decomon.remove(entity.uuid);
      removed++;
    } catch (e) {
      console.error(`Failed to remove ${entity.uuid}: ${e.message}`);
    }
  });

  console.log(`Removed ${removed} decorative Cobblemon`);
  return removed;
}

/**
 * Example 8: Create a custom manager instance (advanced)
 */
export function createCustomManager() {
  // You can create your own manager instance if you want
  // separate tracking from the global one
  const customManager = new DecomonManager();

  // Use it the same way
  const entityUuid = customManager.spawn(
    { x: 0, y: 64, z: 0 },
    { species: "mew", level: 100, shiny: true }
  );

  return { manager: customManager, entityUuid };
}

// ===========================================
// CHAT COMMAND EXAMPLES
// ===========================================
/*
After loading this script, you can use these commands in chat:

BASIC WORKFLOW:
1. /decomon spawn pikachu 50
2. /decomon toggle shiny
3. /decomon pose sit
4. /decomon rename Pikachu Guard
5. /decomon info

SELECTION WORKFLOW:
1. /decomon spawn charizard 70
2. Move away
3. /decomon spawn eevee 30
4. /decomon select (selects nearest)
5. /decomon toggle wandering
6. /decomon move (teleports to you)

FULL MENU:
- /decomon help - Show clickable menu with all commands

SPAWNING:
- /decomon spawn <species> [level] [shiny] [scale]
  Examples:
  - /decomon spawn pikachu
  - /decomon spawn charizard 100 true 200

SELECTION:
- /decomon select - Select nearest within 10 blocks
- /decomon highlight - Highlight selected with glowing

TOGGLES:
- /decomon toggle shiny
- /decomon toggle wandering
- /decomon toggle invulnerable
- /decomon toggle looks-at-player
- /decomon toggle no-ai

POSES:
- /decomon pose stand
- /decomon pose sit
- /decomon pose fly
- /decomon pose hover
- /decomon pose sleep
- /decomon pose float
- /decomon pose glide
- /decomon pose swim
- /decomon pose walk

PROPERTIES:
- /decomon scale <percent> - e.g., /decomon scale 150
- /decomon level <1-100> - e.g., /decomon level 80
- /decomon rename <name> - e.g., /decomon rename Guardian

OTHER:
- /decomon move - Teleport selected to you
- /decomon info - Show selected info
- /decomon list - List all decorative Cobblemon
- /decomon remove - Delete selected
*/

console.log("[Decomon] Example script loaded!");
console.log("[Decomon] Try the functions in the console or use chat commands");
console.log("[Decomon] Examples:");
console.log("  - spawnPikachu(100, 64, 200)");
console.log("  - spawnGiantShinyCharizard(0, 70, 0)");
console.log("  - spawnEeveeCircle(0, 64, 0, 10, 12)");
console.log("  - listAllDecomon()");
console.log("  - removeAllDecomon()");
