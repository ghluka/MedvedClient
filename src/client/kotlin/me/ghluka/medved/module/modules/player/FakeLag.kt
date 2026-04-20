package me.ghluka.medved.module.modules.player

import me.ghluka.medved.util.LagManager
import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft

object FakeLag : Module("Fake Lag", "Delays outgoing packets to simulate real network latency", Category.PLAYER) {

    enum class Mode { DYNAMIC, REPEL, LATENCY }

    val mode  = enum("mode", Mode.LATENCY)
    val lagMs = intRange("lag (ms)", 200 to 300, 50, 2000)

    @Volatile var isCurrentlyLagging = false
        private set

    @Volatile private var repelUntilMs = 0L

    private fun lagConditionActive(): Boolean = when (mode.value) {
        Mode.LATENCY -> true
        Mode.DYNAMIC -> {
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return false
            val level  = mc.level  ?: return false
            val reachModule = me.ghluka.medved.module.modules.combat.Reach
            val maxReach = if (reachModule.enabled.value) reachModule.customRange.value.second else 3.0f
            val triggerDist = maxReach + 0.14f
            level.players().any { e ->
                if (e === player || e.isDeadOrDying) return@any false
                if (player.distanceTo(e) > triggerDist) return@any false
                val dx = e.x - player.x
                val dz = e.z - player.z
                val yaw = Math.toDegrees(kotlin.math.atan2(-dx, dz)).toFloat()
                val fov = net.minecraft.util.Mth.wrapDegrees(yaw - player.yRot)
                kotlin.math.abs(fov) < 20f
            }
        }
        Mode.REPEL -> System.currentTimeMillis() < repelUntilMs
    }

    @JvmStatic fun notifyAttack() {
        if (!enabled.value || mode.value != Mode.REPEL) return
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
            isCurrentlyLagging = false
            return
        }

        isCurrentlyLagging = lagConditionActive()
    }

    override fun onDisabled() {
        repelUntilMs = 0L
        isCurrentlyLagging = false
        LagManager.flushAllOutgoing()
    }
}