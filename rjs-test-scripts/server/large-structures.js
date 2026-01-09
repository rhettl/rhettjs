import '../modules/array-polyfill.js'
import { LargeStructureNbt as Structure } from "rhettjs/structure";
import Commands from "rhettjs/commands";
import Store from "rhettjs/store";


const cmd = Commands.register('large-structure')
  .description('Large structure management commands');

cmd.subcommand('save')
  .argument('name', 'string')
  .argument('size', 'int', 48)
  .executes(async ({caller, args}) => {
    const playerName = caller.name;
    const pos = [
      getPosition(playerName, 1),
      getPosition(playerName, 2),
    ];

    if (!pos[0] || !pos[1]) {
      caller.sendMessage(`Missing position 1 or 2. Please set both positions first.`);
      return 0;
    }

    try {
      caller.sendMessage(`Capturing large structure: ${args.name} (piece size: ${args.size}x${args.size}x${args.size})...`);

      await Structure.capture(pos[0], pos[1], args.name, {
        pieceSize: { x: args.size, y: args.size, z: args.size },
      });

      caller.sendMessage(`Successfully saved large structure: ${args.name}`);
      return 1;
    } catch (err) {
      caller.sendMessage(`Failed to capture structure: ${err.message}`);
      console.error(err.stack || err);
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
  const pos1 = getPosition(caller.name, 1);
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