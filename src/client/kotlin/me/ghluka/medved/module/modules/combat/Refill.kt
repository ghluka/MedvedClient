package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Items
import net.minecraft.world.item.ItemStack
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import java.util.Locale

object Refill : Module("Refill", "Automatically refills your hotbar with healing items", Category.COMBAT) {
    enum class Type { BOTH, POTS, SOUP }
    enum class Mode { INVENTORY, ALWAYS }

    val vertical = boolean("Vertical", false)
    val scatter = boolean("Scatter", true)
    val hotbarClear = boolean("Hotbar Clear", false)
    val nonJunkItems = string("Non Junk Items", "sword,pickaxe,axe,bow,arrow,pearl,apple,steak,porkchop,gold").also {
        it.visibleWhen = { hotbarClear.value }
    }
    val delayAmount = intRange("Delay (ms)", 50 to 100, 0, 1000)
    val type = enum("Type", Type.BOTH)
    val mode = enum("Mode", Mode.INVENTORY)

    private var nextActionTime = 0L

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        val gameMode = client.gameMode ?: return

        if (mode.value == Mode.INVENTORY && client.screen !is InventoryScreen) {
            return
        }

        val now = System.currentTimeMillis()
        if (now < nextActionTime) return

        val inventory = player.inventory

        val wantPots = type.value == Type.BOTH || type.value == Type.POTS
        val wantSoup = type.value == Type.BOTH || type.value == Type.SOUP

        fun isHealing(stack: ItemStack): Boolean {
            if (stack.isEmpty) return false
            if (wantPots && stack.`is`(Items.SPLASH_POTION)) return true
            if (wantSoup && stack.item == Items.MUSHROOM_STEW) return true
            return false
        }
        
        fun isJunk(stack: ItemStack): Boolean {
            if (stack.isEmpty) return false
            val name = stack.item.descriptionId.lowercase(Locale.ROOT)
            val nonJunkList = nonJunkItems.value.lowercase(Locale.ROOT).split(",")
            for (non in nonJunkList) {
                if (non.isNotBlank() && name.contains(non.trim())) {
                    return false
                }
            }
            if (isHealing(stack)) return false
            return true
        }

        if (hotbarClear.value) {
            for (hotbarIndex in 0..8) {
                val hotbarStack = inventory.getItem(hotbarIndex)
                if (isJunk(hotbarStack)) {
                    val emptySlotIndex = (9..35).firstOrNull { inventory.getItem(it).isEmpty }
                    if (emptySlotIndex != null) {
                        gameMode.handleContainerInput(player.inventoryMenu.containerId, emptySlotIndex, hotbarIndex, ContainerInput.SWAP, player)
                        setDelay()
                        return
                    }
                }
            }
        }

        val hotbarSlots = (0..8).toList()
        
        var sourceSlots = (9..35).filter { isHealing(inventory.getItem(it)) }
        if (sourceSlots.isEmpty()) return

        if (scatter.value) {
            sourceSlots = sourceSlots.shuffled()
        } else if (vertical.value) {
            sourceSlots = sourceSlots.sortedBy { (it - 9) % 9 }
        }

        for (hotbarIndex in hotbarSlots) {
            val hotbarStack = inventory.getItem(hotbarIndex)
            val needsRefill = hotbarStack.isEmpty || (hotbarClear.value && isJunk(hotbarStack))
            
            if (needsRefill) {
                val sourceSlot = sourceSlots.firstOrNull() ?: break
                gameMode.handleContainerInput(player.inventoryMenu.containerId, sourceSlot, hotbarIndex, ContainerInput.SWAP, player)
                setDelay()
                return
            }
        }
    }

    private fun setDelay() {
        val (min, max) = delayAmount.value
        val actualDelay = if (min >= max) min else min + java.util.Random().nextInt(max - min + 1)
        nextActionTime = System.currentTimeMillis() + actualDelay
    }
}
