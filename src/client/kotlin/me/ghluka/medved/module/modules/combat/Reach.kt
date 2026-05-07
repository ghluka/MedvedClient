package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft

object Reach : Module("Reach", "Extends your entity attack reach distance", Category.COMBAT) {

    val customRange = floatRange("range", 3.0f to 4.0f, 3.0f, 6.0f, decimals = 2)
    val hitThroughWalls = boolean("hit through walls", false)
    val customChance = int("chance %", 100, 0, 100)

    var tickReach = -1.0
        private set

    override fun onTick(client: Minecraft) {
        val c = customChance.value
        if (c >= 100 || (0 until 100).random() < c) {
            val (lo, hi) = customRange.value
            tickReach = (if (hi > lo) lo + Math.random().toFloat() * (hi - lo) else lo).toDouble()
        } else {
            tickReach = -1.0
        }
    }

    override fun hudInfo(): String {
        val (lo, hi) = customRange.value
        if (lo == 3f) return "%.2f".format(hi)
        return if (hi > lo) "%.2f-%.2f".format(lo, hi) else "%.2f".format(lo)
    }

    override fun onDisabled() {
        tickReach = -1.0
    }
}


