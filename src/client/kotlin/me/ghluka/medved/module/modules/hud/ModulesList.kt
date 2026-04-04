package me.ghluka.medved.module.modules.hud

import me.ghluka.medved.config.entry.Color
import me.ghluka.medved.module.HudModule
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.ModuleManager
import me.ghluka.medved.module.modules.other.Colour
import me.ghluka.medved.module.modules.other.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

object ModulesList : HudModule("Modules List", "Shows all enabled modules") {

    enum class Sort   { SIZE, ALPHA, ORDER }
    enum class Capitalize { UPPER, LOWER, TITLE }

    val showVisuals    = boolean("show visuals", false)
    val leftBorder     = boolean("left border", true)
    val background     = boolean("background", true)
    val bgColor        = color("bg color", Color(0, 0, 0, 160), allowAlpha = true)
    val textShadow     = boolean("text shadow", false)
    val sort           = enum("sort", Sort.ORDER)
    val capitalize     = enum("capitalize", Capitalize.TITLE)
    val padX           = int("pad x", 2, 0, 6)
    val padY           = int("pad y", 3, 0, 6)

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
                (it.category != Module.Category.HUD && it.category != Module.Category.RENDER)
            }
        mods = when (sort.value) {
            Sort.SIZE  -> mods.sortedByDescending { font.width(Font.styledText(applyCase(it.name))) }
            Sort.ALPHA -> mods.sortedBy { it.name }
            Sort.ORDER -> mods
        }
        return mods
    }

    override fun hudWidth(): Int {
        val font = Font.getFont()
        val mods = activeModules()
        if (mods.isEmpty()) return 60
        return mods.maxOf {
            font.width(Font.styledText(applyCase(it.name))) + padX.value * 2 +
                (if (leftBorder.value) BORDER_W else 0)
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
        val accentColor = Colour.accent.value.argb

        var ry = 0
        for (mod in mods) {
            val displayName = applyCase(mod.name)
            val comp = Font.styledText(displayName)
            val textW = font.width(comp)

            val rowW = borderW + padX.value + textW + padX.value

            if (background.value) {
                g.fill(0, ry, rowW, ry + ROW_H, bgColor.value.argb)
            }
            if (leftBorder.value) {
                g.fill(0, ry, borderW, ry + ROW_H, accentColor)
            }

            val textX = borderW + padX.value
            val textY = ry + (ROW_H - FONT_H) / 2
            if (textShadow.value) {
                g.text(font, comp, textX + 1, textY + 1, argb(160, 0, 0, 0))
            }
            g.text(font, comp, textX, textY, argb(255, 215, 215, 228))

            ry += ROW_H
        }
    }
}

