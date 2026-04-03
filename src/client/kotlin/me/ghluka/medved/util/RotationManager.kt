package me.ghluka.medved.util

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

    // True camera rotation, tracked independently from player.yRot/xRot.
    // Updated only from turn() (mouse input) so server look-sync packets
    // cannot corrupt it.
    private var clientYaw = 0f
    private var clientPitch = 0f
    private var overriding = false

    /** Action to fire inside sendPosition, right after rotation override. */
    @JvmField
    var pendingFireAction: Runnable? = null

    /**
     * Set by ScaffoldModule each tick.
     * allowStrafe: A or D held — keep the strafe component in the packet.
     * allowForward: W or S held — keep the forward component in the packet.
     * When both false (no keys), both components are near-zero anyway.
     * When both true (diagonal), neither is clamped — full diagonal movement passes through.
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
            // First call: seed both tracking variables from the player's
            // actual rotation. From here on, clientYaw/Pitch are updated
            // only from turn() (mouse), not from player.getYRot().
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
        // Let MC recompute hit result normally next tick
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
        // Use the FINAL target rotation (not the interpolated current) so the
        // hitResult is always the correct block/face from the very first tick,
        // regardless of how far rotation has visually converged.
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
     * Advances the current rotation toward the target.
     * Uses distance-proportional speed: moves 50-80% of the remaining
     * distance per tick, like a real mouse flick (fast initial snap,
     * then fine-tune). Quantized to sensitivity mouse-counts with
     * fractional jitter so deltas aren't clean integers.
     */
    fun tick() {
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

        // Move 50-80% of remaining distance (like a real flick: fast then fine-tune)
        val fraction = 0.5f + Random.nextFloat() * 0.3f
        val rawStep = dist * fraction
        // Quantize to sensitivity mouse counts + jitter
        val counts = (rawStep / degreesPerCount).coerceIn(1f, 80f) + Random.nextFloat() * 0.6f - 0.3f
        val step = degreesPerCount * counts

        if (dist <= step || dist < degreesPerCount * 1.5f) {
            currentYaw = tYaw
            currentPitch = tPitch
        } else {
            val ratio = step / dist
            currentYaw += yawDiff * ratio
            currentPitch = (currentPitch + pitchDiff * ratio).coerceIn(-90f, 90f)
        }

        // Update the block crosshair/outline to reflect the server rotation.
        updateHitResult()
    }

    /**
     * Called after the server's rotation packet (S08 / ClientboundPlayerRotationPacket)
     * has been processed. The packet may legitimately update our yRot/xRot, but we must
     * restore the client camera to our independently-tracked values so the player
     * doesn't see a forced look-snap.
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
            // Even without rotation override, still fire pending action
            pendingFireAction?.let { it.run(); pendingFireAction = null }
            return
        }
        overriding = true
        player.setYRot(currentYaw)
        player.setXRot(currentPitch)

        // Drift correction: aiStep() computed the position delta using the CLIENT
        // camera yaw, but the packet will carry the SERVER (spoofed) yaw. Grim
        // simulates movement using the sent yaw and checks that the position
        // delta matches one of the 8 possible input directions at that yaw.
        // When client yaw is even a few degrees off from the server yaw, the
        // actual movement direction doesn't match any valid input → simulation flag.
        //
        // Fix: find the closest valid movement direction at the server yaw (from
        // the 8 input combos) and project the actual delta onto it. This removes
        // the perpendicular angle error while preserving movement speed.
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

            // World direction the player intended (input at client camera yaw)
            val clientRad = Math.toRadians(clientYaw.toDouble())
            val cSin = kotlin.math.sin(clientRad)
            val cCos = kotlin.math.cos(clientRad)
            val worldDx = ns * cCos - nf * cSin
            val worldDz = nf * cCos + ns * cSin

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

        // Fire AFTER rotation override so BlockPlace uses spoofed rotation
        pendingFireAction?.let { it.run(); pendingFireAction = null }
    }

    /** Called by mixin at sendPosition RETURN. */
    @JvmStatic
    fun restoreRotation(player: LocalPlayer) {
        if (targetYaw == null) return
        // Restore to our independently tracked client rotation — NOT player.getYRot()
        // which may have been corrupted by a server-forced look sync packet.
        player.setYRot(clientYaw)
        player.setXRot(clientPitch)
        overriding = false
    }

    /**
     * Called by mixin at LocalPlayer.turn() RETURN.
     * This is the ONLY place clientYaw/clientPitch are updated: from real
     * mouse movement. Server look-sync packets cannot interfere here.
     */
    @JvmStatic
    fun onTurn(player: LocalPlayer) {
        if (!overriding) {
            clientYaw = player.getYRot()
            clientPitch = player.getXRot()
        }
    }

}
