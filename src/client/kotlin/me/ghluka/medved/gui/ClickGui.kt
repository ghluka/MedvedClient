package me.ghluka.medved.gui

import me.ghluka.medved.config.ConfigManager
import me.ghluka.medved.config.entry.*
import me.ghluka.medved.module.HudModule
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.ModuleManager
import me.ghluka.medved.module.modules.other.ClickGui
import me.ghluka.medved.module.modules.other.Colour
import me.ghluka.medved.module.modules.other.Font
import me.ghluka.medved.util.*
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import com.mojang.blaze3d.platform.InputConstants
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

class ClickGui : Screen(Component.literal("Medved")) {
    companion object {
        /** Categories that are currently collapsed (header only, content hidden). */
        val collapsed = mutableSetOf<Module.Category>()
        val positions = mutableMapOf<Module.Category, Pair<Int, Int>>()
        val expandedModules = mutableSetOf<Module>()
        var expandedColorEntry: ColorEntry? = null
        var expandedEnum: EnumEntry<*>? = null
        val renderOrder = mutableListOf<Module.Category?>()
        val scrollTimes = mutableMapOf<Module, Long>()
        var presetNameBuffer = "default"
        var cfgPanelX = -1
        var cfgPanelY = -1
        var cfgPanelCollapsed = true
        var sidebarPaneX = -1
        var sidebarPaneY = -1
        private var defaultPositions = mapOf<Module.Category, Pair<Int, Int>>()
        private var defaultCfgPanelY = -1
        var dropdownScroll = 0

        fun resetPositions() {
            val mc = net.minecraft.client.Minecraft.getInstance()
            val w = mc.window.guiScaledWidth
            val h = mc.window.guiScaledHeight
            if (me.ghluka.medved.module.modules.other.ClickGui.currentMode.value ==
                    me.ghluka.medved.module.modules.other.ClickGui.Mode.SIDEBAR) {
                sidebarPaneX = (w - 480) / 2
                sidebarPaneY = (h - 320) / 2
                NotificationManager.show("Panel Centered")
                return
            }
            positions.clear()
            renderOrder.clear()
            renderOrder.add(null) // null = CONFIGS panel; last = on top initially
            renderOrder.addAll(Module.Category.entries)
            collapsed.addAll(Module.Category.entries)
            val gap = 6; val margin = 10; val pnlW = 160; val hdrH = 18
            var x = margin; var y = 30
            for (cat in Module.Category.entries) {
                if (x + pnlW > w - margin) { x = margin; y += hdrH + gap }
                positions[cat] = Pair(x, y)
                x += pnlW + gap
            }
            defaultPositions = positions.toMap()
            if (x + pnlW > w - margin) { x = margin; y += hdrH + gap }
            cfgPanelX = x; cfgPanelY = y; cfgPanelCollapsed = true
            defaultCfgPanelY = y
            dropdownScroll = 0
            NotificationManager.show("Layout Reset")
        }
    }

    private var selectedCategory: Module.Category? = null
    private var sidebarTab = 0 // 0=Modules, 1=Config
    private var sidebarDetailMod: Module? = null // module whose config page is open
    private var draggingSidebar = false
    private var sidebarDragOffX = 0
    private var sidebarDragOffY = 0
    private var sidebarScroll = 0f
    private var sidebarScrollMax = 0f
    private var sidebarConfigScroll = 0f
    private var sidebarConfigScrollMax = 0f

