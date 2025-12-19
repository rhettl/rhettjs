// Phase 3 Test: Combined Threading + Structure Workflow
// Demonstrates realistic usage patterns combining all Phase 3 features

console.log('[Combined Test] Starting combined workflow tests...');

// Test 1: Parallel structure scanning
console.log('[Combined Test] Test 1: Parallel pool scanning');
try {
    let pools = ['village', 'desert', 'plains'];
    let results = {};

    pools.forEach(function(pool) {
        task(function(poolName) {
            console.log('[Combined Test] Worker: Scanning pool', poolName);

            let structures = Structure.list(poolName);
            let count = structures.length;

            console.log('[Combined Test] Worker: Found', count, 'structures in', poolName);

            // Report back on main thread
            schedule(1, function(poolName, count) {
                results[poolName] = count;
                console.log('[Combined Test] Main: Received result for', poolName + ':', count, 'structures');

                // Check if all pools are done
                if (Object.keys(results).length === pools.length) {
                    let total = Object.values(results).reduce(function(a, b) { return a + b }, 0);
                    console.log('[Combined Test] ✓ Test 1 complete: Total structures:', total);
                }
            }, poolName, count);
        }, pool);
    });
} catch (e) {
    console.error('[Combined Test] ✗ Test 1 failed:', e.message);
}

// Test 2: Sequential processing with progress updates
console.log('[Combined Test] Test 2: Sequential processing with progress');
schedule(20, function() {
    try {
        console.log('[Combined Test] Starting sequential scan...');

        task(function() {
            let allStructures = Structure.list();
            let batchSize = 10;

            console.log('[Combined Test] Worker: Processing', allStructures.length, 'structures');

            for (let i = 0; i < allStructures.length; i += batchSize) {
                let batch = allStructures.slice(i, i + batchSize);
                let progress = Math.min(i + batchSize, allStructures.length);

                // Report progress every batch
                schedule(1, function(current, total) {
                    let percent = ((current / total) * 100).toFixed(1);
                    console.log('[Combined Test] Progress:', current, '/', total, '(' + percent + '%)');

                    if (current === total) {
                        console.log('[Combined Test] ✓ Test 2 complete');
                    }
                }, progress, allStructures.length);
            }
        });
    } catch (e) {
        console.error('[Combined Test] ✗ Test 2 failed:', e.message);
    }
});

// Test 3: Find structures with specific entity type
console.log('[Combined Test] Test 3: Find structures with entities');
schedule(40, function() {
    try {
        task(function() {
            let allStructures = Structure.list();
            let structuresWithEntities = 0;
            let totalEntities = 0;

            console.log('[Combined Test] Worker: Scanning', allStructures.length, 'structures for entities');

            allStructures.slice(0, 20).forEach(function(name) {
                let data = Structure.read(name);

                if (data && data.entities && data.entities.length > 0) {
                    structuresWithEntities++;
                    totalEntities += data.entities.length;
                }
            });

            schedule(1, function(withEntities, entities) {
                console.log('[Combined Test] ✓ Test 3 results:');
                console.log('[Combined Test]   Structures with entities:', withEntities);
                console.log('[Combined Test]   Total entities found:', entities);
            }, structuresWithEntities, totalEntities);
        });
    } catch (e) {
        console.error('[Combined Test] ✗ Test 3 failed:', e.message);
    }
});

// Test 4: Filter and analyze specific entity types
console.log('[Combined Test] Test 4: Analyze entity distribution');
schedule(60, function() {
    try {
        task(function() {
            let allStructures = Structure.list().slice(0, 10);
            let entityTypes = {};

            console.log('[Combined Test] Worker: Analyzing entity types in', allStructures.length, 'structures');

            allStructures.forEach(function(name) {
                let data = Structure.read(name);

                if (data) {
                    // Use NBT filter to find all entities
                    let entities = Structure.nbt.filter(data, function(value, path, parent) {
                        return path[0] === 'entities' && parent && parent.nbt && parent.nbt.id;
                    });

                    entities.forEach(function(result) {
                        let entityId = result.parent.nbt.id;
                        entityTypes[entityId] = (entityTypes[entityId] || 0) + 1;
                    });
                }
            });

            schedule(1, function(types) {
                console.log('[Combined Test] ✓ Test 4 complete - Entity distribution:');
                Object.keys(types).forEach(function(entityId) {
                    console.log('[Combined Test]  ', entityId + ':', types[entityId]);
                });
            }, entityTypes);
        });
    } catch (e) {
        console.error('[Combined Test] ✗ Test 4 failed:', e.message);
    }
});

