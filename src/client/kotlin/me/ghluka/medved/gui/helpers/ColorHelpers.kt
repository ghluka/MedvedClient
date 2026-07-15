package me.ghluka.medved.gui.helpers

import me.ghluka.medved.config.entry.Color
import me.ghluka.medved.config.entry.ColorEntry
import me.ghluka.medved.gui.ClickGui
import kotlin.math.roundToInt

internal fun ClickGui.commitEditingColor() {
    val entry = editingColorEntry ?: return
    val text = entryField.text.trim()
    if (editingColorHex) {
        if (text.matches(Regex("#?[0-9a-fA-F]{8}"))) {
            val hex = text.removePrefix("#")
            val a = hex.substring(0, 2).toInt(16)
            val r = hex.substring(2, 4).toInt(16)
            val g = hex.substring(4, 6).toInt(16)
            val b = hex.substring(6, 8).toInt(16)
            entry.customValue = entry.customValue.copy(a = a, r = r, g = g, b = b)
        }
    } else {
        val channel = editingColorChannel ?: return
        val newValue = text.toIntOrNull()?.coerceIn(0, 255) ?: return
        val col = entry.customValue
        entry.customValue = when (channel) {
            0 -> col.copy(r = newValue)
            1 -> col.copy(g = newValue)
            2 -> col.copy(b = newValue)
            3 -> col.copy(a = newValue)
            else -> col
        }
    }
    colorPickerCustomValue = entry.customValue
    if (entry.pickerMode == ColorEntry.PickerMode.CHROMA) {
        val (_, saturation, brightness) = rgbToHsv(entry.customValue)
        entry.chromaSaturation = saturation
        entry.chromaBrightness = brightness
    }
    applyDynamicColorState(entry)
}

internal fun ClickGui.liveColorFor(entry: ColorEntry): Color {
    if (entry.pickerMode != ColorEntry.PickerMode.CHROMA) return entry.value
    val hue = ((ColorEntry.chromaTimeSeconds() * entry.chromaSpeed * 360f) % 360f + 360f) % 360f
    val rgb = hsvToRgb(hue, entry.chromaSaturation, entry.chromaBrightness)
    return rgb.copy(a = entry.customValue.a)
}

internal fun ClickGui.rgbToHsv(color: Color): Triple<Float, Float, Float> {
    val r = color.r / 255f
    val g = color.g / 255f
    val b = color.b / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> ((g - b) / delta % 6f) * 60f
        max == g -> ((b - r) / delta + 2f) * 60f
        else -> ((r - g) / delta + 4f) * 60f
    }.let { if (it < 0f) it + 360f else it }
    val saturation = if (max == 0f) 0f else delta / max
    return Triple(hue, saturation, max)
}

internal fun ClickGui.hsvToRgb(hue: Float, saturation: Float, value: Float): Color {
    val c = value * saturation
    val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
    val m = value - c
    val (r1, g1, b1) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        ((r1 + m) * 255f).roundToInt().coerceIn(0, 255),
        ((g1 + m) * 255f).roundToInt().coerceIn(0, 255),
        ((b1 + m) * 255f).roundToInt().coerceIn(0, 255),
        255,
    )
}