    private var editingString: StringEntry? = null
    private val entryField = TextField()
    private var draggingStringEntry = false
    private var entryFieldTextX = 0
    private var listeningKeybind: KeybindEntry? = null
    private var draggingCat: Module.Category? = null
    private var dragOffX = 0
    private var dragOffY = 0
    private var hoveredMod: Module? = null
    private var draggingSlider: SliderDrag? = null
    private var presetFieldActive = false
    private val presetField = TextField()
    private var draggingCfgPanel = false
    private var cfgDragOffX = 0
    private var cfgDragOffY = 0
    private var draggingPresetField = false
    private val cursorVisible get() = (System.currentTimeMillis() / 530) % 2 == 0L

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
        data class FloatRange(val entry: FloatRangeEntry, val isHigh: Boolean, override val barX: Int, override val barW: Int) : SliderDrag
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
        val c = Colour.bg.value
        val r = (base + (c.r - base) * mix).toInt().coerceIn(0, 255)
        val g = (base + (c.g - base) * mix).toInt().coerceIn(0, 255)
        val b = (base + (c.b - base) * mix).toInt().coerceIn(0, 255)
        return argb(alpha, r, g, b)
    }

    private val BG       get() = shade(8, 0.05f, 100)
    private val PNL_BG   get() = shade(18, 0.08f, 240)
    private val HDR_BG   get() = shade(20, 0.20f)
    private val HDR_ACC  get() = Colour.bg.value.argb
    private val MOD_NORM get() = shade(20, 0.06f)
    private val MOD_HOV  get() = shade(30, 0.10f)
    private val ACCENT   get() = Colour.accent.value.argb
    private val ENT_BG   get() = shade(14, 0.04f)
    private val SLI_BG   get() = shade(30, 0.12f)
    private val SLI_FG   get() = with(Colour.accent.value) { argb(255, (r * 0.8).toInt(), (g * 0.8).toInt(), (b * 0.8).toInt()) }
    private val BTN_BG   get() = shade(35, 0.12f)
    private val BTN_ON   = argb(255,  50, 175,  60)
    private val BTN_OFF  = argb(255, 170,  55,  55)
    private val TEXT     = argb(255, 215, 215, 228)
    private val TEXT_DIM = argb(255, 118, 118, 140)
    private val guiFont  get() = Font.getFont()
    private fun styled(text: String) = Font.styledText(text)
    private fun plain(text: String): Component = Component.literal(text)
    private fun jbMono(text: String): Component = Component.literal(text).withStyle(
        Style.EMPTY.withFont(FontDescription.Resource(
            Identifier.fromNamespaceAndPath("medved", "jetbrains_mono"))))

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
            if (x + PNL_W > width - margin) { x = margin; y += HDR_H + gap }
            cfgPanelX = x; cfgPanelY = y; cfgPanelCollapsed = true
        }
        if (cfgPanelX < 0) { cfgPanelX = 10; cfgPanelY = 30; cfgPanelCollapsed = true }
        if (sidebarPaneX < 0) { sidebarPaneX = (width - 480) / 2; sidebarPaneY = (height - 320) / 2 }
        if (renderOrder.isEmpty()) {
            renderOrder.addAll(Module.Category.entries)
            renderOrder.add(null) // null = CONFIGS panel
        }
        if (null !in renderOrder) renderOrder.add(null)
        
        dropdownScroll = 0
        
        scrollTimes.clear()
        presetField.text = presetNameBuffer
        presetField.end(false)
    }

    override fun isPauseScreen() = false
    override fun isInGameUi() = true

    override fun extractBackground(g: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {
        hoveredMod = null
        if (ClickGui.currentMode.value == ClickGui.Mode.DROPDOWN) {
            if (draggingCat == null && !draggingCfgPanel) constrainDropdownScroll()
            if (ClickGui.showBackground.value) g.fill(0, 0, width, height, BG)
            for (cat in renderOrder) {
                if (cat == null) drawConfigBar(g, mx, my)
                else drawCategoryPanel(g, cat, mx, my)
            }
        } else {
            drawSidebarMode(g, mx, my)
        }
        // draw dropdown overlay (always above panels)
        val enumExp = expandedEnum
        if (enumExp != null) {
            drawEnumDropdown(g, enumExp, enumDropdownX, enumDropdownY, enumDropdownW, mx, my)
        }
        val hov = hoveredMod
        if (hov != null && ClickGui.showDescriptions.value && hov.description.isNotBlank() &&
            ClickGui.currentMode.value == ClickGui.Mode.DROPDOWN) {
            drawTooltip(g, hov.description, mx, my)
        }
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {
    }

    private fun cfgPanelBodyH(): Int {
        val presets = ConfigManager.listPresets()
        return ENT_H + MOD_H + presets.size.coerceAtLeast(1) * ENT_H
    }

    private fun drawConfigBar(g: GuiGraphicsExtractor, mx: Int, my: Int) {
        val px = cfgPanelX; val py = cfgPanelY - dropdownScroll
        val expanded = !cfgPanelCollapsed
        if (expanded) {
            g.roundedFill(px, py, PNL_W, HDR_H, 3, HDR_BG, CORNERS_TOP)
            g.roundedFill(px, py + HDR_H, PNL_W, cfgPanelBodyH(), 3, PNL_BG, CORNERS_BOT)
        } else {
            g.roundedFill(px, py, PNL_W, HDR_H, 3, HDR_BG)
        }
        g.centeredText(guiFont, styled("CONFIGS"), px + PNL_W / 2, py + (HDR_H - 8) / 2, -1)
        g.text(guiFont, jbMono(if (expanded) "-" else "+"), px + PNL_W - 12, py + (HDR_H - 8) / 2, TEXT_DIM)
        if (!expanded) return

        var y = py + HDR_H

        val visW = PNL_W - 10
        val textX = px + 5
        g.fill(px, y, px + PNL_W, y + ENT_H, if (presetFieldActive) shade(40, 0.25f) else ENT_BG)
        g.fill(px, y, px + PNL_W, y + 1, if (presetFieldActive) ACCENT else shade(10, 0.05f))
        g.enableScissor(textX, y, textX + visW, y + ENT_H)
        if (presetFieldActive && presetField.hasSelection) {
            val sx = textX - presetField.scrollPx + guiFont.width(styled(presetField.text.substring(0, presetField.selMin)))
            val ex = textX - presetField.scrollPx + guiFont.width(styled(presetField.text.substring(0, presetField.selMax)))
            g.fill(sx, y + 1, ex, y + ENT_H - 1, argb(170, 60, 110, 210))
        }
        if (presetField.text.isEmpty() && !presetFieldActive) {
            g.text(guiFont, styled("preset name..."), textX, y + (ENT_H - 8) / 2, TEXT_DIM)
        } else {
            g.text(guiFont, styled(presetField.text), textX - presetField.scrollPx, y + (ENT_H - 8) / 2, TEXT)
        }
        if (presetFieldActive && cursorVisible) {
            val cx = textX - presetField.scrollPx + guiFont.width(styled(presetField.text.substring(0, presetField.cursor)))
            g.fill(cx, y + 1, cx + 1, y + ENT_H - 1, argb(230, 220, 220, 255))
        }
        g.disableScissor()
        y += ENT_H

        val btnW = PNL_W / 3
        val saveHov = mx in px until px + btnW && my in y until y + MOD_H
        val loadHov = mx in px + btnW until px + btnW * 2 && my in y until y + MOD_H
        val foldHov = mx in px + btnW * 2 until px + PNL_W && my in y until y + MOD_H
        g.fill(px,            y, px + btnW,     y + MOD_H, if (saveHov) shade(50, 0.20f) else BTN_BG)
        g.fill(px + btnW,     y, px + btnW * 2, y + MOD_H, if (loadHov) shade(50, 0.20f) else BTN_BG)
        g.fill(px + btnW * 2, y, px + PNL_W,   y + MOD_H, if (foldHov) shade(50, 0.20f) else BTN_BG)
        g.fill(px + btnW - 1,     y, px + btnW,     y + MOD_H, shade(10, 0.05f))
        g.fill(px + btnW * 2 - 1, y, px + btnW * 2, y + MOD_H, shade(10, 0.05f))
        g.centeredText(guiFont, styled("Save"),   px + btnW / 2,            y + (MOD_H - 8) / 2, TEXT)
        g.centeredText(guiFont, styled("Load"),   px + btnW + btnW / 2,     y + (MOD_H - 8) / 2, TEXT)
        g.centeredText(guiFont, styled("Folder"), px + btnW * 2 + btnW / 2, y + (MOD_H - 8) / 2, TEXT)
        y += MOD_H

        val presets = ConfigManager.listPresets()
        if (presets.isEmpty()) {
            g.fill(px, y, px + PNL_W, y + ENT_H, ENT_BG)
            g.text(guiFont, styled("(no presets saved)"), px + 5, y + (ENT_H - 8) / 2, TEXT_DIM)
            y += ENT_H
        } else {
            for (preset in presets) {
                val hov = mx in px until px + PNL_W && my in y until y + ENT_H
                val selected = preset == presetField.text
                g.fill(px, y, px + PNL_W, y + ENT_H, if (hov) MOD_HOV else ENT_BG)
                if (selected) g.fill(px, y, px + 3, y + ENT_H, ACCENT)
                g.text(guiFont, styled(preset), px + 7, y + (ENT_H - 8) / 2, if (selected) TEXT else TEXT_DIM)
                y += ENT_H
            }
        }

    }

    private inner class TextField {
        var text      = ""
        var cursor    = 0
        var selAnchor = -1
        var scrollPx  = 0

        val selMin get() = if (selAnchor < 0) cursor else minOf(cursor, selAnchor)
        val selMax get() = if (selAnchor < 0) cursor else maxOf(cursor, selAnchor)
        val hasSelection get() = selAnchor >= 0 && selAnchor != cursor

        fun set(s: String) { text = s; cursor = s.length; selAnchor = -1; scrollPx = 0 }
        fun insert(s: String) {
            if (hasSelection) { text = text.removeRange(selMin, selMax); cursor = selMin; selAnchor = -1 }
            text = text.substring(0, cursor) + s + text.substring(cursor)
            cursor += s.length
        }
        fun backspace() {
            if (hasSelection) { text = text.removeRange(selMin, selMax); cursor = selMin; selAnchor = -1 }
            else if (cursor > 0) { text = text.removeRange(cursor - 1, cursor); cursor-- }
        }
        fun deleteForward() {
            if (hasSelection) { text = text.removeRange(selMin, selMax); cursor = selMin; selAnchor = -1 }
            else if (cursor < text.length) { text = text.removeRange(cursor, cursor + 1) }
        }
        fun backspaceWord() {
            if (hasSelection) { backspace(); return }
            var start = cursor
            while (start > 0 && text[start - 1] == ' ') start--
            while (start > 0 && text[start - 1] != ' ') start--
            text = text.removeRange(start, cursor); cursor = start; selAnchor = -1
        }
        fun move(delta: Int, selecting: Boolean) {
            if (!selecting && hasSelection) { cursor = if (delta < 0) selMin else selMax; selAnchor = -1; return }
            if (selecting && selAnchor < 0) selAnchor = cursor else if (!selecting) selAnchor = -1
            cursor = (cursor + delta).coerceIn(0, text.length)
        }
        fun wordMove(forward: Boolean, selecting: Boolean) {
            var i = cursor
            if (forward) { while (i < text.length && text[i] == ' ') i++; while (i < text.length && text[i] != ' ') i++ }
            else         { while (i > 0 && text[i - 1] == ' ') i--;  while (i > 0 && text[i - 1] != ' ') i-- }
            if (selecting && selAnchor < 0) selAnchor = cursor else if (!selecting) selAnchor = -1
            cursor = i
        }
        fun home(selecting: Boolean) {
            if (selecting && selAnchor < 0) selAnchor = cursor else if (!selecting) selAnchor = -1
            cursor = 0
        }
        fun end(selecting: Boolean) {
            if (selecting && selAnchor < 0) selAnchor = cursor else if (!selecting) selAnchor = -1
            cursor = text.length
        }
        fun selectAll() { selAnchor = 0; cursor = text.length }
        fun copy() = if (hasSelection) text.substring(selMin, selMax) else ""
        fun cut()  = copy().also { if (hasSelection) { text = text.removeRange(selMin, selMax); cursor = selMin; selAnchor = -1 } }
        fun posFromPixel(relPx: Int): Int {
            val adjusted = relPx + scrollPx
            if (adjusted <= 0 || text.isEmpty()) return 0
            for (i in 1..text.length) {
                val half = (guiFont.width(styled(text.substring(0, i - 1))) + guiFont.width(styled(text.substring(0, i)))) / 2
                if (adjusted <= half) return i - 1
            }
            return text.length
        }
        fun clampScroll(visWidth: Int) {
            val curPx = guiFont.width(styled(text.substring(0, cursor)))
            if (curPx - scrollPx < 0)        scrollPx = curPx
            if (curPx - scrollPx > visWidth) scrollPx = curPx - visWidth
            scrollPx = scrollPx.coerceAtLeast(0)
        }
    }

    private fun drawCategoryPanel(g: GuiGraphicsExtractor, cat: Module.Category, mx: Int, my: Int) {
        val pos = positions[cat] ?: return
        val px = pos.first
        val py = pos.second - dropdownScroll
        val expanded = cat !in collapsed
        val panelH   = if (expanded) fullPanelHeight(cat) else HDR_H

        if (expanded) {
            g.roundedFill(px, py, PNL_W, HDR_H, 3, HDR_BG, CORNERS_TOP)
            g.roundedFill(px, py + HDR_H, PNL_W, panelH - HDR_H, 3, PNL_BG, CORNERS_BOT)
        } else {
            g.roundedFill(px, py, PNL_W, HDR_H, 3, HDR_BG)
        }
        g.centeredText(guiFont, styled(cat.name), px + PNL_W / 2, py + (HDR_H - 8) / 2, -1)
        g.text(guiFont, jbMono(if (expanded) "-" else "+"), px + PNL_W - 12, py + (HDR_H - 8) / 2, TEXT_DIM)

        if (!expanded) return

        g.enableScissor(px, py + HDR_H, px + PNL_W, py + panelH)
        var y = py + HDR_H

        for (mod in ModuleManager.getByCategory(cat)) {
            val hovMod = mx in px until px + PNL_W && my in y until y + MOD_H
            if (hovMod) hoveredMod = mod
            g.fill(px, y, px + PNL_W, y + MOD_H, if (hovMod) MOD_HOV else MOD_NORM)
            if (mod.isEnabled()) g.fill(px, y, px + 3, y + MOD_H, ACCENT)
            val entries = configEntries(mod)
            val nameAvailW = PNL_W - 7 - (if (entries.isNotEmpty()) 14 else 4)
            drawModuleName(g, mod, px + 7, y, nameAvailW, if (mod.isEnabled()) TEXT else TEXT_DIM)
            if (entries.isNotEmpty())
                g.text(guiFont, jbMono(if (mod in expandedModules) "-" else "+"), px + PNL_W - 11, y + (MOD_H - 8) / 2, TEXT_DIM)
            y += MOD_H

            if (mod in expandedModules) {
                for (entry in entries) {
                    drawEntry(g, entry, px + 3, y, PNL_W - 3, mx, my)
                    y += ENT_H
                    if (entry is IntEntry) {
                        val bounded = entry.min != Int.MIN_VALUE && entry.max != Int.MAX_VALUE
                        drawNumericSliderBar(g, bounded, entry.value.toFloat(),
                            if (bounded) entry.min.toFloat() else 0f,
                            if (bounded) entry.max.toFloat() else 1f,
                            px + 3, y, PNL_W - 3)
                        y += ENT_H
                    }
                    if (entry is FloatEntry) {
                        val bounded = entry.min != -Float.MAX_VALUE && entry.max != Float.MAX_VALUE
                        drawNumericSliderBar(g, bounded, entry.value,
                            entry.min.takeIf { it != -Float.MAX_VALUE } ?: 0f,
                            entry.max.takeIf { it != Float.MAX_VALUE } ?: 1f,
                            px + 3, y, PNL_W - 3)
                        y += ENT_H
                    }
                    if (entry is DoubleEntry) {
                        val bounded = entry.min != -Double.MAX_VALUE && entry.max != Double.MAX_VALUE
                        drawNumericSliderBar(g, bounded, entry.value.toFloat(),
                            entry.min.takeIf { it != -Double.MAX_VALUE }?.toFloat() ?: 0f,
                            entry.max.takeIf { it != Double.MAX_VALUE }?.toFloat() ?: 1f,
                            px + 3, y, PNL_W - 3)
                        y += ENT_H
                    }
                    if (entry is IntRangeEntry) {
                        drawRangeSliders(g, entry, px + 6, y, PNL_W - 6)
                        y += ENT_H
                    }
                    if (entry is FloatRangeEntry) {
                        drawFloatRangeSliders(g, entry, px + 6, y, PNL_W - 6)
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

    private fun drawSidebarMode(g: GuiGraphicsExtractor, mx: Int, my: Int) {
        if (ClickGui.showBackground.value) g.fill(0, 0, width, height, BG)

        val pw = 480; val ph = 320
        val px = sidebarPaneX; val py = sidebarPaneY
        val tabH = 24
        val catW = 110    // left category column width
        val contentX = px + catW
        val contentW = pw - catW
        val modRowH = 36  // big module card height
        val smallFont = guiFont
        val WHITE = argb(255, 230, 230, 240)
        val GREY  = argb(255, 130, 130, 150)

        val logoH = 24  // full-width drag/logo strip at top
        g.roundedFill(px, py, pw, ph, 4, PNL_BG)

        g.fill(px, py, px + pw, py + logoH, shade(20, 0.15f))
        g.centeredText(smallFont, styled("GRIZZLY"), px + pw / 2, py + (logoH - 8) / 2, TEXT_DIM)

        val tabW = pw / 2
        val tabs = listOf("Modules", "Config")
        val tabBarY = py + logoH
        for ((i, tabName) in tabs.withIndex()) {
            val tx = px + i * tabW
            val tabSel = sidebarTab == i
            val tabHov = mx in tx until tx + tabW && my in tabBarY until tabBarY + tabH
            g.fill(tx, tabBarY, tx + tabW, tabBarY + tabH,
                if (tabSel) shade(30, 0.18f) else if (tabHov) shade(25, 0.12f) else shade(18, 0.08f))
            if (tabSel) g.fill(tx, tabBarY + tabH - 2, tx + tabW, tabBarY + tabH, ACCENT)
            g.centeredText(smallFont, styled(tabName), tx + tabW / 2, tabBarY + (tabH - 8) / 2, if (tabSel) TEXT else TEXT_DIM)
        }

        if (sidebarTab == 0) {
            val catAreaY = py + logoH + tabH
            g.fill(px, catAreaY, contentX, py + ph, shade(14, 0.06f))
            var cy = catAreaY + 6
            val catPad = 2
            for (cat in Module.Category.entries) {
                val rowH = 18
                val catHov = mx in px until contentX && my in cy until cy + rowH
                val catSel = selectedCategory == cat
                g.fill(px, cy, contentX, cy + rowH,
                    if (catSel) shade(35, 0.22f) else if (catHov) shade(28, 0.14f) else shade(14, 0.06f))
                g.centeredText(smallFont, styled(cat.name), px + catW / 2, cy + (rowH - 8) / 2, if (catSel) WHITE else GREY)
                cy += rowH + catPad
            }
        }

        val bodyX = if (sidebarTab == 0) contentX else px
        val bodyY = py + logoH + tabH
        val bodyW = if (sidebarTab == 0) contentW else pw
        val bodyH = ph - logoH - tabH - 12
        g.enableScissor(bodyX, bodyY + 6, bodyX + bodyW, bodyY + 6 + bodyH)

        if (sidebarTab == 0) {
            val detailMod = sidebarDetailMod
            if (detailMod != null) {
                val headerY = bodyY + 6
                val backBtnH = 18
                val nameH = smallFont.lineHeight + 4
                val descMaxW = bodyW - 80
                val descLines = if (detailMod.description.isBlank()) emptyList() else {
                    val words = detailMod.description.split(" ")
                    val lines = mutableListOf<String>()
                    var cur = ""
                    for (word in words) {
                        val candidate = if (cur.isEmpty()) word else "$cur $word"
                        if (smallFont.width(styled(candidate)) <= descMaxW) cur = candidate
                        else { if (cur.isNotEmpty()) lines += cur; cur = word }
                    }
                    if (cur.isNotEmpty()) lines += cur
                    lines
                }
                val descLineH = (smallFont.lineHeight * 1.3).toInt()
                val descH = descLines.size * descLineH
                val tgH = 18
                val toggleH = tgH + 8
                val staticH = backBtnH + nameH + descH + 8 + toggleH
                val entries = configEntries(detailMod)
                var entriesH = 0
                for (entry in entries) {
                    entriesH += ENT_H
                    if (entry is IntEntry || entry is FloatEntry || entry is DoubleEntry) entriesH += ENT_H
                    if (entry is IntRangeEntry || entry is FloatRangeEntry) entriesH += ENT_H
                    if (entry == expandedColorEntry && entry is ColorEntry) entriesH += colorChannelCount(entry) * ENT_H
                }
                sidebarConfigScrollMax = (entriesH - (bodyH - staticH)).coerceAtLeast(0).toFloat()
                if (sidebarConfigScroll > sidebarConfigScrollMax) sidebarConfigScroll = sidebarConfigScrollMax
                if (sidebarConfigScroll < 0f) sidebarConfigScroll = 0f
                var ey = headerY
                g.text(smallFont, styled("< Back"), bodyX + 8, ey + 3, TEXT_DIM)
                ey += backBtnH
                g.text(smallFont, styled(detailMod.name), bodyX + 8, ey, WHITE)
                ey += nameH
                descLines.forEachIndexed { i, line ->
                    g.text(smallFont, styled(line), bodyX + 8, ey + i * descLineH, GREY)
                }
                ey += descH + 8
                val tgX = bodyX + 8; val tgY = ey
                val tgLabel = if (detailMod.isEnabled()) "ON" else "OFF"
                val tgCol = if (detailMod.isEnabled()) BTN_ON else BTN_OFF
                g.fill(tgX, tgY, tgX + 60, tgY + tgH, tgCol)
                g.centeredText(smallFont, styled(tgLabel), tgX + 30, tgY + (tgH - 8) / 2, TEXT)
                ey += toggleH
                // --- Render config entries with scroll ---
                val configRegionTop = ey
                val configRegionBottom = bodyY + 6 + bodyH
                var entryY = configRegionTop - sidebarConfigScroll.toInt()
                if (entries.isEmpty()) {
                    g.text(smallFont, styled("No settings."), bodyX + 6, entryY, GREY)
                } else {
                    for (entry in entries) {
                        if (entryY + ENT_H > configRegionBottom) break
                        drawEntry(g, entry, bodyX + 4, entryY, bodyW - 8, mx, my)
                        entryY += ENT_H
                        if (entry is IntEntry) {
                            val bounded = entry.min != Int.MIN_VALUE && entry.max != Int.MAX_VALUE
                            drawNumericSliderBar(g, bounded, entry.value.toFloat(),
                                if (bounded) entry.min.toFloat() else 0f,
                                if (bounded) entry.max.toFloat() else 1f,
                                bodyX + 4, entryY, bodyW - 8); entryY += ENT_H
                        }
                        if (entry is FloatEntry) {
                            val bounded = entry.min != -Float.MAX_VALUE && entry.max != Float.MAX_VALUE
                            drawNumericSliderBar(g, bounded, entry.value,
                                entry.min.takeIf { it != -Float.MAX_VALUE } ?: 0f,
                                entry.max.takeIf { it != Float.MAX_VALUE } ?: 1f,
                                bodyX + 4, entryY, bodyW - 8); entryY += ENT_H
                        }
                        if (entry is DoubleEntry) {
                            val bounded = entry.min != -Double.MAX_VALUE && entry.max != Double.MAX_VALUE
                            drawNumericSliderBar(g, bounded, entry.value.toFloat(),
                                entry.min.takeIf { it != -Double.MAX_VALUE }?.toFloat() ?: 0f,
                                entry.max.takeIf { it != Double.MAX_VALUE }?.toFloat() ?: 1f,
                                bodyX + 4, entryY, bodyW - 8); entryY += ENT_H
                        }
                        if (entry is IntRangeEntry) { drawRangeSliders(g, entry, bodyX + 6, entryY, bodyW - 8); entryY += ENT_H }
                        if (entry is FloatRangeEntry) { drawFloatRangeSliders(g, entry, bodyX + 6, entryY, bodyW - 8); entryY += ENT_H }
                        if (entry == expandedColorEntry && entry is ColorEntry) {
                            drawColorPicker(g, entry, bodyX + 6, entryY, bodyW - 8)
                            entryY += colorChannelCount(entry) * ENT_H
                        }
                        if (entry == expandedEnum && entry is EnumEntry<*>) {
                            val ew = enumDropdownWidth(entry)
                            enumDropdownX = bodyX + bodyW - ew
                            enumDropdownY = entryY
                            enumDropdownW = ew
                        }
                    }
                }
            } else {
                val mods = selectedCategory?.let { ModuleManager.getByCategory(it) } ?: emptyList()
                if (mods.isEmpty()) {
                    g.centeredText(smallFont, styled("Select a category"), bodyX + bodyW / 2, bodyY + 6 + bodyH / 2 - 4, GREY)
                    sidebarScroll = 0f; sidebarScrollMax = 0f
                } else {
                    val lineH = guiFont.lineHeight
                    val cardPad = 4
                    val cardList = mods.map { mod ->
                        val descMaxW = bodyW - 80
                        val descLines = if (mod.description.isBlank()) emptyList() else {
                            val words = mod.description.split(" ")
                            val lines = mutableListOf<String>()
                            var cur = ""
                            for (word in words) {
                                val candidate = if (cur.isEmpty()) word else "$cur $word"
                                if (guiFont.width(styled(candidate)) <= descMaxW) {
                                    cur = candidate
                                } else {
                                    if (cur.isNotEmpty()) lines += cur
                                    cur = word
                                }
                            }
                            if (cur.isNotEmpty()) lines += cur
                            lines
                        }
                        val nameTopPad = 8
                        val nameBottomPad = 8
                        val descLineSpacing = (lineH * 1.3).toInt()
                        val cardH = nameTopPad + lineH + (if (descLines.isEmpty()) 0 else descLines.size * descLineSpacing + nameBottomPad) + 8
                        Triple(mod, descLines, cardH)
                    }
                    val totalH = cardList.sumOf { it.third + cardPad } - cardPad
                    sidebarScrollMax = (totalH - bodyH).coerceAtLeast(0).toFloat()
                    if (sidebarScroll > sidebarScrollMax) sidebarScroll = sidebarScrollMax
                    if (sidebarScroll < 0f) sidebarScroll = 0f
                    var my2 = bodyY + 6 - sidebarScroll.toInt()
                    for ((mod, descLines, cardH) in cardList) {
                        if (my2 + cardH < bodyY + 6) { my2 += cardH + cardPad; continue }
                        if (my2 > bodyY + 6 + bodyH) break
                        val mHov = mx in bodyX until bodyX + bodyW && my in my2 until my2 + cardH
                        if (mHov) hoveredMod = mod
                        g.roundedFill(bodyX + 4, my2, bodyW - 8, cardH, 3,
                            if (mHov) shade(38, 0.14f) else shade(32, 0.09f))
                        if (mod.isEnabled()) g.roundedFill(bodyX + 4, my2, 3, cardH, 3, ACCENT, CORNERS_LEFT)
                        val nameTopPad = 8
                        val nameBottomPad = 8
                        val descLineSpacing = (lineH * 1.3).toInt()
                        g.text(smallFont, styled(mod.name), bodyX + 12, my2 + nameTopPad, WHITE)
                        descLines.forEachIndexed { i, line ->
                            g.text(smallFont, styled(line), bodyX + 12, my2 + nameTopPad + lineH + nameBottomPad + i * descLineSpacing, GREY)
                        }
                        val tgX = bodyX + bodyW - 44; val tgY = my2 + cardH / 2 - 6
                        g.fill(tgX, tgY, tgX + 32, tgY + 12, if (mod.isEnabled()) BTN_ON else BTN_OFF)
                        g.centeredText(smallFont, styled(if (mod.isEnabled()) "ON" else "OFF"), tgX + 16, tgY + 2, TEXT)
                        if (configEntries(mod).isNotEmpty()) {
                            g.text(smallFont, plain("⋮"), tgX - 12, my2 + cardH / 2 - 4, TEXT_DIM)
                        }
                        my2 += cardH + cardPad
                    }
                }
            }
        } else {
            var ey = bodyY + 6
            val visW = bodyW - 10
            val textX2 = bodyX + 5
            g.fill(bodyX, ey, bodyX + bodyW, ey + ENT_H, if (presetFieldActive) shade(40, 0.25f) else ENT_BG)
            g.fill(bodyX, ey, bodyX + bodyW, ey + 1, if (presetFieldActive) ACCENT else shade(10, 0.05f))
            g.enableScissor(textX2, ey, textX2 + visW, ey + ENT_H)
            if (presetFieldActive && presetField.hasSelection) {
                val sx2 = textX2 - presetField.scrollPx + guiFont.width(styled(presetField.text.substring(0, presetField.selMin)))
                val ex2 = textX2 - presetField.scrollPx + guiFont.width(styled(presetField.text.substring(0, presetField.selMax)))
                g.fill(sx2, ey + 1, ex2, ey + ENT_H - 1, argb(170, 60, 110, 210))
            }
            if (presetField.text.isEmpty() && !presetFieldActive)
                g.text(guiFont, styled("preset name..."), textX2, ey + (ENT_H - 8) / 2, TEXT_DIM)
            else
                g.text(guiFont, styled(presetField.text), textX2 - presetField.scrollPx, ey + (ENT_H - 8) / 2, TEXT)
            if (presetFieldActive && cursorVisible) {
                val cx2 = textX2 - presetField.scrollPx + guiFont.width(styled(presetField.text.substring(0, presetField.cursor)))
                g.fill(cx2, ey + 1, cx2 + 1, ey + ENT_H - 1, argb(230, 220, 220, 255))
            }
            g.disableScissor()
            ey += ENT_H
            val btnW2 = bodyW / 3
            for ((i, label2) in listOf("Save", "Load", "Folder").withIndex()) {
                val bx2 = bodyX + i * btnW2
                val bHov = mx in bx2 until bx2 + btnW2 && my in ey until ey + MOD_H
                g.fill(bx2, ey, bx2 + btnW2, ey + MOD_H, if (bHov) shade(50, 0.20f) else BTN_BG)
                if (i < 2) g.fill(bx2 + btnW2 - 1, ey, bx2 + btnW2, ey + MOD_H, shade(10, 0.05f))
                g.centeredText(guiFont, styled(label2), bx2 + btnW2 / 2, ey + (MOD_H - 8) / 2, TEXT)
            }
            ey += MOD_H
            val presets = ConfigManager.listPresets()
            if (presets.isEmpty()) {
                g.fill(bodyX, ey, bodyX + bodyW, ey + ENT_H, ENT_BG)
                g.text(guiFont, styled("(no presets saved)"), bodyX + 5, ey + (ENT_H - 8) / 2, TEXT_DIM)
            } else {
                for (preset in presets) {
                    if (ey + ENT_H > bodyY + bodyH) break
                    val pHov = mx in bodyX until bodyX + bodyW && my in ey until ey + ENT_H
                    val pSel = preset == presetField.text
                    g.fill(bodyX, ey, bodyX + bodyW, ey + ENT_H, if (pHov) MOD_HOV else ENT_BG)
                    if (pSel) g.fill(bodyX, ey, bodyX + 3, ey + ENT_H, ACCENT)
                    g.text(guiFont, styled(preset), bodyX + 7, ey + (ENT_H - 8) / 2, if (pSel) TEXT else TEXT_DIM)
                    ey += ENT_H
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
                    if (e is IntRangeEntry || e is FloatRangeEntry || e is IntEntry || e is FloatEntry || e is DoubleEntry) h += ENT_H
                }
                val colorExp = entries.firstOrNull { it == expandedColorEntry } as? ColorEntry
                if (colorExp != null) h += colorChannelCount(colorExp) * ENT_H
                val enumExp = entries.firstOrNull { it == expandedEnum } as? EnumEntry<*>
            }
        }
        return h
    }

    private fun drawModuleName(
        g: GuiGraphicsExtractor, mod: Module,
        x: Int, rowY: Int, availW: Int, textColor: Int
    ) {
        val textY = rowY + (MOD_H - 8) / 2
        val comp  = styled(mod.name)
        val textW = guiFont.width(comp)
        if (textW <= availW) {
            g.text(guiFont, comp, x, textY, textColor)
            scrollTimes.remove(mod)
            return
        }
        val now     = System.currentTimeMillis()
        val startMs = scrollTimes.getOrPut(mod) { now }
        val elapsed = (now - startMs) / 1000f
        val gap         = 20            // px between the two looping copies
        val slotW       = textW + gap   // one full scroll cycle in pixels
        val scrollSpeed = 25f
        val pauseStart  = 1.5f
        val offsetX = if (elapsed < pauseStart) {
            0f
        } else {
            ((elapsed - pauseStart) * scrollSpeed) % slotW
        }.toInt()
        g.enableScissor(x, rowY, x + availW, rowY + MOD_H)
        g.text(guiFont, comp, x - offsetX,        textY, textColor)  // primary copy
        g.text(guiFont, comp, x - offsetX + slotW, textY, textColor) // trailing copy
        g.disableScissor()
    }

    private fun drawEntry(g: GuiGraphicsExtractor, entry: ConfigEntry<*>, x: Int, y: Int, w: Int, mx: Int, my: Int) {
        if (entry is HudEditEntry) {
            g.fill(x, y, x + w, y + ENT_H, BTN_BG)
            g.centeredText(guiFont, styled("Edit Position"), x + w / 2, y + (ENT_H - 8) / 2, TEXT)
            return
        }
        if (entry is ButtonEntry) {
            g.fill(x, y, x + w, y + ENT_H, BTN_BG)
            g.centeredText(guiFont, styled(entry.label), x + w / 2, y + (ENT_H - 8) / 2, TEXT)
            return
        }
        g.fill(x, y, x + w, y + ENT_H, ENT_BG)
        g.text(guiFont, styled(fmtLabel(entry.name)), x + 2, y + (ENT_H - 8) / 2, TEXT_DIM)

        when (entry) {
            is BooleanEntry -> {
                val bx = x + w - 28
                g.fill(bx, y + 1, bx + 26, y + ENT_H - 1, if (entry.value) BTN_ON else BTN_OFF)
                g.centeredText(guiFont, styled(if (entry.value) "ON" else "OFF"), bx + 13, y + (ENT_H - 8) / 2, TEXT)
            }
            is IntEntry -> {
                val txt = "${entry.value}"
                g.text(guiFont, styled(txt), x + w - guiFont.width(styled(txt)) - 4, y + (ENT_H - 8) / 2, TEXT)
            }
            is FloatEntry -> {
                val v = entry.value
                val txt = if (v == v.toLong().toFloat()) "${v.toLong()}" else "%.2f".format(v)
                g.text(guiFont, styled(txt), x + w - guiFont.width(styled(txt)) - 4, y + (ENT_H - 8) / 2, TEXT)
            }
            is DoubleEntry -> {
                val v = entry.value.toFloat()
                val txt = if (v == v.toLong().toFloat()) "${v.toLong()}" else "%.2f".format(v)
                g.text(guiFont, styled(txt), x + w - guiFont.width(styled(txt)) - 4, y + (ENT_H - 8) / 2, TEXT)
            }
            is StringEntry -> {
                val fx = x + w - 66
                val textX = fx + 2
                val active = entry == editingString
                g.fill(fx, y + 1, fx + 64, y + ENT_H - 1, if (active) shade(40, 0.25f) else BTN_BG)
                g.enableScissor(textX, y + 1, fx + 64, y + ENT_H - 1)
                if (active && entryField.hasSelection) {
                    val sx = textX - entryField.scrollPx + guiFont.width(styled(entryField.text.substring(0, entryField.selMin)))
                    val ex = textX - entryField.scrollPx + guiFont.width(styled(entryField.text.substring(0, entryField.selMax)))
                    g.fill(sx, y + 2, ex, y + ENT_H - 2, argb(170, 60, 110, 210))
                }
                val displayText = if (active) entryField.text else entry.value
                g.text(guiFont, styled(displayText), textX - (if (active) entryField.scrollPx else 0), y + (ENT_H - 8) / 2, TEXT)
                if (active && cursorVisible) {
                    val cx = textX - entryField.scrollPx + guiFont.width(styled(entryField.text.substring(0, entryField.cursor)))
                    g.fill(cx, y + 2, cx + 1, y + ENT_H - 2, TEXT)
                }
                g.disableScissor()
            }
            is ColorEntry -> {
                val sx = x + w - 16
                g.fill(sx, y + 1, sx + 14, y + ENT_H - 1, entry.value.argb)
                g.outline(sx, y + 1, 14, ENT_H - 2, TEXT_DIM)
                if (entry == expandedColorEntry)
                    g.text(guiFont, jbMono("\u25bc"), sx - 9, y + (ENT_H - 8) / 2, TEXT_DIM)
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
                g.text(guiFont, jbMono(if (isOpen) "\u25bc" else "\u25b2"), ex + ew - 9, y + (ENT_H - 8) / 2, TEXT_DIM)
            }
            is IntRangeEntry -> {
                val txt = "${entry.value.first} - ${entry.value.second}"
                val styledTxt = styled(txt)
                val tw = guiFont.width(styledTxt)
                g.text(guiFont, styledTxt, x + w - tw - 4, y + (ENT_H - 8) / 2, TEXT)
            }
            is FloatRangeEntry -> {
                val fmt = "%.${entry.decimals}f"
                val txt = "$fmt - $fmt".format(entry.value.first, entry.value.second)
                val styledTxt = styled(txt)
                val tw = guiFont.width(styledTxt)
                g.text(guiFont, styledTxt, x + w - tw - 4, y + (ENT_H - 8) / 2, TEXT)
            }
        }
    }

    private fun drawNumericSliderBar(
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

    private fun drawFloatRangeSliders(g: GuiGraphicsExtractor, entry: FloatRangeEntry, x: Int, y: Int, w: Int) {
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

    private fun constrainDropdownScroll() {
        if (ClickGui.currentMode.value != ClickGui.Mode.DROPDOWN) return
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

    override fun mouseClicked(event: MouseButtonEvent, inBounds: Boolean): Boolean {
        val mx = event.x().toInt(); val my = event.y().toInt(); val btn = event.button()

        if (editingString != null) { editingString!!.value = entryField.text; editingString = null }

        val enumExp = expandedEnum
        if (enumExp != null) {
            // Only handle dropdown clicks if the dropdown is visible in the current mode
            val isSidebar = ClickGui.currentMode.value == ClickGui.Mode.SIDEBAR
            val dropdownInSidebar = isSidebar && enumDropdownY >= sidebarPaneY && enumDropdownY < sidebarPaneY + 320
            val dropdownInFloating = !isSidebar && enumDropdownY < 10000 // always true for floating, adjust if needed
            if ((isSidebar && dropdownInSidebar) || (!isSidebar && dropdownInFloating)) {
                if (handleEnumDropdownClick(enumExp, enumDropdownX, enumDropdownY, enumDropdownW, mx, my)) return true
                // clicked outside dropdown, close it.
                expandedEnum = null
                return true
            } else {
                // Dropdown is not visible in this mode, ignore click
                expandedEnum = null
            }
        }

        // Config panel (floating mode only)
        if (ClickGui.currentMode.value != ClickGui.Mode.SIDEBAR) {
            if (presetFieldActive) presetFieldActive = false
            val px = cfgPanelX; val py = cfgPanelY - dropdownScroll
            val expanded = !cfgPanelCollapsed

            // Header click
            if (my in py until py + HDR_H && mx in px until px + PNL_W) {
                bringConfigToFront()
                if (btn == 0) { draggingCfgPanel = true; cfgDragOffX = mx - px; cfgDragOffY = my - py }
                else if (btn == 1) cfgPanelCollapsed = !cfgPanelCollapsed
                return true
            }

            // Body clicks
            if (expanded) {
                val panelH = HDR_H + cfgPanelBodyH()
                if (mx in px until px + PNL_W && my in py + HDR_H until py + panelH) {
                    var y = py + HDR_H
                    // Name input row
                    if (my in y until y + ENT_H) {
                        presetFieldActive = true
                        draggingPresetField = true
                        val relX = mx - px - 5
                        presetField.apply { cursor = posFromPixel(relX); selAnchor = cursor; clampScroll(PNL_W - 10) }
                        return true
                    }
                    y += ENT_H
                    // Buttons row
                    if (my in y until y + MOD_H) {
                        val btnW = PNL_W / 3
                        val name = presetField.text.ifBlank { "default" }
                        when {
                            mx in px until px + btnW -> {
                                ConfigManager.savePreset(name)
                                NotificationManager.show("Config Saved", name)
                            }
                            mx in px + btnW until px + btnW * 2 -> {
                                val exists = name in ConfigManager.listPresets()
                                ConfigManager.loadPreset(name)
                                if (exists) NotificationManager.show("Config Loaded", name)
                                else NotificationManager.show("Not Found", name)
                            }
                            else -> ConfigManager.openPresetFolder()
                        }
                        return true
                    }
                    y += MOD_H
                    // Preset list rows
                    for (preset in ConfigManager.listPresets()) {
                        if (my in y until y + ENT_H) {
                            presetField.set(preset)
                            presetField.clampScroll(PNL_W - 10)
                            presetNameBuffer = preset
                            return true
                        }
                        y += ENT_H
                    }
                    return true
                }
            }
        }
        if (ClickGui.currentMode.value == ClickGui.Mode.SIDEBAR) {
            val pw = 480; val ph = 320
            val px2 = sidebarPaneX; val py2 = sidebarPaneY
            val tabH = 24; val catW = 110
            val contentX2 = px2 + catW; val contentW2 = pw - catW

            val logoH = 24
            if (mx in px2 until px2 + pw && my in py2 until py2 + logoH) {
                if (btn == 0) { draggingSidebar = true; sidebarDragOffX = mx - px2; sidebarDragOffY = my - py2 }
                return true
            }
            if (mx in px2 until px2 + pw && my in py2 + logoH until py2 + logoH + tabH) {
                if (btn == 0) {
                    val tabW = pw / 2
                    val ti = (mx - px2) / tabW
                    sidebarTab = ti.coerceIn(0, 1)
                    sidebarDetailMod = null
                }
                return true
            }
            if (sidebarTab == 0 && mx in px2 until contentX2 && my in py2 + logoH + tabH until py2 + ph) {
                if (btn == 0) {
                    var cy = py2 + logoH + tabH + 6
                    for (cat in Module.Category.entries) {
                        if (my in cy until cy + 18) { selectedCategory = cat; sidebarDetailMod = null; return true }
                        cy += 20
                    }
                }
                return true
            }
            val bodyX = if (sidebarTab == 0) contentX2 else px2
            val bodyW2 = if (sidebarTab == 0) contentW2 else pw
            if (mx in bodyX until px2 + pw && my in py2 + logoH + tabH until py2 + ph) {
                val bodyY = py2 + logoH + tabH
                val bodyH = ph - logoH - tabH - 12

                if (sidebarTab == 0) {
                    val detailMod = sidebarDetailMod
                    if (detailMod != null) {
                        val headerY = bodyY + 6
                        val backBtnH = 18
                        val nameH = guiFont.lineHeight + 4
                        val descMaxW = bodyW2 - 80
                        val descLines = if (detailMod.description.isBlank()) emptyList() else {
                            val words = detailMod.description.split(" ")
                            val lines = mutableListOf<String>()
                            var cur = ""
                            for (word in words) {
                                val candidate = if (cur.isEmpty()) word else "$cur $word"
                                if (guiFont.width(styled(candidate)) <= descMaxW) cur = candidate
                                else { if (cur.isNotEmpty()) lines += cur; cur = word }
                            }
                            if (cur.isNotEmpty()) lines += cur
                            lines
                        }
                        val descLineH = (guiFont.lineHeight * 1.3).toInt()
                        val descH = descLines.size * descLineH
                        val tgH = 18
                        val toggleH = tgH + 8
                        val staticH = backBtnH + nameH + descH + 8 + toggleH
                        val entries = configEntries(detailMod)
                        var entriesH = 0
                        for (entry in entries) {
                            entriesH += ENT_H
                            if (entry is IntEntry || entry is FloatEntry || entry is DoubleEntry) entriesH += ENT_H
                            if (entry is IntRangeEntry || entry is FloatRangeEntry) entriesH += ENT_H
                            if (entry == expandedColorEntry && entry is ColorEntry) entriesH += colorChannelCount(entry) * ENT_H
                        }
                        sidebarConfigScrollMax = (entriesH - (bodyH - staticH)).coerceAtLeast(0).toFloat()
                        if (sidebarConfigScroll > sidebarConfigScrollMax) sidebarConfigScroll = sidebarConfigScrollMax
                        if (sidebarConfigScroll < 0f) sidebarConfigScroll = 0f
                        var ey = headerY
                        if (my in ey until ey + backBtnH && mx in bodyX + 4 until bodyX + 4 + 48) {
                            sidebarDetailMod = null; return true
                        }
                        ey += backBtnH
                        ey += nameH
                        ey += descH + 8
                        val tgY = ey
                        if (my in tgY until tgY + tgH && mx in bodyX + 8 until bodyX + 8 + 60) {
                            if (btn == 0 && !detailMod.isProtected) detailMod.toggle()
                            return true
                        }
                        ey += toggleH
                        val configRegionTop = ey
                        val configRegionBottom = bodyY + 6 + (ph - logoH - tabH)
                        var entryY = configRegionTop - sidebarConfigScroll.toInt()
                        for (entry in entries) {
                            if (entryY + ENT_H > configRegionBottom) break
                            if (entryY + ENT_H >= configRegionTop) {
                                if (my in entryY until entryY + ENT_H) {
                                    if (entry is HudEditEntry && detailMod is HudModule) {
                                        minecraft.setScreen(HudEditorScreen(detailMod, this))
                                    } else {
                                        handleEntryClick(entry, bodyX + 4, entryY, bodyW2 - 8, mx, btn)
                                    }
                                    return true
                                }
                            }
                            entryY += ENT_H
                            if (entry is IntEntry || entry is FloatEntry || entry is DoubleEntry) {
                                if (entryY + ENT_H > configRegionBottom) break
                                if (entryY + ENT_H >= configRegionTop) {
                                    if (my in entryY until entryY + ENT_H) { handleNumericBarClick(entry, bodyX + 4, entryY, bodyW2 - 8, mx, btn); return true }
                                }
                                entryY += ENT_H
                            }
                            if (entry is IntRangeEntry) {
                                if (entryY + ENT_H > configRegionBottom) break
                                if (entryY + ENT_H >= configRegionTop) {
                                    if (handleRangeClick(entry, bodyX + 6, entryY, bodyW2 - 8, mx, my)) return true
                                }
                                entryY += ENT_H
                            }
                            if (entry is FloatRangeEntry) {
                                if (entryY + ENT_H > configRegionBottom) break
                                if (entryY + ENT_H >= configRegionTop) {
                                    if (handleFloatRangeClick(entry, bodyX + 6, entryY, bodyW2 - 8, mx, my)) return true
                                }
                                entryY += ENT_H
                            }
                            if (entry == expandedColorEntry && entry is ColorEntry) {
                                val colorH = colorChannelCount(entry) * ENT_H
                                if (entryY + colorH > configRegionBottom) break
                                if (entryY + colorH >= configRegionTop) {
                                    if (handleColorClick(entry, bodyX + 6, entryY, bodyW2 - 8, mx, my)) return true
                                }
                                entryY += colorH
                            }
                        }
                    } else {
                        val mods = selectedCategory?.let { ModuleManager.getByCategory(it) } ?: emptyList()
                        val lineH = guiFont.lineHeight
                        val cardPad = 4
                        val nameTopPad = 8
                        val nameBottomPad = 8
                        val descLineSpacingF = 1.3f
                        val cardList = mods.map { mod ->
                            val descMaxW = bodyW2 - 80
                            val descLines = if (mod.description.isBlank()) emptyList() else {
                                val words = mod.description.split(" ")
                                val lines = mutableListOf<String>()
                                var cur = ""
                                for (word in words) {
                                    val candidate = if (cur.isEmpty()) word else "$cur $word"
                                    if (guiFont.width(styled(candidate)) <= descMaxW) cur = candidate
                                    else { if (cur.isNotEmpty()) lines += cur; cur = word }
                                }
                                if (cur.isNotEmpty()) lines += cur
                                lines
                            }
                            val descLineSpacing = (lineH * descLineSpacingF).toInt()
                            val cardH = nameTopPad + lineH + (if (descLines.isEmpty()) 0 else descLines.size * descLineSpacing + nameBottomPad) + 8
                            Triple(mod, descLines, cardH)
                        }
                        var my2 = bodyY + 6 - sidebarScroll.toInt()
                        for ((mod, descLines, cardH) in cardList) {
                            if (my2 + cardH < bodyY + 6) { my2 += cardH + cardPad; continue }
                            if (my2 > bodyY + 6 + bodyH) break
                            if (my in my2 until my2 + cardH) {
                                val tgX = bodyX + bodyW2 - 44
                                val tgY = my2 + cardH / 2 - 6
                                if (btn == 0 && mx in tgX until tgX + 40 && my in tgY until tgY + 12) {
                                    if (!mod.isProtected) mod.toggle()
                                } else if (btn == 0) {
                                    sidebarDetailMod = mod
                                }
                                return true
                            }
                            my2 += cardH + cardPad
                        }
                    }
                } else {
                    var ey = bodyY + 6
                    if (my in ey until ey + ENT_H) {
                        presetFieldActive = true; draggingPresetField = true
                        val relX = mx - bodyX - 5
                        presetField.apply { cursor = posFromPixel(relX); selAnchor = cursor; clampScroll(bodyW2 - 10) }
                        return true
                    }
                    ey += ENT_H
                    if (my in ey until ey + MOD_H) {
                        val btnW2 = bodyW2 / 3
                        val name = presetField.text.ifBlank { "default" }
                        when {
                            mx in bodyX until bodyX + btnW2 -> { ConfigManager.savePreset(name); NotificationManager.show("Config Saved", name) }
                            mx in bodyX + btnW2 until bodyX + btnW2 * 2 -> { val exists = name in ConfigManager.listPresets(); ConfigManager.loadPreset(name); if (exists) NotificationManager.show("Config Loaded", name) else NotificationManager.show("Not Found", name) }
                            else -> ConfigManager.openPresetFolder()
                        }
                        return true
                    }
                    ey += MOD_H
                    for (preset in ConfigManager.listPresets()) {
                        if (my in ey until ey + ENT_H) { presetField.set(preset); presetField.clampScroll(bodyW2 - 10); presetNameBuffer = preset; return true }
                        ey += ENT_H
                    }
                }
                return true
            }
            return true
        }

        for (cat in renderOrder.asReversed()) {
            val pos = positions[cat] ?: continue
            val px = pos.first
            val py = pos.second - dropdownScroll
            val expanded = cat!! !in collapsed

            if (my in py until py + HDR_H && mx in px until px + PNL_W) {
                bringToFront(cat)
                if (btn == 0) { draggingCat = cat; dragOffX = mx - px; dragOffY = my - py }
                else if (btn == 1) { if (cat in collapsed) collapsed.remove(cat) else collapsed.add(cat) }
                return true
            }

            if (!expanded) continue

            val panelH = fullPanelHeight(cat)
            if (mx !in px until px + PNL_W || my !in py + HDR_H until py + panelH) continue

            bringToFront(cat)

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
                            if (entry is HudEditEntry && mod is HudModule) {
                                minecraft.setScreen(HudEditorScreen(mod, this))
                            } else {
                                handleEntryClick(entry, px + 3, y, PNL_W - 3, mx, btn)
                            }
                            return true
                        }
                        y += ENT_H

                        if (entry is IntEntry || entry is FloatEntry || entry is DoubleEntry) {
                            if (my in y until y + ENT_H) {
                                handleNumericBarClick(entry, px + 3, y, PNL_W - 3, mx, btn)
                                return true
                            }
                            y += ENT_H
                        }
                        if (entry is IntRangeEntry) {
                            if (handleRangeClick(entry, px + 6, y, PNL_W - 6, mx, my)) return true
                            y += ENT_H
                        }
                        if (entry is FloatRangeEntry) {
                            if (handleFloatRangeClick(entry, px + 6, y, PNL_W - 6, mx, my)) return true
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
    
    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (ClickGui.currentMode.value == ClickGui.Mode.SIDEBAR) {
            if (sidebarTab == 0 && sidebarDetailMod != null) {
                val pw = 480; val ph = 320
                val px = sidebarPaneX; val py = sidebarPaneY
                val catW = 110
                val contentX = px + catW
                val contentW = pw - catW
                val logoH = 24; val tabH = 24
                val bodyX = contentX; val bodyY = py + logoH + tabH; val bodyW = contentW; val bodyH = ph - logoH - tabH - 12
                val detailMod = sidebarDetailMod
                val headerY = bodyY + 6
                val backBtnH = 18
                val nameH = guiFont.lineHeight + 4
                val descMaxW = bodyW - 80
                val descLines = if (detailMod != null && detailMod.description.isNotBlank()) {
                    val words = detailMod.description.split(" ")
                    val lines = mutableListOf<String>()
                    var cur = ""
                    for (word in words) {
                        val candidate = if (cur.isEmpty()) word else "$cur $word"
                        if (guiFont.width(styled(candidate)) <= descMaxW) cur = candidate
                        else { if (cur.isNotEmpty()) lines += cur; cur = word }
                    }
                    if (cur.isNotEmpty()) lines += cur
                    lines
                } else emptyList()
                val descLineH = (guiFont.lineHeight * 1.3).toInt()
                val descH = descLines.size * descLineH
                val tgH = 18
                val toggleH = tgH + 8
                val staticH = backBtnH + nameH + descH + 8 + toggleH
                val entries = if (detailMod != null) configEntries(detailMod) else emptyList()
                var entriesH = 0
                for (entry in entries) {
                    entriesH += ENT_H
                    if (entry is IntEntry || entry is FloatEntry || entry is DoubleEntry) entriesH += ENT_H
                    if (entry is IntRangeEntry || entry is FloatRangeEntry) entriesH += ENT_H
                    if (entry == expandedColorEntry && entry is ColorEntry) entriesH += colorChannelCount(entry) * ENT_H
                }
                sidebarConfigScrollMax = (entriesH - (bodyH - staticH)).coerceAtLeast(0).toFloat()
                if (mouseX in bodyX.toDouble()..(bodyX + bodyW).toDouble() && mouseY in bodyY.toDouble()..(bodyY + bodyH).toDouble()) {
                    sidebarConfigScroll = (sidebarConfigScroll - scrollY * 32f).coerceIn(0.0, sidebarConfigScrollMax.toDouble()).toFloat()
                    return true
                }
            }
            if (sidebarTab == 0 && sidebarDetailMod == null) {
                val pw = 480; val ph = 320
                val px = sidebarPaneX; val py = sidebarPaneY
                val catW = 110
                val contentX = px + catW
                val contentW = pw - catW
                val logoH = 24; val tabH = 24
                val bodyX = contentX; val bodyY = py + logoH + tabH; val bodyW = contentW; val bodyH = ph - logoH - tabH
                if (mouseX in bodyX.toDouble()..(bodyX + bodyW).toDouble() && mouseY in bodyY.toDouble()..(bodyY + bodyH).toDouble()) {
                    sidebarScroll = (sidebarScroll - scrollY * 32f).coerceIn(0.0, sidebarScrollMax.toDouble()).toFloat()
                    return true
                }
            }
        } else {
            val scrollAmount = (scrollY * 24).toInt()
            
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
            
            val potentialNewScroll = dropdownScroll - scrollAmount
            val newScroll = potentialNewScroll.coerceIn(0, maxScrollOffset)
            
            if (newScroll != dropdownScroll) {
                dropdownScroll = newScroll
                return true
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    private fun handleEntryClick(entry: ConfigEntry<*>, x: Int, y: Int, w: Int, mx: Int, btn: Int) {
        when (entry) {
            is BooleanEntry -> entry.value = !entry.value
            is StringEntry  -> {
                editingString = entry
                entryField.set(entry.value)
                val fx = x + w - 66
                entryFieldTextX = fx + 2
                entryField.cursor = entryField.posFromPixel(mx - (fx + 2))
                entryField.selAnchor = entryField.cursor
                draggingStringEntry = true
                entryField.clampScroll(60)
            }
            is ColorEntry   -> expandedColorEntry = if (expandedColorEntry == entry) null else entry
            is KeybindEntry -> listeningKeybind   = if (listeningKeybind  == entry) null else entry
            is EnumEntry<*> -> expandedEnum = if (expandedEnum == entry) null else entry
            is ButtonEntry  -> entry.action()
        }
    }

    private fun handleNumericBarClick(entry: ConfigEntry<*>, x: Int, y: Int, w: Int, mx: Int, btn: Int) {
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

    private fun handleFloatRangeClick(entry: FloatRangeEntry, x: Int, y: Int, w: Int, mx: Int, my: Int): Boolean {
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
            if (key == GLFW.GLFW_KEY_ESCAPE) { listeningKeybind!!.value = GLFW.GLFW_KEY_UNKNOWN; listeningKeybind = null }
            else { listeningKeybind!!.value = key; listeningKeybind!!.suppressNextPress(); listeningKeybind = null }
            return true
        }

        if (presetFieldActive) {
            val f = presetField
            val ctrl  = (event.modifiers() and GLFW.GLFW_MOD_CONTROL) != 0
            val shift = (event.modifiers() and GLFW.GLFW_MOD_SHIFT)   != 0
            when {
                key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER -> presetFieldActive = false
                key == GLFW.GLFW_KEY_ESCAPE    -> presetFieldActive = false
                ctrl && key == GLFW.GLFW_KEY_A -> f.selectAll()
                ctrl && key == GLFW.GLFW_KEY_C -> { val s = f.copy(); if (s.isNotEmpty()) minecraft.keyboardHandler.clipboard = s }
                ctrl && key == GLFW.GLFW_KEY_X -> { val s = f.cut();  if (s.isNotEmpty()) minecraft.keyboardHandler.clipboard = s }
                ctrl && key == GLFW.GLFW_KEY_V -> {
                    val clip = (minecraft.keyboardHandler.clipboard ?: "").replace(Regex("[</*?\"\\\\>:|]+"), "_")
                    f.insert(clip)
                }
                key == GLFW.GLFW_KEY_BACKSPACE -> if (ctrl) f.backspaceWord() else f.backspace()
                key == GLFW.GLFW_KEY_DELETE    -> f.deleteForward()
                key == GLFW.GLFW_KEY_LEFT      -> if (ctrl) f.wordMove(false, shift) else f.move(-1, shift)
                key == GLFW.GLFW_KEY_RIGHT     -> if (ctrl) f.wordMove(true,  shift) else f.move( 1, shift)
                key == GLFW.GLFW_KEY_HOME      -> f.home(shift)
                key == GLFW.GLFW_KEY_END       -> f.end(shift)
            }
            f.clampScroll(PNL_W - 10)
            presetNameBuffer = f.text
            return true
        }

        if (editingString != null) {
            val f = entryField
            val ctrl  = (event.modifiers() and GLFW.GLFW_MOD_CONTROL) != 0
            val shift = (event.modifiers() and GLFW.GLFW_MOD_SHIFT)   != 0
            when {
                key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER -> { editingString!!.value = f.text; editingString = null }
                key == GLFW.GLFW_KEY_ESCAPE    -> { editingString!!.value = f.text; editingString = null }
                key == GLFW.GLFW_KEY_BACKSPACE -> if (ctrl) f.backspaceWord() else f.backspace()
                key == GLFW.GLFW_KEY_DELETE    -> f.deleteForward()
                key == GLFW.GLFW_KEY_LEFT      -> if (ctrl) f.wordMove(false, shift) else f.move(-1, shift)
                key == GLFW.GLFW_KEY_RIGHT     -> if (ctrl) f.wordMove(true,  shift) else f.move( 1, shift)
                key == GLFW.GLFW_KEY_HOME      -> f.home(shift)
                key == GLFW.GLFW_KEY_END       -> f.end(shift)
                ctrl && key == GLFW.GLFW_KEY_A -> f.selectAll()
                ctrl && key == GLFW.GLFW_KEY_C -> { val s = f.copy(); if (s.isNotEmpty()) minecraft.keyboardHandler.clipboard = s }
                ctrl && key == GLFW.GLFW_KEY_X -> { val s = f.cut();  if (s.isNotEmpty()) minecraft.keyboardHandler.clipboard = s }
                ctrl && key == GLFW.GLFW_KEY_V -> f.insert(minecraft.keyboardHandler.clipboard ?: "")
            }
            f.clampScroll(60)
            return true
        }

        if (key == GLFW.GLFW_KEY_ESCAPE || key == ClickGui.keybind.value) { onClose(); return true }

        // route unhandled keys to game input
        val inputKey = InputConstants.getKey(event)
        KeyMapping.set(inputKey, true)
        KeyMapping.click(inputKey)
        return false
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        if (listeningKeybind != null || editingString != null || presetFieldActive) return true
        val inputKey = InputConstants.getKey(event)
        // release keys only if not physically held
        val physHeld = GLFW.glfwGetKey(minecraft.window.handle(), event.key()) == GLFW.GLFW_PRESS
        if (!physHeld) KeyMapping.set(inputKey, false)
        return false
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (presetFieldActive) {
            val ch = event.codepointAsString()
            if (ch.matches(Regex("[^</*?\"\\\\>:|]+"))) {
                presetField.insert(ch)
                presetField.clampScroll(PNL_W - 10)
                presetNameBuffer = presetField.text
            }
            return true
        }
        if (editingString != null) { entryField.insert(event.codepointAsString()); entryField.clampScroll(60); return true }
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
                is SliderDrag.FloatRange -> {
                    val e = slider.entry
                    val scale = Math.pow(10.0, e.decimals.toDouble()).toFloat()
                    val v = (Math.round((e.min + t * (e.max - e.min)) * scale).toFloat()) / scale
                    val (lo, hi) = e.value
                    e.value = if (slider.isHigh) lo to v.coerceAtLeast(lo) else v.coerceAtMost(hi) to hi
                }
            }
            return true
        }
        if (draggingPresetField) {
            val relX = event.x().toInt() - cfgPanelX - 5
            presetField.apply { cursor = posFromPixel(relX); clampScroll(PNL_W - 10) }
            return true
        }
        if (draggingStringEntry && editingString != null) {
            entryField.apply { cursor = posFromPixel(event.x().toInt() - entryFieldTextX); clampScroll(60) }
            return true
        }
        if (draggingSidebar) {
            sidebarPaneX = event.x().toInt() - sidebarDragOffX
            sidebarPaneY = event.y().toInt() - sidebarDragOffY
            return true
        }
        if (draggingCfgPanel) {
            cfgPanelX = event.x().toInt() - cfgDragOffX
            cfgPanelY = event.y().toInt() - cfgDragOffY + dropdownScroll
            return true
        }
        val cat = draggingCat ?: return super.mouseDragged(event, dragX, dragY)
        positions[cat] = Pair(event.x().toInt() - dragOffX, event.y().toInt() - dragOffY + dropdownScroll)
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        draggingCat = null
        draggingSlider = null
        draggingCfgPanel = false
        draggingSidebar = false
        draggingPresetField = false
        draggingStringEntry = false
        constrainDropdownScroll()
        return super.mouseReleased(event)
    }

    override fun onClose() {
        super.onClose()
        presetNameBuffer = presetField.text
        ClickGui.disable()
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

    private fun bringToFront(cat: Module.Category) {
        renderOrder.remove(cat)
        renderOrder.add(cat)
    }

    private fun bringConfigToFront() {
        renderOrder.remove(null)
        renderOrder.add(null)
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
        GLFW.GLFW_KEY_LEFT_SHIFT    -> "LShift"
        GLFW.GLFW_KEY_RIGHT_SHIFT   -> "RShift"
        GLFW.GLFW_KEY_LEFT_CONTROL,
        GLFW.GLFW_KEY_RIGHT_CONTROL -> "Ctrl"
        GLFW.GLFW_KEY_LEFT_ALT,
        GLFW.GLFW_KEY_RIGHT_ALT     -> "Alt"
        else -> GLFW.glfwGetKeyName(keyCode, 0)?.uppercase() ?: "K$keyCode"
    }
}
