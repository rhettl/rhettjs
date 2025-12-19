// Structure API Test Script
// Tests Structure.list() and Structure.read() in both main thread and worker thread contexts

console.log('═══════════════════════════════════════');
console.log('STRUCTURE API TEST');
console.log('═══════════════════════════════════════');
console.log('');

console.log('Testing Structure API in MAIN THREAD context:');
console.log('');

// Test 1: List structures on main thread
console.log('1. Structure.list()');
var mainThreadList = Structure.list();
console.log('   Result: ' + (mainThreadList ? mainThreadList.length + ' structures found' : 'null'));
if (mainThreadList && mainThreadList.length > 0) {
  console.log('   First structure: ' + mainThreadList[0]);
}
console.log('');

// Test 2: Read structure on main thread
console.log('2. Structure.read()');
if (mainThreadList && mainThreadList.length > 0) {
  var testStructureName = mainThreadList[0];
  console.log('   Reading: ' + testStructureName);

  var mainThreadData = Structure.read(testStructureName);
  console.log('   Result: ' + (mainThreadData ? 'success' : 'null'));

  if (mainThreadData) {
    console.log('   Has size property: ' + (mainThreadData.size !== undefined));
    console.log('   Has entities property: ' + (mainThreadData.entities !== undefined));

    if (mainThreadData.size) {
      console.log('   Size: [' + mainThreadData.size.join(', ') + ']');
    }

    if (mainThreadData.entities !== undefined) {
      console.log('   Entities count: ' + (mainThreadData.entities ? mainThreadData.entities.length : 0));
    }
  }
} else {
  console.log('   SKIPPED: No structures available');
}
console.log('');

console.log('═══════════════════════════════════════');
console.log('');
console.log('Testing Structure API in WORKER THREAD context:');
console.log('');

// Test 3: List structures on worker thread
task(function() {
  console.log('3. Structure.list() (worker thread)');
  var workerThreadList = Structure.list();
  console.log('   Result: ' + (workerThreadList ? workerThreadList.length + ' structures found' : 'null'));
  if (workerThreadList && workerThreadList.length > 0) {
    console.log('   First structure: ' + workerThreadList[0]);
  }
  console.log('');

  // Test 4: Read structure on worker thread
  console.log('4. Structure.read() (worker thread)');
  if (workerThreadList && workerThreadList.length > 0) {
    var testStructureName = workerThreadList[0];
    console.log('   Reading: ' + testStructureName);

    var workerThreadData = Structure.read(testStructureName);
    console.log('   Result: ' + (workerThreadData ? 'success' : 'null'));

    if (workerThreadData) {
      console.log('   Has size property: ' + (workerThreadData.size !== undefined));
      console.log('   Has entities property: ' + (workerThreadData.entities !== undefined));

      if (workerThreadData.size) {
        console.log('   Size: [' + workerThreadData.size.join(', ') + ']');
      }

      if (workerThreadData.entities !== undefined) {
        console.log('   Entities count: ' + (workerThreadData.entities ? workerThreadData.entities.length : 0));

        // Test 5: Check entity structure
        if (workerThreadData.entities && workerThreadData.entities.length > 0) {
          console.log('');
          console.log('5. Entity structure test (worker thread)');
          var firstEntity = workerThreadData.entities[0];
          console.log('   First entity has blockPos: ' + (firstEntity.blockPos !== undefined));
          console.log('   First entity has pos: ' + (firstEntity.pos !== undefined));
          console.log('   First entity has nbt: ' + (firstEntity.nbt !== undefined));

          if (firstEntity.nbt) {
            console.log('   Entity nbt.id: ' + (firstEntity.nbt.id || 'undefined'));
          }
        }
      }
    }
  } else {
    console.log('   SKIPPED: No structures available');
  }
  console.log('');

  // Schedule summary on main thread
  schedule(1, function() {
    console.log('═══════════════════════════════════════');
    console.log('TEST COMPLETE');
    console.log('═══════════════════════════════════════');

    if (typeof Caller !== 'undefined') {
      var buffer = new MessageBuffer(Caller);
      buffer.log('═══════════════════════════════════');
      buffer.log('Structure API Test Complete');
      buffer.log('Check console logs for details');
      buffer.log('═══════════════════════════════════');
      buffer.send();
    }
  });
});

console.log('Main thread tests complete, worker thread tests running...');
console.log('');
