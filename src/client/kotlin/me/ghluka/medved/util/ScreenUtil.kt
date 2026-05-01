package me.ghluka.medved.util

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.util.ARGB
import net.minecraft.util.FormattedCharSequence

const val radius = 5

const val CORNER_TL   = 1
const val CORNER_TR   = 2
const val CORNER_BL   = 4
const val CORNER_BR   = 8
const val CORNERS_ALL  = 15
const val CORNERS_TOP  = CORNER_TL or CORNER_TR   // only top corners rounded
const val CORNERS_BOT  = CORNER_BL or CORNER_BR   // only bottom corners rounded
const val CORNERS_LEFT = CORNER_TL or CORNER_BL   // only left corners rounded

fun GuiGraphicsExtractor.Text(font: Font, str: FormattedCharSequence, x: Int, y: Int, color: Int, dropShadow: Boolean) {
    text(font, str, x, y, color, dropShadow)
}

fun GuiGraphicsExtractor.roundedFill(
    x: Int, y: Int, w: Int, h: Int,
    r: Int, color: Int,
    corners: Int = CORNERS_ALL
) {
    if (w <= 0 || h <= 0) return
    val rr = r.coerceAtMost(minOf(w, h)).coerceAtLeast(0)
    if (rr == 0) { fill(x, y, x + w, y + h, color); return }

    val doTL = corners and CORNER_TL != 0
    val doTR = corners and CORNER_TR != 0
    val doBL = corners and CORNER_BL != 0
    val doBR = corners and CORNER_BR != 0

    fill(x + rr, y, x + w - rr, y + h, color)

    if (w >= 2 * rr) {
        // Normal wide case: left and right corner zones don't overlap.
        val lTop = if (doTL) y + rr else y
        val lBot = if (doBL) y + h - rr else y + h
        if (lBot > lTop) fill(x, lTop, x + rr, lBot, color)
        if (!doTL) fill(x, y, x + rr, y + rr, color)
        if (!doBL) fill(x, y + h - rr, x + rr, y + h, color)

        val rTop = if (doTR) y + rr else y
        val rBot = if (doBR) y + h - rr else y + h
        if (rBot > rTop) fill(x + w - rr, rTop, x + w, rBot, color)
        if (!doTR) fill(x + w - rr, y, x + w, y + rr, color)
        if (!doBR) fill(x + w - rr, y + h - rr, x + w, y + h, color)
    } else {
        // Only fill the rows that are NOT inside a corner arc zone.
        val topY = if (doTL || doTR) y + rr else y
        val botY = if (doBL || doBR) y + h - rr else y + h
        if (botY > topY) fill(x, topY, x + w, botY, color)
        if (!(doTL || doTR)) fill(x, y, x + w, y + rr, color)
        if (!(doBL || doBR)) fill(x, y + h - rr, x + w, y + h, color)
    }

    if (!doTL && !doTR && !doBL && !doBR) return

    val baseA = (color ushr 24) and 0xFF
    val rgb   = color and 0x00FFFFFF
    val rrF   = rr.toFloat()

    for (i in 0 until rr) {
        val xi = i + 0.5f   // pixel-centre distance from outer edge   (used by TR / BR)
        val xo = rrF - xi   // pixel-centre distance from inner centre  (used by TL / BL)
        for (j in 0 until rr) {
            val yi = j + 0.5f
            val yo = rrF - yi
            if (doTL) {
                val cov = (rrF - kotlin.math.sqrt((xo * xo + yo * yo).toDouble()).toFloat() + 0.5f).coerceIn(0f, 1f)
                if (cov > 0f) {
                    val a = if (cov >= 1f) baseA else (baseA * cov + 0.5f).toInt()
                    fill(x + i, y + j, x + i + 1, y + j + 1, (a shl 24) or rgb)
                }
            }
            if (doTR) {
                val cov = (rrF - kotlin.math.sqrt((xi * xi + yo * yo).toDouble()).toFloat() + 0.5f).coerceIn(0f, 1f)
                if (cov > 0f) {
                    val a = if (cov >= 1f) baseA else (baseA * cov + 0.5f).toInt()
                    fill(x + w - rr + i, y + j, x + w - rr + i + 1, y + j + 1, (a shl 24) or rgb)
                }
            }
            if (doBL) {
                val cov = (rrF - kotlin.math.sqrt((xo * xo + yi * yi).toDouble()).toFloat() + 0.5f).coerceIn(0f, 1f)
                if (cov > 0f) {
                    val a = if (cov >= 1f) baseA else (baseA * cov + 0.5f).toInt()
                    fill(x + i, y + h - rr + j, x + i + 1, y + h - rr + j + 1, (a shl 24) or rgb)
                }
            }
            if (doBR) {
                val cov = (rrF - kotlin.math.sqrt((xi * xi + yi * yi).toDouble()).toFloat() + 0.5f).coerceIn(0f, 1f)
                if (cov > 0f) {
                    val a = if (cov >= 1f) baseA else (baseA * cov + 0.5f).toInt()
                    fill(x + w - rr + i, y + h - rr + j, x + w - rr + i + 1, y + h - rr + j + 1, (a shl 24) or rgb)
                }
            }
        }
    }
}

