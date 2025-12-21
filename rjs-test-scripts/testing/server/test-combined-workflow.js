// Phase 3 Test: Combined Threading + Structure Workflow
// Demonstrates realistic usage patterns combining all Phase 3 features
//
// New API:
//   task(fn, ...args) → Promise (runs fn on worker thread)
//   wait(ticks) → Promise (resolves after N ticks)
//   promise.thenTask(fn) → Promise (chain to worker)
//   promise.thenWait(ticks) → Promise (chain with delay)

console.log('[Combined Test] Starting combined workflow tests...');

// Test 1: Parallel structure scanning with Promise.all
console.log('[Combined Test] Test 1: Parallel pool scanning');
try {
    let pools = ['village', 'desert', 'plains'];

    Promise.all(pools.map(function(poolName) {
        return task(function(poolName) {
            console.log('[Combined Test] Worker: Scanning pool', poolName);
            let structures = Structure.list(poolName);
            let count = structures.length;
            console.log('[Combined Test] Worker: Found', count, 'structures in', poolName);
            return { pool: poolName, count: count };
        }, poolName);
    })).then(function(results) {
        let total = results.reduce(function(sum, r) { return sum + r.count; }, 0);
        results.forEach(function(r) {
            console.log('[Combined Test] Main: Pool', r.pool + ':', r.count, 'structures');
        });
        console.log('[Combined Test] ✓ Test 1 complete: Total structures:', total);
    }).catch(function(e) {
        console.error('[Combined Test] ✗ Test 1 failed:', e.message);
    });
} catch (e) {
    console.error('[Combined Test] ✗ Test 1 failed:', e.message);
}

// Test 2: Sequential processing with progress updates
console.log('[Combined Test] Test 2: Sequential processing with progress');
wait(20).then(function() {
    console.log('[Combined Test] Starting sequential scan...');

    return task(function() {
        let allStructures = Structure.list();
        console.log('[Combined Test] Worker: Processing', allStructures.length, 'structures');
        return { structures: allStructures, total: allStructures.length };
    });
}).then(function(result) {
    let batchSize = 10;
    let total = result.total;

    // Process in batches with progress reporting
    function processBatch(index) {
        let progress = Math.min(index + batchSize, total);
        let percent = ((progress / total) * 100).toFixed(1);
        console.log('[Combined Test] Progress:', progress, '/', total, '(' + percent + '%)');

        if (progress >= total) {
            console.log('[Combined Test] ✓ Test 2 complete');
            return;
        }

        return wait(1).then(function() {
            return processBatch(index + batchSize);
        });
    }

    return processBatch(0);
}).catch(function(e) {
    console.error('[Combined Test] ✗ Test 2 failed:', e.message);
});

// Test 3: Find structures with specific entity type
console.log('[Combined Test] Test 3: Find structures with entities');
wait(40).then(function() {
    return task(function() {
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

        return { withEntities: structuresWithEntities, entities: totalEntities };
    });
}).then(function(result) {
    console.log('[Combined Test] ✓ Test 3 results:');
    console.log('[Combined Test]   Structures with entities:', result.withEntities);
    console.log('[Combined Test]   Total entities found:', result.entities);
}).catch(function(e) {
    console.error('[Combined Test] ✗ Test 3 failed:', e.message);
});

// Test 4: Filter and analyze specific entity types
console.log('[Combined Test] Test 4: Analyze entity distribution');
wait(60).then(function() {
    return task(function() {
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

        return entityTypes;
    });
}).then(function(types) {
    console.log('[Combined Test] ✓ Test 4 complete - Entity distribution:');
    Object.keys(types).forEach(function(entityId) {
        console.log('[Combined Test]  ', entityId + ':', types[entityId]);
    });
}).catch(function(e) {
    console.error('[Combined Test] ✗ Test 4 failed:', e.message);
});

// Test 5: Concurrent reads with result aggregation
console.log('[Combined Test] Test 5: Concurrent reads and aggregation');
wait(80).then(function() {
    let pools = ['village', 'desert', 'savanna'];
    console.log('[Combined Test] Starting concurrent analysis of', pools.length, 'pools');

    return Promise.all(pools.map(function(poolName) {
        return task(function(poolName) {
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

            return { pool: poolName, blocks: totalBlocks, palette: totalPalette };
        }, poolName);
    }));
}).then(function(results) {
    results.forEach(function(r) {
        console.log('[Combined Test] Pool', r.pool + ':');
        console.log('[Combined Test]   Total block volume:', r.blocks);
        console.log('[Combined Test]   Total palette entries:', r.palette);
    });
    console.log('[Combined Test] ✓ Test 5 complete');
}).catch(function(e) {
    console.error('[Combined Test] ✗ Test 5 failed:', e.message);
});

// Test 6: Error recovery in workflow
console.log('[Combined Test] Test 6: Error recovery');
wait(100).then(function() {
    return task(function() {
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

        return 'recovery-complete';
    });
}).then(function() {
    console.log('[Combined Test] ✓ Test 6 complete - Error recovery works');
}).catch(function(e) {
    console.error('[Combined Test] ✗ Test 6 failed:', e.message);
});

// Test 7: Data passing to worker (workers are isolated, must pass data explicitly)
console.log('[Combined Test] Test 7: Data passing to worker');
wait(120).then(function() {
    let testValue = 'scope-test-42';
    let testData = 123;

    // Pass data explicitly to worker
    return task(function(value, data) {
        console.log('[Combined Test] Worker: Received value =', value);
        console.log('[Combined Test] Worker: Received data =', data);
        return { value: value, data: data };
    }, testValue, testData);
}).then(function(result) {
    console.log('[Combined Test] Main: Got back value =', result.value);
    console.log('[Combined Test] Main: Got back data =', result.data);
    console.log('[Combined Test] ✓ Test 7 complete - Data passing works');
}).catch(function(e) {
    console.error('[Combined Test] ✗ Test 7 failed:', e.message);
});

// Final summary
wait(140).then(function() {
    console.log('');
    console.log('[Combined Test] ═══════════════════════════════════');
    console.log('[Combined Test] All combined workflow tests initiated');
    console.log('[Combined Test] ═══════════════════════════════════');
    console.log('[Combined Test] These tests demonstrate:');
    console.log('[Combined Test] • Parallel processing with task()');
    console.log('[Combined Test] • Progress reporting with wait()');
    console.log('[Combined Test] • Structure API integration');
    console.log('[Combined Test] • NBT utility methods');
    console.log('[Combined Test] • Error handling');
    console.log('[Combined Test] • Data passing to workers');
    console.log('[Combined Test] • Result aggregation');
    console.log('[Combined Test] ═══════════════════════════════════');
});

console.log('[Combined Test] All tests scheduled, watch for results...');
