package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments


object HitSwap : Module(
    name = "Hit Swap",
    description = "Swaps into another weapon on attack, copying its attributes.",
    category = Category.COMBAT
) {

    private val macesEnabled = boolean("maces", true)
    private val smashOnly = boolean("smash only", true).also {
        it.visibleWhen = { macesEnabled.value }
    }
    private val requireBreach = boolean("require breach", true).also {
        it.visibleWhen = { macesEnabled.value }
    }
    private val requireDensity = boolean("require density", false).also {
        it.visibleWhen = { macesEnabled.value }
    }
    private val stunSlam = boolean("stun slam", false).also {
        it.visibleWhen = { macesEnabled.value }
    }

    private val spearsEnabled = boolean("spears", false)
    private val requireLunge = boolean("require lunge", false).also {
        it.visibleWhen = { spearsEnabled.value }
    }

    private val axesEnabled = boolean("axes", true)
    private val axeShieldOnly = boolean("shield only", true).also {
        it.visibleWhen = { axesEnabled.value }
    }
    private val swordsEnabled = boolean("swords", true)
    private val requireFireAspect = boolean("require fire aspect", true).also {
        it.visibleWhen = { swordsEnabled.value }
    }

    private var originalSlot = -1
    private var shouldSwapBack = false
    private var swapBackDelay = 0
    private var stunActive = false

    override fun onDisabled() {
        if (shouldSwapBack && originalSlot in 0..8) {
            Minecraft.getInstance().player?.inventory?.selectedSlot = originalSlot
        }
        shouldSwapBack = false
        originalSlot = -1
        swapBackDelay = 0
        stunActive = false
    }

    fun onAttack(player: Player, target: net.minecraft.world.entity.Entity) {
        if (target !is LivingEntity) return

        val client = Minecraft.getInstance()
        val enchantSlot = chooseWeaponSlot(player, target)
        val hitSlot = if (enchantSlot != -1) enchantSlot else player.inventory.selectedSlot

        if (hitSlot != player.inventory.selectedSlot && hitSlot in 0..8) {
            swapToSlot(player, hitSlot)
            if (shouldSwapBack) {
                swapBackDelay = 1
            }
        }
    }

    fun onStartAttack() {
        val player = Minecraft.getInstance().player ?: return
        if (spearsEnabled.value) {
            val spearSlot = findSpearSlot(player)
            if (spearSlot != -1 && spearSlot != player.inventory.selectedSlot) {
                val stack = player.inventory.getItem(spearSlot)

                val hasLunge = hasEnchantment(stack, Enchantments.LUNGE)
                
                if (!requireLunge.value || hasLunge) {
                    swapToSlot(player, spearSlot)
                    Minecraft.getInstance().connection?.send(net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(spearSlot))
                    if (shouldSwapBack) {
                        swapBackDelay = 1
                    }
                }
            }
        }
    }

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        if (client.screen != null) return

        if (swapBackDelay > 0) {
            swapBackDelay--
            if (swapBackDelay == 0 && shouldSwapBack) {
                if (originalSlot in 0..8) {
                    player.inventory.selectedSlot = originalSlot
                }
                shouldSwapBack = false
                originalSlot = -1
            }
        }
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
        if (axesEnabled.value && target is Player && target.isBlocking) {
            val axeSlot = findAxeSlot(player)
            if (axeSlot != -1) {
                return axeSlot
            }
        }

        if (macesEnabled.value) {
            if (stunSlam.value) {
                val axeSlot = findAxeSlot(player)
                val maceSlot = findMaceSlot(player)
                if (axeSlot != -1 && maceSlot != -1) {
                    if (!stunActive) {
                        stunActive = true
                        return axeSlot
                    } else {
                        stunActive = false
                        return maceSlot
                    }
                }
            }

            val maceSlot = findMaceSlot(player)
            if (maceSlot != -1) {
                return maceSlot
            }
        }

        if (swordsEnabled.value) {
            val swordSlot = findSwordSlot(player)
            if (swordSlot != -1) {
                return swordSlot
            }
        }

        if (axesEnabled.value && !axeShieldOnly.value) {
            val axeSlot = findAxeSlot(player)
            if (axeSlot != -1) {
                return axeSlot
            }
        }

        return -1
    }

    private fun findMaceSlot(player: Player): Int {
        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty) continue
            if (stack.item !is net.minecraft.world.item.MaceItem) continue
            if (smashOnly.value && player.onGround()) continue
            if (requireBreach.value && !hasEnchantment(stack, Enchantments.BREACH)) continue
            if (requireDensity.value && !hasEnchantment(stack, Enchantments.DENSITY)) continue
            return slot
        }
        return -1
    }

    private fun findSpearSlot(player: Player): Int {
        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty) continue
            if (!stack.`is`(ItemTags.SPEARS)) continue
            return slot
        }
        return -1
    }

    private fun findAxeSlot(player: Player): Int {
        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty) continue
            if (!stack.`is`(ItemTags.AXES)) continue
            return slot
        }
        return -1
    }

    private fun findSwordSlot(player: Player): Int {
        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty) continue
            if (!stack.`is`(ItemTags.SWORDS)) continue
            if (requireFireAspect.value && !hasEnchantment(stack, Enchantments.FIRE_ASPECT)) continue      
            return slot
        }
        return -1
    }

    private fun hasEnchantment(stack: ItemStack, enchantmentKey: ResourceKey<Enchantment>): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        try {
            val enchantment = level.registryAccess().getOrThrow(enchantmentKey)
            return stack.enchantments.getLevel(enchantment) != 0
        } catch (_: Exception) {
            return false
        }
    }

    //override fun hudInfo(): String {
    //    val parts = mutableListOf<String>()
    //    if (macesEnabled.value && spearsEnabled.value && axesEnabled.value && swordsEnabled.value) {
    //        return "all"
    //    }
    //    if (macesEnabled.value) parts += "maces"
    //    if (spearsEnabled.value) parts += "spears"
    //    if (axesEnabled.value) parts += "axes"
    //    if (swordsEnabled.value) parts += "swords"
    //    return if (parts.isEmpty()) "disabled" else parts.joinToString(", ")
    //}
}
