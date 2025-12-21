
let arr = [];

Promise.resolve().thenWait(65).then(() => {
  if (arr.join('') === '43215') {
    console.log(`✓ Promise Order: [4,3,2,1,5] same [${arr.join(',')}]`);
  } else {
    console.error(`✗ Promise Order: [4,3,2,1,5] NOT SAME [${arr.join(',')}]`)
  }
})

Promise.resolve().thenWait(50).then(() => arr.push(1))
Promise.resolve().thenWait(30).then(() => arr.push(2))
Promise.resolve().thenWait(20).then(() => arr.push(3))
Promise.resolve().thenWait(1).then(() => arr.push(4))
Promise.resolve().thenWait(60).then(() => arr.push(5))


/*
Output A:
1
2
3
4
5

Output B: (CORRECT)
4
3
2
1
5
 */