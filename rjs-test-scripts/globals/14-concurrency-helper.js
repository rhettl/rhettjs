// RhettJS Concurrency Helper
// Polyfill and utilities for Promise-based async operations
// Compatible with Rhino 1.8.1 + RhettJS task/wait APIs
//
// Usage:
//   Import this in globals/ to make it available everywhere
//   Or require it in scripts that need async utilities
//
// Features:
//   - Promise.all() polyfill
//   - Promise.race() polyfill
//   - Promise.allSettled() polyfill
//   - Promise.sequence() - run promises in order
//   - Async.parallel() - limit concurrent operations
//   - Async.retry() - retry failed operations with backoff
//   - Async.timeout() - add timeout to promises
//   - Async.batch() - batch processing with concurrency
//   - Async.map() - serial async mapping
//   - Async.mapParallel() - parallel async mapping
//   - Async.filter() - async filtering

const Async = (function(global) {
  'use strict';

  // ============================================================================
  // Promise Polyfills (if needed for Rhino 1.8.1)
  // ============================================================================

  // Check if Promise already exists with all methods
  if (typeof Promise === 'undefined') {
    throw new Error('Promise not available in this Rhino version');
  }

  // Polyfill Promise.all if missing
  if (!Promise.all) {
    Promise.all = function(promises) {
      return new Promise(function(resolve, reject) {
        if (!Array.isArray(promises)) {
          return reject(new TypeError('Promise.all requires an array'));
        }

        var results = [];
        var remaining = promises.length;

        if (remaining === 0) {
          return resolve(results);
        }

        promises.forEach(function(promise, index) {
          Promise.resolve(promise).then(
            function(value) {
              results[index] = value;
              remaining--;
              if (remaining === 0) {
                resolve(results);
              }
            },
            function(error) {
              reject(error);
            }
          );
        });
      });
    };
  }

  // Polyfill Promise.race if missing
  if (!Promise.race) {
    Promise.race = function(promises) {
      return new Promise(function(resolve, reject) {
        if (!Array.isArray(promises)) {
          return reject(new TypeError('Promise.race requires an array'));
        }

        promises.forEach(function(promise) {
          Promise.resolve(promise).then(resolve, reject);
        });
      });
    };
  }

  // Polyfill Promise.allSettled if missing
  if (!Promise.allSettled) {
    Promise.allSettled = function(promises) {
      return Promise.all(
        promises.map(function(promise) {
          return Promise.resolve(promise)
            .then(function(value) {
              return { status: 'fulfilled', value: value };
            })
            .catch(function(reason) {
              return { status: 'rejected', reason: reason };
            });
        })
      );
    };
  }

  // Add Promise.sequence - run promises in order
  if (!Promise.sequence) {
    Promise.sequence = function(promiseFactories) {
      return promiseFactories.reduce(function(chain, factory) {
        return chain.then(function(results) {
          return factory().then(function(result) {
            results.push(result);
            return results;
          });
        });
      }, Promise.resolve([]));
    };
  }

  // ============================================================================
  // Async Utilities for RhettJS
  // ============================================================================

  let Async = {
    /**
     * Maximum number of worker threads available.
     * Determined at boot time based on CPU cores (max 4).
     * Use this to set realistic concurrency limits.
     */
    maxWorkerThreads: (typeof Runtime !== 'undefined' && Runtime.env)
      ? Runtime.env.MAX_WORKER_THREADS
      : 4,

    /**
     * Run promises with limited concurrency
     * @param {Function[]} promiseFactories - Array of functions that return promises
     * @param {number} maxConcurrent - Maximum concurrent operations
     * @returns {Promise<Array>} Promise that resolves with all results
     *
     * @example
     * const files = ["file1", "file2", "file3"];
     * const results = await Async.parallel(
     *   files.map(file => () => Async.promisify(() => Structure.read(file))),
     *   2 // Max 2 concurrent reads
     * );
     */
    parallel: function(promiseFactories, maxConcurrent) {
      maxConcurrent = maxConcurrent || 5;

      return new Promise(function(resolve, reject) {
        var results = [];
        var errors = [];
        var index = 0;
        var running = 0;
        var completed = 0;

        function runNext() {
          if (index >= promiseFactories.length) {
            // No more tasks to start
            if (completed === promiseFactories.length) {
              // All done
              if (errors.length > 0) {
                reject(errors);
              } else {
                resolve(results);
              }
            }
            return;
          }

          var currentIndex = index++;
          running++;

          promiseFactories[currentIndex]()
            .then(function(result) {
              results[currentIndex] = result;
            })
            .catch(function(error) {
              errors[currentIndex] = error;
            })
            .finally(function() {
              running--;
              completed++;
              runNext();
            });

          if (running < maxConcurrent) {
            runNext();
          }
        }

        // Start initial batch
        for (var i = 0; i < Math.min(maxConcurrent, promiseFactories.length); i++) {
          runNext();
        }
      });
    },

    /**
     * Retry a promise operation with exponential backoff
     * @param {Function} promiseFactory - Function that returns a promise
     * @param {Object} options - Retry options
     * @returns {Promise} Promise that resolves with result or rejects after retries
     *
     * @example
     * const data = await Async.retry(
     *   () => task(() => Structure.read("structure")),
     *   { retries: 3, delay: 20 }
     * );
     */
    retry: function(promiseFactory, options) {
      options = options || {};
      var retries = options.retries || 3;
      var delayTicks = options.delay || 20; // ticks
      var backoff = options.backoff || 2;

      function attempt(retriesLeft) {
        return promiseFactory().catch(function(error) {
          if (retriesLeft <= 0) {
            throw error;
          }

          var currentDelay = delayTicks * Math.pow(backoff, retries - retriesLeft);
          return wait(currentDelay).then(function() {
            return attempt(retriesLeft - 1);
          });
        });
      }

      return attempt(retries);
    },

    /**
     * Add a timeout to a promise
     * @param {Promise} promise - Promise to wrap
     * @param {number} ticks - Timeout in ticks
     * @param {string} message - Optional timeout message
     * @returns {Promise} Promise that rejects if timeout occurs
     *
     * @example
     * const data = await Async.timeout(
     *   task(() => Structure.read("structure")),
     *   100, // 5 seconds
     *   "Structure read timed out"
     * );
     */
    timeout: function(promise, ticks, message) {
      var timeoutPromise = wait(ticks).then(function() {
        throw new Error(message || 'Operation timed out after ' + ticks + ' ticks');
      });

      return Promise.race([promise, timeoutPromise]);
    },

    /**
     * Batch process items with a worker function
     * @param {Array} items - Items to process
     * @param {Function} worker - Async function to process each item
     * @param {Object} options - Batch options
     * @returns {Promise<Array>} Results array
     *
     * @example
     * const structures = Structure.list();
     * const results = await Async.batch(
     *   structures,
     *   (name) => Async.promisify(() => Structure.read(name)),
     *   { concurrency: 3, batchSize: 10 }
     * );
     */
    batch: function(items, worker, options) {
      options = options || {};
      var concurrency = options.concurrency || 5;
      var batchSize = options.batchSize || items.length;

      var batches = [];
      for (var i = 0; i < items.length; i += batchSize) {
        batches.push(items.slice(i, i + batchSize));
      }

      return Promise.sequence(
        batches.map(function(batch) {
          return function() {
            return Async.parallel(
              batch.map(function(item) {
                return function() {
                  return worker(item);
                };
              }),
              concurrency
            );
          };
        })
      ).then(function(batchResults) {
        // Flatten results
        return batchResults.reduce(function(acc, batch) {
          return acc.concat(batch);
        }, []);
      });
    },

    /**
     * Map over array with async function (serial)
     * @param {Array} items - Items to map
     * @param {Function} mapper - Async mapping function
     * @returns {Promise<Array>} Mapped results
     *
     * @example
     * const structures = ["house1", "house2", "house3"];
     * const data = await Async.map(structures, (name) =>
     *   Async.promisify(() => Structure.read(name))
     * );
     */
    map: function(items, mapper) {
      return Promise.sequence(
        items.map(function(item, index) {
          return function() {
            return mapper(item, index);
          };
        })
      );
    },

    /**
     * Map over array with async function (parallel)
     * @param {Array} items - Items to map
     * @param {Function} mapper - Async mapping function
     * @param {number} concurrency - Max concurrent operations
     * @returns {Promise<Array>} Mapped results
     *
     * @example
     * const structures = Structure.list();
     * const data = await Async.mapParallel(
     *   structures,
     *   (name) => Async.promisify(() => Structure.read(name)),
     *   5
     * );
     */
    mapParallel: function(items, mapper, concurrency) {
      return Async.parallel(
        items.map(function(item, index) {
          return function() {
            return mapper(item, index);
          };
        }),
        concurrency
      );
    },

    /**
     * Filter array with async predicate (serial)
     * @param {Array} items - Items to filter
     * @param {Function} predicate - Async predicate function
     * @returns {Promise<Array>} Filtered items
     *
     * @example
     * const structures = Structure.list();
     * const withPaintings = await Async.filter(structures, (name) =>
     *   Async.promisify(() => {
     *     const data = Structure.read(name);
     *     return data && data.entities && data.entities.some(e =>
     *       e.nbt && e.nbt.id === 'minecraft:painting'
     *     );
     *   })
     * );
     */
    filter: function(items, predicate) {
      return Async.map(items, function(item, index) {
        return predicate(item, index).then(function(result) {
          return { item: item, keep: result };
        });
      }).then(function(results) {
        return results
          .filter(function(r) { return r.keep; })
          .map(function(r) { return r.item; });
      });
    }
  };

  // ============================================================================
  // Export
  // ============================================================================

  // Make Async available globally
  // global.Async = Async;
  return Async;

})();


