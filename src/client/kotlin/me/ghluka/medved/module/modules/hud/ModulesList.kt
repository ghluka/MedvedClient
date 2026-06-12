package me.ghluka.medved.module.modules.hud

import me.ghluka.medved.config.entry.Color
import me.ghluka.medved.module.HudModule
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.ModuleManager
import me.ghluka.medved.module.modules.other.Colour
import me.ghluka.medved.module.modules.other.Font
import me.ghluka.medved.module.modules.player.ClientBrand
import me.ghluka.medved.util.CORNER_BL
import me.ghluka.medved.util.CORNER_BR
import me.ghluka.medved.util.CORNER_TL
import me.ghluka.medved.util.CORNER_TR
import me.ghluka.medved.util.CORNERS_LEFT
import me.ghluka.medved.util.CORNERS_RIGHT
import me.ghluka.medved.util.Text
import me.ghluka.medved.util.roundedFill
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.client.gui.GuiGraphicsExtractor

object ModulesList : HudModule("Modules List", "Shows all enabled modules") {

    enum class Sort   { SIZE, ALPHA, ORDER }
    enum class Capitalize { UPPER, LOWER, TITLE }
    enum class Shadow { NONE, SIMPLE, SOFT }

    val showVisuals    = boolean("show visuals", false)
    val leftBorder     = boolean("left border", true)
    val background     = boolean("background", true)
    val rounded        = boolean("rounded", false).also { it.visibleWhen = { background.value } }
    val roundRadius    = int("round radius", 3, 1, 5).also { it.visibleWhen = { rounded.value && background.value } }
    val bgColor        = color("bg color", Color(0, 0, 0, 160), allowAlpha = true)
    val sort           = enum("sort", Sort.SIZE)
    val reverseOrder   = boolean("reverse order", false)
    val capitalize     = enum("capitalize", Capitalize.LOWER)
    val shadow         = enum("shadow", Shadow.SOFT)
    val shadowStrength = int("shadow strength", 180, 40, 255).also { it.visibleWhen = { shadow.value != Shadow.NONE } }
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

    private fun shadowColor(alpha: Int): Int = argb(alpha.coerceIn(0, 255), 0, 0, 0)

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

        val rawWidths = mods.map { mod ->
            val nameW = font.width(Font.styledText(applyCase(mod.name)))
            val info = if (showConfig.value) applyCase(mod.hudInfo()) else ""
            val infoW = if (info.isNotEmpty()) 4 + font.width(Font.styledText(info)) else 0
            borderW + padX.value + nameW + infoW + padX.value
        }

        data class Group(val start: Int, val end: Int, val maxW: Int)
        val groups = mutableListOf<Group>()
        var i = 0
        while (i < rawWidths.size) {
            var j = i + 1
            var maxW = rawWidths[i]
            val anchor = rawWidths[i]

            while (j < rawWidths.size && kotlin.math.abs(rawWidths[j] - anchor) <= 2) {
                maxW = maxOf(maxW, rawWidths[j])
                j++
            }
            groups.add(Group(i, j, maxW))
            i = j
        }

