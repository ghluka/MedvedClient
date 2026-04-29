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
import me.ghluka.medved.MedvedClient
import me.ghluka.medved.gui.components.*
import me.ghluka.medved.gui.helpers.InputHelper
import me.ghluka.medved.gui.modes.DropdownMode
import me.ghluka.medved.gui.modes.SidebarMode
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

class ClickGui : Screen(Component.literal("Medved")) {

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
        var colorPickerX = 0
        var colorPickerY = 0
        var colorPickerW = 0
        var colorPickerH = 0
        internal var defaultPositions = mapOf<Module.Category, Pair<Int, Int>>()
        internal var defaultCfgPanelY = -1
        var dropdownScroll = 0

        fun resetPos() {
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


    internal var selectedCategory: Module.Category? = null
    internal var sidebarTab = 0 // 0=Modules, 1=Config
    internal var sidebarDetailMod: Module? = null // module whose config page is open
    internal var draggingSidebar = false
    internal var sidebarDragOffX = 0
    internal var sidebarDragOffY = 0
    internal var sidebarScroll = 0f
    internal var sidebarScrollMax = 0f
    internal var sidebarConfigScroll = 0f
    internal var sidebarConfigScrollMax = 0f

    internal var editingString: StringEntry? = null
    internal val entryField = TextField()
    internal var draggingStringEntry = false
    internal var entryFieldTextX = 0
    internal var listeningKeybind: KeybindEntry? = null
    internal var draggingCat: Module.Category? = null
    internal var dragOffX = 0
    internal var dragOffY = 0
    internal var hoveredMod: Module? = null
    internal var draggingSlider: SliderDrag? = null
    internal var editingColorEntry: ColorEntry? = null
    internal var editingColorChannel: Int? = null
    internal var editingColorHex = false
    internal var colorPickerMode = ColorPickerMode.CUSTOM
    internal var colorPickerModeExpanded = false
    internal var colorPickerCustomValue: Color? = null
    internal var presetFieldActive = false
    internal val presetField = TextField()
    internal var draggingCfgPanel = false
    internal var cfgDragOffX = 0
    internal var cfgDragOffY = 0
    internal var draggingPresetField = false
    internal val cursorVisible get() = (System.currentTimeMillis() / 530) % 2 == 0L

    internal var enumDropdownX = 0
    internal var enumDropdownY = 0
    internal var enumDropdownW = 0

    enum class ColorPickerMode { CUSTOM, THEME, CHROMA }

    internal sealed interface SliderDrag {
        val barX: Int
        val barW: Int
        data class Numeric(val entry: ConfigEntry<*>, override val barX: Int, override val barW: Int) : SliderDrag
        data class ColorChannel(val entry: ColorEntry, val channel: Int, override val barX: Int, override val barW: Int) : SliderDrag
        data class ColorMap(val entry: ColorEntry, val mapX: Int, val mapY: Int, val mapW: Int, val mapH: Int) : SliderDrag {
            override val barX: Int get() = mapX
            override val barW: Int get() = mapW
        }
        data class ColorHue(val entry: ColorEntry, override val barX: Int, override val barW: Int, val barY: Int, val barH: Int) : SliderDrag
        data class ColorAlpha(val entry: ColorEntry, override val barX: Int, override val barW: Int, val barY: Int, val barH: Int) : SliderDrag
        data class ChromaSpeed(val entry: ColorEntry, override val barX: Int, override val barW: Int, val barY: Int, val barH: Int) : SliderDrag
        data class Range(val entry: IntRangeEntry, val isHigh: Boolean, override val barX: Int, override val barW: Int) : SliderDrag
        data class FloatRange(val entry: FloatRangeEntry, val isHigh: Boolean, override val barX: Int, override val barW: Int) : SliderDrag
    }

    internal val PNL_W = 160  // panel width
    internal val HDR_H = 18   // header bar height
    internal val MOD_H = 16   // module row height
    internal val ENT_H = 13   // config entry row height
    internal val SLI_X = 78   // slider bar x offset within entry row

    internal fun argb(a: Int, r: Int, g: Int, b: Int) =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    internal fun shade(base: Int, mix: Float, alpha: Int = 255): Int {
        val c = Colour.bg.liveColor(Colour.bg.value)
        val r = (base + (c.r - base) * mix).toInt().coerceIn(0, 255)
        val g = (base + (c.g - base) * mix).toInt().coerceIn(0, 255)
        val b = (base + (c.b - base) * mix).toInt().coerceIn(0, 255)
        return argb(alpha, r, g, b)
    }

    internal val BG       get() = shade(8, 0.05f, 100)
    internal val PNL_BG   get() = shade(18, 0.08f, 240)
    internal val HDR_BG   get() = shade(20, 0.20f)
    internal val HDR_ACC  get() = Colour.bg.liveColor(Colour.bg.value).argb
    internal val MOD_NORM get() = shade(20, 0.06f)
    internal val MOD_HOV  get() = shade(30, 0.10f)
    internal val ACCENT   get() = Colour.accent.liveColor(Colour.accent.value).argb
    internal val ENT_BG   get() = shade(14, 0.04f)
    internal val SLI_BG   get() = shade(30, 0.12f)
    internal val SLI_FG   get() = with(Colour.accent.liveColor(Colour.accent.value)) { argb(255, (r * 0.8).toInt(), (g * 0.8).toInt(), (b * 0.8).toInt()) }
    internal val BTN_BG   get() = shade(35, 0.12f)
    internal val BTN_ON   = argb(255,  50, 175,  60)
    internal val BTN_OFF  = argb(255, 170,  55,  55)
    internal val TEXT     = argb(255, 215, 215, 228)
    internal val TEXT_DIM = argb(255, 118, 118, 140)
    internal val guiFont  get() = Font.getFont()
    internal fun styled(text: String) = Font.styledText(text)
    internal fun plain(text: String): Component = Component.literal(text)
    internal fun jbMono(text: String): Component = Component.literal(text).withStyle(
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
            DropdownMode.render(this, g, mx, my)
        } else {
            SidebarMode.render(this, g, mx, my)
        }
        // draw dropdown overlay (always above panels)
        val enumExp = expandedEnum
        if (enumExp != null) {
            drawEnumDropdown(g, enumExp, enumDropdownX, enumDropdownY, enumDropdownW, mx, my)
        }
        val colorExp = expandedColorEntry
        if (colorExp != null) {
            drawColorPicker(g, colorExp, colorPickerX, colorPickerY, colorPickerW, mx, my)
        }
        val hov = hoveredMod
        if (hov != null && ClickGui.showDescriptions.value && hov.description.isNotBlank() &&
            ClickGui.currentMode.value == ClickGui.Mode.DROPDOWN &&
            expandedEnum == null && expandedColorEntry == null) {
            drawTooltip(g, hov.description, mx, my)
        }
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {
        ConfigManager.refreshDynamicColors()
    }

    internal fun cfgPanelBodyH(): Int {
        val presets = ConfigManager.listPresets()
        return ENT_H + MOD_H + presets.size.coerceAtLeast(1) * ENT_H
    }

    internal inner class TextField {
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

    internal fun drawSidebarMode(g: GuiGraphicsExtractor, mx: Int, my: Int) {
        SidebarMode.render(this, g, mx, my)
    }

    internal fun fullPanelHeight(cat: Module.Category): Int {
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
                if (colorExp != null) {
                    // overlay picker does not reserve inline height
                }
                val enumExp = entries.firstOrNull { it == expandedEnum } as? EnumEntry<*>
            }
        }
        return h
    }

