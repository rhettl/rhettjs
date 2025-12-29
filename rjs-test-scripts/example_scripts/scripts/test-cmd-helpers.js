/**
 * Test script for Command Helpers
 * Demonstrates the command helper library
 *
 * Usage: /rjs run test-cmd-helpers
 */

Caller.sendMessage("§6=== Testing Command Helpers ===");

// Test 1: Simple teleport to coords
const tpCmd1 = Cmd.tp({ to: [100, 64, 200] });
Caller.sendMessage(`§7TP to coords: §f${tpCmd1}`);

// Test 2: Teleport all players to coords
const tpCmd2 = Cmd.tp({ from: "@a", to: { x: 0, y: 100, z: 0 } });
Caller.sendMessage(`§7TP all players: §f${tpCmd2}`);

// Test 3: Entity selector builder
const cowSelector = Cmd.selector("e", { type: "cow", limit: 1, distance: "..10" });
Caller.sendMessage(`§7Cow selector: §f${cowSelector}`);

// Test 4: Quick selectors
const nearPlayers = Cmd.allPlayers({ distance: "..20" });
Caller.sendMessage(`§7Near players: §f${nearPlayers}`);

// Test 5: Give command
const giveCmd1 = Cmd.give("diamond");
const giveCmd2 = Cmd.give("stone", 64);
const giveCmd3 = Cmd.give("@a", "golden_apple", 5);
Caller.sendMessage(`§7Give diamond: §f${giveCmd1}`);
Caller.sendMessage(`§7Give 64 stone: §f${giveCmd2}`);
Caller.sendMessage(`§7Give all players: §f${giveCmd3}`);

// Test 6: Fill command
const fillCmd = Cmd.fill([0, 64, 0], [10, 64, 10], "stone");
Caller.sendMessage(`§7Fill area: §f${fillCmd}`);

// Test 7: Execute builder
const executeCmd = Cmd.execute()
  .as("@a")
  .at("@s")
  .if({ entity: "@e[type=cow,distance=..5]" })
  .run("say Found a cow nearby!")
  .build();
Caller.sendMessage(`§7Execute command: §f${executeCmd}`);

// Test 8: Actually execute a command using helpers
Caller.sendMessage("§6Executing test commands...");

Command.executeAsServer(Cmd.give("@s", "diamond", 1))
  .then(result => {
    Caller.sendMessage(`§aGave diamond! Result count: ${result.resultCount}`);

    // Chain another command
    return Command.executeAsServer(
      Cmd.tp({ to: "@s" }) // TP to self (no-op but tests command)
    );
  })
  .then(result => {
    Caller.sendMessage(`§aTP command succeeded!`);

    // Test execute builder with actual execution
    return Cmd.execute()
      .as("@s")
      .at("@s")
      .run("say Command helpers working!")
      .executeAsServer();
  })
  .then(result => {
    Caller.sendMessage("§a=== All Command Helper tests passed! ===");
  })
  .catch(error => {
    Caller.sendMessage(`§cCommand helper test failed: ${error}`);
  });
