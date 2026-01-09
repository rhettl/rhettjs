import {makeBook, testCharWidth} from "../modules/book-helper-2.js";
// import Script from "rhettjs/script";

console.log('ping', Script.caller.name)
testCharWidth(Script.caller.name);