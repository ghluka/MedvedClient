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
import kotlin.math.roundToInt

const val radius = 5

const val CORNER_TL   = 1
const val CORNER_TR   = 2
const val CORNER_BL   = 4
const val CORNER_BR   = 8
const val CORNERS_ALL  = 15
const val CORNERS_TOP  = CORNER_TL or CORNER_TR   // only top corners rounded
const val CORNERS_BOT  = CORNER_BL or CORNER_BR   // only bottom corners rounded
const val CORNERS_LEFT = CORNER_TL or CORNER_BL   // only left corners rounded
const val CORNERS_RIGHT = CORNER_TR or CORNER_BR   // only right corners rounded

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

        val rTop = if (doTR) y + rr else y
        val rBot = if (doBR) y + h - rr else y + h
        if (rBot > rTop) fill(x + w - rr, rTop, x + w, rBot, color)
    } else {
        // Only fill the rows that are NOT inside a corner arc zone.
        val topY = if (doTL || doTR) y + rr else y
        val botY = if (doBL || doBR) y + h - rr else y + h
        if (botY > topY) fill(x, topY, x + w, botY, color)
    }

    if (!doTL && !doTR && !doBL && !doBR) return

    drawRoundedCornerPixels(x, y, w, h, rr, color, doTL, doTR, doBL, doBR)
}

private fun GuiGraphicsExtractor.drawRoundedCornerPixels(
    x: Int,
    y: Int,
    w: Int,
    h: Int,
    r: Int,
    color: Int,
    doTL: Boolean,
    doTR: Boolean,
    doBL: Boolean,
    doBR: Boolean,
) {
    val scale = Minecraft.getInstance().window.guiScale.toFloat().coerceAtLeast(1f)
    val baseA = (color ushr 24) and 0xFF
    val rgb = color and 0x00FFFFFF
    val radiusPx = (r * scale).roundToInt().coerceAtLeast(1)
    val radiusPxF = radiusPx.toFloat()

    pose().pushMatrix()
    if (scale != 1f) pose().scale(1f / scale, 1f / scale)

    fun guiToPixel(value: Int): Int = (value * scale).roundToInt()

    fun drawPixel(px: Int, py: Int, coverage: Float) {
        if (coverage <= 0f) return
        val alpha = if (coverage >= 1f) baseA else (baseA * coverage + 0.5f).toInt()
        fill(px, py, px + 1, py + 1, (alpha shl 24) or rgb)
    }

    val leftPx = guiToPixel(x)
    val rightPx = guiToPixel(x + w - r)
    val topPx = guiToPixel(y)
    val bottomPx = guiToPixel(y + h - r)

    fun drawCorner(originX: Int, originY: Int, flipX: Boolean, flipY: Boolean) {
        for (j in 0 until radiusPx) {
            val yCenter = j + 0.5f
            val yDistance = radiusPxF - yCenter
            val boundary = radiusPxF - kotlin.math.sqrt(
                (radiusPxF * radiusPxF - yDistance * yDistance).coerceAtLeast(0f).toDouble()
            ).toFloat()
            val firstFull = kotlin.math.ceil(boundary + 0.5f).toInt().coerceIn(0, radiusPx)
            val rowY = if (flipY) originY + radiusPx - 1 - j else originY + j

            if (firstFull < radiusPx) {
                if (flipX) {
                    fill(originX, rowY, originX + radiusPx - firstFull, rowY + 1, color)
                } else {
                    fill(originX + firstFull, rowY, originX + radiusPx, rowY + 1, color)
                }
            }

            val aaIndex = firstFull - 1
            if (aaIndex >= 0) {
                val coverage = (aaIndex + 0.5f - boundary + 0.5f).coerceIn(0f, 1f)
                val rowX = if (flipX) originX + radiusPx - 1 - aaIndex else originX + aaIndex
                drawPixel(rowX, rowY, coverage)
            }
        }
    }

    if (doTL) drawCorner(leftPx, topPx, flipX = false, flipY = false)
    if (doTR) drawCorner(rightPx, topPx, flipX = true, flipY = false)
    if (doBL) drawCorner(leftPx, bottomPx, flipX = false, flipY = true)
    if (doBR) drawCorner(rightPx, bottomPx, flipX = true, flipY = true)

    pose().popMatrix()
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
