package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import me.ghluka.medved.util.RenderUtil
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

object Backtrack : Module("Backtrack", "Delays enemy position updates to hit players at their previosu position", Category.COMBAT) {

    enum class Mode {
        MANUAL,
        LAG
    }

    val mode        = enum("mode", Mode.MANUAL)
    val delay       = int("delay (ms)", 200, 50, 1000).also {
        it.visibleWhen = { mode.value == Mode.MANUAL }
    }
    val lagWindow   = intRange("lag window (ms)", 200 to 300, 0, 1000).also {
        it.visibleWhen = { mode.value == Mode.LAG }
    }
    val onlyPlayers = boolean("only players", true)
    val showESP     = boolean("show esp", true)

    @Volatile var flushing = false
        private set

    private data class Pending(val queuedAt: Long, val entityId: Int, val run: Runnable)

    private val buffer = ArrayDeque<Pending>()

    @Volatile var lagActive = false
        private set
    @Volatile private var lagUntilMs = 0L
    @Volatile private var lagDurationMs = 0L

    @Volatile private var incomingFlushing = false
    @Volatile private var incomingLastDeliveryMs = 0L
    private val incomingQueue = ConcurrentLinkedQueue<Pair<Long, Runnable>>()

    val realPositions: ConcurrentHashMap<Int, Vec3> = ConcurrentHashMap()

    fun enqueue(entityId: Int, realPos: Vec3, run: Runnable) {
        realPositions[entityId] = realPos
        synchronized(buffer) {
            buffer.addLast(Pending(System.currentTimeMillis(), entityId, run))
        }
    }

    fun shouldDelayIncomingPackets(): Boolean {
        if (mode.value != Mode.LAG || incomingFlushing) return false
        return lagActive || incomingQueue.isNotEmpty()
    }

    fun bufferIncomingPacket(action: Runnable) {
        val now = System.currentTimeMillis()
        if (!lagActive && incomingQueue.isEmpty()) {
            incomingLastDeliveryMs = now
        }
        val deliverAt = if (lagActive) {
            maxOf(now + lagDurationMs, incomingLastDeliveryMs + 1L)
        } else {
            maxOf(now, incomingLastDeliveryMs + 1L)
        }
        incomingLastDeliveryMs = deliverAt
        incomingQueue.offer(deliverAt to action)
    }

    private fun getLagWindowMs(): Long {
        val (lo, hi) = lagWindow.value
        return if (hi > lo) (lo + (Math.random() * (hi - lo + 1)).toInt()).toLong() else lo.toLong()
    }

    fun triggerLag() {
        if (mode.value != Mode.LAG) return
        val now = System.currentTimeMillis()
        val newDuration = getLagWindowMs()
        lagDurationMs = maxOf(lagDurationMs, newDuration)
        lagActive = true
        lagUntilMs = maxOf(lagUntilMs, now + lagDurationMs)
    }

    fun shouldDelay(): Boolean = when (mode.value) {
        Mode.MANUAL -> true
        Mode.LAG   -> false
    }

    fun clearBuffer() {
        synchronized(buffer) { buffer.clear() }
        realPositions.clear()
    }

    override fun onTick(client: Minecraft) {
        if (client.level == null) {
            clearBuffer()
            clearIncomingQueue()
            return
        }
        val now = System.currentTimeMillis()
        if (mode.value == Mode.LAG && lagActive && now >= lagUntilMs) {
            lagActive = false
        }
        flushExpired(false)
        flushIncomingQueue()
        if (!lagActive && incomingQueue.isEmpty()) {
            lagDurationMs = 0L
        }
    }

    override fun hudInfo(): String = when (mode.value) {
        Mode.MANUAL -> "${delay.value}ms"
        Mode.LAG   -> {
            val (lo, hi) = lagWindow.value
            if (hi > lo) "Lag ${lo}-${hi}ms" else "Lag ${lo}ms"
        }
    }

    override fun onDisabled() {
        lagActive = false
        lagUntilMs = 0L
        lagDurationMs = 0L
        flushIncomingQueue()
        if (Minecraft.getInstance().level != null) flushExpired(true) else clearBuffer()
    }

    override fun onLevelRender(ctx: LevelRenderContext) {
        if (!showESP.value || realPositions.isEmpty()) return
        val level = Minecraft.getInstance().level ?: return

        RenderUtil.worldContext(ctx) { pose, buf ->
            data class EspEntry(val frozenBox: AABB, val realBox: AABB)
            val entries = mutableListOf<EspEntry>()
            for ((entityId, realPos) in realPositions) {
                val entity = level.getEntity(entityId) ?: continue
                val frozenBox = entity.getBoundingBox()
                val hw = entity.bbWidth / 2.0
                val realBox = AABB(realPos.x - hw, realPos.y, realPos.z - hw,
                                   realPos.x + hw, realPos.y + entity.bbHeight, realPos.z + hw)
                entries += EspEntry(frozenBox, realBox)
            }

            val vcLines = buf.getBuffer(RenderTypes.LINES)
            for ((frozenBox, realBox) in entries) {
                RenderUtil.boxOutline(vcLines, pose, frozenBox, 1f, 1f, 1f, 0.8f)
                val fc = frozenBox.center
                val rc = realBox.center
                RenderUtil.line(vcLines, pose,
                    fc.x.toFloat(), fc.y.toFloat(), fc.z.toFloat(),
                    rc.x.toFloat(), rc.y.toFloat(), rc.z.toFloat(),
                    1f, 1f, 0f, 1f)
            }
            buf.endBatch(RenderTypes.LINES)

            val vcFill = buf.getBuffer(RenderTypes.debugFilledBox())
            for ((_, realBox) in entries) {
                RenderUtil.boxFilled(vcFill, pose, realBox, 1f, 0.15f, 0.15f, 0.45f)
            }
            buf.endBatch(RenderTypes.debugFilledBox())
        }
    }

    private fun flushExpired(all: Boolean) {
        val now = System.currentTimeMillis()
        val delayMs = if (mode.value == Mode.LAG) {
            if (lagDurationMs > 0L) lagDurationMs else getLagWindowMs()
        } else {
            delay.value.toLong()
        }
        val toRun = mutableListOf<Int>()  // entity IDs being flushed
        val runnables = mutableListOf<Runnable>()
        synchronized(buffer) {
            val iter = buffer.iterator()
            while (iter.hasNext()) {
                val p = iter.next()
                if (all || now - p.queuedAt >= delayMs) {
                    iter.remove()
                    toRun.add(p.entityId)
                    runnables.add(p.run)
                }
            }
            val stillPending = buffer.map { it.entityId }.toSet()
            toRun.forEach { id -> if (id !in stillPending) realPositions.remove(id) }
        }
        flushing = true
        try {
            runnables.forEach { it.run() }
        } finally {
            flushing = false
        }
    }

    private fun flushIncomingQueue() {
        if (Minecraft.getInstance().level == null) {
            clearIncomingQueue()
            return
        }

        val now = System.currentTimeMillis()
        incomingFlushing = true
        try {
            while (incomingQueue.peek()?.first?.let { it <= now } == true) {
                if (Minecraft.getInstance().level == null) {
                    clearIncomingQueue()
                    return
                }
                incomingQueue.poll()?.second?.run()
            }
        } finally {
            incomingFlushing = false
        }
    }

    private fun clearIncomingQueue() {
        incomingQueue.clear()
        incomingLastDeliveryMs = 0L
    }
}
