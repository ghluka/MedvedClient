package me.ghluka.medved.util

import me.ghluka.medved.module.modules.other.Rotations
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Overrides the server-side rotation without affecting the client camera.
 * Call [setTargetRotation] to set a desired server yaw/pitch; the rotation
 * smoothly interpolates toward the target each tick using the client's
 * mouse sensitivity setting, so it looks like natural mouse movement.
 *
 * Perspective/silent-aim behaviour:
 * - The client camera always shows the real mouse rotation.
 * - The server rotation (sent in movement packets) is the spoofed value.
 * - The block outline (minecraft.hitResult) is recomputed each tick using
 *   the spoofed rotation so the player sees what the server "sees".
 * - Movement direction (WASD) continues to follow the real camera yaw.
 *
 * The mixin in [me.ghluka.medved.mixin.client.LocalPlayerMixin] swaps the
 * fields for the duration of [LocalPlayer.sendPosition] only, so exactly
 * one packet per tick is sent with the spoofed values.
 */
object RotationManager {

    private var targetYaw: Float? = null
    private var targetPitch: Float? = null
    private var currentYaw = 0f
    private var currentPitch = 0f

    private var clientYaw = 0f
    private var clientPitch = 0f
    private var overriding = false

    enum class MovementMode { CLIENT, SERVER }

    @JvmField var movementMode: MovementMode = MovementMode.CLIENT

    /** Action to fire inside sendPosition, right after rotation override. */
    @JvmField
    var pendingFireAction: Runnable? = null

    /**
     * allowStrafe: A or D held, keep the strafe component in the packet.
     * allowForward: W or S held, keep the forward component in the packet.
     * When both false (no keys): both components are near-zero anyway.
     * When both true (diagonal): neither is clamped, full diagonal movement passes through.
     */
    @JvmField var allowStrafe: Boolean = false
    @JvmField var allowForward: Boolean = false

    /**
     * When true, the KeyboardInputMixin zeros movement input after
     * KeyboardInput.tick() so aiStep() produces no movement.
     * Set each tick in START_CLIENT_TICK, consumed by the mixin.
     */
    @JvmField var freezeMovement: Boolean = false

    /**
     * When true, the KeyboardInputMixin rebuilds keyPresses with jump=false
     * so aiStep() won't make the player jump. Used to delay jumps during
     * WASD+space bridging until the player reaches the block edge.
     */
    @JvmField var suppressJump: Boolean = false

    /** Set the target rotation to smoothly move toward. */
    fun setTargetRotation(yaw: Float, pitch: Float) {
        if (targetYaw == null) {
            val player = Minecraft.getInstance().player
            if (player != null) {
                currentYaw = player.getYRot()
                currentPitch = player.getXRot()
                clientYaw = player.getYRot()
                clientPitch = player.getXRot()
            }
        }
        targetYaw = yaw
        targetPitch = pitch
    }

    /** Stop overriding — sendPosition will use the real camera rotation. */
    fun clearRotation() {
        targetYaw = null
        targetPitch = null
        movementMode = MovementMode.CLIENT
    }

    /** Whether an override is currently active. */
    @JvmStatic
    fun isActive(): Boolean = targetYaw != null

    /** Current spoofed yaw being sent to the server. */
    @JvmStatic fun getCurrentYaw(): Float = currentYaw

    /** Current spoofed pitch being sent to the server. */
    @JvmStatic fun getCurrentPitch(): Float = currentPitch

    /** Client camera yaw (real mouse rotation, never affected by the server override). */
    @JvmStatic fun getClientYaw(): Float = clientYaw

    /**
     * Recompute minecraft.hitResult using the server rotation so the block
     * outline matches where the server thinks the player is looking.
     * Call once per tick after [tick].
     */
    @JvmStatic
    fun updateHitResult() {
        if (targetYaw == null) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        
        val savedYaw = player.getYRot()
        val savedPitch = player.getXRot()
        player.setYRot(targetYaw!!)
        player.setXRot(targetPitch!!)
        mc.hitResult = player.pick(mc.gameMode?.let { if (player.isCreative) 5.0 else 4.5 } ?: 4.5, 1.0f, false)
        player.setYRot(savedYaw)
        player.setXRot(savedPitch)
    }

    /** Whether the current rotation is within tolerance of the target. */
    fun hasReachedTarget(toleranceDeg: Float = 1f): Boolean {
        val tYaw = targetYaw ?: return true
        val tPitch = targetPitch ?: return true
        return kotlin.math.abs(Mth.wrapDegrees(tYaw - currentYaw)) <= toleranceDeg &&
               kotlin.math.abs(Mth.wrapDegrees(tPitch - currentPitch)) <= toleranceDeg
    }

