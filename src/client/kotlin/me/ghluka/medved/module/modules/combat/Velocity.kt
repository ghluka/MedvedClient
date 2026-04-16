package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.manager.LagManager
import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft

object Velocity : Module("Velocity", "Modifies knockback you receive from attacks", Category.COMBAT) {

    enum class Mode {
        REDUCE,
        REVERSE,
        JUMP_RESET,
        DELAY
    }

    val mode = enum("mode", Mode.REDUCE)

    val reducePercent = float("reduce %", 50f, 0f, 100f).also {
        it.visibleWhen = { mode.value == Mode.REDUCE }
    }

    val reduceYPercent = float("reduce y %", 0f, 0f, 100f).also {
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

    val groundDelay = intRange("ground delay (ms)", 200 to 250, 0, 2000).also {
        it.visibleWhen = { mode.value == Mode.DELAY }
    }

    val airDelay = intRange("air delay (ms)", 50 to 100, 0, 2000).also {
        it.visibleWhen = { mode.value == Mode.DELAY }
    }

    private var scheduledJumpAt = 0L
    private var releaseJumpAt    = 0L

    @Volatile var packetDelayActive = false
        private set
    @Volatile private var packetDelayUntilMs = 0L

    private const val PACKET_DELAY_WINDOW_MS = 1500L

    fun scheduleJump(at: Long) { scheduledJumpAt = at }

    fun startPacketDelay() {
        val now = System.currentTimeMillis()
        packetDelayActive = true
        packetDelayUntilMs = maxOf(packetDelayUntilMs, now + PACKET_DELAY_WINDOW_MS)
    }

    fun isDelayWindowActive(): Boolean {
        return packetDelayActive && System.currentTimeMillis() < packetDelayUntilMs
    }

    override fun onTick(client: Minecraft) {
        if (client.player == null || client.level == null) {
            packetDelayActive = false
            return
        }

        val player = client.player!!
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

        if (packetDelayActive && now >= packetDelayUntilMs) {
            packetDelayActive = false
        }
    }

    override fun hudInfo(): String = when (mode.value) {
        Mode.REDUCE     -> "Reduce ${reducePercent.value.toInt()}% ${reduceYPercent.value.toInt()}%"
        Mode.REVERSE    -> "Reverse ${reversePercent.value.toInt()}%"
        Mode.JUMP_RESET -> "Jump Reset"
        Mode.DELAY      -> "Delay"
    }

    override fun onDisabled() {
        scheduledJumpAt = 0L
        releaseJumpAt   = 0L
        packetDelayActive = false
        packetDelayUntilMs = 0L
        Minecraft.getInstance().options?.keyJump?.setDown(false)
        LagManager.flushAllOutgoing()
    }
}

