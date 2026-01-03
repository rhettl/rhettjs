// Test Commands API Platform Integration
// This script tests the full Commands API with Brigadier integration

import Commands from 'Commands';

console.log('=== Commands API Platform Integration Test ===');
console.log('');

// Test 1: Simple command (no arguments)
console.log('Test 1: Register simple command');
try {
    Commands.register('testping')
        .description('Test ping command')
        .executes(({ caller }) => {
            console.log('✓ testping executed');
            console.log('  Caller name:', caller.name);
            console.log('  Caller isPlayer:', caller.isPlayer);
            caller.sendMessage('Pong!');
        });
    console.log('✓ Simple command registered');
} catch (e) {
    console.error('✗ Failed to register simple command:', e.message);
}
console.log('');

// Test 2: Command with string argument
console.log('Test 2: Register command with string argument');
try {
    Commands.register('testsay')
        .description('Say a message')
        .argument('message', 'string')
        .executes(({ caller, args }) => {
            console.log('✓ testsay executed');
            console.log('  Args:', args);
            console.log('  Message:', args.message);
            caller.sendMessage(`You said: ${args.message}`);
        });
    console.log('✓ String argument command registered');
} catch (e) {
    console.error('✗ Failed to register string command:', e.message);
}
console.log('');

// Test 3: Command with int arguments
console.log('Test 3: Register command with int arguments');
try {
    Commands.register('testadd')
        .description('Add two numbers')
        .argument('a', 'int')
        .argument('b', 'int')
        .executes(({ caller, args }) => {
            console.log('✓ testadd executed');
            console.log('  a:', args.a, 'type:', typeof args.a);
            console.log('  b:', args.b, 'type:', typeof args.b);
            const result = args.a + args.b;
            caller.sendMessage(`${args.a} + ${args.b} = ${result}`);
        });
    console.log('✓ Int arguments command registered');
} catch (e) {
    console.error('✗ Failed to register int command:', e.message);
}
console.log('');

// Test 4: Command with player argument
console.log('Test 4: Register command with player argument');
try {
    Commands.register('testheal')
        .description('Heal a player')
        .argument('target', 'player')
        .executes(({ caller, args }) => {
            console.log('✓ testheal executed');
            console.log('  Target:', args.target);
            if (!args.target) {
              caller.sendMessage('No player found.');
              return 0;
            }
            console.log('  Target name:', args.target.name);
            console.log('  Target health:', args.target.health);
            console.log('  Target maxHealth:', args.target.maxHealth);

            // Heal the target
            args.target.setHealth(args.target.maxHealth);
            args.target.sendMessage('You have been healed!');
            caller.sendMessage(`Healed ${args.target.name}`);
        });
    console.log('✓ Player argument command registered');
} catch (e) {
    console.error('✗ Failed to register player command:', e.message);
}
console.log('');

// Test 5: Command with multiple argument types
console.log('Test 5: Register command with mixed arguments');
try {
    Commands.register('testgive')
        .description('Give an item to a player')
        .argument('target', 'player')
        .argument('item', 'item')
        .argument('count', 'int')
        .executes(({ caller, args }) => {
            console.log('✓ testgive executed');
            console.log('  Target:', args.target.name);
            console.log('  Item:', args.item);
            console.log('  Count:', args.count);

            // Give the item
            args.target.giveItem(args.item, args.count);
            caller.sendMessage(`Gave ${args.count}x ${args.item} to ${args.target.name}`);
        });
    console.log('✓ Mixed arguments command registered');
} catch (e) {
    console.error('✗ Failed to register mixed command:', e.message);
}
console.log('');

// Test 6: Command with permission check (string)
console.log('Test 6: Register command with permission check');
try {
    Commands.register('testadmin')
        .description('Admin only command')
        .permission('admin.test')
        .executes(({ caller }) => {
            console.log('✓ testadmin executed');
            caller.sendMessage('You have admin permissions!');
        });
    console.log('✓ Permission command registered');
} catch (e) {
    console.error('✗ Failed to register permission command:', e.message);
}
console.log('');

