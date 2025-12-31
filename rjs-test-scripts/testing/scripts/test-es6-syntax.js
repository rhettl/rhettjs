// ES6+ Syntax Test Suite
// Tests modern JavaScript features supported by GraalVM ES2022

console.log("=".repeat(60));
console.log("ES6+ SYNTAX TEST SUITE");
console.log("=".repeat(60));
console.log("");

let passCount = 0;
let failCount = 0;

function test(name, fn) {
    try {
        fn();
        console.log(`✓ ${name}`);
        passCount++;
    } catch (e) {
        console.error(`✗ ${name}`);
        console.error(`  Error: ${e.message}`);
        failCount++;
    }
}

// ==================== POSITIVE TESTS ====================
console.log("POSITIVE TESTS (Should Pass):");
console.log("-".repeat(60));

// 1. Let and Const
test("let and const declarations", () => {
    let x = 1;
    const y = 2;
    x = 3;
    if (x !== 3 || y !== 2) throw new Error("let/const failed");
});

// 2. Arrow Functions
test("arrow functions", () => {
    const add = (a, b) => a + b;
    const greet = name => `Hello, ${name}`;
    if (add(2, 3) !== 5) throw new Error("arrow function failed");
    if (greet("World") !== "Hello, World") throw new Error("arrow function failed");
});

// 3. Template Literals
test("template literals", () => {
    const name = "Alice";
    const age = 30;
    const msg = `${name} is ${age} years old`;
    if (msg !== "Alice is 30 years old") throw new Error("template literal failed");
});

// 4. Destructuring (Arrays)
test("array destructuring", () => {
    const [a, b, c] = [1, 2, 3];
    if (a !== 1 || b !== 2 || c !== 3) throw new Error("array destructuring failed");
});

// 5. Destructuring (Objects)
test("object destructuring", () => {
    const obj = { x: 10, y: 20 };
    const { x, y } = obj;
    if (x !== 10 || y !== 20) throw new Error("object destructuring failed");
});

// 6. Default Parameters
test("default parameters", () => {
    const greet = (name = "Guest") => `Hello, ${name}`;
    if (greet() !== "Hello, Guest") throw new Error("default params failed");
    if (greet("Bob") !== "Hello, Bob") throw new Error("default params failed");
});

// 7. Rest Parameters
test("rest parameters", () => {
    const sum = (...nums) => nums.reduce((a, b) => a + b, 0);
    if (sum(1, 2, 3, 4) !== 10) throw new Error("rest params failed");
});

// 8. Spread Operator (Arrays)
test("spread operator (arrays)", () => {
    const arr1 = [1, 2, 3];
    const arr2 = [...arr1, 4, 5];
    if (arr2.length !== 5 || arr2[3] !== 4) throw new Error("spread failed");
});

// 9. Spread Operator (Objects)
test("spread operator (objects)", () => {
    const obj1 = { a: 1, b: 2 };
    const obj2 = { ...obj1, c: 3 };
    if (obj2.a !== 1 || obj2.c !== 3) throw new Error("object spread failed");
});

// 10. Enhanced Object Literals
test("enhanced object literals", () => {
    const x = 10, y = 20;
    const obj = { x, y, method() { return this.x + this.y; } };
    if (obj.x !== 10 || obj.method() !== 30) throw new Error("enhanced literal failed");
});

// 11. Computed Property Names
test("computed property names", () => {
    const key = "dynamicKey";
    const obj = { [key]: "value" };
    if (obj.dynamicKey !== "value") throw new Error("computed property failed");
});

// 12. For...of Loop
test("for...of loop", () => {
    const arr = [1, 2, 3];
    let sum = 0;
    for (const num of arr) sum += num;
    if (sum !== 6) throw new Error("for...of failed");
});

// 13. Array Methods (map, filter, reduce)
test("array methods", () => {
    const arr = [1, 2, 3, 4, 5];
    const doubled = arr.map(x => x * 2);
    const evens = arr.filter(x => x % 2 === 0);
    const sum = arr.reduce((a, b) => a + b, 0);

    if (doubled[2] !== 6) throw new Error("map failed");
    if (evens.length !== 2) throw new Error("filter failed");
    if (sum !== 15) throw new Error("reduce failed");
});

// 14. find and findIndex
test("find and findIndex", () => {
    const arr = [1, 2, 3, 4, 5];
    const found = arr.find(x => x > 3);
    const index = arr.findIndex(x => x > 3);
    if (found !== 4 || index !== 3) throw new Error("find/findIndex failed");
});

// 15. includes
test("includes", () => {
    const arr = [1, 2, 3];
    if (!arr.includes(2)) throw new Error("includes failed");
    if (arr.includes(5)) throw new Error("includes failed");
});

// 16. String Methods (startsWith, endsWith, includes)
test("string methods", () => {
    const str = "Hello, World!";
    if (!str.startsWith("Hello")) throw new Error("startsWith failed");
    if (!str.endsWith("!")) throw new Error("endsWith failed");
    if (!str.includes("World")) throw new Error("string includes failed");
});

// 17. Number.isNaN and Number.isFinite
test("Number methods", () => {
    if (!Number.isNaN(NaN)) throw new Error("isNaN failed");
    if (Number.isNaN(123)) throw new Error("isNaN failed");
    if (!Number.isFinite(123)) throw new Error("isFinite failed");
    if (Number.isFinite(Infinity)) throw new Error("isFinite failed");
});

