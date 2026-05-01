package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.combat.KnockbackDisplacement
import me.ghluka.medved.module.modules.world.Scaffold
import me.ghluka.medved.module.modules.other.TargetFilter
import me.ghluka.medved.util.RotationManager
import net.minecraft.client.DeltaTracker
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

object AimAssist : Module(
    name = "Aim Assist",
    description = "Gently nudges your aim toward nearby players without fighting your mouse",
    category = Category.COMBAT
) {

    private val range        = float("range", 5.0f, 1.0f, 12.0f)
    private val fov          = float("fov", 60.0f, 5.0f, 180.0f)
    private val strength     = float("strength", 0.06f, 0.01f, 0.15f)
    private val onlyAttacking = boolean("only when attacking", true)
    private val players      = boolean("players only", true)
    private val showFovCircle = boolean("show fov circle", false)

    private var lastClientYaw = 0f
    private var lastClientPitch = 0f
    private var hasLastClientRotation = false
    private var isAssisting = false

    private fun clearAssist() {
        if (isAssisting) {
            RotationManager.clearRotation()
            isAssisting = false
        }
        hasLastClientRotation = false
    }

    private fun yieldAssist() {
        isAssisting = false
        hasLastClientRotation = false
    }

    override fun onTick(client: Minecraft) {
        if (client.player == null || client.level == null) {
            clearAssist()
        }
        
        if (KnockbackDisplacement.rotationHeld) {
            yieldAssist()
        }
    }

    override fun onDisabled() {
        clearAssist()
    }

    override fun onLevelRender(ctx: LevelRenderContext) {
        val player = Minecraft.getInstance().player ?: return
        val level  = Minecraft.getInstance().level  ?: return
        if (Minecraft.getInstance().screen != null) {
            clearAssist()
            return
        }

        if (Scaffold.isEnabled()) {
            yieldAssist()
            return
        }
        if (KnockbackDisplacement.rotationHeld) {
            yieldAssist()
            return
        }
        if (me.ghluka.medved.module.modules.world.BedBreaker.isEnabled() && me.ghluka.medved.module.modules.world.BedBreaker.pendingHitPos != null) {
            yieldAssist()
            return
        }
        if (me.ghluka.medved.module.modules.world.ChestAura.isEnabled() && RotationManager.isActive()) {
            yieldAssist()
            return
        }
        if (me.ghluka.medved.module.modules.world.Clutch.isEnabled() && RotationManager.isActive()) {
            yieldAssist()
            return
        }

        if (onlyAttacking.value && !Minecraft.getInstance().options.keyAttack.isDown) {
            clearAssist()
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
                player.distanceTo(e) <= maxRange &&
                TargetFilter.isValidTarget(player, e)
            }

        val best = candidates.minByOrNull { e ->
            val (yaw, pitch) = calcRotation(player, e)
            val dy = Mth.wrapDegrees(yaw   - player.yRot)
            val dp = Mth.wrapDegrees(pitch - player.xRot)
            sqrt((dy * dy + dp * dp).toDouble()).toFloat()
        }

        if (best == null) {
            clearAssist()
            return
        }

        val (targetYaw, targetPitch) = calcRotation(player, best)
        val yawDiff   = Mth.wrapDegrees(targetYaw   - player.yRot)
        val pitchDiff = Mth.wrapDegrees(targetPitch - player.xRot)

        if (Mth.abs(yawDiff) > halfFov || Mth.abs(pitchDiff) > halfFov) {
            clearAssist()
            return
        }

        val angleDelta = sqrt((yawDiff * yawDiff + pitchDiff * pitchDiff).toDouble()).toFloat()
        val distanceScale = 1f - (angleDelta / 90f).coerceIn(0f, 0.5f)

        if (!hasLastClientRotation) {
            lastClientYaw = player.yRot
            lastClientPitch = player.xRot
            hasLastClientRotation = true
        }

        val mouseYawDelta = Mth.wrapDegrees(player.yRot - lastClientYaw)
        val mousePitchDelta = player.xRot - lastClientPitch
        lastClientYaw = player.yRot
        lastClientPitch = player.xRot

        val moveDot = yawDiff * mouseYawDelta + pitchDiff * mousePitchDelta
        val moveMagnitude = maxOf(kotlin.math.abs(mouseYawDelta), kotlin.math.abs(mousePitchDelta))
        val assistScale = when {
            moveDot > 0f && moveMagnitude > 0.3f -> 0.2f
            moveDot > 0f && moveMagnitude > 0.15f -> 0.5f
            moveDot > 0f -> 0.75f
            else -> 1f
        }

        val nudgeYaw = yawDiff * strength.value * distanceScale * assistScale

        val (pitchMin, pitchMax) = hitboxPitchRange(player, best)
        val pitchTolerance = 2f
        val pitchNudge = when {
            player.xRot < pitchMin - pitchTolerance -> (pitchMin - player.xRot)
            player.xRot > pitchMax + pitchTolerance -> (pitchMax - player.xRot)
            else -> 0f
        }
        val nudgePitch = pitchNudge * strength.value * distanceScale * assistScale

        RotationManager.movementMode = RotationManager.MovementMode.CLIENT
        RotationManager.rotationMode  = RotationManager.RotationMode.CLIENT
        RotationManager.setTargetRotation(player.yRot + nudgeYaw, player.xRot + nudgePitch)
        RotationManager.tick()
        isAssisting = true
    }

    override fun onHudRender(extractor: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!showFovCircle.value) return

        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.level == null) return

        val screenW = mc.window.guiScaledWidth
        val screenH = mc.window.guiScaledHeight
        val centerX = screenW / 2
        val centerY = screenH / 2
        val cameraFovRad = Math.toRadians(mc.options.fov().get().toDouble())
        val aimFovRad = Math.toRadians(fov.value.toDouble())
        val radius = ((screenW * 0.5f) / kotlin.math.tan(cameraFovRad / 2.0) * kotlin.math.tan(aimFovRad / 2.0)).toFloat()
        drawCircleOutline(extractor, centerX, centerY, radius.coerceAtLeast(2f), 0xAAFFFFFF.toInt())
    }

    private fun drawCircleOutline(g: GuiGraphicsExtractor, centerX: Int, centerY: Int, radius: Float, color: Int) {
        val segments = (radius * 8f).toInt().coerceIn(48, 160)
        var previousX = centerX.toFloat() + radius
        var previousY = centerY.toFloat()

        for (i in 1..segments) {
            val angle = (i.toFloat() / segments.toFloat()) * (Math.PI.toFloat() * 2f)
            val nextX = centerX + cos(angle) * radius
            val nextY = centerY + sin(angle) * radius
            drawSegment(g, previousX, previousY, nextX, nextY, color)
            previousX = nextX
            previousY = nextY
        }
    }

    private fun drawSegment(g: GuiGraphicsExtractor, x0: Float, y0: Float, x1: Float, y1: Float, color: Int) {
        val dx = x1 - x0
        val dy = y1 - y0
        val steps = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)).toInt().coerceAtLeast(1)
        for (step in 0..steps) {
            val t = step.toFloat() / steps.toFloat()
            val x = (x0 + dx * t).toInt()
            val y = (y0 + dy * t).toInt()
            g.fill(x - 1, y - 1, x + 1, y + 1, color)
        }
    }

    private fun hitboxPitchRange(player: Player, target: LivingEntity): Pair<Float, Float> {
        val eyeY = player.eyeY
        val dx = target.x - player.x
        val dz = target.z - player.z
        val horizDist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001)
        val topPitch = (-Math.toDegrees(atan2(target.y + target.bbHeight - eyeY, horizDist))).toFloat()
        val bottomPitch = (-Math.toDegrees(atan2(target.y - eyeY, horizDist))).toFloat()
        return minOf(topPitch, bottomPitch) to maxOf(topPitch, bottomPitch)
    }

    private fun calcRotation(player: Player, target: LivingEntity): Pair<Float, Float> {
        val aimY = target.y + target.bbHeight * 0.5
        val dx = target.x - player.x
        val dy = aimY - player.eyeY
        val dz = target.z - player.z
        val horizDist = sqrt(dx * dx + dz * dz)
        val yaw   = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = (-Math.toDegrees(atan2(dy, horizDist))).toFloat()
        return yaw to pitch
    }

    override fun hudInfo(): String {
        return "${round(strength.value * 100).toInt()}"
    }
}
