package me.ghluka.medved.gui

import me.ghluka.medved.config.entry.*
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.ModuleManager
import me.ghluka.medved.module.modules.other.ClickGuiModule
import me.ghluka.medved.module.modules.other.ColorModule
import me.ghluka.medved.module.modules.other.FontModule
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import com.mojang.blaze3d.platform.InputConstants
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

class ClickGui : Screen(Component.literal("Medved")) {
    companion object {
        /** Categories that are currently collapsed (header only, content hidden). */
        val collapsed = mutableSetOf<Module.Category>()
        /** Top-left corner of each category's floating panel. */
        val positions = mutableMapOf<Module.Category, Pair<Int, Int>>()
        val expandedModules = mutableSetOf<Module>()
        var expandedColorEntry: ColorEntry? = null
        var expandedEnum: EnumEntry<*>? = null
    }

    private var editingString: StringEntry? = null
    private var editText = ""
    private var listeningKeybind: KeybindEntry? = null
    private var draggingCat: Module.Category? = null
    private var dragOffX = 0
    private var dragOffY = 0
    private var hoveredMod: Module? = null
    private var draggingSlider: SliderDrag? = null

    private var enumDropdownX = 0
    private var enumDropdownY = 0
    private var enumDropdownW = 0

    /** Tracks an active slider drag (numeric entry or color channel). */
    private sealed interface SliderDrag {
        val barX: Int
        val barW: Int
        data class Numeric(val entry: ConfigEntry<*>, override val barX: Int, override val barW: Int) : SliderDrag
        data class ColorChannel(val entry: ColorEntry, val channel: Int, override val barX: Int, override val barW: Int) : SliderDrag
        data class Range(val entry: IntRangeEntry, val isHigh: Boolean, override val barX: Int, override val barW: Int) : SliderDrag
    }

    private val PNL_W = 160  // panel width
    private val HDR_H = 18   // header bar height
    private val MOD_H = 16   // module row height
    private val ENT_H = 13   // config entry row height
    private val SLI_X = 78   // slider bar x offset within entry row

    private fun argb(a: Int, r: Int, g: Int, b: Int) =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    /** Blend a gray base toward the accent color. */
    private fun shade(base: Int, mix: Float, alpha: Int = 255): Int {
        val c = ColorModule.accent.value
        val r = (base + (c.r - base) * mix).toInt().coerceIn(0, 255)
        val g = (base + (c.g - base) * mix).toInt().coerceIn(0, 255)
        val b = (base + (c.b - base) * mix).toInt().coerceIn(0, 255)
        return argb(alpha, r, g, b)
    }

    private val BG       get() = shade(8, 0.05f, 100)
    private val PNL_BG   get() = shade(18, 0.08f, 240)
    private val HDR_BG   get() = shade(20, 0.20f)
    private val HDR_ACC  get() = ColorModule.accent.value.argb
    private val MOD_NORM get() = shade(20, 0.06f)
    private val MOD_HOV  get() = shade(30, 0.10f)
    private val ACCENT   get() = ColorModule.accent.value.argb
    private val ENT_BG   get() = shade(14, 0.04f)
    private val SLI_BG   get() = shade(30, 0.12f)
    private val SLI_FG   get() = with(ColorModule.accent.value) { argb(255, (r * 0.8).toInt(), (g * 0.8).toInt(), (b * 0.8).toInt()) }
    private val BTN_BG   get() = shade(35, 0.12f)
    private val BTN_ON   = argb(255,  50, 175,  60)
    private val BTN_OFF  = argb(255, 170,  55,  55)
    private val TEXT     = argb(255, 215, 215, 228)
    private val TEXT_DIM = argb(255, 118, 118, 140)
    private val guiFont  get() = FontModule.getFont()
    private fun styled(text: String) = FontModule.styledText(text)
    private fun plain(text: String): Component = Component.literal(text)

