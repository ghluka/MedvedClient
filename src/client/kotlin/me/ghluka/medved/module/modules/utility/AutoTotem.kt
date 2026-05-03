package me.ghluka.medved.module.modules.utility

import me.ghluka.medved.module.Module
import me.ghluka.medved.util.SilentScreen
import net.minecraft.client.Minecraft
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Items
import net.minecraft.client.gui.screens.inventory.InventoryScreen

object AutoTotem : Module("Auto Totem", "Automatically equips totems of undying to your offhand slot", Category.UTILITY) {

    val openInventory = boolean("Open Inventory", false)
    val randomSlot = boolean("Random Slot", true)
    val delayAmount = intRange("Delay (ms)", 50 to 100, 0, 1000)

    private var nextActionTime = 0L
    private var openedByModule = false
    private var pendingSwapSlot: Int? = null // track swap across ticks
    private var closeTicksLeft = 0
    private val closeDelayTicks = 2

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        val gameMode = client.gameMode ?: return

        if (pendingSwapSlot != null && (client.screen is SilentScreen || client.screen is InventoryScreen)) {
            val now = System.currentTimeMillis()
            if (now < nextActionTime) return

            gameMode.handleContainerInput(
                player.inventoryMenu.containerId,
                pendingSwapSlot!!,
                40,
                ContainerInput.SWAP,
                player
            )
            pendingSwapSlot = null
            closeTicksLeft = closeDelayTicks
            setDelay()
            return
        }

        if (openedByModule) {
            if (client.screen !is SilentScreen && client.screen !is InventoryScreen) {
                openedByModule = false
                pendingSwapSlot = null
                return
            }
            if (closeTicksLeft > 0) {
                closeTicksLeft -= 1
                return
            } else {
                client.setScreen(null)
                openedByModule = false
                return
            }
        }

        if (player.offhandItem.item == Items.TOTEM_OF_UNDYING) return

        val now = System.currentTimeMillis()
        if (now < nextActionTime) return

        val inv = player.inventory
        val hotbarTotems = (0..8).filter { inv.getItem(it).item == Items.TOTEM_OF_UNDYING }
        val mainTotems = (9..35).filter { inv.getItem(it).item == Items.TOTEM_OF_UNDYING }

        if (hotbarTotems.isEmpty() && mainTotems.isEmpty()) return

        val chosenInvSlot: Int = if (hotbarTotems.isNotEmpty()) {
            val slot = if (randomSlot.value) hotbarTotems.random() else hotbarTotems.first()
            slot + 36
        } else {
            val slot = if (randomSlot.value) mainTotems.random() else mainTotems.first()
            slot
        }

        when {
            client.screen is SilentScreen || client.screen is InventoryScreen -> {
                gameMode.handleContainerInput(
                    player.inventoryMenu.containerId,
                    chosenInvSlot,
                    40,
                    ContainerInput.SWAP,
                    player
                )
                setDelay()
            }
            client.screen != null -> return
            openInventory.value -> {
                client.setScreen(SilentScreen(InventoryScreen(player)))
                openedByModule = true
                pendingSwapSlot = chosenInvSlot
                setDelay()
            }
        }
    }

    private fun setDelay() {
        val (min, max) = delayAmount.value
        val actualDelay = if (min >= max) min else min + java.util.Random().nextInt(max - min + 1)
        nextActionTime = System.currentTimeMillis() + actualDelay
    }
}