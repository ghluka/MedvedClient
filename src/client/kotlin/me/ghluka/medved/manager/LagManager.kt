package me.ghluka.medved.manager

import me.ghluka.medved.module.modules.combat.Backtrack
import me.ghluka.medved.module.modules.combat.KnockbackDelay
import me.ghluka.medved.module.modules.player.Blink
import me.ghluka.medved.module.modules.player.FakeLag
import me.ghluka.medved.module.modules.combat.Velocity
import net.minecraft.client.Minecraft
import java.util.concurrent.ConcurrentLinkedQueue

object LagManager {

    private val outgoingQueue = ConcurrentLinkedQueue<Pair<Long, Runnable>>()
    private val incomingQueue = ConcurrentLinkedQueue<Pair<Long, Runnable>>()

    @Volatile var flushingOutgoing = false
        private set
    @Volatile var flushingIncoming = false
        private set

    @Volatile private var lastOutgoingDeliveryMs = 0L
    @Volatile private var lastIncomingDeliveryMs = 0L

    fun getOutgoingQueueSize(): Int = outgoingQueue.size
    fun getIncomingQueueSize(): Int = incomingQueue.size

    fun shouldBufferOutgoing(): Boolean {
        if (flushingOutgoing) return false

        val blink = Blink.enabled.value && Blink.holding
        val fakeLag = FakeLag.enabled.value && FakeLag.isCurrentlyLagging       
        val velocity = Velocity.enabled.value && Velocity.mode.value == Velocity.Mode.DELAY && Velocity.isDelayWindowActive()

        if (!blink && !fakeLag && !velocity) {
            if (!outgoingQueue.isEmpty()) flushAllOutgoing()
            return false
        }

        return true
    }

    fun shouldBufferIncoming(): Boolean {
        if (flushingIncoming) return false

        val knockback = KnockbackDelay.enabled.value && KnockbackDelay.isHolding()
        val backtrackLag = Backtrack.enabled.value && Backtrack.mode.value == Backtrack.Mode.LAG && Backtrack.lagActive
        val backtrackManual = Backtrack.enabled.value && Backtrack.mode.value == Backtrack.Mode.MANUAL

        // If no modules are active, flush all incoming and return false immediately!
        if (!knockback && !backtrackLag && !backtrackManual) {
            if (!incomingQueue.isEmpty()) flushAllIncoming()
            return false
        }
        
        return true
    }

    fun bufferOutgoing(action: Runnable) {
        if (flushingOutgoing) {
            try { action.run() } catch (t: Throwable) { }
            return
        }

        val now = System.currentTimeMillis()

        if (Blink.enabled.value && Blink.holding) {
            outgoingQueue.add(Long.MAX_VALUE to action)
            return
        }

        var delayMs = 0L

        if (FakeLag.enabled.value && FakeLag.isCurrentlyLagging) {
            val (lo, hi) = FakeLag.lagMs.value
            val d = if (hi > lo) (lo + (Math.random() * (hi - lo + 1)).toInt()).toLong() else lo.toLong()
            if (d > delayMs) delayMs = d
        }

        if (Velocity.enabled.value && Velocity.mode.value == Velocity.Mode.DELAY && Velocity.isDelayWindowActive()) {
            val onGround = Minecraft.getInstance().player?.onGround() == true
            val (lo, hi) = if (onGround) Velocity.groundDelay.value else Velocity.airDelay.value
            val d = if (hi > lo) (lo + (Math.random() * (hi - lo + 1)).toInt()).toLong() else lo.toLong()
            if (d > delayMs) delayMs = d
        }

        val active = delayMs > 0

        if (!active && outgoingQueue.isEmpty()) {
            lastOutgoingDeliveryMs = now
        }

        val deliverAt = if (active) {
            maxOf(now + delayMs, lastOutgoingDeliveryMs)
        } else {
            maxOf(now, lastOutgoingDeliveryMs)
        }

        lastOutgoingDeliveryMs = deliverAt
        outgoingQueue.offer(deliverAt to action)
    }

    fun bufferIncoming(action: Runnable) {
        if (flushingIncoming) {
            try { action.run() } catch (_: Throwable) {}
            return
        }

        val now = System.currentTimeMillis()
        var delayMs = 0L

        if (KnockbackDelay.enabled.value && KnockbackDelay.isHolding()) {
            val d = KnockbackDelay.holdPacketsUntil - now
            if (d > delayMs) delayMs = d
        }

        if (Backtrack.enabled.value) {
            if (Backtrack.mode.value == Backtrack.Mode.LAG && Backtrack.lagActive) {
                val d = Backtrack.lagUntilMs - now
                if (d > delayMs) delayMs = d
            } else if (Backtrack.mode.value == Backtrack.Mode.MANUAL) {
                val d = Backtrack.delay.value.toLong()
                if (d > delayMs) delayMs = d
            }
        }

        val active = delayMs > 0

        val deliverAt = if (active) {
            maxOf(now + delayMs, lastIncomingDeliveryMs)
        } else {
            maxOf(now, lastIncomingDeliveryMs)
        }

        lastIncomingDeliveryMs = deliverAt
        incomingQueue.offer(deliverAt to action)
    }

    fun onTick() {
        val now = System.currentTimeMillis()

        // Outgoing flush
        flushingOutgoing = true
        try {
            var processed = 0
            while (processed < 50) {
                val peek = outgoingQueue.peek() ?: break
                if (peek.first <= now) {
                    val action = outgoingQueue.poll()?.second ?: break
                    try { action.run() } catch (t: Throwable) { }
                    processed++
                } else {
                    break
                }
            }
        } finally {
            flushingOutgoing = false
        }

        if (outgoingQueue.isEmpty() && !shouldBufferOutgoing()) {
            lastOutgoingDeliveryMs = 0L
        }

        // Incoming flush
        flushingIncoming = true
        try {
            var processed = 0
            while (processed < 50) {
                val peek = incomingQueue.peek() ?: break
                if (peek.first <= now) {
                    val action = incomingQueue.poll()?.second ?: break
                    try { action.run() } catch (t: Throwable) { }
                    processed++
                } else {
                    break
                }
            }
        } finally {
            flushingIncoming = false
        }

        if (incomingQueue.isEmpty() && !shouldBufferIncoming()) {
            lastIncomingDeliveryMs = 0L
        }
    }

    fun flushAllOutgoing() {
        flushingOutgoing = true
        try {
            while (true) {
                val action = outgoingQueue.poll()?.second ?: break
                try { action.run() } catch (t: Throwable) { }
            }
        } finally {
            flushingOutgoing = false
            lastOutgoingDeliveryMs = 0L
        }
    }

    fun flushAllIncoming() {
        flushingIncoming = true
        try {
            while (true) {
                val action = incomingQueue.poll()?.second ?: break
                try { action.run() } catch (t: Throwable) { }
            }
        } finally {
            flushingIncoming = false
            lastIncomingDeliveryMs = 0L
        }
    }
}