    /**
     * Only update the pitch target, leaving yaw unchanged.
     * Use when airborne to prevent yaw from drifting due to A/D micro-adjustments.
     * No-op if rotation is not currently active.
     */
    fun setTargetPitchOnly(pitch: Float) {
        if (targetYaw == null) return
        targetPitch = pitch
    }

    /** Whether the yaw alone has converged — pitch changes don't need movement frozen. */
    fun hasYawReachedTarget(toleranceDeg: Float = 1f): Boolean {
        val tYaw = targetYaw ?: return true
        return kotlin.math.abs(Mth.wrapDegrees(tYaw - currentYaw)) <= toleranceDeg
    }

    /** Instantly set current rotation to the target (for time-critical placements). */
    fun snapToTarget() {
        val tYaw = targetYaw ?: return
        val tPitch = targetPitch ?: return
        currentYaw = tYaw
        currentPitch = tPitch
    }

    /**
     * Advances toward the target at maximum believable mouse-flick speed.
     */
    fun flickTick() {
        val tYaw = targetYaw ?: return
        val tPitch = targetPitch ?: return

        val yawDiff = Mth.wrapDegrees(tYaw - currentYaw)
        val pitchDiff = Mth.wrapDegrees(tPitch - currentPitch)
        val dist = sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff)

        if (dist < 0.01f) {
            currentYaw = tYaw
            currentPitch = tPitch
            return
        }

        val sensitivity = Minecraft.getInstance().options.sensitivity().get()
        val f = sensitivity * 0.6 + 0.2
        val degreesPerCount = (f * f * f * 8.0).toFloat()

        // Full-speed step: use 100% of distance, capped at 120 mouse counts.
        val counts = (dist / degreesPerCount).coerceIn(1f, 120f) + Random.nextFloat() * 0.4f - 0.2f
        val step = degreesPerCount * counts

