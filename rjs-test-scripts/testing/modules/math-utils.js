// Math utilities module for testing ES6 imports

export function add(a, b) {
    return a + b;
}

export function multiply(a, b) {
    return a * b;
}

export const PI = 3.14159;

// Default export
const MathUtils = {
    add,
    multiply,
    PI
};

export default MathUtils;