    override fun init() {
        super.init()
        // close container
        minecraft.player?.let { player ->
            if (player.containerMenu !== player.inventoryMenu) {
                player.closeContainer()
            }
        }
        // re-sync movement KeyMappings from the physical GLFW state
        val window = minecraft.window.handle()
        for (km in listOf(
            minecraft.options.keyUp, minecraft.options.keyDown,
            minecraft.options.keyLeft, minecraft.options.keyRight,
            minecraft.options.keyJump, minecraft.options.keySprint,
            minecraft.options.keyShift
        )) {
            val bound = InputConstants.getKey(km.saveString())
            if (bound.type == InputConstants.Type.KEYSYM) {
                val held = GLFW.glfwGetKey(window, bound.value) == GLFW.GLFW_PRESS
                KeyMapping.set(bound, held)
            }
        }
        if (positions.isEmpty()) {
            collapsed.addAll(Module.Category.entries)
            val gap = 6
            val margin = 10
            var x = margin
            var y = 30
            for (cat in Module.Category.entries) {
                if (x + PNL_W > width - margin) { x = margin; y += HDR_H + gap }
                positions[cat] = Pair(x, y)
                x += PNL_W + gap
            }
        }
    }

    override fun isPauseScreen() = false
    override fun isInGameUi() = true

