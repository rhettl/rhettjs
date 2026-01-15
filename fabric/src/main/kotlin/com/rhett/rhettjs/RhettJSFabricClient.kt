package com.rhett.rhettjs

import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.engine.ClientScriptInitializer
import com.rhett.rhettjs.events.ClientEventManager
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import org.lwjgl.glfw.GLFW

/**
 * Fabric client entrypoint for RhettJS mod.
 * Handles client-side script initialization, F3+T reload, and client events.
 */
class RhettJSFabricClient : ClientModInitializer {

    private var tickCount = 0L
    private val pressedKeys = mutableSetOf<Int>()

    override fun onInitializeClient() {
        // Config is already loaded by main entrypoint
        if (!ConfigManager.isEnabled()) {
            return
        }

        RhettJSCommon.LOGGER.info("[RhettJS] Client initialization starting")

        // Initialize network packet handlers (client)
        com.rhett.rhettjs.network.FabricNetworkHandler.initializeClient()
        ConfigManager.debug("Initialized client network packet handlers")

        // Load client scripts
        ClientScriptInitializer.initializeClientScripts()

        // Set client player reference (will be updated on world join)
        val minecraft = Minecraft.getInstance()
        minecraft.player?.let { player ->
            ClientEventManager.setPlayer(player)
        }

        // Register F3+T reload listener
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            object : SimpleSynchronousResourceReloadListener {
                override fun onResourceManagerReload(manager: ResourceManager) {
                    RhettJSCommon.LOGGER.info("[RhettJS] Resource reload detected (F3+T)")
                    ClientScriptInitializer.reloadClientScripts()
                }

                override fun getFabricId(): ResourceLocation {
                    //? if >=1.21 {
                    return ResourceLocation.fromNamespaceAndPath(RhettJSCommon.MOD_ID, "client_scripts")
                    //?} else {
                    /*return ResourceLocation(RhettJSCommon.MOD_ID, "client_scripts")
                    *///?}
                }
            }
        )

        // Register client tick events
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            tickCount++
            ClientEventManager.triggerTick(tickCount)

            // Update player reference if it changed
            client.player?.let { player ->
                ClientEventManager.setPlayer(player)
            }

            // Check for key presses (poll GLFW directly)
            val window = client.window.window
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

        ConfigManager.debug("Registered client tick and key press events")

        RhettJSCommon.LOGGER.info("[RhettJS] Client initialization complete")
    }
}
