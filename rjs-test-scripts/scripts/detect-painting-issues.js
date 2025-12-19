// Painting Issue Detector & Fixer
// Analyzes and fixes painting Y-offset and centering issues in structure files
//
// Usage:
//   /rjs run detect-painting-issues                      - Scan all structures, show summary
//   /rjs run detect-painting-issues <structure-name>     - Scan specific structure, show details
//   /rjs run detect-painting-issues <structure-name> -f  - Fix specific structure
//   /rjs run detect-painting-issues <structure-name> --fix
//
// Examples:
//   /rjs run detect-painting-issues                      # Scan all
//   /rjs run detect-painting-issues academy              # Scan one
//   /rjs run detect-painting-issues academy --fix        # Fix one
//
// Dependencies:
//   - Commander (globals/02-commander.js) - Arg parsing
//   - ChatHelper (globals/03-chat-helper.js) - Interactive buttons
//   - MessageBuffer (globals/01-message-buffer.js) - Message buffering
//   - Structure API - Reading/writing .nbt structure files

console.log('═══════════════════════════════════════');
console.log('PAINTING ISSUE DETECTOR & FIXER');
console.log('═══════════════════════════════════════');
console.log('');

// Configuration
const CORRECT_Y_OFFSET = 0.5;      // Paintings should be at block Y + 0.5
const TOLERANCE = 0.01;            // Floating point comparison tolerance
const CENTERING_THRESHOLD = 0.4;   // If offset is not within 0.4 of 0.5, needs fixing


// ============================================================================
// Main Execution
// ============================================================================

// Parse arguments
let cmd = new Commander();
let filename = cmd.get(0);
let shouldFix = cmd.hasFlag('fix') || cmd.hasFlag('f');

console.log('ping', filename)

if (!filename) {
  listAllStructures();
} else if (!shouldFix) {
  describeStructure(filename);
} else if (shouldFix) {
  fixPaintingIssues(filename)
}



