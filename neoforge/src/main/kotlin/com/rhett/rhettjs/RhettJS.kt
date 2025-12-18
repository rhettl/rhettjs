package com.rhett.rhettjs

import com.rhett.rhettjs.api.StructureAPI
import com.rhett.rhettjs.api.StructureAPIWrapper
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.engine.ScriptCategory
import com.rhett.rhettjs.engine.ScriptEngine
import com.rhett.rhettjs.engine.ScriptRegistry
import com.rhett.rhettjs.engine.GlobalsLoader
import com.rhett.rhettjs.threading.TickScheduler
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * NeoForge entrypoint for RhettJS mod.
 */
@Mod(RhettJSCommon.MOD_ID)
class RhettJS(modEventBus: IEventBus) {
    init {
        RhettJSCommon.init()

        // Load configuration
        val configDir = FMLPaths.CONFIGDIR.get()
        ConfigManager.init(configDir)

        // Check if mod is enabled
        if (ConfigManager.isEnabled()) {
            ConfigManager.debug("RhettJS initialization starting")

            // Phase 3: Register tick handler for schedule() processing
            NeoForge.EVENT_BUS.register(TickHandler)
            ConfigManager.debug("Registered tick handler for schedule() processing")

            // Register lifecycle events
            NeoForge.EVENT_BUS.register(LifecycleHandler)
            ConfigManager.debug("Registered lifecycle event handlers")

            ConfigManager.debug("RhettJS initialization complete")
        } else {
            RhettJSCommon.LOGGER.warn("[RhettJS] Mod is disabled in config. Scripts will not be loaded.")
        }
    }

    /**
     * Handles server ticks for schedule() processing.
     */
    object TickHandler {
        @SubscribeEvent
        fun onServerTickPost(event: ServerTickEvent.Post) {
            // Post event fires at the end of the tick
            TickScheduler.tick()
        }
    }

    /**
     * Handles server lifecycle events.
     */
    object LifecycleHandler {
        @SubscribeEvent
        fun onServerStarting(event: ServerStartingEvent) {
            ConfigManager.debug("Server starting, initializing script system")

            val server = event.server
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

        @SubscribeEvent
        fun onServerStopping(event: ServerStoppingEvent) {
            RhettJSCommon.LOGGER.info("[RhettJS] Shutting down...")
            ConfigManager.debug("Server stopping, cleaning up")
            // Phase 3: Shutdown worker threads
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
}