// 18. Object.assign
test("Object.assign", () => {
    const target = { a: 1 };
    const source = { b: 2, c: 3 };
    Object.assign(target, source);
    if (target.a !== 1 || target.b !== 2 || target.c !== 3) throw new Error("Object.assign failed");
});

// 19. Object.keys, values, entries
test("Object.keys/values/entries", () => {
    const obj = { a: 1, b: 2, c: 3 };
    const keys = Object.keys(obj);
    const values = Object.values(obj);
    const entries = Object.entries(obj);

    if (keys.length !== 3) throw new Error("Object.keys failed");
    if (values[1] !== 2) throw new Error("Object.values failed");
    if (entries[0][0] !== "a") throw new Error("Object.entries failed");
});

// 20. Promises (basic)
test("Promise creation and resolution", () => {
    const p = Promise.resolve(42);
    if (!(p instanceof Promise)) throw new Error("Promise creation failed");
});

// 21. Class Syntax
test("class syntax", () => {
    class Person {
        constructor(name) {
            this.name = name;
        }
        greet() {
            return `Hello, I'm ${this.name}`;
        }
    }
    const alice = new Person("Alice");
    if (alice.greet() !== "Hello, I'm Alice") throw new Error("class failed");
});

// 22. Class Inheritance
test("class inheritance", () => {
    class Animal {
        constructor(name) {
            this.name = name;
        }
        speak() {
            return "Some sound";
        }
    }
    class Dog extends Animal {
        speak() {
            return "Woof!";
        }
    }
    const dog = new Dog("Rex");
    if (dog.name !== "Rex" || dog.speak() !== "Woof!") throw new Error("inheritance failed");
});

// 23. Getters and Setters
test("getters and setters", () => {
    const obj = {
        _value: 0,
        get value() { return this._value; },
        set value(v) { this._value = v * 2; }
    };
    obj.value = 5;
    if (obj.value !== 10) throw new Error("getter/setter failed");
});

// 24. Exponentiation Operator
test("exponentiation operator", () => {
    const result = 2 ** 3;
    if (result !== 8) throw new Error("exponentiation failed");
});

// 25. Optional Chaining (ES2020)
test("optional chaining", () => {
    const obj = { a: { b: { c: 42 } } };
    const val1 = obj?.a?.b?.c;
    const val2 = obj?.x?.y?.z;
    if (val1 !== 42 || val2 !== undefined) throw new Error("optional chaining failed");
});

// 26. Nullish Coalescing (ES2020)
test("nullish coalescing", () => {
    const val1 = null ?? "default";
    const val2 = undefined ?? "default";
    const val3 = 0 ?? "default";
    if (val1 !== "default" || val2 !== "default" || val3 !== 0) {
        throw new Error("nullish coalescing failed");
    }
});

console.log("");
console.log("-".repeat(60));
console.log("NEGATIVE TESTS (Should Fail):");
console.log("-".repeat(60));

// ==================== NEGATIVE TESTS ====================

// 1. Reassigning const
test("NEGATIVE: reassigning const should throw", () => {
    try {
        const x = 1;
        eval("x = 2"); // Use eval to avoid syntax error at parse time
        throw new Error("Should have thrown");
    } catch (e) {
        if (e.message === "Should have thrown") throw e;
        // Expected error
    }
});

// 2. Accessing destructured variable that doesn't exist
test("NEGATIVE: destructuring undefined property", () => {
    const obj = { a: 1 };
    const { b } = obj;
    if (b !== undefined) throw new Error("Should be undefined");
});

// 3. Using rest parameter not as last parameter (syntax error - tested via eval)
test("NEGATIVE: rest parameter must be last", () => {
    try {
        eval("function bad(...rest, other) {}");
        throw new Error("Should have thrown syntax error");
    } catch (e) {
        if (e.message === "Should have thrown syntax error") throw e;
        // Expected syntax error
    }
});

// 4. Duplicate parameter names in strict mode
test("NEGATIVE: duplicate parameters in strict mode", () => {
    try {
        eval("'use strict'; function bad(a, a) {}");
        throw new Error("Should have thrown syntax error");
    } catch (e) {
        if (e.message === "Should have thrown syntax error") throw e;
        // Expected error
    }
});

// 5. Using super outside of class
test("NEGATIVE: super outside class should fail", () => {
    try {
        eval("super.method()");
        throw new Error("Should have thrown");
    } catch (e) {
        if (e.message === "Should have thrown") throw e;
        // Expected error
    }
});

// 6. Invalid template literal
test("NEGATIVE: unclosed template literal", () => {
    try {
        eval("`unclosed template");
        throw new Error("Should have thrown syntax error");
    } catch (e) {
        if (e.message === "Should have thrown syntax error") throw e;
        // Expected syntax error
    }
});

// 7. Invalid spread in non-iterable
test("NEGATIVE: spread on non-iterable", () => {
    try {
        const notIterable = 123;
        const arr = [...notIterable];
        throw new Error("Should have thrown");
    } catch (e) {
        if (e.message === "Should have thrown") throw e;
        // Expected error
    }
});

console.log("");
console.log("=".repeat(60));
console.log("TEST SUMMARY");
console.log("=".repeat(60));
console.log(`Passed: ${passCount}`);
console.log(`Failed: ${failCount}`);
console.log(`Total: ${passCount + failCount}`);
console.log(`Success Rate: ${((passCount / (passCount + failCount)) * 100).toFixed(1)}%`);
console.log("=".repeat(60));

if (failCount > 0) {
    console.error(`\n⚠️  ${failCount} test(s) failed!`);
} else {
    console.log("\n✓ All ES6+ syntax tests passed!");
}
