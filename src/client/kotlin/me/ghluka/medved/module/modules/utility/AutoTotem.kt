package me.ghluka.medved.module.modules.utility

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Items
import net.minecraft.client.gui.screens.inventory.InventoryScreen

object AutoTotem : Module("Auto Totem", "Automatically equips totems of undying to your offhand slot", Category.UTILITY) {

    val openInventory = boolean("Open Inventory", false)
    val silentOpen = boolean("Silent Open", false).also {
        it.visibleWhen = { openInventory.value }
    }
    val inventoryOnly = boolean("Inventory Only", true)
    val randomSlot = boolean("Random Slot", true)
    val delayAmount = intRange("Delay (ms)", 50 to 100, 0, 1000)

    private var nextActionTime = 0L

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        val gameMode = client.gameMode ?: return

        if (inventoryOnly.value && client.gui.screen() !is InventoryScreen) {
            return
        }

        if (player.offhandItem.item == Items.TOTEM_OF_UNDYING) {
            return
        }

        val now = System.currentTimeMillis()
        if (now < nextActionTime) return

        val inv = player.inventory
        val hotbarTotems = (0..8).filter { inv.getItem(it).item == Items.TOTEM_OF_UNDYING }
        val mainTotems = (9..35).filter { inv.getItem(it).item == Items.TOTEM_OF_UNDYING }

        var chosenInvSlot: Int? = null

        if (hotbarTotems.isNotEmpty()) {
            val slot = if (randomSlot.value) hotbarTotems.random() else hotbarTotems.first()
            chosenInvSlot = slot + 36
        } else if (mainTotems.isNotEmpty()) {
            if (!openInventory.value && client.gui.screen() !is InventoryScreen) return
            
            val slot = if (randomSlot.value) mainTotems.random() else mainTotems.first()
            chosenInvSlot = slot
            
            if (!silentOpen.value && client.gui.screen() !is InventoryScreen) {
                client.options.keySprint.setDown(false)
                player.setSprinting(false)
                client.gui.setScreen(InventoryScreen(player))
                setDelay()
                return
            }
        }

        if (chosenInvSlot != null) {
            gameMode.handleContainerInput(player.inventoryMenu.containerId, chosenInvSlot, 40, ContainerInput.SWAP, player)
            setDelay()
        }
    }

    private fun setDelay() {
        val (min, max) = delayAmount.value
        val actualDelay = if (min >= max) min else min + java.util.Random().nextInt(max - min + 1)
        nextActionTime = System.currentTimeMillis() + actualDelay
    }
}
