package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft

object NoHitDelay : Module(
    name = "No Hit Delay",
    description = "Removes the 1.8 attack hit delay for 1.7-style PvP on 1.8 servers",
    category = Category.COMBAT,
) {
    override fun onTick(client: Minecraft) {
        client.missTime = 0
    }
}
