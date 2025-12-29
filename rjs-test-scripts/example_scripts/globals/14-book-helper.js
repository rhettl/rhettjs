/**
 * BookHelper - Create interactive written books with clickable links
 *
 * Creates Minecraft written books with pages containing clickable text that runs commands.
 *
 * Usage:
 *   let book = new BookHelper('Admin Tools', 'RhettJS');
 *   book.addLink('Teleport to Test Platform', '/tp @s 1000 100 1000');
 *   book.addLink('Teleport to Spawn', '/tp @s 0 64 0');
 *   book.addText('Regular text without links');
 *   let giveCommand = book.build();
 *   Command.executeAsServer(giveCommand);
 *
 * Features:
 * - Automatic page breaks when content exceeds page limit
 * - Clickable links that run commands
 * - Custom styling per link (color, bold, etc.)
 * - Table of contents generation
 */

/**
 * Create a new book builder.
 *
 * @param {string} title - Book title
 * @param {string} author - Book author
 * @constructor
 */
function BookHelper(title, author) {
  this.title = title || 'Untitled Book';
  this.author = author || 'Unknown';
  this.pages = [];
  this.currentPage = [];
  this.currentPageLength = 0;
  this.maxPageLength = 256; // Minecraft's max characters per page
}

/**
 * Add a clickable link to the current page.
 *
 * @param {string} text - Display text for the link
 * @param {string} command - Command to run when clicked
 * @param {Object} [options] - Optional styling
 * @param {string} [options.color='blue'] - Text color
 * @param {boolean} [options.underlined=true] - Underline the link
 * @param {string} [options.hoverText] - Tooltip text
 * @param {boolean} [options.bold] - Bold text
 * @param {boolean} [options.italic] - Italic text
 * @returns {BookHelper} this (for chaining)
 *
 * @example
 * book.addLink('Go to spawn', '/tp @s 0 64 0');
 */
BookHelper.prototype.addLink = function(text, command, options) {
  options = options || {};

  // Calculate length with newline
  var linkLength = text.length + 1; // +1 for newline

  // Check if we need a new page
  if (this.currentPageLength + linkLength > this.maxPageLength) {
    this._finalizePage();
  }

  var link = {
    text: text + '\n',
    color: options.color || 'blue',
    underlined: options.underlined !== undefined ? options.underlined : true,
    clickEvent: {
      action: 'run_command',
      value: command
    },
    hoverEvent: {
      action: 'show_text',
      value: options.hoverText || ('Click to run: ' + command)
    }
  };

  // Apply additional styling
  if (options.bold) link.bold = true;
  if (options.italic) link.italic = true;

  this.currentPage.push(link);
  this.currentPageLength += linkLength;

  return this;
};

/**
 * Add regular text (non-clickable) to the current page.
 *
 * @param {string} text - Text content
 * @param {Object} [options] - Optional styling
 * @param {string} [options.color] - Text color
 * @param {boolean} [options.bold] - Bold text
 * @param {boolean} [options.italic] - Italic text
 * @returns {BookHelper} this (for chaining)
 */
BookHelper.prototype.addText = function(text, options) {
  options = options || {};

  var textWithNewline = text + '\n';
  var textLength = textWithNewline.length;

  // Check if we need a new page
  if (this.currentPageLength + textLength > this.maxPageLength) {
    this._finalizePage();
  }

  var textComponent = { text: textWithNewline };

  if (options.color) textComponent.color = options.color;
  if (options.bold) textComponent.bold = true;
  if (options.italic) textComponent.italic = true;

  this.currentPage.push(textComponent);
  this.currentPageLength += textLength;

  return this;
};

/**
 * Add a heading to the current page.
 *
 * @param {string} text - Heading text
 * @returns {BookHelper} this (for chaining)
 */
BookHelper.prototype.addHeading = function(text) {
  return this.addText(text, { bold: true, color: 'dark_blue' });
};

/**
 * Add a divider line to the current page.
 *
 * @returns {BookHelper} this (for chaining)
 */
BookHelper.prototype.addDivider = function() {
  return this.addText('─────────────────', { color: 'gray' });
};

/**
 * Force start a new page.
 *
 * @returns {BookHelper} this (for chaining)
 */
BookHelper.prototype.newPage = function() {
  if (this.currentPage.length > 0) {
    this._finalizePage();
  }
  return this;
};

/**
 * Internal: Finalize the current page and start a new one.
 * @private
 */
BookHelper.prototype._finalizePage = function() {
  if (this.currentPage.length > 0) {
    this.pages.push(JSON.stringify(this.currentPage));
    this.currentPage = [];
    this.currentPageLength = 0;
  }
};

/**
 * Build the book and return a give command string.
 *
 * @param {string} [target='@s'] - Target selector for who receives the book
 * @returns {string} Give command to create the book
 */
BookHelper.prototype.build = function(target) {
  target = target || '@s';

  // Finalize current page if any content exists
  this._finalizePage();

  // Build pages array string
  var pagesStr = this.pages.join(',');

  // Escape quotes in title and author for NBT
  var escapedTitle = this.title.replace(/"/g, '\\"');
  var escapedAuthor = this.author.replace(/"/g, '\\"');

  // Build the give command with NBT
  // Note: The pages need to be raw JSON strings in the NBT
  var command = 'give ' + target + ' minecraft:written_book{' +
    'title:"' + escapedTitle + '",' +
    'author:"' + escapedAuthor + '",' +
    'pages:[' + pagesStr + ']' +
    '}';

  return command;
};

/**
 * Build and give the book to a player.
 *
 * @param {string} [target='@s'] - Target selector
 * @returns {Promise} Promise from Command.executeAsServer
 */
BookHelper.prototype.give = function(target) {
  return Command.executeAsServer(this.build(target));
};
