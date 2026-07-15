package me.ghluka.medved.gui

import me.ghluka.medved.config.ConfigManager
import me.ghluka.medved.config.ConfigGroup
import me.ghluka.medved.config.entry.*
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.ModuleManager
import me.ghluka.medved.module.modules.other.ClickGui
import me.ghluka.medved.module.modules.other.Colour
import me.ghluka.medved.module.modules.other.Font
import me.ghluka.medved.util.*
import me.ghluka.medved.util.Text
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import com.mojang.blaze3d.platform.InputConstants
import me.ghluka.medved.gui.helpers.ITEM_LIST_DROPDOWN_MAX_H
import me.ghluka.medved.gui.helpers.InputHelper
import me.ghluka.medved.gui.helpers.commitEditingColor
import me.ghluka.medved.gui.helpers.filteredItemListRowsFor
import me.ghluka.medved.gui.helpers.findItemByName
import me.ghluka.medved.gui.helpers.getAllItems
import me.ghluka.medved.gui.helpers.hsvToRgb
import me.ghluka.medved.gui.helpers.itemCategories
import me.ghluka.medved.gui.helpers.liveColorFor
import me.ghluka.medved.gui.helpers.pickerRowIconName
import me.ghluka.medved.gui.helpers.pickerRowId
import me.ghluka.medved.gui.helpers.pickerRowLabel
import me.ghluka.medved.gui.helpers.rgbToHsv
import me.ghluka.medved.gui.ui.ClickGuiUiFactory
import me.ghluka.medved.gui.ui.MinecraftUiRenderer
import me.ghluka.medved.gui.ui.UiDocument
import me.ghluka.medved.gui.ui.UiRect
import me.ghluka.medved.gui.ui.UiRuntime
import me.ghluka.medved.gui.ui.ClickGuiUiFactory.Companion.sliderT
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

class ClickGui : Screen(Component.literal("Medved")) {
    private val uiController = ClickGuiUiController(this)

        val collapsed = mutableSetOf<Module.Category>()
        val positions = mutableMapOf<Module.Category, Pair<Int, Int>>()
        val expandedModules = mutableSetOf<Module>()
        var expandedColorEntry: ColorEntry? = null
        var expandedEnum: EnumEntry<*>? = null
        val renderOrder = mutableListOf<Module.Category?>()
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
            renderOrder.add(null)
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
    internal var sidebarTab = 0
    internal var sidebarDetailMod: Module? = null
    internal var draggingSidebar = false
    internal var sidebarDragOffX = 0
    internal var sidebarDragOffY = 0
    internal var sidebarScroll = 0f
    internal var sidebarConfigScroll = 0f

    internal var editingString: StringEntry? = null
    internal val entryField = TextField()
    internal var draggingStringEntry = false
    internal var entryFieldTextX = 0
    internal var entryFieldVisibleW = 60
    internal var listeningKeybind: KeybindEntry? = null
    internal var itemListSearch = TextField()
    internal var draggingCat: Module.Category? = null
    internal var dragOffX = 0
    internal var dragOffY = 0
    internal var hoveredMod: Module? = null
    internal var draggingSlider: SliderDrag? = null
    internal var draggingXmlSlider: XmlSliderDrag? = null
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
    internal var presetFieldTextX = 0
    internal var presetFieldVisibleW = 150
    internal val cursorVisible get() = (System.currentTimeMillis() / 530) % 2 == 0L

    internal var enumDropdownX = 0
    internal var enumDropdownY = 0
    internal var enumDropdownW = 0
    internal var itemListDropdownX = 0
    internal var itemListDropdownY = 0
    internal var itemListDropdownW = 0
    internal var expandedItemList: me.ghluka.medved.config.entry.ItemListEntry? = null
    internal var editingItemListSearch = false
    internal var itemListScroll = 0
    internal var itemListAddedScroll = 0
    internal var itemListCategory: String? = null

    enum class ColorPickerMode { CUSTOM, THEME, CHROMA }

