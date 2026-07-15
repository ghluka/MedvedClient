package me.ghluka.medved.module.modules.world

import me.ghluka.medved.module.Module
import me.ghluka.medved.config.entry.ItemListEntry
import me.ghluka.medved.util.RotationManager
import me.ghluka.medved.gui.helpers.itemCategories
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import me.ghluka.medved.util.InputUtil.isPhysicalKeyDown
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt


object Clutch : Module("Clutch", "Bridges blocks back to safety when knocked off an edge", Category.WORLD) {

    enum class Trigger { ALWAYS, ON_VOID, ON_LETHAL_FALL, FALL_DISTANCE }

    private val trigger       = enum("trigger", Trigger.ALWAYS)
    private val minFallBlocks = float("blocks", 4f, 1f, 50f).also {
        it.visibleWhen = { trigger.value == Trigger.FALL_DISTANCE }
    }

    private val returnToSlot     = boolean("return to slot", true)

    private val rotSpeed = float("rotation speed", 60f, 10f, 120f)

    private val blockWhitelist = itemList("Block Whitelist", listOf("wool_category"), defaultMode = ItemListEntry.Mode.WHITELIST, filter = ItemListEntry.Filter.BLOCKS_ONLY)

    private var savedSlot        = -1
    private var moveFreezeTicks  = 0
    private var clutching        = false
    private var returningToCamera = false
    private var savedCamYaw      = 0f
    private var savedCamPitch    = 0f
    private var ownsRotation     = false
    private var postClutchBridgeTicks = 0

    private var savedBackYaw     = 0f
    @JvmField var isActivelyPlacing = false

    private data class Placement(
        val placePos: BlockPos,
        val neighbor: BlockPos,
        val face: Direction,
        val score: Double,
    )

    private data class RayTraceProbe(
        val yaw: Float,
        val pitch: Float,
        val hit: BlockHitResult,
    )

