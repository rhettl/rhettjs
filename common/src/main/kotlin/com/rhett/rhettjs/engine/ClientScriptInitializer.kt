package com.rhett.rhettjs.engine

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.config.FilesystemInitializer
import java.nio.file.Path

/**
 * Client-side script initialization.
 * Handles CLIENT category scripts that run on the Minecraft client.
 *
 * This is parallel to ScriptSystemInitializer but for client-only execution.
 * Client scripts have access to UI + universal APIs (Store, NBT, StructureNbt, Runtime, Console).
 */
object ClientScriptInitializer {

    /**
     * Initialize client scripts during client startup.
     * Called from Fabric/NeoForge client entrypoints.
     */
    fun initializeClientScripts() {
        RhettJSCommon.LOGGER.info("[RhettJS] Loading client scripts...")

        val scriptsDir = getClientScriptsDirectory()
        ConfigManager.debug("Client script directory: $scriptsDir")

        // Initialize filesystem (creates directories, extracts type definitions)
        FilesystemInitializer.initialize(scriptsDir)

        // Set scripts directory for module resolution
        GraalEngine.setScriptsDirectory(scriptsDir)

        // Scan for CLIENT scripts
        RhettJSCommon.LOGGER.info("[RhettJS] Scanning for client scripts...")
        ScriptRegistry.scan(scriptsDir)

        // Execute CLIENT scripts
        executeClientScripts()

        RhettJSCommon.LOGGER.info("[RhettJS] Client scripts initialized")
    }

    /**
     * Execute all CLIENT category scripts.
     */
    fun executeClientScripts() {
        val clientScripts = ScriptRegistry.getScripts(ScriptCategory.CLIENT)
        if (clientScripts.isEmpty()) {
            ConfigManager.debug("No client scripts to execute")
            return
        }

        RhettJSCommon.LOGGER.info("[RhettJS] Executing ${clientScripts.size} client scripts...")
        clientScripts.forEach { script ->
            try {
                val result = GraalEngine.executeScript(script)
                when (result) {
                    is ScriptResult.Success -> ConfigManager.debug("Executed client script: ${script.name}")
                    is ScriptResult.Error -> {
                        RhettJSCommon.LOGGER.error("[RhettJS] Client script failed: ${script.name} - ${result.message}")
                        ScriptRegistry.markFailed(script.name, result.exception ?: RuntimeException(result.message))
                    }
                }
            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[RhettJS] Failed to execute client script: ${script.name}", e)
                ScriptRegistry.markFailed(script.name, e)
            }
        }

        ConfigManager.debug("Client scripts complete")
    }

    /**
     * Reload client scripts (F3+T resource reload).
     */
    fun reloadClientScripts() {
        RhettJSCommon.LOGGER.info("[RhettJS] Reloading client scripts...")

        val scriptsDir = getClientScriptsDirectory()

        // Reset GraalVM context (client runs in separate JVM, has own context)
        GraalEngine.reset()

        // Rescan scripts
        ScriptRegistry.scan(scriptsDir)

        // Re-execute CLIENT scripts
        executeClientScripts()

        RhettJSCommon.LOGGER.info("[RhettJS] Client scripts reloaded")
    }

    /**
     * Get scripts directory for client.
     * Client scripts are in game directory: <minecraft>/rjs/
     * (Not world directory like server scripts)
     */
    private fun getClientScriptsDirectory(): Path {
        // Use reflection to get Minecraft instance to avoid client-only class dependencies
        // This allows this file to compile in common module
        val minecraftClass = Class.forName("net.minecraft.client.Minecraft")
        val getInstanceMethod = minecraftClass.getMethod("getInstance")
        val minecraftInstance = getInstanceMethod.invoke(null)
        val gameDirectoryField = minecraftClass.getField("gameDirectory")
        val gameDir = (gameDirectoryField.get(minecraftInstance) as java.io.File).toPath()

        val baseScriptsDir = gameDir.resolve("rjs")

        // Check for testing mode
        return if (ConfigManager.isIngameTestingEnabled()) {
            val testingDir = baseScriptsDir.resolve("testing")
            if (java.nio.file.Files.exists(testingDir)) {
                RhettJSCommon.LOGGER.info("[RhettJS] Client testing mode: using rjs/testing/")
                testingDir
            } else {
                baseScriptsDir
            }
        } else {
            baseScriptsDir
        }
    }
}
