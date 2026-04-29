package me.ghluka.medved.module.modules.combat

import com.mojang.blaze3d.platform.InputConstants
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.world.Scaffold
import me.ghluka.medved.module.modules.other.TargetFilter
import me.ghluka.medved.util.RotationManager
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.tags.ItemTags
import net.minecraft.world.item.TridentItem
import net.minecraft.world.item.ShieldItem
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random

object SilentAura : Module(
    name = "Silent Aura",
    description = "Silently aims and attacks enemies when they enter your range",
    category = Category.COMBAT
) {
    enum class Mode {
        CPS, SEQUENTIAL
    }

    enum class TargetMode {
        SINGLE, SWITCH
    }

    private val mode = enum("mode", Mode.CPS)
    private val targetMode = enum("targeting", TargetMode.SWITCH)

    private val range       = float("range", 4.0f, 1.0f, 8.0f)
    private val fov         = float("fov", 180.0f, 10.0f, 360.0f)
    private val cps         = floatRange("cps", 10.0f to 13.0f, 1.0f, 20.0f).also {
        it.visibleWhen = { mode.value == Mode.CPS }
    }
    private val extraDelay  = intRange("extra delay", 0 to 2, -5, 5).also {
        it.visibleWhen = { mode.value == Mode.SEQUENTIAL }
    }
    private val smoothSpeed = float("smoothness", 30.0f, 1.0f, 100.0f)
    private val playersOnly = boolean("players only", true)
    private val autoBlock   = boolean("auto block", true)
    private val autoWeapon  = boolean("auto weapon", false)

    private var target: LivingEntity? = null
    private var accumulator = 0.0f
    private var targetCps   = 12.0f
    private var wasBlocking = false
    private var unblockNextTick = false
    private var seqDelayTicks = 0

    override fun onEnabled() {
        accumulator = 0.0f
        targetCps = pickCps()
        target = null
        wasBlocking = false
        unblockNextTick = false
        seqDelayTicks = 0
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
        if (client.gui.screen() != null) return

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

        val bestTarget = candidates.minByOrNull { e ->
            val (yaw, pitch) = calcRotation(player, e)
            val dy = Mth.wrapDegrees(yaw - player.yRot)
            val dp = Mth.wrapDegrees(pitch - player.xRot)
            if (Mth.abs(dy) > halfFov || Mth.abs(dp) > halfFov) 1000f
            else sqrt((dy * dy + dp * dp).toDouble()).toFloat()
        }

        if (targetMode.value == TargetMode.SINGLE && target != null && candidates.contains(target)) {
        } else {
            target = bestTarget
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

        if (autoWeapon.value) {
            val bestSlot = findBestWeapon(player)
            if (bestSlot != -1 && player.inventory.selectedSlot != bestSlot) {
                player.inventory.selectedSlot = bestSlot
            }
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

        when (mode.value) {
            Mode.CPS -> {
                accumulator += targetCps / 20.0f
                while (accumulator >= 1.0f) {
                    accumulator -= 1.0f
                    performNormalAttack(client, isShield, isSwordBlock)
                    targetCps = pickCps()
                }
            }
            Mode.SEQUENTIAL -> {
                if (player.getAttackStrengthScale(0.5f - seqDelayTicks.toFloat()) >= 1.0f) {
                    performNormalAttack(client, isShield, isSwordBlock)
                    val (lo, hi) = extraDelay.value
                    seqDelayTicks = if (hi > lo) (lo..hi).random() else lo
                }
            }
        }
    }

    private fun performNormalAttack(client: Minecraft, isShield: Boolean, isSwordBlock: Boolean) {
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

    private fun findBestWeapon(player: Player): Int {
        var bestSlot = -1
        
        for (i in 0..8) {
            val itemStack = player.inventory.getItem(i)
            if (itemStack.isEmpty) continue
            val isWeapon = itemStack.`is`(ItemTags.SWORDS) || itemStack.`is`(ItemTags.AXES) || 
                           itemStack.item is TridentItem
            
            if (isWeapon) {
                 return i
            }
        }
        return bestSlot
    }

    private fun pickCps(): Float {
        val (lo, hi) = cps.value
        return if (hi > lo) lo + Random.nextFloat() * (hi - lo) else lo
    }

    override fun hudInfo(): String {
        return when (mode.value) {
            Mode.CPS -> {
                val (lo, hi) = cps.value
                if (hi > lo) "%.1f-%.1f cps".format(lo, hi) else "%.1f cps".format(lo)
            }
            Mode.SEQUENTIAL -> "sequential"
        }
    }
}