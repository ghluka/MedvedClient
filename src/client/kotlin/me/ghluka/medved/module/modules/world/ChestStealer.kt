package me.ghluka.medved.module.modules.world

import me.ghluka.medved.config.entry.ItemListEntry
import me.ghluka.medved.gui.components.itemCategories
import me.ghluka.medved.module.Module
import me.ghluka.medved.util.SilentScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import java.util.Locale

object ChestStealer : Module(
    "Chest Stealer",
    "Silently steals filtered items from open chests",
    Category.WORLD,
) {
    val stealFilter = itemList(
        "Steal Filter",
        listOf(
            "ender_pearl",
            "axes_category",
            "swords_category",
            "bow",
            "potion",
            "splash_potion",
            "golden_apple",
            "blocks_category",
        ),
        defaultMode = ItemListEntry.Mode.WHITELIST,
        filter = ItemListEntry.Filter.NONE,
    )
    val delayAmount = intRange("Delay (ms)", 50 to 100, 0, 1000)

    private var nextActionTime = 0L
    private var silentOpenedByModule = false
    private var chestDone = false

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        val gameMode = client.gameMode ?: return

        val menu = player.containerMenu
        if (menu === player.inventoryMenu) {
            silentOpenedByModule = false
            chestDone = false
            return
        }

        if (menu !is ChestMenu) {
            silentOpenedByModule = false
            chestDone = false
            return
        }

        val currentScreen = client.screen
        if (currentScreen is ContainerScreen) {
            client.setScreen(SilentScreen(currentScreen))
            silentOpenedByModule = true
        }

        if (currentScreen != null && currentScreen !is SilentScreen && currentScreen !is ContainerScreen) {
            return
        }

        val now = System.currentTimeMillis()
        if (now < nextActionTime) return

        val chestSlotCount = (menu.slots.size - 36).coerceAtLeast(0)
        if (chestSlotCount <= 0) return

        for (slotIndex in 0 until chestSlotCount) {
            val slot = menu.getSlot(slotIndex)
            val stack = slot.item
            if (stack.isEmpty) continue
            if (!matchesFilter(stack)) continue

            gameMode.handleContainerInput(
                menu.containerId,
                slotIndex,
                0,
                ContainerInput.QUICK_MOVE,
                player,
            )
            setDelay()
            return
        }

        if (!chestDone) {
            chestDone = true
            player.closeContainer()
            silentOpenedByModule = false
        }
    }

    override fun onDisabled() {
        val mc = Minecraft.getInstance()
        val player = mc.player
        if (silentOpenedByModule && mc.screen is SilentScreen && player != null) {
            player.closeContainer()
        }
        silentOpenedByModule = false
        chestDone = false
    }

    private fun matchesFilter(stack: ItemStack): Boolean {
        val itemName = stack.item.descriptionId.lowercase(Locale.ROOT)
        val list = stealFilter.value
        if (list.isEmpty()) {
            return true
        }

        var matched = false
        for (entry in list) {
            val token = entry.lowercase(Locale.ROOT).trim()
            if (token.isBlank()) continue

            if (token.endsWith("_category")) {
                val categoryId = token.removeSuffix("_category")
                val category = itemCategories.firstOrNull { it.id == categoryId }
                if (category != null && category.matches(itemName)) {
                    matched = true
                    break
                }
            } else if (itemName.contains(token)) {
                matched = true
                break
            }
        }

        return when (stealFilter.mode) {
            ItemListEntry.Mode.WHITELIST -> matched
            ItemListEntry.Mode.BLACKLIST -> !matched
            else -> matched
        }
    }

    private fun setDelay() {
        val (min, max) = delayAmount.value
        val actualDelay = if (min >= max) min else min + java.util.Random().nextInt(max - min + 1)
        nextActionTime = System.currentTimeMillis() + actualDelay
    }
}