package me.ghluka.medved.util

import net.minecraft.client.gui.GuiGraphicsExtractor

const val CORNER_TL   = 1
const val CORNER_TR   = 2
const val CORNER_BL   = 4
const val CORNER_BR   = 8
const val CORNERS_ALL  = 15
const val CORNERS_TOP  = CORNER_TL or CORNER_TR   // only top corners rounded
const val CORNERS_BOT  = CORNER_BL or CORNER_BR   // only bottom corners rounded
const val CORNERS_LEFT = CORNER_TL or CORNER_BL   // only left corners rounded

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

    val r4sq = 4 * rr * rr
    for (i in 0 until rr) {
        val ai = 2 * (rr - i) - 1  // left-side horizontal factor
        val bi = 2 * i + 1         // right-side horizontal factor
        for (j in 0 until rr) {
            val aj = 2 * (rr - j) - 1  // top vertical factor
            val bj = 2 * j + 1         // bottom vertical factor
            if (doTL && ai * ai + aj * aj < r4sq)
                fill(x + i,          y + j,           x + i + 1,          y + j + 1,          color)
            if (doTR && bi * bi + aj * aj < r4sq)
                fill(x + w - rr + i, y + j,           x + w - rr + i + 1, y + j + 1,          color)
            if (doBL && ai * ai + bj * bj < r4sq)
                fill(x + i,          y + h - rr + j,  x + i + 1,          y + h - rr + j + 1, color)
            if (doBR && bi * bi + bj * bj < r4sq)
                fill(x + w - rr + i, y + h - rr + j,  x + w - rr + i + 1, y + h - rr + j + 1, color)
        }
    }
}
