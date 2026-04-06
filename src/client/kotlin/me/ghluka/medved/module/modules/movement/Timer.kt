package me.ghluka.medved.module.modules.movement

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft

object Timer : Module(
    name = "Timer",
    description = "Speeds up or slows down the game tick rate",
    category = Category.MOVEMENT,
) {
    @JvmField
    val speed = float("speed", 1.0f, 0.1f, 10.0f)

    override fun hudInfo(): String = "%.1fx".format(speed.value)
}
