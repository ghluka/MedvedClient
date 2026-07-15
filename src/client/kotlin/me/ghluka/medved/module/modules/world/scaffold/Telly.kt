package me.ghluka.medved.module.modules.world.scaffold

import me.ghluka.medved.util.RotationManager
import me.ghluka.medved.gui.helpers.itemCategories
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.BlockItem
import net.minecraft.world.phys.BlockHitResult
import com.mojang.blaze3d.platform.InputConstants
import me.ghluka.medved.util.InputUtil.isPhysicalKeyDown
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt

object Telly {
    private var savedSlot        = -1
    private var moveFreezeTicks  = 0
    private var clutching        = false
    private var returningToCamera = false
    private var savedCamYaw      = 0f
    private var savedCamPitch    = 0f

    private var savedBackYaw     = 0f
    @JvmField var isActivelyPlacing = false

    private fun findBlockSlot(player: LocalPlayer): Int {
        val world  = Minecraft.getInstance().level ?: return -1
        for (i in 0..8) {
            val stack = player.inventory.getItem(i)
            if (stack.isEmpty || stack.item !is BlockItem) continue
            val block = (stack.item as BlockItem).block
            if (!block.defaultBlockState().isCollisionShapeFullBlock(world, BlockPos.ZERO)) continue
            if (Scaffold.blockWhitelist.value.isNotEmpty()) {
                val blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString().lowercase()
                if (!blockMatchesWhitelist(blockId, Scaffold.blockWhitelist.value)) continue
            }
            return i
        }
        return -1
    }

    private fun hasBlocks(player: LocalPlayer) = findBlockSlot(player) != -1

