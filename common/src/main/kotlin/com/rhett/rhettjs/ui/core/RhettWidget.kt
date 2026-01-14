package com.rhett.rhettjs.ui.core

import net.minecraft.client.gui.GuiGraphics

/**
 * Base interface for all RhettJS UI widgets.
 * Widgets are composable UI elements that handle rendering and input.
 */
interface RhettWidget {
    /** Unique identifier for this widget */
    val id: String

    /** X position relative to screen */
    val x: Int

    /** Y position relative to screen */
    val y: Int

    /** Widget width */
    val width: Int

    /** Widget height */
    val height: Int

    /** Whether this widget is currently visible */
    var visible: Boolean

    /**
     * Render this widget.
     *
     * @param graphics Graphics context for rendering
     * @param mouseX Current mouse X position
     * @param mouseY Current mouse Y position
     * @param delta Time since last frame
     */
    fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float)

    /**
     * Handle mouse click events.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param button Mouse button (0=left, 1=right, 2=middle)
     * @return true if the click was handled, false otherwise
     */
    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = false

    /**
     * Handle mouse release events.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param button Mouse button (0=left, 1=right, 2=middle)
     * @return true if the release was handled, false otherwise
     */
    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = false

    /**
     * Handle mouse drag events.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param button Mouse button being dragged
     * @param deltaX Change in X position
     * @param deltaY Change in Y position
     * @return true if the drag was handled, false otherwise
     */
    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean = false

    /**
     * Handle key press events.
     *
     * @param keyCode Key code
     * @param scanCode Scan code
     * @param modifiers Key modifiers (shift, ctrl, alt)
     * @return true if the key press was handled, false otherwise
     */
    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean = false

    /**
     * Handle key release events.
     *
     * @param keyCode Key code
     * @param scanCode Scan code
     * @param modifiers Key modifiers (shift, ctrl, alt)
     * @return true if the key release was handled, false otherwise
     */
    fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean = false

    /**
     * Handle character typed events.
     *
     * @param character Character that was typed
     * @param modifiers Key modifiers
     * @return true if the character was handled, false otherwise
     */
    fun charTyped(character: Char, modifiers: Int): Boolean = false

    /**
     * Called every game tick (20 times per second).
     */
    fun tick() {}

    /**
     * Check if a point is within this widget's bounds.
     *
     * @param mouseX X position to check
     * @param mouseY Y position to check
     * @return true if the point is within bounds
     */
    fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
    }

    /**
     * Called when the widget is removed from the screen.
     * Use for cleanup if needed.
     */
    fun onRemoved() {}
}

/**
 * Abstract base class providing common widget functionality.
 */
abstract class BaseWidget(
    override val id: String,
    override val x: Int,
    override val y: Int,
    override val width: Int,
    override val height: Int
) : RhettWidget {
    override var visible: Boolean = true

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        if (!visible) return
        renderWidget(graphics, mouseX, mouseY, delta)
    }

    /**
     * Subclasses implement this to render their content.
     * Only called if the widget is visible.
     */
    protected abstract fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float)
}
