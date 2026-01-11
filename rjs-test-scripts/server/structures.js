import Commands from "rhettjs/commands";
import { StructureNbt as Structure } from "rhettjs/structure";
import Store from 'rhettjs/store'


const cmd = Commands.register('structure')
  .description('Structure management commands');

cmd.subcommand('save')
  .argument('name', 'string')
  .argument('size', 'int', 48)
  .executes(async ({caller, args}) => {
    console.log('[structure save] Command invoked:', { player: caller.name, name: args.name, size: args.size });

    const playerName = caller.name;

    let pos;
    try {
      pos = [
        await getPosition(playerName, 1),
        await getPosition(playerName, 2),
      ];
      console.log('[structure save] Positions retrieved:', { pos1: pos[0], pos2: pos[1] });
    } catch (err) {
      console.error('[structure save] Error getting positions:', err);
      caller.sendError(`Failed to retrieve positions: ${err.message}`);
      return 0;
    }

    // Validate positions
    if (!pos[0]) {
      const msg = `Position 1 not set. Please set position 1 first.`;
      console.error('[structure save]', msg);
      caller.sendError(msg);
      return 0;
    }
    if (!pos[1]) {
      const msg = `Position 2 not set. Please set position 2 first.`;
      console.error('[structure save]', msg);
      caller.sendError(msg);
      return 0;
    }

    console.log('[structure save] Starting capture...');

    try {
      await Structure.capture(pos[0], pos[1], args.name, {
        pieceSize: {x: args.size, y: args.size, z: args.size},
      });

      console.log('[structure save] Capture successful');
      caller.sendSuccess(`Structure '${args.name}' captured successfully`)
      return 1;
    } catch (err) {
      console.error('[structure save] Capture failed:', err);
      console.error('[structure save] Error stack:', err.stack);
      caller.sendError(`Failed to capture structure: ${err.message || err}`);
      return 0;
    }

  });

cmd.subcommand('list')
  .argument('namespace', 'string', null)
  .executes(async ({caller, args}) => {
    try {
      let structures = await Structure.list(args.namespace);
      caller.sendMessage(`Available Large structures${args.namespace ? ` for namespace "${args.namespace}"` : ''}:`);
      structures.forEach(structure => {
        caller.sendMessage(`  - ${structure}`);
      })
    } catch (err) {
      caller.sendMessage('Failed to parse structures');
      console.error(err.stack);
    }
  });

cmd.subcommand('place')
  .argument('name', 'string')
  .argument('rotation', 'int', 0)
  .argument('mode', 'string', 'replace') // keep_air, overlay
  .executes(async ({caller, args}) => {
    const res = await doPlace({ caller, args, centered: false });
    return res ? 1 : 0;
  })

cmd.subcommand('place-centered')
  .argument('name', 'string')
  .argument('rotation', 'int', 0)
  .argument('mode', 'string', 'replace') // keep_air, overlay
  .executes(async ({caller, args}) => {
    const res = await doPlace({caller, args, centered: true});
    return res ? 1 : 0;
  })




async function doPlace ({caller, args, centered} = {}) {
  if (![0, 90, 180, -90].includes(args.rotation)) {
    caller.sendMessage(`Invalid rotation, must be one of [0, 90, 180, -90] Provided: ${args.rotation}`);
    return 0;
  }
  if (!['replace', 'keep_air', 'overlay'].includes(args.mode)) {
    caller.sendMessage(`Invalid mode, must be one of [replace, keep_air, overlay] Provided: ${args.mode}`);
    return 0;
  }


  const name = args.name.includes(':') ? args.name : 'minecraft:'+args.name;
  const namespace = name.split(':')[0];
  const timer = new Performance();
  const pos1 = await getPosition(caller.name, 1);
  if (!pos1) {
    caller.sendMessage(`No position 1 available; please set`);
    return 0;
  }

  try {
    timer.start();

    const structures = await Structure.list(namespace);
    console.log(structures, typeof structures);
    if (!structures.includes(name)) {
      caller.sendMessage(`Structure ${name} doesn't exist`);
      return 0;
    }
    timer.mark('placing')

    await Structure.place(
      pos1,
      name,
      {
        rotation: args.rotation,
        mode: args.mode,
        centered
      }
    );

    caller.sendMessage(`Structure Successfully Placed`);
  } catch (err) {
    caller.sendMessage('Failed to place structures');
    console.error(err);
  } finally {
    timer.stop();
    caller.sendMessage(`Placement took ${timer.formatElapsed()}`);
  }
}

function getPosition (playerName, posNum) {
  return Store
    .namespace('structures:positions')
    .get(`${playerName}:pos${posNum}`)
  ;
}