    internal fun drawModuleName(
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

    internal fun drawEntry(g: GuiGraphicsExtractor, entry: ConfigEntry<*>, x: Int, y: Int, w: Int, mx: Int, my: Int) {
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
                val swatch = if (entry.pickerMode == ColorEntry.PickerMode.CHROMA) liveColorFor(entry) else entry.value
                g.fill(sx, y + 1, sx + 14, y + ENT_H - 1, swatch.argb)
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

    override fun mouseClicked(event: MouseButtonEvent, inBounds: Boolean): Boolean {
        val mx = event.x().toInt(); val my = event.y().toInt(); val btn = event.button()

        if (editingString != null) { editingString!!.value = entryField.text; editingString = null }
        if (editingColorEntry != null) { commitEditingColor(); editingColorEntry = null; editingColorChannel = null; editingColorHex = false }

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

        val colorExp = expandedColorEntry
        if (colorExp != null) {
            if (mx in colorPickerX until colorPickerX + colorPickerW && my in colorPickerY until colorPickerY + colorPickerH) {
                handleColorClick(colorExp, colorPickerX, colorPickerY, colorPickerW, mx, my)
                return true
            }
            expandedColorEntry = null
            return true
        }

        if (ClickGui.currentMode.value == ClickGui.Mode.SIDEBAR) {
            return SidebarMode.handleMouseClick(this, mx, my, btn)
        }
        if (DropdownMode.handleMouseClick(this, mx, my, btn)) return true
        return super.mouseClicked(event, inBounds)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (ClickGui.currentMode.value == ClickGui.Mode.SIDEBAR) {
            if (SidebarMode.handleScroll(this, mouseX, mouseY, scrollY)) return true
        } else {
            if (DropdownMode.handleScroll(this, mouseY, scrollY)) return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    internal fun handleEntryClick(entry: ConfigEntry<*>, x: Int, y: Int, w: Int, mx: Int, btn: Int) {
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
            is ColorEntry   -> {
                expandedColorEntry = if (expandedColorEntry == entry) null else entry
                if (expandedColorEntry == entry) {
                    colorPickerMode = when (entry.pickerMode) {
                        ColorEntry.PickerMode.CUSTOM -> ColorPickerMode.CUSTOM
                        ColorEntry.PickerMode.THEME -> ColorPickerMode.THEME
                        ColorEntry.PickerMode.CHROMA -> ColorPickerMode.CHROMA
                    }
                    colorPickerModeExpanded = false
                    colorPickerCustomValue = entry.customValue
                } else {
                    colorPickerModeExpanded = false
                    editingColorEntry = null
                    editingColorChannel = null
                    editingColorHex = false
                }
            }
            is KeybindEntry -> listeningKeybind   = if (listeningKeybind  == entry) null else entry
            is EnumEntry<*> -> expandedEnum = if (expandedEnum == entry) null else entry
            is ButtonEntry  -> entry.action()
        }
    }













    override fun keyPressed(event: KeyEvent): Boolean {
        if (InputHelper.handleKeyPressed(this, event)) return true

        val key = event.key()

        if (key == GLFW.GLFW_KEY_ESCAPE || key == ClickGui.keybind.value) { onClose(); return true }

        // route unhandled keys to game input
        val inputKey = InputConstants.getKey(event)
        KeyMapping.set(inputKey, true)
        KeyMapping.click(inputKey)
        return false
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        return InputHelper.handleKeyReleased(this, event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        return InputHelper.handleCharTyped(this, event)
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
                is SliderDrag.ColorAlpha -> {
                    val py = (event.y().toInt() - slider.barY).coerceIn(0, slider.barH - 1)
                    val alpha = (255f * (1f - (py.toFloat() / (slider.barH - 1).coerceAtLeast(1)))).roundToInt().coerceIn(0, 255)
                    slider.entry.customValue = slider.entry.customValue.copy(a = alpha)
                    applyDynamicColorState(slider.entry)
                }
                is SliderDrag.ColorMap -> {
                    val px = (event.x().toInt() - slider.mapX).coerceIn(0, slider.mapW - 1)
                    val py = (event.y().toInt() - slider.mapY).coerceIn(0, slider.mapH - 1)
                    val saturation = px.toFloat() / (slider.mapW - 1).coerceAtLeast(1)
                    val value = 1f - py.toFloat() / (slider.mapH - 1).coerceAtLeast(1)
                    if (slider.entry.pickerMode == ColorEntry.PickerMode.CHROMA) {
                        slider.entry.chromaSaturation = saturation
                        slider.entry.chromaBrightness = value
                    } else {
                        val current = slider.entry.customValue
                        val (hue, _, _) = rgbToHsv(current)
                        slider.entry.customValue = hsvToRgb(hue, saturation, value).copy(a = current.a)
                    }
                    applyDynamicColorState(slider.entry)
                }
                is SliderDrag.ColorHue -> {
                    val py = (event.y().toInt() - slider.barY).coerceIn(0, slider.barH - 1)
                    val tY = py.toFloat() / (slider.barH - 1).coerceAtLeast(1)
                    if (slider.entry.pickerMode == ColorEntry.PickerMode.CHROMA) {
                        slider.entry.chromaSpeed = 0.05f + tY * (8f - 0.05f)
                    } else {
                        val newHue = tY * 360f
                        val current = slider.entry.customValue
                        val (_, saturation, value) = rgbToHsv(current)
                        slider.entry.customValue = hsvToRgb(newHue, saturation, value).copy(a = current.a)
                    }
                    applyDynamicColorState(slider.entry)
                }
                is SliderDrag.ChromaSpeed -> {
                    val px = (event.x().toInt() - slider.barX).coerceIn(0, slider.barW - 1)
                    val tX = px.toFloat() / (slider.barW - 1).coerceAtLeast(1)
                    slider.entry.chromaSpeed = 0.05f + tX * (8f - 0.05f)
                    applyDynamicColorState(slider.entry)
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

    internal fun configEntries(mod: Module) = mod.entries.filter {
        it.name != "enabled" && !(mod.isProtected && it.name == "keybind") &&
                (it.visibleWhen?.invoke() != false)
    }

    internal fun colorChannelCount(entry: ColorEntry) = if (entry.allowAlpha) 4 else 3

    internal fun applyColorChannel(entry: ColorEntry, channel: Int, v: Int) {
        val col = entry.customValue
        entry.customValue = when (channel) {
            0 -> col.copy(r = v)
            1 -> col.copy(g = v)
            2 -> col.copy(b = v)
            else -> col.copy(a = v)
        }
        applyDynamicColorState(entry)
    }

    internal fun supportsThemeMode(entry: ColorEntry) = entry !== Colour.accent

    internal fun applyDynamicColorState(entry: ColorEntry) {
        entry.applyDynamicColor(Colour.accent.liveColor(Colour.accent.value), ColorEntry.chromaTimeSeconds(), supportsThemeMode(entry))
    }

    internal fun bringToFront(cat: Module.Category) {
        renderOrder.remove(cat)
        renderOrder.add(cat)
    }

    internal fun bringConfigToFront() {
        renderOrder.remove(null)
        renderOrder.add(null)
    }

    internal fun toggleExpand(mod: Module) {
        if (mod in expandedModules) expandedModules.remove(mod) else expandedModules.add(mod)
        expandedColorEntry = null
        expandedEnum = null
    }

    internal fun drawTooltip(g: GuiGraphicsExtractor, text: String, mx: Int, my: Int) {
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

    internal fun fmtLabel(name: String) =
        name.replace('_', ' ').replaceFirstChar { it.uppercase() }

    internal fun keyName(keyCode: Int): String = when (keyCode) {
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

    companion object {
        fun resetPositions() {
            val mc = Minecraft.getInstance()
            if (mc.gui.screen() is me.ghluka.medved.gui.ClickGui) {
                (mc.gui.screen() as me.ghluka.medved.gui.ClickGui).resetPos()
            }
        }
    }
}
