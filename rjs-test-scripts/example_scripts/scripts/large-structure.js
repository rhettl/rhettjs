/**
 * Large Structure Manager
 *
 * Capture and place large structures that exceed vanilla structure block limits.
 * Automatically splits large regions into grid pieces for storage and reassembly.
 *
 * Usage:
 *   /rjs run large-structure                       - Show help
 *   /rjs run large-structure read <name> ...       - Capture large structure from world
 *   /rjs run large-structure write <ns:name> ...   - Place large structure into world
 *   /rjs run large-structure list [namespace]      - List available structures
 *   /rjs run large-structure info <ns:name>        - Show structure metadata
 *
 * Examples:
 *   /rjs run large-structure read castle -100 64 -100 100 100 100
 *   /rjs run large-structure write minecraft:castle 1000 64 2000
 *   /rjs run large-structure write minecraft:castle 0 64 0 --rotation 90
 *   /rjs run large-structure write minecraft:castle 500 64 500 --centered
 *   /rjs run large-structure list minecraft
 *   /rjs run large-structure info minecraft:castle
 *
 * Dependencies:
 *   - Commander (globals/12-commander.js) - Arg parsing
 *   - MessageBuffer (globals/11-message-buffer.js) - Message buffering
 *   - Performance (globals/15-performance.js) - Timing measurements
 *   - World API - Reading/writing world blocks, rotation support
 *   - Structure API - Block replacement and analysis
 */

// ============================================================================
// Configuration
// ============================================================================

const CONFIG = {
  DEFAULT_PIECE_SIZE: [48, 48],
  DEFAULT_WORLD: 'overworld',
  DEBUG: Runtime.env.IS_DEBUG
};

// ============================================================================
// Main Entry Point
// ============================================================================

function main() {
  console.debug('═══════════════════════════════════════');
  console.debug('LARGE STRUCTURE MANAGER');
  console.debug(`RhettJS ${Runtime.env.RJS_VERSION}`);
  console.debug('═══════════════════════════════════════');

  // Parse arguments
  const cmd = new Commander();
  const subcommand = cmd.get(0);

  if (!subcommand || subcommand === 'help') {
    return showHelp();
  } else if (subcommand === 'read') {
    return handleRead(cmd);
  } else if (subcommand === 'write') {
    return handleWrite(cmd);
  } else if (subcommand === 'list') {
    return handleList(cmd);
  } else if (subcommand === 'info') {
    return handleInfo(cmd);
  } else {
    const buffer = new MessageBuffer(Caller);
    buffer.error('Unknown command: ' + subcommand);
    buffer.log('Use "read", "write", "list", "info", or no argument for help');
    buffer.send();
    return Promise.resolve();
  }
}

// ============================================================================
// Command Handlers
// ============================================================================

/**
 * Show help message
 */
function showHelp() {
  const buffer = new MessageBuffer(Caller);

  buffer.log('═══════════════════════════════════════');
  buffer.log('  Large Structure Manager');
  buffer.log('═══════════════════════════════════════');
  buffer.log('');
  buffer.log('§6Commands:§r');
  buffer.log('');
  buffer.log('§eread§r - Capture large structure from world');
  buffer.log('  /rjs run large-structure read <name> <x1> <y1> <z1> <x2> <y2> <z2>');
  buffer.log('');
  buffer.log('§ewrite§r - Place large structure into world');
  buffer.log('  /rjs run large-structure write <namespace>:<name> <x> <y> <z>');
  buffer.log('');
  buffer.log('§elist§r - List all available large structures');
  buffer.log('  /rjs run large-structure list [namespace]');
  buffer.log('');
  buffer.log('§einfo§r - Show metadata for a large structure');
  buffer.log('  /rjs run large-structure info <namespace>:<name>');
  buffer.log('');
  buffer.log('§6Read Flags:§r');
  buffer.log('  --world <name>      World/dimension (default: overworld)');
  buffer.log('  --size <x,z>        Piece size (default: 48,48)');
  buffer.log('  --namespace <name>  Namespace for structure (default: minecraft)');
  buffer.log('  --analyze           Check for mod blocks after capture');
  buffer.log('');
  buffer.log('§6Write Flags:§r');
  buffer.log('  --world <name>      World/dimension (default: overworld)');
  buffer.log('  --rotation <deg>    Rotation: 0, 90, 180, 270 (default: 0)');
  buffer.log('  --centered          Center structure on target position');
  buffer.log('');
  buffer.log('§6Examples:§r');
  buffer.log('  /rjs run large-structure read castle -100 64 -100 100 100 100');
  buffer.log('  /rjs run large-structure write minecraft:castle 1000 64 2000');
  buffer.log('  /rjs run large-structure write minecraft:castle 0 64 0 --rotation 90');
  buffer.log('  /rjs run large-structure write minecraft:castle 0 64 0 --centered');
  buffer.log('  /rjs run large-structure list minecraft');
  buffer.log('  /rjs run large-structure info minecraft:castle');
  buffer.log('');

  buffer.send();
  return Promise.resolve();
}

