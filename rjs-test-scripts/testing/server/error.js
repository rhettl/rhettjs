// testing if error kills script or function

console.log('base context') // <-- no semi-colon


(function () {
  const breakThis = 5;
  console.log('function context');
  breakThis += 5;

  console.error('this shouldn\'t run');
})()