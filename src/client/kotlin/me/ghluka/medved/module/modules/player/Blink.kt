package me.ghluka.medved.module.modules.player

import me.ghluka.medved.manager.LagManager
import me.ghluka.medved.module.Module
import me.ghluka.medved.util.RenderUtil
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object Blink : Module("Blink", "Buffers outgoing packets, making you appear frozen to the server", Category.PLAYER) {

    val holdTime     = intRange("hold (ms)",    2000 to 3000, 100, 15000)
    val maxPackets   = int("max packets",       500,          10,  2000)
    val showGhost    = boolean("show ghost",    true)

    @Volatile var holding  = false
        private set

    private var holdUntil = 0L

    @Volatile private var ghostPos: Vec3? = null
    private var ghostWidth  = 0.6f
    private var ghostHeight = 1.8f

    override fun onTick(client: Minecraft) {
        if (client.player == null || client.level == null) {
            LagManager.flushAllOutgoing()
            holding  = false
            ghostPos = null
            return
        }
        val now = System.currentTimeMillis()

        if (holding) {
            if (now >= holdUntil || LagManager.getOutgoingQueueSize() >= maxPackets.value) {
                holding = false
                LagManager.flushAllOutgoing()
                ghostPos = null
                disable()
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

    override fun hudInfo(): String {
        if (!holding) return ""
        val remaining = (holdUntil - System.currentTimeMillis()).coerceAtLeast(0L)
        return "${remaining}ms"
    }

    override fun onDisabled() {
        holding   = false
        holdUntil = 0L
        ghostPos  = null
        LagManager.flushAllOutgoing()
    }
}