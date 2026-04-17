package me.ghluka.medved.module.modules.world

import me.ghluka.medved.module.Module
import me.ghluka.medved.mixin.client.LocalPlayerAccessor
import me.ghluka.medved.util.RotationManager
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.client.KeyMapping
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.util.Mth
import net.minecraft.world.item.BlockItem
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import org.lwjgl.glfw.GLFW
import kotlin.math.atan2
import kotlin.math.floor

object Scaffold : Module("Scaffold", "Automatically places blocks under you while walking", Category.WORLD) {

    enum class BridgeMode {
        NINJA
    }

    private const val SCAFFOLD_PITCH_NINJA = 80f

    private val bridgeMode = enum("mode", BridgeMode.NINJA)
    private val crouchDelay = intRange("crouch delay", 40 to 90, 0, 500).also {
        it.visibleWhen = { bridgeMode.value == BridgeMode.NINJA }
    }

    private val autoclickCps = intRange("autoclick cps", 8 to 12, 1, 20)
    val disableOnDeath = boolean("disable on death", true)
    val disableOnWorldChange = boolean("disable on world change", true)

    private var isCrouching = false
    private var crouchWaitTicks = 0

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

    private fun isPhysicalKeyDown(mapping: KeyMapping): Boolean {
        if (mapping.isUnbound) return false
        val window = Minecraft.getInstance().window.handle()
        val key = InputConstants.getKey(mapping.saveString())
        if (key.type == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.value) == GLFW.GLFW_PRESS
        }
        if (mapping === Minecraft.getInstance().options.keyShift) {
            return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                   GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
        }
        return GLFW.glfwGetKey(window, key.value) == GLFW.GLFW_PRESS
    }

    init {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (!isEnabled()) return@register
            val player = client.player ?: return@register
            if (!hasBlocks(player)) { 
                RotationManager.clearRotation()
                RotationManager.perspective = false
                return@register 
            }

            player.isSprinting = false
            player.setSprinting(false)
            (player as LocalPlayerAccessor).setSprintTriggerTime(0)
            client.options.keySprint.setDown(false)

            RotationManager.movementMode = RotationManager.MovementMode.CLIENT
            RotationManager.rotationMode = RotationManager.RotationMode.CLIENT
            RotationManager.perspective = true

            val W = isPhysicalKeyDown(client.options.keyUp)
            val S = isPhysicalKeyDown(client.options.keyDown)
            val A = isPhysicalKeyDown(client.options.keyLeft)
            val D = isPhysicalKeyDown(client.options.keyRight)

            val movingHoriz = W || S || A || D
            var targetCard = RotationManager.getClientYaw()

            if (movingHoriz) {
                val camRad = Math.toRadians(targetCard.toDouble())
                var mx = 0.0; var mz = 0.0
                if (W) { mx -= kotlin.math.sin(camRad); mz += kotlin.math.cos(camRad) }
                if (S) { mx += kotlin.math.sin(camRad); mz -= kotlin.math.cos(camRad) }
                if (D) { mx -= kotlin.math.cos(camRad); mz -= kotlin.math.sin(camRad) }
                if (A) { mx += kotlin.math.cos(camRad); mz += kotlin.math.sin(camRad) }
                val rawMoveYaw = Math.toDegrees(atan2(-mx, mz)).toFloat()
                targetCard = Math.round(rawMoveYaw / 45.0f) * 45.0f
                
                client.options.keyUp.setDown(false)
                client.options.keyDown.setDown(true)
                client.options.keyLeft.setDown(false)
                client.options.keyRight.setDown(false)
            } else {
                targetCard = Math.round(RotationManager.getClientYaw() / 45.0f) * 45.0f
                client.options.keyUp.setDown(false)
                client.options.keyDown.setDown(false)
                client.options.keyLeft.setDown(false)
                client.options.keyRight.setDown(false)
            }

            val aimYaw = Mth.wrapDegrees(targetCard + 180f)
            val isJumping = isPhysicalKeyDown(client.options.keyJump)
            val isDiagonal = (targetCard % 90.0f) != 0.0f
            
            val aimPitch = if (!movingHoriz && isJumping) {
                90.0f
            } else if (movingHoriz && isJumping) {
                if (isDiagonal) 79.0f else 81.0f
            } else {
                if (isDiagonal) 78.0f else SCAFFOLD_PITCH_NINJA
            }

            RotationManager.setTargetRotation(aimYaw, aimPitch)
            RotationManager.quickTick(60f)
            RotationManager.physicsYawOverride = RotationManager.getCurrentYaw()
            RotationManager.skipPositionSnap = true

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

            val stack = player.mainHandItem
            if (stack.isEmpty || stack.item !is BlockItem) {
                val slot = findBlockSlot(player)
                if (slot != -1) {
                    player.inventory.setSelectedSlot(slot)
                }
            }

            if (player.onGround() && bridgeMode.value == BridgeMode.NINJA) {
                val nearEdge = isNearEdge(player, client.level!!)
                
                if (nearEdge && !isCrouching) {
                    isCrouching = true
                    val (lo, hi) = crouchDelay.value
                    val delayMs = if (hi > lo) (lo..hi).random() else lo
                    crouchWaitTicks = (delayMs / 50).coerceAtLeast(1)
                }
                
                if (isCrouching) {
                    client.options.keyShift.setDown(true)
                    player.setShiftKeyDown(true)
                    if (!nearEdge) {
                        crouchWaitTicks--
                    }
                    if (crouchWaitTicks <= 0 && !nearEdge) {
                        isCrouching = false
                    }
                } else {
                    val physicalShift = isPhysicalKeyDown(client.options.keyShift)
                    client.options.keyShift.setDown(physicalShift)
                    player.setShiftKeyDown(physicalShift)
                }
            } else {
                isCrouching = false
                crouchWaitTicks = 0
                client.options.keyShift.setDown(true)
                player.setShiftKeyDown(true)
            }
        }
    }

    private fun isNearEdge(player: LocalPlayer, world: net.minecraft.client.multiplayer.ClientLevel): Boolean {
        val px = player.x
        val by = Math.round(player.y - 1.0).toInt()
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
            if (state.isAir || !state.fluidState.isEmpty || !state.isCollisionShapeFullBlock(world, BlockPos(bx, by, bz))) {
                return true
            }
        }
        return false
    }

    override fun onDisabled() {
        val opts = Minecraft.getInstance().options
        opts.keyUp.setDown(isPhysicalKeyDown(opts.keyUp))
        opts.keyDown.setDown(isPhysicalKeyDown(opts.keyDown))
        opts.keyLeft.setDown(isPhysicalKeyDown(opts.keyLeft))
        opts.keyRight.setDown(isPhysicalKeyDown(opts.keyRight))
        opts.keyShift.setDown(isPhysicalKeyDown(opts.keyShift))

        RotationManager.clearRotation()
        RotationManager.perspective = false
        RotationManager.allowStrafe = false
        RotationManager.allowForward = false
        RotationManager.freezeMovement = false
        RotationManager.suppressJump = false
        autoclickAccum = 0.0f
        autoclickTargetCps = 0
    }
}
