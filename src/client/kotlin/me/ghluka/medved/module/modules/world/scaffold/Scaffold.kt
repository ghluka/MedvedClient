package me.ghluka.medved.module.modules.world.scaffold

import com.mojang.blaze3d.platform.InputConstants
import me.ghluka.medved.config.entry.ItemListEntry
import me.ghluka.medved.gui.helpers.itemCategories
import me.ghluka.medved.mixin.client.LocalPlayerAccessor
import me.ghluka.medved.module.Module
import me.ghluka.medved.util.InputUtil
import me.ghluka.medved.util.RotationManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

object Scaffold : Module("Scaffold", "Automatically places blocks under you while walking", Category.WORLD) {

    enum class BridgeMode {
        NINJA, BREEZILY, TELLY
    }

    enum class ScaffoldState {
        TOWERING, UPSTACKING, DIAGONAL, STRAIGHT, NONE
    }

    val blockWhitelist = itemList("Block Whitelist", listOf("wool_category"), defaultMode = ItemListEntry.Mode.WHITELIST, filter = ItemListEntry.Filter.BLOCKS_ONLY)
    val bridgeMode = enum("mode", BridgeMode.NINJA)


    val rotSpeed = float("rot speed", 100f, 60f, 120f).also {
        it.visibleWhen = { bridgeMode.value == BridgeMode.TELLY }
    }

    private val crouchDelay = intRange("crouch delay", 45 to 55, 0, 500).also {
        it.visibleWhen = { bridgeMode.value == BridgeMode.NINJA }
    }
    private val autoclickCps = intRange("autoclick cps", 4 to 6, 1, 6)
    val disableOnDeath = boolean("disable on death", true)
    val disableOnWorldChange = boolean("disable on world change", true)

    private var isCrouching = false
    private var crouchWaitTicks = 0

    private var autoclickAccum = 0.0f
    private var autoclickTargetCps = 0
    var ownsRotation = false
    private var ninjaStrafeRight = true
    private var breezilyStrafeRight = true
    private var diagonalBreezilyStrafeTicks = 0
    private var diagonalBreezilyStrafeRight = true
    private var diagonalBreezilyAligned = false

    private var telly: Telly? = null
    private var lastBridgeMode = bridgeMode.value

    private data class Placement(
        val placePos: BlockPos,
        val neighbor: BlockPos,
        val face: Direction,
        val score: Double,
    )

    private fun findBlockSlot(player: LocalPlayer): Int {
        val world = Minecraft.getInstance().level ?: return -1
        for (i in 0..8) {
            val stack = player.inventory.getItem(i)
            if (stack.isEmpty || stack.item !is BlockItem) continue
            val block = (stack.item as BlockItem).block
            if (!block.defaultBlockState().isCollisionShapeFullBlock(world, BlockPos.ZERO)) continue
            if (blockWhitelist.value.isNotEmpty()) {
                val blockId = BuiltInRegistries.BLOCK.getKey(block).toString().lowercase()
                if (!blockMatchesWhitelist(blockId, blockWhitelist.value)) continue
            }
            return i
        }
        return -1
    }

    private fun hasBlocks(player: LocalPlayer): Boolean = findBlockSlot(player) != -1

