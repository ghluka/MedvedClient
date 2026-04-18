package me.ghluka.medved.util

import me.ghluka.medved.mixin.client.CameraMixin
import me.ghluka.medved.module.modules.other.Rotations
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

object RotationManager {

    private var targetYaw: Float? = null
    private var targetPitch: Float? = null
    private var currentYaw = 0f
    private var currentPitch = 0f

    private var clientYaw = 0f
    private var clientPitch = 0f
    private var overriding = false

    @JvmField var perspective: Boolean = false
    @JvmField var firstTime: Boolean = true

    enum class MovementMode { CLIENT, SERVER }
    enum class RotationMode { SERVER, CLIENT }
    @JvmField var movementMode: MovementMode = MovementMode.SERVER
    @JvmField var rotationMode: RotationMode = RotationMode.SERVER

    @JvmField var physicsYawOverride: Float = Float.NaN

    @JvmField var skipPositionSnap: Boolean = false

    @JvmField
    var pendingFireAction: Runnable? = null

    @JvmField var allowStrafe: Boolean = false
    @JvmField var allowForward: Boolean = false

    @JvmField var freezeMovement: Boolean = false

    @JvmField var suppressJump: Boolean = false

    fun setTargetRotation(yaw: Float, pitch: Float) {
        val player = Minecraft.getInstance().player

        if (perspective && firstTime && player is me.ghluka.medved.util.CameraOverriddenEntity) {
            firstTime = false
            player.`medved$setCameraYaw`(player.getYRot())
            player.`medved$setCameraPitch`(player.getXRot())
        }

        if (targetYaw == null && player != null) {
            currentYaw = player.getYRot()
            currentPitch = player.getXRot()
            clientYaw = player.getYRot()
            clientPitch = player.getXRot()
        }
        targetYaw = yaw
        targetPitch = pitch
    }

    fun clearRotation() {
        //perspective = false
        targetYaw = null
        targetPitch = null
        movementMode = MovementMode.CLIENT
        rotationMode = RotationMode.CLIENT
        physicsYawOverride = Float.NaN
        skipPositionSnap = false
        firstTime = true
        val mc = Minecraft.getInstance()
        val player = mc.player
        if (player != null) {
            if (perspective) {
                restoreClientCamera(player)
            }
            
            val currentClientYaw = player.getYRot()
            val diff = currentClientYaw - currentYaw
            val periods = Math.round(diff / 360.0f)
            if (periods != 0) {
                player.setYRot(currentClientYaw - periods * 360.0f)
                clientYaw = player.getYRot()
            }
        }
        perspective = false
    }

    @JvmStatic
    fun isActive(): Boolean = targetYaw != null

    @JvmStatic fun getCurrentYaw(): Float = currentYaw

    @JvmStatic fun getCurrentPitch(): Float = currentPitch

    @JvmStatic fun getClientYaw(): Float {
        val mc = Minecraft.getInstance()
        val player = mc.player
        if (perspective
            && player is me.ghluka.medved.util.CameraOverriddenEntity) {
            return (player as me.ghluka.medved.util.CameraOverriddenEntity).`medved$getCameraYaw`()
        }
        return clientYaw
    }

    @JvmStatic fun getClientPitch(): Float {
        val mc = Minecraft.getInstance()
        val player = mc.player
        if (perspective
            && player is me.ghluka.medved.util.CameraOverriddenEntity) {
            return (player as me.ghluka.medved.util.CameraOverriddenEntity).`medved$getCameraPitch`()
        }
        return clientPitch
    }