/**
 * Handle read subcommand - capture from world
 */
function handleRead(cmd) {
  const buffer = new MessageBuffer(Caller);

  // Parse required arguments
  const name = cmd.get(1);
  const x1 = parseCoord(cmd.get(2));
  const y1 = parseCoord(cmd.get(3));
  const z1 = parseCoord(cmd.get(4));
  const x2 = parseCoord(cmd.get(5));
  const y2 = parseCoord(cmd.get(6));
  const z2 = parseCoord(cmd.get(7));

  // Validate required args
  if (!name || isNaN(x1) || isNaN(y1) || isNaN(z1) || isNaN(x2) || isNaN(y2) || isNaN(z2)) {
    buffer.error('Missing or invalid arguments');
    buffer.log('Usage: /rjs run large-structure read <name> <x1> <y1> <z1> <x2> <y2> <z2>');
    buffer.log('Example: /rjs run large-structure read castle -100 64 -100 100 100 100');
    buffer.send();
    return Promise.resolve();
  }

  // Parse optional flags
  const world = getWorldFlag(cmd);
  const size = getSizeFlag(cmd);
  const namespace = getNamespaceFlag(cmd);
  const analyze = cmd.hasFlag('analyze') || cmd.hasFlag('a');

  // Calculate region info
  const sizeX = Math.abs(x2 - x1) + 1;
  const sizeY = Math.abs(y2 - y1) + 1;
  const sizeZ = Math.abs(z2 - z1) + 1;
  const gridX = Math.ceil(sizeX / size[0]);
  const gridZ = Math.ceil(sizeZ / size[1]);
  const totalPieces = gridX * gridZ;

  // Show capture info
  buffer.log('═══════════════════════════════════════');
  buffer.log('  §6Large Structure Capture§r');
  buffer.log('═══════════════════════════════════════');
  buffer.log('');
  buffer.log('§eName:§r ' + name);
  buffer.log('§eRegion:§r (' + x1 + ', ' + y1 + ', ' + z1 + ') → (' + x2 + ', ' + y2 + ', ' + z2 + ')');
  buffer.log('§eSize:§r ' + sizeX + 'x' + sizeY + 'x' + sizeZ + ' blocks');
  buffer.log('§eWorld:§r ' + world);
  buffer.log('§ePiece Size:§r ' + size[0] + 'x' + size[1]);
  buffer.log('§eGrid:§r ' + gridX + 'x' + gridZ + ' = ' + totalPieces + ' pieces');
  buffer.log('§eNamespace:§r ' + namespace);

  buffer.log('');
  buffer.log('⏳ Capturing structure...');
  buffer.send();

  // Start timing
  const perf = new Performance();
  perf.start();

  try {
    // Capture the large structure
    const metadata = World.grabLarge(world, x1, y1, z1, x2, y2, z2, name, size, namespace);

    // Stop timing
    const elapsed = perf.stop();

    const buffer2 = new MessageBuffer(Caller);
    buffer2.success('✓ Capture complete!');
    buffer2.log('');
    buffer2.log('§6Results:§r');
    buffer2.log('  §eName:§r ' + metadata.name);
    buffer2.log('  §eNamespace:§r ' + metadata.namespace);
    buffer2.log('  §ePieces:§r ' + metadata.pieces + ' files');
    buffer2.log('  §eTime:§r ' + perf.formatElapsed());
    buffer2.log('  §eLocation:§r');
    buffer2.log('    ' + metadata.path);

    // Show required mods if any
    if (metadata.requires && metadata.requires.length > 0) {
      buffer2.log('  §eRequired Mods:§r');
      metadata.requires.forEach(function(mod) {
        buffer2.log('    - ' + mod);
      });
    }

    // Analyze blocks if requested
    if (analyze) {
      buffer2.log('');
      buffer2.log('🔍 Block analysis...');

      if (metadata.requires && metadata.requires.length > 0) {
        buffer2.warn('⚠ Structure contains mod blocks from:');
        metadata.requires.forEach(function(mod) {
          buffer2.log('  - ' + mod);
        });
        buffer2.log('');
        buffer2.log('§7Use Structure.listBlocks() and Structure.replaceBlocks()');
        buffer2.log('to convert mod blocks to vanilla equivalents.§r');
      } else {
        buffer2.success('✓ All blocks are vanilla (no mod blocks)');
      }
    }

    buffer2.log('');
    buffer2.success('✨ Large structure saved successfully!');
    buffer2.log('§7Access via: §r' + metadata.namespace + ':rjs-large/' + metadata.name);
    buffer2.send();

  } catch (error) {
    const buffer3 = new MessageBuffer(Caller);
    buffer3.error('❌ Error capturing structure:');
    buffer3.error(error.message || error);
    buffer3.send();
  }

  return Promise.resolve();
}