    init {
        telly = Telly
        bridgeMode.onChange { newMode ->
            if (lastBridgeMode == BridgeMode.TELLY && newMode != BridgeMode.TELLY) {
                telly?.onDisabled()
            }
            lastBridgeMode = newMode
        }

        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (!isEnabled()) return@register
            if (bridgeMode.value == BridgeMode.TELLY) return@register
            val player = client.player ?: return@register
            if (!hasBlocks(player)) {
                if (ownsRotation) {
                    RotationManager.clearRotation()
                    RotationManager.perspective = false
                    ownsRotation = false
                }
                return@register
            }

            player.isSprinting = false
            player.setSprinting(false)
            (player as LocalPlayerAccessor).setSprintTriggerTime(0)
            client.options.keySprint.setDown(false)

            RotationManager.movementMode = RotationManager.MovementMode.CLIENT
            RotationManager.rotationMode = RotationManager.RotationMode.CLIENT
            RotationManager.perspective = true

            val W = InputUtil.isPhysicalKeyDown(client.options.keyUp)
            val S = InputUtil.isPhysicalKeyDown(client.options.keyDown)
            val A = InputUtil.isPhysicalKeyDown(client.options.keyLeft)
            val D = InputUtil.isPhysicalKeyDown(client.options.keyRight)
            val isJumping = InputUtil.isPhysicalKeyDown(client.options.keyJump)
            val movingHoriz = W || S || A || D

            var targetCard = RotationManager.getClientYaw()
            if (movingHoriz) {
                val camRad = Math.toRadians(targetCard.toDouble())
                var mx = 0.0; var mz = 0.0
                if (W) { mx -= sin(camRad); mz += cos(camRad)
                }
                if (S) { mx += sin(camRad); mz -= cos(camRad)
                }
                if (D) { mx -= cos(camRad); mz -= sin(camRad)
                }
                if (A) { mx += cos(camRad); mz += sin(camRad)
                }

                val rawMoveYaw = Math.toDegrees(atan2(-mx, mz)).toFloat()
                targetCard = Math.round(rawMoveYaw / 45.0f) * 45.0f
            } else {
                targetCard = Math.round(RotationManager.getClientYaw() / 45.0f) * 45.0f
            }

            val isDiagonal = (targetCard % 90.0f) != 0.0f

            val scaffoldState = when {
                !movingHoriz && isJumping -> ScaffoldState.TOWERING
                movingHoriz && isJumping -> ScaffoldState.UPSTACKING
                movingHoriz && isDiagonal -> ScaffoldState.DIAGONAL
                movingHoriz && !isDiagonal -> ScaffoldState.STRAIGHT
                else -> ScaffoldState.NONE
            }

            var pressRight = false
            var pressLeft  = false

            fun applyBreezilyBridge() {
                val camRad = Math.toRadians(RotationManager.getClientYaw().toDouble())
                if (D && !A) breezilyStrafeRight = true
                if (A && !D) breezilyStrafeRight = false
                breezilyStrafeRight = chooseBreezilyStrafeRight(player, camRad, invertCorrection = W && !S)
                pressRight = breezilyStrafeRight
                pressLeft  = !breezilyStrafeRight
                client.options.keyUp.setDown(false)
                client.options.keyDown.setDown(true)
                client.options.keyRight.setDown(pressRight)
                client.options.keyLeft.setDown(pressLeft)
            }

            fun sidewaysBridge() {
                if (W && !S && !A && !D) {
                    val camRad = Math.toRadians(RotationManager.getClientYaw().toDouble())
                    ninjaStrafeRight = chooseNinjaStrafeRight(player, camRad)
                    var mx = -sin(camRad)
                    var mz = cos(camRad)
                    if (ninjaStrafeRight) {
                        mx -= cos(camRad)
                        mz -= sin(camRad)
                    } else {
                        mx += cos(camRad)
                        mz += sin(camRad)
                    }
                    val rawMoveYaw = Math.toDegrees(atan2(-mx, mz)).toFloat()
                    targetCard = Math.round(rawMoveYaw / 45.0f) * 45.0f

                    pressRight = ninjaStrafeRight
                    pressLeft = !ninjaStrafeRight
                    client.options.keyUp.setDown(false)
                    client.options.keyDown.setDown(true)
                    client.options.keyRight.setDown(pressRight)
                    client.options.keyLeft.setDown(pressLeft)
                } else {
                    pressRight = false
                    pressLeft = false
                    client.options.keyUp.setDown(false)
                    client.options.keyDown.setDown(true)
                    client.options.keyRight.setDown(false)
                    client.options.keyLeft.setDown(false)
                }
            }

            when (scaffoldState) {
                ScaffoldState.STRAIGHT -> {
                    resetDiagonalBreezilyCorrection()
                    when (bridgeMode.value) {
                        BridgeMode.NINJA -> {
                            sidewaysBridge()
                        }
                        BridgeMode.BREEZILY -> {
                            applyBreezilyBridge()
                        }

                        else -> {}
                    }
                }


                ScaffoldState.DIAGONAL -> {
                    when (bridgeMode.value) {
                        BridgeMode.NINJA -> {
                            resetDiagonalBreezilyCorrection()
                            pressRight = false
                            pressLeft = false
                            client.options.keyUp.setDown(false)
                            client.options.keyDown.setDown(true)
                            client.options.keyRight.setDown(false)
                            client.options.keyLeft.setDown(false)
                        }
                        BridgeMode.BREEZILY -> {
                            val correction = diagonalBreezilyCorrection(player, Mth.wrapDegrees(targetCard + 180f))
                            pressRight = correction == true
                            pressLeft = correction == false
                            client.options.keyUp.setDown(false)
                            client.options.keyDown.setDown(true)
                            client.options.keyRight.setDown(pressRight)
                            client.options.keyLeft.setDown(pressLeft)
                        }

                        else -> {}
                    }
                }


                ScaffoldState.UPSTACKING, ScaffoldState.TOWERING, ScaffoldState.NONE -> {
                    resetDiagonalBreezilyCorrection()
                    pressRight = false
                    pressLeft = false
                    if (scaffoldState == ScaffoldState.UPSTACKING) {
                        if (!isDiagonal) {
                            sidewaysBridge()
                        }
                        else {
                            client.options.keyUp.setDown(false)
                            client.options.keyDown.setDown(true)
                            client.options.keyRight.setDown(false)
                            client.options.keyLeft.setDown(false)
                        }
                    } else {
                        client.options.keyUp.setDown(false)
                        client.options.keyDown.setDown(false)
                        client.options.keyRight.setDown(false)
                        client.options.keyLeft.setDown(false)
                    }
                }
            }

            val hasSideKey = pressRight || pressLeft

            val aimYaw = Mth.wrapDegrees(targetCard + 180f)
            val aimPitch = when (scaffoldState) {
                ScaffoldState.TOWERING -> 90.0f

                ScaffoldState.UPSTACKING -> correctedUpstackPitch(player, W, S, A, D)
                    //if (isDiagonal || hasSideKey) 75.6f else 78.5f

                ScaffoldState.DIAGONAL -> 75.6f

                ScaffoldState.STRAIGHT -> when (bridgeMode.value) {
                    BridgeMode.NINJA -> 78.0f
                    BridgeMode.BREEZILY -> 79.9f
                    else -> 0f
                }

                else -> if (isDiagonal || hasSideKey) 78.0f else 80f
            }

            RotationManager.setTargetRotation(aimYaw, aimPitch)
            ownsRotation = true
            RotationManager.quickTick(60f)

            val level = client.level
            if (level != null) {
                fun getForceEdgePlaceHit(): BlockHitResult? {
                    if (!player.onGround() && scaffoldState != ScaffoldState.UPSTACKING) {
                        return null
                    }

                    if (scaffoldState == ScaffoldState.UPSTACKING) {
                        findUpstackPlacement(player, level)?.let { placement ->
                            return placement.toHitResult()
                        }
                    }

                    val lookHit = client.hitResult as? BlockHitResult ?: return null

                    val hitPos = lookHit.blockPos
                    if (level.getBlockState(hitPos).isAir) return null

                    val face = lookHit.direction
                    if (
                        (scaffoldState != ScaffoldState.UPSTACKING
                                && !isDiagonal) &&
                        face != Direction.NORTH &&
                        face != Direction.SOUTH &&
                        face != Direction.EAST &&
                        face != Direction.WEST
                    ) {
                        return null
                    }

                    val placePos = hitPos.relative(face)

                    if (!level.getBlockState(placePos).isAir) return null

                    val hitVec = Vec3(
                        hitPos.x + 0.5 + face.stepX * 0.5,
                        hitPos.y + 0.5 + face.stepY * 0.5,
                        hitPos.z + 0.5 + face.stepZ * 0.5
                    )

                    return BlockHitResult(
                        hitVec,
                        face,
                        hitPos,
                        false
                    )
                }

                val stack = player.mainHandItem
                if (stack.isEmpty || stack.item !is BlockItem) {
                    val slot = findBlockSlot(player)
                    if (slot != -1) player.inventory.setSelectedSlot(slot)
                } else if (blockWhitelist.value.isNotEmpty()) {
                    val blockId = BuiltInRegistries.BLOCK.getKey((stack.item as BlockItem).block).toString().lowercase()
                    if (!blockMatchesWhitelist(blockId, blockWhitelist.value)) {
                        val slot = findBlockSlot(player)
                        if (slot != -1) player.inventory.setSelectedSlot(slot)
                    }
                }

                val forceHit = getForceEdgePlaceHit()
                if (forceHit != null) {
                    val result = client.gameMode?.useItemOn(
                        player,
                        InteractionHand.MAIN_HAND,
                        forceHit
                    )

                    if (result?.consumesAction() == true) {
                        player.swing(InteractionHand.MAIN_HAND)
                    }
                }
                else {
                    if (autoclickTargetCps == 0) {
                        val (lo, hi) = autoclickCps.value
                        autoclickTargetCps = if (hi > lo) (lo..hi).random() else lo
                    }
                    autoclickAccum += autoclickTargetCps / 20.0f
                    while (autoclickAccum >= 1.0f) {
                        KeyMapping.click(InputConstants.getKey(client.options.keyUse.saveString()))
                        autoclickAccum -= 1.0f
                        val (lo, hi) = autoclickCps.value
                        autoclickTargetCps = if (hi > lo) (lo..hi).random() else lo
                    }
                }
            }

            // crouch walk
            if (player.onGround()
                && (
                        (bridgeMode.value == BridgeMode.NINJA
                                && (scaffoldState == ScaffoldState.STRAIGHT
                                || scaffoldState == ScaffoldState.DIAGONAL)
                             )
                     || (scaffoldState == ScaffoldState.UPSTACKING
                             )
                )) {
                val nearEdge = if (movingHoriz) {
                    isMovingTowardEdge(player, client.level!!, W, S, A, D)
                } else {
                    isNearEdge(player, client.level!!)
                }

                if (nearEdge && !isCrouching) {
                    isCrouching = true
                    val (lo, hi) = crouchDelay.value
                    val delayMs = if (hi > lo) (lo..hi).random() else lo
                    crouchWaitTicks = (delayMs / 50).coerceAtLeast(1)
                }

                if (isCrouching) {
                    client.options.keyShift.setDown(true)
                    player.setShiftKeyDown(true)
                    if (!nearEdge) crouchWaitTicks--
                    if (crouchWaitTicks <= 0 && !nearEdge) isCrouching = false
                } else {
                    val physicalShift = InputUtil.isPhysicalKeyDown(client.options.keyShift)
                    client.options.keyShift.setDown(physicalShift)
                    player.setShiftKeyDown(physicalShift)
                }
            }
            else {
                isCrouching = false
                crouchWaitTicks = 0
                val physicalShift = InputUtil.isPhysicalKeyDown(client.options.keyShift)
                client.options.keyShift.setDown(physicalShift)
                player.setShiftKeyDown(physicalShift)
            }
        }
    }

    private fun isNearEdge(player: LocalPlayer, world: ClientLevel): Boolean {
        val px = player.x
        val by = floor(player.y - 1.0).toInt()
        val pz = player.z

        val margin = 0.3
        val offsets = arrayOf(
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(-margin, 0.0),
            doubleArrayOf(margin, 0.0),
            doubleArrayOf(0.0, -margin),
            doubleArrayOf(0.0, margin)
        )

        for (offset in offsets) {
            val bx = floor(px + offset[0]).toInt()
            val bz = floor(pz + offset[1]).toInt()
            val state = world.getBlockState(BlockPos(bx, by, bz))
            if (state.isAir || !state.fluidState.isEmpty || !state.isCollisionShapeFullBlock(world,
                    BlockPos(bx, by, bz)
                )) {
                return true
            }
        }
        return false
    }

    private fun chooseNinjaStrafeRight(player: LocalPlayer, camRad: Double): Boolean {
        val sideOffset = lateralBlockOffset(player, camRad)

        return when {
            sideOffset > 0.08 -> true
            sideOffset < -0.08 -> false
            else -> ninjaStrafeRight
        }
    }

    private fun chooseBreezilyStrafeRight(
        player: LocalPlayer,
        camRad: Double,
        invertCorrection: Boolean,
    ): Boolean {
        val sideOffset = if (invertCorrection) {
            -lateralBlockOffset(player, camRad)
        } else {
            lateralBlockOffset(player, camRad)
        }

        return when {
            sideOffset > 0.18 -> false
            sideOffset < -0.18 -> true
            else -> breezilyStrafeRight
        }
    }

    private fun lateralBlockOffset(player: LocalPlayer, camRad: Double): Double {
        val localX = player.x - floor(player.x) - 0.5
        val localZ = player.z - floor(player.z) - 0.5
        val rightX = -cos(camRad)
        val rightZ = -sin(camRad)
        return localX * rightX + localZ * rightZ
    }

    private fun diagonalBreezilyCorrection(player: LocalPlayer, aimYaw: Float): Boolean? {
        if (diagonalBreezilyAligned) {
            return null
        }

        if (diagonalBreezilyStrafeTicks > 0) {
            diagonalBreezilyStrafeTicks--
            if (diagonalBreezilyStrafeTicks == 0) {
                diagonalBreezilyAligned = true
            }
            return diagonalBreezilyStrafeRight
        }

        val sideOffset = lateralBlockOffset(player, Math.toRadians(aimYaw.toDouble()))
        diagonalBreezilyStrafeRight = when {
            sideOffset > 0.08 -> false
            sideOffset < -0.08 -> true
            else -> {
                diagonalBreezilyAligned = true
                return null
            }
        }
        diagonalBreezilyStrafeTicks = 1
        return diagonalBreezilyStrafeRight
    }

    private fun resetDiagonalBreezilyCorrection() {
        diagonalBreezilyStrafeTicks = 0
        diagonalBreezilyAligned = false
    }

    private fun isMovingTowardEdge(
        player: LocalPlayer,
        world: ClientLevel,
        forward: Boolean,
        back: Boolean,
        left: Boolean,
        right: Boolean,
    ): Boolean {
        val yaw = Math.toRadians(RotationManager.getClientYaw().toDouble())
        var moveX = 0.0
        var moveZ = 0.0

        if (forward) {
            moveX -= sin(yaw)
            moveZ += cos(yaw)
        }
        if (back) {
            moveX += sin(yaw)
            moveZ -= cos(yaw)
        }
        if (right) {
            moveX -= cos(yaw)
            moveZ -= sin(yaw)
        }
        if (left) {
            moveX += cos(yaw)
            moveZ += sin(yaw)
        }

        val len = sqrt(moveX * moveX + moveZ * moveZ)
        if (len < 0.001) return isNearEdge(player, world)

        val margin = 0.42
        val checkX = player.x + moveX / len * margin
        val checkZ = player.z + moveZ / len * margin
        val checkY = floor(player.y - 1.0).toInt()
        val pos = BlockPos(floor(checkX).toInt(), checkY, floor(checkZ).toInt())
        val state = world.getBlockState(pos)
        return state.isAir || !state.fluidState.isEmpty || !state.isCollisionShapeFullBlock(world, pos)
    }

    private fun findUpstackPlacement(
        player: LocalPlayer,
        world: ClientLevel,
    ): Placement? {
        val velocity = player.deltaMovement
        val baseY = floor(player.y - 1.0).toInt()
        val candidates = mutableListOf<BlockPos>()

        fun addCandidate(x: Double, z: Double, yOffset: Int = 0) {
            val pos = BlockPos(floor(x).toInt(), baseY + yOffset, floor(z).toInt())
            if (pos !in candidates) candidates.add(pos)
        }

        addCandidate(player.x, player.z)
        addCandidate(player.x + velocity.x * 0.6, player.z + velocity.z * 0.6)
        addCandidate(player.x + velocity.x * 1.2, player.z + velocity.z * 1.2)
        addCandidate(player.x, player.z, -1)
        addCandidate(player.x + velocity.x * 0.8, player.z + velocity.z * 0.8, 1)
        addCandidate(player.x + velocity.x * 1.4, player.z + velocity.z * 1.4, 1)

        val placeFaces = arrayOf(
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST,
            Direction.WEST,
            Direction.DOWN,
        )

        var best: Placement? = null
        for (placePos in candidates) {
            if (!world.getBlockState(placePos).isAir) continue

            for (dir in placeFaces) {
                val neighbor = placePos.relative(dir)
                val neighborState = world.getBlockState(neighbor)
                if (neighborState.isAir || !neighborState.fluidState.isEmpty) continue
                if (!neighborState.isCollisionShapeFullBlock(world, neighbor)) continue

                val face = dir.opposite
                val dist = squaredDistanceToFace(player, neighbor, face)
                if (dist > 20.25) continue

                val predictedX = player.x + velocity.x
                val predictedZ = player.z + velocity.z
                val horizontalError = sqrt(
                    (placePos.x + 0.5 - predictedX) * (placePos.x + 0.5 - predictedX) +
                            (placePos.z + 0.5 - predictedZ) * (placePos.z + 0.5 - predictedZ)
                )
                val verticalPenalty = abs(placePos.y - baseY) * 4.0
                val facePenalty = if (face == Direction.UP) 0.8 else 0.0
                val score = horizontalError * 4.0 + verticalPenalty + facePenalty + dist * 0.02

                if (best == null || score < best.score) {
                    best = Placement(placePos, neighbor, face, score)
                }
            }
        }

        return best
    }

    private fun Placement.toHitResult(): BlockHitResult {
        val hitVec = Vec3(
            neighbor.x + 0.5 + face.stepX * 0.45,
            neighbor.y + 0.5 + face.stepY * 0.45,
            neighbor.z + 0.5 + face.stepZ * 0.45,
        )
        return BlockHitResult(hitVec, face, neighbor, false)
    }

    private fun squaredDistanceToFace(player: LocalPlayer, neighbor: BlockPos, face: Direction): Double {
        val eyeY = player.y + player.eyeHeight
        val x = neighbor.x + 0.5 + face.stepX * 0.45
        val y = neighbor.y + 0.5 + face.stepY * 0.45
        val z = neighbor.z + 0.5 + face.stepZ * 0.45
        return (x - player.x) * (x - player.x) +
                (y - eyeY) * (y - eyeY) +
                (z - player.z) * (z - player.z)
    }

    private fun correctedUpstackPitch(
        player: LocalPlayer,
        forward: Boolean,
        back: Boolean,
        left: Boolean,
        right: Boolean,
    ): Float {
        val yaw = Math.toRadians(RotationManager.getClientYaw().toDouble())
        var moveX = 0.0
        var moveZ = 0.0

        if (forward) {
            moveX -= sin(yaw)
            moveZ += cos(yaw)
        }
        if (back) {
            moveX += sin(yaw)
            moveZ -= cos(yaw)
        }
        if (right) {
            moveX -= cos(yaw)
            moveZ -= sin(yaw)
        }
        if (left) {
            moveX += cos(yaw)
            moveZ += sin(yaw)
        }

        val len = sqrt(moveX * moveX + moveZ * moveZ)
        if (len < 0.001) return 75.6f

        val localX = player.x - floor(player.x) - 0.5
        val localZ = player.z - floor(player.z) - 0.5
        val movementOffset = localX * (moveX / len) + localZ * (moveZ / len)
        val pitchCorrection = (-movementOffset * 0.35).coerceIn(-0.12, 0.12)
        return (75.6 + pitchCorrection).toFloat()
    }

    private fun isSolidSupportBlock(world: ClientLevel, x: Int, y: Int, z: Int): Boolean {
        val pos = BlockPos(x, y, z)
        val state = world.getBlockState(pos)
        return !state.isAir && state.fluidState.isEmpty && state.isCollisionShapeFullBlock(world, pos)
    }

    override fun onDisabled() {
        if (telly != null && bridgeMode.value == BridgeMode.TELLY)
            return telly!!.onDisabled()
        val opts = Minecraft.getInstance().options
        opts.keyUp.setDown(InputUtil.isPhysicalKeyDown(opts.keyUp))
        opts.keyDown.setDown(InputUtil.isPhysicalKeyDown(opts.keyDown))
        opts.keyLeft.setDown(InputUtil.isPhysicalKeyDown(opts.keyLeft))
        opts.keyRight.setDown(InputUtil.isPhysicalKeyDown(opts.keyRight))
        opts.keyShift.setDown(InputUtil.isPhysicalKeyDown(opts.keyShift))

        if (ownsRotation) {
            RotationManager.clearRotation()
            RotationManager.perspective = false
            ownsRotation = false
        }
        RotationManager.allowStrafe = false
        RotationManager.allowForward = false
        RotationManager.freezeMovement = false
        RotationManager.suppressJump = false
        autoclickAccum = 0.0f
        autoclickTargetCps = 0
        isCrouching = false
        crouchWaitTicks = 0
        resetDiagonalBreezilyCorrection()
    }

    private fun blockMatchesWhitelist(blockName: String, whitelist: List<String>): Boolean {
        for (entry in whitelist) {
            if (entry.endsWith("_category")) {
                val categoryId = entry.removeSuffix("_category")
                val category = itemCategories.firstOrNull { it.id == categoryId }
                if (category != null && category.matches(blockName)) return true
            } else {
                if (blockName.contains(entry.lowercase())) return true
            }
        }
        return false
    }

    override fun hudInfo(): String = bridgeMode.value.name.lowercase().replace("_", " ")
}
