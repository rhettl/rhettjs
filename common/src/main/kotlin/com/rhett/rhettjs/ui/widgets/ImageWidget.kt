package com.rhett.rhettjs.ui.widgets

import com.rhett.rhettjs.ui.core.BaseWidget
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation

/**
 * A widget for displaying images/textures.
 *
 * @param id Unique widget identifier
 * @param x X position
 * @param y Y position
 * @param width Image width
 * @param height Image height
 * @param texture Resource location of the texture to display
 * @param u Texture U coordinate (default: 0)
 * @param v Texture V coordinate (default: 0)
 * @param textureWidth Total texture width (default: same as width)
 * @param textureHeight Total texture height (default: same as height)
 */
class ImageWidget(
    id: String,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private var texture: ResourceLocation,
    private var u: Int = 0,
    private var v: Int = 0,
    private var textureWidth: Int = width,
    private var textureHeight: Int = height
) : BaseWidget(id, x, y, width, height) {

    private var tint: Int = 0xFFFFFF // White (no tint)

    /**
     * Update the texture.
     */
    fun setTexture(newTexture: ResourceLocation) {
        this.texture = newTexture
    }

    /**
     * Get the current texture.
     */
    fun getTexture(): ResourceLocation = texture

    /**
     * Set texture coordinates.
     */
    fun setTextureCoordinates(u: Int, v: Int) {
        this.u = u
        this.v = v
    }

    /**
     * Set texture dimensions.
     */
    fun setTextureDimensions(width: Int, height: Int) {
        this.textureWidth = width
        this.textureHeight = height
    }

    /**
     * Set color tint (RGB format, 0xFFFFFF = no tint).
     */
    fun setTint(color: Int) {
        this.tint = color
    }

    /**
     * Get the current tint.
     */
    fun getTint(): Int = tint

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        // Apply tint if needed
        if (tint != 0xFFFFFF) {
            val r = ((tint shr 16) and 0xFF) / 255f
            val g = ((tint shr 8) and 0xFF) / 255f
            val b = (tint and 0xFF) / 255f

            graphics.setColor(r, g, b, 1.0f)
        }

        // Render the texture
        graphics.blit(
            texture,
            x,
            y,
            u.toFloat(),
            v.toFloat(),
            width,
            height,
            textureWidth,
            textureHeight
        )

        // Reset color
        if (tint != 0xFFFFFF) {
            graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f)
        }
    }
}
