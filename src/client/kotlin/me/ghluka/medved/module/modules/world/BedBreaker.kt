package me.ghluka.medved.module.modules.world

import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.world.Scaffold
import me.ghluka.medved.util.RotationManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

object BedBreaker : Module(
    name = "Bed Breaker",
    description = "Automatically breaks enemy beds",
    category = Category.WORLD,
) {
    enum class BreakMode { LEGIT, BLATANT }
    enum class RotationMode { NONE, CLIENT, SERVER }

    private val breakMode    = enum("mode", BreakMode.LEGIT)
    private val rotationMode = enum("rotations", RotationMode.SERVER)
    private val range        = float("range", 4.5f, 1f, 6f).also {
        it.visibleWhen = { breakMode.value == BreakMode.LEGIT }
    }
    private val blatantRange = float("blatant range", 6f, 1f, 10f).also {
        it.visibleWhen = { breakMode.value == BreakMode.BLATANT }
    }
    private val autoTool = boolean("auto tool", true).also {
        it.visibleWhen = { breakMode.value == BreakMode.LEGIT }
    }

    @JvmField var pendingHitPos:  BlockPos?  = null
    @JvmField var pendingHitFace: Direction  = Direction.UP

    private var savedSlot = -1
    private var wasBreaking = false

    override fun onEnabled() {
        RotationManager.clearRotation()
        pendingHitPos = null
        savedSlot = -1
        wasBreaking = false
    }

    override fun onDisabled() {
        stopAndClean()
    }

    override fun hudInfo(): String = breakMode.value.name.lowercase()
        .replaceFirstChar { it.uppercase() }

    init {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (!isEnabled()) return@register
            if (Scaffold.isEnabled()) return@register
            val player = client.player ?: return@register
            val level  = client.level  ?: return@register

            val effectiveRange = if (breakMode.value == BreakMode.BLATANT)
                blatantRange.value else range.value

            val bedPos = findNearestBed(player, level, effectiveRange)
            if (bedPos == null) {
                stopAndClean()
                return@register
            }

            val (targetPos, targetFace) = when (breakMode.value) {
                BreakMode.LEGIT   -> findLegitTarget(player, level, bedPos)
                BreakMode.BLATANT -> bedPos to Direction.UP
            }

            pendingHitPos  = targetPos
            pendingHitFace = targetFace
            wasBreaking    = true

            when (rotationMode.value) {
                RotationMode.CLIENT -> {
                    val (yaw, pitch) = calcRotation(player, targetPos)
                    RotationManager.perspective = false
                    RotationManager.movementMode = RotationManager.MovementMode.CLIENT
                    RotationManager.rotationMode = RotationManager.RotationMode.CLIENT
                    RotationManager.setTargetRotation(yaw, pitch)
                }
                RotationMode.SERVER -> {
                    val (yaw, pitch) = calcRotation(player, targetPos)
                    RotationManager.perspective = true
                    RotationManager.movementMode = RotationManager.MovementMode.CLIENT
                    RotationManager.rotationMode = RotationManager.RotationMode.CLIENT
                    RotationManager.setTargetRotation(yaw, pitch)
                }
                RotationMode.NONE -> {
                    RotationManager.clearRotation()
                }
            }

            if (breakMode.value == BreakMode.LEGIT && autoTool.value) {
                val state = level.getBlockState(targetPos)
                val best  = findBestHotbarSlot(player, state)
                if (best != player.inventory.selectedSlot) {
                    if (savedSlot == -1) savedSlot = player.inventory.selectedSlot
                    player.inventory.selectedSlot = best
                }
            }

            if (pendingHitPos != targetPos || !wasBreaking) {
                client.gameMode?.startDestroyBlock(targetPos, targetFace)
                wasBreaking = true
            } else {
                client.gameMode?.continueDestroyBlock(targetPos, targetFace)
            }
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND)
        }
    }

    private fun stopAndClean() {
        val mc = Minecraft.getInstance()
        if (wasBreaking) {
            mc.gameMode?.stopDestroyBlock()
            wasBreaking = false
        }
        pendingHitPos = null
        restoreTool()
        RotationManager.clearRotation()
    }

    override fun onLevelRender(ctx: LevelRenderContext) {
        if (rotationMode.value != RotationMode.NONE && RotationManager.isActive()) {
            RotationManager.tick()
        }
    }

    private fun restoreTool() {
        if (savedSlot != -1) {
            Minecraft.getInstance().player?.inventory?.let { it.selectedSlot = savedSlot }
            savedSlot = -1
        }
    }

    private fun findNearestBed(player: LocalPlayer, level: ClientLevel, range: Float): BlockPos? {
        val eye = player.eyePosition
        val r   = range.toInt() + 2
        val base = player.blockPosition()
        val rangeSq = (range * range).toDouble()
        var bestSq  = rangeSq
        var bestPos: BlockPos? = null

        for (x in -r..r) for (y in -r..r) for (z in -r..r) {
            val pos = base.offset(x, y, z)
            if (!level.getBlockState(pos).`is`(BlockTags.BEDS)) continue
            val center = Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
            val sq = eye.distanceToSqr(center)
            if (sq < bestSq) {
                bestSq  = sq
                bestPos = pos
            }
        }
        return bestPos
    }

    private fun findLegitTarget(player: LocalPlayer, level: ClientLevel, bedPos: BlockPos): Pair<BlockPos, Direction> {
        val eye    = player.eyePosition
        val target = Vec3(bedPos.x + 0.5, bedPos.y + 0.5, bedPos.z + 0.5)
        val dx = target.x - eye.x
        val dy = target.y - eye.y
        val dz = target.z - eye.z

        var prevPos: BlockPos? = null
        val steps = 64
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val pos = BlockPos(
                Mth.floor(eye.x + dx * t),
                Mth.floor(eye.y + dy * t),
                Mth.floor(eye.z + dz * t),
            )
            if (pos == prevPos) continue
            prevPos = pos
            if (level.getBlockState(pos).isAir) continue
            return pos to faceToward(eye, pos)
        }
        return bedPos to faceToward(eye, bedPos)
    }

    private fun faceToward(eye: Vec3, pos: BlockPos): Direction {
        val dx  = eye.x - (pos.x + 0.5)
        val dy  = eye.y - (pos.y + 0.5)
        val dz  = eye.z - (pos.z + 0.5)
        val adx = abs(dx); val ady = abs(dy); val adz = abs(dz)
        return when {
            ady >= adx && ady >= adz -> if (dy > 0) Direction.UP    else Direction.DOWN
            adx >= adz               -> if (dx > 0) Direction.EAST  else Direction.WEST
            else                     -> if (dz > 0) Direction.SOUTH else Direction.NORTH
        }
    }

    private fun findBestHotbarSlot(player: LocalPlayer, state: BlockState): Int {
        var bestSlot  = player.inventory.selectedSlot
        var bestSpeed = player.inventory.getItem(player.inventory.selectedSlot).getDestroySpeed(state)
        for (slot in 0..8) {
            val speed = player.inventory.getItem(slot).getDestroySpeed(state)
            if (speed > bestSpeed) {
                bestSpeed = speed
                bestSlot  = slot
            }
        }
        return bestSlot
    }

    private fun calcRotation(player: LocalPlayer, pos: BlockPos): Pair<Float, Float> {
        val dx         = pos.x + 0.5 - player.x
        val dy         = pos.y + 0.5 - player.eyeY
        val dz         = pos.z + 0.5 - player.z
        val horizDist  = sqrt(dx * dx + dz * dz)
        val yaw        = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch      = (-Math.toDegrees(atan2(dy, horizDist))).toFloat()
        return yaw to pitch
    }
}
