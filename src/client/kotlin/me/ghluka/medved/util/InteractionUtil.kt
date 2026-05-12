package me.ghluka.medved.util

import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResult.SwingSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult

fun InteractionResult.shouldSwingHand(): Boolean =
    this is InteractionResult.Success && swingSource === SwingSource.CLIENT

data class StrictInteractionResult(
    val hand: InteractionHand,
    val result: InteractionResult,
) {
    val isUseItemSuccess: Boolean
        get() = result is InteractionResult.Success
}

fun useItemStrict(hand: InteractionHand): StrictInteractionResult? {
    val mc = Minecraft.getInstance()
    val player = mc.player ?: return null
    val gameMode = mc.gameMode ?: return null

    val result = gameMode.useItem(player, hand)
    if (result !is InteractionResult.Success) return null

    if (result.shouldSwingHand()) {
        player.swing(hand)
    }

    mc.gameRenderer.itemInHandRenderer.itemUsed(hand)
    return StrictInteractionResult(hand, result)
}

fun interactEntity(
    entity: Entity,
    hitResult: EntityHitResult = EntityHitResult(entity),
    hand: InteractionHand = InteractionHand.MAIN_HAND,
): InteractionResult? {
    val mc = Minecraft.getInstance()
    val player = mc.player ?: return null
    val gameMode = mc.gameMode ?: return null

    if (!entity.level().worldBorder.isWithinBounds(entity.blockPosition())) return null

    val result = gameMode.interact(player, entity, hitResult, hand)
    if (result.shouldSwingHand()) {
        player.swing(hand)
    }

    return result
}

fun interactBlock(
    hitResult: BlockHitResult,
    hand: InteractionHand = InteractionHand.MAIN_HAND,
): InteractionResult {
    val mc = Minecraft.getInstance()
    val player = mc.player ?: return InteractionResult.FAIL
    val gameMode = mc.gameMode ?: return InteractionResult.FAIL

    val itemStack = player.getItemInHand(hand)
    val oldCount = itemStack.count
    val result = gameMode.useItemOn(player, hand, hitResult)

    if (result.shouldSwingHand()) {
        player.swing(hand)
        if (!itemStack.isEmpty && (itemStack.count != oldCount || player.hasInfiniteMaterials())) {
            mc.gameRenderer.itemInHandRenderer.itemUsed(hand)
        }
    }

    return result
}