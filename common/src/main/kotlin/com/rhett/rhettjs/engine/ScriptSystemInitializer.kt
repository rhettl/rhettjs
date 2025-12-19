package com.rhett.rhettjs.engine

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.api.StructureAPI
import com.rhett.rhettjs.api.StructureAPIWrapper
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.events.ServerEventsAPI
import com.rhett.rhettjs.events.StartupEventsAPI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Common initialization logic for the script system.
 * Used by both Fabric and NeoForge to avoid code duplication.
 */
object ScriptSystemInitializer {

    /**
     * Initialize the script system on server start.
     *
     * @param serverDirectory The server's root directory
     */
    fun initialize(serverDirectory: Path) {
        ConfigManager.debug("Server starting, initializing script system")

        val scriptsDir = getScriptsDirectory(serverDirectory)
        ConfigManager.debug("Script directory: $scriptsDir")

        // Ensure script directories exist
        createDirectories(scriptsDir)

        // Initialize Structure API
        initializeStructureAPI(serverDirectory)

        // Scan for scripts
        RhettJSCommon.LOGGER.info("[RhettJS] Scanning for scripts...")
        ScriptRegistry.scan(scriptsDir)

        // Load global libraries
        GlobalsLoader.reload(scriptsDir)
        ConfigManager.debug("Loaded global libraries")

        // Execute startup scripts
        executeStartupScripts()

        // Load server scripts (register event handlers)
        loadServerScripts()

        RhettJSCommon.LOGGER.info("[RhettJS] Ready! Use /rjs list to see available scripts")
        ConfigManager.debug("Script system initialization complete")
    }

    /**
     * Reload all scripts (used by /rjs reload command).
     *
     * @param serverDirectory The server's root directory
     */
    fun reload(serverDirectory: Path) {
        val scriptsDir = getScriptsDirectory(serverDirectory)

        // Clear all event handlers and globals
        RhettJSCommon.LOGGER.info("[RhettJS] Clearing event handlers...")
        StartupEventsAPI.clear()
        ServerEventsAPI.clear()
        GlobalsLoader.clear()

        // Rescan all scripts
        RhettJSCommon.LOGGER.info("[RhettJS] Rescanning scripts...")
        ScriptRegistry.scan(scriptsDir)

        // Reload globals
        RhettJSCommon.LOGGER.info("[RhettJS] Reloading globals...")
        GlobalsLoader.reload(scriptsDir)

        // Re-execute startup scripts
        executeStartupScripts()

        // Reload server scripts (re-register event handlers)
        loadServerScripts()
    }

    /**
     * Get the scripts directory, checking for testing mode.
     */
    private fun getScriptsDirectory(serverDirectory: Path): Path {
        val baseScriptsDir = serverDirectory.resolve("rjs")

        // Check if in-game testing mode is enabled
        return if (ConfigManager.isIngameTestingEnabled()) {
            val testingDir = baseScriptsDir.resolve("testing")
            if (testingDir.exists()) {
                RhettJSCommon.LOGGER.info("[RhettJS] In-game testing mode enabled, using: rjs/testing/")
                ConfigManager.debug("Testing directory exists at: $testingDir")
                testingDir
            } else {
                RhettJSCommon.LOGGER.warn("[RhettJS] In-game testing mode enabled but rjs/testing/ not found, using default: rjs/")
                ConfigManager.debug("Testing directory not found, falling back to: $baseScriptsDir")
                baseScriptsDir
            }
        } else {
            baseScriptsDir
        }
    }

    /**
     * Initialize Structure API.
     */
    private fun initializeStructureAPI(serverDirectory: Path) {
        val structuresDir = serverDirectory.resolve("structures")
        val structureBackupsDir = serverDirectory.resolve("backups/structures")
        Files.createDirectories(structuresDir)
        Files.createDirectories(structureBackupsDir)
        val structureApi = StructureAPI(structuresDir, structureBackupsDir)
        val structureWrapper = StructureAPIWrapper(structureApi)

        // Register Structure API globally so it's available in all script contexts
        ScriptEngine.initializeStructureAPI(structureWrapper)
        ConfigManager.debug("Initialized Structure API with paths: structures=$structuresDir, backups=$structureBackupsDir")
    }

    /**
     * Execute startup scripts.
     */
    private fun executeStartupScripts() {
        val startupScripts = ScriptRegistry.getScripts(ScriptCategory.STARTUP)
        if (startupScripts.isNotEmpty()) {
            RhettJSCommon.LOGGER.info("[RhettJS] Executing ${startupScripts.size} startup scripts...")
            startupScripts.forEach { script ->
                try {
                    ScriptEngine.executeScript(script)
                    ConfigManager.debug("Executed startup script: ${script.name}")
                } catch (e: Exception) {
                    RhettJSCommon.LOGGER.error("[RhettJS] Failed to execute startup script: ${script.name}", e)
                }
            }
        }

        ConfigManager.debug("Startup handlers registered")
    }

    /**
     * Load server scripts (register event handlers).
     */
    private fun loadServerScripts() {
        val serverScripts = ScriptRegistry.getScripts(ScriptCategory.SERVER)
        if (serverScripts.isNotEmpty()) {
            RhettJSCommon.LOGGER.info("[RhettJS] Loading ${serverScripts.size} server scripts...")
            serverScripts.forEach { script ->
                try {
                    ScriptEngine.executeScript(script)
                    ConfigManager.debug("Loaded server script: ${script.name}")
                } catch (e: Exception) {
                    RhettJSCommon.LOGGER.error("[RhettJS] Failed to load server script: ${script.name}", e)
                }
            }
        }
    }

    /**
     * Create script category directories.
     */
    private fun createDirectories(baseDir: Path) {
        ScriptCategory.values().forEach { category ->
            val dir = baseDir.resolve(category.dirName)
            if (!dir.exists()) {
                Files.createDirectories(dir)
                RhettJSCommon.LOGGER.info("[RhettJS] Created directory: ${category.dirName}/")
                ConfigManager.debug("Created script directory: ${category.dirName}")
            }
        }
    }
}