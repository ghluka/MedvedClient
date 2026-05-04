package me.ghluka.medved.module.modules.utility

import me.ghluka.medved.module.Module
import me.ghluka.medved.util.SilentScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.resources.ResourceKey

object InventoryManager : Module(
    "Inventory Manager",
    "Sorts your hotbar, equips better gear, and throws out junk",
    Category.UTILITY,
) {
    enum class Mode { INVENTORY, ALWAYS }

    val mode = enum("Mode", Mode.INVENTORY)
    val activateOnPercent = int("Activate On %", 70, 0, 100).also {
        it.visibleWhen = { mode.value == Mode.ALWAYS }
    }
    val delayAmount = intRange("Delay (ms)", 50 to 100, 0, 1000)

    val autoHotbar = boolean("Auto Hotbar", true)
    val swordSlot = int("Sword Slot", 0, -1, 8).also {
        it.visibleWhen = { autoHotbar.value }
    }
    val pickaxeSlot = int("Pickaxe Slot", 1, -1, 8).also {
        it.visibleWhen = { autoHotbar.value }
    }
    val axeSlot = int("Axe Slot", 2, -1, 8).also {
        it.visibleWhen = { autoHotbar.value }
    }
    val bowSlot = int("Bow Slot", 3, -1, 8).also {
        it.visibleWhen = { autoHotbar.value }
    }
    val pearlSlot = int("Pearl Slot", 4, -1, 8).also {
        it.visibleWhen = { autoHotbar.value }
    }
    val gappleSlot = int("Gapple Slot", 5, -1, 8).also {
        it.visibleWhen = { autoHotbar.value }
    }
    val blockSlot = int("Block Slot", 6, -1, 8).also {
        it.visibleWhen = { autoHotbar.value }
    }
    val foodSlot = int("Food Slot", 7, -1, 8).also {
        it.visibleWhen = { autoHotbar.value }
    }

    val autoArmor = boolean("Auto Armor", true)
    val prioritizeBlastResistance = boolean("Prioritize Blast Resistance", false).also {
        it.visibleWhen = { autoArmor.value }
    }

    val trashJunk = boolean("Inventory Cleaner", true)
    val trashExtras = boolean("Trash Extra Gear", true)

    private var nextActionTime = 0L
    private var openedByModule = false

    private val armorInventorySlots = mapOf(
        EquipmentSlot.FEET to 36,
        EquipmentSlot.LEGS to 37,
        EquipmentSlot.CHEST to 38,
        EquipmentSlot.HEAD to 39,
    )

    private val armorContainerSlots = mapOf(
        EquipmentSlot.HEAD to 5,
        EquipmentSlot.CHEST to 6,
        EquipmentSlot.LEGS to 7,
        EquipmentSlot.FEET to 8,
    )

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        val gameMode = client.gameMode ?: return
        val screenOpen = client.screen is SilentScreen || client.screen is InventoryScreen

        if (openedByModule && !screenOpen) {
            openedByModule = false
        }

        val now = System.currentTimeMillis()
        if (now < nextActionTime) return

        if (mode.value == Mode.ALWAYS && !screenOpen) {
            if (client.screen != null) return
            if (inventoryFullPercent(player) >= activateOnPercent.value && hasWork(player)) {
                client.setScreen(SilentScreen(InventoryScreen(player)))
                openedByModule = true
                setDelay()
            }
            return
        }

        if (!screenOpen) return

        val inventory = player.inventory

        if (autoArmor.value) {
            for (equipSlot in listOf(
                EquipmentSlot.FEET,
                EquipmentSlot.LEGS,
                EquipmentSlot.CHEST,
                EquipmentSlot.HEAD
            )) {
                val armorInvSlot = armorInventorySlots[equipSlot] ?: continue
                val armorContSlot = armorContainerSlots[equipSlot] ?: continue

                val currentArmor = inventory.getItem(armorInvSlot)
                val bestInvSlot = findBestArmorInvSlot(player, equipSlot) ?: continue
                val newArmor = inventory.getItem(bestInvSlot)

                if (bestInvSlot == armorInvSlot) continue
                if (!isDesiredArmor(currentArmor, newArmor, equipSlot)) continue

                if (!currentArmor.isEmpty) {
                    gameMode.handleContainerInput(
                        player.containerMenu.containerId,
                        armorContSlot,
                        1,
                        ContainerInput.THROW,
                        player,
                    )
                    setDelay()
                    return
                }

                val sourceContSlot = invSlotToContainerSlot(bestInvSlot)
                gameMode.handleContainerInput(
                    player.containerMenu.containerId,
                    sourceContSlot,
                    0,
                    ContainerInput.QUICK_MOVE,
                    player,
                )
                setDelay()
                return
            }
        }

        if (autoHotbar.value) {
            for (target in listOf(
                swordSlot.value,
                pickaxeSlot.value,
                axeSlot.value,
                bowSlot.value,
                pearlSlot.value,
                gappleSlot.value,
                blockSlot.value,
                foodSlot.value,
            )) {
                if (target < 0) continue
                val desired = desiredItemForSlot(player, target) ?: continue
                val bestInvSlot = desired.slot
                val targetStack = inventory.getItem(target)
                val bestStack = inventory.getItem(bestInvSlot)
                if (bestInvSlot == target && !targetStack.isEmpty) continue
                if (!shouldSwapToTarget(targetStack, bestStack, bestInvSlot, target)) continue

                val sourceContSlot = invSlotToContainerSlot(bestInvSlot)
                gameMode.handleContainerInput(
                    player.containerMenu.containerId,
                    sourceContSlot,
                    target,
                    ContainerInput.SWAP,
                    player,
                )
                setDelay()
                return
            }
        }

        val keep = computeKeepSlots(player)

        if (trashJunk.value) {
            for (slot in 0..35) {
                if (slot in keep) continue
                val stack = inventory.getItem(slot)
                if (stack.isEmpty) continue
                if (!isJunk(stack)) continue
                val contSlot = invSlotToContainerSlot(slot)
                gameMode.handleContainerInput(
                    player.containerMenu.containerId,
                    contSlot,
                    1,
                    ContainerInput.THROW,
                    player,
                )
                setDelay()
                return
            }
        }

        if (trashExtras.value) {
            for (slot in 0..35) {
                if (slot in keep) continue
                val stack = inventory.getItem(slot)
                if (stack.isEmpty) continue
                if (isWorseExtraGear(player, stack, slot)) {
                    val contSlot = invSlotToContainerSlot(slot)
                    gameMode.handleContainerInput(
                        player.containerMenu.containerId,
                        contSlot,
                        1,
                        ContainerInput.THROW,
                        player,
                    )
                    setDelay()
                    return
                }
            }
        }

        if (openedByModule) {
            client.setScreen(null)
            openedByModule = false
        }
    }

    override fun onDisabled() {
        val mc = Minecraft.getInstance()
        if (openedByModule && (mc.screen is SilentScreen || mc.screen is InventoryScreen)) {
            mc.setScreen(null)
        }
        openedByModule = false
    }

    private fun invSlotToContainerSlot(invSlot: Int): Int = when {
        invSlot in 0..8 -> invSlot + 36
        invSlot in 9..35 -> invSlot
        invSlot in 36..39 -> invSlot - 31
        invSlot == 40 -> 45
        else -> invSlot
    }

    private fun inventoryFullPercent(player: net.minecraft.world.entity.player.Player): Int {
        val total = 27 // slots 9-35
        val used = (9..35).count { !player.inventory.getItem(it).isEmpty }
        return (used * 100) / total
    }

    private fun hasWork(player: net.minecraft.world.entity.player.Player): Boolean {
        val inventory = player.inventory

        if (autoArmor.value) {
            for (equipSlot in EquipmentSlot.entries) {
                val armorInvSlot = armorInventorySlots[equipSlot] ?: continue
                val bestInvSlot = findBestArmorInvSlot(player, equipSlot) ?: continue
                if (bestInvSlot == armorInvSlot) continue
                val currentArmor = inventory.getItem(armorInvSlot)
                val newArmor = inventory.getItem(bestInvSlot)
                if (isDesiredArmor(currentArmor, newArmor, equipSlot)) return true
            }
        }

        if (autoHotbar.value) {
            for (target in listOf(
                swordSlot.value,
                pickaxeSlot.value,
                axeSlot.value,
                bowSlot.value,
                pearlSlot.value,
                gappleSlot.value,
                blockSlot.value,
                foodSlot.value,
            )) {
                if (target < 0) continue
                val desired = desiredItemForSlot(player, target) ?: continue
                return true
            }
        }

        val keep = computeKeepSlots(player)
        if (trashJunk.value) {
            for (slot in 0..35) {
                if (slot in keep) continue
                val stack = inventory.getItem(slot)
                if (stack.isEmpty) continue
                if (isJunk(stack)) return true
            }
        }
        if (trashExtras.value) {
            for (slot in 0..35) {
                if (slot in keep) continue
                val stack = inventory.getItem(slot)
                if (stack.isEmpty) continue
                if (isWorseExtraGear(player, stack, slot)) return true
            }
        }

        return false
    }

    private fun computeKeepSlots(player: net.minecraft.world.entity.player.Player): Set<Int> {
        val keep = mutableSetOf<Int>()

        if (autoArmor.value) {
            for (equipSlot in EquipmentSlot.entries) {
                val armorInvSlot = armorInventorySlots[equipSlot] ?: continue
                keep += armorInvSlot
                val bestInvSlot = findBestArmorInvSlot(player, equipSlot) ?: continue
                keep += bestInvSlot
            }
        }

        if (autoHotbar.value) {
            for (target in listOf(
                swordSlot.value,
                pickaxeSlot.value,
                axeSlot.value,
                bowSlot.value,
                pearlSlot.value,
                gappleSlot.value,
                blockSlot.value,
                foodSlot.value,
            )) {
                if (target < 0) continue
                val targetStack = player.inventory.getItem(target)
                if (!targetStack.isEmpty && isCorrectForTargetSlot(targetStack, target)) {
                    keep += target
                }

                val hotbarStack = targetStack
                val desired = desiredItemForSlot(player, target) ?: continue
                val desiredStack = player.inventory.getItem(desired.slot)
                if (hotbarStack.isEmpty || shouldSwapToTarget(hotbarStack, desiredStack, desired.slot, target)) {
                    keep += desired.slot
                }
            }
        }

        return keep
    }

    private fun isWorseExtraGear(player: net.minecraft.world.entity.player.Player, stack: ItemStack, slot: Int): Boolean {
        val inventory = player.inventory

        if (isBlock(stack)) {
            val thisScore = blockScore(stack)
            val bestScore = (0..35).mapNotNull { s ->
                if (s == slot) return@mapNotNull null
                inventory.getItem(s).takeIf { isBlock(it) }?.let { blockScore(it) }
            }.maxOrNull() ?: return false
            return thisScore <= bestScore
        }

        if (isArmor(stack)) {
            val equipSlot = getArmorSlotForItem(stack) ?: return false
            val thisScore = armorScore(stack)
            val bestScore = (0..39).mapNotNull { s ->
                if (s == slot) return@mapNotNull null
                inventory.getItem(s).takeIf { isArmorForSlot(it, equipSlot) }?.let { armorScore(it) }
            }.maxOrNull() ?: return false
            return thisScore <= bestScore
        }

        if (isSword(stack)) {
            val thisScore = swordScore(stack)
            val bestScore = (0..35).mapNotNull { s ->
                if (s == slot) return@mapNotNull null
                inventory.getItem(s).takeIf { isSword(it) }?.let { swordScore(it) }
            }.maxOrNull() ?: return false
            return thisScore <= bestScore
        }

        if (isPickaxe(stack)) {
            val thisScore = toolScore(stack, ToolType.PICKAXE)
            val bestScore = (0..35).mapNotNull { s ->
                if (s == slot) return@mapNotNull null
                inventory.getItem(s).takeIf { isPickaxe(it) }?.let { toolScore(it, ToolType.PICKAXE) }
            }.maxOrNull() ?: return false
            return thisScore <= bestScore
        }

        if (isAxe(stack)) {
            val thisScore = toolScore(stack, ToolType.AXE)
            val bestScore = (0..35).mapNotNull { s ->
                if (s == slot) return@mapNotNull null
                inventory.getItem(s).takeIf { isAxe(it) }?.let { toolScore(it, ToolType.AXE) }
            }.maxOrNull() ?: return false
            return thisScore <= bestScore
        }

        if (isBow(stack)) {
            val thisScore = bowScore(stack)
            val bestScore = (0..35).mapNotNull { s ->
                if (s == slot) return@mapNotNull null
                inventory.getItem(s).takeIf { isBow(it) }?.let { bowScore(it) }
            }.maxOrNull() ?: return false
            return thisScore <= bestScore
        }

        return false
    }

    private fun isJunk(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        return !isSword(stack) &&
                !isPickaxe(stack) &&
                !isAxe(stack) &&
                !isBow(stack) &&
                !isPearl(stack) &&
                !isGapple(stack) &&
                !isBlock(stack) &&
                !isFood(stack) &&
                !isArmor(stack) &&
                !isTotem(stack) &&
                !isArrow(stack)
    }

    private data class SlotChoice(val slot: Int, val score: Double)

    private fun isCorrectForTargetSlot(stack: ItemStack, targetSlot: Int): Boolean = when (targetSlot) {
        swordSlot.value -> isSword(stack)
        pickaxeSlot.value -> isPickaxe(stack)
        axeSlot.value -> isAxe(stack)
        bowSlot.value -> isBow(stack)
        pearlSlot.value -> isPearl(stack)
        gappleSlot.value -> isGapple(stack)
        blockSlot.value -> isBlock(stack)
        foodSlot.value -> isFood(stack)
        else -> false
    }

    private fun desiredItemForSlot(player: net.minecraft.world.entity.player.Player, targetSlot: Int): SlotChoice? {
        val inventory = player.inventory
        val choices: List<Pair<Int, Double>> = when (targetSlot) {
            swordSlot.value -> (0..35).mapNotNull { slot -> inventory.getItem(slot).takeIf { isSword(it) }?.let { slot to swordScore(it) } }
            pickaxeSlot.value -> (0..35).mapNotNull { slot -> inventory.getItem(slot).takeIf { isPickaxe(it) }?.let { slot to toolScore(it, ToolType.PICKAXE) } }
            axeSlot.value -> (0..35).mapNotNull { slot -> inventory.getItem(slot).takeIf { isAxe(it) }?.let { slot to toolScore(it, ToolType.AXE) } }
            bowSlot.value -> (0..35).mapNotNull { slot -> inventory.getItem(slot).takeIf { isBow(it) }?.let { slot to bowScore(it) } }
            pearlSlot.value -> (0..35).mapNotNull { slot -> inventory.getItem(slot).takeIf { isPearl(it) }?.let { slot to pearlScore(it) } }
            gappleSlot.value -> (0..35).mapNotNull { slot -> inventory.getItem(slot).takeIf { isGapple(it) }?.let { slot to gappleScore(it) } }
            blockSlot.value -> (0..35).mapNotNull { slot -> inventory.getItem(slot).takeIf { isBlock(it) }?.let { slot to blockScore(it) } }
            foodSlot.value -> (0..35).mapNotNull { slot -> inventory.getItem(slot).takeIf { isFood(it) }?.let { slot to foodScore(it) } }
            else -> emptyList()
        }

        val best = choices.maxByOrNull { it.second } ?: return null
        return SlotChoice(best.first, best.second)
    }

    private fun findBestArmorInvSlot(player: net.minecraft.world.entity.player.Player, slot: EquipmentSlot): Int? {
        val inventory = player.inventory
        var bestSlot: Int? = null
        var bestScore = Double.NEGATIVE_INFINITY

        for (candidate in 0..39) {
            val stack = inventory.getItem(candidate)
            if (!isArmorForSlot(stack, slot)) continue
            val score = armorScore(stack)
            if (score > bestScore) {
                bestScore = score
                bestSlot = candidate
            }
        }
        return bestSlot
    }

    private fun shouldSwapToTarget(targetStack: ItemStack, sourceStack: ItemStack, sourceSlot: Int, targetSlot: Int): Boolean {
        if (sourceSlot == targetSlot) return false
        if (sourceStack.isEmpty) return false
        if (targetStack.isEmpty) return true
        return sourceStackCountedBetter(sourceStack, targetStack)
    }

    private fun sourceStackCountedBetter(source: ItemStack, target: ItemStack): Boolean {
        return when {
            isSword(source) && isSword(target) -> swordScore(source) > swordScore(target)
            isPickaxe(source) && isPickaxe(target) -> toolScore(source, ToolType.PICKAXE) > toolScore(target, ToolType.PICKAXE)
            isAxe(source) && isAxe(target) -> toolScore(source, ToolType.AXE) > toolScore(target, ToolType.AXE)
            isBow(source) && isBow(target) -> bowScore(source) > bowScore(target)
            isPearl(source) && isPearl(target) -> pearlScore(source) > pearlScore(target)
            isGapple(source) && isGapple(target) -> gappleScore(source) > gappleScore(target)
            isBlock(source) && isBlock(target) -> blockScore(source) > blockScore(target)
            isFood(source) && isFood(target) -> foodScore(source) > foodScore(target)
            isArmor(source) && isArmor(target) -> armorScore(source) > armorScore(target)
            else -> true
        }
    }

    private fun isDesiredArmor(currentStack: ItemStack, candidateStack: ItemStack, slot: EquipmentSlot): Boolean {
        if (candidateStack.isEmpty) return false
        if (!isArmorForSlot(candidateStack, slot)) return false
        if (currentStack.isEmpty) return true
        return armorScore(candidateStack) > armorScore(currentStack)
    }

    private enum class ToolType { PICKAXE, AXE }

    private fun isSword(stack: ItemStack) = stack.`is`(ItemTags.SWORDS)
    private fun isPickaxe(stack: ItemStack) = stack.`is`(ItemTags.PICKAXES)
    private fun isAxe(stack: ItemStack) = stack.`is`(ItemTags.AXES)
    private fun isBow(stack: ItemStack) = stack.item == Items.BOW
    private fun isPearl(stack: ItemStack) = stack.item == Items.ENDER_PEARL
    private fun isGapple(stack: ItemStack) = stack.item == Items.GOLDEN_APPLE || stack.item == Items.ENCHANTED_GOLDEN_APPLE
    private fun isBlock(stack: ItemStack) = stack.item is BlockItem
    private fun isFood(stack: ItemStack) =
        !isGapple(stack) && stack.item.components().has(net.minecraft.core.component.DataComponents.FOOD)
    private fun isArmor(stack: ItemStack) = stack.`is`(ItemTags.ARMOR_ENCHANTABLE)
    private fun isTotem(stack: ItemStack) = stack.item == Items.TOTEM_OF_UNDYING
    private fun isArrow(stack: ItemStack) = stack.`is`(ItemTags.ARROWS)

    private fun getArmorSlotForItem(stack: ItemStack): EquipmentSlot? {
        val equippable = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE)
            ?: return null

        return when (equippable.slot()) {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET -> equippable.slot()

            else -> null
        }
    }

    private fun isArmorForSlot(stack: ItemStack, slot: EquipmentSlot): Boolean {
        if (!stack.`is`(ItemTags.ARMOR_ENCHANTABLE)) return false
        return getArmorSlotForItem(stack) == slot
    }

    private fun durabilityRatio(stack: ItemStack): Double {
        val hasMending = enchantLevel(stack, Enchantments.MENDING) > 0
        if (hasMending) return 1.0
        val maxDamage = stack.item.components().get(net.minecraft.core.component.DataComponents.MAX_DAMAGE) ?: return 1.0
        if (maxDamage <= 0) return 1.0
        val damage = stack.damageValue
        return ((maxDamage - damage).toDouble() / maxDamage).coerceIn(0.0, 1.0)
    }

    private fun armorTier(stack: ItemStack): Double {
        val name = stack.item.toString().lowercase()
        return when {
            name.contains("netherite") -> 25.0
            name.contains("diamond")   -> 20.0
            name.contains("iron")      -> 15.0
            name.contains("chainmail") -> 10.0
            name.contains("golden")    -> 8.0
            name.contains("leather")   -> 5.0
            else -> 0.0
        }
    }

    private fun weaponTier(stack: ItemStack): Double {
        val name = stack.item.toString().lowercase()
        return when {
            name.contains("netherite") -> 25.0
            name.contains("diamond")   -> 20.0
            name.contains("iron")      -> 15.0
            name.contains("stone")     -> 10.0
            name.contains("golden")    -> 8.0
            name.contains("wood")      -> 5.0
            else -> 0.0
        }
    }

    private fun armorScore(stack: ItemStack): Double {
        val protection = enchantLevel(stack, Enchantments.PROTECTION)
        val blast = enchantLevel(stack, Enchantments.BLAST_PROTECTION)
        val projectile = enchantLevel(stack, Enchantments.PROJECTILE_PROTECTION)
        val fire = enchantLevel(stack, Enchantments.FIRE_PROTECTION)
        val featherFalling = enchantLevel(stack, Enchantments.FEATHER_FALLING)
        val depthStrider = enchantLevel(stack, Enchantments.DEPTH_STRIDER)
        val respiration = enchantLevel(stack, Enchantments.RESPIRATION)
        val aquaAffinity = enchantLevel(stack, Enchantments.AQUA_AFFINITY)
        val unbreaking = enchantLevel(stack, Enchantments.UNBREAKING)
        val mending = enchantLevel(stack, Enchantments.MENDING)
        val thorns = enchantLevel(stack, Enchantments.THORNS)

        val enchantScore = if (prioritizeBlastResistance.value) {
            blast * 3.0 + protection * 0.75 + projectile * 0.5 + fire * 0.5
        } else {
            protection * 3.0 + blast * 0.75 + projectile * 0.5 + fire * 0.5
        }
        val slotBonus = featherFalling * 0.4 + depthStrider * 0.3 + respiration * 0.2 + aquaAffinity * 0.1
        val miscEnchants = unbreaking * 0.15 + mending * 0.2 + thorns * 0.1

        return armorTier(stack) * durabilityRatio(stack) + enchantScore + slotBonus + miscEnchants
    }

    private fun swordScore(stack: ItemStack): Double {
        val sharpness = enchantLevel(stack, Enchantments.SHARPNESS)
        val smite = enchantLevel(stack, Enchantments.SMITE)
        val bane = enchantLevel(stack, Enchantments.BANE_OF_ARTHROPODS)
        val fireAspect = enchantLevel(stack, Enchantments.FIRE_ASPECT)
        val knockback = enchantLevel(stack, Enchantments.KNOCKBACK)
        val looting = enchantLevel(stack, Enchantments.LOOTING)
        val sweepingEdge = enchantLevel(stack, Enchantments.SWEEPING_EDGE)
        val unbreaking = enchantLevel(stack, Enchantments.UNBREAKING)
        val mending = enchantLevel(stack, Enchantments.MENDING)
        val enchantScore = sharpness * 3.0 + maxOf(smite, bane) * 0.75 + fireAspect * 0.4 + knockback * 0.2 + looting * 0.15 + sweepingEdge * 0.2 + unbreaking * 0.1 + mending * 0.2

        return weaponTier(stack) * durabilityRatio(stack) + enchantScore
    }

    private fun toolScore(stack: ItemStack, type: ToolType): Double {
        val efficiency = enchantLevel(stack, Enchantments.EFFICIENCY)
        val fortune = enchantLevel(stack, Enchantments.FORTUNE)
        val silkTouch = enchantLevel(stack, Enchantments.SILK_TOUCH)
        val unbreaking = enchantLevel(stack, Enchantments.UNBREAKING)
        val mending = enchantLevel(stack, Enchantments.MENDING)
        val enchantScore = efficiency * 3.0 + fortune * 0.5 + silkTouch * 0.75 + unbreaking * 0.1 + mending * 0.2 + if (type == ToolType.AXE) 0.05 else 0.0

        return weaponTier(stack) * durabilityRatio(stack) + enchantScore
    }

    private fun bowScore(stack: ItemStack): Double {
        val power = enchantLevel(stack, Enchantments.POWER)
        val punch = enchantLevel(stack, Enchantments.PUNCH)
        val flame = enchantLevel(stack, Enchantments.FLAME)
        val infinity = enchantLevel(stack, Enchantments.INFINITY)
        val unbreaking = enchantLevel(stack, Enchantments.UNBREAKING)
        val mending = enchantLevel(stack, Enchantments.MENDING)
        val enchantScore = power * 2.5 + punch * 0.8 + flame * 0.4 + infinity * 1.2 + unbreaking * 0.1 + mending * 0.15
        return 10.0 * durabilityRatio(stack) + enchantScore
    }

    private fun pearlScore(stack: ItemStack): Double = stack.count.toDouble()
    private fun gappleScore(stack: ItemStack): Double = if (stack.item == Items.ENCHANTED_GOLDEN_APPLE) 100.0 + stack.count else 50.0 + stack.count
    private fun blockScore(stack: ItemStack): Double = stack.count.toDouble()
    private fun foodScore(stack: ItemStack): Double {
        val food = stack.item.components().get(net.minecraft.core.component.DataComponents.FOOD) ?: return 0.0
        val quality = food.saturation() * food.nutrition()
        return quality * 1000.0 + stack.count
    }

    private fun enchantLevel(stack: ItemStack, enchantmentKey: ResourceKey<Enchantment>): Int {
        val level = Minecraft.getInstance().level ?: return 0
        return try {
            val enchantment = level.registryAccess().getOrThrow(enchantmentKey)
            stack.enchantments.getLevel(enchantment)
        } catch (_: Exception) {
            0
        }
    }

    private fun setDelay() {
        val (min, max) = delayAmount.value
        val actualDelay = if (min >= max) min else min + java.util.Random().nextInt(max - min + 1)
        nextActionTime = System.currentTimeMillis() + actualDelay
    }
}