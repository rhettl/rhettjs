package com.rhett.rhettjs.events

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

/**
 * NeoForge-specific event handler that bridges NeoForge's block events to our internal event system.
 * Implements anti-corruption layer pattern by converting NeoForge events to our domain models.
 */
object NeoForgeBlockEventHandler {

    @SubscribeEvent
    fun onBlockRightClick(event: PlayerInteractEvent.RightClickBlock) {
        val player = event.entity
        val level = event.level
        val pos = event.pos
        val hand = event.hand
        val item = player.getItemInHand(hand)
        val face = event.face

        // Convert to our internal event model and trigger
        val eventData = BlockEventAdapter.createClickEvent(
            pos = pos,
            level = level,
            player = player,
            item = item,
            face = face,
            isRightClick = true
        )

        val cancelled = BlockEventTrigger.trigger("blockRightClicked", eventData, player)
        if (cancelled) {
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    fun onBlockLeftClick(event: PlayerInteractEvent.LeftClickBlock) {
        val player = event.entity
        val level = event.level
        val pos = event.pos
        val hand = event.hand
        val item = player.getItemInHand(hand)
        val face = event.face

        // Convert to our internal event model and trigger
        val eventData = BlockEventAdapter.createClickEvent(
            pos = pos,
            level = level,
            player = player,
            item = item,
            face = face,
            isRightClick = false
        )

        val cancelled = BlockEventTrigger.trigger("blockLeftClicked", eventData, player)
        if (cancelled) {
            event.isCanceled = true
        }
    }
}