    private fun findBlockSlot(player: LocalPlayer): Int {
        val world  = Minecraft.getInstance().level ?: return -1
        for (i in 0..8) {
            val stack = player.inventory.getItem(i)
            if (stack.isEmpty || stack.item !is BlockItem) continue
            val block = (stack.item as BlockItem).block
            if (!block.defaultBlockState().isCollisionShapeFullBlock(world, BlockPos.ZERO)) continue
            if (blockWhitelist.value.isNotEmpty()) {
                val blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString().lowercase()
                if (!blockMatchesWhitelist(blockId, blockWhitelist.value)) continue
            }
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
            var continueGroundBridge = false

            if (player.onGround()) {
                isActivelyPlacing = false
                val physicalW = isPhysicalKeyDown(client.options.keyUp)
                val nearEdgeNow = isNearEdge(player, world)
                if (clutching && postClutchBridgeTicks <= 0) {
                    postClutchBridgeTicks = 8
                }

                continueGroundBridge = postClutchBridgeTicks > 0 &&
                        nearEdgeNow &&
                        horizontalSpeed(player) > 0.03 &&
                        hasBlocks(player)

                if (continueGroundBridge) {
                    postClutchBridgeTicks--
                    isActivelyPlacing = true
                } else if (clutching) {
                    if (savedSlot != -1 && returnToSlot.value) {
                        player.inventory.setSelectedSlot(savedSlot)
                        savedSlot = -1
                    }

                    savedBackYaw    = net.minecraft.util.Mth.wrapDegrees(RotationManager.getClientYaw() + 180f)
                    clutching       = false
                    postClutchBridgeTicks = 0
                }

                if (!physicalW) {
                    moveFreezeTicks = 0
                } else if (moveFreezeTicks > 0) {
                    moveFreezeTicks--
                }

                if (moveFreezeTicks == 0) {
                    RotationManager.clearRotation()
                    val opts = Minecraft.getInstance().options
                    opts.keyUp.setDown(isPhysicalKeyDown(opts.keyUp))
                    opts.keyDown.setDown(isPhysicalKeyDown(opts.keyDown))
                    opts.keyLeft.setDown(isPhysicalKeyDown(opts.keyLeft))
                    opts.keyRight.setDown(isPhysicalKeyDown(opts.keyRight))
                    ownsRotation = false
                } else if (!continueGroundBridge && physicalW && ownsRotation) {
                    RotationManager.movementMode = RotationManager.MovementMode.CLIENT
                    RotationManager.rotationMode = RotationManager.RotationMode.CLIENT
                    RotationManager.perspective  = true
                    RotationManager.setTargetRotation(savedBackYaw, player.getXRot())
                    RotationManager.quickTick(rotSpeed.value)

                    client.options.keyUp.setDown(false)
                    client.options.keyDown.setDown(true)
                }

                if (!continueGroundBridge) {
                    return@register
                }
            }

            if (!continueGroundBridge &&
                player.deltaMovement.y >= 0 &&
                !isAirborneOffSupport(player, world)
            ) {
                return@register
            }
            if (!continueGroundBridge && !triggerMet(player, world)) return@register
            if (!hasBlocks(player)) return@register

            if (!clutching) {
                beginClutch(player)
            }

            isActivelyPlacing = true

                client.options.keyUp.setDown(false)
                client.options.keyDown.setDown(false)
                client.options.keyLeft.setDown(false)
                client.options.keyRight.setDown(false)

            if (player.mainHandItem.isEmpty || player.mainHandItem.item !is BlockItem) {
                val slot = findBlockSlot(player)
                if (slot == -1) return@register
                player.inventory.setSelectedSlot(slot)
            } else if (blockWhitelist.value.isNotEmpty()) {
                val blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey((player.mainHandItem.item as BlockItem).block).toString().lowercase()
                if (!blockMatchesWhitelist(blockId, blockWhitelist.value)) {
                    val slot = findBlockSlot(player)
                    if (slot == -1) return@register
                    player.inventory.setSelectedSlot(slot)
                }
            }

            val reach = if (player.isCreative) 5.0 else 4.5
            val placements = findBestPlacements(player, world, reach)
            for (placement in placements) {
                val probe = rayTracePlacement(player, placement, reach) ?: continue

                RotationManager.movementMode = RotationManager.MovementMode.CLIENT
                RotationManager.rotationMode = RotationManager.RotationMode.CLIENT
                RotationManager.perspective  = true
                RotationManager.setTargetRotation(probe.yaw, probe.pitch)
                ownsRotation = true
                RotationManager.quickTick(rotSpeed.value)
                RotationManager.physicsYawOverride = RotationManager.getCurrentYaw()
                RotationManager.skipPositionSnap   = true

                val reachedHit = rayTraceAt(
                    player,
                    reach,
                    RotationManager.getCurrentYaw(),
                    RotationManager.getCurrentPitch(),
                ) ?: return@register
                if (reachedHit.blockPos.relative(reachedHit.direction) != placement.placePos) return@register

                val result = client.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, reachedHit)
                if (result?.consumesAction() == true) {
                    player.swing(InteractionHand.MAIN_HAND)
                    return@register
                }
            }
        }
    }

    override fun onTick(client: Minecraft) {}

    private fun beginClutch(player: LocalPlayer) {
        clutching = true
        returningToCamera = false
        if (savedSlot == -1) {
            savedSlot = if (returnToSlot.value) player.inventory.selectedSlot else -1
        }
        savedCamYaw = RotationManager.getClientYaw()
        savedCamPitch = player.getXRot()
    }

    private fun findBestPlacements(
        player: LocalPlayer,
        world: net.minecraft.client.multiplayer.ClientLevel,
        reach: Double,
    ): List<Placement> {
        val velocity = player.deltaMovement
        val eyeY = player.y + player.eyeHeight
        val playerBB = player.boundingBox
        val placeFaces = arrayOf(
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST,
            Direction.WEST,
            Direction.DOWN,
        )

        val placements = mutableListOf<Placement>()
        val horizontalSpeed = sqrt(velocity.x * velocity.x + velocity.z * velocity.z)
        val diagonalMotion = abs(velocity.x) > 0.04 && abs(velocity.z) > 0.04
        val stepX = velocity.x.compareTo(0.0)
        val stepZ = velocity.z.compareTo(0.0)
        val maxLead = if (horizontalSpeed > 0.6) 8 else 6
        val leadStep = if (horizontalSpeed > 0.45) 0.5 else 1.0

        var lead = 0.0
        while (lead <= maxLead) {
            val predictedX = player.x + velocity.x * lead
            val predictedY = player.y + velocity.y.coerceAtMost(0.0) * lead
            val predictedZ = player.z + velocity.z * lead
            val baseX = floor(predictedX).toInt()
            val baseY = floor(predictedY).toInt() - 1
            val baseZ = floor(predictedZ).toInt()
            val radius = (2 + ceil(horizontalSpeed * lead).toInt()).coerceAtMost(5)

            for (dy in 0 downTo -2) {
                for (dx in -radius..radius) {
                    for (dz in -radius..radius) {
                        val airPos = BlockPos(baseX + dx, baseY + dy, baseZ + dz)
                        if (!world.getBlockState(airPos).isAir) continue

                        val blockBB = net.minecraft.world.phys.AABB(
                            airPos.x.toDouble(), airPos.y.toDouble(), airPos.z.toDouble(),
                            airPos.x + 1.0, airPos.y + 1.0, airPos.z + 1.0,
                        )
                        if (playerBB.intersects(blockBB)) continue

                        for (dir in placeFaces) {
                            val neighbor = airPos.relative(dir)
                            val neighborState = world.getBlockState(neighbor)
                            if (neighborState.isAir || !neighborState.fluidState.isEmpty) continue
                            if (!neighborState.isCollisionShapeFullBlock(world, neighbor)) continue

                            val face = dir.opposite
                            val fx = neighbor.x + 0.5 + face.stepX * 0.45
                            val fy = neighbor.y + 0.5 + face.stepY * 0.45
                            val fz = neighbor.z + 0.5 + face.stepZ * 0.45
                            val ex = fx - player.x
                            val ey = fy - eyeY
                            val ez = fz - player.z
                            if (ex * ex + ey * ey + ez * ez > reach * reach) continue

                            val yDev = abs(airPos.y - baseY).toDouble()
                            val hDev = sqrt(
                                (airPos.x + 0.5 - predictedX) * (airPos.x + 0.5 - predictedX) +
                                        (airPos.z + 0.5 - predictedZ) * (airPos.z + 0.5 - predictedZ)
                            )
                            val landingSupport = horizontalOverlap(airPos, predictedX, predictedZ)
                            val sweptSupport = sweptFootprintOverlap(player, airPos, lead)
                            val currentSupport = horizontalOverlap(airPos, player.x, player.z)
                            val connectorBonus = diagonalConnectorBonus(
                                airPos,
                                baseX,
                                baseZ,
                                stepX,
                                stepZ,
                                diagonalMotion,
                            )
                            if (!isUsefulPlacement(
                                    landingSupport,
                                    sweptSupport,
                                    currentSupport,
                                    connectorBonus,
                                )
                            ) {
                                continue
                            }
                            val facePenalty = if (face == Direction.UP) 0.6 else 0.0
                            val score = yDev * 22.0 +
                                    hDev * 3.0 +
                                    lead * 0.08 +
                                    facePenalty -
                                    landingSupport * 6.0 -
                                    sweptSupport * 14.0 -
                                    currentSupport * 10.0 -
                                    connectorBonus
                            placements.add(Placement(airPos, neighbor, face, score))
                        }
                    }
                }
            }
            lead += leadStep
        }

        return placements
            .groupBy { Triple(it.placePos, it.neighbor, it.face) }
            .mapNotNull { (_, candidates) -> candidates.minByOrNull { it.score } }
            .sortedBy { it.score }
            .take(160)
    }

    private fun isUsefulPlacement(
        landingSupport: Double,
        sweptSupport: Double,
        currentSupport: Double,
        connectorBonus: Double,
    ): Boolean {
        return currentSupport > 0.04 ||
                sweptSupport > 0.015 ||
                landingSupport > 0.04 ||
                connectorBonus > 0.0
    }

    private fun diagonalConnectorBonus(
        pos: BlockPos,
        baseX: Int,
        baseZ: Int,
        stepX: Int,
        stepZ: Int,
        diagonalMotion: Boolean,
    ): Double {
        if (!diagonalMotion || stepX == 0 || stepZ == 0) return 0.0

        val forwardX = pos.x == baseX + stepX && pos.z == baseZ
        val forwardZ = pos.x == baseX && pos.z == baseZ + stepZ
        val diagonal = pos.x == baseX + stepX && pos.z == baseZ + stepZ

        return when {
            forwardX || forwardZ -> 32.0
            diagonal -> 8.0
            else -> 0.0
        }
    }

    private fun horizontalOverlap(pos: BlockPos, x: Double, z: Double): Double {
        val dx = (x - (pos.x + 0.5)).let { abs(it).coerceAtMost(0.5) }
        val dz = (z - (pos.z + 0.5)).let { abs(it).coerceAtMost(0.5) }
        return (1.0 - dx * 2.0) * (1.0 - dz * 2.0)
    }

    private fun sweptFootprintOverlap(player: LocalPlayer, pos: BlockPos, lead: Double): Double {
        val velocity = player.deltaMovement
        val halfWidth = player.bbWidth / 2.0
        val samples = arrayOf(
            0.0,
            (lead * 0.5).coerceAtLeast(0.0),
            lead,
        )

        var best = 0.0
        for (sampleLead in samples) {
            val x = player.x + velocity.x * sampleLead
            val z = player.z + velocity.z * sampleLead
            val minX = x - halfWidth
            val maxX = x + halfWidth
            val minZ = z - halfWidth
            val maxZ = z + halfWidth
            val overlapX = (minOf(maxX, pos.x + 1.0) - maxOf(minX, pos.x.toDouble())).coerceAtLeast(0.0)
            val overlapZ = (minOf(maxZ, pos.z + 1.0) - maxOf(minZ, pos.z.toDouble())).coerceAtLeast(0.0)
            best = maxOf(best, overlapX * overlapZ)
        }
        return best
    }

    private fun horizontalSpeed(player: LocalPlayer): Double {
        val velocity = player.deltaMovement
        return sqrt(velocity.x * velocity.x + velocity.z * velocity.z)
    }

    private fun isNearEdge(
        player: LocalPlayer,
        world: net.minecraft.client.multiplayer.ClientLevel,
    ): Boolean {
        val y = floor(player.y - 1.0).toInt()
        val margin = 0.32
        val points = arrayOf(
            player.x to player.z,
            player.x - margin to player.z,
            player.x + margin to player.z,
            player.x to player.z - margin,
            player.x to player.z + margin,
        )

        return points.any { (x, z) ->
            val pos = BlockPos(floor(x).toInt(), y, floor(z).toInt())
            val state = world.getBlockState(pos)
            state.isAir || !state.fluidState.isEmpty || !state.isCollisionShapeFullBlock(world, pos)
        }
    }

    private fun isAirborneOffSupport(
        player: LocalPlayer,
        world: net.minecraft.client.multiplayer.ClientLevel,
    ): Boolean {
        if (player.onGround()) return false

        val velocity = player.deltaMovement
        val halfWidth = player.bbWidth / 2.0 + 0.03
        val y = floor(player.y - 1.0).toInt()
        val points = arrayOf(
            player.x to player.z,
            player.x + velocity.x to player.z + velocity.z,
            player.x + velocity.x + halfWidth to player.z + velocity.z + halfWidth,
            player.x + velocity.x - halfWidth to player.z + velocity.z + halfWidth,
            player.x + velocity.x + halfWidth to player.z + velocity.z - halfWidth,
            player.x + velocity.x - halfWidth to player.z + velocity.z - halfWidth,
        )

        return points.none { (x, z) ->
            val pos = BlockPos(floor(x).toInt(), y, floor(z).toInt())
            val state = world.getBlockState(pos)
            !state.isAir && state.fluidState.isEmpty && state.isCollisionShapeFullBlock(world, pos)
        }
    }

    private fun rayTracePlacement(
        player: LocalPlayer,
        placement: Placement,
        reach: Double,
    ): RayTraceProbe? {
        for (point in faceAimPoints(player, placement.neighbor, placement.face)) {
            val (aimYaw, aimPitch) = faceAim(player, point)
            val savedY = player.getYRot()
            val savedP = player.getXRot()
            player.setYRot(aimYaw)
            player.setXRot(aimPitch)
            val hitResult = player.pick(reach, 1.0f, false)
            player.setYRot(savedY)
            player.setXRot(savedP)

            val hit = hitResult as? BlockHitResult ?: continue
            val tracedPlacePos = hit.blockPos.relative(hit.direction)
            if (tracedPlacePos == placement.placePos) {
                return RayTraceProbe(aimYaw, aimPitch, hit)
            }
        }
        return null
    }

    private fun rayTraceAt(player: LocalPlayer, reach: Double, yaw: Float, pitch: Float): BlockHitResult? {
        val savedY = player.getYRot()
        val savedP = player.getXRot()
        player.setYRot(yaw)
        player.setXRot(pitch)
        val hitResult = player.pick(reach, 1.0f, false)
        player.setYRot(savedY)
        player.setXRot(savedP)
        return hitResult as? BlockHitResult
    }

    private fun faceAimPoints(player: LocalPlayer, neighbor: BlockPos, face: Direction): Array<Vec3> {
        val cx = neighbor.x + 0.5 + face.stepX * 0.49
        val cy = neighbor.y + 0.5 + face.stepY * 0.49
        val cz = neighbor.z + 0.5 + face.stepZ * 0.49
        val velocity = player.deltaMovement
        val diagonal = abs(velocity.x) > 0.04 && abs(velocity.z) > 0.04
        val offsets = if (diagonal) {
            doubleArrayOf(-0.42, 0.42, -0.28, 0.28, 0.0, -0.14, 0.14)
        } else {
            doubleArrayOf(0.0, -0.32, 0.32, -0.16, 0.16)
        }
        val verticalOffsets = if (diagonal) {
            doubleArrayOf(0.0, -0.18, 0.18, -0.34, 0.34)
        } else {
            offsets
        }

        val points = when (face.axis) {
            Direction.Axis.X -> offsets.flatMap { z ->
                verticalOffsets.map { y -> Vec3(cx, cy + y, cz + z) }
            }
            Direction.Axis.Y -> offsets.flatMap { x ->
                offsets.map { z -> Vec3(cx + x, cy, cz + z) }
            }
            Direction.Axis.Z -> offsets.flatMap { x ->
                verticalOffsets.map { y -> Vec3(cx + x, cy + y, cz) }
            }
        }

        val currentYaw = RotationManager.getClientYaw()
        return points.sortedBy { point ->
            val yaw = faceAim(player, point).first
            abs(net.minecraft.util.Mth.wrapDegrees(yaw - currentYaw))
        }.toTypedArray()
    }

    private fun faceAim(player: LocalPlayer, point: Vec3): Pair<Float, Float> {
        val eyeY  = player.y + player.eyeHeight
        val dx    = point.x - player.x
        val dy    = point.y - eyeY
        val dz    = point.z - player.z
        val hDist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.01)
        val yaw   = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(atan2(-dy, hDist)).toFloat().coerceIn(-90f, 90f)
        return yaw to pitch
    }

    override fun onDisabled() {
        clutching          = false
        returningToCamera  = false
        moveFreezeTicks    = 0
        postClutchBridgeTicks = 0
        isActivelyPlacing  = false
        val player = Minecraft.getInstance().player
        if (savedSlot != -1 && player != null && returnToSlot.value) {
            player.inventory.setSelectedSlot(savedSlot)
        }
        savedSlot = -1
        if (ownsRotation) {
            RotationManager.clearRotation()
            val opts = Minecraft.getInstance().options
            opts.keyUp.setDown(isPhysicalKeyDown(opts.keyUp))
            opts.keyDown.setDown(isPhysicalKeyDown(opts.keyDown))
            opts.keyLeft.setDown(isPhysicalKeyDown(opts.keyLeft))
            opts.keyRight.setDown(isPhysicalKeyDown(opts.keyRight))
            opts.keyShift.setDown(isPhysicalKeyDown(opts.keyShift))
            ownsRotation = false
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
