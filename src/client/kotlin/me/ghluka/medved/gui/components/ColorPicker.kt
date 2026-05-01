package me.ghluka.medved.gui.components

import me.ghluka.medved.gui.ClickGui
import me.ghluka.medved.gui.ClickGui.*
import net.minecraft.client.gui.GuiGraphicsExtractor
import me.ghluka.medved.config.entry.*
import kotlin.math.roundToInt
import me.ghluka.medved.util.roundedFill
import me.ghluka.medved.util.Text

internal fun ClickGui.drawColorPicker(g: GuiGraphicsExtractor, entry: ColorEntry, x: Int, y: Int, w: Int, mx: Int, my: Int) {
    val themeAllowed = supportsThemeMode(entry)
    if (!themeAllowed && entry.pickerMode == ColorEntry.PickerMode.THEME) entry.pickerMode = ColorEntry.PickerMode.CUSTOM
    val isChroma = entry.pickerMode == ColorEntry.PickerMode.CHROMA
    val baseCustom = entry.customValue
    val (baseHue, baseSat, baseValue) = rgbToHsv(baseCustom)
    val hue = if (isChroma) ((ColorEntry.chromaTimeSeconds() * entry.chromaSpeed * 360f) % 360f + 360f) % 360f else baseHue
    val sat = if (isChroma) entry.chromaSaturation else baseSat
    val value = if (isChroma) entry.chromaBrightness else baseValue
    val liveColor = liveColorFor(entry)

    val draggingMap = draggingSlider is SliderDrag.ColorMap && (draggingSlider as SliderDrag.ColorMap).entry == entry
    if (isChroma && !draggingMap && editingColorEntry != entry) {
        applyDynamicColorState(entry)
    }

    val padding = 6
    val modeH = 14
    val mapH = 65
    val barW = 10
    val barGap = 4
    val fieldH = 14
    val fieldGap = 4

    val actualW = w.coerceAtMost(160).coerceAtLeast(135)
    val x0 = x + (w - actualW) / 2
    val hasAlpha = entry.allowAlpha

    val options = if (themeAllowed) {
        listOf(ColorEntry.PickerMode.CUSTOM, ColorEntry.PickerMode.THEME, ColorEntry.PickerMode.CHROMA)
    } else {
        listOf(ColorEntry.PickerMode.CUSTOM, ColorEntry.PickerMode.CHROMA)
    }
    val compactThemeH = padding + modeH + 6 + guiFont.lineHeight + 4 + 16 + padding
    val speedAreaH = if (isChroma) (guiFont.lineHeight + 2 + fieldGap + fieldH) else 0
    val fullPickerH = if (isChroma) {
        padding + modeH + 6 + mapH + 6 + speedAreaH + padding
    } else {
        padding + modeH + 6 + mapH + 6 + fieldH + fieldGap + fieldH + padding
    }
    val optionsH = if (colorPickerModeExpanded) options.size * (modeH + 2) + 2 else 0
    val overlayBaseH = if (entry.pickerMode == ColorEntry.PickerMode.THEME) compactThemeH else fullPickerH

    // options are rendered as an overlay and should not increase the base picker height
    colorPickerH = overlayBaseH
    colorPickerX = x0
    colorPickerY = y
    colorPickerW = actualW

    g.roundedFill(x0, y, actualW, overlayBaseH, 6, PNL_BG)
    g.fill(x0, y, x0 + actualW, y + padding + modeH + 4, shade(20, 0.12f))

    val modeBtnW = actualW - padding * 2
    val modeX = x0 + padding
    val modeY = y + padding
    val modeHover = mx in modeX until modeX + modeBtnW && my in modeY until modeY + modeH
    g.fill(modeX, modeY, modeX + modeBtnW, modeY + modeH, if (modeHover) shade(50, 0.24f) else BTN_BG)
    val modeTextY = modeY + (modeH - guiFont.lineHeight) / 2
    g.Text(guiFont, styled(entry.pickerMode.name.lowercase().replaceFirstChar { it.uppercase() }), modeX + 6, modeTextY, TEXT)
    g.Text(guiFont, jbMono(if (colorPickerModeExpanded) "▲" else "▼"), modeX + modeBtnW - 12, modeTextY, TEXT_DIM)

    if (entry.pickerMode == ColorEntry.PickerMode.THEME) {
        val previewTop = y + padding + modeH + 10
        g.Text(guiFont, styled("Using theme color"), x0 + padding, previewTop, TEXT_DIM)
        val pY = previewTop + guiFont.lineHeight + 4
        g.roundedFill(x0 + padding, pY, actualW - padding * 2, 16, 2, argb(liveColor.a, liveColor.r, liveColor.g, liveColor.b))

        if (colorPickerModeExpanded) {
            val optionY = modeY + modeH
            for ((i, option) in options.withIndex()) {
                val ry = optionY + i * modeH
                val selected = entry.pickerMode == option
                g.fill(modeX, ry, modeX + modeBtnW, ry + modeH, if (selected) shade(30, 0.18f) else ENT_BG)
                g.Text(guiFont, styled(option.name.lowercase().replaceFirstChar { it.uppercase() }), modeX + 6, ry + (modeH - guiFont.lineHeight) / 2, if (selected) TEXT else TEXT_DIM)
            }
        }
        return
    }

    val mapY = modeY + modeH + 6
    val mapX = x0 + padding
    val barsW = if (hasAlpha) barW + barGap + barW else barW
    val mapW = actualW - padding * 2 - barGap - barsW

    val hueX = mapX + mapW + barGap
    val alphaX = hueX + barW + barGap

    val cols = mapW
    for (i in 0 until cols) {
        val rx = mapX + i
        val saturation = i.toFloat() / (cols - 1).coerceAtLeast(1)
        val topColor = hsvToRgb(hue, saturation, 1f)
        g.fillGradient(rx, mapY, rx + 1, mapY + mapH, argb(255, topColor.r, topColor.g, topColor.b), argb(255, 0, 0, 0))
    }

    val selectorX = mapX + (sat * (mapW - 1)).roundToInt()
    val selectorY = mapY + ((1f - value) * (mapH - 1)).roundToInt()
    g.fill(selectorX - 1, selectorY - 1, selectorX + 2, selectorY + 2, TEXT)

    val hueColors = intArrayOf(
        argb(255, 255, 0, 0), argb(255, 255, 255, 0), argb(255, 0, 255, 0),
        argb(255, 0, 255, 255), argb(255, 0, 0, 255), argb(255, 255, 0, 255), argb(255, 255, 0, 0)
    )
    val step = mapH / 6f
    for (i in 0 until 6) {
        val y1 = mapY + (i * step).roundToInt()
        val y2 = mapY + ((i + 1) * step).roundToInt()
        g.fillGradient(hueX, y1, hueX + barW, y2, hueColors[i], hueColors[i + 1])
    }
    // selector follows the current hue (animated in CHROMA)
    val hueSelectorY = mapY + (hue / 360f * (mapH - 1)).roundToInt()
    g.fill(hueX - 1, hueSelectorY - 1, hueX + barW + 1, hueSelectorY + 2, TEXT)

    if (hasAlpha) {
        drawAlphaCheckerboard(g, alphaX, mapY, barW, mapH)
        g.fillGradient(alphaX, mapY, alphaX + barW, mapY + mapH, argb(255, liveColor.r, liveColor.g, liveColor.b), argb(0, liveColor.r, liveColor.g, liveColor.b))
        val alphaSelectorY = mapY + ((255 - liveColor.a) / 255f * (mapH - 1)).roundToInt()
        g.fill(alphaX - 1, alphaSelectorY - 1, alphaX + barW + 1, alphaSelectorY + 2, TEXT)
    }

    val fieldY = mapY + mapH + 6
    val previewW = 20
    if (!isChroma) {
        val channelCount = if (hasAlpha) 4 else 3
        val fieldW = (actualW - padding * 2 - (channelCount - 1) * fieldGap) / channelCount

        val channels = mutableListOf("R" to baseCustom.r, "G" to baseCustom.g, "B" to baseCustom.b)
        if (hasAlpha) channels.add("A" to baseCustom.a)

        for ((i, channel) in channels.withIndex()) {
            val fx = x0 + padding + i * (fieldW + fieldGap)
            g.fill(fx, fieldY, fx + fieldW, fieldY + fieldH, ENT_BG)
            val lblW = guiFont.width(styled(channel.first))
            g.Text(guiFont, styled(channel.first), fx + 2, fieldY + (fieldH - guiFont.lineHeight) / 2, TEXT_DIM)
            val valueText = if (editingColorEntry == entry && editingColorChannel == i && !editingColorHex) entryField.text else channel.second.toString()
            g.Text(guiFont, styled(valueText), fx + 2 + lblW + 2, fieldY + (fieldH - guiFont.lineHeight) / 2, TEXT)
        }

        val hexY = fieldY + fieldH + fieldGap
        val hexW = actualW - padding * 2 - fieldGap - previewW
        val hexX = x0 + padding
        g.fill(hexX, hexY, hexX + hexW, hexY + fieldH, ENT_BG)
        val hexLblW = guiFont.width(styled("HEX"))
        g.Text(guiFont, styled("HEX"), hexX + 2, hexY + (fieldH - guiFont.lineHeight) / 2, TEXT_DIM)
        val hexText = if (editingColorEntry == entry && editingColorHex) entryField.text else String.format("#%02X%02X%02X%02X", liveColor.a, liveColor.r, liveColor.g, liveColor.b)
        g.Text(guiFont, styled(hexText), hexX + 2 + hexLblW + 2, hexY + (fieldH - guiFont.lineHeight) / 2, TEXT)

        val previewX = hexX + hexW + fieldGap
        drawAlphaCheckerboard(g, previewX, hexY, previewW, fieldH)
        g.roundedFill(previewX, hexY, previewW, fieldH, 1, argb(liveColor.a, liveColor.r, liveColor.g, liveColor.b))
    } else {
        val speedLabelY = fieldY
        val speedY = speedLabelY + guiFont.lineHeight + 2
        val speedX = x0 + padding
        val speedW = actualW - padding * 2
        val speedT = ((entry.chromaSpeed - 0.05f) / (8f - 0.05f)).coerceIn(0f, 1f)
        g.Text(guiFont, styled("Speed ${"%.2f".format(entry.chromaSpeed)}"), speedX + 2, speedLabelY, TEXT_DIM)
        g.fill(speedX, speedY, speedX + speedW, speedY + fieldH, ENT_BG)
        g.fill(speedX, speedY, speedX + (speedT * speedW).roundToInt(), speedY + fieldH, ACCENT)
        val kx = speedX + (speedT * (speedW - 1)).roundToInt()
        g.fill(kx - 1, speedY - 1, kx + 2, speedY + fieldH + 1, TEXT)
    }

    if (colorPickerModeExpanded) {
        val optionY = modeY + modeH
        for ((i, option) in options.withIndex()) {
            val ry = optionY + i * modeH
            val selected = entry.pickerMode == option
            g.fill(modeX, ry, modeX + modeBtnW, ry + modeH + 2, if (selected) shade(30, 0.18f) else ENT_BG)
            g.Text(guiFont, styled(option.name.lowercase().replaceFirstChar { it.uppercase() }), modeX + 6, ry + 3, if (selected) TEXT else TEXT_DIM)
        }
    }
}

