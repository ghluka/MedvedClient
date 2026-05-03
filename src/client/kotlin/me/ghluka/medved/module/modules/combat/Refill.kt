package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Items
import net.minecraft.world.item.ItemStack
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import java.util.Locale
import me.ghluka.medved.gui.components.itemCategories
import me.ghluka.medved.util.SilentScreen

object Refill : Module("Refill", "Automatically refills your hotbar with healing items", Category.COMBAT) {
    enum class Type { BOTH, POTS, SOUP }
    enum class Mode { INVENTORY, ALWAYS }

    val vertical = boolean("Vertical", false)
    val scatter = boolean("Scatter", true)
    val hotbarClear = boolean("Hotbar Clear", false)
    val nonJunkItems = itemList("Non Junk Items", listOf("swords_category", "pickaxes_category", "axes_category", "bow", "arrow", "pearl", "food_category")).also {
        it.visibleWhen = { hotbarClear.value }
    }
    val delayAmount = intRange("Delay (ms)", 50 to 100, 0, 1000)
    val type = enum("Type", Type.BOTH)
    val mode = enum("Mode", Mode.INVENTORY)

    private var nextActionTime = 0L
    private var openedByModule = false
    private var closeTicksLeft = 0
    private val closeDelayTicks = 2

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        val gameMode = client.gameMode ?: return

        if (openedByModule && client.screen !is SilentScreen && client.screen !is InventoryScreen) {
            openedByModule = false
        }

        if (mode.value == Mode.INVENTORY && client.screen !is SilentScreen && client.screen !is InventoryScreen) return

        val now = System.currentTimeMillis()
        if (now < nextActionTime) return

        val screenOpen = client.screen is SilentScreen || client.screen is InventoryScreen
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
            val nonJunkList = nonJunkItems.value.map { it.lowercase(Locale.ROOT).trim() }
            for (non in nonJunkList) {
                if (non.isBlank()) continue
                if (non.endsWith("_category")) {
                    val categoryId = non.removeSuffix("_category")
                    val category = itemCategories.firstOrNull { it.id == categoryId }
                    if (category != null && category.matches(name)) return false
                } else {
                    if (name.contains(non)) return false
                }
            }
            if (isHealing(stack)) return false
            return true
        }

        fun hotbarNeedsWork(): Boolean {
            if (hotbarClear.value) {
                for (i in 0..8) {
                    val stack = inventory.getItem(i)
                    if (isJunk(stack)) {
                        val hasEmptyMainSlot = (9..35).any { inventory.getItem(it).isEmpty }
                        if (hasEmptyMainSlot) return true
                    }
                }
            }
            val sourceSlots = (9..35).filter { isHealing(inventory.getItem(it)) }
            if (sourceSlots.isEmpty()) return false
            return (0..8).any { i ->
                val stack = inventory.getItem(i)
                stack.isEmpty || (hotbarClear.value && isJunk(stack))
            }
        }

        if (mode.value == Mode.ALWAYS && !screenOpen) {
            if (client.screen != null) return
            if (hotbarNeedsWork()) {
                client.setScreen(SilentScreen(InventoryScreen(player)))
                openedByModule = true
                setDelay()
            }
            return
        }

        if (!screenOpen) return

        if (hotbarClear.value) {
            for (hotbarIndex in 0..8) {
                val hotbarStack = inventory.getItem(hotbarIndex)
                if (isJunk(hotbarStack)) {
                    val emptySlotIndex = (9..35).firstOrNull { inventory.getItem(it).isEmpty }
                    if (emptySlotIndex != null) {
                        gameMode.handleContainerInput(player.inventoryMenu.containerId, emptySlotIndex, hotbarIndex, ContainerInput.SWAP, player)
                        setDelay()
                        return  // next tick will close if no more work remains
                    }
                }
            }
        }

        var sourceSlots = (9..35).filter { isHealing(inventory.getItem(it)) }
        if (sourceSlots.isEmpty()) {
            if (openedByModule) {
                client.setScreen(null)
                openedByModule = false
            }
            return
        }

        sourceSlots = when {
            scatter.value -> sourceSlots.shuffled()
            vertical.value -> sourceSlots.sortedBy { (it - 9) % 9 }
            else -> sourceSlots
        }

        for (hotbarIndex in 0..8) {
            val hotbarStack = inventory.getItem(hotbarIndex)
            val needsRefill = hotbarStack.isEmpty || (hotbarClear.value && isJunk(hotbarStack))
            if (needsRefill) {
                val sourceSlot = sourceSlots.firstOrNull() ?: break
                gameMode.handleContainerInput(player.inventoryMenu.containerId, sourceSlot, hotbarIndex, ContainerInput.SWAP, player)
                setDelay()
                return
            }
        }

        if (openedByModule) {
            client.setScreen(null)
            openedByModule = false
        }
    }

    override fun onDisabled() {
        val mc = Minecraft.getInstance()
        if (openedByModule && (mc.screen is SilentScreen || mc.screen is InventoryScreen)) mc.setScreen(null)
        openedByModule = false
    }

    private fun setDelay() {
        val (min, max) = delayAmount.value
        val actualDelay = if (min >= max) min else min + java.util.Random().nextInt(max - min + 1)
        nextActionTime = System.currentTimeMillis() + actualDelay
    }
}