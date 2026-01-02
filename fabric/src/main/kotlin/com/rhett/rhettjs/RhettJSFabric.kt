package com.rhett.rhettjs

import com.rhett.rhettjs.commands.RJSCommand
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.engine.ScriptSystemInitializer
import com.rhett.rhettjs.engine.GraalEngine
import com.rhett.rhettjs.threading.TickScheduler
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager

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

        // Load startup scripts early (dimensions via rjs/data/ datapack)
        ScriptSystemInitializer.initializeStartupScripts()

        // Register reload listener for SERVER scripts (executes on initial load + /reload)
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(object : SimpleSynchronousResourceReloadListener {
            override fun onResourceManagerReload(manager: ResourceManager) {
                RhettJSCommon.LOGGER.info("[RhettJS] Reloading server scripts (datapack reload)...")
                ScriptSystemInitializer.executeServerScripts()
            }

            override fun getFabricId(): ResourceLocation {
                return ResourceLocation.fromNamespaceAndPath(RhettJSCommon.MOD_ID, "server_scripts")
            }
        })
        ConfigManager.debug("Registered reload listener for server scripts")

        // Register block event handlers
        com.rhett.rhettjs.events.FabricBlockEventHandler.register()
        ConfigManager.debug("Registered block event handlers")

        // Register commands
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            RJSCommand.register(dispatcher)
            ConfigManager.debug("Registered /rjs command")

            // Register custom commands that were registered during script initialization
            GraalEngine.storeCommandDispatcher(dispatcher)
            GraalEngine.getCommandRegistry().registerAll()
            ConfigManager.debug("Registered custom commands from server scripts")
        }

        // Register tick handler for schedule() processing
        ServerTickEvents.END_SERVER_TICK.register { _ ->
            TickScheduler.tick()
        }
        ConfigManager.debug("Registered tick handler for schedule() processing")

        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            ScriptSystemInitializer.initialize(server)
        }

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            ScriptSystemInitializer.reinitializeWithWorldPaths(server)
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
            RhettJSCommon.LOGGER.info("[RhettJS] Shutting down...")
            ConfigManager.debug("Server stopping, cleaning up")
        }

        ConfigManager.debug("RhettJS initialization complete")
    }
}
