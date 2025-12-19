// Test if Utils exists from previous global
console.log('Checking for Utils...');
console.log('typeof Utils:', typeof Utils);

if (typeof Utils !== 'undefined') {
  console.log('SUCCESS: Utils found from previous global');
  console.log('Utils.greeting exists:', typeof Utils.greeting);
} else {
  console.error('FAIL: Utils should exist because it was defined before the error in 00-utils.js');
}

const LaterUtils = (function() {
  return {
    useUtils: function() {
      if (typeof Utils === 'undefined') {
        return 'Utils not available';
      }
      return Utils.greeting('LaterUtils');
    }
  };
})();

console.log('LaterUtils can access Utils:', LaterUtils.useUtils());
