/**
 * Admin Tools Book - Quick access book for admin commands
 *
 * Creates a written book with clickable links to common admin commands.
 * Use /adminbook to get the book.
 */

ServerEvents.basicCommand('adminbook', function(event) {
  var book = new BookHelper('Admin Tools', 'RhettJS');

  // Page 1: Teleports
  book.addHeading('=== Teleports ===');
  book.addDivider();
  book.addLink('Structure Test Platform', '/tp @s 1000 100 1000', {
    color: 'green',
    hoverText: 'Teleport to the structure testing area'
  });
  book.addLink('Return to Spawn', '/tp @s 0 64 0', {
    color: 'aqua',
    hoverText: 'Teleport back to world spawn'
  });
  book.addLink('Go to Nether Portal', '/execute in minecraft:the_nether run tp @s 0 64 0', {
    color: 'red',
    hoverText: 'Teleport to nether hub'
  });

  // Page 2: Utilities
  book.newPage();
  book.addHeading('=== Utilities ===');
  book.addDivider();
  book.addLink('Activate Decomon UI', '/decomon ui', {
    color: 'light_purple',
    hoverText: 'Open the Decomon interface'
  });
  book.addLink('Reload Scripts', '/rjs reload', {
    color: 'yellow',
    hoverText: 'Hot reload all RhettJS scripts'
  });
  book.addLink('Clear Inventory', '/clear @s', {
    color: 'red',
    hoverText: 'Clear your inventory'
  });

  // Page 3: Debugging
  book.newPage();
  book.addHeading('=== Debug ===');
  book.addDivider();
  book.addLink('Show Coordinates', '/rjs run util coords', {
    hoverText: 'Display current coordinates'
  });
  book.addLink('List Entities', '/execute at @s run kill @e[type=!player,distance=..50]', {
    color: 'red',
    hoverText: 'Kill entities near you (50 block radius)'
  });
  book.addText('');
  book.addText('More tools coming soon...', { color: 'gray', italic: true });

  // Give the book to the command sender
  var playerName = event.getSenderName();

  book.give(playerName)
    .then(function() {
      event.sendSuccess('Given admin book to ' + playerName);
    })
    .catch(function(err) {
      event.sendError('Failed to give book: ' + err);
      console.error(err.stack);
    });
});

console.log('[RhettJS] Registered /adminbook command');
