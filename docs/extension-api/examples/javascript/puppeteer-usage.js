/**
 * Example JavaScript usage of the Puppeteer extension.
 *
 * This would be placed in rjs/scripts/ and run via `/rjs run puppeteer-demo`.
 */

import Puppeteer from 'rhettjs/puppeteer';
import World from 'rhettjs/world';

console.log("=== Puppeteer Demo ===");

// Example 1: Spawn a single puppet
async function example1_spawnSingle() {
    console.log("\n--- Example 1: Spawn Single Puppet ---");

    const puppet = await Puppeteer.spawn(
        { x: 100, y: 64, z: 200 },
        { name: "Steve", skin: "minecraft:textures/entity/steve.png" }
    );

    console.log(`Spawned puppet: ${puppet.name}`);
    console.log(`ID: ${puppet.id}`);
    console.log(`Position: ${puppet.position.x}, ${puppet.position.y}, ${puppet.position.z}`);

    return puppet;
}

// Example 2: Control a puppet
async function example2_controlPuppet(puppet) {
    console.log("\n--- Example 2: Control Puppet ---");

    const controller = Puppeteer.control(puppet.id);

    if (!controller) {
        console.log("Puppet not found!");
        return;
    }

    // Move the puppet
    console.log("Moving puppet to (110, 64, 200)...");
    await controller.move({ x: 110, y: 64, z: 200 });
    console.log("Movement complete!");

    // Rotate the puppet
    console.log("Rotating puppet...");
    controller.rotate(90, 0); // Face east

    // Rename the puppet
    console.log("Renaming puppet...");
    controller.setName("Guard");

    console.log(`Puppet position: ${controller.getPosition().x}, ${controller.getPosition().y}, ${controller.getPosition().z}`);
}

// Example 3: Spawn multiple puppets in a formation
async function example3_spawnFormation() {
    console.log("\n--- Example 3: Spawn Formation ---");

    const positions = [
        { x: 100, y: 64, z: 100 },
        { x: 105, y: 64, z: 100 },
        { x: 110, y: 64, z: 100 },
        { x: 100, y: 64, z: 105 },
        { x: 105, y: 64, z: 105 },
        { x: 110, y: 64, z: 105 }
    ];

    console.log(`Spawning ${positions.length} puppets...`);

    const puppets = await Promise.all(
        positions.map((pos, index) =>
            Puppeteer.spawn(pos, { name: `Guard ${index + 1}` })
        )
    );

    console.log(`Spawned ${puppets.length} puppets`);
    puppets.forEach(p => console.log(`- ${p.name} at (${p.position.x}, ${p.position.z})`));

    return puppets;
}

// Example 4: Patrol route
async function example4_patrolRoute(puppet) {
    console.log("\n--- Example 4: Patrol Route ---");

    const controller = Puppeteer.control(puppet.id);
    if (!controller) {
        console.log("Puppet not found!");
        return;
    }

    const route = [
        { x: 100, y: 64, z: 100 },
        { x: 110, y: 64, z: 100 },
        { x: 110, y: 64, z: 110 },
        { x: 100, y: 64, z: 110 }
    ];

    console.log("Starting patrol...");

    for (const waypoint of route) {
        console.log(`Moving to (${waypoint.x}, ${waypoint.z})...`);
        await controller.move(waypoint);

        // Rotate to face next waypoint
        const currentIndex = route.indexOf(waypoint);
        const nextIndex = (currentIndex + 1) % route.length;
        const next = route[nextIndex];

        const dx = next.x - waypoint.x;
        const dz = next.z - waypoint.z;
        const yaw = Math.atan2(-dx, dz) * (180 / Math.PI);

        controller.rotate(yaw, 0);
    }

    console.log("Patrol complete!");
}

// Example 5: List and cleanup
async function example5_listAndCleanup() {
    console.log("\n--- Example 5: List and Cleanup ---");

    const puppets = Puppeteer.list();
    console.log(`Active puppets: ${puppets.length}`);

    if (puppets.length === 0) {
        console.log("No puppets to clean up");
        return;
    }

    puppets.forEach(p => {
        console.log(`- ${p.name} (${p.id}) at (${p.position.x}, ${p.position.y}, ${p.position.z})`);
    });

    console.log("Removing all puppets...");
    Puppeteer.removeAll();

    console.log(`Active puppets after cleanup: ${Puppeteer.list().length}`);
}

// Example 6: Integration with World API
async function example6_worldIntegration() {
    console.log("\n--- Example 6: World Integration ---");

    // Find a safe spawn location
    const basePos = { x: 100, y: 64, z: 100 };

    // Check if the block below is solid
    const blockBelow = World.getBlock({ x: basePos.x, y: basePos.y - 1, z: basePos.z });
    console.log(`Block below spawn: ${blockBelow.id}`);

    if (blockBelow.id === "minecraft:air") {
        console.log("Unsafe spawn location - finding ground...");

        // Find ground (this is simplified)
        let y = basePos.y;
        while (y > 0) {
            const block = World.getBlock({ x: basePos.x, y: y - 1, z: basePos.z });
            if (block.id !== "minecraft:air") {
                basePos.y = y;
                console.log(`Found ground at y=${y}`);
                break;
            }
            y--;
        }
    }

    // Spawn puppet at safe location
    const puppet = await Puppeteer.spawn(basePos, { name: "Scout" });
    console.log(`Spawned ${puppet.name} at safe location (${puppet.position.x}, ${puppet.position.y}, ${puppet.position.z})`);

    return puppet;
}

// Run all examples
async function runDemo() {
    try {
        // Example 1: Spawn single puppet
        const singlePuppet = await example1_spawnSingle();

        // Example 2: Control the puppet
        await example2_controlPuppet(singlePuppet);

        // Example 3: Spawn formation
        const formation = await example3_spawnFormation();

        // Example 4: Patrol route
        await example4_patrolRoute(formation[0]);

        // Example 6: World integration
        await example6_worldIntegration();

        // Example 5: List and cleanup (do this last)
        await example5_listAndCleanup();

        console.log("\n=== Demo Complete ===");

        if (Puppeteer.debugMode) {
            console.log("(Debug mode was enabled during demo)");
        }

    } catch (error) {
        console.error("Demo failed:", error);
    }
}

// Run the demo
await runDemo();
