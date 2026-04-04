package me.ghluka.medved.module.modules.movement

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft

object Sprint : Module(
    name = "Sprint",
    description = "Automatically sprints",
    category = Category.MOVEMENT,
) {
    private val omnisprint = boolean("omnisprint", false)

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        if (omnisprint.value) {
            player.setSprinting(true)
        } else {
            client.options.keySprint.isDown = true
        }
    }
}
