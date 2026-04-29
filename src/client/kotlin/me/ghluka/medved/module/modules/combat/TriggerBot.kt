package me.ghluka.medved.module.modules.combat

import com.mojang.blaze3d.platform.InputConstants
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.other.TargetFilter
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.tags.ItemTags
import net.minecraft.world.item.TridentItem
import net.minecraft.world.phys.HitResult

object TriggerBot : Module(
    "Trigger Bot", 
    "Automatically attacks when your crosshair is over an enemy", 
    Category.COMBAT
) {

    private val range = float("range", 3.0f, 1.0f, 6.0f)
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

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        if (client.gui.screen() != null) return

        val hitResult = client.hitResult ?: return
        if (hitResult.type != HitResult.Type.ENTITY) return

        val entityHit = hitResult as EntityHitResult
        val target = entityHit.entity

        if (target !is LivingEntity) return
        if (target.isDeadOrDying) return
        if (playersOnly.value && target !is Player) return
        if (player.distanceTo(target) > range.value) return
        if (!TargetFilter.isValidTarget(player, target)) return

        if (requireMouseDown.value && !client.options.keyAttack.isDown) return
        if (shieldCheck.value && target.isBlocking) return
        if (airCrits.value && player.onGround()) return
        
        if (onlyWeapon.value) {
            val mainHand = player.mainHandItem
            val isWeapon = mainHand.`is`(ItemTags.SWORDS) ||
                           mainHand.`is`(ItemTags.AXES) ||
                           mainHand.item is TridentItem
            if (!isWeapon) {
                return
            }
        }

        if (player.getAttackStrengthScale(0.5f - seqDelayTicks.toFloat()) >= 1.0f) {
            val attackKey = InputConstants.getKey(client.options.keyAttack.saveString())
            KeyMapping.click(attackKey)
            
            val (lo, hi) = extraDelay.value
            seqDelayTicks = if (hi > lo) (lo..hi).random() else lo
        }
    }
}
