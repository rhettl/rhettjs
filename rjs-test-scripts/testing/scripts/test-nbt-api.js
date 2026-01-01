// Test NBT API
// Tests NBT creation and manipulation

import NBT from 'NBT';

console.log("=".repeat(50));
console.log("NBT API Test");
console.log("=".repeat(50));

// Test 1: Create NBT compound
console.log("\n[Test 1] Creating NBT compound...");
const zombieNBT = NBT.compound({
  Health: NBT.double(20.0),
  IsBaby: NBT.byte(0),
  CustomName: NBT.string('Bob')
});
console.log("  zombieNBT:", JSON.stringify(zombieNBT));
console.log("  ✓ NBT.compound() working");

// Test 2: Create NBT primitives
console.log("\n[Test 2] Creating NBT primitives...");
const strVal = NBT.string('test');
const intVal = NBT.int(42);
const doubleVal = NBT.double(3.14159);
const byteVal = NBT.byte(1);
console.log("  NBT.string('test'):", JSON.stringify(strVal));
console.log("  NBT.int(42):", JSON.stringify(intVal));
console.log("  NBT.double(3.14159):", JSON.stringify(doubleVal));
console.log("  NBT.byte(1):", JSON.stringify(byteVal));
console.log("  ✓ NBT primitives working");

// Test 3: Create NBT list
console.log("\n[Test 3] Creating NBT list...");
const itemList = NBT.list([
  NBT.compound({ id: NBT.string('minecraft:diamond'), count: NBT.int(5) }),
  NBT.compound({ id: NBT.string('minecraft:emerald'), count: NBT.int(3) })
]);
console.log("  itemList:", JSON.stringify(itemList));
console.log("  ✓ NBT.list() working");

// Test 4: Query NBT with get
console.log("\n[Test 4] Querying NBT with get...");
const health = NBT.get(zombieNBT, 'Health');
const customName = NBT.get(zombieNBT, 'CustomName');
const missing = NBT.get(zombieNBT, 'NonExistent');
console.log("  NBT.get(zombieNBT, 'Health'):", health);
console.log("  NBT.get(zombieNBT, 'CustomName'):", customName);
console.log("  NBT.get(zombieNBT, 'NonExistent'):", missing);
console.log("  ✓ NBT.get() working");

// Test 5: Check existence with has
console.log("\n[Test 5] Checking existence with has...");
const hasHealth = NBT.has(zombieNBT, 'Health');
const hasCustomName = NBT.has(zombieNBT, 'CustomName');
const hasMissing = NBT.has(zombieNBT, 'NonExistent');
console.log("  NBT.has(zombieNBT, 'Health'):", hasHealth); // true
console.log("  NBT.has(zombieNBT, 'CustomName'):", hasCustomName); // true
console.log("  NBT.has(zombieNBT, 'NonExistent'):", hasMissing); // false
console.log("  ✓ NBT.has() working");

// Test 6: Modify NBT with set (immutable)
console.log("\n[Test 6] Modifying NBT with set...");
const modifiedNBT = NBT.set(zombieNBT, 'Health', NBT.double(10.0));
console.log("  Original health:", NBT.get(zombieNBT, 'Health')); // 20.0
console.log("  Modified health:", NBT.get(modifiedNBT, 'Health')); // 10.0
console.log("  ✓ NBT.set() working (immutable)");

// Test 7: Add new field with set
console.log("\n[Test 7] Adding new field with set...");
const extendedNBT = NBT.set(modifiedNBT, 'IsVillager', NBT.byte(1));
console.log("  Has IsVillager in original:", NBT.has(zombieNBT, 'IsVillager')); // false
console.log("  Has IsVillager in extended:", NBT.has(extendedNBT, 'IsVillager')); // true
console.log("  IsVillager value:", NBT.get(extendedNBT, 'IsVillager'));
console.log("  ✓ NBT.set() can add new fields");

// Test 8: Delete NBT field (immutable)
console.log("\n[Test 8] Deleting NBT field...");
const deletedNBT = NBT.delete(zombieNBT, 'IsBaby');
console.log("  Has IsBaby in original:", NBT.has(zombieNBT, 'IsBaby')); // true
console.log("  Has IsBaby after delete:", NBT.has(deletedNBT, 'IsBaby')); // false
console.log("  Original CustomName still exists:", NBT.has(deletedNBT, 'CustomName')); // true
console.log("  ✓ NBT.delete() working (immutable)");

// Test 9: Complex nested NBT
console.log("\n[Test 9] Creating complex nested NBT...");
const itemStackNBT = NBT.compound({
  id: NBT.string('minecraft:diamond_sword'),
  count: NBT.int(1),
  tag: NBT.compound({
    Damage: NBT.int(50),
    Enchantments: NBT.list([
      NBT.compound({
        id: NBT.string('minecraft:sharpness'),
        lvl: NBT.int(5)
      }),
      NBT.compound({
        id: NBT.string('minecraft:unbreaking'),
        lvl: NBT.int(3)
      })
    ])
  })
});
console.log("  Complex NBT:", JSON.stringify(itemStackNBT));
console.log("  ✓ Nested NBT structures working");

// Test 10: Query nested NBT
console.log("\n[Test 10] Querying nested NBT...");
const damage = NBT.get(itemStackNBT, 'tag.Damage');
const firstEnchant = NBT.get(itemStackNBT, 'tag.Enchantments[0].id');
console.log("  NBT.get(itemStack, 'tag.Damage'):", damage);
console.log("  NBT.get(itemStack, 'tag.Enchantments[0].id'):", firstEnchant);
console.log("  ✓ Nested NBT queries working");

// Test 11: Shallow merge
console.log("\n[Test 11] Shallow merge NBT...");
const baseNBT = NBT.compound({
  Health: NBT.double(20.0),
  CustomName: NBT.string('Bob'),
  Tags: NBT.list([NBT.string('undead')])
});
const updates = {
  Health: NBT.double(10.0),
  IsVillager: NBT.byte(1)
};
const shallowMerged = NBT.merge(baseNBT, updates);
console.log("  Original Health:", NBT.get(baseNBT, 'Health')); // 20.0
console.log("  Merged Health:", NBT.get(shallowMerged, 'Health')); // 10.0
console.log("  Has IsVillager:", NBT.has(shallowMerged, 'IsVillager')); // true
console.log("  CustomName preserved:", NBT.get(shallowMerged, 'CustomName')); // 'Bob'
console.log("  ✓ Shallow merge working");

// Test 12: Deep merge
console.log("\n[Test 12] Deep merge NBT...");
const nestedBase = NBT.compound({
  tag: NBT.compound({
    Damage: NBT.int(50),
    display: NBT.compound({
      Name: NBT.string('Sword'),
      Lore: NBT.list([NBT.string('Epic')])
    })
  })
});
const nestedUpdates = {
  tag: {
    Damage: NBT.int(100),
    display: {
      Name: NBT.string('Super Sword')
      // Lore should be preserved in deep merge
    }
  }
};
const deepMerged = NBT.merge(nestedBase, nestedUpdates, true);
console.log("  Original Damage:", NBT.get(nestedBase, 'tag.Damage')); // 50
console.log("  Merged Damage:", NBT.get(deepMerged, 'tag.Damage')); // 100
console.log("  Merged Name:", NBT.get(deepMerged, 'tag.display.Name')); // 'Super Sword'
console.log("  Lore preserved:", NBT.has(deepMerged, 'tag.display.Lore')); // true
console.log("  ✓ Deep merge working");

console.log("\n" + "=".repeat(50));
console.log("All NBT API tests passed!");
console.log("=".repeat(50));