fun GuiGraphicsExtractor.TextHighlight(x0: Int, y0: Int, x1: Int, y1: Int, invertText: Boolean) {
    if (invertText) {
        fill(RenderPipelines.GUI_INVERT, x0, y0, x1, y1, -1)
    }

    fill(RenderPipelines.GUI_TEXT_HIGHLIGHT, x0, y0, x1, y1, -16776961)
}

fun GuiGraphicsExtractor.Text(font: Font, str: String?, x: Int, y: Int, color: Int) {
    Text(font, str, x, y, color, true)
}

fun GuiGraphicsExtractor.Text(font: Font, str: String?, x: Int, y: Int, color: Int, dropShadow: Boolean) {
    if (str != null) {
        Text(font, Language.getInstance().getVisualOrder(FormattedText.of(str)), x, y, color, dropShadow)
    }
}

fun GuiGraphicsExtractor.Text(font: Font, str: FormattedCharSequence, x: Int, y: Int, color: Int) {
    Text(font, str, x, y, color, true)
}

fun GuiGraphicsExtractor.Text(font: Font, str: Component, x: Int, y: Int, color: Int) {
    Text(font, str, x, y, color, true)
}

fun GuiGraphicsExtractor.Text(font: Font, str: Component, x: Int, y: Int, color: Int, dropShadow: Boolean) {
    Text(font, str.getVisualOrderText(), x, y, color, dropShadow)
}

fun GuiGraphicsExtractor.TextCentered(font: Font, str: String, x: Int, y: Int, color: Int) {
    Text(font, str, x - font.width(str) / 2, y, color)
}

fun GuiGraphicsExtractor.TextCentered(font: Font, text: Component, x: Int, y: Int, color: Int) {
    val toRender = text.getVisualOrderText()
    Text(font, toRender, x - font.width(toRender) / 2, y, color)
}

fun GuiGraphicsExtractor.TextCentered(font: Font, text: FormattedCharSequence, x: Int, y: Int, color: Int) {
    Text(font, text, x - font.width(text) / 2, y, color)
}

fun GuiGraphicsExtractor.TextWithWordWrap(font: Font, string: FormattedText, x: Int, y: Int, width: Int, col: Int) {
    TextWithWordWrap(font, string, x, y, width, col, true)
}

fun GuiGraphicsExtractor.TextWithWordWrap(font: Font, string: FormattedText, x: Int, y: Int, width: Int, col: Int, dropShadow: Boolean) {
    var y = y
    for (line in font.split(string, width)) {
        Text(font, line, x, y, col, dropShadow)
        y += 9
    }
}

fun GuiGraphicsExtractor.TextWithBackdrop(font: Font, str: Component, textX: Int, textY: Int, textWidth: Int, textColor: Int) {
    val backgroundColor: Int = Minecraft.getInstance().options.getBackgroundColor(0.0f)
    if (backgroundColor != 0) {
        val padding = 2
        fill(textX - 2, textY - 2, textX + textWidth + 2, textY + 9 + 2, ARGB.multiply(backgroundColor, textColor))
    }

    Text(font, str, textX, textY, textColor, true)
}