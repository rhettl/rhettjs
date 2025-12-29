/**
 * Performance measurement utility
 * Provides simple timing for operations with optional mark points
 *
 * Basic usage:
 *   const perf = new Performance();
 *   perf.start();
 *   // ... do work ...
 *   const elapsed = perf.stop();
 *   console.log('Took ' + elapsed + 'ms');
 *
 * Or with auto-formatting:
 *   console.log('Took ' + perf.formatElapsed());
 *
 * With marks (lap timing):
 *   const perf = new Performance();
 *   perf.start();
 *   // ... phase 1 ...
 *   perf.mark('Phase 1 complete');
 *   // ... phase 2 ...
 *   perf.mark('Phase 2 complete');
 *   perf.stop();
 *   console.log(perf.formatMarks());
 *   // Output:
 *   // Phase 1 complete: 150ms (+150ms)
 *   // Phase 2 complete: 320ms (+170ms)
 */

/**
 * Performance measurement class
 */
function Performance() {
  this.startTime = null;
  this.endTime = null;
  this.marks = [];  // Array of {label, time} objects
}

/**
 * Start timing
 */
Performance.prototype.start = function() {
  this.startTime = Date.now();
  this.endTime = null;
  this.marks = [];
};

/**
 * Stop timing and return elapsed milliseconds
 */
Performance.prototype.stop = function() {
  this.endTime = Date.now();
  return this.elapsed();
};

/**
 * Get elapsed time in milliseconds
 * If still running, returns time since start
 */
Performance.prototype.elapsed = function() {
  if (!this.startTime) {
    return 0;
  }

  const end = this.endTime || Date.now();
  return end - this.startTime;
};

/**
 * Format elapsed time as human-readable string
 * Examples: "1.2s", "342ms", "2m 15s"
 */
Performance.prototype.formatElapsed = function() {
  const ms = this.elapsed();

  if (ms < 1000) {
    return ms + 'ms';
  }

  const seconds = Math.floor(ms / 1000);
  const remainingMs = ms % 1000;

  if (seconds < 60) {
    // Under 1 minute - show seconds with 1 decimal
    return (seconds + (remainingMs / 1000)).toFixed(1) + 's';
  }

  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;

  if (minutes < 60) {
    // Under 1 hour - show "Xm Ys"
    return minutes + 'm ' + remainingSeconds + 's';
  }

  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;

  // 1 hour or more - show "Xh Ym"
  return hours + 'h ' + remainingMinutes + 'm';
};

/**
 * Reset timer
 */
Performance.prototype.reset = function() {
  this.startTime = null;
  this.endTime = null;
};

/**
 * Check if timer is running
 */
Performance.prototype.isRunning = function() {
  return this.startTime !== null && this.endTime === null;
};

/**
 * Mark a point in time with a label (like a lap timer)
 * @param label Label for this mark
 * @return Elapsed time since start in milliseconds
 */
Performance.prototype.mark = function(label) {
  if (!this.startTime) {
    this.start();
  }

  const now = Date.now();
  const elapsedSinceStart = now - this.startTime;

  this.marks.push({
    label: label,
    time: now,
    elapsed: elapsedSinceStart
  });

  return elapsedSinceStart;
};

/**
 * Get all marks
 * @return Array of {label, time, elapsed} objects
 */
Performance.prototype.getMarks = function() {
  return this.marks.slice();  // Return copy
};

/**
 * Get the time between two marks (or from start to first mark)
 * @param fromLabel Label of starting mark (or null for start time)
 * @param toLabel Label of ending mark
 * @return Elapsed milliseconds between marks, or null if not found
 */
Performance.prototype.getTimeBetween = function(fromLabel, toLabel) {
  const toMark = this.marks.find(function(m) { return m.label === toLabel; });

  if (!toMark) {
    return null;
  }

  if (!fromLabel) {
    // From start
    return toMark.elapsed;
  }

  const fromMark = this.marks.find(function(m) { return m.label === fromLabel; });

  if (!fromMark) {
    return null;
  }

  return toMark.time - fromMark.time;
};

/**
 * Format all marks as a readable string
 * @return String showing all marks with their times
 */
Performance.prototype.formatMarks = function() {
  if (this.marks.length === 0) {
    return 'No marks';
  }

  const lines = [];
  let prevTime = this.startTime;

  this.marks.forEach(function(mark, index) {
    const sinceStart = mark.elapsed;
    const sincePrev = mark.time - prevTime;

    lines.push(mark.label + ': ' + sinceStart + 'ms (+' + sincePrev + 'ms)');
    prevTime = mark.time;
  });

  return lines.join('\n');
};

// Make available globally
globalThis.Performance = Performance;
