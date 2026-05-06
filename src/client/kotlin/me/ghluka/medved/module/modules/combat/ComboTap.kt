package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft
import kotlin.random.Random

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
}
