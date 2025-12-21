// Phase 3 Example: Real-World Painting Fixer
// Demonstrates threading + structure + NBT utilities working together
// Fixes incorrect painting Y-offsets in structure files

console.log('[Painting Fixer] Starting painting offset correction...');

// NOTE: This script requires Structure API to be injected
// This is a real-world example of the use case that motivated Phase 3

// Configuration
let POOLS_TO_FIX = ['village', 'desert', 'savanna', 'plains', 'taiga', 'snowy'];
let CORRECT_Y_OFFSET = 0.5;  // Paintings should be at blockY + 0.5
let INCORRECT_Y_OFFSET = 1.0; // Bug: they're at blockY + 1.0

// Statistics
let stats = {
    structuresProcessed: 0,
    paintingsFound: 0,
    paintingsFixed: 0,
    poolsProcessed: 0
};

console.log('[Painting Fixer] Configuration:');
console.log('[Painting Fixer]   Pools:', POOLS_TO_FIX.join(', '));
console.log('[Painting Fixer]   Correct offset:', CORRECT_Y_OFFSET);
console.log('[Painting Fixer]   Detecting offset:', INCORRECT_Y_OFFSET);
console.log('');

// Main processing function
function fixPaintingsInStructure(structureData) {
    let fixedCount = 0;

    // Use Structure.nbt.filter to find all paintings
    let paintings = Structure.nbt.filter(structureData, function(value, path, parent) {
        if (!parent || !parent.nbt) return false;
        return parent.nbt.id === 'minecraft:painting';
    });

    paintings.forEach(function(result) {
        let entity = result.parent;
        let blockPos = entity.blockPos;
        let pos = entity.pos;

        if (!blockPos || !pos) return;

        // Check Y offset
        let blockY = blockPos[1];
        let y = pos[1];
        let yOffset = y - blockY;

        // Fix if incorrect (approximately 1.0 instead of 0.5)
        if (Math.abs(yOffset - INCORRECT_Y_OFFSET) < 0.01) {
            pos[1] = blockY + CORRECT_Y_OFFSET;
            fixedCount++;

            console.log('[Painting Fixer]     Fixed painting:');
            console.log('[Painting Fixer]       Position:', blockPos.join(','));
            console.log('[Painting Fixer]       Old Y:', y.toFixed(2), '(offset:', yOffset.toFixed(2) + ')');
            console.log('[Painting Fixer]       New Y:', pos[1].toFixed(2), '(offset:', CORRECT_Y_OFFSET.toFixed(2) + ')');
        }
    });

    return fixedCount;
}

// Process a single pool on worker thread
function processPool(poolName) {
    console.log('[Painting Fixer] Processing pool:', poolName);

    try {
        // Get all structures in pool
        let structures = Structure.list(poolName);

        if (structures.length === 0) {
            console.log('[Painting Fixer]   No structures found in', poolName);
            return;
        }

        console.log('[Painting Fixer]   Found', structures.length, 'structures');

        let poolPaintingsFound = 0;
        let poolPaintingsFixed = 0;

        // Process each structure
        structures.forEach(function(structureName) {
            // Read structure data
            let data = Structure.read(structureName);

            if (!data || !data.entities) {
                return; // No entities, skip
            }

            // Check if structure has any paintings
            let hasPaintings = Structure.nbt.some(data, function(value, path, parent) {
                if (!parent || !parent.nbt) return false;
                return parent.nbt.id === 'minecraft:painting';
            });

            if (!hasPaintings) {
                return; // No paintings, skip
            }

            // Count paintings before fix
            let paintingsBefore = Structure.nbt.filter(data, function(value, path, parent) {
                if (!parent || !parent.nbt) return false;
                return parent.nbt.id === 'minecraft:painting';
            }).length;

            poolPaintingsFound += paintingsBefore;

            // Fix paintings in this structure
            let fixed = fixPaintingsInStructure(data);

            if (fixed > 0) {
                console.log('[Painting Fixer]   Structure:', structureName);
                console.log('[Painting Fixer]     Paintings found:', paintingsBefore);
                console.log('[Painting Fixer]     Paintings fixed:', fixed);

                // Write back the modified structure
                Structure.write(structureName, data);
                console.log('[Painting Fixer]     ✓ Saved (backup created)');

                poolPaintingsFixed += fixed;
            }

            stats.structuresProcessed++;
        });

        console.log('[Painting Fixer] ✓ Pool', poolName, 'complete');
        console.log('[Painting Fixer]   Paintings found:', poolPaintingsFound);
        console.log('[Painting Fixer]   Paintings fixed:', poolPaintingsFixed);
        console.log('');

        stats.paintingsFound += poolPaintingsFound;
        stats.paintingsFixed += poolPaintingsFixed;
        stats.poolsProcessed++;

    } catch (e) {
        console.error('[Painting Fixer] ✗ Error processing pool', poolName + ':', e.message);
    }
}

