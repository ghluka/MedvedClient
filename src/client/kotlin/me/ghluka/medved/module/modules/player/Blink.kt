package me.ghluka.medved.module.modules.player

import me.ghluka.medved.module.Module
import me.ghluka.medved.util.RenderUtil
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentLinkedQueue

object Blink : Module("Blink", "Buffers outgoing packets, making you appear frozen to the server", Category.PLAYER) {

    val holdTime     = intRange("hold (ms)",    2000 to 3000, 100, 15000)
    val maxPackets   = int("max packets",       500,          10,  2000)
    val showGhost    = boolean("show ghost",    true)

    private val packetBuffer = ConcurrentLinkedQueue<Runnable>()

    @Volatile var holding  = false
        private set
    @Volatile var flushing = false
        private set

    private var holdUntil = 0L

    @Volatile private var ghostPos: Vec3? = null
    private var ghostWidth  = 0.6f
    private var ghostHeight = 1.8f

    fun shouldBuffer(): Boolean = isEnabled() && holding && !flushing

    fun bufferPacket(action: Runnable) { packetBuffer.add(action) }

    override fun onTick(client: Minecraft) {
        if (client.player == null || client.level == null) {
            packetBuffer.clear()
            holding  = false
            ghostPos = null
            return
        }
        val now = System.currentTimeMillis()

        if (holding) {
            if (now >= holdUntil || packetBuffer.size >= maxPackets.value) {
                holding = false
                flushBuffer()
            }
        } else {
            val player = client.player!!
            ghostPos    = player.position()
            ghostWidth  = player.bbWidth
            ghostHeight = player.bbHeight
            val (lo, hi) = holdTime.value
            val ms = if (hi > lo) (lo + (Math.random() * (hi - lo + 1)).toInt()).toLong() else lo.toLong()
            holdUntil = now + ms
            holding = true
        }
    }

    private fun flushBuffer() {
        flushing = true
        try {
            while (true) { val a = packetBuffer.poll() ?: break; a.run() }
        } finally {
            flushing = false
        }
        ghostPos = null
        disable()
    }

    override fun onLevelRender(ctx: LevelRenderContext) {
        if (!showGhost.value) return
        val pos = ghostPos ?: return
        val hw = ghostWidth / 2.0
        val box = AABB(pos.x - hw, pos.y, pos.z - hw, pos.x + hw, pos.y + ghostHeight, pos.z + hw)
        RenderUtil.worldContext(ctx) { pose, buf ->
            val vcFill = buf.getBuffer(RenderTypes.debugFilledBox())
            RenderUtil.boxFilled(vcFill, pose, box, 0.2f, 0.6f, 1f, 0.4f)
            buf.endBatch(RenderTypes.debugFilledBox())
            val vcLines = buf.getBuffer(RenderTypes.LINES)
            RenderUtil.boxOutline(vcLines, pose, box, 0.4f, 0.8f, 1f, 0.9f)
            buf.endBatch(RenderTypes.LINES)
        }
    }

    override fun onDisabled() {
        holding   = false
        holdUntil = 0L
        ghostPos  = null
        if (Minecraft.getInstance().level != null) {
            flushing = true
            try {
                while (true) { val a = packetBuffer.poll() ?: break; a.run() }
            } finally {
                flushing = false
            }
        } else {
            packetBuffer.clear()
        }
    }
}