// else {
//   // MODE 2 or 3: Scan or fix specific file
//
//   // Find matching structure
//   let matches = allStructures.filter(function(name) {
//     // Exact match
//     if (name === filename) return true;
//     // Ends with the requested name
//     if (name.endsWith('/' + filename)) return true;
//     // Substring match (case-insensitive)
//     if (name.toLowerCase().indexOf(filename.toLowerCase()) !== -1) return true;
//     return false;
//   });
//
//   if (matches.length === 0) {
//     console.log(`⚠ No structure found matching '${filename}'`);
//
//     let buffer = new MessageBuffer(Caller);
//     buffer.error('Structure not found: ' + filename);
//     buffer.log('');
//     buffer.log('Available structures:');
//     allStructures.slice(0, 10).forEach(function(name) {
//       buffer.log('  - ' + name);
//     });
//     if (allStructures.length > 10) {
//       buffer.log('  ... and ' + (allStructures.length - 10) + ' more');
//     }
//     buffer.send();
//
//   } else {
//     let targetStructure = matches[0];
//
//     if (shouldFix) {
//       // MODE 3: Fix mode
//       console.log('MODE: Fix structure');
//       console.log(`Target: ${targetStructure}`);
//       console.log('');
//
//       task(function() {
//         let structureData = Structure.read(targetStructure);
//
//         if (!structureData) {
//           console.log('ERROR: Failed to read structure file');
//           schedule(1, function() {
//             let buffer = new MessageBuffer(Caller);
//             buffer.error('Failed to read structure file');
//             buffer.send();
//           });
//           return;
//         }
//
//         // Detect issues
//         let results = detectPaintingIssues(structureData, targetStructure);
//
//         if (results.incorrectOffsets.length === 0) {
//           console.log('No issues to fix!');
//           schedule(1, function() {
//             let buffer = new MessageBuffer(Caller);
//             buffer.success('✓ No issues found - structure is already clean!');
//             buffer.send();
//           });
//           return;
//         }
//
//         // Apply fixes
//         console.log('');
//         console.log('Applying fixes...');
//         let fixedCount = fixPaintingIssues(structureData, results.incorrectOffsets);
//
//         // Write back to file
//         console.log('');
//         console.log('Writing fixed structure...');
//         Structure.write(targetStructure, structureData);
//
//         console.log('');
//         console.log(`✓ Fixed ${fixedCount} painting(s) in ${targetStructure}`);
//
//         schedule(1, function(targetStructure, fixedCount, totalPaintings) {
//           let buffer = new MessageBuffer(Caller);
//           buffer.success('✓ Fixed ' + fixedCount + ' painting(s)');
//           buffer.log('Structure: ' + targetStructure);
//           buffer.log('Total paintings: ' + totalPaintings);
//           buffer.log('');
//           buffer.log('Backup saved as: ' + targetStructure + '.bak');
//           buffer.send();
//         }, targetStructure, fixedCount, results.totalPaintings);
//       });
//
//     } else {
//       // MODE 2: Scan specific file, show details
//       console.log('MODE: Scan specific structure');
//       console.log(`Target: ${targetStructure}`);
//       console.log('');
//
//       task(function() {
//         let structureData = Structure.read(targetStructure);
//
//         if (!structureData) {
//           console.log('ERROR: Failed to read structure file');
//           schedule(1, function() {
//             let buffer = new MessageBuffer(Caller);
//             buffer.error('Failed to read structure file');
//             buffer.send();
//           });
//           return;
//         }
//
//         let results = detectPaintingIssues(structureData, targetStructure);
//
//         schedule(1, function(results) {
//           let buffer = new MessageBuffer(Caller);
//
//           buffer.log('═══════════════════════════════════');
//           buffer.log('PAINTING ANALYSIS: ' + results.structureName);
//           buffer.log('═══════════════════════════════════');
//           buffer.log('Entities: ' + results.totalEntities + ' | Paintings: ' + results.totalPaintings);
//           buffer.log('');
//
//           if (results.totalPaintings === 0) {
//             buffer.log('⊘ No paintings found');
//           } else if (results.incorrectOffsets.length === 0) {
//             buffer.success('✓ All ' + results.totalPaintings + ' painting(s) are correct!');
//           } else {
//             buffer.warn('Issues found: ' + results.incorrectOffsets.length + ' painting(s)');
//             buffer.log('Correct: ' + results.correctOffsets.length);
//             buffer.log('');
//
//             results.incorrectOffsets.forEach(function(issue) {
//               let issueTypes = [];
//               if (issue.yOffsetIncorrect) issueTypes.push('Y-offset');
//               if (issue.centeringIncorrect) issueTypes.push(issue.centeringAxis + '-centering');
//
//               buffer.log('  #' + issue.painting + ' (' + issue.variant + '): ' + issueTypes.join(', '));
//             });
//
//             buffer.log('');
//
//             let fixBtn = ChatHelper.button('[Fix All]', '/rjs run detect-painting-issues ' + results.structureName + ' --fix', {
//               color: 'green',
//               bold: true
//             });
//             fixBtn.label = '[Fix All]';
//
//             let msg = ChatHelper.replace('Click [Fix All] to automatically fix these issues', [fixBtn]);
//             buffer.raw(msg);
//           }
//
//           buffer.send();
//         }, results);
//       });
//     }
//   }
// }


console.log('═══════════════════════════════════════');
console.log('');