        if (dist <= step || dist < degreesPerCount * 1.5f) {
            currentYaw = tYaw
            currentPitch = tPitch
        } else {
            val ratio = step / dist
            currentYaw += yawDiff * ratio
            currentPitch = (currentPitch + pitchDiff * ratio).coerceIn(-90f, 90f)
        }
    }

    /**
     * Advances the current rotation toward the target.
     * All interpolation parameters (easing, speed, jitter, overshoot) are read from
     * the [Rotations] settings module so users can tune them without recompiling.
     */
    fun tick() {
        val tYaw = targetYaw ?: return
        val tPitch = targetPitch ?: return

        val yawDiff   = Mth.wrapDegrees(tYaw   - currentYaw)
        val pitchDiff = Mth.wrapDegrees(tPitch - currentPitch)
        val dist = sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff)

        if (dist < 0.01f) {
            currentYaw   = tYaw
            currentPitch = tPitch
            applyMicroJitter()
            return
        }

        val sensitivity = Minecraft.getInstance().options.sensitivity().get()
        val f = sensitivity * 0.6 + 0.2
        val degreesPerCount = (f * f * f * 8.0).toFloat()

        // --- per-tick speed fraction (randomised within slider range, then eased) ---
        val (speedLo, speedHi) = Rotations.speed.value
        val rawFraction = if (speedHi > speedLo)
            speedLo + Random.nextFloat() * (speedHi - speedLo)
        else speedLo
        val fraction = Rotations.ease(rawFraction)

        val rawStep = dist * fraction
        val jitter  = Rotations.countJitter.value
        val counts  = (rawStep / degreesPerCount)
            .coerceIn(1f, Rotations.maxCounts.value.toFloat()) +
            Random.nextFloat() * jitter * 2f - jitter
        val step = degreesPerCount * counts

        if (dist <= step || dist < degreesPerCount * 1.5f) {
            currentYaw   = tYaw
            currentPitch = tPitch
        } else {
            // --- optional overshoot ---
            val overshootRoll = Rotations.overshootChance.value
            val doOvershoot   = overshootRoll > 0 && Random.nextInt(100) < overshootRoll
            val effectiveDist = if (doOvershoot) {
                val (osLo, osHi) = Rotations.overshootAmount.value
                val os = if (osHi > osLo) osLo + Random.nextFloat() * (osHi - osLo) else osLo
                dist + os           // move past target; next tick will snap back
            } else dist

            val ratio = step / effectiveDist
            currentYaw    = currentYaw  + yawDiff   * ratio
            currentPitch  = (currentPitch + pitchDiff * ratio).coerceIn(-90f, 90f)
        }

        applyMicroJitter()

        // Update the block crosshair/outline to reflect the server rotation.
        updateHitResult()
    }

    /** Adds tiny random noise to the current rotation (hand-tremor simulation). */
    private fun applyMicroJitter() {
        val mj = Rotations.microJitter.value
        if (mj <= 0f) return
        currentYaw   += (Random.nextFloat() * 2f - 1f) * mj
        currentPitch  = (currentPitch + (Random.nextFloat() * 2f - 1f) * mj).coerceIn(-90f, 90f)
    }

    /**
     * Called after the server's rotation packet (S08 / ClientboundPlayerRotationPacket)
     * has been processed.
     */
    @JvmStatic
    fun restoreClientCamera(player: LocalPlayer) {
        player.setYRot(clientYaw)
        player.setXRot(clientPitch)
    }

    /** Called by mixin at sendPosition HEAD. */
    @JvmStatic
    fun applyOverride(player: LocalPlayer) {
        if (targetYaw == null) {
            pendingFireAction?.let { it.run(); pendingFireAction = null }
            return
        }
        overriding = true
        player.setYRot(currentYaw)
        player.setXRot(currentPitch)

        val mc = Minecraft.getInstance()
        val opts = mc.options
        var forwardInput = 0f
        var strafeInput = 0f
        if (opts.keyUp.isDown) forwardInput += 1f
        if (opts.keyDown.isDown) forwardInput -= 1f
        if (opts.keyLeft.isDown) strafeInput += 1f
        if (opts.keyRight.isDown) strafeInput -= 1f

        if (forwardInput != 0f || strafeInput != 0f) {
            val inputLen = sqrt(forwardInput * forwardInput + strafeInput * strafeInput)
            val nf = (forwardInput / inputLen).toDouble()
            val ns = (strafeInput / inputLen).toDouble()

            // World direction: CLIENT mode follows the real camera yaw; SERVER mode follows the
            // spoofed yaw so the packet movement aligns with where the server thinks we're looking.
            val dirYaw = if (movementMode == MovementMode.SERVER) currentYaw else clientYaw
            val dirRad = Math.toRadians(dirYaw.toDouble())
            val dSin = kotlin.math.sin(dirRad)
            val dCos = kotlin.math.cos(dirRad)
            val worldDx = ns * dCos - nf * dSin
            val worldDz = nf * dCos + ns * dSin

            // Find the closest of 8 possible input directions at the server yaw
            val serverRad = Math.toRadians(currentYaw.toDouble())
            val sSin = kotlin.math.sin(serverRad)
            val sCos = kotlin.math.cos(serverRad)
            val inv = 1.0 / sqrt(2.0)
            val candidates = arrayOf(
                doubleArrayOf(0.0, 1.0),    doubleArrayOf(0.0, -1.0),
                doubleArrayOf(1.0, 0.0),    doubleArrayOf(-1.0, 0.0),
                doubleArrayOf(inv, inv),     doubleArrayOf(-inv, inv),
                doubleArrayOf(inv, -inv),    doubleArrayOf(-inv, -inv),
            )
            var bestDot = -2.0
            var bestDirX = 0.0
            var bestDirZ = 0.0
            for (c in candidates) {
                val ddx = c[0] * sCos - c[1] * sSin
                val ddz = c[1] * sCos + c[0] * sSin
                val dot = worldDx * ddx + worldDz * ddz
                if (dot > bestDot) {
                    bestDot = dot
                    bestDirX = ddx
                    bestDirZ = ddz
                }
            }

            val acc = player as me.ghluka.medved.mixin.client.LocalPlayerAccessor
            val dx = player.x - acc.xLast
            val dz = player.z - acc.zLast
            if (dx * dx + dz * dz > 1e-10) {
                val proj = dx * bestDirX + dz * bestDirZ
                val newX = acc.xLast + proj * bestDirX
                val newZ = acc.zLast + proj * bestDirZ
                if (newX != player.x || newZ != player.z) {
                    player.setPos(newX, player.y, newZ)
                }
            }
        }

        // If movement is frozen (pauseOnRotate) the keys are zeroed so aiStep()
        // adds no new velocity, but the player still has residual momentum from the
        // previous tick. Without this, the server sees a non-zero position delta
        // while the yaw is rotating. Snap position back to xLast/zLast so the
        // packet sends zero movement.
        if (freezeMovement) {
            val acc2 = player as me.ghluka.medved.mixin.client.LocalPlayerAccessor
            val rdx = player.x - acc2.xLast
            val rdz = player.z - acc2.zLast
            if (rdx * rdx + rdz * rdz > 1e-10) {
                player.setPos(acc2.xLast, player.y, acc2.zLast)
            }
        }

        pendingFireAction?.let { it.run(); pendingFireAction = null }
    }

    /** Called by mixin at sendPosition RETURN. */
    @JvmStatic
    fun restoreRotation(player: LocalPlayer) {
        if (targetYaw == null) return
        player.setYRot(clientYaw)
        player.setXRot(clientPitch)
        overriding = false
    }

    /**
     * Called by mixin at LocalPlayer.turn() RETURN.
     */
    @JvmStatic
    fun onTurn(player: LocalPlayer) {
        if (!overriding) {
            clientYaw = player.getYRot()
            clientPitch = player.getXRot()
        }
    }

}
