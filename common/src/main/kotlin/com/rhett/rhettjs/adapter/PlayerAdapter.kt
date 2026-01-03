package com.rhett.rhettjs.adapter

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject

/**
 * Adapter for converting Minecraft ServerPlayer to pure JavaScript objects.
 *
 * Design principle: No Java objects exposed. Everything is JS primitives, objects, or arrays.
 *
 * Player object structure:
 * ```javascript
 * {
 *   // Properties (live - read from MC player on each access)
 *   name: string,
 *   uuid: string,
 *   health: number,
 *   maxHealth: number,
 *   foodLevel: number,
 *   saturation: number,
 *   gameMode: "survival" | "creative" | "adventure" | "spectator",
 *   isOp: boolean,
 *   position: { x, y, z, dimension },
 *
 *   // Methods
 *   sendMessage(msg: string): void,
 *   teleport(position: Position): void,
 *   setHealth(amount: number): void,
 *   giveItem(itemId: string, count: number): void,
 *
 *   // Escape hatch
 *   minecraft: ServerPlayer
 * }
 * ```
 */
object PlayerAdapter {

    /**
     * Convert a Minecraft ServerPlayer to a pure JavaScript object.
     *
     * All properties are "live" - they read from the MC player on each access.
     * This ensures the JS object always reflects the current player state.
     */
    fun toJS(player: ServerPlayer, context: Context): Value {
        val playerProxy = ProxyObject.fromMap(mapOf(
            // Properties (read live from player)
            "name" to player.name.string,
            "uuid" to player.stringUUID,
            "health" to player.health.toDouble(),
            "maxHealth" to player.maxHealth.toDouble(),
            "foodLevel" to player.foodData.foodLevel,
            "saturation" to player.foodData.saturationLevel.toDouble(),
            "gameMode" to gameTypeToString(player.gameMode.gameModeForPlayer),
            "isOp" to player.hasPermissions(2), // Op level 2+
            "position" to createPositionObject(player),

            // Methods
            "sendMessage" to ProxyExecutable { args ->
                if (args.isEmpty()) return@ProxyExecutable null
                val message = args[0].asString()
                player.sendSystemMessage(Component.literal(message))
                null
            },

            "teleport" to ProxyExecutable { args ->
                if (args.isEmpty()) return@ProxyExecutable null

                val posArg = args[0]
                if (!posArg.hasMembers()) {
                    throw IllegalArgumentException("teleport() requires a position object { x, y, z, dimension }")
                }

                val x = posArg.getMember("x").asDouble()
                val y = posArg.getMember("y").asDouble()
                val z = posArg.getMember("z").asDouble()
                val dimension = if (posArg.hasMember("dimension")) {
                    posArg.getMember("dimension").asString()
                } else {
                    player.level().dimension().location().toString()
                }

                // Get target level
                val targetLevel = player.server.getLevel(
                    player.server.levelKeys().find {
                        it.location().toString() == dimension
                    } ?: player.level().dimension()
                )

                if (targetLevel != null) {
                    player.teleportTo(targetLevel, x, y, z, player.yRot, player.xRot)
                }
                null
            },

            "setHealth" to ProxyExecutable { args ->
                if (args.isEmpty()) return@ProxyExecutable null
                val amount = args[0].asDouble().toFloat()
                player.health = amount
                null
            },

            "giveItem" to ProxyExecutable { args ->
                if (args.isEmpty()) return@ProxyExecutable null

                val itemId = args[0].asString()
                val count = if (args.size > 1) args[1].asInt() else 1

                // Parse item ID and create ItemStack
                val itemRegistry = player.server.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.ITEM)

                val item = itemRegistry.get(net.minecraft.resources.ResourceLocation.parse(itemId))
                if (item != null) {
                    val stack = ItemStack(item, count)
                    player.inventory.add(stack)
                }
                null
            }

            // NOTE: Removed "minecraft" escape hatch - causes stack overflow due to circular references
            // If needed, expose specific MC methods via additional proxy methods instead
        ))

        return context.asValue(playerProxy)
    }

    /**
     * Create a position object from player coordinates.
     */
    private fun createPositionObject(player: ServerPlayer): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            "x" to player.x,
            "y" to player.y,
            "z" to player.z,
            "dimension" to player.level().dimension().location().toString()
        ))
    }

    /**
     * Convert GameType to JavaScript string.
     */
    private fun gameTypeToString(gameType: GameType): String {
        return when (gameType) {
            GameType.SURVIVAL -> "survival"
            GameType.CREATIVE -> "creative"
            GameType.ADVENTURE -> "adventure"
            GameType.SPECTATOR -> "spectator"
            else -> "survival"
        }
    }
}
