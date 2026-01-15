package com.rhett.rhettjs

import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.engine.ClientScriptInitializer
import com.rhett.rhettjs.events.ClientEventManager
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.lwjgl.glfw.GLFW
import java.util.concurrent.CompletableFuture

/**
 * NeoForge client entrypoint for RhettJS mod.
 * Handles client-side script initialization, F3+T reload, and client events.
 */
@Mod(value = RhettJSCommon.MOD_ID, dist = [Dist.CLIENT])
class RhettJSClient(modEventBus: IEventBus) {

    companion object {
        private var tickCount = 0L
        private val pressedKeys = mutableSetOf<Int>()
    }

    init {
        // Config is already loaded by main entrypoint
        if (ConfigManager.isEnabled()) {
            RhettJSCommon.LOGGER.info("[RhettJS] Client initialization starting")

            // Load client scripts
            ClientScriptInitializer.initializeClientScripts()

            // Set client player reference (will be updated on world join)
            val minecraft = Minecraft.getInstance()
            minecraft.player?.let { player ->
                ClientEventManager.setPlayer(player)
            }

            // Register network payload handlers (client)
            modEventBus.addListener<RegisterPayloadHandlersEvent> { event ->
                com.rhett.rhettjs.network.NeoForgeNetworkHandler.register(event)
                ConfigManager.debug("Registered RhettJS client network payloads")
            }

            // Register F3+T reload listener
            modEventBus.addListener<RegisterClientReloadListenersEvent> { event ->
                event.registerReloadListener { _, _, _, _, _, gameExecutor ->
                    CompletableFuture.runAsync({
                        RhettJSCommon.LOGGER.info("[RhettJS] Resource reload detected (F3+T)")
                        ClientScriptInitializer.reloadClientScripts()
                    }, gameExecutor)
                }
            }

            // Register client event listeners
            NeoForge.EVENT_BUS.register(ClientEventHandlers)

            ConfigManager.debug("Registered client tick and key press events")

            RhettJSCommon.LOGGER.info("[RhettJS] Client initialization complete")
        }
    }

    /**
     * NeoForge client event handlers.
     * Registered with NeoForge.EVENT_BUS.
     */
    object ClientEventHandlers {

        @SubscribeEvent
        fun onClientTick(event: ClientTickEvent.Post) {
            tickCount++
            ClientEventManager.triggerTick(tickCount)

            // Update player reference if it changed
            val minecraft = Minecraft.getInstance()
            minecraft.player?.let { player ->
                ClientEventManager.setPlayer(player)
            }

            // Check for key presses (poll GLFW directly)
            val window = minecraft.window.window
            for (key in 32..348) { // Printable keys + special keys
                val pressed = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS
                if (pressed && !pressedKeys.contains(key)) {
                    // Key just pressed
                    pressedKeys.add(key)
                    val keyName = GLFW.glfwGetKeyName(key, 0) ?: "UNKNOWN"
                    ClientEventManager.triggerKeyPress(keyName.uppercase(), key, 1, 0)
                } else if (!pressed && pressedKeys.contains(key)) {
                    // Key just released
                    pressedKeys.remove(key)
                    val keyName = GLFW.glfwGetKeyName(key, 0) ?: "UNKNOWN"
                    ClientEventManager.triggerKeyPress(keyName.uppercase(), key, 0, 0)
                }
            }
        }
    }
}
