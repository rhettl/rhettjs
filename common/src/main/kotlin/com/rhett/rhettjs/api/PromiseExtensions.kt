package com.rhett.rhettjs.api

import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable

/**
 * Injects Promise prototype extensions: thenTask() and thenWait().
 * 
 * These must be injected via JavaScript since they extend Promise.prototype.
 */
object PromiseExtensions {

    /**
     * JavaScript code to add thenTask() and thenWait() to Promise.prototype.
     */
    private val EXTENSIONS_SCRIPT = """
        (function() {
            // thenTask(callback) - chain to worker thread
            // Returns a Promise that resolves with the worker result
            if (!Promise.prototype.thenTask) {
                Promise.prototype.thenTask = function(callback) {
                    return this.then(function(result) {
                        // Don't pass undefined to task() - it would fail validation
                        if (result === undefined) {
                            return task(callback);
                        } else {
                            return task(callback, result);
                        }
                    });
                };
            }

            // thenWait(ticks) - chain with delay, passes result through
            // Returns a Promise that resolves with the same result after N ticks
            if (!Promise.prototype.thenWait) {
                Promise.prototype.thenWait = function(ticks) {
                    var self = this;
                    return this.then(function(result) {
                        return wait(ticks).then(function() {
                            return result;
                        });
                    });
                };
            }
        })();
    """.trimIndent()

    /**
     * Inject Promise extensions into a scope.
     * Should be called after task() and wait() are available in the scope.
     * 
     * @param scope The JavaScript scope to inject into
     */
    fun inject(scope: Scriptable) {
        val cx = Context.getCurrentContext()
        cx.evaluateString(scope, EXTENSIONS_SCRIPT, "promise-extensions", 1, null)
    }
}

