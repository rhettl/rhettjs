import {executeAsServer, give} from "../modules/command-helpers.js";
import Commands from "rhettjs/commands";
import Server from "rhettjs/server";
import Store from "rhettjs/store";

// Create namespace for position storage
const positions = Store.namespace('structures:positions');

const cmd = Commands.register('positions')
  .description('Positional helper for structure management tools')

cmd.subcommand('wand')
  .executes(async ({caller}) => {
    const playerName = caller.name;
    console.log(`Player name: ${playerName}`);
    await executeAsServer('/' + give(
      `stick[item_name='{"text":"Large Structure Wand","italic":false}',enchantment_glint_override=true]`,
      1,
      playerName
    ));
  });

cmd.subcommand('pos1')
  .argument('pos', 'xyz-position')
  .executes(({caller, args}) => {
    setPosition(caller.name, 1, {
      ...args.pos,
      dimension: caller.isPlayer ? caller.position.dimension : caller.dimension
    });
    caller.sendMessage(`§aPos1 set at ${args.pos.x}, ${args.pos.y}, ${args.pos.z}`);
  });

cmd.subcommand('pos2')
  .argument('pos', 'xyz-position')
  .executes(({caller, args}) => {
    setPosition(caller.name, 2, {
      ...args.pos,
      dimension: caller.isPlayer ? caller.position.dimension : caller.dimension
    });
    caller.sendMessage(`§aPos2 set at ${args.pos.x}, ${args.pos.y}, ${args.pos.z}`);
  });

cmd.subcommand('check-positions')
  .executes(({caller, args}) => {
    const pos1 = getPosition(caller.name, 1);
    const pos2 = getPosition(caller.name, 2);

    if (!pos1) {
      caller.sendMessage('No position 1 available');
      return 0;
    }
    if (!pos2) {
      caller.sendMessage('No position 2 available');
      return 0;
    }

    const delta = {
      x: Math.abs(pos1.x - pos2.x) + 1,
      y: Math.abs(pos1.y - pos2.y) + 1,
      z: Math.abs(pos1.z - pos2.z) + 1,
    };

    caller.sendMessage(`Position 1: ${pos1.x}, ${pos1.y}, ${pos1.z}`);
    caller.sendMessage(`Position 2: ${pos2.x}, ${pos2.y}, ${pos2.z}`);
    caller.sendMessage(`Difference: ${delta.x}, ${delta.y}, ${delta.z}`);
    caller.sendMessage(`Volume: ${delta.x * delta.y * delta.z} m^3`);

  });

cmd.subcommand('clear-positions')
  .executes(({caller}) => {
    clearPositions(caller.name);
    caller.sendMessage('Positions cleared');
  });


// Left-click to set pos1 (position already includes dimension)
Server.on(Server.eventTypes.BLOCK_LEFT_CLICK, (event) => {
  const playerName = event.player.name;
  const item = event.item;
  const isWand = /Large Structure Wand/i.test(item?.displayName ?? '');

  if (!isWand) {
    return 1;
  }

  event.cancel();
  setPosition(playerName, 1, event.position);
  event.player.sendMessage(`§aPos1 set at ${event.position.x}, ${event.position.y}, ${event.position.z}`);
});

// Right-click to set pos2 (position already includes dimension)
Server.on(Server.eventTypes.BLOCK_RIGHT_CLICK, (event) => {
  const playerName = event.player.name;
  const item = event.item;
  const isWand = /Large Structure Wand/i.test(item?.displayName ?? '');
  if (!isWand) {
    return 1;
  }
  event.cancel();
  setPosition(playerName, 2, event.position);
  event.player.sendMessage(`§aPos2 set at ${event.position.x}, ${event.position.y}, ${event.position.z}`);
});


// Helper functions
function setPosition (playerName, posNum, position) {
  positions.set(`${playerName}:pos${posNum}`, position);
}

function getPosition (playerName, posNum) {
  return positions.get(`${playerName}:pos${posNum}`);
}

function clearPositions (playerName) {
  positions.delete(`${playerName}:pos1`);
  positions.delete(`${playerName}:pos2`);
}