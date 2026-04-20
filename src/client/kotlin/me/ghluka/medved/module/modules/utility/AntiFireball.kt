package me.ghluka.medved.module.modules.utility

import me.ghluka.medved.module.Module
import me.ghluka.medved.util.RotationManager
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.player.Player
import net.minecraft.util.Mth
import kotlin.math.atan2
import kotlin.math.sqrt

object AntiFireball : Module("Anti Fireball", "Automatically aims and swings at incoming fireballs to deflect them", Category.UTILITY) {
    val angleLimit = float("angle limit", 180f, 10f, 360f)
    val aimSpeed = float("aim speed", 30f, 1f, 100f)
    val stopMovement = boolean("stop movement", true)
    val moveOnFinish = boolean("move on finish", true)
    val silentAim = boolean("silent aim", true)

    private var target: Projectile? = null
    private var stoppedMovement = false

    override fun onDisabled() {
        clearAura()
        if (stoppedMovement && moveOnFinish.value) {
            resumeMovement()
        } else { stoppedMovement = false }
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

        val range = 5.0
        val maxFov = angleLimit.value / 2f

        val candidates = level.entitiesForRendering()
            .filterIsInstance<Projectile>()
            .filter { e ->
                val t = e.type.toString().lowercase()
                (t.contains("fireball") || t.contains("ghast")) && player.distanceTo(e) <= range
            }

        val bestTarget = candidates.minByOrNull { e ->
            val (yaw, pitch) = calcRotation(player, e)
            val dy = Mth.wrapDegrees(yaw - player.yRot)
            val dp = Mth.wrapDegrees(pitch - player.xRot)
            if (Mth.abs(dy) > maxFov || Mth.abs(dp) > maxFov) 1000f
            else sqrt((dy * dy + dp * dp).toDouble()).toFloat()
        }

        target = bestTarget

        if (target == null) {
            clearAura()
            if (stoppedMovement && moveOnFinish.value) {
                resumeMovement()
            }
            return
        }

        if (stopMovement.value) {
            client.options.keyUp.setDown(false)
            client.options.keyDown.setDown(false)
            client.options.keyLeft.setDown(false)
            client.options.keyRight.setDown(false)
            client.options.keyJump.setDown(false)
            stoppedMovement = true
        }

        client.gameMode?.attack(player, target!!)
        player.swing(InteractionHand.MAIN_HAND)
    }

    private fun resumeMovement() {
        val client = Minecraft.getInstance()
        stoppedMovement = false
    }

    override fun onLevelRender(ctx: LevelRenderContext) {
        val player = Minecraft.getInstance().player ?: return
        val currentTarget = target ?: return

        val (targetYaw, targetPitch) = calcRotation(player, currentTarget)

        if (silentAim.value) {
            RotationManager.perspective = true
            RotationManager.movementMode = RotationManager.MovementMode.CLIENT
            RotationManager.rotationMode = RotationManager.RotationMode.CLIENT
            RotationManager.setTargetRotation(targetYaw, targetPitch)
            RotationManager.quickTick(aimSpeed.value)
        } else {
            player.yRot = targetYaw
            player.xRot = targetPitch
        }
    }

    private fun calcRotation(player: Player, t: Projectile): Pair<Float, Float> {
        val dx = t.x - player.x
        val dy = t.y - player.eyeY
        val dz = t.z - player.z
        val horizDist = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = (-Math.toDegrees(atan2(dy, horizDist))).toFloat()
        return yaw to pitch
    }
}
