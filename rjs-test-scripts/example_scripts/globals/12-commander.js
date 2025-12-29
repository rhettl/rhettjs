/**
 * Commander - Command-line argument parser
 *
 * Parses Args into flags and positional arguments.
 *
 * Usage:
 *   let cmd = new Commander();
 *   if (cmd.hasFlag('fix')) { ... }
 *   let filename = cmd.get(0);
 *
 * @example
 * // Args = ['myfile.nbt', '--fix', '--verbose']
 * let cmd = new Commander();
 * cmd.get(0);           // 'myfile.nbt'
 * cmd.hasFlag('fix');   // true
 * cmd.hasFlag('test');  // false
 *
 * @example
 * // Short flags: Args = ['file.nbt', '-fv']
 * let cmd = new Commander();
 * cmd.hasFlag('f');     // true
 * cmd.hasFlag('v');     // true
 */
function Commander(args) {
  this.args = args || (typeof Args !== 'undefined' ? Args : []);
  this.flags = {};
  this.positional = [];

  // Parse args
  this._parse();
}

/**
 * Parse arguments into flags and positional args.
 * Long flags: --fix → flags.fix = true
 * Short flags: -f → flags.f = true
 * Combined short flags: -fgj → flags.f, flags.g, flags.j = true
 * Negative numbers: -100 → positional (not a flag)
 */
Commander.prototype._parse = function() {
  for (let i = 0; i < this.args.length; i++) {
    let arg = this.args[i];

    if (arg.startsWith('--')) {
      // Long flag: --fix
      this.flags[arg.substring(2)] = true;
    } else if (arg.startsWith('-') && arg.length > 1 && isNaN(arg)) {
      // Short flag(s): -f or -fgj
      // But NOT negative numbers like -100
      for (let j = 1; j < arg.length; j++) {
        this.flags[arg[j]] = true;
      }
    } else {
      // Positional argument (including negative numbers)
      this.positional.push(arg);
    }
  }
};

/**
 * Check if a flag is present.
 * @param {string} name - Flag name (without --)
 * @returns {boolean}
 *
 * @example
 * // Args = ['file.nbt', '--fix']
 * cmd.hasFlag('fix');  // true
 *
 * @example
 * // Args = ['file.nbt', '-f']
 * cmd.hasFlag('f');    // true
 */
Commander.prototype.hasFlag = function(name) {
  return this.flags[name] === true;
};

/**
 * Get positional argument by index.
 * @param {number} index - Zero-based index
 * @returns {string|undefined}
 *
 * @example
 * // Args = ['file1.nbt', 'file2.nbt', '--fix']
 * cmd.get(0);  // 'file1.nbt'
 * cmd.get(1);  // 'file2.nbt'
 * cmd.get(2);  // undefined
 */
Commander.prototype.get = function(index) {
  return this.positional[index];
};

/**
 * Get all positional arguments.
 * @returns {Array<string>}
 *
 * @example
 * // Args = ['file1.nbt', 'file2.nbt', '--fix']
 * cmd.getAll();  // ['file1.nbt', 'file2.nbt']
 */
Commander.prototype.getAll = function() {
  return this.positional;
};
