import { decomon } from '../modules/decomon.js';
import { ChatHelper } from '../modules/chat-helper.js';
import Commands from 'rhettjs/commands';

// ============================================
// DECOMON - Decorative Cobblemon Manager
// ============================================
// Creates Decorative Pokemon for use in aesthetic village composition

/**
 * Send success message
 */
const sendSuccess = (caller, message) => {
  caller.sendMessage([
    { text: "✓ ", color: "green" },
    { text: message, color: "gray" }
  ]);
};

/**
 * Send error message
 */
const sendError = (caller, message) => {
  caller.sendMessage([
    { text: "✗ ", color: "red" },
    { text: message, color: "gray" }
  ]);
};

/**
 * Get selected entity for caller, or send error
 * @returns {string|null} Entity UUID or null
 */
const getSelectedOrError = (caller) => {
  const selected = decomon.getSelected(caller.uuid);
  if (!selected) {
    const selectBtn = ChatHelper.button("[Select]", "/decomon select", {
      color: ChatHelper.colors.AQUA,
      hoverText: "Click to select nearest"
    });
    const msg = ChatHelper.replace("✗ No Cobblemon selected. Use [Select] first.", [selectBtn]);
    caller.sendRaw(msg);
    return null;
  }
  return selected;
};

// ===========================================
// COMMAND REGISTRATION
// ===========================================

const cmd = Commands.register('decomon')
  .description('Creates Decorative Pokemon for use in aesthetic village composition.');

// Main help menu
cmd.subcommand('help', (caller) => {
  // Header
  caller.sendMessage([
    { text: "\n=== ", color: "yellow" },
    { text: "Decomon Manager", color: "gold", bold: true },
    { text: " ===\n", color: "yellow" },
    { text: "Manage decorative Cobblemon\n\n", color: "gray" }
  ]);

  // Main commands with buttons
  const spawnBtn = ChatHelper.button("[Spawn]", "/decomon spawn ", {
    color: ChatHelper.colors.GREEN,
    hoverText: "Click to spawn"
  });
  caller.sendRaw(ChatHelper.replace("[Spawn] - Spawn decorative Cobblemon\n", [spawnBtn]));

  const selectBtn = ChatHelper.button("[Select]", "/decomon select", {
    color: ChatHelper.colors.AQUA,
    hoverText: "Click to select nearest"
  });
  caller.sendRaw(ChatHelper.replace("[Select] - Select nearest Cobblemon\n", [selectBtn]));

  const highlightBtn = ChatHelper.button("[Highlight]", "/decomon highlight", {
    color: ChatHelper.colors.YELLOW,
    hoverText: "Click to highlight selected"
  });
  caller.sendRaw(ChatHelper.replace("[Highlight] - Highlight selected Cobblemon\n", [highlightBtn]));

  // Toggles
  caller.sendMessage([{ text: "\nToggles:\n", color: "yellow" }]);
  const toggles = ["shiny", "wandering", "invulnerable", "looks-at-player", "no-ai"];
  const toggleButtons = toggles.map(toggle =>
    ChatHelper.button(`[${toggle}]`, `/decomon toggle ${toggle}`, {
      color: ChatHelper.colors.AQUA,
      hoverText: `Toggle ${toggle}`
    })
  );
  caller.sendRaw(ChatHelper.replace(toggleButtons.map(b => b.label).join(" ") + "\n", toggleButtons));

  // Poses
  caller.sendMessage([{ text: "\nPoses:\n", color: "yellow" }]);
  const poses = ["stand", "sit", "fly", "hover", "sleep", "float", "glide", "swim", "walk"];
  const poseButtons = poses.map(pose =>
    ChatHelper.button(`[${pose}]`, `/decomon pose ${pose}`, {
      color: ChatHelper.colors.LIGHT_PURPLE,
      hoverText: `Set pose to ${pose}`
    })
  );
  caller.sendRaw(ChatHelper.replace(poseButtons.map(b => b.label).join(" ") + "\n", poseButtons));

  // Other commands
  caller.sendMessage([{ text: "\nOther Commands:\n", color: "yellow" }]);

  const moveBtn = ChatHelper.button("[Move]", "/decomon move", {
    color: ChatHelper.colors.GREEN,
    hoverText: "Teleport to your position"
  });
  caller.sendRaw(ChatHelper.replace("[Move] - Move selected to you\n", [moveBtn]));

  const infoBtn = ChatHelper.button("[Info]", "/decomon info", {
    color: ChatHelper.colors.AQUA,
    hoverText: "Show selected info"
  });
  caller.sendRaw(ChatHelper.replace("[Info] - Show selected info\n", [infoBtn]));

  const removeBtn = ChatHelper.button("[Remove]", "/decomon remove", {
    color: ChatHelper.colors.RED,
    hoverText: "Delete selected"
  });
  caller.sendRaw(ChatHelper.replace("[Remove] - Remove selected\n", [removeBtn]));

  const listBtn = ChatHelper.button("[List]", "/decomon list", {
    color: ChatHelper.colors.AQUA,
    hoverText: "Show all decorative Cobblemon"
  });
  caller.sendRaw(ChatHelper.replace("[List] - List all decorative Cobblemon\n", [listBtn]));
});

