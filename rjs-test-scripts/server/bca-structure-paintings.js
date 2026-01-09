/**
 * BCA Structure Paintings - Server Controller
 *
 * Registers commands for analyzing and fixing painting entity offsets in structures.
 * Thin orchestration layer - delegates business logic to painting-fixer module.
 *
 * Commands:
 *   /bca-structure-paintings scan [structure]  - Scan for issues
 *   /bca-structure-paintings fix <structure>   - Fix specific structure
 *   /bca-structure-paintings fix-all           - Fix all structures with issues
 */

import Commands from "rhettjs/commands";
import { StructureNbt } from 'rhettjs/structure';
import { analyzePaintings, analyzeAndFix, formatIssue } from '../modules/painting-fixer.js';
import ChatHelper from '../modules/chat-helper.js';
import {filterByPattern} from "../modules/resource-pattern.js";

// ============================================================================
// Command Registration
// ============================================================================

const cmd = Commands.register('bca-structure-paintings')
  .description('Fix painting entity offsets in structures');

cmd.subcommand('scan')
  .argument('pattern', 'string', null)
  .executes(async ({caller, args}) => {
    await handleScan(caller, args.pattern);
  });

cmd.subcommand('fix')
  .argument('structure', 'string')
  .executes(async ({caller, args}) => {
    await handleFix(caller, [args.structure]);
  });

cmd.subcommand('fix-all')
  .executes(async ({caller}) => {
    await fixAllStructures(caller);
  });

// ============================================================================
// Subcommand Handlers
// ============================================================================

async function handleScan(caller, pattern) {
  if (!pattern || /[*?]/.test(pattern)) {
    await scanAllStructures(caller, pattern);
  } else {
    // pattern is literal
    await scanStructure(caller, pattern);
  }
}

async function handleFix(caller, args) {
  const structureName = args[0];

  if (!structureName) {
    caller.sendMessage('§cUsage: /bca-structure-paintings fix <structure-name>');
    return;
  }

  await fixStructure(caller, structureName);
}

// ============================================================================
// Command Handlers
// ============================================================================

/**
 * Scan all structures and show summary of issues
 */
async function scanAllStructures(caller, pattern) {

  try {
    let allStructures = await StructureNbt.listGenerated();
    if (pattern) {
      allStructures = filterByPattern(allStructures, pattern);
    }

    if (allStructures.length === 0) {
      caller.sendMessage('§7⊘ No structures found');
      caller.sendMessage('Place structure files in the structures/ directory');
      return;
    }

    caller.sendMessage('§7Scanning §f' + allStructures.length + '§7 structures...');

    // Scan all structures
    const structuresWithIssues = [];

    for (const name of allStructures) {
      const data = await StructureNbt.load(name);
      if (!data) continue;

      const analysis = analyzePaintings(data);

      if (analysis.issues.length > 0) {
        structuresWithIssues.push({
          name,
          issueCount: analysis.issues.length,
          totalPaintings: analysis.totalPaintings,
        });
      }
    }

    if (structuresWithIssues.length === 0) {
      caller.sendSuccess('✓ No painting issues found!');
      caller.sendMessage('All ' + allStructures.length + ' structures are clean.');
    } else {
      console.warn('Issues found in ' + structuresWithIssues.length + ' structure(s):');
      caller.sendMessage('');

      structuresWithIssues.forEach(file => {
        const msg = ChatHelper.replace(
          '  §7> §f' + file.name + '\n    §7- §e' + file.issueCount + '§7/§f' + file.totalPaintings + '§7 issue(s)     [scan] [fix]',
          [
            ChatHelper.button('[scan]', '/bca-structure-paintings scan ' + file.name, {
              clickAction: 'suggest_command',
              hoverText: 'View detailed analysis'
            }),
            ChatHelper.button('[fix]', '/bca-structure-paintings fix ' + file.name, {
              color: 'green',
              clickAction: 'suggest_command',
              hoverText: 'Auto-fix all issues'
            })
          ]
        );
        caller.sendRaw(msg);
      });

      caller.sendMessage('');
      caller.sendRaw(
        ChatHelper.replace('Or [fix all structures] at once', [
          ChatHelper.button('[fix all structures]', '/bca-structure-paintings fix-all', {
            color: 'green',
            clickAction: 'suggest_command',
            hoverText: 'Fix all structures with issues'
          })
        ])
      );
    }

  } catch (error) {
    console.error('Failed to scan structures');
    caller.sendMessage('Error: ' + error.message);
  }
}

/**
 * Scan specific structure and show detailed analysis
 */
