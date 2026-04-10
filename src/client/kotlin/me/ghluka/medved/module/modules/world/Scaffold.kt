package me.ghluka.medved.module.modules.world

import me.ghluka.medved.module.Module
import me.ghluka.medved.mixin.client.LocalPlayerAccessor
import me.ghluka.medved.util.RotationManager
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.client.KeyMapping
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.util.Mth
import net.minecraft.world.item.BlockItem
import net.minecraft.world.phys.Vec3
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt

object Scaffold : Module("Scaffold", "Automatically places blocks under you while walking", Category.WORLD) {

    enum class BridgeMode {
        NINJA,
        //GODBRIDGE
    }

    /** Ninja bridge pitch */
    private const val SCAFFOLD_PITCH_NINJA = 80f
    /** Upstack pitch */
    private const val SCAFFOLD_PITCH_JUMP = 81f
    /** Godbridge pitch */
    private const val SCAFFOLD_PITCH_GOD = 79f

    private val bridgeMode = enum("mode", BridgeMode.NINJA)
    private val crouchDelay = intRange("crouch delay", 40 to 90, 0, 500).also {
        it.visibleWhen = { bridgeMode.value == BridgeMode.NINJA }
    }

    private val pauseOnRotate = boolean("pause on rotate", true)
    private val autoclickCps = intRange("autoclick cps", 8 to 12, 1, 20)
    val disableOnDeath = boolean("disable on death", true)
    val disableOnWorldChange = boolean("disable on world change", true)

    private var crouchTicksElapsed = 0
    private var crouchTicksNeeded = 0
    private var pendingPlace: (() -> Unit)? = null
    private var clickCooldownTicks = 0

    private var pendingNeighbor: BlockPos? = null
    private var pendingFace: Direction? = null
    private var pendingAimYaw: Float? = null

    private var lockedScaffoldYaw: Float? = null

    private var jumpBridgeQueued = false

    private var autoclickAccum = 0.0f
    private var autoclickTargetCps = 0
    
    private fun findBlockSlot(player: LocalPlayer): Int {
        val world  = Minecraft.getInstance().level ?: return -1
        for (i in 0..8) {
            val stack = player.inventory.getItem(i)
            if (stack.isEmpty || stack.item !is BlockItem) continue
            val block = (stack.item as BlockItem).block
            if (!block.defaultBlockState().isCollisionShapeFullBlock(world, BlockPos.ZERO)) continue
            return i
        }
        return -1
    }

    private fun hasBlocks(player: LocalPlayer): Boolean = findBlockSlot(player) != -1

