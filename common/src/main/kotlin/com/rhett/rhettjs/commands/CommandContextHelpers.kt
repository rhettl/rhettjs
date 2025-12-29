package com.rhett.rhettjs.commands

import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/**
 * Wrapper for CommandContext that adds convenient JavaScript accessors.
 * Makes command contexts consistent with Caller and event APIs.
 */
class CommandContextWrapper(
    private val context: CommandContext<CommandSourceStack>,
    private val scope: Scriptable
) {
    // Delegate to underlying context
    fun getSource() = context.source
    fun getArgument(name: String, clazz: Class<*>) = context.getArgument(name, clazz)
    fun getInput() = context.input

    /**
     * Get the player who executed the command, or null if not a player.
     * JavaScript: ctx.player
     */
    fun getPlayer(): ServerPlayer? {
        return context.source.entity as? ServerPlayer
    }

    /**
     * Get the player's name, or "Server" if not a player.
     * JavaScript: ctx.playerName
     */
    fun getPlayerName(): String {
        return (context.source.entity as? ServerPlayer)?.name?.string ?: "Server"
    }

    /**
     * Get the unwrapped CommandContext for argument type access.
     * JavaScript: ctx.unwrap()
     */
    fun unwrap(): CommandContext<CommandSourceStack> {
        return context
    }
}

/**
 * Create a JavaScript-friendly wrapper for CommandContext.
 * Creates a plain JS object with convenient properties.
 */
fun CommandContext<CommandSourceStack>.wrapForJavaScript(scope: Scriptable): Any {
    val cx = Context.getCurrentContext()

    // Create a plain JavaScript object (not a Java wrapper)
    val jsObj = cx.newObject(scope)

    // Add player object directly
    val player = (this.source.entity as? ServerPlayer)
    ScriptableObject.putProperty(jsObj, "player", if (player != null) Context.javaToJS(player, scope) else null)

    // Add source
    ScriptableObject.putProperty(jsObj, "source", Context.javaToJS(this.source, scope))

    // Add helper for player name
    ScriptableObject.putProperty(jsObj, "playerName", player?.name?.string ?: "Server")

    // Add unwrap method to get the raw context (for argument type getters)
    val unwrapFunc = object : org.mozilla.javascript.BaseFunction() {
        override fun call(
            cx: Context,
            scope: Scriptable,
            thisObj: Scriptable?,
            args: Array<Any?>
        ): Any {
            return Context.javaToJS(this@wrapForJavaScript, scope)
        }
    }
    ScriptableObject.putProperty(jsObj, "unwrap", unwrapFunc)

    // Add messaging helper methods (consistent with Caller/event APIs)
    val sendSuccessFunc = object : org.mozilla.javascript.BaseFunction() {
        override fun call(
            cx: Context,
            scope: Scriptable,
            thisObj: Scriptable?,
            args: Array<Any?>
        ): Any {
            if (args.isNotEmpty()) {
                val message = args[0].toString()
                this@wrapForJavaScript.source.sendSuccess({ net.minecraft.network.chat.Component.literal(message) }, false)
            }
            return Context.getUndefinedValue()
        }
    }
    ScriptableObject.putProperty(jsObj, "sendSuccess", sendSuccessFunc)

    val sendErrorFunc = object : org.mozilla.javascript.BaseFunction() {
        override fun call(
            cx: Context,
            scope: Scriptable,
            thisObj: Scriptable?,
            args: Array<Any?>
        ): Any {
            if (args.isNotEmpty()) {
                val message = args[0].toString()
                this@wrapForJavaScript.source.sendFailure(net.minecraft.network.chat.Component.literal(message))
            }
            return Context.getUndefinedValue()
        }
    }
    ScriptableObject.putProperty(jsObj, "sendError", sendErrorFunc)

    val sendMessageFunc = object : org.mozilla.javascript.BaseFunction() {
        override fun call(
            cx: Context,
            scope: Scriptable,
            thisObj: Scriptable?,
            args: Array<Any?>
        ): Any {
            if (args.isNotEmpty()) {
                val message = args[0].toString()
                this@wrapForJavaScript.source.sendSuccess({ net.minecraft.network.chat.Component.literal(message) }, false)
            }
            return Context.getUndefinedValue()
        }
    }
    ScriptableObject.putProperty(jsObj, "sendMessage", sendMessageFunc)

    return jsObj
}
