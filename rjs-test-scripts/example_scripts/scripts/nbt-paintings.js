/**
 * Painting Issue Detector & Fixer
 *
 * Analyzes and fixes painting Y-offset and centering issues in structure files.
 * Uses RhettJS async utilities for efficient parallel processing.
 *
 * Usage:
 *   /rjs run nbt-paintings                      - Scan all structures, show summary
 *   /rjs run nbt-paintings <structure-name>     - Scan specific structure, show details
 *   /rjs run nbt-paintings <structure-name> -f  - Fix specific structure
 *   /rjs run nbt-paintings --fix-all            - Fix all structures with issues
 *
 * Examples:
 *   /rjs run nbt-paintings                      # Scan all
 *   /rjs run nbt-paintings academy              # Scan one
 *   /rjs run nbt-paintings academy --fix        # Fix one
 *   /rjs run nbt-paintings --fix-all            # Fix all
 *
 * Dependencies:
 *   - Commander (globals/12-commander.js) - Arg parsing
 *   - ChatHelper (globals/13-chat-helper.js) - Interactive buttons
 *   - MessageBuffer (globals/11-message-buffer.js) - Message buffering
 *   - Async (globals/14-concurrency-helper.js) - Parallel processing
 *   - Structure API - Reading/writing .nbt structure files
 */

  // ============================================================================
  // Configuration
  // ============================================================================

const CONFIG = {
    CORRECT_Y_OFFSET: 0.5,      // Paintings should be at block Y + 0.5
    TOLERANCE: 0.01,            // Floating point comparison tolerance
    CENTERING_THRESHOLD: 0.4,   // If offset is not within 0.4 of 0.5, needs fixing
    MAX_CONCURRENCY: Runtime.env.MAX_WORKER_THREADS || 4,  // Use available workers
    DEBUG: Runtime.env.IS_DEBUG
  };

// ============================================================================
// Main Entry Point
// ============================================================================

function main () {
  console.debug('═══════════════════════════════════════');
  console.debug('PAINTING ISSUE DETECTOR & FIXER');
  console.debug(`RhettJS ${Runtime.env.RJS_VERSION}`);
  console.debug(`Workers: ${CONFIG.MAX_CONCURRENCY}`);
  console.debug('═══════════════════════════════════════');

  // Parse arguments
  const cmd = new Commander();
  const filename = cmd.get(0);
  const shouldFix = cmd.hasFlag('fix') || cmd.hasFlag('f');
  const shouldFixAll = cmd.hasFlag('fix-all');

  // Route to appropriate handler and return the Promise
  if (shouldFixAll) {
    return fixAllPaintings();
  } else if (!filename) {
    return listAllStructures();
  } else if (shouldFix) {
    return fixPaintingIssues(filename);
  } else {
    return describeStructure(filename);
  }
}

// ============================================================================
// Command Handlers
// ============================================================================

/**
 * List all structures and show which have painting issues.
 * Uses parallel processing to scan structures efficiently.
 */
