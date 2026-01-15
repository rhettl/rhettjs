package com.rhett.rhettjs

import com.rhett.rhettjs.commands.RJSCommand
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.engine.ScriptSystemInitializer
import com.rhett.rhettjs.engine.GraalEngine
import com.rhett.rhettjs.threading.TickScheduler
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.AddReloadListenerEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

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

            // Load startup scripts early (dimensions via rjs/data/ datapack)
            ScriptSystemInitializer.initializeStartupScripts()

            // Register network payload handlers
            modEventBus.register(NetworkPayloadHandler)
            ConfigManager.debug("Registered network payload handler")

            // Register block event handlers
            NeoForge.EVENT_BUS.register(com.rhett.rhettjs.events.NeoForgeBlockEventHandler)
            ConfigManager.debug("Registered block event handlers")

            // Register player connection event handlers (Server API)
            NeoForge.EVENT_BUS.register(PlayerEventHandler)
            ConfigManager.debug("Registered player connection event handlers")

            // Register reload listener handler for SERVER scripts
            NeoForge.EVENT_BUS.register(ReloadListenerHandler)
            ConfigManager.debug("Registered reload listener handler")

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
     * Handles datapack reload to rescan all scripts and execute SERVER scripts.
     */
    object ReloadListenerHandler {
        @SubscribeEvent
        fun onAddReloadListeners(event: AddReloadListenerEvent) {
            event.addListener(object : PreparableReloadListener {
                override fun reload(
                    preparationBarrier: PreparableReloadListener.PreparationBarrier,
                    resourceManager: ResourceManager,
                    preparationsProfiler: ProfilerFiller,
                    reloadProfiler: ProfilerFiller,
                    backgroundExecutor: Executor,
                    gameExecutor: Executor
                ): CompletableFuture<Void> {
                    // Execute on the game thread
                    return preparationBarrier.wait(Unit).thenRunAsync({
                        RhettJSCommon.LOGGER.info("[RhettJS] Reloading scripts (datapack reload)...")

                        // Rescan all scripts (including utility scripts) to pick up new/changed files
                        val serverDirectory = FMLPaths.GAMEDIR.get().resolve(".")
                        val scriptsDir = ScriptSystemInitializer.getScriptsDirectory(serverDirectory)
                        com.rhett.rhettjs.engine.ScriptRegistry.scan(scriptsDir)
                        ConfigManager.debug("Rescanned scripts directory on /reload")

                        // Execute SERVER scripts
                        ScriptSystemInitializer.executeServerScripts()
                    }, gameExecutor)
                }
            })
            ConfigManager.debug("Added reload listener for script rescan and server script execution")
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
            ConfigManager.debug("[Commands] RegisterCommandsEvent fired - beginning custom command registration")
            GraalEngine.storeCommandDispatcher(event.dispatcher, event.buildContext)
            ConfigManager.debug("[Commands] Calling registry.registerAll()...")
            GraalEngine.getCommandRegistry().registerAll()
            ConfigManager.debug("[Commands] RegisterCommandsEvent complete")
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

    /**
     * Handles player connection events (Server API).
     */
    object PlayerEventHandler {
        @SubscribeEvent
        fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
            if (event.entity is net.minecraft.server.level.ServerPlayer) {
                val player = event.entity as net.minecraft.server.level.ServerPlayer
                com.rhett.rhettjs.events.ServerEventManager.triggerPlayerJoin(player)
                ConfigManager.debug("Player joined: ${player.name.string}")
            }
        }

        @SubscribeEvent
        fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
            if (event.entity is net.minecraft.server.level.ServerPlayer) {
                val player = event.entity as net.minecraft.server.level.ServerPlayer
                com.rhett.rhettjs.events.ServerEventManager.triggerPlayerLeave(player)
                ConfigManager.debug("Player disconnected: ${player.name.string}")
            }
        }
    }

    /**
     * Handles network payload registration.
     */
    object NetworkPayloadHandler {
        @SubscribeEvent
        fun onRegisterPayloadHandlers(event: RegisterPayloadHandlersEvent) {
            com.rhett.rhettjs.network.NeoForgeNetworkHandler.register(event)
            ConfigManager.debug("Registered RhettJS network payloads")
        }
    }
}