function listAllStructures () {
  // Get all structures
  let allStructures = Structure.list();
  console.log(`Found ${allStructures.length} structure(s) total`);
  console.log('');

  if (allStructures.length === 0) {
    console.log('⊘ No structures found!');
    console.log('');

    let buffer = new MessageBuffer(Caller);
    buffer.log('⊘ No structures found');
    buffer.log('Place structure files in the structures/ directory');
    buffer.send();

  } else {
    // MODE 1: Scan all structures, show summary with clickable buttons
    console.log(`found ${allStructures.length} structures`);
    console.log('');

    task(function (allStructures) {
      let filesWithIssues = [];
      let buffer = new MessageBuffer();

      // Scan all structures
      allStructures.forEach(function (structName) {
        let structureData = Structure.read(structName);
        if (structureData) {
          let results = detectPaintingIssues(structureData, structName);

          if (results.incorrectOffsets.length > 0) {
            filesWithIssues.push({
              name: structName,
              issueCount: results.incorrectOffsets.length,
              totalPaintings: results.totalPaintings
            });
          }
        }
      });

      if (filesWithIssues.length === 0) {
        buffer.success('✓ No painting issues found!');
        buffer.log('All ' + allStructures.length + ' structures are clean.');
      } else {
        buffer.warn('Issues found in ' + filesWithIssues.length + ' structure(s):');
        buffer.log('');

        filesWithIssues.forEach(function (file) {
          let msg = ChatHelper.replace(
            `  > ${file.name}\n    - ${file.issueCount}/${file.totalPaintings} issue(s)     [scan] [fix]`,
            [
              ChatHelper.button('[scan]', `/rjs run detect-painting-issues ${file.name}`, {
                clickAction: 'suggest_command',
              }),
              ChatHelper.button('[fix]', `/rjs run detect-painting-issues ${file.name} --fix`, {
                color: 'green',
                clickAction: 'suggest_command',
              })
            ]
          );

          buffer.raw(msg);
        });

        buffer.log('');
        buffer.log('Click [scan] to see details, [fix] to auto-fix');
      }

      // Schedule summary on main thread for chat output
      schedule(1, function (buffer) {
        buffer.setCaller(Caller)
        buffer.send();
      }, buffer);
    }, allStructures);

  }

}

function describeStructure (filename) {
  const matches = getStructuresByName(filename);

  if (matches.length === 0) {
    console.log(`⚠ No structure found matching '${filename}'`);

    let buffer = new MessageBuffer(Caller);
    buffer.error('Structure not found: ' + filename);
    buffer.send();
    return;
  }

  let targetStructure = matches[0];

  console.log('MODE: Scan specific structure');
  console.log(`Target: ${targetStructure}`);
  console.log('');

  task(function (targetStructure) {
    let structureData = Structure.read(targetStructure);

    if (!structureData) {
      console.log('ERROR: Failed to read structure file');
      schedule(1, function () {
        let buffer = new MessageBuffer(Caller);
        buffer.error('Failed to read structure file');
        buffer.send();
      });
      return;
    }

    let results = detectPaintingIssues(structureData, targetStructure);
    let buffer = new MessageBuffer();

    buffer.log('═══════════════════════════════════');
    buffer.log('PAINTING ANALYSIS: ' + results.structureName);
    buffer.log('═══════════════════════════════════');
    buffer.log('Entities: ' + results.totalEntities + ' | Paintings: ' + results.totalPaintings);
    buffer.log('');

    if (results.totalPaintings === 0) {
      buffer.log('⊘ No paintings found');
    } else if (results.incorrectOffsets.length === 0) {
      buffer.success('✓ All ' + results.totalPaintings + ' painting(s) are correct!');
      buffer.log('');
    } else {
      buffer.warn('Issues found: ' + results.incorrectOffsets.length + ' painting(s)');
      buffer.log('Correct: ' + results.correctOffsets.length);
      buffer.log('');

      // Show detailed info for each problematic painting
      results.incorrectOffsets.forEach(function (issue) {
        let issueTypes = [];
        if (issue.yOffsetIncorrect) issueTypes.push('Y-offset');
        if (issue.centeringIncorrect) issueTypes.push(issue.centeringAxis + '-centering');

        buffer.log('  #' + issue.painting + ' (' + issue.variant + ') facing ' + issue.facing + ':');
        buffer.log('    Issues: ' + issueTypes.join(', '));
        buffer.log('    Block: [' + issue.blockPos.join(', ') + ']');
        buffer.log('    Current: [' +
          issue.currentPos[0].toFixed(2) + ', ' +
          issue.currentPos[1].toFixed(2) + ', ' +
          issue.currentPos[2].toFixed(2) + ']');
        buffer.log('    Suggested: [' +
          issue.correctPos[0].toFixed(2) + ', ' +
          issue.correctPos[1].toFixed(2) + ', ' +
          issue.correctPos[2].toFixed(2) + ']');
        buffer.log('');
      });

      let msg = ChatHelper.replace('Click [Fix All] to automatically fix these issues', [
        ChatHelper.button('[Fix All]', '/rjs run detect-painting-issues ' + results.structureName + ' --fix', {
          color: 'green',
          bold: true
        })
      ]);
      buffer.raw(msg);
    }


    schedule(1, function (buffer) {
      buffer.setCaller(Caller);
      buffer.send();
    }, buffer);
  }, targetStructure);
}

