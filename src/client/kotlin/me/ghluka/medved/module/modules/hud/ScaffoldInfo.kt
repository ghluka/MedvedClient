package me.ghluka.medved.module.modules.hud

import me.ghluka.medved.config.entry.Color
import me.ghluka.medved.module.HudModule
import me.ghluka.medved.module.modules.other.Font
import me.ghluka.medved.module.modules.world.Clutch
import me.ghluka.medved.module.modules.world.Scaffold
import me.ghluka.medved.util.roundedFill
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack

object ScaffoldInfo : HudModule("Block Counter", "Displays hotbar block count for the item in hand") {
    private val bgColor = color("bg color", Color(0, 0, 0, 140), allowAlpha = true)
    private val textColor = color("text color", Color(255, 255, 255, 255))
    private val scaffOnly = boolean("only on scaffold", true)

    private val padding = 4
    private val iconSize = 12
    private val iconGap = 3

    init {
        hudX.value = 0.5f
        hudX.defaultValue = 0.5f
        hudY.value = 0.52f
        hudY.defaultValue = 0.52f
        enable()
    }

    override fun renderHudElement(g: GuiGraphicsExtractor) {
        if (scaffOnly.value && (!Scaffold.isEnabled() && !Clutch.isEnabled())) return
        
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val handStack = player.mainHandItem
        if (handStack.item !is BlockItem) return

        val font = Font.getFont()
        val countText = getText(handStack)
        val textComp = Font.styledText(countText)

        val textWidth = font.width(textComp)
        val totalWidth = textWidth + (padding * 2) + iconSize + iconGap
        val height = font.lineHeight + (padding * 2)

        val px = hudX.value - (totalWidth / 2)
        val py = hudY.value

        g.pose().pushMatrix()
        g.pose().translate(px, py)

        g.roundedFill(0, 0, totalWidth, height, 4, bgColor.liveColor(bgColor.value).argb)

        val iconY = font.lineHeight + padding - iconSize
        g.item(handStack, padding, iconY)

        val textX = padding + iconSize + iconGap
        val textY = padding
        g.text(font, textComp, textX, textY, textColor.liveColor(textColor.value).argb)

        g.pose().popMatrix()
    }

    override fun hudWidth(): Int = 20
    override fun hudHeight(): Int = 20

    fun getText(handStack: ItemStack): String {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return ""
        if (handStack.item !is BlockItem) return ""
        
        var count = 0
        for (i in 0..8) {
            val stack = player.inventory.getItem(i)
            if (!stack.isEmpty && stack.item == handStack.item) {
                count += stack.count
            }
        }
        return " $count"
    }
}