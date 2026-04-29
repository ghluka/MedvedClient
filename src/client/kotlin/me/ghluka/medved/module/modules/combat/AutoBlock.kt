package me.ghluka.medved.module.modules.combat

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.TridentItem

object AutoBlock : Module(
    name = "Block Hit",
    description = "Holds block with your sword to reduce incoming damage",
    category = Category.COMBAT
) {

    enum class BlockMode { STATIC, DYNAMIC }

    private val mode = enum("mode", BlockMode.STATIC)

    private val rightClickOnly = boolean("right click only", true)
    private val releaseOnSwing = boolean("release on swing", true)
    private val releaseMs = intRange("release (ms)", 50 to 80, 20, 3000).also {
        it.visibleWhen = { releaseOnSwing.value }
    }
    private val dynamicRange = float("dynamic range", 5.0f, 1.0f, 10.0f).also {
        it.visibleWhen = { mode.value == BlockMode.DYNAMIC }
    }

    private var wasBlocking    = false
    private var releaseUntilMs = 0L
    private var prevAttackDown = false

    override fun onEnabled() {
        wasBlocking    = false
        releaseUntilMs = 0L
        prevAttackDown = false
    }

    override fun onDisabled() {
        release(Minecraft.getInstance())
        releaseUntilMs = 0L
        prevAttackDown = false
    }

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        if (client.gui.screen() != null) { release(client); return }

        val stack = player.mainHandItem
        val isSword = stack.`is`(ItemTags.SWORDS) ||
                      stack.`is`(ItemTags.AXES)   ||
                      stack.item is TridentItem

        val attackDown = client.options.keyAttack.isDown
        val useDown = isUseHeld(client)

        if (!isSword || (rightClickOnly.value && !useDown)) {
            release(client)
            prevAttackDown = attackDown
            return
        }

        val now = System.currentTimeMillis()

        if (rightClickOnly.value) {
            if (releaseOnSwing.value && now >= releaseUntilMs) {
                val autoClickerFired = LeftClicker.isEnabled() && LeftClicker.clickedThisTick
                val manualClickEdge  = !LeftClicker.isEnabled() && attackDown && !prevAttackDown
                if (autoClickerFired || manualClickEdge) {
                    val (lo, hi) = releaseMs.value
                    val ms = if (hi > lo) (lo..hi).random().toLong() else lo.toLong()
                    releaseUntilMs = now + ms
                }
            }

            if (now < releaseUntilMs) {
                release(client)
                prevAttackDown = attackDown
                return
            }

            if (shouldBlock(player, client)) {
                if (!wasBlocking) {
                    val useKey = InputConstants.getKey(client.options.keyUse.saveString())
                    KeyMapping.click(useKey)
                }
                client.options.keyUse.setDown(true)
                wasBlocking = true
            } else {
                release(client)
            }
            prevAttackDown = attackDown
            return
        }

        if (releaseOnSwing.value && now >= releaseUntilMs) {
            val autoClickerFired = LeftClicker.isEnabled() && LeftClicker.clickedThisTick
            val manualClickEdge  = !LeftClicker.isEnabled() && attackDown && !prevAttackDown
            if (autoClickerFired || manualClickEdge) {
                val (lo, hi) = releaseMs.value
                val ms = if (hi > lo) (lo..hi).random().toLong() else lo.toLong()
                releaseUntilMs = now + ms
            }
        }
        prevAttackDown = attackDown

        if (now < releaseUntilMs) {
            release(client)
            return
        }

        if (shouldBlock(player, client)) {
            val useKey = InputConstants.getKey(client.options.keyUse.saveString())
            KeyMapping.click(useKey)
            client.options.keyUse.setDown(true)
            wasBlocking = true
        } else {
            release(client)
        }
    }

    private fun shouldBlock(player: net.minecraft.world.entity.player.Player, client: Minecraft): Boolean {
        if (mode.value == BlockMode.STATIC) return true

        val level = client.level ?: return false
        return level.entitiesForRendering()
            .filterIsInstance<LivingEntity>()
            .any { e -> e !== player && !e.isDeadOrDying && player.distanceTo(e) <= dynamicRange.value }
    }

    private fun release(client: Minecraft) {
        if (wasBlocking) {
            client.options.keyUse.setDown(false)
            wasBlocking = false
        }
    }

    private fun isUseHeld(client: Minecraft): Boolean {
        val key = InputConstants.getKey(client.options.keyUse.saveString())
        val window = client.window.handle()
        val physicallyHeld = if (key.type == InputConstants.Type.MOUSE) {
            org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, key.value) == org.lwjgl.glfw.GLFW.GLFW_PRESS
        } else {
            InputConstants.isKeyDown(client.window, key.value)
        }
        return physicallyHeld || client.options.keyUse.isDown
    }
}