/**
 * Handle write subcommand - place into world
 */
function handleWrite(cmd) {
  const buffer = new MessageBuffer(Caller);

  // Parse structure location (namespace:name)
  const structureLoc = cmd.get(1);
  if (!structureLoc || !structureLoc.includes(':')) {
    buffer.error('Missing or invalid structure location');
    buffer.log('Usage: /rjs run large-structure write <namespace>:<name> <x> <y> <z>');
    buffer.log('Example: /rjs run large-structure write minecraft:castle 0 64 0');
    buffer.send();
    return Promise.resolve();
  }

  const parts = structureLoc.split(':');
  const namespace = parts[0];
  const name = parts[1];

  // Parse coordinates
  const x = parseCoord(cmd.get(2));
  const y = parseCoord(cmd.get(3));
  const z = parseCoord(cmd.get(4));

  if (isNaN(x) || isNaN(y) || isNaN(z)) {
    buffer.error('Missing or invalid coordinates');
    buffer.log('Usage: /rjs run large-structure write <namespace>:<name> <x> <y> <z>');
    buffer.send();
    return Promise.resolve();
  }

  // Parse optional flags
  const world = getWorldFlag(cmd);
  const rotation = getRotationFlag(cmd);
  const centered = cmd.hasFlag('centered') || cmd.hasFlag('c');

  // Get metadata first to show info
  let metadata;
  try {
    metadata = World.getLargeMetadata(namespace, name);
    if (!metadata) {
      buffer.error('Structure not found: ' + structureLoc);
      buffer.log('Use "/rjs run large-structure list" to see available structures');
      buffer.send();
      return Promise.resolve();
    }
  } catch (error) {
    buffer.error('Error loading structure metadata:');
    buffer.error(error.message || error);
    buffer.send();
    return Promise.resolve();
  }

  // Calculate center offset if requested
  let actualX = x;
  let actualY = y;
  let actualZ = z;

  if (centered) {
    const centerOffset = calculateCenterOffset(metadata, rotation);
    actualX = x + centerOffset.x;
    actualY = y; // Y is not centered (always placed from bottom)
    actualZ = z + centerOffset.z;
  }

  // Show placement info
  buffer.log('═══════════════════════════════════════');
  buffer.log('  §6Large Structure Placement§r');
  buffer.log('═══════════════════════════════════════');
  buffer.log('');
  buffer.log('§eStructure:§r ' + structureLoc);
  buffer.log('§eTarget Position:§r (' + x + ', ' + y + ', ' + z + ')');

  if (centered) {
    buffer.log('§ePlacement Mode:§r §6Centered§r');
    buffer.log('§eActual Origin:§r (' + actualX + ', ' + actualY + ', ' + actualZ + ')');
  }

  buffer.log('§eWorld:§r ' + world);
  buffer.log('§eRotation:§r ' + rotation + '°');
  buffer.log('');
  buffer.log('§ePieces:§r ' + metadata.pieceCount);

  const totalSize = metadata.totalSize || {};
  buffer.log('§eSize:§r ' + (totalSize.x || '?') + 'x' + (totalSize.y || '?') + 'x' + (totalSize.z || '?') + ' blocks');

  // Show required mods warning if any
  if (metadata.requires && metadata.requires.length > 0) {
    buffer.warn('⚠ Required mods: ' + metadata.requires.join(', '));
  }

  buffer.log('');
  buffer.log('⏳ Placing structure...');
  buffer.send();

  // Start timing
  const perf = new Performance();
  perf.start();

  try {
    // Place the structure (use actualX/Y/Z which includes center offset if --centered)
    const result = World.placeLarge(world, actualX, actualY, actualZ, namespace, name, rotation);

    // Stop timing
    const elapsed = perf.stop();

    const buffer2 = new MessageBuffer(Caller);
    buffer2.success('✓ Placement complete!');
    buffer2.log('');
    buffer2.log('§6Results:§r');
    buffer2.log('  §ePieces Placed:§r ' + result.piecesPlaced + ' / ' + metadata.pieceCount);
    buffer2.log('  §eBlocks Placed:§r ' + result.blocksPlaced.toLocaleString());
    buffer2.log('  §eRotation Applied:§r ' + result.rotation + '°');

    if (centered) {
      buffer2.log('  §ePlacement Mode:§r Centered');
    }

    buffer2.log('  §eTime:§r ' + perf.formatElapsed());
    buffer2.log('');

    const pos = result.position || {};
    buffer2.success('✨ Large structure placed successfully!');

    if (centered) {
      buffer2.log('§7Centered at: §r(' + x + ', ' + y + ', ' + z + ')');
      buffer2.log('§7Origin placed at: §r(' + (pos.x || actualX) + ', ' + (pos.y || actualY) + ', ' + (pos.z || actualZ) + ')');
    } else {
      buffer2.log('§7Origin: §r(' + (pos.x || actualX) + ', ' + (pos.y || actualY) + ', ' + (pos.z || actualZ) + ')');
    }

    buffer2.send();

  } catch (error) {
    const buffer3 = new MessageBuffer(Caller);
    buffer3.error('❌ Error placing structure:');
    buffer3.error(error.message || error);
    buffer3.send();
  }

  return Promise.resolve();
}