    init {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (!isEnabled()) return@register
            val player = client.player ?: return@register
            if (!hasBlocks(player)) { RotationManager.clearRotation(); return@register }

            player.isSprinting = false
            player.setSprinting(false)
            (player as LocalPlayerAccessor).setSprintTriggerTime(0)
            client.options.keySprint.setDown(false)

            val strafing = client.options.keyLeft.isDown || client.options.keyRight.isDown
            val movingForwardBack = client.options.keyUp.isDown || client.options.keyDown.isDown
            val movingHorizontally = strafing || movingForwardBack
            val diagonal = strafing && movingForwardBack

            RotationManager.allowStrafe  = strafing
            RotationManager.allowForward = movingForwardBack
            RotationManager.movementMode = RotationManager.MovementMode.CLIENT
            RotationManager.rotationMode = RotationManager.RotationMode.SERVER

            if (bridgeMode.value == BridgeMode.NINJA) {
                val wantsJumpBridge = movingHorizontally && client.options.keyJump.isDown && player.onGround()
                jumpBridgeQueued = wantsJumpBridge
                if (wantsJumpBridge) {
                    val world = client.level
                    if (world != null && !isNearEdge(player, world)) {
                        client.options.keyJump.setDown(false)
                        RotationManager.suppressJump = true
                    } else {
                        RotationManager.suppressJump = false
                    }
                } else {
                    RotationManager.suppressJump = false
                }
            } else {
                jumpBridgeQueued = false
                RotationManager.suppressJump = false
            }

            val shouldFreeze = pauseOnRotate.value && player.onGround() && RotationManager.isActive() && !RotationManager.hasYawReachedTarget(0.1f)
            RotationManager.freezeMovement = shouldFreeze
            if (shouldFreeze) {
                client.options.keyUp.setDown(false)
                client.options.keyDown.setDown(false)
                client.options.keyLeft.setDown(false)
                client.options.keyRight.setDown(false)
            }

            if (clickCooldownTicks > 0) clickCooldownTicks--

            if (RotationManager.isActive()) {
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
            } else {
                autoclickAccum = 0.0f
                autoclickTargetCps = 0
            }

            if (player.onGround()) lockedScaffoldYaw = null

            if (!player.onGround() && clickCooldownTicks == 0) {
                val world = client.level ?: return@register
                val feetY = floor(player.y).toInt()
                val targetPos = BlockPos(floor(player.x).toInt(), feetY - 1, floor(player.z).toInt())
                val targetState = world.getBlockState(targetPos)
                if (targetState.isAir) {
                    val support = findSupport(world, targetPos, preferSide = movingHorizontally)
                    if (support != null) {
                        val (neighbor, face) = support
                        val (tYaw, tPitch) = stableAimFor(player, neighbor, face)

                        val camYaw = RotationManager.getClientYaw()
                        val camRad  = Math.toRadians(camYaw.toDouble())

                        val aimYaw = when {
                            strafing && !movingForwardBack -> {
                                // A/D
                                var sideX = -kotlin.math.cos(camRad); var sideZ = -kotlin.math.sin(camRad)
                                var mdx = 0.0; var mdz = 0.0
                                if (client.options.keyRight.isDown) { mdx += sideX; mdz += sideZ }
                                if (client.options.keyLeft.isDown)  { mdx -= sideX; mdz -= sideZ }
                                val rawBehind = Mth.wrapDegrees(
                                    Math.toDegrees(atan2(-mdx, mdz)).toFloat() + 180f)
                                val snapped = Math.round(rawBehind / 90.0f) * 90.0f
                                if (lockedScaffoldYaw == null) lockedScaffoldYaw = snapped
                                lockedScaffoldYaw!!
                            }
                            movingForwardBack -> {
                                // W/S
                                var fX = 0.0; var fZ = 0.0
                                if (client.options.keyUp.isDown)   { fX -= kotlin.math.sin(camRad); fZ += kotlin.math.cos(camRad) }
                                if (client.options.keyDown.isDown) { fX += kotlin.math.sin(camRad); fZ -= kotlin.math.cos(camRad) }
                                val rawBehind = Mth.wrapDegrees(
                                    Math.toDegrees(atan2(-fX, fZ)).toFloat() + 180f)
                                val snapped = Math.round(rawBehind / 90.0f) * 90.0f
                                if (lockedScaffoldYaw == null) lockedScaffoldYaw = snapped
                                lockedScaffoldYaw!!
                            }
                            else -> tYaw  // towering
                        }

                        val airPitch = SCAFFOLD_PITCH_JUMP
                        val usePitch = if (movingHorizontally) airPitch else {
                            if (face == Direction.UP) {
                                val eyeY = player.y + player.eyeHeight
                                val faceY = neighbor.y + 1.0
                                val dy = faceY - eyeY
                                val aimRad = Math.toRadians(aimYaw.toDouble())
                                val hDist = kotlin.math.abs(
                                    (neighbor.x + 0.5 - player.x) * -kotlin.math.sin(aimRad) +
                                    (neighbor.z + 0.5 - player.z) *  kotlin.math.cos(aimRad)
                                ).coerceAtLeast(0.3)
                                Math.toDegrees(atan2(-dy, hDist)).toFloat().coerceIn(-90f, 90f)
                            } else tPitch
                        }
                        RotationManager.setTargetRotation(aimYaw, usePitch)
                        KeyMapping.click(InputConstants.getKey(client.options.keyUse.saveString()))
                        RotationManager.tick()
                        KeyMapping.click(InputConstants.getKey(client.options.keyUse.saveString()))
                        clickCooldownTicks = 0
                    }
                }
            }
        }
    }

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        val world = client.level ?: return

        if (!hasBlocks(player)) {
            pendingPlace = null
            pendingNeighbor = null
            pendingFace = null
            clickCooldownTicks = 0
            RotationManager.pendingFireAction = null
            RotationManager.clearRotation()
            return
        }

        val stack = player.mainHandItem
        if (stack.isEmpty || stack.item !is BlockItem) {
            val slot = findBlockSlot(player)
            if (slot == -1) return
            player.inventory.setSelectedSlot(slot)
        }

        player.isSprinting = false
        player.setSprinting(false)
        (player as LocalPlayerAccessor).setSprintTriggerTime(0)
        client.options.keySprint.setDown(false)

        val opts = client.options
        val movingHorizontally = opts.keyUp.isDown || opts.keyDown.isDown ||
                                  opts.keyLeft.isDown || opts.keyRight.isDown
        if (player.onGround() && bridgeMode.value == BridgeMode.NINJA) {
            val jumping = opts.keyJump.isDown || jumpBridgeQueued
            val nearEdge = isNearEdge(player, world)
            val stillRotating = RotationManager.isActive() && !RotationManager.hasReachedTarget(2f)
            val jumpPending = jumping && nearEdge && pendingPlace != null
            client.options.keyShift.setDown(nearEdge && ((!jumping && stillRotating) || jumpPending || jumpBridgeQueued))
        }

        if (clickCooldownTicks > 0) {
            RotationManager.tick()
            return
        }

        if (pauseOnRotate.value && player.onGround() && RotationManager.isActive() && !RotationManager.hasYawReachedTarget(0.1f)) {
            RotationManager.tick()
            return
        }

        if (pendingPlace != null) {
            val curF = opts.keyUp.isDown || opts.keyDown.isDown
            val curS = opts.keyLeft.isDown || opts.keyRight.isDown
            if ((curF || curS) && pendingAimYaw != null) {
                val camYaw = RotationManager.getClientYaw()
                val camRad = Math.toRadians(camYaw.toDouble())
                var mx = 0.0; var mz = 0.0
                if (opts.keyUp.isDown)    { mx -= kotlin.math.sin(camRad); mz += kotlin.math.cos(camRad) }
                if (opts.keyDown.isDown)  { mx += kotlin.math.sin(camRad); mz -= kotlin.math.cos(camRad) }
                if (opts.keyRight.isDown) { mx -= kotlin.math.cos(camRad); mz -= kotlin.math.sin(camRad) }
                if (opts.keyLeft.isDown)  { mx += kotlin.math.cos(camRad); mz += kotlin.math.sin(camRad) }
                val rawBehind = Mth.wrapDegrees(Math.toDegrees(atan2(-mx, mz)).toFloat() + 180f)
                val curCardinal = Math.round(rawBehind / 90.0f) * 90.0f
                if (kotlin.math.abs(Mth.wrapDegrees(curCardinal - pendingAimYaw!!)) > 45f) {
                    pendingPlace = null; pendingNeighbor = null; pendingFace = null; pendingAimYaw = null
                }
            }
        }

        if (pendingPlace != null) {
            val nb = pendingNeighbor
            val fc = pendingFace

            if (opts.keyJump.isDown && nb != null && fc != null) {
                KeyMapping.click(InputConstants.getKey(client.options.keyUse.saveString()))
                pendingPlace = null; pendingNeighbor = null; pendingFace = null; pendingAimYaw = null
                clickCooldownTicks = 0
                RotationManager.tick()
                return
            }

            client.options.keyShift.setDown(true)
            crouchTicksElapsed++
            val minDelayMet = crouchTicksElapsed >= crouchTicksNeeded

            if (minDelayMet && nb != null && fc != null) {
                KeyMapping.click(InputConstants.getKey(client.options.keyUse.saveString()))
                pendingPlace = null
                pendingNeighbor = null
                pendingFace = null
                pendingAimYaw = null

                clickCooldownTicks = 0
            }
            RotationManager.tick()
            return
        }

        val feetY = floor(player.y).toInt()
        val targetPos = BlockPos(
            floor(player.x).toInt(),
            feetY - 1,
            floor(player.z).toInt()
        )

        val targetState = world.getBlockState(targetPos)
        val hasFluid = !targetState.fluidState.isEmpty

        if (!targetState.isAir && !hasFluid) {
            if (!RotationManager.isActive() || !RotationManager.hasReachedTarget(2f)) {
                preAimNextGap(player, world, targetPos)
            }
            RotationManager.tick()
            return
        }

        if (hasFluid) {
            RotationManager.tick()
            return
        }

        if (!player.onGround()) {
            return
        }

        val (neighbor, face) = findSupport(world, targetPos) ?: run {
            RotationManager.tick()
            return
        }

        val opts2 = client.options
        val s2 = opts2.keyLeft.isDown || opts2.keyRight.isDown
        val f2 = opts2.keyUp.isDown   || opts2.keyDown.isDown
        val (tYaw2, tPitch2) = stableAimFor(player, neighbor, face)
        val camYaw2 = RotationManager.getClientYaw()
        val camRad2 = Math.toRadians(camYaw2.toDouble())
        val groundAimYaw = when {
            s2 && !f2 -> {
                // A/D strafes
                var rX = -kotlin.math.cos(camRad2); var rZ = -kotlin.math.sin(camRad2)
                var mx = 0.0; var mz = 0.0
                if (opts2.keyRight.isDown) { mx += rX; mz += rZ }
                if (opts2.keyLeft.isDown)  { mx -= rX; mz -= rZ }
                val rawBehind = Mth.wrapDegrees(Math.toDegrees(atan2(-mx, mz)).toFloat() + 180f)
                Math.round(rawBehind / 90.0f) * 90.0f
            }
            f2 -> {
                // W/S bridging
                var fX = 0.0; var fZ = 0.0
                if (opts2.keyUp.isDown)   { fX -= kotlin.math.sin(camRad2); fZ += kotlin.math.cos(camRad2) }
                if (opts2.keyDown.isDown) { fX += kotlin.math.sin(camRad2); fZ -= kotlin.math.cos(camRad2) }
                val rawBehind = Mth.wrapDegrees(Math.toDegrees(atan2(-fX, fZ)).toFloat() + 180f)
                Math.round(rawBehind / 90.0f) * 90.0f
            }
            else -> tYaw2 // tower
        }
        val groundPitch = SCAFFOLD_PITCH_NINJA
        RotationManager.setTargetRotation(groundAimYaw, groundPitch)

        if (opts2.keyJump.isDown) {
            KeyMapping.click(InputConstants.getKey(client.options.keyUse.saveString()))
            clickCooldownTicks = 0
            RotationManager.tick()
            return
        }

        client.options.keyShift.setDown(true)
        val (lo, hi) = crouchDelay.value
        val delayMs = if (hi > lo) (lo..hi).random() else lo
        crouchTicksNeeded = (delayMs / 50).coerceAtLeast(1)
        crouchTicksElapsed = 0

        pendingAimYaw = groundAimYaw
        pendingNeighbor = neighbor
        pendingFace = face
        pendingPlace = {}

        RotationManager.tick()
    }

    private fun preAimNextGap(
        player: LocalPlayer,
        world: net.minecraft.client.multiplayer.ClientLevel,
        solidBelow: BlockPos
    ) {
        val opts = Minecraft.getInstance().options

        val movingForward = opts.keyUp.isDown || (RotationManager.freezeMovement && RotationManager.allowForward)
        val movingBack = opts.keyDown.isDown || (RotationManager.freezeMovement && RotationManager.allowForward)
        val movingLeft = opts.keyLeft.isDown || (RotationManager.freezeMovement && RotationManager.allowStrafe)
        val movingRight = opts.keyRight.isDown || (RotationManager.freezeMovement && RotationManager.allowStrafe)
        val jumping = opts.keyJump.isDown

        if (!movingForward && !movingBack && !movingLeft && !movingRight && !jumping) return

        if (jumping && !movingForward && !movingBack && !movingLeft && !movingRight) {
            val (yaw, pitch) = stableAimFor(player, solidBelow, Direction.UP)
            RotationManager.setTargetRotation(yaw, pitch)
            return
        }

        val camYaw = RotationManager.getClientYaw()
        val yawRad = Math.toRadians(camYaw.toDouble())
        var dx = 0.0
        var dz = 0.0
        if (movingForward) { dx -= kotlin.math.sin(yawRad); dz += kotlin.math.cos(yawRad) }
        if (movingBack) { dx += kotlin.math.sin(yawRad); dz -= kotlin.math.cos(yawRad) }
        if (movingLeft) { dx += kotlin.math.cos(yawRad); dz += kotlin.math.sin(yawRad) }
        if (movingRight) { dx -= kotlin.math.cos(yawRad); dz -= kotlin.math.sin(yawRad) }

        val rawBehind = Mth.wrapDegrees(
            Math.toDegrees(atan2(-dx, dz)).toFloat() + 180f)
        val behindYaw = Math.round(rawBehind / 90.0f) * 90.0f

        val dir = if (kotlin.math.abs(dx) > kotlin.math.abs(dz)) {
            if (dx > 0) Direction.EAST else Direction.WEST
        } else {
            if (dz > 0) Direction.SOUTH else Direction.NORTH
        }

        val movingHoriz = movingForward || movingBack || movingLeft || movingRight

        val nextPos = solidBelow.relative(dir)
        val nextState = world.getBlockState(nextPos)
        if (nextState.isAir || !nextState.fluidState.isEmpty) {
            val support = findSupport(world, nextPos)
            if (support != null) {
                val preAimPitch =
                    if (movingHoriz) SCAFFOLD_PITCH_JUMP
                    else stableAimFor(player, support.first, support.second).second
                if (!player.onGround() && RotationManager.isActive()) {
                    RotationManager.setTargetPitchOnly(preAimPitch)
                } else {
                    RotationManager.setTargetRotation(behindYaw, preAimPitch)
                }
                return
            }
        }

        val fallbackPitch =
            if (movingHoriz) SCAFFOLD_PITCH_JUMP
            else stableAimFor(player, solidBelow, Direction.UP).second
        if (!player.onGround() && RotationManager.isActive()) {
            RotationManager.setTargetPitchOnly(fallbackPitch)
        } else {
            RotationManager.setTargetRotation(behindYaw, fallbackPitch)
        }
    }

    override fun onDisabled() {
        val opts = Minecraft.getInstance().options
        opts.keyShift.setDown(false)
        RotationManager.clearRotation()
        RotationManager.pendingFireAction = null
        RotationManager.allowStrafe = false
        RotationManager.allowForward = false
        RotationManager.freezeMovement = false
        RotationManager.suppressJump = false
        autoclickAccum = 0.0f
        autoclickTargetCps = 0
        pendingPlace = null
        pendingNeighbor = null
        pendingFace = null
        pendingAimYaw = null
        clickCooldownTicks = 0
        lockedScaffoldYaw = null
    }

    private fun stableAimFor(player: LocalPlayer, neighbor: BlockPos, face: Direction): Pair<Float, Float> {
        val eyeY = player.y + player.eyeHeight
        return when (face) {
            Direction.UP -> {
                val faceX = neighbor.x + 0.5
                val faceZ = neighbor.z + 0.5
                val dx = faceX - player.x
                val dz = faceZ - player.z
                val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
                yaw to 80f
            }
            Direction.DOWN -> {
                val faceY = neighbor.y.toDouble()
                val dy = faceY - eyeY
                val hDist = 0.6
                val pitch = Math.toDegrees(atan2(-dy, hDist)).toFloat().coerceIn(-90f, 90f)
                val yaw = Mth.wrapDegrees(player.getYRot() + 180f)
                yaw to pitch
            }
            else -> {
                val faceX = neighbor.x + 0.5 + face.stepX * 0.5
                val faceY = neighbor.y + 0.5
                val faceZ = neighbor.z + 0.5 + face.stepZ * 0.5
                val hDist = sqrt(
                    (faceX - player.x) * (faceX - player.x) +
                    (faceZ - player.z) * (faceZ - player.z)
                ).coerceAtLeast(0.01)
                val dy = faceY - eyeY
                val pitch = Math.toDegrees(atan2(-dy, hDist)).toFloat().coerceIn(-90f, 90f)
                
                val yaw = Math.toDegrees(atan2(face.stepX.toDouble(), -face.stepZ.toDouble())).toFloat()
                yaw to pitch
            }
        }
    }

    private fun edgeHitVec(player: LocalPlayer, neighbor: BlockPos, face: Direction): Vec3 {
        val cx = neighbor.x + 0.5
        val cy = neighbor.y + 0.5
        val cz = neighbor.z + 0.5
        return when (face) {
            Direction.UP -> Vec3(
                cx + (player.x - cx).coerceIn(-0.48, 0.48),
                neighbor.y + 1.0,
                cz + (player.z - cz).coerceIn(-0.48, 0.48)
            )
            Direction.DOWN -> Vec3(
                cx + (player.x - cx).coerceIn(-0.48, 0.48),
                neighbor.y.toDouble(),
                cz + (player.z - cz).coerceIn(-0.48, 0.48)
            )
            else -> Vec3(
                cx + face.stepX * 0.5,
                cy + (player.y - cy).coerceIn(-0.48, 0.48),
                cz + face.stepZ * 0.5
            )
        }
    }

    private fun rotationToVec(player: LocalPlayer, target: Vec3): Pair<Float, Float> {
        val dx = target.x - player.x
        val dy = target.y - (player.y + player.eyeHeight.toDouble())
        val dz = target.z - player.z
        val dist = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(atan2(-dy, dist)).toFloat()
        return yaw to pitch.coerceIn(-90f, 90f)
    }

    private fun findSupport(
        world: net.minecraft.client.multiplayer.ClientLevel,
        target: BlockPos,
        preferSide: Boolean = false
    ): Pair<BlockPos, Direction>? {
        val order = if (preferSide)
            arrayOf(Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP)
        else
            arrayOf(Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP)
        for (dir in order) {
            if (preferSide && dir == Direction.UP) {
                continue
            }
            val neighbor = target.relative(dir)
            val state = world.getBlockState(neighbor)
            if (!state.isAir && state.fluidState.isEmpty) return neighbor to dir.opposite
        }
        
        if (preferSide) {
            val neighbor = target.relative(Direction.UP)
            val state = world.getBlockState(neighbor)
            if (!state.isAir && state.fluidState.isEmpty) return neighbor to Direction.DOWN
        }
        return null
    }

    private fun isNearEdge(player: LocalPlayer, world: net.minecraft.client.multiplayer.ClientLevel): Boolean {
        val px = player.x
        val pz = player.z
        val bx = floor(px).toInt()
        val bz = floor(pz).toInt()
        val belowY = floor(player.y).toInt() - 1

        val margin = 0.3
        val fracX = px - bx
        val fracZ = pz - bz

        if (fracX < margin) {
            val adj = world.getBlockState(BlockPos(bx - 1, belowY, bz))
            if (adj.isAir || !adj.fluidState.isEmpty) return true
        }
        if (fracX > 1.0 - margin) {
            val adj = world.getBlockState(BlockPos(bx + 1, belowY, bz))
            if (adj.isAir || !adj.fluidState.isEmpty) return true
        }
        if (fracZ < margin) {
            val adj = world.getBlockState(BlockPos(bx, belowY, bz - 1))
            if (adj.isAir || !adj.fluidState.isEmpty) return true
        }
        if (fracZ > 1.0 - margin) {
            val adj = world.getBlockState(BlockPos(bx, belowY, bz + 1))
            if (adj.isAir || !adj.fluidState.isEmpty) return true
        }
        return false
    }

    private fun getMovementDirection(client: Minecraft): Direction? {
        val camYaw = RotationManager.getClientYaw()
        val camRad = Math.toRadians(camYaw.toDouble())
        var dx = 0.0; var dz = 0.0
        if (client.options.keyUp.isDown)    { dx -= kotlin.math.sin(camRad); dz += kotlin.math.cos(camRad) }
        if (client.options.keyDown.isDown)  { dx += kotlin.math.sin(camRad); dz -= kotlin.math.cos(camRad) }
        if (client.options.keyLeft.isDown)  { dx += kotlin.math.cos(camRad); dz += kotlin.math.sin(camRad) }
        if (client.options.keyRight.isDown) { dx -= kotlin.math.cos(camRad); dz -= kotlin.math.sin(camRad) }
        if (dx == 0.0 && dz == 0.0) return null
        return if (kotlin.math.abs(dx) > kotlin.math.abs(dz)) {
            if (dx > 0) Direction.EAST else Direction.WEST
        } else {
            if (dz > 0) Direction.SOUTH else Direction.NORTH
        }
    }

    private fun isNearForwardEdge(
        player: LocalPlayer,
        world: net.minecraft.client.multiplayer.ClientLevel,
        moveDir: Direction
    ): Boolean {
        val px = player.x
        val pz = player.z
        val bx = floor(px).toInt()
        val bz = floor(pz).toInt()
        val belowY = floor(player.y).toInt() - 1
        val fracX = px - bx
        val fracZ = pz - bz
        val margin = 0.15

        val nearEdge = when (moveDir) {
            Direction.WEST  -> fracX < margin
            Direction.EAST  -> fracX > 1.0 - margin
            Direction.NORTH -> fracZ < margin
            Direction.SOUTH -> fracZ > 1.0 - margin
            else -> false
        }
        if (!nearEdge) return false

        val adjPos = BlockPos(
            bx + moveDir.stepX,
            belowY,
            bz + moveDir.stepZ
        )
        val adj = world.getBlockState(adjPos)
        return adj.isAir || !adj.fluidState.isEmpty
    }
}