    internal sealed interface SliderDrag {
        val barX: Int
        val barW: Int
        data class ColorMap(val entry: ColorEntry, val mapX: Int, val mapY: Int, val mapW: Int, val mapH: Int) : SliderDrag {
            override val barX: Int get() = mapX
            override val barW: Int get() = mapW
        }
        data class ColorHue(val entry: ColorEntry, override val barX: Int, override val barW: Int, val barY: Int, val barH: Int) : SliderDrag
        data class ColorAlpha(val entry: ColorEntry, override val barX: Int, override val barW: Int, val barY: Int, val barH: Int) : SliderDrag
        data class ChromaSpeed(val entry: ColorEntry, override val barX: Int, override val barW: Int, val barY: Int, val barH: Int) : SliderDrag
    }

    internal sealed interface XmlSliderDrag {
        val bounds: UiRect
        data class IntValue(val entry: IntEntry, override val bounds: UiRect) : XmlSliderDrag
        data class FloatValue(val entry: FloatEntry, override val bounds: UiRect) : XmlSliderDrag
        data class DoubleValue(val entry: DoubleEntry, override val bounds: UiRect) : XmlSliderDrag
        data class IntRangeValue(val entry: IntRangeEntry, val high: Boolean, override val bounds: UiRect) : XmlSliderDrag
        data class FloatRangeValue(val entry: FloatRangeEntry, val high: Boolean, override val bounds: UiRect) : XmlSliderDrag
    }

    internal val PNL_W = 160  // panel width
    internal val HDR_H = 18   // header bar height
    internal val MOD_H = 16   // module row height
    internal val ENT_H = 13   // config entry row height