// Execute processing workflow
console.log('[Painting Fixer] Starting processing workflow...');
console.log('');

// Process all pools in parallel using Promise.all
Promise.all(POOLS_TO_FIX.map(function(poolName) {
    return task(function(poolName) {
        processPool(poolName);
        return {
            pool: poolName,
            structuresProcessed: stats.structuresProcessed,
            paintingsFound: stats.paintingsFound,
            paintingsFixed: stats.paintingsFixed
        };
    }, poolName);
})).then(function(results) {
    // Aggregate results
    let totalStructures = 0;
    let totalFound = 0;
    let totalFixed = 0;

    results.forEach(function(r) {
        totalStructures += r.structuresProcessed;
        totalFound += r.paintingsFound;
        totalFixed += r.paintingsFixed;
    });

    console.log('');
    console.log('[Painting Fixer] ═══════════════════════════════════');
    console.log('[Painting Fixer] FINAL STATISTICS');
    console.log('[Painting Fixer] ═══════════════════════════════════');
    console.log('[Painting Fixer] Pools processed:', POOLS_TO_FIX.length);
    console.log('[Painting Fixer] Structures processed:', totalStructures);
    console.log('[Painting Fixer] Paintings found:', totalFound);
    console.log('[Painting Fixer] Paintings fixed:', totalFixed);

    if (totalFixed > 0) {
        let fixRate = ((totalFixed / totalFound) * 100).toFixed(1);
        console.log('[Painting Fixer] Fix rate:', fixRate + '%');
        console.log('[Painting Fixer] ✓ SUCCESS - Backups created for all modified structures');
    } else if (totalFound > 0) {
        console.log('[Painting Fixer] ✓ All paintings already have correct offsets');
    } else {
        console.log('[Painting Fixer] ⊘ No paintings found in any structures');
    }

    console.log('[Painting Fixer] ═══════════════════════════════════');
}).catch(function(e) {
    console.error('[Painting Fixer] ✗ Fatal error:', e.message);
    console.error('[Painting Fixer] Stack:', e.stack);
});

console.log('[Painting Fixer] Processing initiated on worker threads');
console.log('[Painting Fixer] Results will appear as processing completes...');
console.log('');

// Explanation of what this script demonstrates:
console.log('[Painting Fixer] ───────────────────────────────────');
console.log('[Painting Fixer] This script demonstrates:');
console.log('[Painting Fixer] • task() - Processing pools on worker threads');
console.log('[Painting Fixer] • Promise.all() - Parallel processing');
console.log('[Painting Fixer] • Structure.list() - Finding structures by pool');
console.log('[Painting Fixer] • Structure.read() - Loading structure NBT data');
console.log('[Painting Fixer] • Structure.write() - Saving with automatic backup');
console.log('[Painting Fixer] • Structure.nbt.filter() - Finding specific entities');
console.log('[Painting Fixer] • Structure.nbt.some() - Checking for paintings');
console.log('[Painting Fixer] ───────────────────────────────────');
