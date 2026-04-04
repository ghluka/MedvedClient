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
    /**
     * When we move while rotating it flags on predictive anticheats,
     * this just pauses all movement while we rotate the yaw.
     */
    private val pauseOnRotate = boolean("pause on rotate", true)
    private val autoclickCps = intRange("autoclick cps", 8 to 12, 1, 20)
    val disableOnDeath = boolean("disable on death", true)
    val disableOnWorldChange = boolean("disable on world change", true)

    private var crouchTicksElapsed = 0
    private var crouchTicksNeeded = 0
    private var pendingPlace: (() -> Unit)? = null
    // Ticks remaining before the next click attempt. Set to a CPS-derived value after each click.
    private var clickCooldownTicks = 0

    // Pending placement target stored as block data so hit vector is recomputed at fire time
    private var pendingNeighbor: BlockPos? = null
    private var pendingFace: Direction? = null
    // Movement-aware yaw computed when the pending placement was created.
    // Kept separate from the face yaw so A/D uses camYaw, not the block face direction.
    private var pendingAimYaw: Float? = null

    // Yaw locked when the player first goes airborne while holding a movement key.
    // Cleared on landing or on disable.
    private var lockedScaffoldYaw: Float? = null

    // True when the player is holding WASD+space on the ground (read from raw key
    // state BEFORE any suppression). Used in onTick for shift logic since the
    // actual jump key may have been suppressed by that point.
    private var jumpBridgeQueued = false

    // Accumulator for the background auto-clicker. Each tick adds CPS/20;
    // when it reaches >= 1.0 a right-click fires and 1.0 is subtracted.
    private var autoclickAccum = 0.0f
    // Per-interval target CPS, re-rolled each time a click fires for randomness.
    private var autoclickTargetCps = 0

    // true on the tick we fired godbridge edge-jump, released next tick.
    //private var godbridgeJumpTick = false
    // true after we fire a godbridge jump, cleared once the player goes airborne.
    // Prevents re-jumping while still on the ground from the same edge.
    //private var godbridgeJumped = false

    /** Find a hotbar slot (0-8) containing a BlockItem, or -1 if none. */
    private fun findBlockSlot(player: LocalPlayer): Int {
        for (i in 0..8) {
            val s = player.inventory.getItem(i)
            if (!s.isEmpty && s.item is BlockItem) return i
        }
        return -1
    }

    /** True if the player has blocks available in their hotbar. */
    private fun hasBlocks(player: LocalPlayer): Boolean = findBlockSlot(player) != -1

    init {
        // START_CLIENT_TICK runs BEFORE LocalPlayer.tick() (which calls sendPosition).
        // This means anything we set here fires in the SAME tick's sendPosition = 0ms Post delay.
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (!isEnabled()) return@register
            val player = client.player ?: return@register
            if (!hasBlocks(player)) return@register

            // Kill sprint so aiStep() never sees the key held.
            player.isSprinting = false
            player.setSprinting(false)
            (player as LocalPlayerAccessor).setSprintTriggerTime(0)
            client.options.keySprint.setDown(false)

            // Release godbridge auto-jump after one tick.
            //if (godbridgeJumpTick) {
            //    client.options.keyJump.setDown(false)
            //    godbridgeJumpTick = false
            //}

            val strafing = client.options.keyLeft.isDown || client.options.keyRight.isDown
            val movingForwardBack = client.options.keyUp.isDown || client.options.keyDown.isDown
            val movingHorizontally = strafing || movingForwardBack
            val diagonal = strafing && movingForwardBack

            RotationManager.allowStrafe  = strafing
            RotationManager.allowForward = movingForwardBack

            // jump timing for WASD+space bridging: suppress the jump
            // key until the player reaches the block edge
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
                //if (bridgeMode.value == BridgeMode.GODBRIDGE && player.onGround() && movingHorizontally) {
                //    if (!godbridgeJumped) {
                //        val world = client.level
                //        if (world != null) {
                //            val moveDir = getMovementDirection(client)
                //            if (moveDir != null && isNearForwardEdge(player, world, moveDir)) {
                //                client.options.keyJump.setDown(true)
                //                godbridgeJumpTick = true
                //                godbridgeJumped = true
                //            }
                //        }
                //    }
                //}
                //if (bridgeMode.value == BridgeMode.GODBRIDGE && !player.onGround()) {
                //    godbridgeJumped = false
                //}
            }

            val shouldFreeze = pauseOnRotate.value && player.onGround() && RotationManager.isActive() && !RotationManager.hasYawReachedTarget(0.1f)
            RotationManager.freezeMovement = shouldFreeze
            if (shouldFreeze) {
                client.options.keyUp.setDown(false)
                client.options.keyDown.setDown(false)
                client.options.keyLeft.setDown(false)
                client.options.keyRight.setDown(false)
            }

            // Decrement click cooldown each tick regardless of ground state.
            if (clickCooldownTicks > 0) clickCooldownTicks--

            // Autoclicker
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

            // Unlock yaw
            if (player.onGround()) lockedScaffoldYaw = null

            //if (bridgeMode.value == BridgeMode.GODBRIDGE && player.onGround() && movingHorizontally && clickCooldownTicks == 0) {
            //    val world = client.level
            //    if (world != null) {
            //        val feetY = floor(player.y).toInt()
            //        val targetPos = BlockPos(floor(player.x).toInt(), feetY - 1, floor(player.z).toInt())
            //        val targetState = world.getBlockState(targetPos)
            //        // Compute movement direction from raw keys
            //        val camYawGod = RotationManager.getClientYaw()
            //        val camRadGod = Math.toRadians(camYawGod.toDouble())
            //        var gdx = 0.0; var gdz = 0.0
            //        if (client.options.keyUp.isDown)    { gdx -= kotlin.math.sin(camRadGod); gdz += kotlin.math.cos(camRadGod) }
            //        if (client.options.keyDown.isDown)  { gdx += kotlin.math.sin(camRadGod); gdz -= kotlin.math.cos(camRadGod) }
            //        if (client.options.keyRight.isDown) { gdx -= kotlin.math.cos(camRadGod); gdz -= kotlin.math.sin(camRadGod) }
            //        if (client.options.keyLeft.isDown)  { gdx += kotlin.math.cos(camRadGod); gdz += kotlin.math.sin(camRadGod) }
            //        val rawBehindGod = Mth.wrapDegrees(Math.toDegrees(atan2(-gdx, gdz)).toFloat() + 180f)
            //        val godYaw = Math.round(rawBehindGod / 90.0f) * 90.0f
            //        if (targetState.isAir) {
            //            val support = findSupport(world, targetPos)
            //            if (support != null) {
            //                RotationManager.setTargetRotation(godYaw, SCAFFOLD_PITCH_GOD)
            //                RotationManager.snapToTarget()
            //                RotationManager.updateHitResult()
            //                KeyMapping.click(InputConstants.getKey(client.options.keyUse.saveString()))
            //                KeyMapping.click(InputConstants.getKey(client.options.keyUse.saveString()))
            //                clickCooldownTicks = 0
            //            }
            //        } else if (!targetState.fluidState.isEmpty) {
            //            // fluid
            //        } else {
            //            val dir = if (kotlin.math.abs(gdx) > kotlin.math.abs(gdz)) {
            //                if (gdx > 0) Direction.EAST else Direction.WEST
            //            } else {
            //                if (gdz > 0) Direction.SOUTH else Direction.NORTH
            //            }
            //            val nextPos = targetPos.relative(dir)
            //            val nextState = world.getBlockState(nextPos)
            //            if (nextState.isAir || !nextState.fluidState.isEmpty) {
            //                val support = findSupport(world, nextPos)
            //                if (support != null) {
            //                    RotationManager.setTargetRotation(godYaw, SCAFFOLD_PITCH_GOD)
            //                }
            //            }
            //        }
            //    }
            //}

            if (!player.onGround() && clickCooldownTicks == 0) {
                val world = client.level ?: return@register
                val feetY = floor(player.y).toInt()
                val targetPos = BlockPos(floor(player.x).toInt(), feetY - 1, floor(player.z).toInt())
                val targetState = world.getBlockState(targetPos)
                if (targetState.isAir) {
                    // skip up so we dont face down
                    val support = findSupport(world, targetPos, preferSide = movingHorizontally)
                    if (support != null) {
                        val (neighbor, face) = support
                        val (tYaw, tPitch) = stableAimFor(player, neighbor, face)

                        // clientYaw is the real camera yaw (independent of server override)
                        val camYaw = RotationManager.getClientYaw()
                        val camRad  = Math.toRadians(camYaw.toDouble())

                        val aimYaw = when {
                            diagonal -> {
                                var fwdX = -kotlin.math.sin(camRad); var fwdZ = kotlin.math.cos(camRad)
                                var sideX = -kotlin.math.cos(camRad); var sideZ = -kotlin.math.sin(camRad)
                                var mdx = 0.0; var mdz = 0.0
                                if (client.options.keyUp.isDown)    { mdx += fwdX;  mdz += fwdZ  }
                                if (client.options.keyDown.isDown)  { mdx -= fwdX;  mdz -= fwdZ  }
                                if (client.options.keyRight.isDown) { mdx += sideX; mdz += sideZ }
                                if (client.options.keyLeft.isDown)  { mdx -= sideX; mdz -= sideZ  }
                                val behindYaw = Mth.wrapDegrees(
                                    Math.toDegrees(atan2(-mdx, mdz)).toFloat() + 180f)
                                if (lockedScaffoldYaw == null) lockedScaffoldYaw = behindYaw
                                lockedScaffoldYaw!!
                            }
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

                        val airPitch = SCAFFOLD_PITCH_JUMP//if (bridgeMode.value == BridgeMode.GODBRIDGE) SCAFFOLD_PITCH_GOD else SCAFFOLD_PITCH_JUMP
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
                        // hitResult is always correct (uses targetYaw/Pitch), so click immediately.
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

        // If no blocks in hotbar, do nothing
        if (!hasBlocks(player)) {
            pendingPlace = null
            pendingNeighbor = null
            pendingFace = null
            clickCooldownTicks = 0
            RotationManager.pendingFireAction = null
            return
        }

        // Swap to a block slot if not already holding one.
        val stack = player.mainHandItem
        if (stack.isEmpty || stack.item !is BlockItem) {
            val slot = findBlockSlot(player)
            if (slot == -1) return
            player.inventory.setSelectedSlot(slot)
        }

        // Prevent sprinting
        player.isSprinting = false
        player.setSprinting(false)
        (player as LocalPlayerAccessor).setSprintTriggerTime(0)
        client.options.keySprint.setDown(false)

        // Don't force shift while airborne
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

        // Post-click cooldown
        if (clickCooldownTicks > 0) {
            RotationManager.tick()
            return
        }

        // Pause on rotate
        if (pauseOnRotate.value && player.onGround() && RotationManager.isActive() && !RotationManager.hasYawReachedTarget(0.1f)) {
            RotationManager.tick()
            return
        }

        // Waiting for shift delay.
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
                    // Direction changed, abort.
                    pendingPlace = null; pendingNeighbor = null; pendingFace = null; pendingAimYaw = null
                }
            }
        }

        if (pendingPlace != null) {
            val nb = pendingNeighbor
            val fc = pendingFace

            // Jump pressed while pending, don't wait for shift.
            if (opts.keyJump.isDown && nb != null && fc != null) {
                KeyMapping.click(InputConstants.getKey(client.options.keyUse.saveString()))
                pendingPlace = null; pendingNeighbor = null; pendingFace = null; pendingAimYaw = null
                clickCooldownTicks = 0
                RotationManager.tick()
                return
            }

            // Grounded pending: hold shift during shift delay
            client.options.keyShift.setDown(true)
            crouchTicksElapsed++
            val minDelayMet = crouchTicksElapsed >= crouchTicksNeeded

            // Do NOT call setTargetRotation here, it would overwrite whatever cardinal
            // target was set (either by this pending's creation tick or a subsequent
            // direction change), releasing the pauseOnRotate freeze prematurely.

            // hitResult is always correct (uses targetYaw/Pitch), so fire as soon as
            // the minimum shift delay has elapsed.
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

        // Block position directly below the player's feet
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

        // Don't place on liquid
        if (hasFluid) {
            RotationManager.tick()
            return
        }

        if (!player.onGround()) {
            RotationManager.tick()
            return
        }

        val (neighbor, face) = findSupport(world, targetPos) ?: run {
            RotationManager.tick()
            return
        }

        // movement-aware aim yaw
        val opts2 = client.options
        val s2 = opts2.keyLeft.isDown || opts2.keyRight.isDown
        val f2 = opts2.keyUp.isDown   || opts2.keyDown.isDown
        val (tYaw2, tPitch2) = stableAimFor(player, neighbor, face)
        val camYaw2 = RotationManager.getClientYaw()
        val camRad2 = Math.toRadians(camYaw2.toDouble())
        val groundAimYaw = when {
            s2 && f2 -> {
                // diagonals
                var fX = -kotlin.math.sin(camRad2); var fZ = kotlin.math.cos(camRad2)
                var rX = -kotlin.math.cos(camRad2); var rZ = -kotlin.math.sin(camRad2)
                var mx = 0.0; var mz = 0.0
                if (opts2.keyUp.isDown)    { mx += fX; mz += fZ }
                if (opts2.keyDown.isDown)  { mx -= fX; mz -= fZ }
                if (opts2.keyRight.isDown) { mx += rX; mz += rZ }
                if (opts2.keyLeft.isDown)  { mx -= rX; mz -= rZ }
                Mth.wrapDegrees(Math.toDegrees(atan2(-mx, mz)).toFloat() + 180f)
            }
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
        val groundPitch = SCAFFOLD_PITCH_NINJA//if (bridgeMode.value == BridgeMode.GODBRIDGE) SCAFFOLD_PITCH_GOD else SCAFFOLD_PITCH_NINJA
        RotationManager.setTargetRotation(groundAimYaw, groundPitch)

        //if (bridgeMode.value == BridgeMode.GODBRIDGE) {
        //    // godbridge
        //    KeyMapping.click(InputConstants.getKey(client.options.keyUse.saveString()))
        //    clickCooldownTicks = 0
        //    RotationManager.tick()
        //    return
        //}

        // WASD+jump
        if (opts2.keyJump.isDown) {
            KeyMapping.click(InputConstants.getKey(client.options.keyUse.saveString()))
            clickCooldownTicks = 0
            RotationManager.tick()
            return
        }

        // edge safety
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

    /**
     * While standing on solid ground, predict where the next air gap will be
     * and pre-aim the server-side rotation there. This way rotation is already
     * converged when we step/jump off the edge.
     */
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

        // Tower
        if (jumping && !movingForward && !movingBack && !movingLeft && !movingRight) {
            val (yaw, pitch) = stableAimFor(player, solidBelow, Direction.UP)
            RotationManager.setTargetRotation(yaw, pitch)
            return
        }

        // compute movement direction based on client rot
        val camYaw = RotationManager.getClientYaw()
        val yawRad = Math.toRadians(camYaw.toDouble())
        var dx = 0.0
        var dz = 0.0
        if (movingForward) { dx -= kotlin.math.sin(yawRad); dz += kotlin.math.cos(yawRad) }
        if (movingBack) { dx += kotlin.math.sin(yawRad); dz -= kotlin.math.cos(yawRad) }
        if (movingLeft) { dx += kotlin.math.cos(yawRad); dz += kotlin.math.sin(yawRad) }
        if (movingRight) { dx -= kotlin.math.cos(yawRad); dz -= kotlin.math.sin(yawRad) }

        // compute server yaw
        val rawBehind = Mth.wrapDegrees(
            Math.toDegrees(atan2(-dx, dz)).toFloat() + 180f)
        val behindYaw = Math.round(rawBehind / 90.0f) * 90.0f

        // get axis for gap
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
                val preAimPitch = //if (bridgeMode.value == BridgeMode.GODBRIDGE) SCAFFOLD_PITCH_GOD else
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

        val fallbackPitch = //if (bridgeMode.value == BridgeMode.GODBRIDGE) SCAFFOLD_PITCH_GOD else 
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
        //godbridgeJumpTick = false
        //godbridgeJumped = false
        pendingPlace = null
        pendingNeighbor = null
        pendingFace = null
        pendingAimYaw = null
        clickCooldownTicks = 0
        lockedScaffoldYaw = null
    }

    /**
     * Stable placement aim
     */
    private fun stableAimFor(player: LocalPlayer, neighbor: BlockPos, face: Direction): Pair<Float, Float> {
        val eyeY = player.y + player.eyeHeight
        return when (face) {
            Direction.UP -> {
                // aim at the XZ center of the top face so the ray hits regardless of
                // how far the player is offset from the center of the block.
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

    /**
     * Hit vector on [face] of [neighbor], offset toward the player so it
     * resembles crouching at the edge and looking at the near corner of the block.
     */
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

    /** Compute the yaw and pitch from the player's eye position to a target point. */
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

    /**
     * True if the player is within 0.3 blocks of the edge of the block they're standing on,
     * AND the adjacent block at that edge (at feet-1 level) is air. This triggers crouching
     * to prevent falling off during direction changes.
     */
    private fun isNearEdge(player: LocalPlayer, world: net.minecraft.client.multiplayer.ClientLevel): Boolean {
        val px = player.x
        val pz = player.z
        val bx = floor(px).toInt()
        val bz = floor(pz).toInt()
        val belowY = floor(player.y).toInt() - 1

        // check distance to each block edge
        val margin = 0.3
        val fracX = px - bx  // 0.0 to 1.0 within the block
        val fracZ = pz - bz

        // west edge (fracX near 0)
        if (fracX < margin) {
            val adj = world.getBlockState(BlockPos(bx - 1, belowY, bz))
            if (adj.isAir || !adj.fluidState.isEmpty) return true
        }
        // east edge (fracX near 1)
        if (fracX > 1.0 - margin) {
            val adj = world.getBlockState(BlockPos(bx + 1, belowY, bz))
            if (adj.isAir || !adj.fluidState.isEmpty) return true
        }
        // north edge (fracZ near 0)
        if (fracZ < margin) {
            val adj = world.getBlockState(BlockPos(bx, belowY, bz - 1))
            if (adj.isAir || !adj.fluidState.isEmpty) return true
        }
        // south edge (fracZ near 1)
        if (fracZ > 1.0 - margin) {
            val adj = world.getBlockState(BlockPos(bx, belowY, bz + 1))
            if (adj.isAir || !adj.fluidState.isEmpty) return true
        }
        return false
    }

    /**
     * Compute the cardinal Direction the player is moving based on held WASD keys.
     * Returns null if no movement keys are held.
     */
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

    /**
     * True if the player is near the edge of their current block in [moveDir]
     * AND the adjacent block in that direction (at feet-1) is air.
     */
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
