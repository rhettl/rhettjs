/**
 * Test script for Command API
 * Tests command execution with promises
 *
 * Usage: /rjs run test-command
 */

// Test 1: Simple command execution
Caller.sendMessage("§6Testing Command.executeAsServer()...");

Command.executeAsServer("time query daytime")
  .then(result => {
    Caller.sendMessage("§aCommand succeeded!");
    Caller.sendMessage(`  Success: ${result.success}`);
    Caller.sendMessage(`  Result count: ${result.resultCount}`);
    Caller.sendMessage(`  Error: ${result.error || 'none'}`);

    // Test 2: Chain another command
    return Command.executeAsServer("weather query");
  })
  .then(result => {
    Caller.sendMessage("§aWeather query succeeded!");
    Caller.sendMessage(`  Result count: ${result.resultCount}`);

    // Test 3: Execute as player (caller)
    return Command.execute("give @s diamond 1");
  })
  .then(result => {
    Caller.sendMessage("§aGive command succeeded!");
    Caller.sendMessage(`  Result count: ${result.resultCount}`);

    // Test 4: Try command suggestions
    return Command.suggest("time ");
  })
  .then(suggestions => {
    Caller.sendMessage("§aSuggestions for 'time ':");
    suggestions.forEach(s => Caller.sendMessage(`  - ${s}`));

    Caller.sendMessage("§a=== All Command API tests passed! ===");
  })
  .catch(error => {
    Caller.sendMessage("§c=== Command API test failed! ===");
    Caller.sendMessage(`§cError: ${error}`);
  });

Caller.sendMessage("§7Command tests started (async)...");
