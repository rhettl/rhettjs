// Example: Store player positions using Store API with namespaces

const ADMIN_POSITION_STICK = 'stick[custom_name=\'["",{"text":"Position Stick","italic":false,"color":"green"}]\',enchantment_glint_override=true]'
const positions = Store.namespace('positions');

ServerEvents.command('adminbook', builder => {
  builder.literal('adminbook')
    .then(
      builder.literal('givestick')
        .executes(ctx => {
          console.log(Cmd.give(ctx.playerName, ADMIN_POSITION_STICK, 1))
          Command.execute(Cmd.give(ctx.playerName, ADMIN_POSITION_STICK, 1))
          ctx.sendSuccess('You are now in possession of the all powerful position stick!')
          return 1;
        })
    )
})


// Left-click to set pos1
ServerEvents.blockLeftClicked(event => {
  const item = event.item;
  const player = event.playerData.name;

  if (!item || item.id !== 'minecraft:stick' || item.displayName !== "Position Stick") {
    return;
  }
  event.cancel();

  positions.set(`${player}:pos1`, event.position);
  event.sendInfo(`[${player}] pos1 set at ${event.position.x}, ${event.position.y}, ${event.position.z}`);
});

// Right-click to set pos2
ServerEvents.blockRightClicked(event => {
  const item = event.item;
  const player = event.playerData.name;

  if (!item || item.id !== 'minecraft:stick' || item.displayName !== "Position Stick") {
    return;
  }

  positions.set(`${player}:pos2`, event.position);
  event.sendInfo(`[${player}] pos2 set at ${event.position.x}, ${event.position.y}, ${event.position.z}`);
});

ServerEvents.command('largeStructure', builder => {
  builder.literal('largeStructure').then(
    builder.literal('save').then(
      builder.argument('name', builder.arguments.STRING)
        .executes(ctx => {
          let name = builder.arguments.STRING.get(ctx, 'name');
          let player = ctx.playerName;
          let world = ctx.dimension;

          console.log('[DEBUG] world variable type:', typeof world, 'value:', world);

          console.debug(`fetching positions for ${player}`);
          let pos = [
            posToArray(positions.get(`${player}:pos1`)),
            posToArray(positions.get(`${player}:pos2`)),
          ];
          if (!pos[0] || !pos[1]) {
            ctx.sendError(`[Large Structure] Either [pos1,pos2] missing: [(${pos[0]
              .join(',')}), (${pos[1].join(',')})]`);
            return 0;
          }
          ctx.sendMessage(`[Large Structure] using positions [pos1, pos2]: [(${pos[0]
            .join(',')}), (${pos[1].join(',')})]`);

          console.log('[DEBUG] About to call task()...');
          const promise = task(handleRead, name, pos[0], pos[1], {world: world, analyze: true});
          console.log('[DEBUG] task() returned promise:', promise);

          const thenPromise = promise.then(res => {
            console.log('[DEBUG] .then() callback executing!');
            ctx.sendMessage('Success!!');
            console.log(res, res.metadata);
          });
          console.log('[DEBUG] .then() attached, returned:', thenPromise);

          thenPromise.catch(err => {
            console.log('[DEBUG] .catch() callback executing!');
            throw err;
          });
          console.log('[DEBUG] .catch() attached');

          return 1;
        })
    )
  )
  // .then(
  //   builder.literal('load')
  //     .executes(ctx => {
  //
  //
  //       return 1;
  //     })
  // )
})



function posToArray (pos) {
  if (pos) {
    return [
      pos.x,
      pos.y,
      pos.z
    ]
  }
  return [];
}

function expandSize (sizes) {
  console.log('sizes received', sizes, typeof sizes);
  if (!sizes) {
    throw new Error(`Sizes not valid, must be int or int[]: ${sizes}`);
  }
  if (!Array.isArray(sizes)) {
    return [sizes, sizes, sizes];
  }
  if (sizes.length === 3) {
    return sizes;
  }
  if (sizes.length === 1) {
    return [sizes[0], sizes[0], sizes[0]];
  }
  if (sizes.length === 2) {
    return [sizes[0], sizes[0], sizes[1]];
  }
}

/**
 * Handle read subcommand - capture from world
 */
function handleRead (name, pos1, pos2, options) {
  options = Object.assign({
    world: 'overworld',
    size: 48,
    namespace: 'minecraft',
    analyze: false,
  }, options || {});
  console.log('options', options)

  options.size = expandSize(options.size);

  // Parse required arguments
  const x1 = pos1[0];
  const y1 = pos1[1];
  const z1 = pos1[2];
  const x2 = pos2[0];
  const y2 = pos2[1];
  const z2 = pos2[2];

  // Validate required args
  if (!name || isNaN(x1) || isNaN(y1) || isNaN(z1) || isNaN(x2) || isNaN(y2) || isNaN(z2)) {
    throw new Error(`missing variables [name, pos1, pos2] [${name}, (${pos1}), (${pos2})]`);
  }

  // Parse optional flags
  const world = options.world;
  const size = options.size;
  const namespace = options.namespace;

  // Calculate region info
  const sizeX = Math.abs(x2 - x1) + 1;
  const sizeY = Math.abs(y2 - y1) + 1;
  const sizeZ = Math.abs(z2 - z1) + 1;
  const gridX = Math.ceil(sizeX / size[0]);
  const gridY = Math.ceil(sizeY / size[1]);
  const gridZ = Math.ceil(sizeZ / size[2]);
  const totalPieces = gridX * gridY * gridZ;

  console.log(sizeX, sizeY, sizeZ)
  console.log(gridX, gridY, gridZ)
  console.log(totalPieces)

  // Start timing
  const perf = new Performance();
  perf.start();

  try {
    // Capture the large structure
    const metadata = World.grabLarge(world, x1, y1, z1, x2, y2, z2, name, size, namespace);

    // Stop timing
    const elapsed = perf.stop();

    return {
      success: true,
      elapsed: elapsed,
      metadata: metadata
    }

  } catch (error) {
    throw error;
  }
}