package com.rhett.rhettjs.engine.api

import com.rhett.rhettjs.ui.core.UIManager
import com.rhett.rhettjs.ui.widgets.ButtonWidget
import com.rhett.rhettjs.ui.widgets.ImageWidget
import com.rhett.rhettjs.ui.widgets.LabelWidget
import com.rhett.rhettjs.ui.widgets.PanelWidget
import net.minecraft.resources.ResourceLocation
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject
import java.util.UUID

/**
 * UI API proxy for JavaScript scripts.
 * Provides screen management and widget creation.
 *
 * This is a client-side only API - all operations are synchronous and immediate.
 */
object UIAPIProxy {

    /**
     * Create the UI API proxy for JavaScript.
     * Exposes the UI management system to scripts.
     */
    fun create(context: Context): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            // Screen management
            "createScreen" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("createScreen() requires a screen ID")
                }
                val screenId = args[0].asString()
                val screen = UIManager.createScreen(screenId)
                createScreenProxy(context, screenId)
            },

            "getScreen" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("getScreen() requires a screen ID")
                }
                val screenId = args[0].asString()
                val screen = UIManager.getScreen(screenId)
                if (screen != null) {
                    createScreenProxy(context, screenId)
                } else {
                    null
                }
            },

            "removeScreen" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("removeScreen() requires a screen ID")
                }
                val screenId = args[0].asString()
                UIManager.removeScreen(screenId)
                null
            }
        ))
    }

    /**
     * Create a proxy object for a specific screen.
     * This provides methods to add widgets, show/hide the screen, etc.
     */
    private fun createScreenProxy(context: Context, screenId: String): ProxyObject {
        val screen = UIManager.getScreen(screenId)
            ?: throw IllegalStateException("Screen '$screenId' does not exist")

        return ProxyObject.fromMap(mapOf(
            // Screen ID (readonly property)
            "id" to screenId,

            // Widget creation methods
            "addButton" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("addButton() requires an options object")
                }
                val options = args[0]
                addButton(screen, options)
            },

            "addLabel" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("addLabel() requires an options object")
                }
                val options = args[0]
                addLabel(screen, options)
            },

            "addImage" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("addImage() requires an options object")
                }
                val options = args[0]
                addImage(screen, options)
            },

            "addPanel" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("addPanel() requires an options object")
                }
                val options = args[0]
                addPanel(screen, options)
            },

            // Widget management
            "removeWidget" to ProxyExecutable { args ->
                if (args.isEmpty()) {
                    throw IllegalArgumentException("removeWidget() requires a widget ID")
                }
                val widgetId = args[0].asString()
                screen.removeWidget(widgetId)
                null
            },

            "updateWidget" to ProxyExecutable { args ->
                if (args.size < 2) {
                    throw IllegalArgumentException("updateWidget() requires widget ID and updates object")
                }
                val widgetId = args[0].asString()
                val updates = args[1]
                updateWidget(screen, widgetId, updates)
            },

            // Screen lifecycle
            "show" to ProxyExecutable { _ ->
                UIManager.showScreen(screenId)
                null
            },

            "hide" to ProxyExecutable { _ ->
                UIManager.hideScreen(screenId)
                null
            },

            "isVisible" to ProxyExecutable { _ ->
                UIManager.isScreenActive(screenId)
            },

            // Events
            "on" to ProxyExecutable { args ->
                if (args.size < 2) {
                    throw IllegalArgumentException("on() requires event name and callback")
                }
                val eventName = args[0].asString()
                val callback = args[1]
                screen.registerCallback(eventName, callback)
                null
            }
        ))
    }

    /**
     * Add a button widget to a screen.
     */
    private fun addButton(screen: com.rhett.rhettjs.ui.core.RhettScreen, options: Value): String {
        val id = if (options.hasMember("id")) options.getMember("id").asString() else UUID.randomUUID().toString()
        val x = options.getMember("x").asInt()
        val y = options.getMember("y").asInt()
        val width = options.getMember("width").asInt()
        val height = options.getMember("height").asInt()
        val text = options.getMember("text").asString()
        val onClick = if (options.hasMember("onClick")) options.getMember("onClick") else null
        val textureStr = if (options.hasMember("texture")) options.getMember("texture").asString() else null
        val texture = textureStr?.let { ResourceLocation.parse(it) }

        val button = ButtonWidget(id, x, y, width, height, text, onClick, texture)
        screen.addWidget(button)
        return id
    }

    /**
     * Add a label widget to a screen.
     */
    private fun addLabel(screen: com.rhett.rhettjs.ui.core.RhettScreen, options: Value): String {
        val id = if (options.hasMember("id")) options.getMember("id").asString() else UUID.randomUUID().toString()
        val x = options.getMember("x").asInt()
        val y = options.getMember("y").asInt()
        val text = options.getMember("text").asString()
        val color = if (options.hasMember("color")) options.getMember("color").asInt() else 0xFFFFFF
        val shadow = if (options.hasMember("shadow")) options.getMember("shadow").asBoolean() else true
        val scale = if (options.hasMember("scale")) options.getMember("scale").asFloat() else 1.0f

        val label = LabelWidget(id, x, y, text, color, shadow, scale)
        screen.addWidget(label)
        return id
    }

    /**
     * Add an image widget to a screen.
     */
    private fun addImage(screen: com.rhett.rhettjs.ui.core.RhettScreen, options: Value): String {
        val id = if (options.hasMember("id")) options.getMember("id").asString() else UUID.randomUUID().toString()
        val x = options.getMember("x").asInt()
        val y = options.getMember("y").asInt()
        val width = options.getMember("width").asInt()
        val height = options.getMember("height").asInt()
        val textureStr = options.getMember("texture").asString()
        val texture = ResourceLocation.parse(textureStr)
        val u = if (options.hasMember("u")) options.getMember("u").asInt() else 0
        val v = if (options.hasMember("v")) options.getMember("v").asInt() else 0
        val textureWidth = if (options.hasMember("textureWidth")) options.getMember("textureWidth").asInt() else width
        val textureHeight = if (options.hasMember("textureHeight")) options.getMember("textureHeight").asInt() else height

        val image = ImageWidget(id, x, y, width, height, texture, u, v, textureWidth, textureHeight)
        screen.addWidget(image)
        return id
    }

    /**
     * Add a panel widget to a screen.
     */
    private fun addPanel(screen: com.rhett.rhettjs.ui.core.RhettScreen, options: Value): String {
        val id = if (options.hasMember("id")) options.getMember("id").asString() else UUID.randomUUID().toString()
        val x = options.getMember("x").asInt()
        val y = options.getMember("y").asInt()
        val width = options.getMember("width").asInt()
        val height = options.getMember("height").asInt()
        val backgroundColor = if (options.hasMember("backgroundColor")) options.getMember("backgroundColor").asInt() else null
        val borderColor = if (options.hasMember("borderColor")) options.getMember("borderColor").asInt() else null
        val borderWidth = if (options.hasMember("borderWidth")) options.getMember("borderWidth").asInt() else 1

        val panel = PanelWidget(id, x, y, width, height, backgroundColor, borderColor, borderWidth)
        screen.addWidget(panel)
        return id
    }

    /**
     * Update an existing widget's properties.
     */
    private fun updateWidget(screen: com.rhett.rhettjs.ui.core.RhettScreen, widgetId: String, updates: Value) {
        val widget = screen.getWidget(widgetId)
            ?: throw IllegalArgumentException("Widget '$widgetId' not found")

        // Update common properties
        if (updates.hasMember("visible")) {
            widget.visible = updates.getMember("visible").asBoolean()
        }

        // Update widget-specific properties
        when (widget) {
            is ButtonWidget -> {
                if (updates.hasMember("text")) {
                    widget.setText(updates.getMember("text").asString())
                }
                if (updates.hasMember("enabled")) {
                    widget.enabled = updates.getMember("enabled").asBoolean()
                }
                if (updates.hasMember("onClick")) {
                    widget.setOnClick(updates.getMember("onClick"))
                }
            }
            is LabelWidget -> {
                if (updates.hasMember("text")) {
                    widget.setText(updates.getMember("text").asString())
                }
                if (updates.hasMember("color")) {
                    widget.setColor(updates.getMember("color").asInt())
                }
                if (updates.hasMember("shadow")) {
                    widget.setShadow(updates.getMember("shadow").asBoolean())
                }
                if (updates.hasMember("scale")) {
                    widget.setScale(updates.getMember("scale").asFloat())
                }
            }
            is ImageWidget -> {
                if (updates.hasMember("texture")) {
                    val texture = ResourceLocation.parse(updates.getMember("texture").asString())
                    widget.setTexture(texture)
                }
                if (updates.hasMember("tint")) {
                    widget.setTint(updates.getMember("tint").asInt())
                }
            }
            is PanelWidget -> {
                if (updates.hasMember("backgroundColor")) {
                    widget.setBackgroundColor(updates.getMember("backgroundColor").asInt())
                }
                if (updates.hasMember("borderColor")) {
                    widget.setBorderColor(updates.getMember("borderColor").asInt())
                }
                if (updates.hasMember("borderWidth")) {
                    widget.setBorderWidth(updates.getMember("borderWidth").asInt())
                }
            }
        }
    }
}