    override fun extractRenderState(g: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {
        hoveredMod = null
        g.fill(0, 0, width, height, BG)
        for (cat in Module.Category.entries) drawCategoryPanel(g, cat, mx, my)
        // draw dropdown overlay
        val enumExp = expandedEnum
        if (enumExp != null) {
            drawEnumDropdown(g, enumExp, enumDropdownX, enumDropdownY, enumDropdownW, mx, my)
        }
        val hov = hoveredMod
        if (hov != null && ClickGuiModule.showDescriptions.value && hov.description.isNotBlank()) {
            drawTooltip(g, hov.description, mx, my)
        }
    }

    private fun drawCategoryPanel(g: GuiGraphicsExtractor, cat: Module.Category, mx: Int, my: Int) {
        val (px, py) = positions[cat] ?: return
        val expanded = cat !in collapsed
        val panelH   = if (expanded) fullPanelHeight(cat) else HDR_H

        if (expanded) g.fill(px, py + HDR_H, px + PNL_W, py + panelH, PNL_BG)

        // Header bar
        g.fill(px, py, px + PNL_W, py + HDR_H, HDR_BG)
        g.fill(px, py, px + 3, py + HDR_H, HDR_ACC)
        g.centeredText(guiFont, styled(cat.name), px + PNL_W / 2, py + (HDR_H - 8) / 2, -1)
        g.text(guiFont, plain(if (expanded) "▾" else "▸"), px + PNL_W - 12, py + (HDR_H - 8) / 2, TEXT_DIM)

        if (!expanded) return

        g.enableScissor(px, py + HDR_H, px + PNL_W, py + panelH)
        var y = py + HDR_H

        for (mod in ModuleManager.getByCategory(cat)) {
            val hovMod = mx in px until px + PNL_W && my in y until y + MOD_H
            if (hovMod) hoveredMod = mod
            g.fill(px, y, px + PNL_W, y + MOD_H, if (hovMod) MOD_HOV else MOD_NORM)
            if (mod.isEnabled()) g.fill(px, y, px + 3, y + MOD_H, ACCENT)
            g.text(guiFont, styled(mod.name), px + 7, y + (MOD_H - 8) / 2, if (mod.isEnabled()) TEXT else TEXT_DIM)
            val entries = configEntries(mod)
            if (entries.isNotEmpty())
                g.text(guiFont, plain(if (mod in expandedModules) "▾" else "▸"), px + PNL_W - 11, y + (MOD_H - 8) / 2, TEXT_DIM)
            y += MOD_H

            if (mod in expandedModules) {
                for (entry in entries) {
                    drawEntry(g, entry, px + 3, y, PNL_W - 3, mx, my)
                    y += ENT_H
                    if (entry is IntRangeEntry) {
                        drawRangeSliders(g, entry, px + 6, y, PNL_W - 6)
                        y += ENT_H
                    }
                    if (entry == expandedColorEntry && entry is ColorEntry) {
                        drawColorPicker(g, entry, px + 6, y, PNL_W - 6)
                        y += colorChannelCount(entry) * ENT_H
                    }
                    if (entry == expandedEnum && entry is EnumEntry<*>) {
                        val ew = enumDropdownWidth(entry)
                        enumDropdownX = px + PNL_W - ew
                        enumDropdownY = y
                        enumDropdownW = ew
                    }
                }
            }
        }

        g.disableScissor()
    }

    private fun fullPanelHeight(cat: Module.Category): Int {
        var h = HDR_H
        for (mod in ModuleManager.getByCategory(cat)) {
            h += MOD_H
            if (mod in expandedModules) {
                val entries = configEntries(mod)
                h += entries.size * ENT_H
                for (e in entries) {
                    if (e is IntRangeEntry) h += ENT_H
                }
                val colorExp = entries.firstOrNull { it == expandedColorEntry } as? ColorEntry
                if (colorExp != null) h += colorChannelCount(colorExp) * ENT_H
                val enumExp = entries.firstOrNull { it == expandedEnum } as? EnumEntry<*>
            }
        }
        return h
    }

    private fun drawEntry(g: GuiGraphicsExtractor, entry: ConfigEntry<*>, x: Int, y: Int, w: Int, mx: Int, my: Int) {
        g.fill(x, y, x + w, y + ENT_H, ENT_BG)
        g.text(guiFont, styled(fmtLabel(entry.name)), x + 2, y + (ENT_H - 8) / 2, TEXT_DIM)

        when (entry) {
            is BooleanEntry -> {
                val bx = x + w - 28
                g.fill(bx, y + 1, bx + 26, y + ENT_H - 1, if (entry.value) BTN_ON else BTN_OFF)
                g.centeredText(guiFont, styled(if (entry.value) "ON" else "OFF"), bx + 13, y + (ENT_H - 8) / 2, TEXT)
            }
            is IntEntry -> drawNumericSlider(
                g, entry.min != Int.MIN_VALUE && entry.max != Int.MAX_VALUE,
                entry.value.toFloat(),
                if (entry.min != Int.MIN_VALUE) entry.min.toFloat() else 0f,
                if (entry.max != Int.MAX_VALUE) entry.max.toFloat() else 100f,
                x, y, w
            )
            is FloatEntry -> drawNumericSlider(
                g, entry.min != -Float.MAX_VALUE && entry.max != Float.MAX_VALUE,
                entry.value,
                entry.min.takeIf { it != -Float.MAX_VALUE } ?: 0f,
                entry.max.takeIf { it != Float.MAX_VALUE } ?: 1f,
                x, y, w
            )
            is DoubleEntry -> drawNumericSlider(
                g, entry.min != -Double.MAX_VALUE && entry.max != Double.MAX_VALUE,
                entry.value.toFloat(),
                entry.min.takeIf { it != -Double.MAX_VALUE }?.toFloat() ?: 0f,
                entry.max.takeIf { it != Double.MAX_VALUE }?.toFloat() ?: 1f,
                x, y, w
            )
            is StringEntry -> {
                val fx = x + w - 66
                g.fill(fx, y + 1, fx + 64, y + ENT_H - 1, BTN_BG)
                val display = if (entry == editingString) "$editText|" else entry.value
                g.text(guiFont, styled(display.take(9)), fx + 2, y + (ENT_H - 8) / 2, TEXT)
            }
            is ColorEntry -> {
                val sx = x + w - 16
                g.fill(sx, y + 1, sx + 14, y + ENT_H - 1, entry.value.argb)
                g.outline(sx, y + 1, 14, ENT_H - 2, TEXT_DIM)
                if (entry == expandedColorEntry)
                    g.text(guiFont, plain("▾"), sx - 9, y + (ENT_H - 8) / 2, TEXT_DIM)
            }
            is KeybindEntry -> {
                val kx = x + w - 50
                val listening = entry == listeningKeybind
                g.fill(kx, y + 1, kx + 48, y + ENT_H - 1, if (listening) ACCENT else BTN_BG)
                g.centeredText(guiFont, styled(if (listening) "..." else keyName(entry.value)), kx + 24, y + (ENT_H - 8) / 2, TEXT)
            }
            is EnumEntry<*> -> {
                val ew = enumButtonWidth(entry)
                val ex = x + w - ew
                g.fill(ex, y + 1, ex + ew, y + ENT_H - 1, BTN_BG)
                val isOpen = entry == expandedEnum
                val label = fmtLabel(entry.value.name)
                g.text(guiFont, styled(label), ex + 3, y + (ENT_H - 8) / 2, TEXT)
                g.text(guiFont, plain(if (isOpen) "▾" else "▸"), ex + ew - 9, y + (ENT_H - 8) / 2, TEXT_DIM)
            }
            is IntRangeEntry -> {
                val txt = "${entry.value.first} - ${entry.value.second}"
                val styledTxt = styled(txt)
                val tw = guiFont.width(styledTxt)
                g.text(guiFont, styledTxt, x + w - tw - 4, y + (ENT_H - 8) / 2, TEXT)
            }
        }
    }

    private fun drawNumericSlider(
        g: GuiGraphicsExtractor,
        bounded: Boolean, v: Float, minV: Float, maxV: Float,
        x: Int, y: Int, w: Int
    ) {
        val barX = x + SLI_X; val barW = w - SLI_X - 24
        if (bounded && barW > 0) {
            g.fill(barX, y + 2, barX + barW, y + ENT_H - 2, SLI_BG)
            val fraction = ((v - minV) / (maxV - minV)).coerceIn(0f, 1f)
            val filled = (fraction * barW).roundToInt()
            if (filled > 0) g.fill(barX, y + 2, barX + filled, y + ENT_H - 2, SLI_FG)
        }
        val txt = if (v == v.toLong().toFloat()) "${v.toLong()}" else "%.2f".format(v)
        g.text(guiFont, styled(txt), x + w - 22, y + (ENT_H - 8) / 2, TEXT)
    }

    private fun drawRangeSliders(g: GuiGraphicsExtractor, entry: IntRangeEntry, x: Int, y: Int, w: Int) {
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

    private fun drawColorPicker(g: GuiGraphicsExtractor, entry: ColorEntry, x: Int, y: Int, w: Int) {
        val col = entry.value
        val channels = mutableListOf(
            Triple("R", col.r, argb(255, 190,  60,  60)),
            Triple("G", col.g, argb(255,  60, 190,  60)),
            Triple("B", col.b, argb(255,  60, 100, 200)),
        )
        if (entry.allowAlpha) channels.add(Triple("A", col.a, TEXT_DIM))
        channels.forEachIndexed { i, (lbl, v, fill) ->
            val ry = y + i * ENT_H
            g.fill(x, ry, x + w, ry + ENT_H, ENT_BG)
            g.text(guiFont, styled(lbl), x + 2, ry + (ENT_H - 8) / 2, TEXT_DIM)
            val bx = x + 12; val bw = w - 34
            g.fill(bx, ry + 2, bx + bw, ry + ENT_H - 2, SLI_BG)
            val filled = ((v / 255f) * bw).roundToInt()
            if (filled > 0) g.fill(bx, ry + 2, bx + filled, ry + ENT_H - 2, fill)
            g.text(guiFont, styled("$v"), bx + bw + 2, ry + (ENT_H - 8) / 2, TEXT)
        }
    }

    private fun drawEnumDropdown(g: GuiGraphicsExtractor, entry: EnumEntry<*>, x: Int, y: Int, w: Int, mx: Int, my: Int) {
        for ((i, c) in entry.constants.withIndex()) {
            val ry = y + i * ENT_H
            val hov = mx in x until x + w && my in ry until ry + ENT_H
            val selected = c == entry.value
            g.fill(x, ry, x + w, ry + ENT_H, if (hov) MOD_HOV else ENT_BG)
            if (selected) g.fill(x, ry, x + 2, ry + ENT_H, ACCENT)
            g.text(guiFont, styled(fmtLabel(c.name)), x + 5, ry + (ENT_H - 8) / 2, if (selected) TEXT else TEXT_DIM)
        }
    }

    /** Width of the enum button: fits the current selection + padding + arrow. */
    private fun enumButtonWidth(entry: EnumEntry<*>): Int {
        val labelW = guiFont.width(styled(fmtLabel(entry.value.name)))
        return (labelW + 16).coerceAtMost(PNL_W - 10)
    }

    /** Width of the enum dropdown: fits the widest option label. */
    private fun enumDropdownWidth(entry: EnumEntry<*>): Int {
        val maxLabel = entry.constants.maxOf { guiFont.width(styled(fmtLabel(it.name))) }
        return (maxLabel + 16).coerceAtMost(PNL_W - 10)
    }

    override fun mouseClicked(event: MouseButtonEvent, inBounds: Boolean): Boolean {
        val mx = event.x().toInt(); val my = event.y().toInt(); val btn = event.button()

        if (editingString != null) { editingString!!.value = editText; editingString = null }

        val enumExp = expandedEnum
        if (enumExp != null) {
            if (handleEnumDropdownClick(enumExp, enumDropdownX, enumDropdownY, enumDropdownW, mx, my)) return true
            // clicked outside dropdown, close it.
            expandedEnum = null
            return true
        }

        for (cat in Module.Category.entries) {
            val (px, py) = positions[cat] ?: continue
            val expanded = cat !in collapsed

            if (my in py until py + HDR_H && mx in px until px + PNL_W) {
                if (btn == 0) { draggingCat = cat; dragOffX = mx - px; dragOffY = my - py }
                else if (btn == 1) { if (cat in collapsed) collapsed.remove(cat) else collapsed.add(cat) }
                return true
            }

            if (!expanded) continue

            val panelH = fullPanelHeight(cat)
            if (mx !in px until px + PNL_W || my !in py + HDR_H until py + panelH) continue

            var y = py + HDR_H
            for (mod in ModuleManager.getByCategory(cat)) {
                if (my in y until y + MOD_H) {
                    val entries = configEntries(mod)
                    when {
                        btn == 0 && mx >= px + PNL_W - 14 && entries.isNotEmpty() -> toggleExpand(mod)
                        btn == 0 && !mod.isProtected -> mod.toggle()
                        btn == 0 && mod.isProtected && entries.isNotEmpty() -> toggleExpand(mod)
                        btn == 1 && entries.isNotEmpty() -> toggleExpand(mod)
                    }
                    return true
                }
                y += MOD_H

                if (mod in expandedModules) {
                    for (entry in configEntries(mod)) {
                        if (my in y until y + ENT_H) {
                            handleEntryClick(entry, px + 3, y, PNL_W - 3, mx, btn)
                            return true
                        }
                        y += ENT_H

                        if (entry is IntRangeEntry) {
                            if (handleRangeClick(entry, px + 6, y, PNL_W - 6, mx, my)) return true
                            y += ENT_H
                        }
                        if (entry == expandedColorEntry && entry is ColorEntry) {
                            if (handleColorClick(entry, px + 6, y, PNL_W - 6, mx, my)) return true
                            y += colorChannelCount(entry) * ENT_H
                        }
                    }
                }
            }
            return true
        }
        return super.mouseClicked(event, inBounds)
    }

    private fun handleEntryClick(entry: ConfigEntry<*>, x: Int, y: Int, w: Int, mx: Int, btn: Int) {
        when (entry) {
            is BooleanEntry -> entry.value = !entry.value
            is IntEntry -> if (btn == 0 && entry.min != Int.MIN_VALUE && entry.max != Int.MAX_VALUE) {
                val barX = x + SLI_X; val barW = w - SLI_X - 24
                if (mx in barX until barX + barW) {
                    val t = ((mx - barX).toFloat() / barW).coerceIn(0f, 1f)
                    entry.value = (entry.min + t * (entry.max - entry.min)).roundToInt()
                    draggingSlider = SliderDrag.Numeric(entry, barX, barW)
                }
            }
            is FloatEntry -> if (btn == 0 && entry.min != -Float.MAX_VALUE && entry.max != Float.MAX_VALUE) {
                val barX = x + SLI_X; val barW = w - SLI_X - 24
                if (mx in barX until barX + barW) {
                    val t = ((mx - barX).toFloat() / barW).coerceIn(0f, 1f)
                    entry.value = entry.min + t * (entry.max - entry.min)
                    draggingSlider = SliderDrag.Numeric(entry, barX, barW)
                }
            }
            is DoubleEntry -> if (btn == 0 && entry.min != -Double.MAX_VALUE && entry.max != Double.MAX_VALUE) {
                val barX = x + SLI_X; val barW = w - SLI_X - 24
                if (mx in barX until barX + barW) {
                    val t = ((mx - barX).toFloat() / barW).coerceIn(0f, 1f)
                    entry.value = entry.min + t * (entry.max - entry.min)
                    draggingSlider = SliderDrag.Numeric(entry, barX, barW)
                }
            }
            is StringEntry  -> { editingString = entry; editText = entry.value }
            is ColorEntry   -> expandedColorEntry = if (expandedColorEntry == entry) null else entry
            is KeybindEntry -> listeningKeybind   = if (listeningKeybind  == entry) null else entry
            is EnumEntry<*> -> expandedEnum = if (expandedEnum == entry) null else entry
        }
    }

    private fun handleColorClick(entry: ColorEntry, x: Int, y: Int, w: Int, mx: Int, my: Int): Boolean {
        val bx = x + 12; val bw = w - 34
        if (mx !in bx until bx + bw) return false
        val channels = colorChannelCount(entry)
        for (i in 0 until channels) {
            val ry = y + i * ENT_H
            if (my in ry until ry + ENT_H) {
                val v = (((mx - bx).toFloat() / bw).coerceIn(0f, 1f) * 255).roundToInt()
                applyColorChannel(entry, i, v)
                draggingSlider = SliderDrag.ColorChannel(entry, i, bx, bw)
                return true
            }
        }
        return false
    }

    private fun handleRangeClick(entry: IntRangeEntry, x: Int, y: Int, w: Int, mx: Int, my: Int): Boolean {
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

    private fun handleEnumDropdownClick(entry: EnumEntry<*>, x: Int, y: Int, w: Int, mx: Int, my: Int): Boolean {
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

    override fun keyPressed(event: KeyEvent): Boolean {
        val key = event.key()

        if (listeningKeybind != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) listeningKeybind = null
            else { listeningKeybind!!.value = key; listeningKeybind!!.suppressNextPress(); listeningKeybind = null }
            return true
        }

        if (editingString != null) {
            when (key) {
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER ->
                    { editingString!!.value = editText; editingString = null }
                GLFW.GLFW_KEY_BACKSPACE ->
                    if (editText.isNotEmpty()) editText = editText.dropLast(1)
                GLFW.GLFW_KEY_ESCAPE ->
                    { editingString!!.value = editText; editingString = null }
            }
            return true
        }

        if (key == GLFW.GLFW_KEY_ESCAPE || key == ClickGuiModule.keybind.value) { onClose(); return true }

        // route unhandled keys to game input
        val inputKey = InputConstants.getKey(event)
        KeyMapping.set(inputKey, true)
        KeyMapping.click(inputKey)
        return false
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        if (listeningKeybind != null || editingString != null) return true
        val inputKey = InputConstants.getKey(event)
        // release keys only if not physically held
        val physHeld = GLFW.glfwGetKey(minecraft.window.handle(), event.key()) == GLFW.GLFW_PRESS
        if (!physHeld) KeyMapping.set(inputKey, false)
        return false
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (editingString != null) { editText += event.codepointAsString(); return true }
        return false
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        val slider = draggingSlider
        if (slider != null) {
            val mx = event.x().toInt()
            val t = ((mx - slider.barX).toFloat() / slider.barW).coerceIn(0f, 1f)
            when (slider) {
                is SliderDrag.Numeric -> when (val e = slider.entry) {
                    is IntEntry   -> e.value = (e.min + t * (e.max - e.min)).roundToInt()
                    is FloatEntry -> e.value = e.min + t * (e.max - e.min)
                    is DoubleEntry -> e.value = e.min + t * (e.max - e.min)
                }
                is SliderDrag.ColorChannel -> {
                    val v = (t * 255).roundToInt()
                    applyColorChannel(slider.entry, slider.channel, v)
                }
                is SliderDrag.Range -> {
                    val e = slider.entry
                    val v = (e.min + t * (e.max - e.min)).roundToInt()
                    val (lo, hi) = e.value
                    e.value = if (slider.isHigh) lo to v.coerceAtLeast(lo) else v.coerceAtMost(hi) to hi
                }
            }
            return true
        }
        val cat = draggingCat ?: return super.mouseDragged(event, dragX, dragY)
        positions[cat] = Pair(event.x().toInt() - dragOffX, event.y().toInt() - dragOffY)
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        draggingCat = null
        draggingSlider = null
        return super.mouseReleased(event)
    }

    override fun onClose() {
        super.onClose()
        ClickGuiModule.disable()
    }

    private fun configEntries(mod: Module) = mod.entries.filter {
        it.name != "enabled" && !(mod.isProtected && it.name == "keybind") &&
        (it.visibleWhen?.invoke() != false)
    }

    private fun colorChannelCount(entry: ColorEntry) = if (entry.allowAlpha) 4 else 3

    private fun applyColorChannel(entry: ColorEntry, channel: Int, v: Int) {
        val col = entry.value
        entry.value = when (channel) {
            0 -> col.copy(r = v)
            1 -> col.copy(g = v)
            2 -> col.copy(b = v)
            else -> col.copy(a = v)
        }
    }

    private fun toggleExpand(mod: Module) {
        if (mod in expandedModules) expandedModules.remove(mod) else expandedModules.add(mod)
        expandedColorEntry = null
        expandedEnum = null
    }

    private fun drawTooltip(g: GuiGraphicsExtractor, text: String, mx: Int, my: Int) {
        val pad = 4
        val styledTooltip = styled(text)
        val bw = guiFont.width(styledTooltip) + pad * 2
        val bh = 8 + pad * 2
        var tx = mx + 10
        var ty = my - bh - 4
        if (tx + bw > width - 2) tx = width - bw - 2
        if (ty < 2) ty = my + 10
        g.fill(tx, ty, tx + bw, ty + bh, argb(230, 10, 10, 20))
        g.fill(tx, ty, tx + 1, ty + bh, ACCENT)
        g.text(guiFont, styledTooltip, tx + pad, ty + pad, TEXT)
    }

    private fun fmtLabel(name: String) =
        name.replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun keyName(keyCode: Int): String = when (keyCode) {
        GLFW.GLFW_KEY_UNKNOWN       -> "None"
        GLFW.GLFW_KEY_SPACE         -> "Space"
        GLFW.GLFW_KEY_ESCAPE        -> "Esc"
        GLFW.GLFW_KEY_ENTER,
        GLFW.GLFW_KEY_KP_ENTER      -> "Enter"
        GLFW.GLFW_KEY_BACKSPACE     -> "Back"
        GLFW.GLFW_KEY_TAB           -> "Tab"
        GLFW.GLFW_KEY_INSERT        -> "Insert"
        GLFW.GLFW_KEY_DELETE        -> "Del"
        GLFW.GLFW_KEY_HOME          -> "Home"
        GLFW.GLFW_KEY_END           -> "End"
        GLFW.GLFW_KEY_PAGE_UP       -> "PgUp"
        GLFW.GLFW_KEY_PAGE_DOWN     -> "PgDn"
        GLFW.GLFW_KEY_LEFT_SHIFT,
        GLFW.GLFW_KEY_RIGHT_SHIFT   -> "Shift"
        GLFW.GLFW_KEY_LEFT_CONTROL,
        GLFW.GLFW_KEY_RIGHT_CONTROL -> "Ctrl"
        GLFW.GLFW_KEY_LEFT_ALT,
        GLFW.GLFW_KEY_RIGHT_ALT     -> "Alt"
        else -> GLFW.glfwGetKeyName(keyCode, 0)?.uppercase() ?: "K$keyCode"
    }
}
