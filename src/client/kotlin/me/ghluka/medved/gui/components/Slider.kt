package me.ghluka.medved.gui.components

import me.ghluka.medved.gui.ClickGui
import me.ghluka.medved.gui.ClickGui.*
import net.minecraft.client.gui.GuiGraphicsExtractor
import me.ghluka.medved.config.entry.*
import kotlin.math.roundToInt

internal fun ClickGui.drawNumericSliderBar(
    g: GuiGraphicsExtractor,
    bounded: Boolean, v: Float, minV: Float, maxV: Float,
    x: Int, y: Int, w: Int
) {
    g.fill(x, y, x + w, y + ENT_H, ENT_BG)
    val barX = x + 2; val barW = w - 4
    if (bounded && barW > 0) {
        g.fill(barX, y + 2, barX + barW, y + ENT_H - 2, SLI_BG)
        val fraction = ((v - minV) / (maxV - minV)).coerceIn(0f, 1f)
        val filled = (fraction * barW).roundToInt()
        if (filled > 0) g.fill(barX, y + 2, barX + filled, y + ENT_H - 2, SLI_FG)
    }
}

internal fun ClickGui.drawRangeSliders(g: GuiGraphicsExtractor, entry: IntRangeEntry, x: Int, y: Int, w: Int) {
    g.fill(x, y, x + w, y + ENT_H, ENT_BG)
    val bx = x + 4; val bw = w - 8
    if (bw > 0) {
        g.fill(bx, y + 2, bx + bw, y + ENT_H - 2, SLI_BG)
        val range = (entry.max - entry.min).toFloat()
        val loFrac = ((entry.value.first - entry.min) / range).coerceIn(0f, 1f)
        val hiFrac = ((entry.value.second - entry.min) / range).coerceIn(0f, 1f)
        val loX = (loFrac * bw).roundToInt()
        val hiX = (hiFrac * bw).roundToInt()
        if (hiX > loX) g.fill(bx + loX, y + 2, bx + hiX, y + ENT_H - 2, SLI_FG)
        g.fill(bx + loX, y + 1, bx + loX + 2, y + ENT_H - 1, TEXT)
        g.fill(bx + hiX - 1, y + 1, bx + hiX + 1, y + ENT_H - 1, TEXT)
    }
}

internal fun ClickGui.drawFloatRangeSliders(g: GuiGraphicsExtractor, entry: FloatRangeEntry, x: Int, y: Int, w: Int) {
    g.fill(x, y, x + w, y + ENT_H, ENT_BG)
    val bx = x + 4; val bw = w - 8
    if (bw > 0) {
        g.fill(bx, y + 2, bx + bw, y + ENT_H - 2, SLI_BG)
        val range = entry.max - entry.min
        val loFrac = ((entry.value.first - entry.min) / range).coerceIn(0f, 1f)
        val hiFrac = ((entry.value.second - entry.min) / range).coerceIn(0f, 1f)
        val loX = (loFrac * bw).roundToInt()
        val hiX = (hiFrac * bw).roundToInt()
        if (hiX > loX) g.fill(bx + loX, y + 2, bx + hiX, y + ENT_H - 2, SLI_FG)
        g.fill(bx + loX, y + 1, bx + loX + 2, y + ENT_H - 1, TEXT)
        g.fill(bx + hiX - 1, y + 1, bx + hiX + 1, y + ENT_H - 1, TEXT)
    }
}

internal fun ClickGui.handleNumericBarClick(entry: ConfigEntry<*>, x: Int, y: Int, w: Int, mx: Int, btn: Int) {
    if (btn != 0) return
    val barX = x + 2; val barW = w - 4
    if (mx !in barX until barX + barW) return
    val t = ((mx - barX).toFloat() / barW).coerceIn(0f, 1f)
    when (entry) {
        is IntEntry -> if (entry.min != Int.MIN_VALUE && entry.max != Int.MAX_VALUE) {
            entry.value = (entry.min + t * (entry.max - entry.min)).roundToInt()
            draggingSlider = SliderDrag.Numeric(entry, barX, barW)
        }
        is FloatEntry -> if (entry.min != -Float.MAX_VALUE && entry.max != Float.MAX_VALUE) {
            entry.value = entry.min + t * (entry.max - entry.min)
            draggingSlider = SliderDrag.Numeric(entry, barX, barW)
        }
        is DoubleEntry -> if (entry.min != -Double.MAX_VALUE && entry.max != Double.MAX_VALUE) {
            entry.value = entry.min + t * (entry.max - entry.min)
            draggingSlider = SliderDrag.Numeric(entry, barX, barW)
        }
        else -> {}
    }
}

internal fun ClickGui.handleRangeClick(entry: IntRangeEntry, x: Int, y: Int, w: Int, mx: Int, my: Int): Boolean {
    if (my !in y until y + ENT_H) return false
    val bx = x + 4; val bw = w - 8
    if (mx !in bx until bx + bw) return false
    val t = ((mx - bx).toFloat() / bw).coerceIn(0f, 1f)
    val clickVal = entry.min + t * (entry.max - entry.min)
    val (lo, hi) = entry.value
    val mid = (lo + hi) / 2f
    val isHigh = clickVal >= mid
    val v = clickVal.roundToInt()
    entry.value = if (isHigh) lo to v.coerceAtLeast(lo) else v.coerceAtMost(hi) to hi
    draggingSlider = SliderDrag.Range(entry, isHigh, bx, bw)
    return true
}

internal fun ClickGui.handleFloatRangeClick(entry: FloatRangeEntry, x: Int, y: Int, w: Int, mx: Int, my: Int): Boolean {
    if (my !in y until y + ENT_H) return false
    val bx = x + 4; val bw = w - 8
    if (mx !in bx until bx + bw) return false
    val t = ((mx - bx).toFloat() / bw).coerceIn(0f, 1f)
    val scale = Math.pow(10.0, entry.decimals.toDouble()).toFloat()
    val clickVal = (Math.round((entry.min + t * (entry.max - entry.min)) * scale).toFloat()) / scale
    val (lo, hi) = entry.value
    val mid = (lo + hi) / 2f
    val isHigh = clickVal >= mid
    entry.value = if (isHigh) lo to clickVal.coerceAtLeast(lo) else clickVal.coerceAtMost(hi) to hi
    draggingSlider = SliderDrag.FloatRange(entry, isHigh, bx, bw)
    return true
}

