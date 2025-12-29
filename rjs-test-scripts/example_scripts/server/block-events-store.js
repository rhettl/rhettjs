// Example: Store player positions using Store API with namespaces

const positions = Store.namespace('positions');

// Left-click to set pos1
ServerEvents.blockLeftClicked(event => {
    const player = event.player.name;

    positions.set(`${player}:pos1`, event.position);
    console.log(`[${player}] pos1 set at ${event.position.x}, ${event.position.y}, ${event.position.z}`);
});

// Right-click to set pos2
ServerEvents.blockRightClicked(event => {
    const player = event.player.name;

    positions.set(`${player}:pos2`, event.position);
    console.log(`[${player}] pos2 set at ${event.position.x}, ${event.position.y}, ${event.position.z}`);
});