// Test 7: Command with permission function
console.log('Test 7: Register command with permission function');
try {
    Commands.register('testop')
        .description('Op only command')
        .permission((caller) => {
            console.log('  Permission check for:', caller.name);
            return caller.isOp || false;
        })
        .executes(({ caller }) => {
            console.log('✓ testop executed');
            caller.sendMessage('You are an operator!');
        });
    console.log('✓ Permission function command registered');
} catch (e) {
    console.error('✗ Failed to register permission function command:', e.message);
}
console.log('');

// Test 8: Async command
console.log('Test 8: Register async command');
try {
    Commands.register('testwait')
        .description('Test async command with wait')
        .executes(async ({ caller }) => {
            console.log('✓ testwait started');
            caller.sendMessage('Waiting 1 second...');
            await wait(20); // Wait 1 second
            caller.sendMessage('Done!');
            console.log('✓ testwait completed');
        });
    console.log('✓ Async command registered');
} catch (e) {
    console.error('✗ Failed to register async command:', e.message);
}
console.log('');

// Test 9: Command with block argument
console.log('Test 9: Register command with block argument');
try {
    Commands.register('testblock')
        .description('Test block argument')
        .argument('block', 'block')
        .executes(({ caller, args }) => {
            console.log('✓ testblock executed');
            console.log('  Block:', args.block);
            caller.sendMessage(`Block: ${args.block}`);
        });
    console.log('✓ Block argument command registered');
} catch (e) {
    console.error('✗ Failed to register block command:', e.message);
}
console.log('');

// Test 10: Command with entity argument
console.log('Test 10: Register command with entity argument');
try {
    Commands.register('testentity')
        .description('Test entity argument')
        .argument('entity', 'entity')
        .executes(({ caller, args }) => {
            console.log('✓ testentity executed');
            console.log('  Entity:', args.entity);
            caller.sendMessage(`Entity: ${args.entity}`);
        });
    console.log('✓ Entity argument command registered');
} catch (e) {
    console.error('✗ Failed to register entity command:', e.message);
}
console.log('');

// Test 11: Unregister command
console.log('Test 11: Unregister a command');
try {
    Commands.register('testtemp')
        .description('Temporary command')
        .executes(() => {
            console.log('This should not be called');
        });

    Commands.unregister('testtemp');
    console.log('✓ Command unregistered');
} catch (e) {
    console.error('✗ Failed to unregister command:', e.message);
}
console.log('');

// Test 12: Caller object properties
console.log('Test 12: Test caller object structure');
try {
    Commands.register('testcaller')
        .description('Test caller object')
        .executes(({ caller }) => {
            console.log('✓ testcaller executed');
            console.log('  Caller structure:');
            console.log('    name:', caller.name);
            console.log('    isPlayer:', caller.isPlayer);

            if (caller.isPlayer) {
                console.log('    Player properties:');
                console.log('      uuid:', caller.uuid);
                console.log('      health:', caller.health);
                console.log('      maxHealth:', caller.maxHealth);
                console.log('      position:', caller.position);
                console.log('      gameMode:', caller.gameMode);
            }

            caller.sendMessage('Caller test complete');
        });
    console.log('✓ Caller test command registered');
} catch (e) {
    console.error('✗ Failed to register caller test:', e.message);
}
console.log('');

// Test 13: Command with float argument
console.log('Test 13: Register command with float argument');
try {
    Commands.register('testfloat')
        .description('Test float argument')
        .argument('value', 'float')
        .executes(({ caller, args }) => {
            console.log('✓ testfloat executed');
            console.log('  Value:', args.value, 'type:', typeof args.value);
            caller.sendMessage(`Float value: ${args.value}`);
        });
    console.log('✓ Float argument command registered');
} catch (e) {
    console.error('✗ Failed to register float command:', e.message);
}
console.log('');

console.log('=== Commands API Registration Complete ===');
console.log('');
console.log('Test the following commands in-game:');
console.log('  /testping');
console.log('  /testsay <message>');
console.log('  /testadd <a> <b>');
console.log('  /testheal <player>');
console.log('  /testgive <player> <item> <count>');
console.log('  /testadmin (requires admin.test permission)');
console.log('  /testop (requires op)');
console.log('  /testwait');
console.log('  /testblock <block>');
console.log('  /testentity <entity>');
console.log('  /testcaller');
console.log('  /testfloat <value>');
console.log('');
console.log('Note: Commands will be registered after this script completes.');
