# RhettJS Testing Scripts

This directory contains internal testing scripts used for development and QA.

## Purpose

These scripts are used to test RhettJS features in-game but are **not loaded by default**. This keeps production `rjs/` directories clean while preserving test scripts for development.

## Usage

### Enabling Testing Scripts

1. Open `config/rhettjs.json`
2. Set `"debug_run_ingame_testing": true`
3. Copy this `testing/` directory to your Minecraft instance at `rjs/testing/`
4. Start/restart your server

The mod will automatically load scripts from `rjs/testing/` instead of `rjs/` when:
- `debug_run_ingame_testing` is `true` AND
- `rjs/testing/` directory exists

### Disabling Testing Scripts

Set `"debug_run_ingame_testing": false` in config, or remove the `rjs/testing/` directory.

## Directory Structure

```
testing/
├── globals/          # Global utility libraries
├── scripts/          # Utility scripts (run via /rjs run)
├── server/           # Server event handlers
├── startup/          # Startup initialization scripts
└── README.md         # This file
```

## Test Scripts Included

### Scripts (Utility)
- `smoke-test.js` - Basic API functionality test
- `test-errors.js` - Error handling validation
- `test-performance.js` - Threading system load test
- `detect-painting-issues.js` - Structure file painting offset analyzer

### Server Scripts
- `test-server.js` - Server event handlers test
- `test-threading.js` - Threading system test
- `test-structure.js` - Structure API test
- `test-combined-workflow.js` - End-to-end workflow test
- `test-painting-fixer.js` - Automated painting offset fixer

### Startup Scripts
- `test-startup.js` - Startup phase test

### Globals
- `00-utils.js` - Utility functions
- `01-util-deps.js` - Utility dependencies

## Notes

- **Production Safety**: Test scripts are isolated and won't affect production environments
- **Config Requirement**: Testing mode must be explicitly enabled in config
- **Directory Requirement**: Both config setting AND directory existence required
- **Reload Support**: `/rjs reload` respects the testing mode setting