        var ry = 0
        for (idx in mods.indices) {
            val mod = mods[idx]
            val displayName = applyCase(mod.name)
            val nameComp = Font.styledText(displayName)
            val nameW = font.width(nameComp)

            val info = if (showConfig.value) applyCase(mod.hudInfo()) else ""
            val infoComp = if (info.isNotEmpty()) Font.styledText(info) else null
            val infoW = if (infoComp != null) 4 + font.width(infoComp) else 0

            val group = groups.first { idx >= it.start && idx < it.end }
            val rowW = group.maxW
            val textY = ry + (ROW_H - FONT_H) / 2

            if (onRight) {
                val rightEdge = hudWidth()
                if (background.value) {
                    if (rounded.value) {
                        val corners = run {
                            val len = group.end - group.start
                            var c = 0
                            if (len == 1) {
                                if (group.start == 0) c = c or CORNER_TL
                                if (idx == group.end - 1) c = c or CORNER_BL
                            } else {
                                if (idx == group.start && group.start == 0) c = c or CORNER_TL
                                if (idx == group.end - 1) c = c or CORNER_BL
                            }
                            c
                        }
                        g.roundedFill(rightEdge - rowW, ry, rowW, ROW_H, roundRadius.value, bgColor.liveColor(bgColor.value).argb, corners)
                    } else {
                        g.fill(rightEdge - rowW, ry, rightEdge, ry + ROW_H, bgColor.liveColor(bgColor.value).argb)
                    }
                }
                if (leftBorder.value) g.fill(rightEdge - borderW, ry, rightEdge, ry + ROW_H, accentColor)
                if (infoComp != null) {
                    val infoX = rightEdge - borderW - padX.value - font.width(infoComp)
                    drawText(g, font, infoComp, infoX, textY, mod.hudInfoColor())
                    val nameX = infoX - 4 - nameW
                    drawText(g, font, nameComp, nameX, textY, argb(255, 215, 215, 228))
                } else {
                    val nameX = rightEdge - borderW - padX.value - nameW
                    drawText(g, font, nameComp, nameX, textY, argb(255, 215, 215, 228))
                }
            } else {
                if (background.value) {
                    if (rounded.value) {
                        val corners = run {
                            val len = group.end - group.start
                            var c = 0
                            if (len == 1) {
                                if (group.start == 0) c = c or CORNER_TR
                                if (idx == group.end - 1) c = c or CORNER_BR
                            } else {
                                if (idx == group.start && group.start == 0) c = c or CORNER_TR
                                if (idx == group.end - 1) c = c or CORNER_BR
                            }
                            c
                        }
                        g.roundedFill(0, ry, rowW, ROW_H, roundRadius.value, bgColor.liveColor(bgColor.value).argb, corners)
                    } else {
                        g.fill(0, ry, rowW, ry + ROW_H, bgColor.liveColor(bgColor.value).argb)
                    }
                }
                if (leftBorder.value) g.fill(0, ry, borderW, ry + ROW_H, accentColor)
                val nameX = borderW + padX.value
                drawText(g, font, nameComp, nameX, textY, argb(255, 215, 215, 228))
                if (infoComp != null) {
                    val infoX = nameX + nameW + 4
                    drawText(g, font, infoComp, infoX, textY, mod.hudInfoColor())
                }
            }

            ry += ROW_H
        }
    }

    private fun drawText(
        g: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        text: Component,
        x: Int,
        y: Int,
        color: Int
    ) {
        when (shadow.value) {
            Shadow.NONE -> {}
            Shadow.SIMPLE -> g.Text(font, text, x + 1, y + 1, shadowColor((shadowStrength.value * 0.75f).toInt()), false)
            Shadow.SOFT -> drawSoftShadow(g, font, text, x, y)
        }
        g.Text(font, text, x, y, color, false)
    }

    private fun drawSoftShadow(
        g: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        text: Component,
        x: Int,
        y: Int
    ) {
        val strength = shadowStrength.value
        val outer = (strength * 0.22f).toInt()
        val mid = (strength * 0.34f).toInt()
        val inner = (strength * 0.50f).toInt()

        g.Text(font, text, x - 1, y, shadowColor(mid), false)
        g.Text(font, text, x + 1, y, shadowColor(mid), false)
        g.Text(font, text, x, y - 1, shadowColor(mid), false)
        g.Text(font, text, x, y + 1, shadowColor(mid), false)

        g.Text(font, text, x - 1, y - 1, shadowColor(outer), false)
        g.Text(font, text, x + 1, y - 1, shadowColor(outer), false)
        g.Text(font, text, x - 1, y + 1, shadowColor(inner), false)
        g.Text(font, text, x + 1, y + 1, shadowColor(inner), false)

        g.Text(font, text, x, y + 2, shadowColor(outer), false)
    }
}