// Test 5: Concurrent reads with result aggregation
console.log('[Combined Test] Test 5: Concurrent reads and aggregation');
schedule(80, function() {
    try {
        let pools = ['village', 'desert', 'savanna'];
        let poolStats = {};

        console.log('[Combined Test] Starting concurrent analysis of', pools.length, 'pools');

        pools.forEach(function(pool) {
            task(function(poolName) {
                let structures = Structure.list(poolName).slice(0, 5);
                let totalBlocks = 0;
                let totalPalette = 0;

                console.log('[Combined Test] Worker: Processing pool', poolName);

                structures.forEach(function(name) {
                    let data = Structure.read(name);
                    if (data && data.size && data.palette) {
                        let volume = data.size[0] * data.size[1] * data.size[2];
                        totalBlocks += volume;
                        totalPalette += data.palette.length;
                    }
                });

                schedule(1, function(poolName, blocks, palette) {
                    poolStats[poolName] = { blocks, palette };
                    console.log('[Combined Test] Pool', poolName + ':');
                    console.log('[Combined Test]   Total block volume:', blocks);
                    console.log('[Combined Test]   Total palette entries:', palette);

                    if (Object.keys(poolStats).length === pools.length) {
                        console.log('[Combined Test] ✓ Test 5 complete');
                    }
                }, poolName, totalBlocks, totalPalette);
            }, pool);
        });
    } catch (e) {
        console.error('[Combined Test] ✗ Test 5 failed:', e.message);
    }
});

// Test 6: Error recovery in workflow
console.log('[Combined Test] Test 6: Error recovery');
schedule(100, function() {
    try {
        task(function() {
            console.log('[Combined Test] Worker: Testing error recovery');

            try {
                // Try to read nonexistent structure
                let data = Structure.read('nonexistent/structure');
                if (data === null) {
                    console.log('[Combined Test] Worker: Handled null result correctly');
                }
            } catch (e) {
                console.log('[Combined Test] Worker: Caught error:', e.message);
            }

            schedule(1, function() {
                console.log('[Combined Test] ✓ Test 6 complete - Error recovery works');
            });
        });
    } catch (e) {
        console.error('[Combined Test] ✗ Test 6 failed:', e.message);
    }
});

// Test 7: Scope preservation across async boundary
console.log('[Combined Test] Test 7: Scope preservation in workflow');
schedule(120, function() {
    let testValue = 'scope-test-42';
    let testObject = { key: 'value', nested: { data: 123 } };

    task(function() {
        console.log('[Combined Test] Worker: Processing with scope access');
        console.log('[Combined Test] Worker: testValue =', testValue);
        console.log('[Combined Test] Worker: testObject.key =', testObject.key);

        schedule(1, function() {
            console.log('[Combined Test] Main: testValue preserved =', testValue);
            console.log('[Combined Test] Main: testObject.nested.data =', testObject.nested.data);
            console.log('[Combined Test] ✓ Test 7 complete - Scope preserved');
        });
    });
});

// Final summary
schedule(140, function() {
    console.log('');
    console.log('[Combined Test] ═══════════════════════════════════');
    console.log('[Combined Test] All combined workflow tests initiated');
    console.log('[Combined Test] ═══════════════════════════════════');
    console.log('[Combined Test] These tests demonstrate:');
    console.log('[Combined Test] • Parallel processing with task()');
    console.log('[Combined Test] • Progress reporting with schedule()');
    console.log('[Combined Test] • Structure API integration');
    console.log('[Combined Test] • NBT utility methods');
    console.log('[Combined Test] • Error handling');
    console.log('[Combined Test] • Scope preservation');
    console.log('[Combined Test] • Result aggregation');
    console.log('[Combined Test] ═══════════════════════════════════');
});

console.log('[Combined Test] All tests scheduled, watch for results...');