/**
 * Handle list subcommand - show available structures
 */
function handleList(cmd) {
  const buffer = new MessageBuffer(Caller);

  // Optional namespace filter
  const namespaceFilter = cmd.get(1);

  buffer.log('═══════════════════════════════════════');
  buffer.log('  §6Available Large Structures§r');
  buffer.log('═══════════════════════════════════════');
  buffer.log('');

  try {
    const structures = World.listLarge(namespaceFilter || null);

    if (structures.length === 0) {
      buffer.warn('No large structures found');
      if (namespaceFilter) {
        buffer.log('Try without namespace filter or check: /rjs run large-structure list');
      } else {
        buffer.log('Use "read" command to capture structures first');
      }
    } else {
      // Group by namespace
      const byNamespace = {};
      structures.forEach(function(s) {
        const ns = s.namespace || 'unknown';
        if (!byNamespace[ns]) {
          byNamespace[ns] = [];
        }
        byNamespace[ns].push(s);
      });

      // Display grouped by namespace
      Object.keys(byNamespace).sort().forEach(function(ns) {
        buffer.log('§e' + ns + ':§r');
        byNamespace[ns].forEach(function(s) {
          buffer.log('  ' + s.location);
        });
        buffer.log('');
      });

      buffer.log('§7Total: ' + structures.length + ' structure' + (structures.length === 1 ? '' : 's') + '§r');
      buffer.log('');
      buffer.log('§7Use "info" to see details: /rjs run large-structure info <location>§r');
    }

  } catch (error) {
    buffer.error('Error listing structures:');
    buffer.error(error.message || error);
  }

  buffer.send();
  return Promise.resolve();
}

