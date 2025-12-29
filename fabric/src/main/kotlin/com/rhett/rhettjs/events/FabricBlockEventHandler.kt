package com.rhett.rhettjs.events

import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.world.InteractionResult

/**
 * Fabric-specific event handler that bridges Fabric's block events to our internal event system.
 * Implements anti-corruption layer pattern by converting Fabric events to our domain models.
 */
object FabricBlockEventHandler {

    /**
     * Register all Fabric block event listeners.
     * Called during mod initialization.
     */
    fun register() {
        // Right-click on block
        UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
            if (world.isClientSide) {
                return@register InteractionResult.PASS
            }

            val pos = hitResult.blockPos
            val item = player.getItemInHand(hand)
            val face = hitResult.direction

            // Convert to our internal event model and trigger
            val eventData = BlockEventAdapter.createClickEvent(
                pos = pos,
                level = world,
                player = player,
                item = item,
                face = face,
                isRightClick = true
            )

            val cancelled = BlockEventTrigger.trigger("blockRightClicked", eventData, player)

            if (cancelled) InteractionResult.FAIL else InteractionResult.PASS
        }

        // Left-click on block
        AttackBlockCallback.EVENT.register { player, world, hand, pos, direction ->
            if (world.isClientSide) {
                return@register InteractionResult.PASS
            }

            val item = player.getItemInHand(hand)

            // Convert to our internal event model and trigger
            val eventData = BlockEventAdapter.createClickEvent(
                pos = pos,
                level = world,
                player = player,
                item = item,
                face = direction,
                isRightClick = false
            )

            val cancelled = BlockEventTrigger.trigger("blockLeftClicked", eventData, player)

            if (cancelled) InteractionResult.FAIL else InteractionResult.PASS
        }

        com.rhett.rhettjs.RhettJSCommon.LOGGER.info("[RhettJS] Registered Fabric block event handlers")
    }
}
