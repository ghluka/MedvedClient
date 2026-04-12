package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft
import java.util.concurrent.ConcurrentLinkedQueue

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

    val groundDelay = intRange("ground delay (ms)", 100 to 200, 0, 2000).also {
        it.visibleWhen = { mode.value == Mode.DELAY }
    }

    val airDelay = intRange("air delay (ms)", 200 to 300, 0, 2000).also {
        it.visibleWhen = { mode.value == Mode.DELAY }
    }

    private var scheduledJumpAt = 0L
    private var releaseJumpAt    = 0L

    @Volatile var packetDelayActive = false
        private set
    @Volatile var flushing = false
        private set
    @Volatile private var lastDeliveryMs = 0L
    @Volatile private var packetDelayUntilMs = 0L
    private val outgoingQueue = ConcurrentLinkedQueue<Pair<Long, Runnable>>()

    private const val PACKET_DELAY_WINDOW_MS = 1500L

    fun scheduleJump(at: Long) { scheduledJumpAt = at }

    fun startPacketDelay() {
        val now = System.currentTimeMillis()
        packetDelayActive = true
        packetDelayUntilMs = maxOf(packetDelayUntilMs, now + PACKET_DELAY_WINDOW_MS)
    }

    private fun isDelayWindowActive(): Boolean {
        return packetDelayActive && System.currentTimeMillis() < packetDelayUntilMs
    }

    fun shouldDelayPackets(): Boolean {
        if (!isEnabled() || mode.value != Mode.DELAY || flushing) {
            return false
        }
        return isDelayWindowActive() || outgoingQueue.isNotEmpty()
    }

    fun queuePacket(action: Runnable) {
        val now = System.currentTimeMillis()
        if (!isDelayWindowActive() && outgoingQueue.isEmpty()) {
            lastDeliveryMs = now
        }
        val deliverAt = if (isDelayWindowActive()) {
            val (lo, hi) = if (Minecraft.getInstance().player != null &&
                Minecraft.getInstance().player!!.onGround()) groundDelay.value else airDelay.value
            val delay = if (hi > lo) (lo + (Math.random() * (hi - lo + 1)).toInt()) else lo
            maxOf(now + delay, lastDeliveryMs + 1L)
        } else {
            maxOf(now, lastDeliveryMs + 1L)
        }
        lastDeliveryMs = deliverAt
        outgoingQueue.offer(deliverAt to action)
    }

    fun getRandomDelay(onGround: Boolean): Int {
        val (lo, hi) = if (onGround) groundDelay.value else airDelay.value
        return if (hi > lo) (lo + (Math.random() * (hi - lo + 1)).toInt()) else lo
    }

    override fun onTick(client: Minecraft) {
        if (client.player == null || client.level == null) {
            flushAndReset()
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

        flushing = true
        try {
            while (outgoingQueue.peek()?.first?.let { it <= now } == true) {
                outgoingQueue.poll()?.second?.run()
            }
        } finally {
            flushing = false
        }

        if (packetDelayActive && now >= packetDelayUntilMs) {
            packetDelayActive = false
        }

        if (!packetDelayActive && outgoingQueue.isEmpty()) {
            lastDeliveryMs = 0L
            packetDelayUntilMs = 0L
        }
    }

    private fun flushAndReset() {
        flushing = true
        try { while (true) { outgoingQueue.poll()?.second?.run() ?: break } }
        finally { flushing = false }
        lastDeliveryMs = 0L
        packetDelayUntilMs = 0L
        packetDelayActive = false
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
        flushAndReset()
    }
}

