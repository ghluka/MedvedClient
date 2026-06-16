package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.other.TargetFilter
import me.ghluka.medved.util.InputUtil.isPhysicalKeyDown
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.tags.ItemTags
import net.minecraft.world.item.TridentItem

object TriggerBot : Module(
    "Trigger Bot", 
    "Automatically attacks when your crosshair is over an enemy", 
    Category.COMBAT
) {

    private val extraDelay = intRange("extra delay", 0 to 2, -5, 5)
    private val playersOnly = boolean("players only", true)
    
    private val onlyWeapon = boolean("only weapon", true)
    private val requireMouseDown = boolean("require mouse down", false)
    private val shieldCheck = boolean("shield check", true)
    private val airCrits = boolean("air crits", false)

    private var seqDelayTicks = 0

    override fun onEnabled() {
        seqDelayTicks = 0
    }

    init {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            val player = client.player ?: return@register
            if (client.gui.screen() != null) return@register
            if (seqDelayTicks > 0) {
                seqDelayTicks--
                return@register
            }

            val entityHit = attackRaycast(player) ?: return@register
            val target = entityHit.entity

            if (target !is LivingEntity) return@register
            if (target.isDeadOrDying) return@register
            if (playersOnly.value && target !is Player) return@register
            if (!TargetFilter.isValidTarget(player, target)) return@register

            if (requireMouseDown.value && !isPhysicalKeyDown(client.options.keyAttack)) return@register
            if (shieldCheck.value && target.isBlocking) return@register
            if (airCrits.value && player.onGround()) return@register

            if (onlyWeapon.value) {
                val mainHand = player.mainHandItem
                val isWeapon = mainHand.`is`(ItemTags.SWORDS) ||
                        mainHand.`is`(ItemTags.AXES) ||
                        mainHand.item is TridentItem
                if (!isWeapon) {
                    return@register
                }
            }

            if (player.getAttackStrengthScale(0.5f) >= 1.0f) {
                client.gameMode?.attack(player, target)
                player.swing(InteractionHand.MAIN_HAND)

                val (lo, hi) = extraDelay.value
                seqDelayTicks = (if (hi > lo) (lo..hi).random() else lo).coerceAtLeast(0)
            }
        }
    }
}
