package com.rhett.rhettjs.ui.widgets

import com.rhett.rhettjs.ui.core.BaseWidget
import com.rhett.rhettjs.ui.core.RhettWidget
import net.minecraft.client.gui.GuiGraphics

/**
 * A container panel widget with optional background and border.
 * Can contain child widgets for layout purposes.
 *
 * @param id Unique widget identifier
 * @param x X position
 * @param y Y position
 * @param width Panel width
 * @param height Panel height
 * @param backgroundColor Background color (ARGB format, null for transparent)
 * @param borderColor Border color (ARGB format, null for no border)
 * @param borderWidth Border thickness in pixels
 */
class PanelWidget(
    id: String,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private var backgroundColor: Int? = null,
    private var borderColor: Int? = null,
    private var borderWidth: Int = 1
) : BaseWidget(id, x, y, width, height) {

    private val children: MutableList<RhettWidget> = mutableListOf()

    /**
     * Add a child widget to this panel.
     * Child positions are relative to the panel's position.
     */
    fun addChild(widget: RhettWidget) {
        children.add(widget)
    }

    /**
     * Remove a child widget by ID.
     */
    fun removeChild(widgetId: String): RhettWidget? {
        val widget = children.find { it.id == widgetId } ?: return null
        children.remove(widget)
        widget.onRemoved()
        return widget
    }

    /**
     * Get all child widgets.
     */
    fun getChildren(): List<RhettWidget> = children.toList()

    /**
     * Clear all child widgets.
     */
    fun clearChildren() {
        children.forEach { it.onRemoved() }
        children.clear()
    }

    /**
     * Set the background color.
     * Use null for transparent background.
     */
    fun setBackgroundColor(color: Int?) {
        this.backgroundColor = color
    }

    /**
     * Set the border color.
     * Use null for no border.
     */
    fun setBorderColor(color: Int?) {
        this.borderColor = color
    }

    /**
     * Set the border width in pixels.
     */
    fun setBorderWidth(width: Int) {
        this.borderWidth = width.coerceAtLeast(1)
    }

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        // Render background
        backgroundColor?.let { color ->
            graphics.fill(x, y, x + width, y + height, color)
        }

        // Render border
        borderColor?.let { color ->
            val bw = borderWidth
            // Top
            graphics.fill(x, y, x + width, y + bw, color)
            // Bottom
            graphics.fill(x, y + height - bw, x + width, y + height, color)
            // Left
            graphics.fill(x, y, x + bw, y + height, color)
            // Right
            graphics.fill(x + width - bw, y, x + width, y + height, color)
        }

        // Render children
        // Enable scissor (clipping) to keep children inside panel bounds
        graphics.enableScissor(x, y, x + width, y + height)
        for (child in children) {
            if (child.visible) {
                child.render(graphics, mouseX, mouseY, delta)
            }
        }
        graphics.disableScissor()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Only process if click is within panel bounds
        if (!isMouseOver(mouseX, mouseY)) {
            return false
        }

        // Check children in reverse order (top to bottom in z-order)
        for (i in children.lastIndex downTo 0) {
            val child = children[i]
            if (child.visible && child.mouseClicked(mouseX, mouseY, button)) {
                return true
            }
        }

        // Panel itself consumes clicks if it has a background (acts as a blocker)
        return backgroundColor != null
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        for (i in children.lastIndex downTo 0) {
            val child = children[i]
            if (child.visible && child.mouseReleased(mouseX, mouseY, button)) {
                return true
            }
        }
        return false
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        for (i in children.lastIndex downTo 0) {
            val child = children[i]
            if (child.visible && child.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true
            }
        }
        return false
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        for (child in children) {
            if (child.visible && child.keyPressed(keyCode, scanCode, modifiers)) {
                return true
            }
        }
        return false
    }

    override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        for (child in children) {
            if (child.visible && child.keyReleased(keyCode, scanCode, modifiers)) {
                return true
            }
        }
        return false
    }

    override fun charTyped(character: Char, modifiers: Int): Boolean {
        for (child in children) {
            if (child.visible && child.charTyped(character, modifiers)) {
                return true
            }
        }
        return false
    }

    override fun tick() {
        children.forEach { it.tick() }
    }

    override fun onRemoved() {
        children.forEach { it.onRemoved() }
    }
}
