package com.rhett.rhettjs.engine

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.config.ConfigManager
import java.nio.file.Path

/**
 * Manages server script loading during the data pack creation phase.
 * This follows KubeJS's pattern where server scripts load EARLY (before command registration)
 * via Mixin injection into WorldLoader.PackConfig.
 *
 * Lifecycle:
 * 1. createAndLoad() called from WorldLoaderPackConfigMixin during data pack creation
 * 2. Server scripts execute and register handlers (commands, events, etc.)
 * 3. Later, command registration event fires and commands are registered
 */
object ServerScriptManager {

    private var loadedScriptsPath: Path? = null
    private var hasLoaded = false

    /**
     * Create and load server scripts.
     * Called from WorldLoaderPackConfigMixin during data pack creation.
     * On first load, also loads globals. On reload (after /reload), reuses existing globals.
     *
     * @param scriptsDir The base scripts directory (rjs/)
     */
    fun createAndLoad(scriptsDir: Path) {
        val isReload = hasLoaded

        loadedScriptsPath = scriptsDir
        hasLoaded = true

        if (isReload) {
            RhettJSCommon.LOGGER.info("[RhettJS] Reloading server scripts (data pack reload)...")
            // Clear server event handlers before reload
            com.rhett.rhettjs.events.ServerEventsAPI.clear()
        } else {
            RhettJSCommon.LOGGER.info("[RhettJS] Loading server scripts (data pack creation)...")
        }

        ConfigManager.debug("Server scripts directory: $scriptsDir")

        try {
            // Note: Globals are loaded once at mod init by initializeStartupScripts()
            // On reload, globals are reloaded by /rjs reload command before this runs

            // Load server scripts
            val serverScripts = ScriptRegistry.getScripts(ScriptCategory.SERVER)
            if (serverScripts.isNotEmpty()) {
                RhettJSCommon.LOGGER.info("[RhettJS] Executing ${serverScripts.size} server script(s)...")
                serverScripts.forEach { script ->
                    try {
                        GraalEngine.executeScript(script)
                        ConfigManager.debug("Executed server script: ${script.name}")
                    } catch (e: Exception) {
                        RhettJSCommon.LOGGER.error("[RhettJS] Failed to execute server script: ${script.name}", e)
                    }
                }
                RhettJSCommon.LOGGER.info("[RhettJS] Server scripts loaded successfully")

                // Re-register commands after server scripts have run
                if (isReload) {
                    com.rhett.rhettjs.commands.CustomCommandRegistry.registerCommands()
                }
            } else {
                ConfigManager.debug("No server scripts found")
            }
        } catch (e: Exception) {
            RhettJSCommon.LOGGER.error("[RhettJS] Error loading server scripts", e)
            throw e
        }
    }

    /**
     * Check if server scripts have been loaded.
     */
    fun isLoaded(): Boolean = hasLoaded

    /**
     * Get the path where scripts were loaded from.
     */
    fun getScriptsPath(): Path? = loadedScriptsPath

    /**
     * Reset the manager state (for testing/reloading).
     */
    fun reset() {
        hasLoaded = false
        loadedScriptsPath = null
        ConfigManager.debug("ServerScriptManager reset")
    }
}