/**
 * Detect painting offset issues in a structure.
 *
 * @param {Object} structureData - NBT data from Structure.read()
 * @param {string} structureName - Name for logging
 * @returns {Object} Analysis results
 */
function detectPaintingIssues (structureData, structureName) {
  const results = {
    structureName: structureName,
    totalEntities: 0,
    totalPaintings: 0,
    incorrectOffsets: [],
    correctOffsets: [],
    otherIssues: []
  };

  // Check if structure has entities
  if (!structureData || !structureData.entities) {
    console.log('  ⊘ No entities found in structure');
    return results;
  }

  results.totalEntities = structureData.entities.length;
  console.log(`  Total entities: ${results.totalEntities}`);

  // Find all paintings
  const paintings = structureData.entities.filter(function (entity) {
    return entity && entity.nbt && entity.nbt.id === 'minecraft:painting';
  });

  results.totalPaintings = paintings.length;

  if (paintings.length === 0) {
    console.log('  ⊘ No paintings found');
    return results;
  }

  console.log(`  Found ${paintings.length} painting(s)`);
  console.log('');

  // Analyze each painting
  paintings.forEach(function (entity, index) {
    const paintingNum = index + 1;
    console.log(`  Painting #${paintingNum}:`);

    // Get position data
    const blockPos = entity.blockPos;
    const pos = entity.pos;

    if (!blockPos || !pos) {
      console.log('    ✗ Missing position data');
      results.otherIssues.push({
        painting: paintingNum,
        issue: 'Missing position data'
      });
      return;
    }

    // Extract coordinates
    const blockX = blockPos[0];
    const blockY = blockPos[1];
    const blockZ = blockPos[2];

    const x = pos[0];
    const y = pos[1];
    const z = pos[2];

    console.log(`    Block position: [${blockX}, ${blockY}, ${blockZ}]`);
    console.log(`    Entity position: [${x.toFixed(2)}, ${y.toFixed(2)}, ${z.toFixed(2)}]`);

    // Calculate offsets
    const xOffset = x - blockX;
    const yOffset = y - blockY;
    const zOffset = z - blockZ;

    console.log(`    Offsets: X=${xOffset.toFixed(2)}, Y=${yOffset.toFixed(2)}, Z=${zOffset.toFixed(2)}`);

    // Get painting data
    const variant = entity.nbt && entity.nbt.variant ? entity.nbt.variant : 'unknown';
    const facing = entity.nbt && entity.nbt.facing !== undefined ? entity.nbt.facing : -1;
    const facingNames = ['south', 'west', 'north', 'east'];
    const facingName = facing >= 0 && facing < 4 ? facingNames[facing] : 'unknown';

    console.log(`    Variant: ${variant}`);
    console.log(`    Facing: ${facingName}`);

    // Check Y offset
    const yOffsetIncorrect = Math.abs(yOffset - CORRECT_Y_OFFSET) > TOLERANCE;

    // Check centering axis based on facing
    // Facing: 0=South, 1=West, 2=North, 3=East
    // South/North (0/2): X should be 0.5 (centering axis)
    // West/East (1/3): Z should be 0.5 (centering axis)
    let centeringIncorrect = false;
    let centeringAxis = '';
    let centeringOffset = 0;

    if (facing === 0 || facing === 2) {
      // South/North - X is centering axis
      centeringAxis = 'X';
      centeringOffset = xOffset;
      centeringIncorrect = Math.abs(xOffset - 0.5) > CENTERING_THRESHOLD;
    } else if (facing === 1 || facing === 3) {
      // West/East - Z is centering axis
      centeringAxis = 'Z';
      centeringOffset = zOffset;
      centeringIncorrect = Math.abs(zOffset - 0.5) > CENTERING_THRESHOLD;
    }

    // Report issues
    const hasIssues = yOffsetIncorrect || centeringIncorrect;

    if (hasIssues) {
      console.log(`    ✗ ISSUES DETECTED:`);

      if (yOffsetIncorrect) {
        console.log(`      • Y-offset incorrect: ${yOffset.toFixed(2)} (should be 0.5)`);
      }

      if (centeringIncorrect) {
        console.log(`      • ${centeringAxis}-axis not centered: ${centeringOffset.toFixed(2)} (should be 0.5)`);
      }

      // Calculate corrected position
      let correctedX = x;
      let correctedY = yOffsetIncorrect ? (blockY + 0.5) : y;
      let correctedZ = z;

      if (centeringIncorrect) {
        if (facing === 0 || facing === 2) {
          correctedX = blockX + 0.5;
        } else if (facing === 1 || facing === 3) {
          correctedZ = blockZ + 0.5;
        }
      }

      results.incorrectOffsets.push({
        painting: paintingNum,
        entity: entity,
        variant: variant,
        facing: facingName,
        blockPos: [blockX, blockY, blockZ],
        currentPos: [x, y, z],
        yOffsetIncorrect: yOffsetIncorrect,
        centeringIncorrect: centeringIncorrect,
        centeringAxis: centeringAxis,
        correctPos: [correctedX, correctedY, correctedZ]
      });
    } else {
      console.log(`    ✓ Offsets are correct`);
      results.correctOffsets.push({
        painting: paintingNum,
        variant: variant,
        facing: facingName
      });
    }

    console.log('');
  });

  return results;
}