// Spawn command
cmd.subcommand('spawn', (caller, args) => {
  if (args.length < 1) {
    sendError(caller, "Usage: /decomon spawn <species> [level] [shiny] [scale]");
    const exampleBtn = ChatHelper.button("[Example]", "/decomon spawn pikachu 50", {
      color: ChatHelper.colors.AQUA,
      hoverText: "Click to try this example"
    });
    caller.sendRaw(ChatHelper.replace("Try: [Example]", [exampleBtn]));
    return;
  }

  const species = args[0];
  const level = args[1] ? parseInt(args[1]) : 50;
  const shiny = args[2] === "true" || args[2] === "shiny";
  const scale = args[3] ? parseInt(args[3]) : 100;

  try {
    const entityUuid = decomon.spawn(caller.position, { species, level, shiny, scale });
    if (entityUuid) {
      // Auto-select the spawned entity
      decomon.select(caller.uuid, entityUuid);
      sendSuccess(caller, `Spawned ${shiny ? "shiny " : ""}${species} (Level ${level}) and selected it!`);
    } else {
      sendError(caller, "Failed to spawn entity. Make sure the species name is valid.");
    }
  } catch (e) {
    sendError(caller, `Error: ${e.message}`);
  }
});

// Select command
cmd.subcommand('select', (caller) => {
  try {
    const success = decomon.selectNearest(caller);
    if (success) {
      const selected = decomon.getSelected(caller.uuid);
      const info = decomon.getInfo(selected);
      sendSuccess(caller, `Selected ${info.species}${info.customName ? ` "${info.customName}"` : ""}`);
    } else {
      sendError(caller, `No decorative Cobblemon found within 10 blocks`);
    }
  } catch (e) {
    sendError(caller, `Error: ${e.message}`);
  }
});

// Highlight command
cmd.subcommand('highlight', (caller) => {
  const selected = getSelectedOrError(caller);
  if (!selected) return;

  try {
    decomon.highlight(selected, 10);
    sendSuccess(caller, "Highlighting selected Cobblemon for 10 seconds");
  } catch (e) {
    sendError(caller, `Error: ${e.message}`);
  }
});

// Toggle commands
const toggleGroup = cmd.subcommandGroup('toggle');

const toggleCommands = {
  "shiny": { method: "toggleShiny", onMsg: "now shiny", offMsg: "no longer shiny" },
  "wandering": { method: "toggleWandering", onMsg: "wandering enabled", offMsg: "wandering disabled" },
  "invulnerable": { method: "toggleInvulnerable", onMsg: "now invulnerable", offMsg: "no longer invulnerable" },
  "looks-at-player": { method: "toggleLooksAtPlayer", onMsg: "now looks at players", offMsg: "no longer looks at players" },
  "no-ai": { method: "toggleNoAI", onMsg: "AI disabled", offMsg: "AI enabled" }
};

Object.entries(toggleCommands).forEach(([name, config]) => {
  toggleGroup.subcommand(name, (caller) => {
    const selected = getSelectedOrError(caller);
    if (!selected) return;

    try {
      const newState = decomon[config.method](selected);
      const message = newState ? config.onMsg : config.offMsg;
      const color = newState ? "aqua" : "gray";
      caller.sendMessage([
        { text: "✓ ", color: "green" },
        { text: message, color }
      ]);
    } catch (e) {
      sendError(caller, `Error: ${e.message}`);
    }
  });
});

// Pose commands
const poseGroup = cmd.subcommandGroup('pose');

const poses = ["stand", "sit", "fly", "hover", "sleep", "float", "glide", "swim", "walk"];
poses.forEach(pose => {
  poseGroup.subcommand(pose, (caller) => {
    const selected = getSelectedOrError(caller);
    if (!selected) return;

    try {
      decomon.setPose(selected, pose);
      sendSuccess(caller, `Set pose to ${pose}`);
    } catch (e) {
      sendError(caller, `Error: ${e.message}`);
    }
  });
});

// Move command
cmd.subcommand('move', (caller) => {
  const selected = getSelectedOrError(caller);
  if (!selected) return;

  try {
    decomon.move(selected, caller.position);
    sendSuccess(caller, "Moved selected Cobblemon to your position");
  } catch (e) {
    sendError(caller, `Error: ${e.message}`);
  }
});

