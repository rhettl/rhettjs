/**
 * Command Helpers - Level 4 Helper Library
 *
 * Provides high-level helpers for building Minecraft commands using Command API.
 * These helpers construct valid command strings that can be executed via Command.execute().
 *
 * Philosophy: This is a Level 4 helper - uses foundation APIs (Command) to provide
 * user-friendly abstractions without requiring Kotlin changes.
 *
 * Exports: Cmd (global)
 */

const Cmd = (function () {
  // Internal implementation object
  const CommandHelpers = {
    /**
     * Teleport helper - flexible tp command builder
     *
     * @param {Object} options - Teleport options
     * @param {string|Object} options.from - Entity selector or "self" (optional, defaults to "self")
     * @param {string|Object|Array} options.to - Entity selector, coords [x,y,z], or {x,y,z}
     * @param {boolean} options.facing - If true and to is entity, use "facing entity" (optional)
     * @returns {string} - Teleport command string
     *
     * Examples:
     *   tp({ to: [100, 64, 200] })                    // tp @s 100 64 200
     *   tp({ to: { x: 100, y: 64, z: 200 } })        // tp @s 100 64 200
     *   tp({ to: "@e[type=cow,limit=1]" })           // tp @s @e[type=cow,limit=1]
     *   tp({ from: "@a", to: [0, 100, 0] })          // tp @a 0 100 0
     *   tp({ to: "@p", facing: true })               // tp @s @p (faces toward target)
     */
    tp (options) {
      // Parse 'from' parameter
      let fromSelector = "@s"; // Default to self
      if (options.from) {
        if (options.from === "self") {
          fromSelector = "@s";
        } else if (typeof options.from === "string") {
          fromSelector = options.from;
        } else {
          throw new Error("tp: 'from' must be a string selector or 'self'");
        }
      }

      // Parse 'to' parameter
      let toStr;
      if (Array.isArray(options.to)) {
        // Array of coords: [x, y, z]
        if (options.to.length !== 3) {
          throw new Error("tp: coords array must have 3 elements [x, y, z]");
        }
        toStr = options.to.join(" ");
      } else if (typeof options.to === "object" && options.to !== null) {
        // Object with x, y, z properties
        if (options.to.x === undefined || options.to.y === undefined || options.to.z === undefined) {
          throw new Error("tp: coords object must have x, y, z properties");
        }
        toStr = `${options.to.x} ${options.to.y} ${options.to.z}`;
      } else if (typeof options.to === "string") {
        // Entity selector
        toStr = options.to;
        // Add facing modifier if requested
        if (options.facing) {
          toStr = `${toStr} facing entity ${toStr}`;
        }
      } else {
        throw new Error("tp: 'to' must be coords [x,y,z], {x,y,z}, or entity selector string");
      }

      return `tp ${fromSelector} ${toStr}`;
    },

    /**
     * Entity Selector Builder
     * Constructs Minecraft entity selectors like @e[type=cow,distance=..10]
     *
     * @param {string} base - Base selector: "e", "a", "p", "r", "s" (or with @)
     * @param {Object} params - Selector parameters
     * @returns {string} - Complete entity selector
     *
     * Examples:
     *   selector("e", { type: "cow" })                          // @e[type=cow]
     *   selector("a", { distance: "..10", gamemode: "creative" }) // @a[distance=..10,gamemode=creative]
     *   selector("e", { type: "!player", limit: 1 })            // @e[type=!player,limit=1]
     *   selector("p")                                           // @p
     */
    selector (base, params = {}) {
      // Normalize base (allow with or without @)
      let selector = base.startsWith("@") ? base : `@${base}`;

      // Validate base selector
      if (!["@e", "@a", "@p", "@r", "@s"].includes(selector)) {
        throw new Error(`selector: invalid base '${base}'. Must be one of: e, a, p, r, s`);
      }

      // Build parameter string
      const paramParts = [];
      for (let key of Object.getOwnPropertyNames(params)) {
        let value = params[key];
        // Handle different value types
        if (typeof value === "boolean") {
          // Boolean parameters (not common in selectors, but handle anyway)
          paramParts.push(`${key}=${value}`);
        } else if (typeof value === "number") {
          paramParts.push(`${key}=${value}`);
        } else if (typeof value === "string") {
          paramParts.push(`${key}=${value}`);
        } else if (Array.isArray(value)) {
          // Array values for tags, scores, etc.
          paramParts.push(`${key}=${value.join(",")}`);
        } else {
          throw new Error(`selector: invalid value type for '${key}': ${typeof value}`);
        }
      }

      // Add parameters if any
      if (paramParts.length > 0) {
        selector += `[${paramParts.join(",")}]`;
      }

      return selector;
    },

    /**
     * Quick selector helpers for common cases
     */
    allPlayers (params = {}) {
      return this.selector("a", params);
    },

    nearestPlayer (params = {}) {
      return this.selector("p", params);
    },

    allEntities (params = {}) {
      return this.selector("e", params);
    },

    randomPlayer (params = {}) {
      return this.selector("r", params);
    },

    self () {
      return "@s";
    },

    /**
     * Give command helper
     *
     * @param {string} target - Entity selector (optional, defaults to @s)
     * @param {string} item - Item ID (e.g., "diamond", "minecraft:stone")
     * @param {number} count - Item count (optional, defaults to 1)
     * @returns {string} - Give command string
     *
     * Examples:
     *   give("diamond")                    // give @s diamond 1
     *   give("stone", 64)                  // give @s stone 64
     *   give("@a", "diamond_sword", 1)     // give @a diamond_sword 1
     */
    give (target, item, count) {
      // Handle overloaded parameters
      if (count === undefined && typeof item === "number") {
        // give(item, count)
        count = item;
        item = target;
        target = "@s";
      } else if (item === undefined) {
        // give(item)
        item = target;
        target = "@s";
        count = 1;
      } else if (count === undefined) {
        count = 1;
      }

      return `give ${target} ${item} ${count}`;
    },

    /**
     * Fill command helper
     *
     * @param {Array|Object} from - Start coords [x,y,z] or {x,y,z}
     * @param {Array|Object} to - End coords [x,y,z] or {x,y,z}
     * @param {string} block - Block ID
     * @param {string} mode - Fill mode: "replace", "destroy", "keep", "hollow", "outline" (optional)
     * @returns {string} - Fill command string
     *
     * Examples:
     *   fill([0,64,0], [10,64,10], "stone")                // fill 0 64 0 10 64 10 stone
     *   fill({x:0,y:64,z:0}, {x:10,y:64,z:10}, "air")      // fill 0 64 0 10 64 10 air
     *   fill([0,64,0], [10,70,10], "stone", "hollow")      // fill 0 64 0 10 70 10 stone hollow
     */
    fill (from, to, block, mode) {
      const fromCoords = Array.isArray(from) ? from : [from.x, from.y, from.z];
      const toCoords = Array.isArray(to) ? to : [to.x, to.y, to.z];

      let cmd = `fill ${fromCoords.join(" ")} ${toCoords.join(" ")} ${block}`;
      if (mode) {
        cmd += ` ${mode}`;
      }

      return cmd;
    },

    /**
     * Setblock command helper
     *
     * @param {Array|Object} pos - Block coords [x,y,z] or {x,y,z}
     * @param {string} block - Block ID
     * @param {string} mode - Mode: "replace", "destroy", "keep" (optional)
     * @returns {string} - Setblock command string
     */
    setblock (pos, block, mode) {
      const coords = Array.isArray(pos) ? pos : [pos.x, pos.y, pos.z];

      let cmd = `setblock ${coords.join(" ")} ${block}`;
      if (mode) {
        cmd += ` ${mode}`;
      }

      return cmd;
    },

    /**
     * Execute command builder - constructs complex /execute commands
     * Returns a builder object for chaining
     *
     * @returns {ExecuteBuilder}
     *
     * Example:
     *   execute()
     *     .as("@a")
     *     .at("@s")
     *     .if({ block: [0, 64, 0], is: "stone" })
     *     .run("say Hello")
     *     .build()
     *   // Returns: "execute as @a at @s if block 0 64 0 stone run say Hello"
     */
    execute () {
      return new ExecuteBuilder();
    }
  };

  /**
   * Execute Command Builder
   * Provides fluent API for building complex /execute commands
   */
  function ExecuteBuilder () {
    this.parts = [];
  }

  /**
   * Add "as <selector>" clause
   */
  ExecuteBuilder.prototype.as = function (selector) {
    this.parts.push(`as ${selector}`);
    return this;
  }

  /**
   * Add "at <selector>" clause
   */
  ExecuteBuilder.prototype.at = function (selector) {
    this.parts.push(`at ${selector}`);
    return this;
  }

  /**
   * Add "positioned <coords>" clause
   */
  ExecuteBuilder.prototype.positioned = function (coords) {
    const coordStr = Array.isArray(coords)
                     ? coords.join(" ")
                     : `${coords.x} ${coords.y} ${coords.z}`;
    this.parts.push(`positioned ${coordStr}`);
    return this;
  }

  /**
   * Add "if" condition clause
   *
   * @param {Object} condition - Condition object
   * @param {Array|Object} condition.block - Block coords for "if block" check
   * @param {string} condition.is - Block type to check
   * @param {string} condition.entity - Entity selector for "if entity" check
   * @param {Object} condition.score - Score condition {target, objective, matches}
   */
  ExecuteBuilder.prototype.if = function (condition) {
    if (condition.block && condition.is) {
      const coords = Array.isArray(condition.block)
                     ? condition.block.join(" ")
                     : `${condition.block.x} ${condition.block.y} ${condition.block.z}`;
      this.parts.push(`if block ${coords} ${condition.is}`);
    } else if (condition.entity) {
      this.parts.push(`if entity ${condition.entity}`);
    } else if (condition.score) {
      const {target, objective, matches} = condition.score;
      this.parts.push(`if score ${target} ${objective} matches ${matches}`);
    } else {
      throw new Error("execute.if: invalid condition");
    }
    return this;
  }

  /**
   * Add "unless" condition clause (same format as if)
   */
  ExecuteBuilder.prototype.unless = function (condition) {
    if (condition.block && condition.is) {
      const coords = Array.isArray(condition.block)
                     ? condition.block.join(" ")
                     : `${condition.block.x} ${condition.block.y} ${condition.block.z}`;
      this.parts.push(`unless block ${coords} ${condition.is}`);
    } else if (condition.entity) {
      this.parts.push(`unless entity ${condition.entity}`);
    } else if (condition.score) {
      const {target, objective, matches} = condition.score;
      this.parts.push(`unless score ${target} ${objective} matches ${matches}`);
    } else {
      throw new Error("execute.unless: invalid condition");
    }
    return this;
  }

  /**
   * Add "in <dimension>" clause
   */
  ExecuteBuilder.prototype.in = function dimension () {
    this.parts.push(`in ${dimension}`);
    return this;
  }

  /**
   * Add "run <command>" clause (final clause)
   */
  ExecuteBuilder.prototype.run = function (command) {
    this.parts.push(`run ${command}`);
    return this;
  }

  /**
   * Build the final execute command string
   */
  ExecuteBuilder.prototype.build = function () {
    if (this.parts.length === 0) {
      throw new Error("execute builder: no clauses added");
    }
    return `execute ${this.parts.join(" ")}`;
  }

  /**
   * Build and execute the command immediately
   * Returns a Promise
   */
  ExecuteBuilder.prototype.executeAsServer = function () {
    return Command.executeAsServer(this.build());
  }

  /**
   * Build and execute as caller
   * Returns a Promise
   */
  ExecuteBuilder.prototype.executeCaller = function () {
    return Command.execute(this.build());
  }

  // Return CommandHelpers as the exported API
  return CommandHelpers;
})();
