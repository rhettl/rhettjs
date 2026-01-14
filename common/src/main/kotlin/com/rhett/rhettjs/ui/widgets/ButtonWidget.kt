package com.rhett.rhettjs.ui.widgets

import com.rhett.rhettjs.ui.core.BaseWidget
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.graalvm.polyglot.Value

/**
 * A clickable button widget.
 *
 * @param id Unique widget identifier
 * @param x X position
 * @param y Y position
 * @param width Button width
 * @param height Button height
 * @param text Button label text
 * @param onClick JavaScript callback function called when button is clicked
 * @param texture Optional custom texture (uses default button texture if null)
 */
class ButtonWidget(
    id: String,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private var text: String,
    private var onClick: Value? = null,
    private var texture: ResourceLocation? = null
) : BaseWidget(id, x, y, width, height) {

    companion object {
        // Default Minecraft button texture
        private val DEFAULT_BUTTON_TEXTURE = ResourceLocation.withDefaultNamespace("widget/button")
        private val DEFAULT_BUTTON_HIGHLIGHTED_TEXTURE = ResourceLocation.withDefaultNamespace("widget/button_highlighted")
        private val DEFAULT_BUTTON_DISABLED_TEXTURE = ResourceLocation.withDefaultNamespace("widget/button_disabled")
    }

    private var isHovered: Boolean = false
    private var isPressed: Boolean = false
    var enabled: Boolean = true

    /**
     * Update the button text.
     */
    fun setText(newText: String) {
        this.text = newText
    }

    /**
     * Get the current button text.
     */
    fun getText(): String = text

    /**
     * Update the click callback.
     */
    fun setOnClick(callback: Value?) {
        if (callback != null && !callback.canExecute()) {
            throw IllegalArgumentException("onClick must be an executable function")
        }
        this.onClick = callback
    }

    /**
     * Update the button texture.
     */
    fun setTexture(newTexture: ResourceLocation?) {
        this.texture = newTexture
    }

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        isHovered = isMouseOver(mouseX.toDouble(), mouseY.toDouble()) && enabled

        // Choose texture based on button state
        val buttonTexture = when {
            !enabled -> texture ?: DEFAULT_BUTTON_DISABLED_TEXTURE
            isHovered -> texture ?: DEFAULT_BUTTON_HIGHLIGHTED_TEXTURE
            else -> texture ?: DEFAULT_BUTTON_TEXTURE
        }

        // Render button background
        graphics.blitSprite(buttonTexture, x, y, width, height)

        // Render button text
        val textComponent = Component.literal(text)
        val textColor = if (enabled) 0xFFFFFF else 0xA0A0A0
        val textX = x + (width - graphics.font.width(textComponent)) / 2
        val textY = y + (height - 8) / 2

        graphics.drawString(
            graphics.font,
            textComponent,
            textX,
            textY,
            textColor,
            true // drop shadow
        )
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!enabled || !visible || !isMouseOver(mouseX, mouseY)) {
            return false
        }

        if (button == 0) { // Left click
            isPressed = true
            playClickSound()
            onClick?.executeVoid()
            return true
        }

        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            isPressed = false
            return isMouseOver(mouseX, mouseY)
        }
        return false
    }

    private fun playClickSound() {
        // Play button click sound
        net.minecraft.client.Minecraft.getInstance().soundManager.play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK,
                1.0f
            )
        )
    }
}