function fixPaintingIssues (filename) {
  const matches = getStructuresByName(filename, true);

  if (matches.length === 0) {
    console.log(`⚠ No structure found matching '${filename}'`);

    let buffer = new MessageBuffer(Caller);
    buffer.error('Structure not found: ' + filename);
    buffer.send();
    return;
  }

  let targetStructure = matches[0];

  console.log('MODE: fix specific structure');
  console.log(`Target: ${targetStructure}`);
  console.log('');

  task(function (targetStructure) {
    let structureData = Structure.read(targetStructure);

    if (!structureData) {
      console.log('ERROR: Failed to read structure file');
      schedule(1, function () {
        let buffer = new MessageBuffer(Caller);
        buffer.error('Failed to read structure file');
        buffer.send();
      });
      return;
    }

    let results = detectPaintingIssues(structureData, targetStructure);
    let buffer = new MessageBuffer();

    if (results.totalPaintings === 0) {
      buffer.log('⊘ No paintings found');
    } else if (results.incorrectOffsets.length === 0) {
      buffer.success('✓ All ' + results.totalPaintings + ' painting(s) are correct!');
      buffer.log('');
    } else {
      buffer.warn('Issues found: ' + results.incorrectOffsets.length + ' painting(s)');
      buffer.log('');

      let fixedCount = 0;

      results.incorrectOffsets.forEach(function (issue) {
        if (issue.entity && issue.entity.pos) {
          // Apply fix to entity.pos
          issue.entity.pos[0] = issue.correctPos[0];
          issue.entity.pos[1] = issue.correctPos[1];
          issue.entity.pos[2] = issue.correctPos[2];

          buffer.success(`  ✓ Fixed painting #${issue.painting} (${issue.variant})`);
          fixedCount++;
        }
      });

      try {
        buffer.log(`Backing up ${targetStructure}`);
        buffer.log(`Saving changes to ${targetStructure}`);
        Structure.write(targetStructure, structureData);
      } catch (err) {
        buffer.error('Failed to save structure');
        buffer.log('Error: ' + err.message);
        console.error('Full error details:', err);
      }
    }


    schedule(1, function (buffer) {
      buffer.setCaller(Caller);
      buffer.send();
    }, buffer);
  }, targetStructure);
}

function getStructuresByName (filename, exact) {
  // Get all structures
  let allStructures = Structure.list();
  exact = exact || false;

  // Find matching structure
  return allStructures.filter(function (name) {
    // Exact match
    if (name === filename) return true;
    // Ends with the requested name
    if (!exact && name.endsWith('/' + filename)) return true;
    // Substring match (case-insensitive)
    if (!exact && name.toLowerCase().indexOf(filename.toLowerCase()) !== -1) return true;
    return false;
  });
}