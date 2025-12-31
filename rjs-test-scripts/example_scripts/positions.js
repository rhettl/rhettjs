// Example: Read stored positions using Store API

const player = Caller.name;
const positions = Store.namespace('positions');

const pos1 = positions.get(`${player}:pos1`);
const pos2 = positions.get(`${player}:pos2`);

if (!pos1 && !pos2) {
    Caller.sendWarning('No positions set yet!');
    Caller.sendInfo('Left-click a block to set pos1');
    Caller.sendInfo('Right-click a block to set pos2');
} else {
    if (pos1) {
        Caller.sendSuccess(`Pos1: ${pos1.x}, ${pos1.y}, ${pos1.z} in ${pos1.dimension}`);
    } else {
        Caller.sendWarning('Pos1 not set. Left-click a block to set it.');
    }

    if (pos2) {
        Caller.sendSuccess(`Pos2: ${pos2.x}, ${pos2.y}, ${pos2.z} in ${pos2.dimension}`);
    } else {
        Caller.sendWarning('Pos2 not set. Right-click a block to set it.');
    }

    // Calculate distance if both positions are set
    if (pos1 && pos2) {
        if (pos1.dimension !== pos2.dimension) {
            Caller.sendError('Positions are in different dimensions!');
        } else {
            const dx = pos2.x - pos1.x;
            const dy = pos2.y - pos1.y;
            const dz = pos2.z - pos1.z;
            const distance = Math.sqrt(dx*dx + dy*dy + dz*dz);

            Caller.sendInfo(`Distance: ${distance.toFixed(2)} blocks`);
        }
    }
}
