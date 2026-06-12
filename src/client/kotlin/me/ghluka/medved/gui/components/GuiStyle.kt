package me.ghluka.medved.gui.components

import me.ghluka.medved.gui.ClickGui
import me.ghluka.medved.util.CORNERS_BOT
import me.ghluka.medved.util.Text
import me.ghluka.medved.util.TextCentered
import me.ghluka.medved.util.roundedFill
import net.minecraft.client.gui.GuiGraphicsExtractor

internal fun ClickGui.drawRowSurface(
    g: GuiGraphicsExtractor,
    x: Int,
    y: Int,
    w: Int,
    h: Int = ENT_H,
    hovered: Boolean = false,
    selected: Boolean = false,
    bottomRounded: Boolean = false,
) {
    val bg = when {
        selected -> shade(42, 0.18f)
        hovered -> shade(32, 0.12f)
        else -> ENT_BG
    }
    if (bottomRounded) {
        g.roundedFill(x, y, w, h, 4, bg, CORNERS_BOT)
    } else {
        g.fill(x, y, x + w, y + h, bg)
    }
    if (selected) {
        if (bottomRounded) {
            g.fill(x, y, x + 2, y + h - 2, ACCENT)
            g.fill(x + 1, y + h - 2, x + 2, y + h - 1, ACCENT)
        } else {
            g.fill(x, y, x + 2, y + h, ACCENT)
        }
    }
}

internal fun ClickGui.drawControlSurface(
    g: GuiGraphicsExtractor,
    x: Int,
    y: Int,
    w: Int,
    h: Int,
    active: Boolean = false,
    hovered: Boolean = false,
) {
    val bg = when {
        active -> shade(45, 0.25f)
        hovered -> shade(42, 0.18f)
        else -> BTN_BG
    }
    g.roundedFill(x, y, w, h, 2, bg)
    if (active) g.fill(x, y + h - 1, x + w, y + h, ACCENT)
}

internal fun ClickGui.drawValueText(g: GuiGraphicsExtractor, text: String, x: Int, y: Int, w: Int) {
    val comp = styled(text)
    g.Text(guiFont, comp, x + w - guiFont.width(comp) - 4, y + (ENT_H - 8) / 2, TEXT)
}

internal fun ClickGui.drawToggle(g: GuiGraphicsExtractor, x: Int, y: Int, on: Boolean) {
    val w = 22
    val h = 10
    val trackY = y + (ENT_H - h) / 2
    val trackColor = if (on) {
        val accent = ACCENT
        val r = (accent ushr 16) and 0xFF
        val g2 = (accent ushr 8) and 0xFF
        val b = accent and 0xFF
        argb(105, r, g2, b)
    } else {
        shade(48, 0.12f)
    }
    g.roundedFill(x, trackY, w, h, h / 2, trackColor)
    g.fill(x + 3, trackY + h / 2, x + w - 3, trackY + h / 2 + 1, if (on) ACCENT else TEXT_DIM)

    val knobSize = 6
    val knobX = if (on) x + w - knobSize - 2 else x + 2
    val knobY = trackY + 2
    g.roundedFill(knobX, knobY, knobSize, knobSize, knobSize / 2, if (on) ACCENT else shade(140, 0.10f))
}
