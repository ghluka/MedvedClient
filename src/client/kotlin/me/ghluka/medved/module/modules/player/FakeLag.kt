package me.ghluka.medved.module.modules.player

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft
import java.util.concurrent.ConcurrentLinkedQueue

object FakeLag : Module("Fake Lag", "Delays outgoing packets to simulate real network latency", Category.PLAYER) {

    enum class Mode { DYNAMIC, REPEL, LATENCY }

    val mode  = enum("mode", Mode.LATENCY)
    val lagMs = intRange("lag (ms)", 200 to 300, 50, 2000)

    @Volatile var flushing = false
        private set

    @Volatile var isCurrentlyLagging = false
        private set

    private val queue = ConcurrentLinkedQueue<Pair<Long, Runnable>>()

    @Volatile private var repelUntilMs = 0L

    @Volatile private var lastDeliveryMs = 0L

    private fun lagConditionActive(): Boolean = when (mode.value) {
        Mode.LATENCY -> true
        Mode.DYNAMIC -> {
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return false
            val level  = mc.level  ?: return false
            level.players().any { e ->
                e !== player && !e.isDeadOrDying && player.distanceTo(e) <= 6f
            }
        }
        Mode.REPEL -> System.currentTimeMillis() < repelUntilMs
    }

    fun shouldDelay(): Boolean {
        if (!isEnabled() || flushing) return false
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.level == null) return false
        return lagConditionActive() || queue.isNotEmpty()
    }

    fun queuePacket(action: Runnable) {
        val now = System.currentTimeMillis()
        val deliverAt = if (lagConditionActive()) {
            val (lo, hi) = lagMs.value
            val delay = if (hi > lo) (lo + (Math.random() * (hi - lo + 1)).toInt()).toLong() else lo.toLong()
            maxOf(now + delay, lastDeliveryMs + 1L)
        } else {
            maxOf(now, lastDeliveryMs + 1L)
        }
        lastDeliveryMs = deliverAt
        queue.offer(deliverAt to action)
    }

    @JvmStatic fun notifyAttack() {
        if (!isEnabled() || mode.value != Mode.REPEL) return
        val (lo, hi) = lagMs.value
        val delay = if (hi > lo) (lo + (Math.random() * (hi - lo + 1)).toInt()).toLong() else lo.toLong()
        val candidate = System.currentTimeMillis() + delay
        if (candidate > repelUntilMs) repelUntilMs = candidate
    }

    override fun hudInfo(): String {
        val (lo, hi) = lagMs.value
        val modeStr = mode.value.name.lowercase().replaceFirstChar { it.uppercase() }
        val lagStr  = if (hi > lo) "${lo}-${hi}ms" else "${lo}ms"
        return "$modeStr $lagStr"
    }

    override fun hudInfoColor(): Int =
        if (mode.value != Mode.LATENCY && isCurrentlyLagging)
            (255 shl 24) or (255 shl 16) or (80 shl 8) or 80
        else super.hudInfoColor()

    override fun onTick(client: Minecraft) {
        if (client.player == null || client.level == null) {
            flushAndReset()
            isCurrentlyLagging = false
            return
        }

        val condActive = lagConditionActive()
        isCurrentlyLagging = condActive && mode.value != Mode.LATENCY

        val now = System.currentTimeMillis()
        flushing = true
        try {
            while (queue.peek()?.first?.let { it <= now } == true) {
                queue.poll()?.second?.run()
            }
        } finally {
            flushing = false
        }
    }

    override fun onDisabled() {
        flushAndReset()
        repelUntilMs = 0L
        isCurrentlyLagging = false
    }

    private fun flushAndReset() {
        flushing = true
        try { while (true) { queue.poll()?.second?.run() ?: break } }
        finally { flushing = false }
        lastDeliveryMs = 0L
    }
}