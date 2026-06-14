package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.world.scaffold.Scaffold
import me.ghluka.medved.module.modules.other.TargetFilter
import me.ghluka.medved.util.RotationManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.tags.ItemTags
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.TridentItem
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
    private val visibilityCheck = boolean("visibility check", true)
    private val playersOnly = boolean("players only", true)
    val autoBlock   = boolean("auto block", true)
    private val autoWeapon  = boolean("auto weapon", false)

    var target: LivingEntity? = null
    private var accumulator = 0.0f
    private var targetCps   = 12.0f
    private var seqDelayTicks = 0
    private var ownsRotation = false

    @JvmField var attackedThisTick = false

    override fun onEnabled() {
        accumulator = 0.0f
        targetCps = pickCps()
        target = null
        seqDelayTicks = 0
        ownsRotation = false
        attackedThisTick = false
    }

    override fun onDisabled() {
        clearAura()
    }

    private fun clearAura() {
        if (ownsRotation && !me.ghluka.medved.module.modules.combat.KnockbackDisplacement.rotationHeld) {
            RotationManager.clearRotation()
        }
        target = null
        ownsRotation = false
        attackedThisTick = false
    }

    init {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (!isEnabled()) return@register
            val player = client.player ?: return@register
            val level = client.level ?: return@register
            if (client.gui.screen() != null) return@register

            val maxRange = range.value.toDouble()
            val halfFov = fov.value / 2f

            val candidates = level.entitiesForRendering()
                .filterIsInstance<LivingEntity>()
                .filter { e ->
                    e !== player &&
                            !e.isDeadOrDying &&
                            !(playersOnly.value && e !is Player) &&
                            player.distanceTo(e) <= maxRange &&
                            TargetFilter.isValidTarget(player, e) &&
                            (!visibilityCheck.value || player.hasLineOfSight(e))
                }
                .filter { e ->
                    val (yaw, pitch) = calcRotation(player, e)
                    Mth.abs(Mth.wrapDegrees(yaw - player.yRot)) <= halfFov &&
                            Mth.abs(Mth.wrapDegrees(pitch - player.xRot)) <= halfFov
                }

            val bestTarget = candidates.minByOrNull { e ->
                val (yaw, pitch) = calcRotation(player, e)
                val dy = Mth.wrapDegrees(yaw - player.yRot)
                val dp = Mth.wrapDegrees(pitch - player.xRot)
                sqrt((dy * dy + dp * dp).toDouble()).toFloat()
            }

            if (targetMode.value == TargetMode.SINGLE && target != null && candidates.contains(target)) {
            } else {
                target = bestTarget
            }

            if (target == null) {
                clearAura()
                return@register
            }

            if (autoWeapon.value) {
                val bestSlot = findBestWeapon(player)
                if (bestSlot != -1 && player.inventory.selectedSlot != bestSlot) {
                    player.inventory.selectedSlot = bestSlot
                }
            }

            val currentTarget = target ?: return@register
            if (!canUseAuraRotation()) return@register
            updateAuraRotation(player, currentTarget)
            if (!isAimedAtTarget(player, currentTarget)) return@register

            when (mode.value) {
                Mode.CPS -> {
                    accumulator += targetCps / 20.0f
                    while (accumulator >= 1.0f) {
                        accumulator -= 1.0f
                        performNormalAttack(client)
                        targetCps = pickCps()
                    }
                }

                Mode.SEQUENTIAL -> {
                    if (seqDelayTicks > 0) {
                        seqDelayTicks--
                        return@register
                    }
                    if (player.getAttackStrengthScale(0.5f) >= 1.0f) {
                        performNormalAttack(client)
                        val (lo, hi) = extraDelay.value
                        seqDelayTicks = (if (hi > lo) (lo..hi).random() else lo).coerceAtLeast(0)
                    }
                }
            }
        }
    }

    private fun performNormalAttack(client: Minecraft) {
        val player = client.player ?: return
        val currentTarget = target ?: return
        if (autoBlock.value) {
            AutoBlock.prepareAuraSwing(client)
        }
        client.gameMode?.attack(player, currentTarget)
        player.swing(InteractionHand.MAIN_HAND)
        attackedThisTick = true
    }

    override fun onLevelRender(ctx: LevelRenderContext) {
        val player = Minecraft.getInstance().player ?: return
        val currentTarget = target ?: return

        if (!canUseAuraRotation()) {
            return
        }

        updateAuraRotation(player, currentTarget)
    }

    private fun updateAuraRotation(player: Player, currentTarget: LivingEntity) {
        val (targetYaw, targetPitch) = calcRotation(player, currentTarget)
        RotationManager.perspective = true
        RotationManager.movementMode = RotationManager.MovementMode.CLIENT
        RotationManager.rotationMode  = RotationManager.RotationMode.CLIENT

        RotationManager.setTargetRotation(targetYaw, targetPitch)
        ownsRotation = true
        RotationManager.quickTick(smoothSpeed.value)
    }

    private fun canUseAuraRotation(): Boolean =
        !((Scaffold.isEnabled() && RotationManager.isActive()) ||
                me.ghluka.medved.module.modules.combat.KnockbackDisplacement.rotationHeld ||
                (me.ghluka.medved.module.modules.world.BedBreaker.isEnabled() && me.ghluka.medved.module.modules.world.BedBreaker.pendingHitPos != null) ||
                (me.ghluka.medved.module.modules.world.ChestAura.isEnabled() && RotationManager.isActive()) ||
                me.ghluka.medved.module.modules.world.Clutch.isActivelyPlacing)

    private fun isAimedAtTarget(player: Player, currentTarget: LivingEntity): Boolean {
        val hit = attackRaycast(player) { it === currentTarget } ?: return false
        return hit.entity === currentTarget
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
