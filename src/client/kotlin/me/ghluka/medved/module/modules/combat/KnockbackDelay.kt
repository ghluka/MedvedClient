package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft

object KnockbackDelay : Module("Knockback Delay", "Buffers all incoming packets when hit, freezing the world until the delay expires", Category.COMBAT) {

    val airDelay    = intRange("delay (ms)",    1900 to 2100, 0, 5000)
    val chance      = int("chance %",               100,          0, 100)

    @JvmField @Volatile var holdPacketsUntil = 0L

    @JvmField @Volatile var cachedPlayerId = -1
    @JvmField @Volatile var cachedOnGround = false

    fun triggerDelay(onGround: Boolean) {
        if (System.currentTimeMillis() < holdPacketsUntil) return
        val (lo, hi) = airDelay.value
        val delayMs = if (hi > lo) (lo + (Math.random() * (hi - lo + 1)).toInt()).toLong() else lo.toLong()
        holdPacketsUntil = System.currentTimeMillis() + delayMs
    }

    fun isHolding(): Boolean = isEnabled() && System.currentTimeMillis() < holdPacketsUntil

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        cachedPlayerId = player.id
        cachedOnGround = player.onGround()
    }

    override fun hudInfo(): String {
        if (isHolding()) return "Holding"
        val (lo, hi) = airDelay.value
        return if (hi > lo) "${lo}-${hi}ms" else "${lo}ms"
    }

    override fun hudInfoColor(): Int =
        if (isHolding()) (255 shl 24) or (255 shl 16) or (80 shl 8) or 80
        else super.hudInfoColor()

    override fun onDisabled() {
        holdPacketsUntil = 0L
        me.ghluka.medved.manager.LagManager.flushAllIncoming()
    }
}
