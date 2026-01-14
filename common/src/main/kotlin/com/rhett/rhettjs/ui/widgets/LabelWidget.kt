package com.rhett.rhettjs.ui.widgets

import com.rhett.rhettjs.ui.core.BaseWidget
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

/**
 * A text label widget for displaying static or dynamic text.
 *
 * @param id Unique widget identifier
 * @param x X position
 * @param y Y position
 * @param text Text content to display
 * @param color Text color (ARGB format, default: white)
 * @param shadow Whether to render drop shadow (default: true)
 * @param scale Text scale multiplier (default: 1.0)
 */
class LabelWidget(
    id: String,
    x: Int,
    y: Int,
    private var text: String,
    private var color: Int = 0xFFFFFF,
    private var shadow: Boolean = true,
    private var scale: Float = 1.0f
) : BaseWidget(id, x, y, 0, 0) {

    // Width and height are calculated based on text
    override val width: Int
        get() = (font.width(text) * scale).toInt()

    override val height: Int
        get() = (8 * scale).toInt() // Font height is 8 pixels

    private val font
        get() = net.minecraft.client.Minecraft.getInstance().font

    /**
     * Update the label text.
     */
    fun setText(newText: String) {
        this.text = newText
    }

    /**
     * Get the current text.
     */
    fun getText(): String = text

    /**
     * Update the text color.
     */
    fun setColor(newColor: Int) {
        this.color = newColor
    }

    /**
     * Get the current color.
     */
    fun getColor(): Int = color

    /**
     * Enable or disable drop shadow.
     */
    fun setShadow(enabled: Boolean) {
        this.shadow = enabled
    }

    /**
     * Check if drop shadow is enabled.
     */
    fun hasShadow(): Boolean = shadow

    /**
     * Set the text scale.
     */
    fun setScale(newScale: Float) {
        this.scale = newScale.coerceAtLeast(0.1f) // Minimum scale 0.1
    }

    /**
     * Get the current scale.
     */
    fun getScale(): Float = scale

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        if (text.isEmpty()) return

        graphics.pose().pushPose()
        graphics.pose().translate(x.toFloat(), y.toFloat(), 0f)

        if (scale != 1.0f) {
            graphics.pose().scale(scale, scale, 1f)
        }

        // Support multi-line text (split by \n)
        val lines = text.split("\n")
        var currentY = 0

        for (line in lines) {
            if (line.isNotEmpty()) {
                graphics.drawString(
                    font,
                    Component.literal(line),
                    0,
                    currentY,
                    color,
                    shadow
                )
            }
            currentY += 10 // Line height (8px text + 2px spacing)
        }

        graphics.pose().popPose()
    }
}
