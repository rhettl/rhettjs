package com.rhett.rhettjs

import com.rhett.rhettjs.commands.RJSCommand
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.engine.ScriptSystemInitializer
import com.rhett.rhettjs.engine.GraalEngine
import com.rhett.rhettjs.threading.TickScheduler
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

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

            // Load globals and startup scripts early (dimensions, registries)
            ScriptSystemInitializer.initializeStartupScripts()

            // Register block event handlers
            NeoForge.EVENT_BUS.register(com.rhett.rhettjs.events.NeoForgeBlockEventHandler)
            ConfigManager.debug("Registered block event handlers")

            // Register tick handler for schedule() processing
            NeoForge.EVENT_BUS.register(TickHandler)
            ConfigManager.debug("Registered tick handler for schedule() processing")

            // Register lifecycle events
            NeoForge.EVENT_BUS.register(LifecycleHandler)
            ConfigManager.debug("Registered lifecycle event handlers")

            // Register command handler
            NeoForge.EVENT_BUS.register(CommandHandler)
            ConfigManager.debug("Registered command handler")

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
            TickScheduler.tick()
        }
    }

    /**
     * Handles command registration.
     */
    object CommandHandler {
        @SubscribeEvent
        fun onRegisterCommands(event: RegisterCommandsEvent) {
            RJSCommand.register(event.dispatcher)
            ConfigManager.debug("Registered /rjs command")

            // Register custom commands that were registered during script initialization
            GraalEngine.storeCommandDispatcher(event.dispatcher)
            GraalEngine.getCommandRegistry().registerAll()
            ConfigManager.debug("Registered custom commands from server scripts")
        }
    }

    /**
     * Handles server lifecycle events.
     */
    object LifecycleHandler {
        @SubscribeEvent
        fun onServerStarting(event: ServerStartingEvent) {
            ScriptSystemInitializer.initialize(event.server)
        }

        @SubscribeEvent
        fun onServerStarted(event: ServerStartedEvent) {
            ScriptSystemInitializer.reinitializeWithWorldPaths(event.server)
        }

        @SubscribeEvent
        fun onServerStopping(event: ServerStoppingEvent) {
            RhettJSCommon.LOGGER.info("[RhettJS] Shutting down...")
            ConfigManager.debug("Server stopping, cleaning up")
        }
    }
}
