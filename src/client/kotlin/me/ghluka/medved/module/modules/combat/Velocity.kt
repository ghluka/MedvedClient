package me.ghluka.medved.module.modules.combat

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

    val airDelay = intRange("air delay", 1900 to 2100, 0, 5000).also {
        it.visibleWhen = { mode.value == Mode.DELAY }
    }

    val groundDelay = intRange("ground delay", 1900 to 2100, 0, 5000).also {
        it.visibleWhen = { mode.value == Mode.DELAY }
    }

    val delayChance = int("delay chance %", 100, 0, 100).also {
        it.visibleWhen = { mode.value == Mode.DELAY }
    }

    private var scheduledJumpAt  = 0L
    private var releaseJumpAt    = 0L
    @Volatile private var holdPacketsUntil = 0L
    private val packetBuffer     = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()

    @JvmField @Volatile var cachedPlayerId = -1
    @JvmField @Volatile var cachedOnGround = false

    fun scheduleJump(at: Long) { scheduledJumpAt = at }

    fun triggerDelayForPlayer(onGround: Boolean) {
        if (System.currentTimeMillis() < holdPacketsUntil) return
        val (lo, hi) = if (onGround) groundDelay.value else airDelay.value
        val delayMs = if (hi > lo) (lo + (Math.random() * (hi - lo + 1)).toInt()).toLong() else lo.toLong()
        holdPacketsUntil = System.currentTimeMillis() + delayMs
    }

    fun isHolding(): Boolean =
        isEnabled() && mode.value == Mode.DELAY && System.currentTimeMillis() < holdPacketsUntil

    fun bufferPacket(action: Runnable) { packetBuffer.add(action) }

    fun shouldHoldPackets(): Boolean = false

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        val now = System.currentTimeMillis()

        cachedPlayerId = player.id
        cachedOnGround = player.onGround()

        if (!isHolding()) {
            while (true) { val action = packetBuffer.poll() ?: break; action.run() }
        }

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
        scheduledJumpAt  = 0L
        releaseJumpAt    = 0L
        holdPacketsUntil = 0L
        packetBuffer.clear()
        Minecraft.getInstance().options?.keyJump?.setDown(false)
    }
}

