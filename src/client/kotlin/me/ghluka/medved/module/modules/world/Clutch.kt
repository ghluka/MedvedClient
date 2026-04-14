package me.ghluka.medved.module.modules.world

import me.ghluka.medved.module.Module
import me.ghluka.medved.util.RotationManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.BlockItem
import net.minecraft.world.phys.BlockHitResult
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.world.level.BlockGetter
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt


object Clutch : Module("Clutch", "Bridges blocks back to safety when knocked off an edge", Category.WORLD) {

    enum class Trigger { ALWAYS, ON_VOID, ON_LETHAL_FALL, FALL_DISTANCE }

    private val trigger       = enum("trigger", Trigger.ALWAYS)
    private val minFallBlocks = float("blocks", 4f, 1f, 50f).also {
        it.visibleWhen = { trigger.value == Trigger.FALL_DISTANCE }
    }

    private val silent = boolean("silent aim", false)

    private val rotateBack = boolean("rotate back", true).also {
        it.visibleWhen = { !silent.value }
    }

    private val returnToSlot     = boolean("return to slot", true)

    private val clutchMoveDelay = int("clutch move delay", 0, 0, 20)

    private val maxBlocks = int("max blocks", 10, 1, 64)

    private val rotSpeed = float("rotation speed", 60f, 10f, 120f)

    enum class FilterMode { NONE, BLACKLIST, WHITELIST }
    private val filterMode = enum("filter mode", FilterMode.NONE)

    private var blocksPlaced    = 0
    private var savedSlot       = -1
    private var moveFreezeTicks = 0
    private var clutching       = false
    private var returningToCamera = false
    private var savedCamYaw   = 0f
    private var savedCamPitch = 0f

    private fun findBlockSlot(player: LocalPlayer): Int {
        val world  = Minecraft.getInstance().level ?: return -1
        for (i in 0..8) {
            val stack = player.inventory.getItem(i)
            if (stack.isEmpty || stack.item !is BlockItem) continue
            val block = (stack.item as BlockItem).block
            if (!block.defaultBlockState().isCollisionShapeFullBlock(world, BlockPos.ZERO)) continue
            // filterMode
            return i
        }
        return -1
    }

    private fun hasBlocks(player: LocalPlayer) = findBlockSlot(player) != -1

    private fun triggerMet(
        player: LocalPlayer,
        world: net.minecraft.client.multiplayer.ClientLevel
    ): Boolean = when (trigger.value) {
        Trigger.ALWAYS -> true
        Trigger.ON_VOID -> {
            val px     = floor(player.x).toInt()
            val pz     = floor(player.z).toInt()
            val startY = floor(player.y).toInt() - 1
            (startY downTo startY - 64).none { y ->
                val s = world.getBlockState(BlockPos(px, y, pz))
                !s.isAir && s.fluidState.isEmpty
            }
        }
        Trigger.ON_LETHAL_FALL -> {
            val px = floor(player.x).toInt()
            val pz = floor(player.z).toInt()
            var depth = 0
            for (y in (floor(player.y).toInt() - 1) downTo (floor(player.y).toInt() - 41)) {
                val s = world.getBlockState(BlockPos(px, y, pz))
                if (!s.isAir && s.fluidState.isEmpty) break
                depth++
            }
            (player.fallDistance + depth - 3f) >= player.health / 2f
        }
        Trigger.FALL_DISTANCE -> {
            // Predictive: fire when (current fall + air column below) >= threshold
            val px = floor(player.x).toInt()
            val pz = floor(player.z).toInt()
            var depth = 0
            for (y in (floor(player.y).toInt() - 1) downTo (floor(player.y).toInt() - minFallBlocks.value.toInt() - 2)) {
                val s = world.getBlockState(BlockPos(px, y, pz))
                if (!s.isAir && s.fluidState.isEmpty) break
                depth++
            }
            (player.fallDistance + depth) >= minFallBlocks.value
        }
    }

