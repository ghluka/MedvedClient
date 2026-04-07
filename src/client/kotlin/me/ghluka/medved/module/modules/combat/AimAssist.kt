package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.world.Scaffold
import me.ghluka.medved.util.RotationManager
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import kotlin.math.atan2
import kotlin.math.sqrt

object AimAssist : Module(
    name = "Aim Assist",
    description = "Gently nudges your aim toward nearby players without fighting your mouse",
    category = Category.COMBAT
) {

    enum class TargetPoint { BODY, HEAD }

    private val range        = float("range", 5.0f, 1.0f, 12.0f)
    private val fov          = float("fov", 60.0f, 5.0f, 180.0f)
    private val strength     = float("strength", 0.06f, 0.01f, 0.15f)
    private val targetPoint  = enum("target", TargetPoint.HEAD)
    private val onlyAttacking = boolean("only when attacking", true)
    private val players      = boolean("players only", true)
    private val silent       = boolean("silent", false)

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        val level  = client.level  ?: return
        if (client.screen != null) return

        if (Scaffold.isEnabled()) return

        if (onlyAttacking.value && !client.options.keyAttack.isDown) {
            RotationManager.clearRotation()
            return
        }

        val maxRange = range.value.toDouble()
        val halfFov  = fov.value / 2f

        val candidates = level.entitiesForRendering()
            .filterIsInstance<LivingEntity>()
            .filter { e ->
                e !== player &&
                !e.isDeadOrDying &&
                !(players.value && e !is Player) &&
                player.distanceTo(e) <= maxRange
            }

        if (candidates.isEmpty()) {
            RotationManager.clearRotation()
            return
        }

        val best = candidates.minByOrNull { e ->
            val (yaw, pitch) = calcRotation(player, e)
            val dy = Mth.wrapDegrees(yaw   - player.yRot)
            val dp = Mth.wrapDegrees(pitch - player.xRot)
            sqrt((dy * dy + dp * dp).toDouble()).toFloat()
        } ?: run { RotationManager.clearRotation(); return }

        val (targetYaw, targetPitch) = calcRotation(player, best)

        val yawDiff   = Mth.wrapDegrees(targetYaw   - player.yRot)
        val pitchDiff = Mth.wrapDegrees(targetPitch - player.xRot)

        if (Mth.abs(yawDiff) > halfFov || Mth.abs(pitchDiff) > halfFov) {
            RotationManager.clearRotation()
            return
        }

        val nudgeYaw   = yawDiff   * strength.value
        val nudgePitch = pitchDiff * strength.value

        if (silent.value) {
            RotationManager.movementMode = RotationManager.MovementMode.SERVER
            RotationManager.rotationMode  = RotationManager.RotationMode.SERVER
            RotationManager.setTargetRotation(player.yRot + nudgeYaw, player.xRot + nudgePitch)
            RotationManager.snapToTarget()
        } else {
            player.yRot  = player.yRot  + nudgeYaw
            player.xRot  = (player.xRot + nudgePitch).coerceIn(-90f, 90f)
        }
    }

    override fun onDisabled() {
        if (silent.value) RotationManager.clearRotation()
    }

    private fun calcRotation(player: Player, target: LivingEntity): Pair<Float, Float> {
        val aimY = when (targetPoint.value) {
            TargetPoint.HEAD -> target.eyeY - 0.1
            TargetPoint.BODY -> target.y + target.bbHeight * 0.5
        }
        val dx = target.x - player.x
        val dy = aimY - player.eyeY
        val dz = target.z - player.z
        val horizDist = sqrt(dx * dx + dz * dz)
        val yaw   = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = (-Math.toDegrees(atan2(dy, horizDist))).toFloat()
        return yaw to pitch
    }
}
