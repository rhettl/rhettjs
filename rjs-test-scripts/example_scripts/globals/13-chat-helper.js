/**
 * ChatHelper - Interactive chat message builder
 *
 * Create clickable buttons and build interactive messages with button replacements.
 *
 * Usage:
 *   let btn = ChatHelper.button('[Click]', '/command');
 *   let json = ChatHelper.replace('Text [Click] here', [btn]);
 *   Caller.sendRaw(json);
 *
 * Or with MessageBuffer:
 *   buffer.raw(ChatHelper.replace('Text [Click]', [btn]));
 *   buffer.send();
 *
 * Convenience alias:
 *   let c = ChatHelper;
 *   c.button('[Scan]', '/cmd');
 */
const ChatHelper = {};

/**
 * Create a clickable chat button.
 *
 * @param {string} label - Button text (e.g., '[Scan]', '<Fix>', '{Go}')
 * @param {string} command - Command to execute when clicked
 * @param {Object} [options] - Optional Minecraft text component properties
 * @param {string} [options.color='aqua'] - Text color
 * @param {string} [options.clickAction='run_command'] - Click action type
 * @param {string} [options.hoverText] - Hover tooltip text
 * @param {boolean} [options.bold] - Bold text
 * @param {boolean} [options.italic] - Italic text
 * @param {boolean} [options.underlined] - Underlined text
 * @param {boolean} [options.strikethrough] - Strikethrough text
 * @param {boolean} [options.obfuscated] - Obfuscated text
 * @returns {Object} Minecraft text component with .label property for replacement
 *
 * @example
 * // Simple button
 * ChatHelper.button('[Scan]', '/rjs run detect file')
 *
 * @example
 * // Styled button with tooltip
 * ChatHelper.button('[Fix]', '/rjs run detect file --fix', {
 *   color: 'green',
 *   bold: true,
 *   hoverText: 'Click to fix all issues'
 * })
 *
 * @example
 * // Suggest command instead of running it
 * ChatHelper.button('[?]', '/help detect', {
 *   clickAction: 'suggest_command',
 *   hoverText: 'Get help with detect command'
 * })
 */
ChatHelper.button = function(label, command, options) {
  options = options || {};

  let button = {
    label: label, // Used by replace() to find where to insert this button
    text: label,
    color: options.color || 'aqua',
    clickEvent: {
      action: options.clickAction || 'run_command',
      value: command
    },
    hoverEvent: {
      action: 'show_text',
      value: options.hoverText || ('Click to ' + label)
    }
  };

  // Allow any extra Minecraft text component properties to pass through
  // (bold, italic, underlined, strikethrough, obfuscated, insertion, font, etc.)
  for (let key in options) {
    if (key !== 'color' && key !== 'clickAction' && key !== 'hoverText') {
      button[key] = options[key];
    }
  }

  return button;
};

/**
 * Build an interactive chat message by replacing button labels in text.
 * Buttons are inserted by finding their labels in the text (left-to-right).
 *
 * @param {string} text - Message template with button labels to replace
 * @param {Array<Object>} buttons - Array of button objects (must have .label property)
 * @param {Function} [partsMapper] - Optional function to transform parts array before JSON stringify
 * @returns {string} JSON string for Caller.sendRaw() or MessageBuffer.raw()
 *
 * @example
 * // Simple usage
 * let msg = ChatHelper.replace('Click [Here] to continue', [
 *   ChatHelper.button('[Here]', '/next')
 * ]);
 * Caller.sendRaw(msg);
 *
 * @example
 * // Multiple buttons
 * let msg = ChatHelper.replace('Issues found [Scan] or [Fix]', [
 *   ChatHelper.button('[Scan]', '/rjs run detect file'),
 *   ChatHelper.button('[Fix]', '/rjs run detect file --fix', { color: 'green' })
 * ]);
 *
 * @example
 * // Multiple buttons with same label (left-to-right replacement)
 * let msg = ChatHelper.replace('Compare [File] vs [File]', [
 *   ChatHelper.button('[File]', '/show file1'),
 *   ChatHelper.button('[File]', '/show file2')
 * ]);
 *
 * @example
 * // With MessageBuffer
 * let buffer = new MessageBuffer(Caller);
 * buffer.log('Scanning complete.');
 * buffer.raw(ChatHelper.replace('Found issues [Scan] [Fix]', [scanBtn, fixBtn]));
 * buffer.send();
 *
 * @example
 * // With parts mapper to add default styling
 * let msg = ChatHelper.replace('§aGreen text [Button]', [btn], function(part) {
 *   // Add gray color to plain text parts
 *   if (part.text && !part.clickEvent) {
 *     part.color = part.color || 'gray';
 *   }
 *   return part;
 * });
 *
 * @example
 * // Any label format works (not just [...])
 * let msg = ChatHelper.replace('Use <brackets> or {braces} or **stars**', [
 *   ChatHelper.button('<brackets>', '/cmd1'),
 *   ChatHelper.button('{braces}', '/cmd2'),
 *   ChatHelper.button('**stars**', '/cmd3')
 * ]);
 */
ChatHelper.replace = function(text, buttons, partsMapper) {
  let parts = [];
  let remaining = text;

  buttons.forEach(function(button) {
    let idx = remaining.indexOf(button.label);

    if (idx !== -1) {
      // Add text before the button
      if (idx > 0) {
        parts.push({ text: remaining.substring(0, idx) });
      }

      // Add the button (without the .label property - Minecraft doesn't need it)
      let buttonCopy = {};
      for (let key in button) {
        if (key !== 'label') {
          buttonCopy[key] = button[key];
        }
      }
      parts.push(buttonCopy);

      // Update remaining text (everything after this button label)
      remaining = remaining.substring(idx + button.label.length);
    }
  });

  // Add any remaining text after all buttons
  if (remaining) {
    parts.push({ text: remaining });
  }

  // Apply mapper if provided
  if (partsMapper) {
    parts = parts.map(partsMapper);
  }

  return JSON.stringify(parts);
};
