package com.rhett.rhettjs.ui.core

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.graalvm.polyglot.Value

/**
 * A screen that can contain and manage RhettJS widgets.
 * This is the main container for UI elements created from JavaScript.
 */
class RhettScreen(val screenId: String) : Screen(Component.literal(screenId)) {
    private val widgets: MutableList<RhettWidget> = mutableListOf()
    private val widgetsById: MutableMap<String, RhettWidget> = mutableMapOf()
    private val callbacks: MutableMap<String, Value> = mutableMapOf()

    /**
     * Add a widget to this screen.
     *
     * @param widget The widget to add
     * @throws IllegalArgumentException if a widget with the same ID already exists
     */
    fun addWidget(widget: RhettWidget) {
        if (widgetsById.containsKey(widget.id)) {
            throw IllegalArgumentException("Widget with ID '${widget.id}' already exists")
        }
        widgets.add(widget)
        widgetsById[widget.id] = widget
    }

    /**
     * Remove a widget by ID.
     *
     * @param widgetId The ID of the widget to remove
     * @return The removed widget, or null if not found
     */
    fun removeWidget(widgetId: String): RhettWidget? {
        val widget = widgetsById.remove(widgetId) ?: return null
        widgets.remove(widget)
        widget.onRemoved()
        return widget
    }

    /**
     * Get a widget by ID.
     *
     * @param widgetId The widget ID
     * @return The widget, or null if not found
     */
    fun getWidget(widgetId: String): RhettWidget? {
        return widgetsById[widgetId]
    }

    /**
     * Get all widgets.
     */
    fun getWidgets(): List<RhettWidget> = widgets.toList()

    /**
     * Clear all widgets from this screen.
     */
    fun clearWidgets() {
        widgets.forEach { it.onRemoved() }
        widgets.clear()
        widgetsById.clear()
    }

    /**
     * Register a JavaScript callback for an event.
     *
     * @param eventName The event name (e.g., "shown", "hidden", "tick")
     * @param callback The JavaScript function to call
     */
    fun registerCallback(eventName: String, callback: Value) {
        if (!callback.canExecute()) {
            throw IllegalArgumentException("Callback must be an executable function")
        }
        callbacks[eventName] = callback
    }

    /**
     * Invoke a registered callback.
     *
     * @param eventName The event name
     * @param args Arguments to pass to the callback
     */
    fun invokeCallback(eventName: String, vararg args: Any?) {
        callbacks[eventName]?.executeVoid(*args)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(graphics, mouseX, mouseY, delta)

        // Render all visible widgets
        for (widget in widgets) {
            if (widget.visible) {
                widget.render(graphics, mouseX, mouseY, delta)
            }
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Check widgets in reverse order (top to bottom in z-order)
        for (i in widgets.lastIndex downTo 0) {
            val widget = widgets[i]
            if (widget.visible && widget.mouseClicked(mouseX, mouseY, button)) {
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        for (i in widgets.lastIndex downTo 0) {
            val widget = widgets[i]
            if (widget.visible && widget.mouseReleased(mouseX, mouseY, button)) {
                return true
            }
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        for (i in widgets.lastIndex downTo 0) {
            val widget = widgets[i]
            if (widget.visible && widget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        for (widget in widgets) {
            if (widget.visible && widget.keyPressed(keyCode, scanCode, modifiers)) {
                return true
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        for (widget in widgets) {
            if (widget.visible && widget.keyReleased(keyCode, scanCode, modifiers)) {
                return true
            }
        }
        return super.keyReleased(keyCode, scanCode, modifiers)
    }

    override fun charTyped(character: Char, modifiers: Int): Boolean {
        for (widget in widgets) {
            if (widget.visible && widget.charTyped(character, modifiers)) {
                return true
            }
        }
        return super.charTyped(character, modifiers)
    }

    override fun tick() {
        super.tick()
        widgets.forEach { it.tick() }
        invokeCallback("tick")
    }

    override fun onClose() {
        super.onClose()
        invokeCallback("hidden")
    }

    /**
     * Called when the screen is shown.
     */
    fun onShown() {
        invokeCallback("shown")
    }

    /**
     * Check if this screen should pause the game.
     * Defaults to true for UI screens.
     */
    override fun isPauseScreen(): Boolean = true
}
