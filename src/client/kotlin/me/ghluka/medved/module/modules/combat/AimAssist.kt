package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.config.entry.Color
import me.ghluka.medved.module.Module
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
    private val strength     = float("strength", 0.06f, 0.01f, 0.5f)
    private val visibilityCheck = boolean("visibility check", true)
    private val onlyAttacking = boolean("only when attacking", true)
    private val players      = boolean("players only", true)
    private val showFovCircle = boolean("fov circle", false)
    private val fovCircleColor = color("color", Color(255, 0, 0, 80), allowAlpha = true).also {
        it.visibleWhen = { showFovCircle.value }
    }

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
        val client = Minecraft.getInstance()
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
        var halfFov = fov.value
        //if (halfFov == 90f) halfFov = 89f

        val candidates = level.entitiesForRendering()
            .filterIsInstance<LivingEntity>()
            .filter { e ->
                e !== player &&
                        !e.isDeadOrDying &&
                        !(players.value && e !is Player) &&
                        player.distanceTo(e) <= maxRange &&
                        TargetFilter.isValidTarget(player, e) &&
                        (!visibilityCheck.value || player.hasLineOfSight(e))
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
        val yawDiff   = Mth.wrapDegrees(targetYaw - player.yRot)
        val pitchDiff = targetPitch - player.xRot

        if (fov.value < 179.9f) {
            val screenH = Minecraft.getInstance().window.guiScaledHeight.toFloat()
            val screenW = Minecraft.getInstance().window.guiScaledWidth.toFloat()
            val gameFovRad = Math.toRadians(Minecraft.getInstance().options.fov().get().toDouble()).toFloat()
            val radius = (screenH * 0.5f * (fov.value / Minecraft.getInstance().options.fov().get().toFloat()))
                .coerceIn(2f, screenH)

            fun worldToScreen(yawDeg: Float, pitchDeg: Float): Pair<Float, Float> {
                val relYaw   = Math.toRadians(Mth.wrapDegrees(yawDeg   - player.yRot).toDouble()).toFloat()
                val relPitch = Math.toRadians((pitchDeg - player.xRot).toDouble()).toFloat()
                val px = (-Math.tan(relYaw.toDouble())   * (screenH / (2.0 * Math.tan(gameFovRad / 2.0)))).toFloat() + screenW / 2f
                val py = (-Math.tan(relPitch.toDouble()) * (screenH / (2.0 * Math.tan(gameFovRad / 2.0)))).toFloat() + screenH / 2f
                return px to py
            }

            val (pitchMin, pitchMax) = hitboxPitchRange(player, best)
            val (yawMin,   yawMax)   = hitboxYawRange(player, best)

            val corners = listOf(
                worldToScreen(yawMin, pitchMin),
                worldToScreen(yawMin, pitchMax),
                worldToScreen(yawMax, pitchMin),
                worldToScreen(yawMax, pitchMax)
            )

            val cx = screenW / 2f
            val cy = screenH / 2f
            val minX = corners.minOf { it.first }
            val maxX = corners.maxOf { it.first }
            val minY = corners.minOf { it.second }
            val maxY = corners.maxOf { it.second }

            val nearestX = cx.coerceIn(minX, maxX)
            val nearestY = cy.coerceIn(minY, maxY)

            val distPx = sqrt(((cx - nearestX) * (cx - nearestX) + (cy - nearestY) * (cy - nearestY)).toDouble()).toFloat()
            if (distPx > radius) { clearAssist(); return }
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
        val radius = (screenH * 0.5f * (fov.value.coerceAtMost(179.9f) / mc.options.fov().get().toFloat()))
            .coerceIn(2f, screenH.toFloat())
        drawCircleOutline(extractor, centerX, centerY, radius.coerceAtLeast(2f), fovCircleColor.value.argb)
    }

    private fun drawCircleOutline(g: GuiGraphicsExtractor, centerX: Int, centerY: Int, radius: Float, color: Int) {
        if (radius < 2f) return
        val segments = 256
        var prevX = centerX + cos(0f) * radius
        var prevY = centerY + sin(0f) * radius

        for (i in 1..segments) {
            val angle = (i.toFloat() / segments) * (Math.PI * 2).toFloat()
            val curX = centerX + cos(angle) * radius
            val curY = centerY + sin(angle) * radius
            drawSegmentAA(g, prevX, prevY, curX, curY, color)
            prevX = curX
            prevY = curY
        }
    }

    private fun drawSegmentAA(g: GuiGraphicsExtractor, x0: Float, y0: Float, x1: Float, y1: Float, color: Int) {
        val a = (color ushr 24) and 0xFF
        val r = (color ushr 16) and 0xFF
        val gr = (color ushr 8)  and 0xFF
        val b =  color            and 0xFF

        fun plot(x: Int, y: Int, brightness: Float) {
            if (brightness <= 0f) return
            val alpha = ((a * brightness).toInt()).coerceIn(0, 255)
            val packed = (alpha shl 24) or (r shl 16) or (gr shl 8) or b
            g.fill(x, y, x + 1, y + 1, packed)
        }

        fun fpart(x: Float) = x - kotlin.math.floor(x).toFloat()
        fun rfpart(x: Float) = 1f - fpart(x)

        var ax = x0; var ay = y0
        var bx = x1; var by = y1
        val steep = kotlin.math.abs(by - ay) > kotlin.math.abs(bx - ax)

        if (steep) { ax = ay.also { ay = ax }; bx = by.also { by = bx } }
        if (ax > bx) { ax = bx.also { bx = ax }; ay = by.also { by = ay } }

        val dx = bx - ax
        val dy = by - ay
        val gradient = if (dx == 0f) 1f else dy / dx

        // first endpoint
        val xend0 = kotlin.math.round(ax).toFloat()
        val yend0 = ay + gradient * (xend0 - ax)
        val xgap0 = rfpart(ax + 0.5f)
        val xpx0  = xend0.toInt()
        val ypx0  = kotlin.math.floor(yend0).toInt()
        if (steep) {
            plot(ypx0,     xpx0, rfpart(yend0) * xgap0)
            plot(ypx0 + 1, xpx0,  fpart(yend0) * xgap0)
        } else {
            plot(xpx0, ypx0,     rfpart(yend0) * xgap0)
            plot(xpx0, ypx0 + 1,  fpart(yend0) * xgap0)
        }
        var intery = yend0 + gradient

        // second endpoint
        val xend1 = kotlin.math.round(bx).toFloat()
        val yend1 = by + gradient * (xend1 - bx)
        val xgap1 = fpart(bx + 0.5f)
        val xpx1  = xend1.toInt()
        val ypx1  = kotlin.math.floor(yend1).toInt()
        if (steep) {
            plot(ypx1,     xpx1, rfpart(yend1) * xgap1)
            plot(ypx1 + 1, xpx1,  fpart(yend1) * xgap1)
        } else {
            plot(xpx1, ypx1,     rfpart(yend1) * xgap1)
            plot(xpx1, ypx1 + 1,  fpart(yend1) * xgap1)
        }

        // main loop
        for (x in (xpx0 + 1) until xpx1) {
            if (steep) {
                plot(kotlin.math.floor(intery).toInt(),     x, rfpart(intery))
                plot(kotlin.math.floor(intery).toInt() + 1, x,  fpart(intery))
            } else {
                plot(x, kotlin.math.floor(intery).toInt(),     rfpart(intery))
                plot(x, kotlin.math.floor(intery).toInt() + 1,  fpart(intery))
            }
            intery += gradient
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

    private fun hitboxYawRange(player: Player, target: LivingEntity): Pair<Float, Float> {
        val halfWidth = (target.bbWidth / 2.0)
        val dx = target.x - player.x
        val dz = target.z - player.z
        val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001)
        val perpX = -dz / dist
        val perpZ =  dx / dist

        val leftX  = dx + perpX * halfWidth
        val leftZ  = dz + perpZ * halfWidth
        val rightX = dx - perpX * halfWidth
        val rightZ = dz - perpZ * halfWidth

        val leftYaw  = Math.toDegrees(atan2(-leftX,  leftZ)).toFloat()
        val rightYaw = Math.toDegrees(atan2(-rightX, rightZ)).toFloat()
        return minOf(leftYaw, rightYaw) to maxOf(leftYaw, rightYaw)
    }
}
