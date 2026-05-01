package me.ghluka.medved.gui.components

import me.ghluka.medved.gui.ClickGui
import net.minecraft.client.gui.GuiGraphicsExtractor
import me.ghluka.medved.config.entry.*
import me.ghluka.medved.module.Module
import me.ghluka.medved.util.Text

internal fun ClickGui.drawEnumDropdown(g: GuiGraphicsExtractor, entry: EnumEntry<*>, x: Int, y: Int, w: Int, mx: Int, my: Int) {
    for ((i, c) in entry.constants.withIndex()) {
        val ry = y + i * ENT_H
        val hov = mx in x until x + w && my in ry until ry + ENT_H
        val selected = c == entry.value
        g.fill(x, ry, x + w, ry + ENT_H, if (hov) MOD_HOV else ENT_BG)
        if (selected) g.fill(x, ry, x + 2, ry + ENT_H, ACCENT)
        g.Text(guiFont, styled(fmtLabel(c.name)), x + 5, ry + (ENT_H - 8) / 2, if (selected) TEXT else TEXT_DIM)
    }
}

internal fun ClickGui.enumButtonWidth(entry: EnumEntry<*>): Int {
    val labelW = guiFont.width(styled(fmtLabel(entry.value.name)))
    return (labelW + 16).coerceAtMost(PNL_W - 10)
}

internal fun ClickGui.enumDropdownWidth(entry: EnumEntry<*>): Int {
    val maxLabel = entry.constants.maxOf { guiFont.width(styled(fmtLabel(it.name))) }
    return (maxLabel + 16).coerceAtMost(PNL_W - 10)
}

internal fun ClickGui.constrainDropdownScroll() {
    if (me.ghluka.medved.module.modules.other.ClickGui.currentMode.value !=
        me.ghluka.medved.module.modules.other.ClickGui.Mode.DROPDOWN) return
    var maxExpandedY = 0
    for (cat in Module.Category.entries) {
        val py = positions[cat]?.second ?: continue
        val h = if (cat in collapsed) HDR_H else fullPanelHeight(cat)
        val unscrolledBottomY = py + h
        if (unscrolledBottomY > maxExpandedY) maxExpandedY = unscrolledBottomY
    }
    val cfgH = if (cfgPanelCollapsed) HDR_H else HDR_H + cfgPanelBodyH()
    val cfgUnscrolledBottomY = cfgPanelY + cfgH
    if (cfgUnscrolledBottomY > maxExpandedY) maxExpandedY = cfgUnscrolledBottomY

    val viewHeight = height - 50
    val maxScrollOffset = Math.max(0, maxExpandedY - viewHeight)

    if (dropdownScroll > maxScrollOffset) {
        dropdownScroll = maxScrollOffset
    }
    if (dropdownScroll < 0) {
        dropdownScroll = 0
    }
}

internal fun ClickGui.handleEnumDropdownClick(entry: EnumEntry<*>, x: Int, y: Int, w: Int, mx: Int, my: Int): Boolean {
    if (mx !in x until x + w) return false
    for ((i, _) in entry.constants.withIndex()) {
        val ry = y + i * ENT_H
        if (my in ry until ry + ENT_H) {
            entry.setByIndex(i)
            expandedEnum = null
            return true
        }
    }
    return false
}

