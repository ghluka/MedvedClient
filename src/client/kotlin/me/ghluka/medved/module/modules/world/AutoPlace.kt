package me.ghluka.medved.module.modules.world

import com.mojang.blaze3d.platform.InputConstants
import me.ghluka.medved.config.entry.ItemListEntry
import me.ghluka.medved.gui.components.itemCategories
import me.ghluka.medved.module.Module
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.phys.BlockHitResult
import org.lwjgl.glfw.GLFW

object AutoPlace : Module(
    name = "Auto Place",
    description = "Automatically places blocks when looking at a block surface",
    category = Category.WORLD,
) {
    private val requireHolding = boolean("require holding", true)
    private val onlyAir  = boolean("only air", true)
    private val blockWhitelist = itemList("Block Whitelist", listOf("wool_category"), defaultMode = ItemListEntry.Mode.WHITELIST, filter = ItemListEntry.Filter.BLOCKS_ONLY)

    private var armUseNextTick = false

    init {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (!isEnabled()) return@register
            tickAutoPlace(client)
        }
    }

    override fun onDisabled() {
        armUseNextTick = false
        val opts = Minecraft.getInstance().options
        opts.keyUse.setDown(isPhysicalKeyDown(opts.keyUse))
    }

    private fun tickAutoPlace(client: Minecraft) {
        val player = client.player ?: run {
            armUseNextTick = false
            return
        }
        if (client.screen != null) {
            armUseNextTick = false
            return
        }

        val physicalUseDown = isPhysicalKeyDown(client.options.keyUse)
        if (requireHolding.value && !physicalUseDown) {
            armUseNextTick = false
            return
        }

        val stack = player.mainHandItem
        if (stack.isEmpty || stack.item !is BlockItem) {
            armUseNextTick = false
            return
        }

        if (blockWhitelist.value.isNotEmpty()) {
            val itemName = stack.item.descriptionId.lowercase()
            if (!itemMatchesWhitelist(itemName, blockWhitelist.value)) {
                armUseNextTick = false
                return
            }
        }

        val level = client.level ?: run {
            armUseNextTick = false
            return
        }
        val hit = client.hitResult as? BlockHitResult ?: run {
            armUseNextTick = false
            return
        }

        val hitPos = hit.blockPos
        val hitState = level.getBlockState(hitPos)
        if (!hitState.isSolid) {
            armUseNextTick = false
            return
        }

        val placePos = hitPos.relative(hit.direction)
        if (onlyAir.value && !level.getBlockState(placePos).isAir) {
            armUseNextTick = false
            return
        }

        client.options.keyUse.setDown(false)

        if (!armUseNextTick) {
            armUseNextTick = true
            return
        }

        val result = client.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hit) ?: return
        if (result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND)
        }
    }

    private fun isPhysicalKeyDown(mapping: KeyMapping): Boolean {
        if (mapping.isUnbound) return false
        val window = Minecraft.getInstance().window.handle()
        val key = InputConstants.getKey(mapping.saveString())
        return if (key.type == InputConstants.Type.MOUSE) {
            GLFW.glfwGetMouseButton(window, key.value) == GLFW.GLFW_PRESS
        } else {
            GLFW.glfwGetKey(window, key.value) == GLFW.GLFW_PRESS
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
