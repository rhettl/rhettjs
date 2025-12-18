// Example utility script - run with /rjs run test
console.log('[Test] Utility script executing...');

// Test console API
console.log('Testing console.log');
console.warn('Testing console.warn');
console.error('Testing console.error');

// Test logger API
logger.info('Testing logger.info');
logger.warn('Testing logger.warn');
logger.error('Testing logger.error');

// Test globals
if (typeof Utils !== 'undefined') {
    console.log('Globals work: ' + Utils.greeting('Utility'));
    console.log('2 + 3 = ' + Utils.add(2, 3));
} else {
    console.log('Utils global not loaded');
}

// Test ES6 features
const message = `Template literals work!`;
const numbers = [1, 2, 3, 4, 5];
const doubled = numbers.map(n => n * 2);
console.log('ES6 arrow functions: ' + doubled.join(', '));

'test-complete';