internal fun ClickGui.handleColorClick(entry: ColorEntry, x: Int, y: Int, w: Int, mx: Int, my: Int): Boolean {
    if (entry != expandedColorEntry) return false
    val pickerX = colorPickerX
    val pickerY = colorPickerY
    val pickerW = colorPickerW

    val pickerH = colorPickerH
    if (pickerW <= 0 || pickerH <= 0) return false

    val padding = 6
    val modeH = 14
    val mapH = 65
    val barW = 10
    val barGap = 4
    val fieldH = 14
    val fieldGap = 4

    val modeX = pickerX + padding
    val modeY = pickerY + padding
    val options = if (supportsThemeMode(entry)) {
        listOf(ColorEntry.PickerMode.CUSTOM, ColorEntry.PickerMode.THEME, ColorEntry.PickerMode.CHROMA)
    } else {
        listOf(ColorEntry.PickerMode.CUSTOM, ColorEntry.PickerMode.CHROMA)
    }
    val optionTop = modeY + modeH
    val optionBottom = optionTop + options.size * (modeH + 2)

    // allow clicks inside the picker box OR inside the overlayed options area
    if (!(mx in pickerX until pickerX + pickerW && my in pickerY until pickerY + pickerH) &&
        !(colorPickerModeExpanded && mx in pickerX until pickerX + pickerW && my in optionTop until optionBottom)) return false

    val modeBtnW = pickerW - padding * 2

    if (mx in modeX until modeX + modeBtnW && my in modeY until modeY + modeH) {
        colorPickerModeExpanded = !colorPickerModeExpanded
        return true
    }

    if (colorPickerModeExpanded) {
        val optionY = modeY + modeH
        val options = if (supportsThemeMode(entry)) {
            listOf(ColorEntry.PickerMode.CUSTOM, ColorEntry.PickerMode.THEME, ColorEntry.PickerMode.CHROMA)
        } else {
            listOf(ColorEntry.PickerMode.CUSTOM, ColorEntry.PickerMode.CHROMA)
        }
        for ((i, option) in options.withIndex()) {
            val ry = optionY + i * modeH
            if (mx in modeX until modeX + modeBtnW && my in ry until ry + modeH) {
                entry.pickerMode = option
                colorPickerModeExpanded = false
                colorPickerMode = when (option) {
                    ColorEntry.PickerMode.CUSTOM -> ColorPickerMode.CUSTOM
                    ColorEntry.PickerMode.THEME -> ColorPickerMode.THEME
                    ColorEntry.PickerMode.CHROMA -> ColorPickerMode.CHROMA
                }
                applyDynamicColorState(entry)
                return true
            }
        }
    }

    if (entry.pickerMode == ColorEntry.PickerMode.THEME) return true

    val mapY = modeY + modeH + 6
    val mapX = pickerX + padding
    val hasAlpha = entry.allowAlpha
    val barsW = if (hasAlpha) barW + barGap + barW else barW
    val mapW = pickerW - padding * 2 - barGap - barsW

    val hueX = mapX + mapW + barGap
    val alphaX = hueX + barW + barGap

    val col = entry.customValue
    val (hue, _, _) = rgbToHsv(col)

    if (mx in mapX until mapX + mapW && my in mapY until mapY + mapH) {
        val saturation = ((mx - mapX).toFloat() / (mapW - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
        val value = 1f - ((my - mapY).toFloat() / (mapH - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
        if (entry.pickerMode == ColorEntry.PickerMode.CHROMA) {
            entry.chromaSaturation = saturation
            entry.chromaBrightness = value
        } else {
            entry.customValue = hsvToRgb(hue, saturation, value).copy(a = col.a)
            colorPickerCustomValue = entry.customValue
        }
        applyDynamicColorState(entry)
        draggingSlider = SliderDrag.ColorMap(entry, mapX, mapY, mapW, mapH)
        return true
    }

    if (mx in hueX until hueX + barW && my in mapY until mapY + mapH) {
        if (entry.pickerMode == ColorEntry.PickerMode.CHROMA) {
            return true
        } else {
            val t = ((my - mapY).toFloat() / (mapH - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            val newHue = t * 360f
            val (_, saturation, value) = rgbToHsv(col)
            entry.customValue = hsvToRgb(newHue, saturation, value).copy(a = col.a)
            colorPickerCustomValue = entry.customValue
            applyDynamicColorState(entry)
            draggingSlider = SliderDrag.ColorHue(entry, hueX, barW, mapY, mapH)
            return true
        }
    }

    if (hasAlpha && mx in alphaX until alphaX + barW && my in mapY until mapY + mapH) {
        val py = (my - mapY).coerceIn(0, mapH - 1)
        val alpha = (255f * (1f - (py.toFloat() / (mapH - 1).coerceAtLeast(1)))).roundToInt().coerceIn(0, 255)
        entry.customValue = entry.customValue.copy(a = alpha)
        colorPickerCustomValue = entry.customValue
        applyDynamicColorState(entry)
        draggingSlider = SliderDrag.ColorAlpha(entry, alphaX, barW, mapY, mapH)
        return true
    }

    if (entry.pickerMode == ColorEntry.PickerMode.CHROMA) {
        val speedLabelY = mapY + mapH + 6
        val speedY = speedLabelY + guiFont.lineHeight + 2
        val speedX = pickerX + padding
        val speedW = pickerW - padding * 2
        if (mx in speedX until speedX + speedW && my in speedY until speedY + fieldH) {
            val t = ((mx - speedX).toFloat() / (speedW - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            entry.chromaSpeed = 0.05f + t * (8f - 0.05f)
            applyDynamicColorState(entry)
            draggingSlider = SliderDrag.ChromaSpeed(entry, speedX, speedW, speedY, fieldH)
            return true
        }
        return true
    }

    val fieldY = mapY + mapH + 6
    val channelCount = if (hasAlpha) 4 else 3
    val fieldW = (pickerW - padding * 2 - (channelCount - 1) * fieldGap) / channelCount

    for (i in 0 until channelCount) {
        val fx = pickerX + padding + i * (fieldW + fieldGap)
        if (mx in fx until fx + fieldW && my in fieldY until fieldY + fieldH) {
            editingColorEntry = entry
            editingColorChannel = i
            editingColorHex = false
            entryField.set(listOf(col.r, col.g, col.b, col.a)[i].toString())
            entryField.cursor = entryField.text.length
            entryField.selAnchor = -1
            return true
        }
    }

    val hexY = fieldY + fieldH + fieldGap
    val previewW = 20
    val hexW = pickerW - padding * 2 - fieldGap - previewW
    val hexX = pickerX + padding

    if (mx in hexX until hexX + hexW && my in hexY until hexY + fieldH) {
        editingColorEntry = entry
        editingColorHex = true
        editingColorChannel = null
        entryField.set(String.format("#%02X%02X%02X%02X", col.a, col.r, col.g, col.b))
        entryField.cursor = entryField.text.length
        entryField.selAnchor = -1
        return true
    }

    return true
}

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
    val isChroma = entry.pickerMode == ColorEntry.PickerMode.CHROMA
    if (!isChroma) return entry.value
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
        255
    )
}

internal fun ClickGui.drawAlphaCheckerboard(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
    val size = 4
    for (row in 0 until (h + size - 1) / size) {
        for (col in 0 until (w + size - 1) / size) {
            val left = x + col * size
            val top = y + row * size
            val right = (left + size).coerceAtMost(x + w)
            val bottom = (top + size).coerceAtMost(y + h)
            val color = if ((row + col) % 2 == 0) argb(255, 220, 220, 220) else argb(255, 192, 192, 192)
            g.fill(left, top, right, bottom, color)
        }
    }
}

