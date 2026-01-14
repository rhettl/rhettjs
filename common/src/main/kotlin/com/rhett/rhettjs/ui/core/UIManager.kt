package com.rhett.rhettjs.ui.core

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

/**
 * Global manager for RhettJS UI screens.
 * Handles screen creation, storage, and display.
 */
object UIManager {
    private val screens: MutableMap<String, RhettScreen> = mutableMapOf()
    private val minecraft: Minecraft
        get() = Minecraft.getInstance()

    /**
     * Create a new screen with the given ID.
     *
     * @param id Unique screen identifier
     * @return The created screen
     * @throws IllegalArgumentException if a screen with this ID already exists
     */
    fun createScreen(id: String): RhettScreen {
        if (screens.containsKey(id)) {
            throw IllegalArgumentException("Screen with ID '$id' already exists")
        }
        val screen = RhettScreen(id)
        screens[id] = screen
        return screen
    }

    /**
     * Get an existing screen by ID.
     *
     * @param id The screen ID
     * @return The screen, or null if not found
     */
    fun getScreen(id: String): RhettScreen? {
        return screens[id]
    }

    /**
     * Get or create a screen with the given ID.
     * If the screen exists, returns it. Otherwise, creates a new one.
     *
     * @param id The screen ID
     * @return The screen
     */
    fun getOrCreateScreen(id: String): RhettScreen {
        return screens.getOrPut(id) { RhettScreen(id) }
    }

    /**
     * Remove a screen by ID.
     *
     * @param id The screen ID
     * @return The removed screen, or null if not found
     */
    fun removeScreen(id: String): RhettScreen? {
        val screen = screens.remove(id)
        // If this screen is currently displayed, close it
        if (screen != null && minecraft.screen == screen) {
            minecraft.setScreen(null)
        }
        return screen
    }

    /**
     * Show a screen to the player.
     * This makes the screen the active screen.
     *
     * @param id The screen ID
     * @throws IllegalArgumentException if the screen doesn't exist
     */
    fun showScreen(id: String) {
        val screen = screens[id]
            ?: throw IllegalArgumentException("Screen with ID '$id' does not exist")

        minecraft.execute {
            minecraft.setScreen(screen)
            screen.onShown()
        }
    }

    /**
     * Hide the currently active screen if it's a RhettScreen.
     *
     * @param id Optional screen ID to hide. If provided, only hides if this screen is active.
     */
    fun hideScreen(id: String? = null) {
        minecraft.execute {
            val currentScreen = minecraft.screen
            if (currentScreen is RhettScreen) {
                if (id == null || currentScreen.screenId == id) {
                    minecraft.setScreen(null)
                }
            }
        }
    }

    /**
     * Get the currently active screen if it's a RhettScreen.
     *
     * @return The active RhettScreen, or null
     */
    fun getActiveScreen(): RhettScreen? {
        val screen = minecraft.screen
        return if (screen is RhettScreen) screen else null
    }

    /**
     * Check if a specific screen is currently displayed.
     *
     * @param id The screen ID
     * @return true if the screen is active
     */
    fun isScreenActive(id: String): Boolean {
        val screen = minecraft.screen
        return screen is RhettScreen && screen.screenId == id
    }

    /**
     * Get all registered screens.
     */
    fun getAllScreens(): Map<String, RhettScreen> = screens.toMap()

    /**
     * Clear all screens.
     * Removes all registered screens and closes any active RhettScreen.
     */
    fun clearAll() {
        if (minecraft.screen is RhettScreen) {
            minecraft.setScreen(null)
        }
        screens.clear()
    }
}
