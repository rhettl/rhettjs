// Register custom BASIC commands
// Basic commands use simple greedy string argument parsing
// For typed arguments with autocomplete, see register-full-commands.js

// Simple hello command
ServerEvents.basicCommand('hello', event => {
    event.sendSuccess('Hello, ' + event.getSenderName() + '!');
});

// Echo command with arguments
ServerEvents.basicCommand('echo', event => {
    if (event.args.length === 0) {
        event.sendError('Usage: /echo <message>');
        return;
    }

    let message = event.args.join(' ');
    event.sendMessage('§7Echo: §f' + message);
});

// Info command showing event properties
ServerEvents.basicCommand('myinfo', event => {
    if (!event.isPlayer()) {
        event.sendError('This command can only be used by players');
        return;
    }

    event.sendMessage('§6=== Your Info ===');
    event.sendMessage('§7Name: §f' + event.getSenderName());
    event.sendMessage('§7Position: §f' + event.position.toShortString());
    event.sendMessage('§7Dimension: §f' + event.level.dimension().location());
    event.sendMessage('§7Permission Level: §f' + (event.hasPermission(4) ? '4 (Owner)' : event.hasPermission(2) ? '2 (Operator)' : '0 (Player)'));
});

// Heal command (requires op)
ServerEvents.basicCommand('heal', event => {
    if (!event.isPlayer()) {
        event.sendError('This command can only be used by players');
        return;
    }

    if (!event.hasPermission(2)) {
        event.sendError('You need operator permissions to use this command');
        return;
    }

    let player = event.player;
    player.setHealth(player.getMaxHealth());
    event.sendSuccess('You have been healed!');
});

// Test command with multiple arguments
ServerEvents.basicCommand('test', event => {
    event.sendMessage('§6=== Command Test ===');
    event.sendMessage('§7Command: §f/' + event.commandName);
    event.sendMessage('§7Arguments (' + event.args.length + '): §f' + (event.args.length > 0 ? event.args.join(', ') : '(none)'));
    event.sendMessage('§7Sender: §f' + event.getSenderName());
    event.sendMessage('§7Is Player: §f' + event.isPlayer());
    event.sendMessage('§7Is Server: §f' + event.isServer());
});

console.log('[RhettJS] Registered basic commands: /hello, /echo, /myinfo, /heal, /test');