    @JvmStatic
    fun updateHitResult() {
        if (targetYaw == null) return
        if (rotationMode == RotationMode.CLIENT) return
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

    fun hasReachedTarget(toleranceDeg: Float = 1f): Boolean {
        val tYaw = targetYaw ?: return true
        val tPitch = targetPitch ?: return true
        return kotlin.math.abs(Mth.wrapDegrees(tYaw - currentYaw)) <= toleranceDeg &&
               kotlin.math.abs(Mth.wrapDegrees(tPitch - currentPitch)) <= toleranceDeg
    }

    fun setTargetPitchOnly(pitch: Float) {
        if (targetYaw == null) return
        targetPitch = pitch
    }

    fun hasYawReachedTarget(toleranceDeg: Float = 1f): Boolean {
        val tYaw = targetYaw ?: return true
        return kotlin.math.abs(Mth.wrapDegrees(tYaw - currentYaw)) <= toleranceDeg
    }

    fun snapToTarget() {
        val tYaw = targetYaw ?: return
        val tPitch = targetPitch ?: return
        
        val sensitivity = Minecraft.getInstance().options.sensitivity().get()
        val f = sensitivity * 0.6 + 0.2
        val gcdMulti = (f * f * f * 8.0).toFloat() * 0.15f
        
        snapToGcdTarget(tYaw, tPitch, gcdMulti)
        updateClientIfRequired()
    }

    fun flickTick() {
        quickTick(20000f)
    }

    fun quickTick(maxDeg: Float = 50f) {
        val tYaw   = targetYaw   ?: return
        val tPitch = targetPitch ?: return

        val yawDiff   = Mth.wrapDegrees(tYaw   - currentYaw)
        val pitchDiff = Mth.wrapDegrees(tPitch - currentPitch)
        val dist = sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff)

        val sensitivity = Minecraft.getInstance().options.sensitivity().get()
        val f = sensitivity * 0.6 + 0.2
        val gcdMulti = (f * f * f * 8.0).toFloat() * 0.15f

        if (dist <= maxDeg) {
            snapToGcdTarget(tYaw, tPitch, gcdMulti)
        } else {
            val ratio = maxDeg / dist
            val mouseDX = Math.round((yawDiff * ratio) / gcdMulti)
            val mouseDY = Math.round((pitchDiff * ratio) / gcdMulti)
            
            currentYaw += mouseDX * gcdMulti
            currentPitch = (currentPitch + mouseDY * gcdMulti).coerceIn(-90f, 90f)
        }

        applyMicroJitter()
        updateClientIfRequired()
    }

    fun tick() {
        val tYaw = targetYaw ?: return
        val tPitch = targetPitch ?: return

        val yawDiff   = Mth.wrapDegrees(tYaw   - currentYaw)
        val pitchDiff = Mth.wrapDegrees(tPitch - currentPitch)
        val dist = sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff)

        val sensitivity = Minecraft.getInstance().options.sensitivity().get()
        val f = sensitivity * 0.6 + 0.2
        val gcdMulti = (f * f * f * 8.0).toFloat() * 0.15f

        if (dist < stdMaxAngle(gcdMulti)) {
            snapToGcdTarget(tYaw, tPitch, gcdMulti)
            applyMicroJitter()
            updateClientIfRequired()
            return
        }

        val (speedLo, speedHi) = Rotations.speed.value
        val rawFraction = if (speedHi > speedLo) speedLo + Random.nextFloat() * (speedHi - speedLo) else speedLo
        val fraction = Rotations.ease(rawFraction)

        val rawStep = Rotations.maxSpeedDeg.value * fraction
        val jitter  = Rotations.countJitter.value

        val progress = 1f - (dist / Math.max(1f, 180f))
        val arcFactor = if (progress < 0.7f) 0.2f else 1.0f
        val effectivePitchDiff = pitchDiff * arcFactor

        val step = rawStep.coerceAtMost(Rotations.maxSpeedDeg.value)

        if (dist <= step || dist < gcdMulti * 2f) {
            snapToGcdTarget(tYaw, tPitch, gcdMulti)
        } else {
            val ratio = step / Math.max(0.1f, dist)
            val dYaw = yawDiff * ratio
            val dPitch = effectivePitchDiff * ratio

            val mouseDX = Math.round(dYaw / gcdMulti)
            val mouseDY = Math.round(dPitch / gcdMulti)

            currentYaw += mouseDX * gcdMulti
            currentPitch = (currentPitch + mouseDY * gcdMulti).coerceIn(-90f, 90f)
        }