    init {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (!Scaffold.isEnabled()) return@register
            if (Scaffold.bridgeMode.value != Scaffold.BridgeMode.TELLY) return@register
            val player = client.player ?: return@register
            val world  = client.level  ?: return@register

            if (player.onGround()) {
                isActivelyPlacing = false
                val physicalW = isPhysicalKeyDown(client.options.keyUp)

                if (clutching) {
                    if (savedSlot != -1) {
                        player.inventory.setSelectedSlot(savedSlot)
                        savedSlot = -1
                    }

                    savedBackYaw    = net.minecraft.util.Mth.wrapDegrees(RotationManager.getClientYaw() + 180f)
                    moveFreezeTicks = 0
                    clutching       = false
                }

                if (!physicalW) {
                    moveFreezeTicks = 0
                } else if (moveFreezeTicks > 0) {
                    moveFreezeTicks--
                }

                val opts = client.options
                opts.keyJump.setDown(true)
                if (moveFreezeTicks == 0) {
                    RotationManager.clearRotation()
                    opts.keyUp.setDown(isPhysicalKeyDown(opts.keyUp))
                    opts.keyDown.setDown(isPhysicalKeyDown(opts.keyDown))
                    opts.keyLeft.setDown(isPhysicalKeyDown(opts.keyLeft))
                    opts.keyRight.setDown(isPhysicalKeyDown(opts.keyRight))
                    opts.keyShift.setDown(isPhysicalKeyDown(opts.keyShift))
                    Scaffold.ownsRotation = false
                } else if (physicalW && Scaffold.ownsRotation) {
                    RotationManager.movementMode = RotationManager.MovementMode.CLIENT
                    RotationManager.rotationMode = RotationManager.RotationMode.CLIENT
                    RotationManager.perspective  = true
                    RotationManager.setTargetRotation(savedBackYaw, player.getXRot())
                    RotationManager.quickTick(Scaffold.rotSpeed.value)

                    opts.keyUp.setDown(false)
                    opts.keyDown.setDown(true)
                }

                return@register
            }
            moveFreezeTicks = 0

            if (player.deltaMovement.y >= 0) return@register
            if (!hasBlocks(player)) return@register

            if (!clutching) {
                clutching         = true
                returningToCamera = false
                savedSlot         = player.inventory.selectedSlot
                savedCamYaw       = RotationManager.getClientYaw()
                savedCamPitch     = player.getXRot()
            }

            isActivelyPlacing = true

            if (player.mainHandItem.isEmpty || player.mainHandItem.item !is BlockItem) {
                val slot = findBlockSlot(player)
                if (slot == -1) return@register
                player.inventory.setSelectedSlot(slot)
            } else if (Scaffold.blockWhitelist.value.isNotEmpty()) {
                val blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey((player.mainHandItem.item as BlockItem).block).toString().lowercase()
                if (!blockMatchesWhitelist(blockId, Scaffold.blockWhitelist.value)) {
                    val slot = findBlockSlot(player)
                    if (slot == -1) return@register
                    player.inventory.setSelectedSlot(slot)
                }
            }

            val reach = if (player.isCreative) 5.0 else 4.5
            val eyeX  = player.x
            val eyeY  = player.y + player.eyeHeight
            val eyeZ  = player.z
            val bx    = floor(player.x).toInt()
            val feetY = floor(player.y).toInt()
            val bz    = floor(player.z).toInt()

            val velY         = player.deltaMovement.y.coerceAtMost(0.0)
            val targetY      = floor(player.y + velY).toInt() - 1
            val playerBB     = player.boundingBox
            val trajectoryBB = playerBB.expandTowards(0.0, velY, 0.0)

            data class Candidate(val neighbor: BlockPos, val face: Direction, val score: Double)
            var best: Candidate? = null

            for (dy in 3 downTo -4) {
                for (ddx in -4..4) {
                    for (ddz in -4..4) {
                        val airPos = BlockPos(bx + ddx, feetY + dy, bz + ddz)
                        if (!world.getBlockState(airPos).isAir) continue

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

            RotationManager.movementMode = RotationManager.MovementMode.CLIENT
            RotationManager.rotationMode = RotationManager.RotationMode.CLIENT
            RotationManager.perspective  = true
            RotationManager.setTargetRotation(aimYaw, aimPitch)
            Scaffold.ownsRotation = true
            RotationManager.quickTick(Scaffold.rotSpeed.value)
            RotationManager.physicsYawOverride = RotationManager.getCurrentYaw()
            RotationManager.skipPositionSnap   = true

            val savedY = player.getYRot()
            val savedP = player.getXRot()
            player.setYRot(aimYaw)
            player.setXRot(aimPitch)
            val hitResult = player.pick(reach, 1.0f, false)
            player.setYRot(savedY)
            player.setXRot(savedP)

            val bhr = hitResult as? BlockHitResult ?: return@register
            if (bhr.blockPos != placement.neighbor || bhr.direction != placement.face) return@register

            //val result = client.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, bhr)
            //if (result?.consumesAction() == true) {
            //    player.swing(InteractionHand.MAIN_HAND)
            //}
            KeyMapping.click(InputConstants.getKey(client.options.keyUse.saveString()))
        }
    }

    private fun faceAim(player: LocalPlayer, neighbor: BlockPos, face: Direction): Pair<Float, Float> {
        val eyeY  = player.y + player.eyeHeight
        val fx    = neighbor.x + 0.5 + face.stepX * 0.45
        val fy    = neighbor.y + 0.5 + face.stepY * 0.45
        val fz    = neighbor.z + 0.5 + face.stepZ * 0.45
        val dx    = fx - player.x
        val dy    = fy - eyeY
        val dz    = fz - player.z
        val hDist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.01)
        val yaw   = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(atan2(-dy, hDist)).toFloat().coerceIn(-90f, 90f)
        return yaw to pitch
    }

    fun onDisabled() {
        clutching          = false
        returningToCamera  = false
        moveFreezeTicks    = 0
        isActivelyPlacing  = false
        val player = Minecraft.getInstance().player
        if (savedSlot != -1 && player != null) {
            player.inventory.setSelectedSlot(savedSlot)
        }
        savedSlot = -1
        if (Scaffold.ownsRotation) {
            RotationManager.clearRotation()
            val opts = Minecraft.getInstance().options
            opts.keyUp.setDown(isPhysicalKeyDown(opts.keyUp))
            opts.keyDown.setDown(isPhysicalKeyDown(opts.keyDown))
            opts.keyLeft.setDown(isPhysicalKeyDown(opts.keyLeft))
            opts.keyRight.setDown(isPhysicalKeyDown(opts.keyRight))
            opts.keyShift.setDown(isPhysicalKeyDown(opts.keyShift))
            opts.keyJump.setDown(isPhysicalKeyDown(opts.keyJump))
            Scaffold.ownsRotation = false
        }
    }

    private fun blockMatchesWhitelist(blockName: String, whitelist: List<String>): Boolean {
        for (entry in whitelist) {
            if (entry.endsWith("_category")) {
                val categoryId = entry.removeSuffix("_category")
                val category   = itemCategories.firstOrNull { it.id == categoryId }
                if (category != null && category.matches(blockName)) return true
            } else {
                if (blockName.contains(entry.lowercase())) return true
            }
        }
        return false
    }
}
