package me.ghluka.medved.module.modules.combat

import com.mojang.blaze3d.platform.InputConstants
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.world.Scaffold
import me.ghluka.medved.module.modules.other.TargetFilter
import me.ghluka.medved.util.RotationManager
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ShieldItem
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random

object KillAura : Module(
    name = "Kill Aura",
    description = "Silently aims and attacks enemies when they enter your range",
    category = Category.COMBAT
) {
    private val range       = float("range", 4.0f, 1.0f, 8.0f)
    private val fov         = float("fov", 180.0f, 10.0f, 360.0f)
    private val cps         = floatRange("cps", 10.0f to 13.0f, 1.0f, 20.0f)
    private val smoothSpeed = float("smoothness", 30.0f, 1.0f, 100.0f)
    private val playersOnly = boolean("players only", true)
    private val autoBlock   = boolean("auto block", true)

    private var target: LivingEntity? = null
    private var accumulator = 0.0f
    private var targetCps   = 12.0f
    private var wasBlocking = false
    private var unblockNextTick = false

    override fun onEnabled() {
        accumulator = 0.0f
        targetCps = pickCps()
        target = null
        wasBlocking = false
        unblockNextTick = false
    }

    override fun onDisabled() {
        clearAura()
        val client = Minecraft.getInstance()
        if (wasBlocking || unblockNextTick) {
            client.options.keyUse.setDown(false)
            wasBlocking = false
            unblockNextTick = false
        }
    }

    private fun clearAura() {
        if (!me.ghluka.medved.module.modules.combat.KnockbackDisplacement.rotationHeld) {
            RotationManager.clearRotation()
        }
        target = null
    }

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        val level = client.level ?: return
        if (client.screen != null) return

        val maxRange = range.value.toDouble()
        val halfFov = fov.value / 2f

        val candidates = level.entitiesForRendering()
            .filterIsInstance<LivingEntity>()
            .filter { e ->
                e !== player &&
                !e.isDeadOrDying &&
                !(playersOnly.value && e !is Player) &&
                player.distanceTo(e) <= maxRange &&
                TargetFilter.isValidTarget(player, e)
            }

        target = candidates.minByOrNull { e ->
            val (yaw, pitch) = calcRotation(player, e)
            val dy = Mth.wrapDegrees(yaw - player.yRot)
            val dp = Mth.wrapDegrees(pitch - player.xRot)
            if (Mth.abs(dy) > halfFov || Mth.abs(dp) > halfFov) 1000f
            else sqrt((dy * dy + dp * dp).toDouble()).toFloat()
        }

        if (target == null) {
            clearAura()
            if (wasBlocking || unblockNextTick) {
                client.options.keyUse.setDown(false)
                wasBlocking = false
                unblockNextTick = false
            }
            return
        }

        val mainHandItem = player.mainHandItem.item
        val offHandItem = player.offhandItem.item
        val isShield = mainHandItem is ShieldItem || offHandItem is ShieldItem
        val isSwordBlock = !isShield && player.mainHandItem.components.has(BLOCKS_ATTACKS)

        if (unblockNextTick) {
            client.options.keyUse.setDown(false)
            unblockNextTick = false
            wasBlocking = false
        }

        if (autoBlock.value && isShield) {
            if (!wasBlocking) {
                client.options.keyUse.setDown(true)
                wasBlocking = true
            }
        } else if (wasBlocking && !unblockNextTick && !isShield) {
            client.options.keyUse.setDown(false)
            wasBlocking = false
        }

        accumulator += targetCps / 20.0f
        while (accumulator >= 1.0f) {
            accumulator -= 1.0f
            
            if (wasBlocking && isShield) {
                client.options.keyUse.setDown(false)
                wasBlocking = false
            }
            
            val attackKey = InputConstants.getKey(client.options.keyAttack.saveString())
            KeyMapping.click(attackKey)

            
            if (autoBlock.value) {
                val useKey = InputConstants.getKey(client.options.keyUse.saveString())
                if (isShield) {
                    KeyMapping.click(useKey)
                    client.options.keyUse.setDown(true)
                    wasBlocking = true
                } else if (isSwordBlock) {
                    KeyMapping.click(useKey)
                    client.options.keyUse.setDown(true)
                    wasBlocking = true
                    unblockNextTick = true
                }
            }
            targetCps = pickCps()
        }
    }

    override fun onLevelRender(ctx: LevelRenderContext) {
        val player = Minecraft.getInstance().player ?: return
        val currentTarget = target ?: return

        if (Scaffold.isEnabled() ||
            me.ghluka.medved.module.modules.combat.KnockbackDisplacement.rotationHeld ||
            (me.ghluka.medved.module.modules.world.BedBreaker.isEnabled() && me.ghluka.medved.module.modules.world.BedBreaker.pendingHitPos != null) ||
            (me.ghluka.medved.module.modules.world.ChestAura.isEnabled() && RotationManager.isActive()) ||
            (me.ghluka.medved.module.modules.world.Clutch.isEnabled() && RotationManager.isActive())) {
            return
        }

        val (targetYaw, baseTargetPitch) = calcRotation(player, currentTarget)

        val time = System.currentTimeMillis()
        val pitchJitter = (kotlin.math.sin(time / 150.0) * 1.5).toFloat()
        val targetPitch = baseTargetPitch + pitchJitter

        RotationManager.perspective = true
        RotationManager.movementMode = RotationManager.MovementMode.CLIENT
        RotationManager.rotationMode  = RotationManager.RotationMode.CLIENT
        RotationManager.setTargetRotation(targetYaw, targetPitch)
        RotationManager.quickTick(smoothSpeed.value)
    }

    private fun calcRotation(player: Player, t: LivingEntity): Pair<Float, Float> {
        val aimY = t.y + t.bbHeight * 0.5
        val dx = t.x - player.x
        val dy = aimY - player.eyeY
        val dz = t.z - player.z
        val horizDist = sqrt(dx * dx + dz * dz)
        val yaw   = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = (-Math.toDegrees(atan2(dy, horizDist))).toFloat()
        return yaw to pitch
    }

    private fun pickCps(): Float {
        val (lo, hi) = cps.value
        return if (hi > lo) lo + Random.nextFloat() * (hi - lo) else lo
    }

    override fun hudInfo(): String {
        val (lo, hi) = cps.value
        return if (hi > lo) "%.1f-%.1f cps".format(lo, hi) else "%.1f cps".format(lo)
    }
}