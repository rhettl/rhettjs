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
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
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

        // Initialize network packet handlers
        com.rhett.rhettjs.network.FabricNetworkHandler.initializeServer()
        ConfigManager.debug("Initialized network packet handlers")

        // Load startup scripts early (dimensions via rjs/data/ datapack)
        ScriptSystemInitializer.initializeStartupScripts()

        // Register reload listener to rescan all scripts and execute SERVER scripts on /reload
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(object : SimpleSynchronousResourceReloadListener {
            override fun onResourceManagerReload(manager: ResourceManager) {
                RhettJSCommon.LOGGER.info("[RhettJS] Reloading scripts (datapack reload)...")

                // Rescan all scripts (including utility scripts) to pick up new/changed files
                val serverDirectory = FabricLoader.getInstance().gameDir.resolve(".")
                val scriptsDir = ScriptSystemInitializer.getScriptsDirectory(serverDirectory)
                com.rhett.rhettjs.engine.ScriptRegistry.scan(scriptsDir)
                ConfigManager.debug("Rescanned scripts directory on /reload")

                // Execute SERVER scripts
                ScriptSystemInitializer.executeServerScripts()
            }

            override fun getFabricId(): ResourceLocation {
                return ResourceLocation.fromNamespaceAndPath(RhettJSCommon.MOD_ID, "server_scripts")
            }
        })
        ConfigManager.debug("Registered reload listener for script rescan and server script execution")

        // Register block event handlers
        com.rhett.rhettjs.events.FabricBlockEventHandler.register()
        ConfigManager.debug("Registered block event handlers")

        // Register player connection event handlers (Server API)
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            com.rhett.rhettjs.events.ServerEventManager.triggerPlayerJoin(handler.player)
            ConfigManager.debug("Player joined: ${handler.player.name.string}")
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            com.rhett.rhettjs.events.ServerEventManager.triggerPlayerLeave(handler.player)
            ConfigManager.debug("Player disconnected: ${handler.player.name.string}")
        }
        ConfigManager.debug("Registered player connection event handlers")

        // Register commands
        CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, environment ->
            RJSCommand.register(dispatcher)
            ConfigManager.debug("Registered /rjs command")

            // Register custom commands that were registered during script initialization
            ConfigManager.debug("[Commands] CommandRegistrationCallback fired - beginning custom command registration")
            GraalEngine.storeCommandDispatcher(dispatcher, net.minecraft.commands.Commands.createValidationContext(registryAccess))
            ConfigManager.debug("[Commands] Calling registry.registerAll()...")
            GraalEngine.getCommandRegistry().registerAll()
            ConfigManager.debug("[Commands] CommandRegistrationCallback complete")
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
