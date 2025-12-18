# RhettJS Test Scripts

This directory contains test scripts for in-game testing of RhettJS functionality.

## Directory Structure

```
rjs-test-scripts/
├── globals/    # Global libraries (loaded first, available in all scripts)
├── startup/    # Startup scripts (executed on server start, register items/blocks)
├── server/     # Server scripts (executed on server start, register event handlers)
└── scripts/    # Utility scripts (executed on-demand via /rjs run <name>)
```

## Symlink Setup

This directory is symlinked into the run directories:
- `fabric/versions/1.21.1/run/rjs` → `rjs-test-scripts/`
- `neoforge/versions/1.21.1/run/rjs` → `rjs-test-scripts/`

This allows you to:
1. Edit test scripts in one place
2. Have the same scripts work for both Fabric and NeoForge runs
3. Keep test scripts under version control separate from runtime data

## Usage

### In-Game Testing
1. Start the dev server (Fabric or NeoForge)
2. Scripts in `startup/` and `server/` execute automatically
3. Run utility scripts with `/rjs run <name>`
4. Check loaded globals with `/rjs globals`
5. Reload all scripts with `/rjs reload`

### Example Test Scripts
See `dev-docs/PHASE2_IN_GAME_TESTING.md` for complete testing examples.

## Quick Start

Create a simple test:

```javascript
// globals/00-utils.js
var Utils = (function() {
    return {
        greeting: function(name) {
            return `Hello, ${name}!`;
        }
    };
})();
console.log('[Globals] Utils loaded');
```

```javascript
// scripts/test.js
console.log(Utils.greeting('World'));
'success';
```

Then in-game:
```
/rjs globals    # Should show "Utils"
/rjs run test   # Should print "Hello, World!"
```

## Notes

- Changes to scripts take effect on `/rjs reload` (except startup scripts - need server restart)
- The server creates the `rjs/` directory automatically if it doesn't exist
- Symlinks are preserved across builds
- If run directory doesn't exist yet, it will be created on first run