/**
 * Handle info subcommand - show metadata for a structure
 */
function handleInfo(cmd) {
  const buffer = new MessageBuffer(Caller);

  // Parse structure location (namespace:name)
  const structureLoc = cmd.get(1);
  if (!structureLoc || !structureLoc.includes(':')) {
    buffer.error('Missing or invalid structure location');
    buffer.log('Usage: /rjs run large-structure info <namespace>:<name>');
    buffer.log('Example: /rjs run large-structure info minecraft:castle');
    buffer.send();
    return Promise.resolve();
  }

  const parts = structureLoc.split(':');
  const namespace = parts[0];
  const name = parts[1];

  buffer.log('═══════════════════════════════════════');
  buffer.log('  §6Structure Information§r');
  buffer.log('═══════════════════════════════════════');
  buffer.log('');

  try {
    const metadata = World.getLargeMetadata(namespace, name);

    if (!metadata) {
      buffer.error('Structure not found: ' + structureLoc);
      buffer.log('Use "/rjs run large-structure list" to see available structures');
      buffer.send();
      return Promise.resolve();
    }

    // Display metadata
    buffer.log('§eLocation:§r ' + metadata.location);
    buffer.log('');

    // Total size
    const totalSize = metadata.totalSize || {};
    buffer.log('§6Dimensions:§r');
    buffer.log('  Total Size: ' + (totalSize.x || '?') + 'x' + (totalSize.y || '?') + 'x' + (totalSize.z || '?') + ' blocks');

    // Piece info
    const pieceSize = metadata.pieceSize || {};
    const gridSize = metadata.gridSize || {};
    buffer.log('  Piece Size: ' + (pieceSize.x || '?') + 'x' + (pieceSize.z || '?'));
    buffer.log('  Grid Layout: ' + (gridSize.x || '?') + 'x' + (gridSize.z || '?') + ' = ' + (metadata.pieceCount || '?') + ' pieces');
    buffer.log('');

    // Show center offset info
    buffer.log('§6Center Offsets:§r');
    const offset0 = calculateCenterOffset(metadata, 0);
    const offset90 = calculateCenterOffset(metadata, 90);
    const offset180 = calculateCenterOffset(metadata, 180);
    const offset270 = calculateCenterOffset(metadata, 270);

    buffer.log('  0°:   offset ' + offset0.x + ', ' + offset0.z);
    if (offset90.x !== offset0.x || offset90.z !== offset0.z) {
      buffer.log('  90°:  offset ' + offset90.x + ', ' + offset90.z);
    }
    if (offset180.x !== offset0.x || offset180.z !== offset0.z) {
      buffer.log('  180°: offset ' + offset180.x + ', ' + offset180.z);
    }
    if (offset270.x !== offset0.x || offset270.z !== offset0.z) {
      buffer.log('  270°: offset ' + offset270.x + ', ' + offset270.z);
    }
    buffer.log('  §7Use --centered flag to auto-apply offset§r');
    buffer.log('');

    // Required mods
    if (metadata.requires && metadata.requires.length > 0) {
      buffer.log('§6Required Mods:§r');
      metadata.requires.forEach(function(mod) {
        buffer.log('  - ' + mod);
      });
      buffer.warn('⚠ This structure contains mod blocks');
    } else {
      buffer.success('✓ All blocks are vanilla (no mod requirements)');
    }

    buffer.log('');
    buffer.log('§7To place: /rjs run large-structure write ' + structureLoc + ' <x> <y> <z>§r');

  } catch (error) {
    buffer.error('Error loading structure metadata:');
    buffer.error(error.message || error);
  }

  buffer.send();
  return Promise.resolve();
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Parse coordinate (handles negative numbers)
 */
function parseCoord(value) {
  if (!value) return NaN;
  return parseInt(value, 10);
}

/**
 * Get --world flag value
 */
function getWorldFlag(cmd) {
  // Check for --world flag followed by value
  const args = cmd.args;
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--world' && i + 1 < args.length) {
      return args[i + 1];
    }
  }
  return CONFIG.DEFAULT_WORLD;
}

