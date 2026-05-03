package me.ghluka.medved.module.modules.world

import me.ghluka.medved.mixin.client.MinecraftAccessor
import me.ghluka.medved.module.Module
import me.ghluka.medved.config.entry.ItemListEntry
import me.ghluka.medved.gui.components.itemCategories
import net.minecraft.client.Minecraft
import net.minecraft.tags.ItemTags

object FastPlace : Module(
    name = "Fast Place",
    description = "Removes the right-click delay when placing blocks",
    category = Category.WORLD,
) {
    private val delay    = int("delay ticks", 0, 0, 4)
    private val blockWhitelist = itemList("Item Whitelist", listOf(), defaultMode = ItemListEntry.Mode.WHITELIST)

    override fun onTick(client: Minecraft) {
        if (blockWhitelist.value.isNotEmpty()) {
            val player = client.player ?: return
            val itemName = player.mainHandItem.item.descriptionId.lowercase()
            if (!itemMatchesWhitelist(itemName, blockWhitelist.value)) return
        }
        val current = (client as MinecraftAccessor).rightClickDelay
        if (current > delay.value) {
            (client as MinecraftAccessor).setRightClickDelay(delay.value)
        }
    }

    private fun itemMatchesWhitelist(itemName: String, whitelist: List<String>): Boolean {
        for (entry in whitelist) {
            if (entry.endsWith("_category")) {
                val categoryId = entry.removeSuffix("_category")
                val category = itemCategories.firstOrNull { it.id == categoryId }
                if (category != null && category.matches(itemName)) return true
            } else {
                if (itemName.contains(entry.lowercase())) return true
            }
        }
        return false
    }
}
