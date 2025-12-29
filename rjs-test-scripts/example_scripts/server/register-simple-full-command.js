// Simple test of full Brigadier command registration
// Uses imperative API compatible with Rhino 1.8.1

// Example 1: Simple command with one typed argument
// Use GREEDY_STRING to accept resource locations like "minecraft:diamond"
ServerEvents.command('givetest', function(cmd) {
    cmd.addArgument('item', cmd.types.GREEDY_STRING);
    cmd.setExecutor(function(ctx) {
        let item = cmd.types.GREEDY_STRING.get(ctx, 'item');

        // Use Runtime.inspect() to explore the entity (uncomment to debug)
        let entityInfo = Runtime.inspect(ctx.source.entity);
        console.log('Entity info:', JSON.stringify(entityInfo, null, 2));

        // Get the player who executed the command
        let playerName = ctx.source.entity ? ctx.source.entity.scoreboardName : '@s';
        console.log('Giving ' + item + ' to player: ' + playerName);

        Command.executeAsServer(Cmd.give(playerName, item, 1))
            .then(function(result) {
                console.log('Gave ' + item + ' to player');
            })
          .catch(err => console.error(err.stack))
        ;

        return 1;
    });
});

// Example 2: Command with player argument
ServerEvents.command('healplayer', function(cmd) {
    cmd.addArgument('target', cmd.types.PLAYER);
    cmd.setExecutor(function(ctx) {
        let player = cmd.types.PLAYER.get(ctx, 'target');

        player.setHealth(20)

        return 1;
    });
});

// Example 3: Command with integer argument
ServerEvents.command('countdown', function(cmd) {
    cmd.addArgument('seconds', cmd.types.INTEGER);
    cmd.setExecutor(function(ctx) {
        let seconds = cmd.types.INTEGER.get(ctx, 'seconds');
        console.log('Countdown from: ' + seconds);
        return 1;
    });
});

// Example 4: Command with multiple arguments
ServerEvents.command('tp2', function(cmd) {
    cmd.addArgument('player', cmd.types.PLAYER);
    cmd.addArgument('x', cmd.types.INTEGER);
    cmd.addArgument('y', cmd.types.INTEGER);
    cmd.addArgument('z', cmd.types.INTEGER);
    cmd.requiresPermission(2);
    cmd.setExecutor(function(ctx) {
        let player = cmd.types.PLAYER.get(ctx, 'player');
        let x = cmd.types.INTEGER.get(ctx, 'x');
        let y = cmd.types.INTEGER.get(ctx, 'y');
        let z = cmd.types.INTEGER.get(ctx, 'z');

        Command.executeAsServer(Cmd.tp({
            from: player.scoreboardName,
            to: [x, y, z]
        })).then(function(result) {
            console.log('Teleported ' + player.scoreboardName + ' to ' + x + ', ' + y + ', ' + z);
        });

        return 1;
    });
});

console.log('[RhettJS] Registered full commands: /givetest, /healplayer, /countdown, /tp2');
