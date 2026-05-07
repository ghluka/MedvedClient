package me.ghluka.medved.module.modules.movement

import com.mojang.blaze3d.platform.InputConstants
import me.ghluka.medved.module.Module
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.KeyMapping
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import org.lwjgl.glfw.GLFW
import kotlin.math.floor

object Parkour : Module("Parkour", "Automatically jumps off ledges", Category.MOVEMENT) {

    private var debounce = false
    private var savedJumpState = false

    init {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (!isEnabled()) return@register
            val player = client.player as? LocalPlayer ?: return@register
            val world = client.level ?: return@register

            if (debounce) {
                val opts = client.options
                opts.keyJump.setDown(savedJumpState)
                debounce = false
            }

            if (!player.onGround() || player.isCrouching) return@register

            val px = player.x
            val pz = player.z
            val by = floor(player.y - 1.0).toInt()

            val offsets = arrayOf(
                doubleArrayOf(0.0, 0.0),
                doubleArrayOf(-0.2, 0.0),
                doubleArrayOf(0.2, 0.0),
                doubleArrayOf(0.0, -0.2),
                doubleArrayOf(0.0, 0.2)
            )

            var hasSupport = false
            for (off in offsets) {
                val bx = floor(px + off[0]).toInt()
                val bz = floor(pz + off[1]).toInt()
                val pos = BlockPos(bx, by, bz)
                val state = world.getBlockState(pos)
                if (!state.isAir && state.fluidState.isEmpty && state.isCollisionShapeFullBlock(world, pos)) {
                    hasSupport = true
                    break
                }
            }

            if (!hasSupport) {
                val opts = client.options
                savedJumpState = isPhysicalKeyDown(opts.keyJump)
                opts.keyJump.setDown(true)
                debounce = true
            }
        }
    }

    private fun isPhysicalKeyDown(mapping: KeyMapping): Boolean {
        if (mapping.isUnbound) return false
        val window = Minecraft.getInstance().window.handle()
        val key = InputConstants.getKey(mapping.saveString())
        if (key.type == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.value) == GLFW.GLFW_PRESS
        }
        return GLFW.glfwGetKey(window, key.value) == GLFW.GLFW_PRESS
    }
}