/**
 * Get --size flag value
 */
function getSizeFlag(cmd) {
  const args = cmd.args;
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--size' && i + 1 < args.length) {
      const sizeStr = args[i + 1];
      const parts = sizeStr.split(',');
      if (parts.length === 2) {
        const x = parseInt(parts[0], 10);
        const z = parseInt(parts[1], 10);
        if (!isNaN(x) && !isNaN(z)) {
          return [x, z];
        }
      }
    }
  }
  return CONFIG.DEFAULT_PIECE_SIZE;
}

/**
 * Get --namespace flag value
 */
function getNamespaceFlag(cmd) {
  const args = cmd.args;
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--namespace' && i + 1 < args.length) {
      return args[i + 1];
    }
  }
  return 'minecraft';  // Default namespace
}

/**
 * Get --rotation flag value
 */
function getRotationFlag(cmd) {
  const args = cmd.args;
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--rotation' && i + 1 < args.length) {
      const rotation = parseInt(args[i + 1], 10);
      if (!isNaN(rotation)) {
        // Normalize to valid rotations
        if (rotation === 0 || rotation === 90 || rotation === 180 || rotation === 270 || rotation === -90) {
          return rotation;
        }
      }
    }
  }
  return 0;  // Default: no rotation
}

/**
 * Calculate center offset for a structure.
 * Returns the offset to apply to the target position to center the structure.
 *
 * @param metadata Structure metadata with totalSize
 * @param rotation Rotation angle (0, 90, 180, 270, -90)
 * @return {x, z} offset (negative values move origin backward)
 *
 * Example: 100x25x100 structure centered at (1000, 64, 2000)
 *   - Center offset: {x: -50, z: -50}
 *   - Actual origin: (950, 64, 1950)
 */
function calculateCenterOffset(metadata, rotation) {
  const totalSize = metadata.totalSize || {};
  const sizeX = totalSize.x || 0;
  const sizeZ = totalSize.z || 0;

  // Normalize rotation
  let normalizedRotation = ((rotation % 360) + 360) % 360;
  if (normalizedRotation === -90) normalizedRotation = 270;

  // Calculate center position in original (0°) structure
  const centerX = Math.floor(sizeX / 2);
  const centerZ = Math.floor(sizeZ / 2);

  // Rotate the center position using same math as block rotation
  // This tells us where the center block ends up after rotation
  let rotatedCenterX, rotatedCenterZ;

  switch (normalizedRotation) {
    case 0:
      // (x, z) → (x, z)
      rotatedCenterX = centerX;
      rotatedCenterZ = centerZ;
      break;

    case 90:
      // (x, z) → (-z, x)
      rotatedCenterX = -centerZ;
      rotatedCenterZ = centerX;
      break;

    case 180:
      // (x, z) → (-x, -z)
      rotatedCenterX = -centerX;
      rotatedCenterZ = -centerZ;
      break;

    case 270:
      // (x, z) → (z, -x)
      rotatedCenterX = centerZ;
      rotatedCenterZ = -centerX;
      break;

    default:
      rotatedCenterX = centerX;
      rotatedCenterZ = centerZ;
  }

  // To place the rotated center at target, origin must be offset by negative rotated center
  return {
    x: -rotatedCenterX,
    z: -rotatedCenterZ
  };
}

// ============================================================================
// Execute Main
// ============================================================================

main();
