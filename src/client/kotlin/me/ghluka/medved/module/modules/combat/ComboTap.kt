package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.KeyMapping
import kotlin.random.Random
import com.mojang.blaze3d.platform.InputConstants
import org.lwjgl.glfw.GLFW

object ComboTap : Module(
    name = "Combo Tap",
    description = "Taps movement keys on attack to adjust velocity for better combos",
    category = Category.COMBAT
) {

    enum class Method {
        W_TAP,
        S_TAP,
        SHIFT_TAP,
        SPRINT_RESET,
        JUMP_RESET
    }

    val method      = enum("method", Method.W_TAP)
    val tapDuration = intRange("tap duration (ms)", 60 to 100, 10, 500)
    val tapCooldown = intRange("cooldown (ms)", 50 to 80, 0, 500)

    val stopSprint  = boolean("stop sprint", true).also {
        it.visibleWhen = { method.value == Method.W_TAP }
    }

    val onGroundOnly = boolean("on ground only", true)

    @JvmField var suppressForward = false
    @JvmField var suppressSprint  = false
    @JvmField var forceBackward   = false
    @JvmField var forceSneak      = false
    @JvmField var forceJump       = false

    private var tapDeadlineMs      = 0L
    private var cooldownDeadlineMs  = 0L
    private var wasSuppressForward = false
    private var wasSuppressSprint   = false
    private var wasForceBackward    = false
    private var wasForceSneak       = false
    private var wasForceJump        = false

    init {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (isEnabled()) {
                applyKeyStates(client)
            }
        }
    }

    override fun onEnabled()  { resetState() }
    override fun onDisabled() { resetState() }

    private fun resetInputFlags() {
        suppressForward = false
        suppressSprint  = false
        forceBackward   = false
        forceSneak      = false
        forceJump       = false
    }

    private fun resetState() {
        resetInputFlags()
        tapDeadlineMs      = 0L
        cooldownDeadlineMs = 0L
        val client = Minecraft.getInstance()
        val options = client.options
        options.keyUp.setDown(isPhysicalKeyDown(options.keyUp))
        options.keyDown.setDown(isPhysicalKeyDown(options.keyDown))
        options.keyLeft.setDown(isPhysicalKeyDown(options.keyLeft))
        options.keyRight.setDown(isPhysicalKeyDown(options.keyRight))
        options.keyJump.setDown(isPhysicalKeyDown(options.keyJump))
        options.keyShift.setDown(isPhysicalKeyDown(options.keyShift))
        options.keySprint.setDown(isPhysicalKeyDown(options.keySprint))
    }

    override fun hudInfo(): String = when (method.value) {
        Method.W_TAP        -> "W Tap"
        Method.S_TAP        -> "S Tap"
        Method.SHIFT_TAP    -> "Shift Tap"
        Method.SPRINT_RESET -> "Sprint Reset"
        Method.JUMP_RESET   -> "Jump Reset"
    }

    fun onAttack() {
        if (onGroundOnly.value) {
            val player = Minecraft.getInstance().player ?: return
            if (!player.onGround()) return
        }
        val now = System.currentTimeMillis()
        if (now < cooldownDeadlineMs) return
        val (durLo, durHi) = tapDuration.value
        val durMs = if (durHi > durLo) Random.nextLong(durLo.toLong(), durHi.toLong() + 1) else durLo.toLong()
        val (cdLo, cdHi)   = tapCooldown.value
        val cdMs  = if (cdHi > cdLo) Random.nextLong(cdLo.toLong(), cdHi.toLong() + 1) else cdLo.toLong()
        tapDeadlineMs      = now + durMs
        cooldownDeadlineMs = now + durMs + cdMs
    }

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        if (client.screen != null) { resetInputFlags(); return }

        val now = System.currentTimeMillis()
        resetInputFlags()
        if (now < tapDeadlineMs) {
            when (method.value) {
                Method.W_TAP -> {
                    suppressForward = true
                    suppressSprint  = true
                    if (stopSprint.value) player.setSprinting(false)
                }
                Method.S_TAP        -> forceBackward  = true
                Method.SHIFT_TAP    -> forceSneak     = true
                Method.SPRINT_RESET -> suppressSprint = true
                Method.JUMP_RESET   -> if (player.onGround()) forceJump = true
            }
        }
    }

    private fun applyKeyStates(client: Minecraft) {
        if (client.screen != null) return
        val options = client.options

        if (suppressForward) {
            options.keyUp.setDown(false)
        } else if (wasSuppressForward) {
            options.keyUp.setDown(isPhysicalKeyDown(options.keyUp))
        }

        if (forceBackward) {
            options.keyDown.setDown(true)
        } else if (wasForceBackward) {
            options.keyDown.setDown(isPhysicalKeyDown(options.keyDown))
        }

        if (forceSneak) {
            options.keyShift.setDown(true)
        } else if (wasForceSneak) {
            options.keyShift.setDown(isPhysicalKeyDown(options.keyShift))
        }

        if (forceJump) {
            options.keyJump.setDown(true)
        } else if (wasForceJump) {
            options.keyJump.setDown(isPhysicalKeyDown(options.keyJump))
        }

        if (suppressSprint) {
            options.keySprint.setDown(false)
        } else if (wasSuppressSprint) {
            options.keySprint.setDown(isPhysicalKeyDown(options.keySprint))
        }

        wasSuppressForward = suppressForward
        wasSuppressSprint  = suppressSprint
        wasForceBackward   = forceBackward
        wasForceSneak      = forceSneak
        wasForceJump       = forceJump
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
