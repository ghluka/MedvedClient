package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft

object Velocity : Module("Velocity", "Modifies knockback you receive from attacks", Category.COMBAT) {

    enum class Mode {
        REDUCE,
        REVERSE,
        JUMP_RESET
    }

    val mode = enum("mode", Mode.REDUCE)

    val reducePercent = float("reduce %", 50f, 0f, 100f).also {
        it.visibleWhen = { mode.value == Mode.REDUCE }
    }

    val reduceYPercent = float("reduce y %", 50f, 0f, 100f).also {
        it.visibleWhen = { mode.value == Mode.REDUCE }
    }

    val reversePercent = float("reverse %", 30f, 0f, 50f).also {
        it.visibleWhen = { mode.value == Mode.REVERSE }
    }

    val jumpChance = int("chance %", 100, 0, 100).also {
        it.visibleWhen = { mode.value == Mode.JUMP_RESET }
    }

    val jumpTiming = intRange("timing (ms)", 70 to 100, 0, 500).also {
        it.visibleWhen = { mode.value == Mode.JUMP_RESET }
    }

    private var scheduledJumpAt = 0L
    private var releaseJumpAt    = 0L

    fun scheduleJump(at: Long) { scheduledJumpAt = at }

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        val now = System.currentTimeMillis()

        if (scheduledJumpAt > 0L && now >= scheduledJumpAt) {
            scheduledJumpAt = 0L
            if (player.onGround()) {
                client.options.keyJump.setDown(true)
                releaseJumpAt = now + 100L
            }
        }
        if (releaseJumpAt > 0L && now >= releaseJumpAt) {
            releaseJumpAt = 0L
            client.options.keyJump.setDown(false)
        }
    }

    override fun onDisabled() {
        scheduledJumpAt = 0L
        releaseJumpAt   = 0L
        Minecraft.getInstance().options?.keyJump?.setDown(false)
    }
}