    internal fun argb(a: Int, r: Int, g: Int, b: Int) =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    internal val ACCENT   get() = Colour.accent.liveColor(Colour.accent.value).argb
    internal val TEXT     = argb(255, 215, 215, 228)
    internal val guiFont  get() = Font.getFont()
    internal fun styled(text: String) = Font.styledText(text)

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
            renderOrder.add(null)
        }
        if (null !in renderOrder) renderOrder.add(null)
        dropdownScroll = 0

        presetField.text = presetNameBuffer
        presetField.end(false)
    }

    override fun isPauseScreen() = false
    override fun isInGameUi() = true

    override fun extractBackground(g: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {
        hoveredMod = null
        uiController.renderMain(g, mx, my)
        // draw dropdown overlay (always above panels)
        val enumExp = expandedEnum
        if (enumExp != null) {
            renderXmlEnumDropdown(g, enumExp, mx, my)
        }
        val colorExp = expandedColorEntry
        if (colorExp != null) {
            renderXmlColorPicker(g, colorExp, mx, my)
        }
        val listExp = expandedItemList
        if (listExp != null) {
            renderXmlItemListDropdown(g, listExp, mx, my)
        }
        val hov = hoveredMod
        if (hov != null && ClickGui.showDescriptions.value && hov.description.isNotBlank() &&
            ClickGui.currentMode.value == ClickGui.Mode.DROPDOWN &&
            expandedEnum == null && expandedColorEntry == null && expandedItemList == null) {
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

        val selMin get() = if (selAnchor < 0) cursor.coerceIn(0, text.length) else minOf(cursor, selAnchor).coerceIn(0, text.length)
        val selMax get() = if (selAnchor < 0) cursor.coerceIn(0, text.length) else maxOf(cursor, selAnchor).coerceIn(0, text.length)
        val hasSelection get() = selAnchor >= 0 && selAnchor != cursor

        fun set(s: String) { text = s; cursor = s.length; selAnchor = -1; scrollPx = 0 }
        fun insert(s: String) {
            clampCursor()
            if (hasSelection) { text = text.removeRange(selMin, selMax); cursor = selMin; selAnchor = -1 }
            text = text.substring(0, cursor) + s + text.substring(cursor)
            cursor += s.length
        }
        fun backspace() {
            clampCursor()
            if (hasSelection) { text = text.removeRange(selMin, selMax); cursor = selMin; selAnchor = -1 }
            else if (cursor > 0) { text = text.removeRange(cursor - 1, cursor); cursor-- }
        }
        fun deleteForward() {
            clampCursor()
            if (hasSelection) { text = text.removeRange(selMin, selMax); cursor = selMin; selAnchor = -1 }
            else if (cursor < text.length) { text = text.removeRange(cursor, cursor + 1) }
        }
        fun backspaceWord() {
            clampCursor()
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
            clampCursor()
            val adjusted = relPx + scrollPx
            if (adjusted <= 0 || text.isEmpty()) return 0
            for (i in 1..text.length) {
                val half = (guiFont.width(styled(text.substring(0, i - 1))) + guiFont.width(styled(text.substring(0, i)))) / 2
                if (adjusted <= half) return i - 1
            }
            return text.length
        }
        fun clampScroll(visWidth: Int) {
            clampCursor()
            val curPx = guiFont.width(styled(text.substring(0, cursor)))
            if (curPx - scrollPx < 0)        scrollPx = curPx
            if (curPx - scrollPx > visWidth) scrollPx = curPx - visWidth
            scrollPx = scrollPx.coerceAtLeast(0)
        }
        private fun clampCursor() {
            cursor = cursor.coerceIn(0, text.length)
            if (selAnchor >= 0) selAnchor = selAnchor.coerceIn(0, text.length)
        }
    }

    internal fun fullPanelHeight(cat: Module.Category): Int {
        var h = HDR_H
        for (mod in ModuleManager.getByCategory(cat)) {
            h += MOD_H
            if (mod in expandedModules) {
                val entries = configEntries(mod)
                h += configEntriesHeight(entries)
            }
        }
        return h
    }

    internal fun configEntriesHeight(entries: List<ConfigEntry<*>>): Int {
        var height = 0
        var previousGroup: ConfigGroup? = null
        for (entry in entries) {
            if (shouldDrawGroupHeader(entry, previousGroup)) height += ENT_H
            height += entryHeight(entry)
            previousGroup = entry.group
        }
        return height
    }

    internal fun entryHeight(entry: ConfigEntry<*>): Int {
        var height = ENT_H
        if (entry is IntEntry || entry is FloatEntry || entry is DoubleEntry) height += ENT_H
        if (entry is IntRangeEntry || entry is FloatRangeEntry) height += ENT_H
        return height
    }

    internal fun shouldDrawGroupHeader(entry: ConfigEntry<*>, previousGroup: ConfigGroup?): Boolean =
        entry.group != null && entry.group !== previousGroup

    override fun mouseClicked(event: MouseButtonEvent, inBounds: Boolean): Boolean {
        val mx = event.x().toInt(); val my = event.y().toInt(); val btn = event.button()

        if (editingString != null) { editingString!!.value = entryField.text; editingString = null }
        if (editingColorEntry != null) { commitEditingColor(); editingColorEntry = null; editingColorChannel = null; editingColorHex = false }
        presetFieldActive = false

        val enumExp = expandedEnum
        if (enumExp != null) {
            return handleXmlEnumDropdownClick(enumExp, mx, my, btn)
        }

        val colorExp = expandedColorEntry
        if (colorExp != null) {
            if (mx in colorPickerX until colorPickerX + colorPickerW && my in colorPickerY until colorPickerY + colorPickerH) {
                return handleXmlColorPickerClick(colorExp, mx, my, btn)
            }
            expandedColorEntry = null
            return true
        }

        val listExp = expandedItemList
        if (listExp != null) {
            val h = xmlItemListDropdownHeight(listExp)
            if (mx in itemListDropdownX until itemListDropdownX + itemListDropdownW && my in itemListDropdownY until itemListDropdownY + h) {
                if (handleXmlItemListDropdownClick(listExp, mx, my, btn)) return true
            }
            expandedItemList = null
            editingItemListSearch = false
            itemListScroll = 0
            itemListAddedScroll = 0
            return true
        }

        if (uiController.handleMainClick(mx, my, btn)) return true
        return super.mouseClicked(event, inBounds)
    }

    private fun renderXmlEnumDropdown(g: GuiGraphicsExtractor, entry: EnumEntry<*>, mx: Int, my: Int) {
        val renderer = MinecraftUiRenderer(g)
        val runtime = UiRuntime(renderer)
        val document = ClickGuiUiFactory().enumDropdownDocument(entry, enumDropdownX, enumDropdownY, enumDropdownW)
        runtime.layout(document, UiRect(0f, 0f, width.toFloat(), height.toFloat()))
        runtime.render(document, mx.toFloat(), my.toFloat())
    }

    private fun handleXmlEnumDropdownClick(entry: EnumEntry<*>, mx: Int, my: Int, btn: Int): Boolean {
        if (btn != 0) {
            expandedEnum = null
            return true
        }
        val renderer = object : me.ghluka.medved.gui.ui.UiRenderer {
            override val fontHeight: Float get() = guiFont.lineHeight.toFloat()
            override fun textWidth(text: String): Float = guiFont.width(styled(text)).toFloat()
            override fun fill(rect: UiRect, color: Int, radius: Float) {}
            override fun border(rect: UiRect, color: Int, width: Float, radius: Float) {}
            override fun text(text: String, x: Float, y: Float, color: Int) {}
            override fun clip(rect: UiRect) {}
            override fun unclip() {}
        }
        val runtime = UiRuntime(renderer)
        val document = ClickGuiUiFactory().enumDropdownDocument(entry, enumDropdownX, enumDropdownY, enumDropdownW)
        runtime.layout(document, UiRect(0f, 0f, width.toFloat(), height.toFloat()))
        val handled = runtime.mouseClicked(document, mx.toFloat(), my.toFloat(), btn)
        expandedEnum = null
        return handled || (mx in enumDropdownX until enumDropdownX + enumDropdownW &&
            my in enumDropdownY until enumDropdownY + entry.constants.size * ENT_H)
    }

    private fun renderXmlColorPicker(g: GuiGraphicsExtractor, entry: ColorEntry, mx: Int, my: Int) {
        val document = buildXmlColorPickerDocument(entry)
        val runtime = UiRuntime(MinecraftUiRenderer(g))
        runtime.layout(document, UiRect(0f, 0f, width.toFloat(), height.toFloat()))
        runtime.render(document, mx.toFloat(), my.toFloat())
    }

    private fun buildXmlColorPickerDocument(entry: ColorEntry): UiDocument {
        val size = xmlColorPickerSize(entry)
        colorPickerW = size.first
        colorPickerH = size.second
        val document = ClickGuiUiFactory().colorPickerDocument(
            entry = entry,
            x = colorPickerX,
            y = colorPickerY,
            width = colorPickerW,
            height = colorPickerH,
            modeExpanded = colorPickerModeExpanded,
            editingChannel = if (editingColorEntry == entry && !editingColorHex) editingColorChannel else null,
            editingHex = editingColorEntry == entry && editingColorHex,
            editingText = entryField.text,
        )
        document.onClick("color:mode") {
            colorPickerModeExpanded = !colorPickerModeExpanded
            true
        }
        ColorEntry.PickerMode.entries.forEach { mode ->
            document.onClick("color:mode:${mode.name}") {
                if (mode == ColorEntry.PickerMode.THEME && !supportsThemeMode(entry)) return@onClick true
                entry.pickerMode = mode
                colorPickerMode = when (mode) {
                    ColorEntry.PickerMode.CUSTOM -> ColorPickerMode.CUSTOM
                    ColorEntry.PickerMode.THEME -> ColorPickerMode.THEME
                    ColorEntry.PickerMode.CHROMA -> ColorPickerMode.CHROMA
                }
                colorPickerModeExpanded = false
                applyDynamicColorState(entry)
                true
            }
        }
        document.onClick("color:map") { event ->
            applyColorMapClick(entry, event)
            draggingSlider = SliderDrag.ColorMap(entry, event.node.bounds.x.toInt(), event.node.bounds.y.toInt(), event.node.bounds.width.toInt(), event.node.bounds.height.toInt())
            true
        }
        document.onClick("color:hue") { event ->
            if (entry.pickerMode != ColorEntry.PickerMode.CHROMA) {
                applyColorHueClick(entry, event)
                draggingSlider = SliderDrag.ColorHue(entry, event.node.bounds.x.toInt(), event.node.bounds.width.toInt(), event.node.bounds.y.toInt(), event.node.bounds.height.toInt())
            }
            true
        }
        document.onClick("color:alpha") { event ->
            applyColorAlphaClick(entry, event)
            draggingSlider = SliderDrag.ColorAlpha(entry, event.node.bounds.x.toInt(), event.node.bounds.width.toInt(), event.node.bounds.y.toInt(), event.node.bounds.height.toInt())
            true
        }
        document.onClick("color:speed") { event ->
            applyColorSpeedClick(entry, event)
            draggingSlider = SliderDrag.ChromaSpeed(entry, event.node.bounds.x.toInt(), event.node.bounds.width.toInt(), event.node.bounds.y.toInt(), event.node.bounds.height.toInt())
            true
        }
        repeat(colorChannelCount(entry)) { channel ->
            document.onClick("color:channel:$channel") {
                editingColorEntry = entry
                editingColorChannel = channel
                editingColorHex = false
                entryField.set(listOf(entry.customValue.r, entry.customValue.g, entry.customValue.b, entry.customValue.a)[channel].toString())
                entryField.cursor = entryField.text.length
                entryField.selAnchor = -1
                true
            }
        }
        document.onClick("color:hex") {
            editingColorEntry = entry
            editingColorHex = true
            editingColorChannel = null
            entryField.set("#%02X%02X%02X%02X".format(entry.value.a, entry.value.r, entry.value.g, entry.value.b))
            entryField.cursor = entryField.text.length
            entryField.selAnchor = -1
            true
        }
        return document
    }

    private fun handleXmlColorPickerClick(entry: ColorEntry, mx: Int, my: Int, btn: Int): Boolean {
        if (btn != 0) return true
        val runtime = UiRuntime(noopUiRenderer())
        val document = buildXmlColorPickerDocument(entry)
        runtime.layout(document, UiRect(0f, 0f, width.toFloat(), height.toFloat()))
        return runtime.mouseClicked(document, mx.toFloat(), my.toFloat(), btn)
    }

    private fun xmlColorPickerSize(entry: ColorEntry): Pair<Int, Int> {
        val maxW = (width - colorPickerX - 8).coerceAtLeast(160)
        val upperW = maxW.coerceAtMost(240).coerceAtLeast(160)
        val actualW = colorPickerW.coerceIn(minOf(180, upperW), upperW)
        val base = when (entry.pickerMode) {
            ColorEntry.PickerMode.THEME -> 6 + 14 + 6 + guiFont.lineHeight + 4 + 16 + 6
            ColorEntry.PickerMode.CHROMA -> 6 + 14 + 6 + 65 + 6 + guiFont.lineHeight + 2 + 14 + 6
            ColorEntry.PickerMode.CUSTOM -> 6 + 14 + 6 + 65 + 6 + 14 + 4 + 14 + 6
        }
        return actualW to base
    }

    private fun applyColorMapClick(entry: ColorEntry, event: me.ghluka.medved.gui.ui.UiPointerEvent) {
        val bounds = event.node.bounds
        val saturation = ((event.x - bounds.x) / (bounds.width - 1f).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val value = 1f - ((event.y - bounds.y) / (bounds.height - 1f).coerceAtLeast(1f)).coerceIn(0f, 1f)
        if (entry.pickerMode == ColorEntry.PickerMode.CHROMA) {
            entry.chromaSaturation = saturation
            entry.chromaBrightness = value
        } else {
            val (hue, _, _) = rgbToHsv(entry.customValue)
            entry.customValue = hsvToRgb(hue, saturation, value).copy(a = entry.customValue.a)
            colorPickerCustomValue = entry.customValue
        }
        applyDynamicColorState(entry)
    }

    private fun applyColorHueClick(entry: ColorEntry, event: me.ghluka.medved.gui.ui.UiPointerEvent) {
        val bounds = event.node.bounds
        val hue = ((event.y - bounds.y) / (bounds.height - 1f).coerceAtLeast(1f)).coerceIn(0f, 1f) * 360f
        val (_, saturation, value) = rgbToHsv(entry.customValue)
        entry.customValue = hsvToRgb(hue, saturation, value).copy(a = entry.customValue.a)
        colorPickerCustomValue = entry.customValue
        applyDynamicColorState(entry)
    }

    private fun applyColorAlphaClick(entry: ColorEntry, event: me.ghluka.medved.gui.ui.UiPointerEvent) {
        val bounds = event.node.bounds
        val alpha = (255f * (1f - ((event.y - bounds.y) / (bounds.height - 1f).coerceAtLeast(1f)).coerceIn(0f, 1f))).roundToInt().coerceIn(0, 255)
        entry.customValue = entry.customValue.copy(a = alpha)
        colorPickerCustomValue = entry.customValue
        applyDynamicColorState(entry)
    }

    private fun applyColorSpeedClick(entry: ColorEntry, event: me.ghluka.medved.gui.ui.UiPointerEvent) {
        val bounds = event.node.bounds
        val t = ((event.x - bounds.x) / bounds.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
        entry.chromaSpeed = 0.05f + t * (8f - 0.05f)
        applyDynamicColorState(entry)
    }

    private fun renderXmlItemListDropdown(g: GuiGraphicsExtractor, entry: ItemListEntry, mx: Int, my: Int) {
        val document = buildXmlItemListDocument(entry)
        val runtime = UiRuntime(MinecraftUiRenderer(g))
        runtime.layout(document, UiRect(0f, 0f, width.toFloat(), height.toFloat()))
        runtime.render(document, mx.toFloat(), my.toFloat())
    }

    private fun buildXmlItemListDocument(entry: ItemListEntry): UiDocument {
        val rows = filteredItemListRowsFor(itemListSearch.text, entry.filter)
        val addedSet = entry.value.map { it.lowercase() }.toHashSet()
        val overlayRows = rows.map { row ->
            val id = pickerRowId(row)
            ClickGuiUiFactory.ItemListOverlayRow(
                id = id,
                label = pickerRowLabel(row),
                iconName = pickerRowIconName(row),
                added = id.lowercase() in addedSet,
            )
        }
        val document = ClickGuiUiFactory().itemListDropdownDocument(
            x = itemListDropdownX,
            y = itemListDropdownY,
            width = itemListDropdownW,
            height = xmlItemListDropdownHeight(entry),
            header = "${entry.value.size} items (${entry.mode.name.lowercase().replaceFirstChar { it.uppercase() }})",
            search = itemListSearch.text,
            searchActive = editingItemListSearch,
            searchCursor = itemListSearch.cursor,
            searchScroll = itemListSearch.scrollPx,
            rows = overlayRows,
            addedItems = xmlAddedItemIcons(entry),
            rowScroll = itemListScroll * 18,
            listHeight = xmlItemListListHeight(entry),
        )
        document.onClick("item-list:search") { event ->
            itemListSearch.cursor = itemListSearch.posFromPixel((event.x - event.node.bounds.x - 3f).roundToInt())
            editingItemListSearch = true
            true
        }
        overlayRows.forEach { row ->
            document.onClick("item-list:row:${row.id}") {
                if (entry.value.any { it.equals(row.id, ignoreCase = true) }) entry.remove(row.id) else entry.add(row.id)
                true
            }
        }
        entry.value.forEach { id ->
            document.onClick("item-list:remove:$id") {
                entry.remove(id)
                true
            }
        }
        return document
    }

    private fun handleXmlItemListDropdownClick(entry: ItemListEntry, mx: Int, my: Int, btn: Int): Boolean {
        if (btn != 0) return true
        val runtime = UiRuntime(noopUiRenderer())
        val document = buildXmlItemListDocument(entry)
        runtime.layout(document, UiRect(0f, 0f, width.toFloat(), height.toFloat()))
        return runtime.mouseClicked(document, mx.toFloat(), my.toFloat(), btn)
    }

    private fun handleXmlItemListScroll(entry: ItemListEntry, mx: Float, my: Float, scrollY: Float): Boolean {
        val runtime = UiRuntime(noopUiRenderer())
        val document = buildXmlItemListDocument(entry)
        runtime.layout(document, UiRect(0f, 0f, width.toFloat(), height.toFloat()))
        return runtime.mouseScrolled(document, mx, my, scrollY) { key, value, _ ->
            if (key == "item-list.rows") itemListScroll = (value / 18f).roundToInt().coerceAtLeast(0)
        }
    }

    private fun xmlItemListDropdownHeight(entry: ItemListEntry): Int =
        (6 + 10 + 4 + 14 + 4 + (if (entry.value.isNotEmpty()) 16 + 4 else 0) + xmlItemListListHeight(entry) + 6)
            .coerceAtMost(ITEM_LIST_DROPDOWN_MAX_H)

    private fun xmlItemListListHeight(entry: ItemListEntry): Int {
        val rows = filteredItemListRowsFor(itemListSearch.text, entry.filter).size.coerceAtLeast(1)
        val top = 6 + 10 + 4 + 14 + 4 + (if (entry.value.isNotEmpty()) 16 + 4 else 0) + 6
        return ((ITEM_LIST_DROPDOWN_MAX_H - top) / 18).coerceAtLeast(1).coerceAtMost(rows) * 18
    }

    private fun xmlAddedItemIcons(entry: ItemListEntry): List<Pair<String, String>> =
        entry.value.mapNotNull { id ->
            val icon = if (id.endsWith("_category")) {
                val categoryId = id.removeSuffix("_category")
                val category = itemCategories.firstOrNull { it.id == categoryId }
                getAllItems().firstOrNull { category?.matches(it.first) == true }?.first
            } else {
                findItemByName(id)?.first
            }
            icon?.let { id to it }
        }

    private fun noopUiRenderer(): me.ghluka.medved.gui.ui.UiRenderer =
        object : me.ghluka.medved.gui.ui.UiRenderer {
            override val fontHeight: Float get() = guiFont.lineHeight.toFloat()
            override fun textWidth(text: String): Float = guiFont.width(styled(text)).toFloat()
            override fun fill(rect: UiRect, color: Int, radius: Float) {}
            override fun border(rect: UiRect, color: Int, width: Float, radius: Float) {}
            override fun text(text: String, x: Float, y: Float, color: Int) {}
            override fun clip(rect: UiRect) {}
            override fun unclip() {}
        }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val mx = mouseX.toInt(); val my = mouseY.toInt()
        val listExp = expandedItemList
        if (listExp != null) {
            if (handleXmlItemListScroll(listExp, mouseX.toFloat(), mouseY.toFloat(), scrollY.toFloat())) return true
        }
        if (uiController.handleMainScroll(mouseX.toFloat(), mouseY.toFloat(), scrollY.toFloat())) return true
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (InputHelper.handleKeyPressed(this, event)) return true

        val key = event.key()

        if (key == GLFW.GLFW_KEY_ESCAPE || key == ClickGui.keybind.value) { onClose(); return true }

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
        val xmlSlider = draggingXmlSlider
        if (xmlSlider != null) {
            val t = sliderT(xmlSlider.bounds, event.x().toFloat())
            when (xmlSlider) {
                is XmlSliderDrag.IntValue ->
                    xmlSlider.entry.value = (xmlSlider.entry.min + t * (xmlSlider.entry.max - xmlSlider.entry.min)).roundToInt()
                is XmlSliderDrag.FloatValue ->
                    xmlSlider.entry.value = xmlSlider.entry.min + t * (xmlSlider.entry.max - xmlSlider.entry.min)
                is XmlSliderDrag.DoubleValue ->
                    xmlSlider.entry.value = xmlSlider.entry.min + t.toDouble() * (xmlSlider.entry.max - xmlSlider.entry.min)
                is XmlSliderDrag.IntRangeValue -> {
                    val value = (xmlSlider.entry.min + t * (xmlSlider.entry.max - xmlSlider.entry.min)).roundToInt()
                    val (lo, hi) = xmlSlider.entry.value
                    xmlSlider.entry.value =
                        if (xmlSlider.high) lo to value.coerceAtLeast(lo)
                        else value.coerceAtMost(hi) to hi
                }
                is XmlSliderDrag.FloatRangeValue -> {
                    val scale = Math.pow(10.0, xmlSlider.entry.decimals.toDouble()).toFloat()
                    val value = (Math.round((xmlSlider.entry.min + t * (xmlSlider.entry.max - xmlSlider.entry.min)) * scale).toFloat()) / scale
                    val (lo, hi) = xmlSlider.entry.value
                    xmlSlider.entry.value =
                        if (xmlSlider.high) lo to value.coerceAtLeast(lo)
                        else value.coerceAtMost(hi) to hi
                }
            }
            return true
        }

        val slider = draggingSlider
        if (slider != null) {
            when (slider) {
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
            }
            return true
        }
        if (draggingPresetField) {
            val relX = event.x().toInt() - presetFieldTextX
            presetField.apply { cursor = posFromPixel(relX); clampScroll(presetFieldVisibleW) }
            return true
        }
        if (draggingStringEntry && editingString != null) {
            entryField.apply { cursor = posFromPixel(event.x().toInt() - entryFieldTextX); clampScroll(entryFieldVisibleW) }
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
        draggingXmlSlider = null
        draggingCfgPanel = false
        draggingSidebar = false
        draggingPresetField = false
        draggingStringEntry = false
        constrainXmlDropdownScroll()
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

    internal fun supportsThemeMode(entry: ColorEntry) = entry !== Colour.accent

    internal fun constrainXmlDropdownScroll() {
        if (ClickGui.currentMode.value != ClickGui.Mode.DROPDOWN) return
        val categoryBottom = Module.Category.entries.maxOfOrNull { category ->
            val y = positions[category]?.second ?: return@maxOfOrNull 0
            y + if (category in collapsed) HDR_H else fullPanelHeight(category)
        } ?: 0
        val configBottom = cfgPanelY + if (cfgPanelCollapsed) HDR_H else HDR_H + cfgPanelBodyH()
        val maxExpandedY = maxOf(categoryBottom, configBottom)
        val viewHeight = height - 50
        val maxScrollOffset = (maxExpandedY - viewHeight).coerceAtLeast(0)
        dropdownScroll = dropdownScroll.coerceIn(0, maxScrollOffset)
    }

    internal fun applyDynamicColorState(entry: ColorEntry) {
        entry.applyDynamicColor(Colour.accent.liveColor(Colour.accent.value), ColorEntry.chromaTimeSeconds(), supportsThemeMode(entry))
    }

    internal fun toggleExpand(mod: Module) {
        if (mod in expandedModules) expandedModules.remove(mod) else expandedModules.add(mod)
        expandedColorEntry = null
        expandedEnum = null
    }

    internal fun bringToFront(cat: Module.Category) {
        renderOrder.remove(cat)
        renderOrder.add(cat)
    }

    internal fun bringConfigToFront() {
        renderOrder.remove(null)
        renderOrder.add(null)
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
        g.Text(guiFont, styledTooltip, tx + pad, ty + pad, TEXT)
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
