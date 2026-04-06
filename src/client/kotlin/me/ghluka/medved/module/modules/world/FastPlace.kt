package me.ghluka.medved.module.modules.world

import me.ghluka.medved.mixin.client.MinecraftAccessor
import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.tags.ItemTags

object FastPlace : Module(
    name = "Fast Place",
    description = "Removes the right-click delay when placing blocks",
    category = Category.WORLD,
) {
    private val delay    = int("delay ticks", 0, 0, 4)
    private val woolOnly  = boolean("wool only", false)

    override fun onTick(client: Minecraft) {
        if (woolOnly.value) {
            val player = client.player ?: return
            if (!player.mainHandItem.`is`(ItemTags.WOOL)) return
        }
        val current = (client as MinecraftAccessor).rightClickDelay
        if (current > delay.value) {
            (client as MinecraftAccessor).setRightClickDelay(delay.value)
        }
    }
}