        applyMicroJitter()
        updateClientIfRequired()
    }

    private fun stdMaxAngle(gcd: Float) = Math.max(0.1f, gcd * 1.5f)

    private fun snapToGcdTarget(tYaw: Float, tPitch: Float, gcd: Float) {
        val yDiff = Mth.wrapDegrees(tYaw - currentYaw)
        val pDiff = tPitch - currentPitch
        
        val mouseDX = Math.round(yDiff / gcd)
        val mouseDY = Math.round(pDiff / gcd)
        
        currentYaw += mouseDX * gcd
        currentPitch = (currentPitch + mouseDY * gcd).coerceIn(-90f, 90f)
    }

    private fun updateClientIfRequired() {
        if (rotationMode == RotationMode.CLIENT) {
            val player = Minecraft.getInstance().player
            if (player != null) {
                player.setYRot(currentYaw)
                player.setXRot(currentPitch)
                clientYaw   = currentYaw
                clientPitch = currentPitch
            }
        }
        updateHitResult()
    }

    private fun applyMicroJitter() {
        val rawMj = Rotations.microJitter.value
        if (rawMj <= 0f) return
        
        val tYaw = targetYaw ?: currentYaw
        val tPitch = targetPitch ?: currentPitch
        val yDiff = Mth.wrapDegrees(tYaw - currentYaw)
        val pDiff = tPitch - currentPitch
        val dist = sqrt(yDiff * yDiff + pDiff * pDiff)

        val scale = (dist / 10f).coerceIn(0f, 1f)
        val mj = rawMj * scale
        if (mj <= 0.001f) return
        
        val sensitivity = Minecraft.getInstance().options.sensitivity().get()
        val f = sensitivity * 0.6 + 0.2
        val gcd = (f * f * f * 8.0).toFloat() * 0.15f
        
        val countsRaw = mj / gcd
        val maxCounts = countsRaw.toInt()
        val fractional = countsRaw - maxCounts
        
        var jitterCounts = maxCounts
        if (Random.nextFloat() < fractional) {
            jitterCounts += 1
        }
        
        if (jitterCounts <= 0) return
        
        val jitterX = Random.nextInt(-jitterCounts, jitterCounts + 1)
        val jitterY = Random.nextInt(-jitterCounts, jitterCounts + 1)
        
        currentYaw += jitterX * gcd
        currentPitch = (currentPitch + jitterY * gcd).coerceIn(-90f, 90f)
    }

    @JvmStatic
    fun restoreClientCamera(player: LocalPlayer) {
        if (perspective
            && player is me.ghluka.medved.util.CameraOverriddenEntity) {
            player.setYRot((player as me.ghluka.medved.util.CameraOverriddenEntity).`medved$getCameraYaw`())
            player.setXRot((player as me.ghluka.medved.util.CameraOverriddenEntity).`medved$getCameraPitch`())
        } else {
            player.setYRot(clientYaw)
            player.setXRot(clientPitch)
        }
    }

    @JvmStatic
    fun applyOverride(player: LocalPlayer) {
        if (targetYaw == null) {
            pendingFireAction?.let { it.run(); pendingFireAction = null }
            return
        }

        overriding = true
        player.setYRot(currentYaw)
        player.setXRot(currentPitch)

        if ((rotationMode == RotationMode.CLIENT && movementMode == MovementMode.CLIENT) ||
            perspective) {
            return
        }

        val mc = Minecraft.getInstance()
        val opts = mc.options
        var forwardInput = 0f
        var strafeInput = 0f
        if (opts.keyUp.isDown) forwardInput += 1f
        if (opts.keyDown.isDown) forwardInput -= 1f
        if (opts.keyLeft.isDown) strafeInput += 1f
        if (opts.keyRight.isDown) strafeInput -= 1f

        if (forwardInput != 0f || strafeInput != 0f) {
            if (!skipPositionSnap) {
            val inputLen = sqrt(forwardInput * forwardInput + strafeInput * strafeInput)
            val nf = (forwardInput / inputLen).toDouble()
            val ns = (strafeInput / inputLen).toDouble()

            val dirYaw = if (movementMode == MovementMode.SERVER) currentYaw else clientYaw
            val dirRad = Math.toRadians(dirYaw.toDouble())
            val dSin = kotlin.math.sin(dirRad)
            val dCos = kotlin.math.cos(dirRad)
            val worldDx = ns * dCos - nf * dSin
            val worldDz = nf * dCos + ns * dSin

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
        }

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

    @JvmStatic
    fun restoreRotation(player: LocalPlayer) {
        if (targetYaw == null) return
        if (!perspective) {

            player.setYRot(clientYaw)
            player.setXRot(clientPitch)
        }
        overriding = false
        skipPositionSnap = false
    }

    @JvmStatic
    fun onTurn(player: LocalPlayer) {
        if (!overriding) {
            clientYaw = player.getYRot()
            clientPitch = player.getXRot()
        }
    }
}
