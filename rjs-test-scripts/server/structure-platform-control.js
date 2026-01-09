import Commands from "rhettjs/commands";
import {read, platform} from '../modules/structure-test-control.js'


const cmd = Commands.register('structure-platform-command')
  .description('Structure Testing platform controller');

cmd.subcommand('reset')
  .argument('bookPos', 'xyz-position')
  .executes(async ({ caller, args }) => {
    // let bookPos = {
    //   x: args.bookX,
    //   y: args.bookY,
    //   z: args.bookz,
    //   dimension: caller?.position?.dimension ?? 'rhettjs:structure-test',
    // };
    const dimension = caller.isPlayer ? caller.position.dimension : caller.dimension;
    console.log({caller, args})
    let bookPos = {
      ...args.bookPos,
      dimension: dimension,
    }

    console.log('pos', bookPos);

    const book = await read.lectern(bookPos);
    console.log('page', book.currentPage);
    if (!book.hasBook && !book.currentPage) {
      console.error(`missing book or page: `, book, bookPos);
      throw new Error(`No book or page found at ${bookPos}`);
    }
    const structure = findPlatformInPage(book.currentPage);

    await platform.clear();
    await platform.placePlatform(structure);

    caller.sendMessage(`${structure} placed and centered at ${posToString(platform.center)}`)
    return 1;
  })

// /worldgen-structure place <name> <x> <z> [seed] [surface] [rotation]
// Note: String args with special chars (like :) must be quoted
cmd.subcommand('place')
  .argument('sign', 'xyz-position')
  .executes(async ({caller, args}) => {
    let name = '';
    let dimension = caller.isPlayer ? caller.position.dimension : caller.dimension;
    const signPos = {
      ...args.sign,
      dimension,
    };

    const sign = await read.sign(signPos);
    if (!sign?.hasSign) {
      throw new Error(`missing sign: ${sign}`);
    }

    name = sign.front?.combine().replace(/\s/g, '');
    await platform.placeVillage(name, {});
    console.log('last placement', platform.lastPlacement)

    caller.sendMessage(`Placed ${name} at ${posToString(platform.center)}`);
    return 1;
  });



function posToString (pos) {
  return `(${pos.x}, ${pos.y}, ${pos.z})`;
}
function findPlatformInPage(page) {
  const headerIndex = page.rows.findIndex(i => /^#+ ?Structure/.test(i));
  if (headerIndex === -1) {
    console.error('Unable to find structure on page:', page);
    throw new Error(`Unable to find structure on page`);
  }

  const structure  = page.rows
    .slice(headerIndex+1)
    .join('')
    .replace(/\n/g, '')
    .replace(/ +/g, '')
    .trim();

  const match = structure.match(/^([a-z][a-z0-9-_]+):(.*)$/);
  if (!match) {
    console.error('Unable to find structure on page:', page);
    console.error(`structrue found: ${structure}`);
    throw new Error(`cannot assemble structure resource name from page`)
  }

  return `${match[1]}:${match[2]}`;
}
