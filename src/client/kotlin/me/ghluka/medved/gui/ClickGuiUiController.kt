package me.ghluka.medved.gui

import me.ghluka.medved.config.ConfigManager
import me.ghluka.medved.config.entry.ColorEntry
import me.ghluka.medved.config.entry.DoubleEntry
import me.ghluka.medved.config.entry.FloatEntry
import me.ghluka.medved.config.entry.FloatRangeEntry
import me.ghluka.medved.config.entry.IntEntry
import me.ghluka.medved.config.entry.IntRangeEntry
import me.ghluka.medved.gui.ui.ClickGuiUiFactory
import me.ghluka.medved.gui.ui.MinecraftUiRenderer
import me.ghluka.medved.gui.ui.UiDocument
import me.ghluka.medved.gui.ui.UiRect
import me.ghluka.medved.gui.ui.UiRenderer
import me.ghluka.medved.gui.ui.UiRuntime
import me.ghluka.medved.gui.ui.enumDropdownWidth
import me.ghluka.medved.module.modules.other.ClickGui as ClickGuiModule
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

internal class ClickGuiUiController(
    private val gui: ClickGui,
) {
    fun renderMain(g: GuiGraphicsExtractor, mx: Int, my: Int) {
        if (ClickGuiModule.blurBackground.value && ClickGuiModule.showBackground.value) {
            try {
                g.javaClass.getMethod("blurBeforeThisStratum").invoke(g)
                g.javaClass.getMethod("nextStratum").invoke(g)
            } catch (ignored: Exception) {}
        }

        val runtime = UiRuntime(MinecraftUiRenderer(g))
        val document = buildMainDocument()
        runtime.layout(document, UiRect(0f, 0f, gui.width.toFloat(), gui.height.toFloat()))
        runtime.render(document, mx.toFloat(), my.toFloat())
    }

    fun handleMainClick(mx: Int, my: Int, btn: Int): Boolean {
        val runtime = UiRuntime(noopRenderer())
        val document = buildMainDocument()
        runtime.layout(document, UiRect(0f, 0f, gui.width.toFloat(), gui.height.toFloat()))
        return runtime.mouseClicked(document, mx.toFloat(), my.toFloat(), btn)
    }

    fun handleMainScroll(mouseX: Float, mouseY: Float, scrollY: Float): Boolean {
        val runtime = UiRuntime(noopRenderer())
        val document = buildMainDocument()
        runtime.layout(document, UiRect(0f, 0f, gui.width.toFloat(), gui.height.toFloat()))
        return runtime.mouseScrolled(document, mouseX, mouseY, scrollY) { key, value, max ->
            when (key) {
                "dropdown" -> gui.dropdownScroll = value.roundToInt().coerceIn(0, max.roundToInt().coerceAtLeast(0))
                "sidebar.modules" -> gui.sidebarScroll = value.coerceAtLeast(0f)
                "sidebar.config" -> gui.sidebarConfigScroll = value.coerceAtLeast(0f)
            }
        }
    }

    private fun buildMainDocument(): UiDocument {
        val factory = ClickGuiUiFactory(
            onCategoryClicked = { category, event ->
                if (ClickGuiModule.currentMode.value == ClickGuiModule.Mode.SIDEBAR) {
                    gui.selectedCategory = category
                    gui.sidebarDetailMod = null
                    gui.sidebarScroll = 0f
                    true
                } else if (event.button == 1) {
                    gui.bringToFront(category)
                    if (category in gui.collapsed) gui.collapsed.remove(category) else gui.collapsed.add(category)
                    true
                } else {
                    gui.bringToFront(category)
                    gui.draggingCat = category
                    gui.dragOffX = (event.x - event.node.bounds.x).roundToInt()
                    gui.dragOffY = (event.y - event.node.bounds.y).roundToInt()
                    true
                }
            },
            onModuleExpandRequested = { module -> gui.toggleExpand(module) },
            onSidebarModuleSelected = { module ->
                gui.sidebarDetailMod = module
                gui.sidebarScroll = 0f
            },
            onSidebarModuleToggleRequested = { module ->
                if (!module.isProtected) module.toggle()
            },
            onKeybindRequested = { entry -> gui.listeningKeybind = entry },
            onNumericDragStarted = { entry, bounds ->
                gui.draggingXmlSlider = when (entry) {
                    is IntEntry -> ClickGui.XmlSliderDrag.IntValue(entry, bounds)
                    is FloatEntry -> ClickGui.XmlSliderDrag.FloatValue(entry, bounds)
                    is DoubleEntry -> ClickGui.XmlSliderDrag.DoubleValue(entry, bounds)
                    else -> null
                }
            },
            onRangeDragStarted = { entry, bounds, high ->
                gui.draggingXmlSlider = when (entry) {
                    is IntRangeEntry -> ClickGui.XmlSliderDrag.IntRangeValue(entry, high, bounds)
                    is FloatRangeEntry -> ClickGui.XmlSliderDrag.FloatRangeValue(entry, high, bounds)
                    else -> null
                }
            },
            onStringEditRequested = { entry, event ->
                gui.editingString = entry
                gui.entryField.set(entry.value)
                val textX = event.node.bounds.x.toInt() + event.node.style.padding.left.toInt()
                val visibleW = (event.node.bounds.width - event.node.style.padding.left - event.node.style.padding.right)
                    .toInt()
                    .coerceAtLeast(1)
                gui.entryFieldTextX = textX
                gui.entryFieldVisibleW = visibleW
                gui.entryField.cursor = gui.entryField.posFromPixel(event.x.toInt() - textX)
                gui.entryField.selAnchor = gui.entryField.cursor
                gui.draggingStringEntry = true
                gui.entryField.clampScroll(visibleW)
            },
            onColorEditRequested = { entry, event ->
                gui.expandedColorEntry = if (gui.expandedColorEntry == entry) null else entry
                if (gui.expandedColorEntry == entry) {
                    gui.colorPickerMode = when (entry.pickerMode) {
                        ColorEntry.PickerMode.CUSTOM -> ClickGui.ColorPickerMode.CUSTOM
                        ColorEntry.PickerMode.THEME -> ClickGui.ColorPickerMode.THEME
                        ColorEntry.PickerMode.CHROMA -> ClickGui.ColorPickerMode.CHROMA
                    }
                    gui.colorPickerModeExpanded = false
                    gui.colorPickerCustomValue = entry.customValue
                    val sidebarContent = sidebarContentBounds()
                    gui.colorPickerX = sidebarContent?.left ?: event.node.bounds.x.toInt()
                    gui.colorPickerY = (event.node.bounds.y + event.node.bounds.height).toInt()
                    val pickerW = sidebarContent?.width
                        ?: 220.coerceAtMost((gui.width - event.node.bounds.x.toInt() - 8).coerceAtLeast(160))
                    gui.colorPickerW = pickerW
                } else {
                    gui.colorPickerModeExpanded = false
                    gui.editingColorEntry = null
                    gui.editingColorChannel = null
                    gui.editingColorHex = false
                }
            },
            onItemListRequested = { entry, event ->
                gui.expandedItemList = if (gui.expandedItemList == entry) null else entry
                if (gui.expandedItemList == entry) {
                    gui.itemListSearch.set("")
                    gui.editingItemListSearch = true
                    gui.itemListScroll = 0
                    gui.itemListAddedScroll = 0
                    gui.itemListCategory = null
                    val sidebarContent = sidebarContentBounds()
                    val maxW = sidebarContent?.width ?: (gui.width - 8).coerceAtLeast(120)
                    val iw = 240.coerceAtMost(maxW)
                    gui.itemListDropdownX = sidebarContent?.left ?: event.node.bounds.x.toInt().coerceAtMost(gui.width - iw)
                    gui.itemListDropdownY = (event.node.bounds.y + event.node.bounds.height).toInt()
                    gui.itemListDropdownW = iw
                } else {
                    gui.editingItemListSearch = false
                    gui.itemListScroll = 0
                    gui.itemListAddedScroll = 0
                }
            },
            onEnumDropdownRequested = { entry, event ->
                gui.expandedEnum = if (gui.expandedEnum == entry) null else entry
                if (gui.expandedEnum == entry) {
                    val sidebarContent = sidebarContentBounds()
                    val dropdownW = enumDropdownWidth(entry)
                        .coerceAtMost(sidebarContent?.width ?: Int.MAX_VALUE)
                    gui.enumDropdownX = if (sidebarContent != null) {
                        event.node.bounds.right
                            .roundToInt()
                            .minus(dropdownW)
                            .coerceIn(sidebarContent.left, (sidebarContent.right - dropdownW).coerceAtLeast(sidebarContent.left))
                    } else {
                        event.node.bounds.right
                            .roundToInt()
                            .minus(dropdownW)
                            .coerceIn(0, (gui.width - dropdownW).coerceAtLeast(0))
                    }
                    gui.enumDropdownY = (event.node.bounds.y + event.node.bounds.height).toInt()
                    gui.enumDropdownW = dropdownW
                }
            },
            onHudEditRequested = { module ->
                Minecraft.getInstance().gui.setScreen(HudEditorScreen(module, gui))
            },
            onConfigHeaderClicked = handler@ { event ->
                if (ClickGuiModule.currentMode.value != ClickGuiModule.Mode.DROPDOWN) return@handler false
                when (event.button) {
                    0 -> {
                        gui.bringConfigToFront()
                        gui.draggingCfgPanel = true
                        gui.cfgDragOffX = (event.x - event.node.bounds.x).roundToInt()
                        gui.cfgDragOffY = (event.y - event.node.bounds.y).roundToInt()
                        true
                    }
                    1 -> {
                        gui.bringConfigToFront()
                        gui.cfgPanelCollapsed = !gui.cfgPanelCollapsed
                        true
                    }
                    else -> false
                }
            },
            onPresetEditRequested = { event ->
                gui.presetFieldActive = true
                gui.draggingPresetField = true
                val textX = event.node.bounds.x.toInt() + event.node.style.padding.left.toInt()
                val visibleW = (event.node.bounds.width - event.node.style.padding.left - event.node.style.padding.right)
                    .toInt()
                    .coerceAtLeast(1)
                gui.presetFieldTextX = textX
                gui.presetFieldVisibleW = visibleW
                gui.presetField.cursor = gui.presetField.posFromPixel(event.x.toInt() - textX)
                gui.presetField.selAnchor = gui.presetField.cursor
                gui.presetField.clampScroll(visibleW)
            },
            onPresetSelected = { preset ->
                gui.presetField.set(preset)
                gui.presetField.clampScroll(gui.presetFieldVisibleW)
                gui.presetNameBuffer = preset
            },
            presetNameProvider = { gui.presetField.text },
        )

        val document = if (ClickGuiModule.currentMode.value == ClickGuiModule.Mode.SIDEBAR) {
            factory.sidebarDocument(
                selectedCategory = gui.selectedCategory,
                sidebarX = gui.sidebarPaneX,
                sidebarY = gui.sidebarPaneY,
                panelPositions = gui.positions,
                expandedModules = gui.expandedModules,
                collapsedCategories = gui.collapsed,
                activeStringEntry = gui.editingString,
                activeStringText = gui.entryField.text,
                activeStringCursor = gui.entryField.cursor,
                activeStringScrollPx = gui.entryField.scrollPx,
                openEnumEntry = gui.expandedEnum,
                openItemListEntry = gui.expandedItemList,
                sidebarTab = gui.sidebarTab,
                detailModule = gui.sidebarDetailMod,
                modulesScrollY = gui.sidebarScroll,
                configScrollY = gui.sidebarConfigScroll,
                presetName = gui.presetField.text,
                presetActive = gui.presetFieldActive,
                presetCursor = gui.presetField.cursor,
                presetScrollPx = gui.presetField.scrollPx,
                presets = ConfigManager.listPresets(),
                includeEntry = { module, entry -> entry in gui.configEntries(module) },
            )
        } else {
            factory.dropdownDocument(
                panelPositions = gui.positions,
                categoryOrder = gui.renderOrder.filterNotNull(),
                expandedModules = gui.expandedModules,
                collapsedCategories = gui.collapsed,
                activeStringEntry = gui.editingString,
                activeStringText = gui.entryField.text,
                activeStringCursor = gui.entryField.cursor,
                activeStringScrollPx = gui.entryField.scrollPx,
                openEnumEntry = gui.expandedEnum,
                openItemListEntry = gui.expandedItemList,
                configX = gui.cfgPanelX,
                configY = gui.cfgPanelY,
                configCollapsed = gui.cfgPanelCollapsed,
                presetName = gui.presetField.text,
                presetActive = gui.presetFieldActive,
                presetCursor = gui.presetField.cursor,
                presetScrollPx = gui.presetField.scrollPx,
                presets = ConfigManager.listPresets(),
                scrollY = gui.dropdownScroll.toFloat(),
                includeEntry = { module, entry -> entry in gui.configEntries(module) },
            )
        }
        document.onClick("sidebar:drag") { event ->
            if (event.button != 0) return@onClick false
            gui.draggingSidebar = true
            gui.sidebarDragOffX = (event.x - gui.sidebarPaneX).roundToInt()
            gui.sidebarDragOffY = (event.y - gui.sidebarPaneY).roundToInt()
            true
        }
        document.onClick("sidebar:tab:modules") { event ->
            if (event.button != 0) return@onClick false
            gui.sidebarTab = 0
            gui.sidebarDetailMod = null
            true
        }
        document.onClick("sidebar:tab:config") { event ->
            if (event.button != 0) return@onClick false
            gui.sidebarTab = 1
            gui.sidebarDetailMod = null
            true
        }
        document.onClick("sidebar:detail:back") { event ->
            if (event.button != 0) return@onClick false
            gui.sidebarDetailMod = null
            gui.sidebarScroll = 0f
            true
        }
        return document
    }

    private fun noopRenderer(): UiRenderer =
        object : UiRenderer {
            override val fontHeight: Float get() = gui.guiFont.lineHeight.toFloat()
            override fun textWidth(text: String): Float = gui.guiFont.width(gui.styled(text)).toFloat()
            override fun fill(rect: UiRect, color: Int, radius: Float) {}
            override fun border(rect: UiRect, color: Int, width: Float, radius: Float) {}
            override fun text(text: String, x: Float, y: Float, color: Int) {}
            override fun clip(rect: UiRect) {}
            override fun unclip() {}
        }

    private data class SidebarContentBounds(
        val left: Int,
        val right: Int,
    ) {
        val width: Int get() = (right - left).coerceAtLeast(1)
    }

    private fun sidebarContentBounds(): SidebarContentBounds? {
        if (ClickGuiModule.currentMode.value != ClickGuiModule.Mode.SIDEBAR || gui.sidebarDetailMod == null) {
            return null
        }
        val left = gui.sidebarPaneX + 110 + 1 + 6
        val right = gui.sidebarPaneX + 480 - 6
        return SidebarContentBounds(left, right)
    }
}