// ============================================================================
// Usage Examples
// ============================================================================

/*
// Example 1: Read multiple structures in parallel using task()
const structures = Structure.list();
const results = Async.mapParallel(
  structures.slice(0, 10),
  (name) => task((n) => Structure.read(n), name),
  3 // Max 3 concurrent reads
).then(results => {
  console.log("Read " + results.length + " structures");
});

// Example 2: Sequential processing with wait()
task(() => console.log("Step 1"))
  .thenWait(20)  // Wait 1 second
  .then(() => console.log("Step 2"))
  .thenWait(20)
  .then(() => console.log("Step 3"));

// Example 3: Batch processing
const allStructures = Structure.list();
Async.batch(
  allStructures,
  (name) => task((n) => {
    const data = Structure.read(n);
    return data;
  }, name),
  { concurrency: 5, batchSize: 20 }
).then(results => {
  console.log("Processed " + results.length + " structures");
});

// Example 4: Retry with timeout
Async.timeout(
  Async.retry(
    () => task((n) => Structure.read(n), "unreliable_structure"),
    { retries: 3, delay: 10 }
  ),
  200, // 10 second timeout
  "Structure read failed"
).then(data => {
  console.log("Got data:", data);
}).catch(err => {
  console.error("Failed:", err);
});

// Example 5: Filter structures with paintings
Async.filter(
  Structure.list(),
  (name) => task((n) => {
    const data = Structure.read(n);
    return data && data.entities && data.entities.some(entity =>
      entity.nbt && entity.nbt.id === 'minecraft:painting'
    );
  }, name)
).then(structuresWithPaintings => {
  console.log("Found " + structuresWithPaintings.length + " with paintings");
});

// Example 6: Using thenTask for chained worker operations
task((x) => x * 2, 10)
  .thenTask((x) => x + 5)
  .thenWait(10)
  .then(result => console.log("Result:", result)); // 25
*/