package me.ghluka.medved.module.modules.hud

import me.ghluka.medved.config.entry.Color
import me.ghluka.medved.module.HudModule
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.ModuleManager
import me.ghluka.medved.module.modules.other.Colour
import me.ghluka.medved.module.modules.other.Font
import me.ghluka.medved.module.modules.player.ClientBrand
import me.ghluka.medved.util.Text
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object ModulesList : HudModule("Modules List", "Shows all enabled modules") {

    enum class Sort   { SIZE, ALPHA, ORDER }
    enum class Capitalize { UPPER, LOWER, TITLE }

    val showVisuals    = boolean("show visuals", false)
    val leftBorder     = boolean("left border", true)
    val background     = boolean("background", true)
    val bgColor        = color("bg color", Color(0, 0, 0, 160), allowAlpha = true)
    val textShadow     = boolean("text shadow", false)
    val sort           = enum("sort", Sort.SIZE)
    val reverseOrder   = boolean("reverse order", false)
    val capitalize     = enum("capitalize", Capitalize.LOWER)
    val padX           = int("pad x", 2, 0, 6)
    val padY           = int("pad y", 3, 0, 6)
    val showConfig     = boolean("show config", true)

    private const val FONT_H = 8
    private val ROW_H get() = FONT_H + padY.value * 2
    private const val BORDER_W = 2

    init {
        hudX.value = 0f
        hudY.value = 0f
        enable()
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun applyCase(name: String) = when (capitalize.value) {
        Capitalize.UPPER -> name.uppercase()
        Capitalize.LOWER -> name.lowercase()
        Capitalize.TITLE -> name
    }

    private fun activeModules(): List<Module> {
        val font = Font.getFont()
        var mods = ModuleManager.getAll()
            .filter { it !== this }
            .filter { it.isEnabled() }
            .filter { it.showInModulesList }
            .filter {
                showVisuals.value ||
                (it.category != Module.Category.HUD && it.category != Module.Category.RENDER && it::class != ClientBrand::class)
            }
        mods = when (sort.value) {
            Sort.SIZE         -> mods.sortedByDescending {
                val nameW = font.width(Font.styledText(applyCase(it.name)))
                val info = if (showConfig.value) applyCase(it.hudInfo()) else ""
                val infoW = if (info.isNotEmpty()) 4 + font.width(Font.styledText(info)) else 0
                nameW + infoW
            }
            Sort.ALPHA        -> mods.sortedBy { it.name }
            Sort.ORDER        -> mods
        }
        if (reverseOrder.value) mods = mods.reversed()
        return mods
    }

    override fun hudPixelX(screenW: Int): Int {
        val px = (hudX.value * screenW).toInt()
        return if (hudX.value > 0.5f) px - hudWidth() else px
    }

    override fun hudXFromLeftPixel(leftPx: Int, screenW: Int): Float =
        if (hudX.value > 0.5f) (leftPx + hudWidth()).toFloat() / screenW
        else leftPx.toFloat() / screenW

    override fun onHudRender(extractor: GuiGraphicsExtractor, delta: DeltaTracker) {
        val mc = Minecraft.getInstance()
        val px = hudPixelX(mc.window.guiScaledWidth)
        val py = (hudY.value * mc.window.guiScaledHeight).toInt()
        val sc = hudScale.value
        extractor.pose().pushMatrix()
        extractor.pose().translate(px.toFloat(), py.toFloat())
        if (sc != 1.0f) extractor.pose().scale(sc, sc)
        renderHudElement(extractor)
        extractor.pose().popMatrix()
    }

    override fun hudWidth(): Int {
        val font = Font.getFont()
        val mods = activeModules()
        if (mods.isEmpty()) return 60
        val borderW = if (leftBorder.value) BORDER_W else 0
        return mods.maxOf { mod ->
            val nameW = font.width(Font.styledText(applyCase(mod.name)))
            val info = if (showConfig.value) applyCase(mod.hudInfo()) else ""
            val infoW = if (info.isNotEmpty()) 4 + font.width(Font.styledText(info)) else 0
            borderW + padX.value + nameW + infoW + padX.value
        }
    }

    override fun hudHeight(): Int {
        val mods = activeModules()
        return if (mods.isEmpty()) ROW_H else mods.size * ROW_H
    }

    override fun renderHudElement(g: GuiGraphicsExtractor) {
        val mods = activeModules()
        if (mods.isEmpty()) return

        val font = Font.getFont()
        val borderW = if (leftBorder.value) BORDER_W else 0
        val accentColor = Colour.accent.liveColor(Colour.accent.value).argb
        val onRight = hudX.value > 0.5f

        var ry = 0
        for (mod in mods) {
            val displayName = applyCase(mod.name)
            val nameComp = Font.styledText(displayName)
            val nameW = font.width(nameComp)

            val info = if (showConfig.value) applyCase(mod.hudInfo()) else ""
            val infoComp = if (info.isNotEmpty()) Font.styledText(info) else null
            val infoW = if (infoComp != null) 4 + font.width(infoComp) else 0

            val rowW = borderW + padX.value + nameW + infoW + padX.value
            val textY = ry + (ROW_H - FONT_H) / 2

            if (onRight) {
                val rightEdge = hudWidth()
                if (background.value) g.fill(rightEdge - rowW, ry, rightEdge, ry + ROW_H, bgColor.liveColor(bgColor.value).argb)
                if (leftBorder.value) g.fill(rightEdge - borderW, ry, rightEdge, ry + ROW_H, accentColor)
                if (infoComp != null) {
                    val infoX = rightEdge - borderW - padX.value - font.width(infoComp)
                    if (textShadow.value) g.Text(font, infoComp, infoX + 1, textY + 1, argb(160, 0, 0, 0))
                    g.Text(font, infoComp, infoX, textY, mod.hudInfoColor())
                    val nameX = infoX - 4 - nameW
                    if (textShadow.value) g.Text(font, nameComp, nameX + 1, textY + 1, argb(160, 0, 0, 0))
                    g.Text(font, nameComp, nameX, textY, argb(255, 215, 215, 228))
                } else {
                    val nameX = rightEdge - borderW - padX.value - nameW
                    if (textShadow.value) g.Text(font, nameComp, nameX + 1, textY + 1, argb(160, 0, 0, 0))
                    g.Text(font, nameComp, nameX, textY, argb(255, 215, 215, 228))
                }
            } else {
                if (background.value) g.fill(0, ry, rowW, ry + ROW_H, bgColor.liveColor(bgColor.value).argb)
                if (leftBorder.value) g.fill(0, ry, borderW, ry + ROW_H, accentColor)
                val nameX = borderW + padX.value
                if (textShadow.value) g.Text(font, nameComp, nameX + 1, textY + 1, argb(160, 0, 0, 0))
                g.Text(font, nameComp, nameX, textY, argb(255, 215, 215, 228))
                if (infoComp != null) {
                    val infoX = nameX + nameW + 4
                    if (textShadow.value) g.Text(font, infoComp, infoX + 1, textY + 1, argb(160, 0, 0, 0))
                    g.Text(font, infoComp, infoX, textY, mod.hudInfoColor())
                }
            }

            ry += ROW_H
        }
    }
}

