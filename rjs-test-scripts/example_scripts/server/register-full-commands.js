// Register FULL Brigadier commands with typed arguments and autocomplete
// Full commands provide type safety, validation, and automatic suggestions

// Example 1: Teleport command with typed player and position arguments
ServerEvents.command('rtp', builder => {
  builder.literal('rtp')
    .then(
      builder.argument('target', builder.arguments.PLAYER)
        .then(
          builder.argument('x', builder.arguments.INTEGER)
            .then(
              builder.argument('y', builder.arguments.INTEGER)
                .then(
                  builder.argument('z', builder.arguments.INTEGER)
                    .executes(ctx => {
                      // Get typed arguments - Brigadier validates them automatically
                      let target = builder.arguments.PLAYER.get(ctx, 'target');
                      let x = builder.arguments.INTEGER.get(ctx, 'x');
                      let y = builder.arguments.INTEGER.get(ctx, 'y');
                      let z = builder.arguments.INTEGER.get(ctx, 'z');

                      // Execute teleport command
                      Command.executeAsServer(`tp ${target.scoreboardName} ${x} ${y} ${z}`)
                        .then(result => {
                          console.log(`Teleported ${target.scoreboardName} to ${x}, ${y}, ${z}`);
                        });

                      return 1; // Success
                    })
                )
            )
        )
    )
    .requires(2); // Operator level 2 required
});

// Example 2: Give command with custom suggestions
ServerEvents.command('customgive', builder => {
  builder.literal('customgive').then(
    builder.argument('player', builder.arguments.PLAYER).then(
      builder.argument('item', builder.arguments.STRING).suggests((ctx, suggestionsBuilder) => {
        // Provide custom item suggestions
        return ['diamond', 'gold_ingot', 'iron_sword', 'enchanted_golden_apple'];
      }).then(
        builder.argument('amount', builder.arguments.INTEGER)
          .executes(ctx => {
            let player = builder.arguments.PLAYER.get(ctx, 'player');
            let item = builder.arguments.STRING.get(ctx, 'item');
            let amount = builder.arguments.INTEGER.get(ctx, 'amount');

            Command.executeAsServer(`give ${player.scoreboardName} ${item} ${amount}`)
              .then(result => {
                if (result.success) {
                  console.log(`Gave ${amount}x ${item} to ${player.scoreboardName}`);
                }
              });

            return 1;
          })
      )
    )
  );
});

// Example 3: Setblock command with position and block arguments
ServerEvents.command('rsetblock', builder => {
  builder.literal('rsetblock')
    .then(
      builder.argument('pos', builder.arguments.BLOCK_POS)
        .then(
          builder.argument('block', builder.arguments.BLOCK_STATE)
            .executes(ctx => {
              let pos = builder.arguments.BLOCK_POS.get(ctx, 'pos');
              let block = builder.arguments.BLOCK_STATE.get(ctx, 'block');

              // BlockPos has x(), y(), z() methods
              Command.executeAsServer(`setblock ${pos.x} ${pos.y} ${pos.z} ${block.state.block.toString()}`)
                .then(result => {
                  if (result.success) {
                    console.log(`Set block at ${pos.toShortString()} to ${block.state.block.toString()}`);
                  }
                });

              return 1;
            })
        )
    );
});

// Example 4: Multi-path command with subcommands
ServerEvents.command('admin', builder => {
  let root = builder.literal('admin').requires(2); // Base command requires op

  // /admin heal <player>
  root.then(
    builder.literal('heal')
      .then(
        builder.argument('player', builder.arguments.PLAYER)
          .executes(ctx => {
            let player = builder.arguments.PLAYER.get(ctx, 'player');
            player.setHealth(player.getMaxHealth());
            return 1;
          })
      )
  );

  // /admin kill <entities>
  root.then(
    builder.literal('kill')
      .then(
        builder.argument('entities', builder.arguments.ENTITIES)
          .executes(ctx => {
            let entities = builder.arguments.ENTITIES.get(ctx, 'entities');
            Command.executeAsServer(`kill ${entities.toString()}`)
              .then(result => {
                console.log(`Killed ${entities.size()} entities`);
              });
            return 1;
          })
      )
  );

  // /admin gamemode <mode> <player>
  root.then(
    builder.literal('gamemode')
      .then(
        builder.argument('mode', builder.arguments.WORD)
          .suggests((ctx, suggestionsBuilder) => {
            return ['survival', 'creative', 'adventure', 'spectator'];
          })
          .then(
            builder.argument('player', builder.arguments.PLAYER)
              .executes(ctx => {
                let mode = builder.arguments.WORD.get(ctx, 'mode');
                let player = builder.arguments.PLAYER.get(ctx, 'player');

                Command.executeAsServer(`gamemode ${mode} ${player.scoreboardName}`)
                  .then(result => {
                    console.log(`Set ${player.scoreboardName} to ${mode} mode`);
                  });

                return 1;
              })
          )
      )
  );
});

// Example 5: Simple boolean flag command
ServerEvents.command('debug', builder => {
  builder.literal('debug')
    .then(
      builder.argument('enabled', builder.arguments.BOOLEAN)
        .executes(ctx => {
          let enabled = builder.arguments.BOOLEAN.get(ctx, 'enabled');
          console.log(`Debug mode: ${enabled ? 'ENABLED' : 'DISABLED'}`);
          return 1;
        })
    );
});

console.log('[RhettJS] Registered full commands: /rtp, /customgive, /rsetblock, /admin, /debug');
