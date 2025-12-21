# RhettJS TypeScript Definitions

Auto-generated type definitions for IDE autocomplete.

**Generated**: 2025-12-20T21:09:55.914351

## Files

- `rhettjs.d.ts` - Core RhettJS APIs (console, logger, task, schedule, Structure, Caller, Args)
- `jsconfig.json.template` - VSCode project configuration template

## IDE Setup

### Visual Studio Code

**Option 1: Project-Wide (Recommended)**

1. Copy the template:
   ```bash
   cp rjs/__types/jsconfig.json.template rjs/jsconfig.json
   ```
2. Reload VSCode (Cmd/Ctrl + Shift + P → "Reload Window")
3. All scripts now have autocomplete!

**Option 2: Per-File**

Add to the top of your script:
```javascript
/// <reference path="../__types/rhettjs.d.ts" />
```

### IntelliJ IDEA / WebStorm

**Automatic** - Should work out of the box!

If not:
1. Right-click `rjs/__types/` folder
2. Mark Directory As → **Resource Root**
3. File → Invalidate Caches → Restart (if needed)

### Other IDEs

Most IDEs with TypeScript/JavaScript support will auto-discover `.d.ts` files.
If not, add reference directives (see VSCode Option 2 above).

## Testing Autocomplete

Create a test script and type:

```javascript
console.    // Should show: log, info, warn, error
Structure.  // Should show: read, write, list
```

If you see suggestions, autocomplete is working! 🎉

## Notes

- **Core APIs** are dynamically introspected and accurate
- **Structure/NBT types** match the official Minecraft NBT structure format
- **Custom globals** are introspected at runtime - complex patterns may need manual refinement
- **Re-generate** by running `/rjs probe` in-game
- **Manually edit** `rhettjs-globals.d.ts` if auto-generated types aren't perfect

## Examples

See example scripts at:
- `docs/example-scripts/` (in repository)
- GitHub: https://github.com/your-org/RhettJS/tree/main/docs/example-scripts

## More Information

- RhettJS Documentation: `docs/`
- API Reference: `docs/api/`
- GitHub: https://github.com/your-org/RhettJS