async function scanStructure(caller, structureName) {

  try {
    const exists = await StructureNbt.exists(structureName);

    if (!exists) {
      console.error('Structure not found: ' + structureName);
      return;
    }

    const data = await StructureNbt.load(structureName);

    if (!data) {
      console.error('Failed to read structure file');
      return;
    }

    const analysis = analyzePaintings(data);

    caller.sendMessage('═══════════════════════════════════');
    caller.sendMessage('§fPAINTING ANALYSIS: §e' + structureName);
    caller.sendMessage('═══════════════════════════════════');
    caller.sendMessage('§7Entities: §f' + analysis.totalEntities + ' §7| Paintings: §f' + analysis.totalPaintings);
    caller.sendMessage('');

    if (analysis.totalPaintings === 0) {
      caller.sendMessage('§7⊘ No paintings found');
    } else if (analysis.issues.length === 0) {
      caller.sendSuccess('✓ All ' + analysis.totalPaintings + ' painting(s) are correct!');
    } else {
      console.warn('Issues found: ' + analysis.issues.length + ' painting(s)');
      caller.sendMessage('§7Correct: §a' + analysis.correctCount);
      caller.sendMessage('');

      // Show detailed info for each issue
      analysis.issues.forEach(issue => {
        const formatted = formatIssue(issue);

        caller.sendMessage('  §f' + formatted.summary + '§7:');
        caller.sendMessage('    §7Issues: §e' + formatted.issueTypes);
        caller.sendMessage('    §7Block: §f' + formatted.blockPos);
        caller.sendMessage('    §7Current: §c' + formatted.currentPos);
        caller.sendMessage('    §7Suggested: §a' + formatted.correctPos);
        caller.sendMessage('');
      });

      const msg = ChatHelper.replace('Click [Fix All] to automatically fix these issues', [
        ChatHelper.button('[Fix All]', '/bca-structure-paintings fix ' + structureName, {
          color: 'green',
          bold: true,
          clickAction: 'suggest_command',
          hoverText: 'Auto-fix ' + analysis.issues.length + ' issue(s)'
        })
      ]);
      caller.sendRaw(msg);
    }


  } catch (error) {
    console.error('Failed to analyze structure');
    caller.sendMessage('Error: ' + error.message);
  }
}

/**
 * Fix painting issues in specific structure
 */
async function fixStructure(caller, structureName) {

  try {
    const exists = await StructureNbt.exists(structureName);

    if (!exists) {
      console.error('Structure not found: ' + structureName);
      return;
    }

    const data = await StructureNbt.load(structureName);

    if (!data) {
      console.error('Failed to read structure file');
      return;
    }

    const result = analyzeAndFix(data);

    if (result.totalPaintings === 0) {
      caller.sendMessage('§7⊘ No paintings found');
    } else if (result.issues.length === 0) {
      caller.sendSuccess('✓ All ' + result.totalPaintings + ' painting(s) are already correct!');
    } else {
      // Write back the modified structure
      await StructureNbt.save(structureName, data);

      caller.sendSuccess('✓ Fixed ' + result.fixed + ' painting(s)');
      caller.sendMessage('§7Structure: §f' + structureName);
      caller.sendMessage('§7Total paintings: §f' + result.totalPaintings);
    }


  } catch (error) {
    console.error('Failed to fix structure');
    caller.sendMessage('Error: ' + error.message);
  }
}

/**
 * Fix all structures with painting issues
 */
async function fixAllStructures(caller) {

  try {
    const allStructures = await StructureNbt.list();

    if (allStructures.length === 0) {
      caller.sendMessage('§7⊘ No structures found');
      return;
    }

    caller.sendMessage('§7Fixing all structures: §f' + allStructures.length + '§7 total');

    // Process all structures
    const results = [];
    let totalFixed = 0;
    let structuresFixed = 0;

    for (const name of allStructures) {
      const data = await StructureNbt.load(name);
      if (!data) continue;

      const result = analyzeAndFix(data);

      if (result.fixed > 0) {
        await StructureNbt.save(name, data);
        results.push({ name, fixed: result.fixed, total: result.totalPaintings });
        totalFixed += result.fixed;
        structuresFixed++;
      }
    }

    if (structuresFixed > 0) {
      results.forEach(result => {
        caller.sendMessage('  §a✓ §f' + result.name + '§7: fixed §e' + result.fixed + '§7/§f' + result.total);
      });
      caller.sendMessage('');
    }

    if (totalFixed === 0) {
      caller.sendSuccess('✓ No issues found - all structures are clean!');
    } else {
      caller.sendSuccess('✓ Fixed ' + totalFixed + ' painting(s) across ' + structuresFixed + ' structure(s)');
    }

  } catch (error) {
    console.error('Failed to fix all structures');
    console.log('Error: ' + error.message);
  }
}

console.log('§7[§6BCA§7] Registered: §f/bca-structure-paintings §7(scan, fix, fix-all)');