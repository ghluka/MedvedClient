package me.ghluka.medved.module.modules.movement

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft

object NoSlow : Module(
    name = "No Slow",
    description = "Prevents movement slowdown when using items like bows, food, or potions",
    category = Category.MOVEMENT,
) {
    @JvmField
    var active = false

    override fun onTick(client: Minecraft) {
        active = isEnabled()
    }

    override fun onDisabled() {
        active = false
    }
}