function listAllStructures () {
  const allStructures = Structure.list();

  if (allStructures.length === 0) {
    const buffer = new MessageBuffer(Caller);
    buffer.log('⊘ No structures found');
    buffer.log('Place structure files in the structures/ directory');
    buffer.send();
    return Promise.resolve();
  }

  console.debug(`Scanning ${allStructures.length} structures...`);

  // Scan all structures in parallel using Async.mapParallel
  return Async
    .mapParallel(
      allStructures,
      function (structName) {
        return task(function (structName) {
          const structureData = Structure.read(structName);
          if (!structureData) {
            return null;
          }

          const results = analyzePaintings(structureData, structName);

          if (results.incorrectOffsets.length > 0) {
            return {
              name: structName,
              issueCount: results.incorrectOffsets.length,
              totalPaintings: results.totalPaintings
            };
          } else {
            return null;
          }
        }, structName);
      },
      CONFIG.MAX_CONCURRENCY
    )
    .then(function (results) {
      // Filter out null results (structures with no issues)
      const filesWithIssues = results.filter(function (r) {
        return r !== null;
      });
      const totalCount = allStructures.length;

      const buffer = new MessageBuffer(Caller);

      if (filesWithIssues.length === 0) {
        buffer.success('✓ No painting issues found!');
        buffer.log('All ' + totalCount + ' structures are clean.');
      } else {
        buffer.warn('Issues found in ' + filesWithIssues.length + ' structure(s):');
        buffer.log('');

        filesWithIssues.forEach(function (file) {
          const msg = ChatHelper.replace(
            '  > ' + file.name + '\n    - ' + file.issueCount + '/' + file.totalPaintings + ' issue(s)     [scan] [fix]',
            [
              ChatHelper.button('[scan]', '/rjs run nbt-paintings ' + file.name, {
                clickAction: 'suggest_command'
              }),
              ChatHelper.button('[fix]', '/rjs run nbt-paintings ' + file.name + ' --fix', {
                color: 'green',
                clickAction: 'suggest_command'
              })
            ]
          );
          buffer.raw(msg);
        });

        buffer.log('');
        buffer.log('Click [scan] to see details, [fix] to auto-fix');
        buffer.raw(
          ChatHelper.replace('Or use the --fix-all flag to run for all structures with issue', [
            ChatHelper.button('--fix-all', '/rjs run nbt-paintings --fix-all', {
              color: 'green',
              clickAction: 'suggest_command',
              hoverText: "Click here to fix all!"
            })
          ])
        );
      }

      buffer.send();
    })
    .catch(function (error) {
      const buffer = new MessageBuffer(Caller);
      buffer.error('Failed to scan structures');
      buffer.log('Error: ' + (error.message || String(error)));
      buffer.send();
    });
}

/**
 * Describe painting issues in a specific structure.
 * Shows detailed analysis with suggested fixes.
 */