    init {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (!isEnabled()) return@register
            val player = client.player ?: return@register
            val world  = client.level  ?: return@register

            if (moveFreezeTicks > 0) { moveFreezeTicks--; return@register }

            if (player.onGround()) {
                if (clutching) {
                    if (savedSlot != -1 && returnToSlot.value) {
                        player.inventory.setSelectedSlot(savedSlot)
                        savedSlot = -1
                    }
                    moveFreezeTicks = clutchMoveDelay.value
                    clutching    = false
                    blocksPlaced = 0
                    if (rotateBack.value && !silent.value) {
                        // Kick off smooth return to pre-clutch camera angle
                        returningToCamera = true
                        RotationManager.movementMode = RotationManager.MovementMode.CLIENT
                        RotationManager.rotationMode = RotationManager.RotationMode.CLIENT
                        RotationManager.setTargetRotation(savedCamYaw, savedCamPitch)
                    } else {
                        RotationManager.clearRotation()
                    }
                }
                // Keep advancing toward saved camera until converged
                if (returningToCamera) {
                    RotationManager.quickTick(rotSpeed.value)
                    RotationManager.physicsYawOverride = RotationManager.getCurrentYaw()
                    RotationManager.skipPositionSnap = true
                    if (RotationManager.hasReachedTarget(2f)) {
                        RotationManager.clearRotation()
                        returningToCamera = false
                    }
                }
                return@register
            }

            if (player.deltaMovement.y >= 0) return@register   // only fire when actually falling
            if (!triggerMet(player, world)) return@register
            if (!hasBlocks(player)) return@register

            if (!clutching) {
                clutching       = true
                blocksPlaced    = 0
                returningToCamera = false
                savedSlot       = if (returnToSlot.value) player.inventory.selectedSlot else -1
                // Snapshot real camera angles before we start rotating
                savedCamYaw   = RotationManager.getClientYaw()
                savedCamPitch = player.getXRot()
            }

            if (blocksPlaced >= maxBlocks.value) return@register

            // Ensure holding a block item
            if (player.mainHandItem.isEmpty || player.mainHandItem.item !is BlockItem) {
                val slot = findBlockSlot(player)
                if (slot == -1) return@register
                player.inventory.setSelectedSlot(slot)
            }

            val reach = if (player.isCreative) 5.0 else 4.5
            val eyeX  = player.x
            val eyeY  = player.y + player.eyeHeight
            val eyeZ  = player.z
            val bx    = floor(player.x).toInt()
            val feetY = floor(player.y).toInt()
            val bz    = floor(player.z).toInt()

            val velY = player.deltaMovement.y.coerceAtMost(0.0)
            val targetY = floor(player.y + velY).toInt() - 1
            val playerBB = player.boundingBox
            val trajectoryBB = playerBB.expandTowards(0.0, velY, 0.0)
            data class Candidate(val neighbor: BlockPos, val face: Direction, val score: Double)
            var best: Candidate? = null

            for (dy in 3 downTo -4) {
                for (ddx in -4..4) {
                    for (ddz in -4..4) {
                        val airPos = BlockPos(bx + ddx, feetY + dy, bz + ddz)
                        if (!world.getBlockState(airPos).isAir) continue

                        // Skip if block space overlaps player body or fall trajectory
                        val blockBB = net.minecraft.world.phys.AABB(
                            airPos.x.toDouble(), airPos.y.toDouble(), airPos.z.toDouble(),
                            airPos.x + 1.0, airPos.y + 1.0, airPos.z + 1.0
                        )
                        if (trajectoryBB.intersects(blockBB)) continue

                        for (dir in arrayOf(
                            Direction.NORTH, Direction.SOUTH,
                            Direction.EAST,  Direction.WEST
                        )) {
                            val nb = airPos.relative(dir)
                            val nbState = world.getBlockState(nb)
                            if (nbState.isAir || !nbState.fluidState.isEmpty) continue

                            val face = dir.opposite
                            val fx = nb.x + 0.5 + face.stepX * 0.45
                            val fy = nb.y + 0.5 + face.stepY * 0.45
                            val fz = nb.z + 0.5 + face.stepZ * 0.45
                            val ex = fx - eyeX; val ey2 = fy - eyeY; val ez = fz - eyeZ
                            if (ex * ex + ey2 * ey2 + ez * ez > reach * reach) continue

                            // Primary: prefer airPos.Y closest to targetY (feetY-1)
                            // Secondary: prefer closest horizontal to player centre
                            val yDev  = Math.abs(airPos.y - targetY).toDouble()
                            val hDist = Math.sqrt(
                                (airPos.x + 0.5 - player.x) * (airPos.x + 0.5 - player.x) +
                                (airPos.z + 0.5 - player.z) * (airPos.z + 0.5 - player.z)
                            )
                            val score = yDev * 20.0 + hDist
                            if (best == null || score < best.score) {
                                best = Candidate(nb, face, score)
                            }
                        }
                    }
                }
            }

            val placement = best ?: return@register

            val (aimYaw, aimPitch) = faceAim(player, placement.neighbor, placement.face)

            // server's known look vector and the placed block face always agree.
                RotationManager.movementMode = RotationManager.MovementMode.CLIENT
                RotationManager.rotationMode = RotationManager.RotationMode.CLIENT
            if (silent.value) {
                RotationManager.perspective = true
            }
            RotationManager.setTargetRotation(aimYaw, aimPitch)
            RotationManager.quickTick(rotSpeed.value)
            RotationManager.physicsYawOverride = RotationManager.getCurrentYaw()
            RotationManager.skipPositionSnap = true

            val cy = RotationManager.getCurrentYaw()
            val cp = RotationManager.getCurrentPitch()
            val savedY = player.getYRot()
            val savedP = player.getXRot()
            player.setYRot(cy)
            player.setXRot(cp)
            val hitResult = player.pick(reach, 1.0f, false)
            player.setYRot(savedY)
            player.setXRot(savedP)

            val bhr = hitResult as? BlockHitResult ?: return@register
            if (bhr.blockPos != placement.neighbor || bhr.direction != placement.face) return@register

            client.hitResult = bhr
            KeyMapping.click(InputConstants.getKey(client.options.keyUse.saveString()))
            blocksPlaced++
        }
    }

    override fun onTick(client: Minecraft) {}

    /** Compute exact yaw/pitch from the player's eye toward a block face centre. */
    private fun faceAim(player: LocalPlayer, neighbor: BlockPos, face: Direction): Pair<Float, Float> {
        val eyeY = player.y + player.eyeHeight
        val fx = neighbor.x + 0.5 + face.stepX * 0.45
        val fy = neighbor.y + 0.5 + face.stepY * 0.45
        val fz = neighbor.z + 0.5 + face.stepZ * 0.45
        val dx = fx - player.x
        val dy = fy - eyeY
        val dz = fz - player.z
        val hDist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.01)
        val yaw   = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(atan2(-dy, hDist)).toFloat().coerceIn(-90f, 90f)
        return yaw to pitch
    }

    override fun onDisabled() {
        clutching         = false
        returningToCamera  = false
        blocksPlaced       = 0
        moveFreezeTicks    = 0
        val player = Minecraft.getInstance().player
        if (savedSlot != -1 && player != null && returnToSlot.value) {
            player.inventory.setSelectedSlot(savedSlot)
        }
        savedSlot = -1
        RotationManager.clearRotation()
    }
}