// Info command
cmd.subcommand('info', (caller) => {
  const selected = getSelectedOrError(caller);
  if (!selected) return;

  try {
    const info = decomon.getInfo(selected);
    caller.sendMessage([
      { text: "\n=== ", color: "yellow" },
      { text: "Decomon Info", color: "gold", bold: true },
      { text: " ===\n", color: "yellow" }
    ]);
    caller.sendMessage([{ text: `Species: ${info.species}\n`, color: "gray" }]);
    caller.sendMessage([{ text: `Level: ${info.level}\n`, color: "gray" }]);
    caller.sendMessage([{ text: `Shiny: ${info.shiny ? "Yes" : "No"}\n`, color: info.shiny ? "aqua" : "gray" }]);
    caller.sendMessage([{ text: `Scale: ${info.scale}%\n`, color: "gray" }]);
    caller.sendMessage([{ text: `Wandering: ${info.wandering ? "Yes" : "No"}\n`, color: "gray" }]);
    caller.sendMessage([{ text: `Invulnerable: ${info.invulnerable ? "Yes" : "No"}\n`, color: "gray" }]);
    caller.sendMessage([{ text: `Looks at Player: ${info.looksAtPlayer ? "Yes" : "No"}\n`, color: "gray" }]);
    caller.sendMessage([{ text: `No AI: ${info.noAI ? "Yes" : "No"}\n`, color: "gray" }]);
    caller.sendMessage([{ text: `Pose: ${info.pose}\n`, color: "gray" }]);
    if (info.customName) {
      caller.sendMessage([{ text: `Name: "${info.customName}"\n`, color: "gray" }]);
    }
  } catch (e) {
    sendError(caller, `Error: ${e.message}`);
  }
});

// Remove command
cmd.subcommand('remove', (caller) => {
  const selected = getSelectedOrError(caller);
  if (!selected) return;

  try {
    decomon.remove(selected);
    sendSuccess(caller, "Removed selected Cobblemon");
  } catch (e) {
    sendError(caller, `Error: ${e.message}`);
  }
});

// List command
cmd.subcommand('list', (caller) => {
  try {
    const entities = decomon.listAll();
    if (entities.length === 0) {
      caller.sendMessage([{ text: "No decorative Cobblemon found", color: "gray" }]);
      return;
    }

    caller.sendMessage([
      { text: "\n=== ", color: "yellow" },
      { text: "Decorative Cobblemon", color: "gold", bold: true },
      { text: ` (${entities.length}) ===\n`, color: "yellow" }
    ]);

    entities.forEach((entity, index) => {
      const prefix = entity.shiny ? "✨ " : "";
      const name = entity.customName ? `"${entity.customName}"` : entity.species;
      caller.sendMessage([
        { text: `${index + 1}. ${prefix}${name} `, color: "gray" },
        { text: `(Lv${entity.level})`, color: "dark_gray" },
        { text: "\n", color: "gray" }
      ]);
    });
  } catch (e) {
    sendError(caller, `Error: ${e.message}`);
  }
});

// Scale command
cmd.subcommand('scale', (caller, args) => {
  const selected = getSelectedOrError(caller);
  if (!selected) return;

  if (args.length < 1) {
    sendError(caller, "Usage: /decomon scale <percent>");
    const exampleBtn = ChatHelper.button("[Example]", "/decomon scale 150", {
      color: ChatHelper.colors.AQUA,
      hoverText: "Set to 150% size"
    });
    caller.sendRaw(ChatHelper.replace("Try: [Example]", [exampleBtn]));
    return;
  }

  const scale = parseInt(args[0]);
  if (isNaN(scale)) {
    sendError(caller, "Scale must be a number");
    return;
  }

  try {
    decomon.setScale(selected, scale);
    sendSuccess(caller, `Set scale to ${scale}%`);
  } catch (e) {
    sendError(caller, `Error: ${e.message}`);
  }
});

// Rename command
cmd.subcommand('rename', (caller, args) => {
  const selected = getSelectedOrError(caller);
  if (!selected) return;

  if (args.length < 1) {
    sendError(caller, "Usage: /decomon rename <name>");
    return;
  }

  const name = args.join(" ");

  try {
    decomon.rename(selected, name);
    sendSuccess(caller, `Renamed to "${name}"`);
  } catch (e) {
    sendError(caller, `Error: ${e.message}`);
  }
});

// Level command
cmd.subcommand('level', (caller, args) => {
  const selected = getSelectedOrError(caller);
  if (!selected) return;

  if (args.length < 1) {
    sendError(caller, "Usage: /decomon level <1-100>");
    return;
  }

  const level = parseInt(args[0]);
  if (isNaN(level)) {
    sendError(caller, "Level must be a number");
    return;
  }

  try {
    decomon.setLevel(selected, level);
    sendSuccess(caller, `Set level to ${level}`);
  } catch (e) {
    sendError(caller, `Error: ${e.message}`);
  }
});

console.log("[Decomon] Commands registered successfully!");
