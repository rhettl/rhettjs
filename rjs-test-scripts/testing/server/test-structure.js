// Phase 3 Test: Structure API (list, read, write)
// Tests high-level structure operations and NBT utilities

console.log('[Structure Test] Starting structure API tests...');

// NOTE: Structure API requires injection during platform initialization
// These tests will work once structure API is injected with server paths

// Test 1: List all structures
console.log('[Structure Test] Test 1: List all structures');
try {
    let allStructures = Structure.list();
    console.log('[Structure Test] ✓ Found', allStructures.length, 'structures');
    if (allStructures.length > 0) {
        console.log('[Structure Test] Sample:', allStructures.slice(0, 3).join(', '));
    }
} catch (e) {
    console.error('[Structure Test] ✗ Test 1 failed:', e.message);
}

// Test 2: List structures by pool
console.log('[Structure Test] Test 2: List structures in village pool');
try {
    let villageStructures = Structure.list('village');
    console.log('[Structure Test] ✓ Found', villageStructures.length, 'village structures');
} catch (e) {
    console.error('[Structure Test] ✗ Test 2 failed:', e.message);
}

// Test 3: Read a structure (if any exist)
console.log('[Structure Test] Test 3: Read structure data');
try {
    let structureList = Structure.list();
    if (structureList.length > 0) {
        let testStructure = structureList[0];
        console.log('[Structure Test] Reading structure:', testStructure);

        let data = Structure.read(testStructure);

        if (data && data.size && data.palette) {
            console.log('[Structure Test] ✓ Successfully read structure');
            console.log('[Structure Test] Size:', data.size.join('x'));
            console.log('[Structure Test] Palette entries:', data.palette.length);
            console.log('[Structure Test] Has entities:', !!data.entities);
        } else {
            console.error('[Structure Test] ✗ Structure data incomplete');
        }
    } else {
        console.log('[Structure Test] ⊘ No structures found to test read operation');
    }
} catch (e) {
    console.error('[Structure Test] ✗ Test 3 failed:', e.message);
}

// Test 4: Read with .nbt extension
console.log('[Structure Test] Test 4: Read with .nbt extension');
try {
    let allStructures = Structure.list();
    if (allStructures.length > 0) {
        let testStructure = allStructures[0];

        // Should work with or without .nbt extension
        let data1 = Structure.read(testStructure);
        let data2 = Structure.read(testStructure + '.nbt');

        if (data1 && data2) {
            console.log('[Structure Test] ✓ Extension normalization works');
        }
    } else {
        console.log('[Structure Test] ⊘ No structures to test');
    }
} catch (e) {
    console.error('[Structure Test] ✗ Test 4 failed:', e.message);
}

// Test 5: NBT utility - forEach
console.log('[Structure Test] Test 5: Structure.nbt.forEach()');
try {
    let allStructures = Structure.list();
    if (allStructures.length > 0) {
        let data = Structure.read(allStructures[0]);

        let pathCount = 0;
        Structure.nbt.forEach(data, function(value, path, parent) {
            pathCount++;
        });

        console.log('[Structure Test] ✓ forEach traversed', pathCount, 'paths');
    } else {
        console.log('[Structure Test] ⊘ No structures to test');
    }
} catch (e) {
    console.error('[Structure Test] ✗ Test 5 failed:', e.message);
}

// Test 6: NBT utility - filter for paintings
console.log('[Structure Test] Test 6: Structure.nbt.filter() for paintings');
try {
    let allStructures = Structure.list();
    if (allStructures.length > 0) {
        let data = Structure.read(allStructures[0]);

        let paintings = Structure.nbt.filter(data, function(value, path, parent) {
            if (!parent || !parent.nbt) return false;
            return parent.nbt.id === 'minecraft:painting';
        });

        console.log('[Structure Test] ✓ Found', paintings.length, 'paintings');

        if (paintings.length > 0) {
            let firstPainting = paintings[0];
            console.log('[Structure Test] Sample painting:');
            console.log('[Structure Test]   Position:', firstPainting.parent.pos);
            console.log('[Structure Test]   Variant:', firstPainting.parent.nbt.variant);
        }
    } else {
        console.log('[Structure Test] ⊘ No structures to test');
    }
} catch (e) {
    console.error('[Structure Test] ✗ Test 6 failed:', e.message);
}

// Test 7: NBT utility - find first entity
console.log('[Structure Test] Test 7: Structure.nbt.find() first entity');
try {
    let allStructures = Structure.list();
    if (allStructures.length > 0) {
        let data = Structure.read(allStructures[0]);

        let firstEntity = Structure.nbt.find(data, function(value, path, parent) {
            return path[0] === 'entities' && parent && parent.nbt;
        });

        if (firstEntity) {
            console.log('[Structure Test] ✓ Found entity:', firstEntity.parent.nbt.id);
        } else {
            console.log('[Structure Test] ⊘ No entities found in structure');
        }
    } else {
        console.log('[Structure Test] ⊘ No structures to test');
    }
} catch (e) {
    console.error('[Structure Test] ✗ Test 7 failed:', e.message);
}

// Test 8: NBT utility - some (check for entities)
console.log('[Structure Test] Test 8: Structure.nbt.some() check for entities');
try {
    let allStructures = Structure.list();
    if (allStructures.length > 0) {
        let data = Structure.read(allStructures[0]);

        let hasEntities = Structure.nbt.some(data, function(value, path, parent) {
            return path[0] === 'entities';
        });

        console.log('[Structure Test] ✓ Structure has entities:', hasEntities);
    } else {
        console.log('[Structure Test] ⊘ No structures to test');
    }
} catch (e) {
    console.error('[Structure Test] ✗ Test 8 failed:', e.message);
}

// Test 9: Write structure (creates backup)
console.log('[Structure Test] Test 9: Write structure (backup test)');
try {
    let allStructures = Structure.list();
    if (allStructures.length > 0) {
        let testStructure = allStructures[0];
        let data = Structure.read(testStructure);

        if (data) {
            // Write back the same data (should create backup)
            Structure.write(testStructure, data);
            console.log('[Structure Test] ✓ Write successful, backup should be created');
        }
    } else {
        console.log('[Structure Test] ⊘ No structures to test');
    }
} catch (e) {
    console.error('[Structure Test] ✗ Test 9 failed:', e.message);
}

// Test 10: Filter by pool and process
console.log('[Structure Test] Test 10: Filter and process pool structures');
try {
    let pools = ['village', 'desert', 'savanna', 'plains'];

    pools.forEach(function(pool) {
        let poolStructures = Structure.list(pool);
        if (poolStructures.length > 0) {
            console.log('[Structure Test] ✓ Pool', pool + ':', poolStructures.length, 'structures');
        }
    });
} catch (e) {
    console.error('[Structure Test] ✗ Test 10 failed:', e.message);
}

console.log('[Structure Test] All structure API tests completed');
console.log('[Structure Test] Note: If tests failed with "structure is not defined",');
console.log('[Structure Test] the Structure API needs to be injected during platform init.');