function describeStructure (filename) {
  const matches = getStructuresByName(filename);

  if (matches.length === 0) {
    const buffer = new MessageBuffer(Caller);
    buffer.error('Structure not found: ' + filename);
    buffer.send();
    return Promise.resolve();
  }

  const targetStructure = matches[0];

  console.debug('Analyzing structure: ' + targetStructure);

  return task(function (targetStructure) {
    const structureData = Structure.read(targetStructure);

    if (!structureData) {
      return { error: 'Failed to read structure file' };
    }

    return analyzePaintings(structureData, targetStructure);
  }, targetStructure).then(function (results) {
    const buffer = new MessageBuffer(Caller);

    if (results.error) {
      buffer.error(results.error);
      buffer.send();
      return;
    }

    buffer.log('═══════════════════════════════════');
    buffer.log('PAINTING ANALYSIS: ' + results.structureName);
    buffer.log('═══════════════════════════════════');
    buffer.log('Entities: ' + results.totalEntities + ' | Paintings: ' + results.totalPaintings);
    buffer.log('');

    if (results.totalPaintings === 0) {
      buffer.log('⊘ No paintings found');
    } else if (results.incorrectOffsets.length === 0) {
      buffer.success('✓ All ' + results.totalPaintings + ' painting(s) are correct!');
    } else {
      buffer.warn('Issues found: ' + results.incorrectOffsets.length + ' painting(s)');
      buffer.log('Correct: ' + results.correctOffsets.length);
      buffer.log('');

      // Show detailed info for each problematic painting
      results.incorrectOffsets.forEach(function (issue) {
        const issueTypes = [];
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

      const msg = ChatHelper.replace('Click [Fix All] to automatically fix these issues', [
        ChatHelper.button('[Fix All]', '/rjs run nbt-paintings ' + results.structureName + ' --fix', {
          color: 'green',
          bold: true
        })
      ]);
      buffer.raw(msg);
    }

    buffer.send();
  });
}

// ============================================================================
// Core Analysis Functions
// ============================================================================

/**
 * Analyze painting offset issues in a structure.
 * Pure function - does not modify input data.
 *
 * @param {Object} structureData - NBT data from Structure.read()
 * @param {string} structureName - Name for logging
 * @returns {Object} Analysis results with incorrectOffsets, correctOffsets, etc.
 */
function analyzePaintings (structureData, structureName) {
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
    console.debug('  ⊘ No entities found in structure');
    return results;
  }

  results.totalEntities = structureData.entities.length;

  console.debug('  Total entities: ' + results.totalEntities);

  // Find all paintings
  const paintings = structureData.entities.filter(function (entity) {
    return entity && entity.nbt && entity.nbt.id === 'minecraft:painting';
  });

  results.totalPaintings = paintings.length;

  if (paintings.length === 0) {
    console.debug('  ⊘ No paintings found');
    return results;
  }

  console.debug('  Found ' + paintings.length + ' painting(s)');

  // Analyze each painting
  paintings.forEach(function (entity, index) {
    const paintingNum = index + 1;

    console.debug('  Painting #' + paintingNum + ':');

    // Get position data
    const blockPos = entity.blockPos;
    const pos = entity.pos;

    if (!blockPos || !pos) {
      console.debug('    ✗ Missing position data');
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

    console.debug('    Block position: [' + blockX + ', ' + blockY + ', ' + blockZ + ']');
    console.debug('    Entity position: [' + x.toFixed(2) + ', ' + y.toFixed(2) + ', ' + z.toFixed(2) + ']');

    // Calculate offsets
    const xOffset = x - blockX;
    const yOffset = y - blockY;
    const zOffset = z - blockZ;

    console.debug('    Offsets: X=' + xOffset.toFixed(2) + ', Y=' + yOffset.toFixed(2) + ', Z=' + zOffset.toFixed(2));

    // Get painting data
    const variant = entity.nbt && entity.nbt.variant ? entity.nbt.variant : 'unknown';
    const facing = entity.nbt && entity.nbt.facing !== undefined ? entity.nbt.facing : -1;
    const facingNames = ['south', 'west', 'north', 'east'];
    const facingName = facing >= 0 && facing < 4 ? facingNames[facing] : 'unknown';

    console.debug('    Variant: ' + variant);
    console.debug('    Facing: ' + facingName);

    // Check Y offset
    const yOffsetIncorrect = Math.abs(yOffset - CONFIG.CORRECT_Y_OFFSET) > CONFIG.TOLERANCE;

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
      centeringIncorrect = Math.abs(xOffset - 0.5) > CONFIG.CENTERING_THRESHOLD;
    } else if (facing === 1 || facing === 3) {
      // West/East - Z is centering axis
      centeringAxis = 'Z';
      centeringOffset = zOffset;
      centeringIncorrect = Math.abs(zOffset - 0.5) > CONFIG.CENTERING_THRESHOLD;
    }

    // Report issues
    const hasIssues = yOffsetIncorrect || centeringIncorrect;

    if (hasIssues) {

      console.debug('    ✗ ISSUES DETECTED:');
      if (yOffsetIncorrect) {
        console.debug('      • Y-offset incorrect: ' + yOffset.toFixed(2) + ' (should be 0.5)');
      }
      if (centeringIncorrect) {
        console.debug('      • ' + centeringAxis + '-axis not centered: ' + centeringOffset.toFixed(2) + ' (should be 0.5)');
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
      if (CONFIG.DEBUG) {
        console.debug('    ✓ Offsets are correct');
      }
      results.correctOffsets.push({
        painting: paintingNum,
        variant: variant,
        facing: facingName
      });
    }
  });

  return results;
}

// ============================================================================
// Fix Functions
// ============================================================================

/**
 * Fix painting issues in a single structure.
 * Pure worker function - runs on worker thread.
 *
 * @param {string} structureName - Structure to fix
 * @returns {Object} Result object with success, fixed count, total count
 */
function applyPaintingFixes (structureName) {
  try {
    const structureData = Structure.read(structureName);

    if (!structureData) {
      return {
        success: false,
        fixed: 0,
        total: 0,
        error: new Error('Failed to read structure file: ' + structureName)
      };
    }

    const results = analyzePaintings(structureData, structureName);

    if (results.totalPaintings === 0) {
      return {
        success: true,
        fixed: 0,
        total: 0,
        message: 'No paintings found'
      };
    }

    if (results.incorrectOffsets.length === 0) {
      return {
        success: true,
        fixed: 0,
        total: results.totalPaintings,
        message: 'All paintings already correct'
      };
    }

    // Apply fixes
    let fixedCount = 0;
    results.incorrectOffsets.forEach(function (issue) {
      if (issue.entity && issue.entity.pos) {
        let oldPos = [issue.entity.pos[0], issue.entity.pos[1], issue.entity.pos[2]];
        console.debug('  Fixed painting #' + issue.painting + ':');
        console.debug('    Old: [' + oldPos[0].toFixed(2) + ', ' + oldPos[1].toFixed(2) + ', ' + oldPos[2].toFixed(2) + ']');
        console.debug('    New: [' + issue.correctPos[0].toFixed(2) + ', ' + issue.correctPos[1].toFixed(2) + ', ' + issue.correctPos[2].toFixed(2) + ']');

        issue.entity.pos[0] = issue.correctPos[0];
        issue.entity.pos[1] = issue.correctPos[1];
        issue.entity.pos[2] = issue.correctPos[2];
        fixedCount++;
      }
    });

    console.debug('Writing ' + structureName + ' with ' + fixedCount + ' fixes...');

    // Write back
    Structure.write(structureName, structureData);

    return {
      success: true,
      fixed: fixedCount,
      total: results.totalPaintings
    };

  } catch (err) {
    return {
      success: false,
      fixed: 0,
      total: 0,
      error: new Error(err.message || String(err))
    };
  }
}

/**
 * Fix painting issues in a specific structure.
 * Orchestrates worker task and main thread output.
 */
function fixPaintingIssues (filename) {
  const matches = getStructuresByName(filename, true);

  if (matches.length === 0) {
    const buffer = new MessageBuffer(Caller);
    buffer.error('Structure not found: ' + filename);
    buffer.send();
    return Promise.resolve();
  }

  const targetStructure = matches[0];

  console.debug('Fixing structure: ' + targetStructure);

  return task(function (targetStructure) {
    return applyPaintingFixes(targetStructure);
  }, targetStructure).then(function (result) {
    const buffer = new MessageBuffer(Caller);

    if (!result.success) {
      buffer.error('Failed to fix structure');
      buffer.log('Error: ' + result.error.message);
    } else if (result.fixed === 0 && result.total === 0) {
      buffer.log('⊘ ' + (result.message || 'No paintings found'));
    } else if (result.fixed === 0) {
      buffer.success('✓ All ' + result.total + ' painting(s) are correct!');
    } else {
      buffer.success('✓ Fixed ' + result.fixed + ' painting(s)');
      buffer.log('Structure: ' + targetStructure);
      buffer.log('Total paintings: ' + result.total);
    }

    buffer.send();
  });
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Find structures matching a name pattern.
 * Supports exact match, path suffix match, and substring match.
 *
 * @param {string} filename - Name or pattern to match
 * @param {boolean} exact - If true, only exact matches
 * @returns {string[]} Array of matching structure names
 */
function getStructuresByName (filename, exact) {
  const allStructures = Structure.list();
  exact = exact || false;

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

/**
 * Fix painting issues in all structures.
 * Uses parallel processing for efficiency.
 */
function fixAllPaintings () {
  const allStructures = Structure.list();

  console.debug('Fixing all structures: ' + allStructures.length + ' total');

  // Process all structures in parallel
  return Async
    .mapParallel(
      allStructures,
      function (structName, index) {
        return task(function (structName, index) {
          console.debug('Structure[' + index + ']: ' + structName);
          const result = applyPaintingFixes(structName);
          console.debug('Structure[' + index + ']: Finished ' + structName);
          if (!result.success) {
            throw result.error;
          }
          return result;
        }, structName, index);
      },
      CONFIG.MAX_CONCURRENCY
    )
    .then(function (results) {
      const buffer = new MessageBuffer(Caller);
      let totalFixed = 0;
      let structuresFixed = 0;

      results.forEach(function (result, index) {
        if (result && result.fixed > 0) {
          buffer.log('  ✓ ' + allStructures[index] + ': fixed ' + result.fixed + '/' + result.total);
          totalFixed += result.fixed;
          structuresFixed++;
        }
      });

      buffer.log('');
      if (totalFixed === 0) {
        buffer.success('✓ No issues found - all structures are clean!');
      } else {
        buffer.success('✓ Fixed ' + totalFixed + ' painting(s) across ' + structuresFixed + ' structure(s)');
      }

      buffer.send();
    })
    .catch(function (error) {
      const buffer = new MessageBuffer(Caller);
      buffer.error('Failed to fix all paintings');
      buffer.log('Error: ' + (error.message || String(error)));
      buffer.send();
    });
}

// ============================================================================
// Execute Main
// ============================================================================

// Execute main and return its Promise
// This must be the last expression in the file so the script engine can detect and wait for it
main();