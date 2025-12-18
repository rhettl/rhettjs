package com.rhett.rhettjs

import com.rhett.rhettjs.api.StructureAPI
import com.rhett.rhettjs.api.StructureAPIWrapper
import com.rhett.rhettjs.commands.RJSCommand
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.engine.ScriptCategory
import com.rhett.rhettjs.engine.ScriptEngine
import com.rhett.rhettjs.engine.ScriptRegistry
import com.rhett.rhettjs.engine.GlobalsLoader
import com.rhett.rhettjs.events.StartupEventsAPI
import com.rhett.rhettjs.events.ServerEventsAPI
import com.rhett.rhettjs.threading.TickScheduler
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Fabric entrypoint for RhettJS mod.
 */
class RhettJSFabric : ModInitializer {
    override fun onInitialize() {
        RhettJSCommon.init()

        // Load configuration
        val configDir = FabricLoader.getInstance().configDir
        ConfigManager.init(configDir)

        // Check if mod is enabled
        if (!ConfigManager.isEnabled()) {
            RhettJSCommon.LOGGER.warn("[RhettJS] Mod is disabled in config. Scripts will not be loaded.")
            return
        }

        ConfigManager.debug("RhettJS initialization starting")

        // Register command
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            RJSCommand.register(dispatcher)
            ConfigManager.debug("Registered /rjs command")
        }

        // Phase 3: Register tick handler for schedule() processing
        ServerTickEvents.END_SERVER_TICK.register { _ ->
            TickScheduler.tick()
        }
        ConfigManager.debug("Registered tick handler for schedule() processing")

        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            ConfigManager.debug("Server starting, initializing script system")

            // In Minecraft 1.21.1, serverDirectory is a Path
            val scriptsDir = server.serverDirectory.resolve("rjs")
            ConfigManager.debug("Script directory: $scriptsDir")

            // Ensure script directories exist
            createDirectories(scriptsDir)

            // Phase 3: Initialize Structure API
            val structuresDir = server.serverDirectory.resolve("structures")
            val structureBackupsDir = server.serverDirectory.resolve("backups/structures")
            Files.createDirectories(structuresDir)
            Files.createDirectories(structureBackupsDir)
            val structureApi = StructureAPI(structuresDir, structureBackupsDir)
            val structureWrapper = StructureAPIWrapper(structureApi)
            val additionalApis = mapOf("Structure" to structureWrapper)
            ConfigManager.debug("Initialized Structure API with paths: structures=$structuresDir, backups=$structureBackupsDir")

            // Scan for scripts
            RhettJSCommon.LOGGER.info("[RhettJS] Scanning for scripts...")
            ScriptRegistry.scan(scriptsDir)

            // Phase 2: Load global libraries
            GlobalsLoader.reload(scriptsDir)
            ConfigManager.debug("Loaded global libraries")

            // Phase 2: Execute startup scripts (register items, blocks, etc.)
            val startupScripts = ScriptRegistry.getScripts(ScriptCategory.STARTUP)
            if (startupScripts.isNotEmpty()) {
                RhettJSCommon.LOGGER.info("[RhettJS] Executing ${startupScripts.size} startup scripts...")
                startupScripts.forEach { script ->
                    try {
                        ScriptEngine.executeScript(script, additionalApis)
                        ConfigManager.debug("Executed startup script: ${script.name}")
                    } catch (e: Exception) {
                        RhettJSCommon.LOGGER.error("[RhettJS] Failed to execute startup script: ${script.name}", e)
                    }
                }
            }

            // Phase 2: Execute startup registrations
            // Note: In a real implementation, this would pass actual registry contexts
            // For now, we just log that handlers are registered
            ConfigManager.debug("Startup handlers registered")

            // Phase 2: Load server scripts (register event handlers)
            val serverScripts = ScriptRegistry.getScripts(ScriptCategory.SERVER)
            if (serverScripts.isNotEmpty()) {
                RhettJSCommon.LOGGER.info("[RhettJS] Loading ${serverScripts.size} server scripts...")
                serverScripts.forEach { script ->
                    try {
                        ScriptEngine.executeScript(script, additionalApis)
                        ConfigManager.debug("Loaded server script: ${script.name}")
                    } catch (e: Exception) {
                        RhettJSCommon.LOGGER.error("[RhettJS] Failed to load server script: ${script.name}", e)
                    }
                }
            }

            RhettJSCommon.LOGGER.info("[RhettJS] Ready! Use /rjs list to see available scripts")
            ConfigManager.debug("Script system initialization complete")
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
            RhettJSCommon.LOGGER.info("[RhettJS] Shutting down...")
            ConfigManager.debug("Server stopping, cleaning up")
            // Phase 3: Shutdown worker threads
        }

        ConfigManager.debug("RhettJS initialization complete")
    }

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
