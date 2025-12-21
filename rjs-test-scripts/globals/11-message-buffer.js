/**
 * MessageBuffer - Thread-safe message buffering for RhettJS
 *
 * Collects messages across different execution contexts (worker threads, main thread)
 * and outputs them all at once when ready. Intelligently routes to chat (for players)
 * or console (for command blocks/server).
 *
 * @example Basic Usage
 * let buffer = new MessageBuffer(Caller);
 * buffer.log('Processing...');
 * buffer.success('Done!');
 * buffer.send();
 *
 * @example Threading Workflow
 * let buffer = new MessageBuffer();  // No caller yet
 *
 * task(function() {
 *     // Worker thread - buffer messages
 *     buffer.log('Working on thread...');
 *
 *     schedule(1, function(buffer) {
 *         // Main thread - set caller and send
 *         buffer.setCaller(Caller);
 *         buffer.success('All done!');
 *         buffer.send();
 *     }, buffer);
 * });
 *
 * @example Multiple Threads
 * let buffer = new MessageBuffer();
 * doWork1(buffer);
 * doWork2(buffer);
 * doWork3(buffer);
 *
 * schedule(1, function(buffer) {
 *     buffer.setCaller(Caller);
 *     buffer.send();  // Send all buffered messages
 * }, buffer);
 */

/**
 * Create a new MessageBuffer.
 *
 * @constructor
 * @param {Object} [caller] - Optional Caller API object. Can be set later with setCaller()
 */
function MessageBuffer(caller) {
    this.messages = [];
    if (caller) {
        this.setCaller(caller);
    } else {
        this.caller = null;
    }
    // Default to global IS_DEBUG, but can be overridden per-instance
    this._debugEnabled = typeof IS_DEBUG !== 'undefined' ? IS_DEBUG : false;
}

/**
 * Set or change the Caller API object.
 * Useful for setting the caller after the buffer has been passed through multiple contexts.
 *
 * @param {Object} caller - The Caller API object
 */
MessageBuffer.prototype.setCaller = function(caller) {
    this.caller = caller;
}

/**
 * Override debug mode for this buffer instance.
 * By default, uses the global IS_DEBUG constant.
 *
 * @param {boolean} enabled - Whether to enable debug messages for this buffer
 */
MessageBuffer.prototype.setDebug = function(enabled) {
    this._debugEnabled = enabled;
}

/**
 * Check if the caller is a player.
 * Returns false for command blocks and server/console.
 *
 * @returns {boolean} True if caller is a player
 */
MessageBuffer.prototype.isPlayer = function() {
    return this.caller && this.caller.isPlayer() || false;
}

/**
 * Add an info message to the buffer.
 *
 * @param {string} message - The message to buffer
 */
MessageBuffer.prototype.log = function(message) {
    this.messages.push({type: 'info', message: message});
}

/**
 * Add a success message (green) to the buffer.
 *
 * @param {string} message - The message to buffer
 */
MessageBuffer.prototype.success = function(message) {
    this.messages.push({type: 'success', message: message});
}

/**
 * Add an error message (red) to the buffer.
 *
 * @param {string} message - The message to buffer
 */
MessageBuffer.prototype.error = function(message) {
    this.messages.push({type: 'error', message: message});
}

/**
 * Add a warning message (yellow) to the buffer.
 *
 * @param {string} message - The message to buffer
 */
MessageBuffer.prototype.warn = function(message) {
    this.messages.push({type: 'warn', message: message});
}

/**
 * Add a debug message to the buffer.
 * Only added if debug mode is enabled (global IS_DEBUG or instance setDebug(true)).
 *
 * @param {string} message - The message to buffer
 */
MessageBuffer.prototype.debug = function(message) {
    if (this._debugEnabled) {
        this.messages.push({type: 'debug', message: message});
    }
}

/**
 * Add a raw tellraw JSON component to the buffer.
 * Supports full Minecraft text component features like click events, hover text, etc.
 *
 * @param {string} message - JSON string representing a text component
 * @example
 * buffer.raw('{"text":"Click me!","color":"aqua","clickEvent":{"action":"run_command","value":"/say hello"}}');
 */
MessageBuffer.prototype.raw = function(message) {
    this.messages.push({type: 'raw', message: message});
}

/**
 * Send buffered messages to the caller (player chat).
 * Lower-level method - use send() for automatic routing.
 * Useful when you want to explicitly send to Caller regardless of player status.
 *
 * @param {boolean} [stopAfterOne] - If true, send only one message. Otherwise send all.
 */
MessageBuffer.prototype.sendCaller = function(stopAfterOne) {
    if (this.messages.length) {
        let next = this.messages.shift();

        switch (next.type) {
            case 'raw':
                this.caller.sendRaw(next.message);
                break;
            case 'success':
                this.caller.sendSuccess(next.message);
                break;
            case 'error':
                this.caller.sendError(next.message);
                break;
            case 'warn':
                this.caller.sendWarning(next.message);
                break;
            case 'debug':
                this.caller.sendInfo('[DEBUG] ' + next.message);
                break;
            default:
                this.caller.sendMessage(next.message);
        }

        if (!stopAfterOne) {
            return this.sendCaller(stopAfterOne);
        }
    }
}

/**
 * Send buffered messages to console.
 * Lower-level method - use send() for automatic routing.
 * Useful when you want to explicitly log to console regardless of caller.
 *
 * @param {boolean} [stopAfterOne] - If true, send only one message. Otherwise send all.
 */
MessageBuffer.prototype.sendConsole = function(stopAfterOne) {
    if (this.messages.length) {
        let next = this.messages.shift();

        switch (next.type) {
            case 'raw':
                console.log(next.message);
                break;
            case 'success':
                console.log(next.message);
                break;
            case 'error':
                console.error(next.message);
                break;
            case 'warn':
                console.warn(next.message);
                break;
            case 'debug':
                console.debug('[DEBUG] ' + next.message);
                break;
            default:
                console.log(next.message);
        }

        if (!stopAfterOne) {
            return this.sendConsole(stopAfterOne);
        }
    }
}

/**
 * Send all buffered messages.
 * Automatically routes to player chat or console based on caller type.
 *
 * Players: Messages sent to their chat via Caller API
 * Command blocks: Messages sent to console (command block output is sandboxed)
 * Server/console: Messages sent to console
 *
 * @param {boolean} [stopAfterOne] - If true, send only the next message. Otherwise send all.
 */
MessageBuffer.prototype.send = function(stopAfterOne) {
    if (this.isPlayer()) {
        return this.sendCaller(stopAfterOne);
    }
    return this.sendConsole(stopAfterOne);
}

/**
 * Clear all buffered messages without sending them.
 * Useful for reusing the same buffer.
 */
MessageBuffer.prototype.clear = function() {
    this.messages = [];
}
