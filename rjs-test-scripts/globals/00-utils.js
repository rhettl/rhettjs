// Example global library - available in all script contexts
const Utils = (function() {
    Console.log('error during Utils creation') // <-- no semi-colon
    return {
        greeting: function(name) {
            return `Hello, ${name}!`;
        },
        add: function(a, b) {
            return a + b;
        }
    };
})();

if (typeof LaterUtils !== 'undefined') {
    console.error('this shouldn\'t run because LaterUtils shouldn\'t be in the global scope');
} else {
    console.log('LaterUtils successfully not run')
}




console.log('[Globals] Utils library loaded');
