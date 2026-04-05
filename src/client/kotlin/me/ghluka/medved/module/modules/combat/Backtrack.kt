package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import me.ghluka.medved.util.RenderUtil
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap

object Backtrack : Module("Backtrack", "Delays enemy position updates", Category.COMBAT) {

    val delay       = int("delay (ms)", 200, 50, 1000)
    val onlyPlayers = boolean("only players", true)
    val showESP     = boolean("show esp", true)

    @Volatile var flushing = false
        private set

    private data class Pending(val queuedAt: Long, val entityId: Int, val run: Runnable)

    private val buffer = ArrayDeque<Pending>()

    val realPositions: ConcurrentHashMap<Int, Vec3> = ConcurrentHashMap()

    fun enqueue(entityId: Int, realPos: Vec3, run: Runnable) {
        realPositions[entityId] = realPos
        synchronized(buffer) {
            buffer.addLast(Pending(System.currentTimeMillis(), entityId, run))
        }
    }

    fun clearBuffer() {
        synchronized(buffer) { buffer.clear() }
        realPositions.clear()
    }

    override fun onTick(client: Minecraft) {
        if (client.level == null) { clearBuffer(); return }
        flushExpired(false)
    }

    override fun hudInfo(): String = "${delay.value}ms"

    override fun onDisabled() {
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
        val delayMs = delay.value.toLong()
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
}
