package me.ghluka.medved.module.modules.movement

import me.ghluka.medved.module.Module
import me.ghluka.medved.util.RotationManager
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec2
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Spin : Module(
    name = "Spin",
    description = "Makes you spin fast to confuse your opponents",
    category = Category.MOVEMENT,
) {
    enum class Direction { RIGHT, LEFT }

    private val speed = float("speed", 620f, 30f, 1440f)
    private val direction = enum("direction", Direction.RIGHT)
    private val pitch = float("pitch", 0f, -90f, 90f)
    private val movementCorrection = boolean("movement correction", true)
    private val jumpTiming = boolean("jump timing", true).also {
        it.visibleWhen = { movementCorrection.value }
    }
    private val jumpWindow = float("jump window", 35f, 5f, 90f).also {
        it.visibleWhen = { movementCorrection.value && jumpTiming.value }
    }

    private var spinYaw = 0f

    override fun onEnabled() {
        val player = Minecraft.getInstance().player
        spinYaw = player?.yRot ?: 0f
        RotationManager.clearRotation(RotationManager.SPIN_ROTATION_OWNER)
    }

    override fun onDisabled() {
        RotationManager.clearRotation(RotationManager.SPIN_ROTATION_OWNER)
    }

    override fun onTick(client: Minecraft) {
        client.player ?: return
        if (RotationManager.hasExternalRotation(RotationManager.SPIN_ROTATION_OWNER)) {
            spinYaw = RotationManager.getCurrentYaw()
            return
        }
        val sign = if (direction.value == Direction.RIGHT) 1f else -1f
        spinYaw += sign * speed.value / 20f

        RotationManager.perspective = true
        RotationManager.movementMode = RotationManager.MovementMode.CLIENT
        RotationManager.rotationMode = RotationManager.RotationMode.SERVER
        RotationManager.setTargetRotation(spinYaw, pitch.value, RotationManager.SPIN_ROTATION_OWNER)
        RotationManager.quickTick(speed.value / 20f + 8f)
        RotationManager.physicsYawOverride = RotationManager.getCurrentYaw()
    }

    @JvmStatic
    fun correctInput(input: Input): Input {
        val corrected = correctedMovement(input) ?: return input

        return Input(
            corrected.forward > 0f,
            corrected.forward < 0f,
            corrected.strafe > 0f,
            corrected.strafe < 0f,
            corrected.jump,
            input.shift(),
            input.sprint(),
        )
    }

    @JvmStatic
    fun correctMoveVector(input: Input): Vec2 {
        val corrected = correctedMovement(input) ?: return moveVectorFor(input)
        return Vec2(corrected.strafe, corrected.forward)
    }

    private fun correctedMovement(input: Input): CorrectedMovement? {
        if (!isEnabled() || !movementCorrection.value) return null
        if (RotationManager.hasExternalRotation(RotationManager.SPIN_ROTATION_OWNER)) return null

        var forward = 0f
        var strafe = 0f
        if (input.forward()) forward += 1f
        if (input.backward()) forward -= 1f
        if (input.left()) strafe += 1f
        if (input.right()) strafe -= 1f

        if (forward == 0f && strafe == 0f) {
            return null
        }

        val desired = desiredWorldDirection(forward, strafe)
        val corrected = closestServerInput(desired.first, desired.second)
        val jump = input.jump() && shouldAllowJump(desired.first, desired.second)
        return CorrectedMovement(corrected.strafe, corrected.forward, jump)
    }

    private fun moveVectorFor(input: Input): Vec2 {
        var forward = 0f
        var strafe = 0f
        if (input.forward()) forward += 1f
        if (input.backward()) forward -= 1f
        if (input.left()) strafe += 1f
        if (input.right()) strafe -= 1f

        if (forward != 0f && strafe != 0f) {
            val diagonal = (1f / sqrt(2f))
            forward *= diagonal
            strafe *= diagonal
        }

        return Vec2(strafe, forward)
    }

    private fun desiredWorldDirection(forward: Float, strafe: Float): Pair<Double, Double> {
        val len = sqrt(forward * forward + strafe * strafe).coerceAtLeast(0.001f)
        val nf = (forward / len).toDouble()
        val ns = (strafe / len).toDouble()
        val yawRad = Math.toRadians(RotationManager.getClientYaw().toDouble())
        val sin = sin(yawRad)
        val cos = cos(yawRad)
        val worldDx = ns * cos - nf * sin
        val worldDz = nf * cos + ns * sin
        return worldDx to worldDz
    }

    private fun closestServerInput(worldDx: Double, worldDz: Double): CorrectedInput {
        val yawRad = Math.toRadians(serverMovementYaw().toDouble())
        val sin = sin(yawRad)
        val cos = cos(yawRad)
        val inv = 1.0 / sqrt(2.0)
        val candidates = arrayOf(
            CorrectedInput(1f, 0f),
            CorrectedInput(-1f, 0f),
            CorrectedInput(0f, 1f),
            CorrectedInput(0f, -1f),
            CorrectedInput(inv.toFloat(),  inv.toFloat()),
            CorrectedInput((-inv).toFloat(), inv.toFloat()),
            CorrectedInput(inv.toFloat(),  (-inv).toFloat()),
            CorrectedInput((-inv).toFloat(), (-inv).toFloat()),
        )

        var best = candidates[0]
        var bestDot = -2.0
        for (candidate in candidates) {
            val dx = candidate.strafe * cos - candidate.forward * sin
            val dz = candidate.forward * cos + candidate.strafe * sin
            val dot = worldDx * dx + worldDz * dz
            if (dot > bestDot) {
                bestDot = dot
                best = candidate
            }
        }
        return best
    }

    private fun shouldAllowJump(worldDx: Double, worldDz: Double): Boolean {
        if (!jumpTiming.value) return true
        val intendedYaw = Math.toDegrees(atan2(-worldDx, worldDz)).toFloat()
        val diff = kotlin.math.abs(Mth.wrapDegrees(serverMovementYaw() - intendedYaw))
        return diff <= jumpWindow.value
    }

    private fun serverMovementYaw(): Float {
        val physicsYaw = RotationManager.physicsYawOverride
        return if (physicsYaw.isFinite()) physicsYaw else RotationManager.getCurrentYaw()
    }

    private data class CorrectedInput(
        val strafe: Float,
        val forward: Float,
    )

    private data class CorrectedMovement(
        val strafe: Float,
        val forward: Float,
        val jump: Boolean,
    )
}
