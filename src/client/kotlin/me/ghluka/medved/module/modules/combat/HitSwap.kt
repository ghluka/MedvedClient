package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.other.TargetFilter
import net.minecraft.client.Minecraft
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TridentItem
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import java.util.Locale

object HitSwap : Module(
    name = "Hit Swap",
    description = "Swaps into another weapon on attack, copying its attributes.",
    category = Category.COMBAT
) {

    private val macesEnabled = boolean("maces", true)
    private val smashOnly = boolean("smash only", false).also {
        it.visibleWhen = { macesEnabled.value }
    }
    private val requireBreach = boolean("require breach", false).also {
        it.visibleWhen = { macesEnabled.value }
    }
    private val requireDensity = boolean("require density", false).also {
        it.visibleWhen = { macesEnabled.value }
    }
    private val stunSlam = boolean("stun slam", false).also {
        it.visibleWhen = { macesEnabled.value }
    }

    private val axesEnabled = boolean("axes", true)
    private val swordsEnabled = boolean("swords", true)
    private val requireFireAspect = boolean("require fire aspect", false).also {
        it.visibleWhen = { swordsEnabled.value }
    }

    private var lastAttackDown = false
    private var originalSlot = -1
    private var shouldSwapBack = false
    private var followUpSlot = -1

    override fun onDisabled() {
        if (shouldSwapBack && originalSlot in 0..8) {
            Minecraft.getInstance().player?.inventory?.selectedSlot = originalSlot
        }
        lastAttackDown = false
        shouldSwapBack = false
        followUpSlot = -1
        originalSlot = -1
    }

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        if (client.screen != null) return

        val attackDown = client.options.keyAttack.isDown
        val hitResult = client.hitResult ?: return
        val target = (hitResult as? EntityHitResult)?.entity as? LivingEntity

        if (attackDown && !lastAttackDown && target != null && !target.isDeadOrDying && TargetFilter.isValidTarget(player, target)) {
            if (followUpSlot != -1) {
                swapToSlot(player, followUpSlot)
                followUpSlot = -1
            } else {
                val chosenSlot = chooseWeaponSlot(player, target)
                if (chosenSlot != -1) {
                    swapToSlot(player, chosenSlot)
                }
            }
        }

        if (!attackDown && lastAttackDown && shouldSwapBack) {
            if (originalSlot in 0..8) {
                player.inventory.selectedSlot = originalSlot
            }
            shouldSwapBack = false
        }

        lastAttackDown = attackDown
    }

    private fun swapToSlot(player: Player, slot: Int) {
        if (slot !in 0..8) return
        if (!shouldSwapBack) {
            originalSlot = player.inventory.selectedSlot
            shouldSwapBack = true
        }
        if (player.inventory.selectedSlot != slot) {
            player.inventory.selectedSlot = slot
        }
    }

    private fun chooseWeaponSlot(player: Player, target: LivingEntity): Int {
        if (macesEnabled.value) {
            val maceSlot = findMaceSlot(player)
            if (maceSlot != -1) {
                if (stunSlam.value) {
                    val axeSlot = findAxeSlot(player)
                    if (axeSlot != -1 && maceSlot != -1) {
                        followUpSlot = maceSlot
                        return axeSlot
                    }
                }
                return maceSlot
            }
        }

        if (axesEnabled.value) {
            val axeSlot = findAxeSlot(player)
            if (axeSlot != -1) {
                return axeSlot
            }
        }

        if (swordsEnabled.value) {
            val swordSlot = findSwordSlot(player)
            if (swordSlot != -1) {
                return swordSlot
            }
        }

        return -1
    }

    private fun findMaceSlot(player: Player): Int {
        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty) continue
            if (!isMace(stack)) continue
            if (smashOnly.value && player.onGround()) continue
            return slot
        }
        return -1
    }

    private fun findAxeSlot(player: Player): Int {
        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty) continue
            if (stack.`is`(ItemTags.AXES)) return slot
        }
        return -1
    }

    private fun findSwordSlot(player: Player): Int {
        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty) continue
            if (!stack.`is`(ItemTags.SWORDS)) continue
            if (requireFireAspect.value && !hasFireAspect(stack)) continue
            return slot
        }
        return -1
    }

    private fun isMace(stack: ItemStack): Boolean {
        val name = stack.item.toString().lowercase(Locale.ROOT)
        val hoverText = stack.components.toString().lowercase(Locale.ROOT)
        if (!name.contains("mace")) return false
        if (requireBreach.value && !hoverText.contains("breach")) return false
        if (requireDensity.value && !hoverText.contains("density")) return false
        return true
    }

    private fun hasFireAspect(stack: ItemStack): Boolean {
        return stack.components.toString().lowercase(Locale.ROOT).contains("fire")
    }

    override fun hudInfo(): String {
        val parts = mutableListOf<String>()
        if (macesEnabled.value && axesEnabled.value && swordsEnabled.value) {
            return "all"
        }
        if (macesEnabled.value) parts += "maces"
        if (axesEnabled.value) parts += "axes"
        if (swordsEnabled.value) parts += "swords"
        return if (parts.isEmpty()) "disabled" else parts.joinToString(", ")
    }
}
