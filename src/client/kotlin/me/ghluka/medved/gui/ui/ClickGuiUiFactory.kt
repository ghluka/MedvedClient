package me.ghluka.medved.gui.ui

import me.ghluka.medved.config.ConfigGroup
import me.ghluka.medved.config.ConfigManager
import me.ghluka.medved.config.entry.BooleanEntry
import me.ghluka.medved.config.entry.ButtonEntry
import me.ghluka.medved.config.entry.ColorEntry
import me.ghluka.medved.config.entry.Color
import me.ghluka.medved.config.entry.ConfigEntry
import me.ghluka.medved.config.entry.DoubleEntry
import me.ghluka.medved.config.entry.EnumEntry
import me.ghluka.medved.config.entry.FloatEntry
import me.ghluka.medved.config.entry.FloatRangeEntry
import me.ghluka.medved.config.entry.HudEditEntry
import me.ghluka.medved.config.entry.IntEntry
import me.ghluka.medved.config.entry.IntRangeEntry
import me.ghluka.medved.config.entry.ItemListEntry
import me.ghluka.medved.config.entry.KeybindEntry
import me.ghluka.medved.config.entry.StringEntry
import me.ghluka.medved.module.HudModule
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.ModuleManager
import me.ghluka.medved.module.modules.other.Colour
import me.ghluka.medved.util.NotificationManager
import kotlin.math.roundToInt

class ClickGuiUiFactory(
    private val templates: UiTemplateSet = ClickGuiUiResources.templates(),
    private val onCategoryClicked: (Module.Category, UiPointerEvent) -> Boolean = { _, _ -> false },
    private val onModuleExpandRequested: (Module) -> Unit = {},
    private val onSidebarModuleSelected: (Module) -> Unit = {},
    private val onSidebarModuleToggleRequested: (Module) -> Unit = {},
    private val onKeybindRequested: (KeybindEntry) -> Unit = {},
    private val onNumericDragStarted: (ConfigEntry<*>, UiRect) -> Unit = { _, _ -> },
    private val onRangeDragStarted: (ConfigEntry<*>, UiRect, Boolean) -> Unit = { _, _, _ -> },
    private val onStringEditRequested: (StringEntry, UiPointerEvent) -> Unit = { _, _ -> },
    private val onColorEditRequested: (ColorEntry, UiPointerEvent) -> Unit = { _, _ -> },
    private val onItemListRequested: (ItemListEntry, UiPointerEvent) -> Unit = { _, _ -> },
    private val onEnumDropdownRequested: (EnumEntry<*>, UiPointerEvent) -> Unit = { _, _ -> },
    private val onHudEditRequested: (HudModule) -> Unit = {},
    private val onConfigHeaderClicked: (UiPointerEvent) -> Boolean = { false },
    private val onPresetEditRequested: (UiPointerEvent) -> Unit = {},
    private val onPresetSelected: (String) -> Unit = {},
    private val presetNameProvider: () -> String = { "default" },
) {
    data class ItemListOverlayRow(
        val id: String,
        val label: String,
        val iconName: String,
        val added: Boolean,
    )

    fun colorPickerDocument(
        entry: ColorEntry,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        modeExpanded: Boolean,
        editingChannel: Int?,
        editingHex: Boolean,
        editingText: String,
    ): UiDocument {
        val body = when (entry.pickerMode) {
            ColorEntry.PickerMode.THEME -> listOf(templates.instantiate("color-theme-body", themeValues() + colorValues(entry)))
            ColorEntry.PickerMode.CHROMA -> listOf(
                templates.instantiate(
                    "color-map-body",
                    themeValues() + colorValues(entry),
                    slots = mapOf("alpha" to alphaSlot(entry)),
                ),
                templates.instantiate("color-speed-body", themeValues() + colorValues(entry)),
            )
            ColorEntry.PickerMode.CUSTOM -> listOf(
                templates.instantiate(
                    "color-map-body",
                    themeValues() + colorValues(entry),
                    slots = mapOf("alpha" to alphaSlot(entry)),
                ),
                templates.instantiate(
                    "color-channel-row",
                    themeValues(),
                    slots = mapOf("fields" to colorChannelFields(entry, editingChannel, editingText)),
                ),
                templates.instantiate("color-hex-row", themeValues() + colorValues(entry, editingHex, editingText)),
            )
        }
        val options = if (modeExpanded) {
            listOf(
                templates.instantiate(
                    "color-mode-options",
                    themeValues() + mapOf("mode.optionsH" to (colorModeCount(entry) * 14).toString()),
                    slots = mapOf("options" to colorModeOptions(entry)),
                ),
            )
        } else {
            emptyList()
        }
        return UiDocument(
            templates.instantiate(
                "color-picker",
                themeValues() + colorValues(entry) + mapOf(
                    "overlay.x" to x.toString(),
                    "overlay.y" to y.toString(),
                    "overlay.w" to width.toString(),
                    "overlay.h" to height.toString(),
                ),
                slots = mapOf("body" to body, "options" to options),
            ),
        ).validate("color picker UI")
    }

    fun itemListDropdownDocument(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        header: String,
        search: String,
        searchActive: Boolean,
        searchCursor: Int,
        searchScroll: Int,
        rows: List<ItemListOverlayRow>,
        addedItems: List<Pair<String, String>>,
        rowScroll: Int,
        listHeight: Int,
    ): UiDocument {
        val added = if (addedItems.isEmpty()) {
            emptyList()
        } else {
            listOf(
                templates.instantiate(
                    "item-list-added-strip",
                    themeValues(),
                    slots = mapOf(
                        "items" to addedItems.map { (id, icon) ->
                            templates.instantiate(
                                "item-list-added-item",
                                mapOf("item.removeId" to "item-list:remove:$id", "item.name" to icon) + themeValues(),
                            )
                        },
                    ),
                ),
            )
        }
        val rowNodes = rows.map { row ->
            templates.instantiate(
                "item-list-row",
                mapOf(
                    "item.rowId" to "item-list:row:${row.id}",
                    "item.name" to row.iconName,
                    "item.label" to row.label,
                    "item.action" to if (row.added) "-" else "+",
                    "item.actionBg" to if (row.added) themeValues()["theme.entrySelectedBg"].orEmpty() else themeValues()["theme.controlBg"].orEmpty(),
                ) + themeValues(),
            )
        }
        return UiDocument(
            templates.instantiate(
                "item-list-dropdown",
                mapOf(
                    "overlay.x" to x.toString(),
                    "overlay.y" to y.toString(),
                    "overlay.w" to width.toString(),
                    "overlay.h" to height.toString(),
                    "item.header" to header,
                    "item.search" to if (search.isBlank() && !searchActive) "Search..." else search,
                    "item.searchFg" to if (search.isBlank() && !searchActive) themeValues()["theme.textDim"].orEmpty() else themeValues()["theme.text"].orEmpty(),
                    "item.searchActive" to searchActive.toString(),
                    "item.searchCursor" to searchCursor.toString(),
                    "item.searchScroll" to searchScroll.toString(),
                    "item.scroll" to rowScroll.toString(),
                    "item.listH" to listHeight.toString(),
                ) + themeValues(),
                slots = mapOf("added" to added, "rows" to rowNodes),
            ),
        ).validate("item list UI")
    }

    private fun themeValues(): Map<String, String> =
        mapOf(
            "theme.bg" to hex(shade(9, 0.06f, 115)),
            "theme.panelBg" to hex(shade(20, 0.10f, 242)),
            "theme.headerBg" to hex(shade(24, 0.18f)),
            "theme.moduleBg" to hex(shade(24, 0.08f)),
            "theme.moduleHoverBg" to hex(shade(38, 0.13f)),
            "theme.entryBg" to hex(shade(21, 0.07f)),
            "theme.entrySelectedBg" to hex(shade(42, 0.18f)),
            "theme.entryHoverBg" to hex(shade(32, 0.12f)),
            "theme.controlBg" to hex(shade(42, 0.14f)),
            "theme.controlActiveBg" to hex(shade(45, 0.25f)),
            "theme.sliderBg" to hex(shade(38, 0.14f)),
            "theme.sliderFg" to sliderFg(),
            "theme.text" to "#FFD7D7E4",
            "theme.textDim" to "#FF76768C",
            "theme.accent" to accentHex(),
            "theme.transparent" to "#00000000",
        )

    private fun shade(base: Int, mix: Float, alpha: Int = 255): Int {
        val c = Colour.bg.liveColor(Colour.bg.value)
        val r = (base + (c.r - base) * mix).toInt().coerceIn(0, 255)
        val g = (base + (c.g - base) * mix).toInt().coerceIn(0, 255)
        val b = (base + (c.b - base) * mix).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun hex(argb: Int): String =
        "#%08X".format(argb)

    private fun accentHex(): String =
        "#%08X".format(Colour.accent.liveColor(Colour.accent.value).argb)

    private fun sliderFg(): String {
        val color = Colour.accent.liveColor(Colour.accent.value)
        return "#%08X".format((255 shl 24) or ((color.r * 0.8f).toInt() shl 16) or ((color.g * 0.8f).toInt() shl 8) or (color.b * 0.8f).toInt())
    }

    fun dropdownDocument(
        panelPositions: Map<Module.Category, Pair<Int, Int>>,
        categoryOrder: List<Module.Category> = Module.Category.entries,
        expandedModules: Set<Module>,
        collapsedCategories: Set<Module.Category>,
        activeStringEntry: StringEntry? = null,
        activeStringText: String = "",
        activeStringCursor: Int = 0,
        activeStringScrollPx: Int = 0,
        openEnumEntry: EnumEntry<*>? = null,
        openItemListEntry: ItemListEntry? = null,
        configX: Int = 10,
        configY: Int = 30,
        configCollapsed: Boolean = true,
        presetName: String = "",
        presetActive: Boolean = false,
        presetCursor: Int = 0,
        presetScrollPx: Int = 0,
        presets: List<String> = emptyList(),
        scrollY: Float = 0f,
        includeEntry: (Module, ConfigEntry<*>) -> Boolean = { module, entry ->
            entry.name != "enabled" && !(module.isProtected && entry.name == "keybind") &&
                (entry.visibleWhen?.invoke() != false)
        },
    ): UiDocument {
        return UiDocument(
            ClickGuiBlueprint(
                templates,
                ClickGuiLayoutContext(
                    categoryPositions = panelPositions,
                    categoryOrder = categoryOrder,
                    expandedModules = expandedModules,
                    collapsedCategories = collapsedCategories,
                    activeStringEntry = activeStringEntry,
                    activeStringText = activeStringText,
                    activeStringCursor = activeStringCursor,
                    activeStringScrollPx = activeStringScrollPx,
                    openEnumEntry = openEnumEntry,
                    openItemListEntry = openItemListEntry,
                    configX = configX,
                    configY = configY,
                    configCollapsed = configCollapsed,
                    presetName = presetName,
                    presetActive = presetActive,
                    presetCursor = presetCursor,
                    presetScrollPx = presetScrollPx,
                    presets = presets,
                    dropdownScroll = scrollY,
                    includeEntry = includeEntry,
                ),
            ).instantiateMode("dropdown-mode")
        ).validate("dropdown Click GUI UI").bindGeneratedActions()
    }

    fun sidebarDocument(
        selectedCategory: Module.Category?,
        sidebarX: Int,
        sidebarY: Int,
        panelPositions: Map<Module.Category, Pair<Int, Int>> = emptyMap(),
        expandedModules: Set<Module> = emptySet(),
        collapsedCategories: Set<Module.Category> = emptySet(),
        activeStringEntry: StringEntry? = null,
        activeStringText: String = "",
        activeStringCursor: Int = 0,
        activeStringScrollPx: Int = 0,
        openEnumEntry: EnumEntry<*>? = null,
        openItemListEntry: ItemListEntry? = null,
        sidebarTab: Int = 0,
        detailModule: Module? = null,
        modulesScrollY: Float = 0f,
        configScrollY: Float = 0f,
        presetName: String = "",
        presetActive: Boolean = false,
        presetCursor: Int = 0,
        presetScrollPx: Int = 0,
        presets: List<String> = emptyList(),
        includeEntry: (Module, ConfigEntry<*>) -> Boolean = { module, entry ->
            entry.name != "enabled" && !(module.isProtected && entry.name == "keybind") &&
                (entry.visibleWhen?.invoke() != false)
        },
    ): UiDocument =
        UiDocument(
            ClickGuiBlueprint(
                templates,
                ClickGuiLayoutContext(
                    categoryPositions = panelPositions,
                    expandedModules = expandedModules,
                    collapsedCategories = collapsedCategories,
                    selectedCategory = selectedCategory,
                    detailModule = detailModule,
                    sidebarTab = sidebarTab,
                    sidebarX = sidebarX,
                    sidebarY = sidebarY,
                    presetName = presetName,
                    presetActive = presetActive,
                    presetCursor = presetCursor,
                    presetScrollPx = presetScrollPx,
                    presets = presets,
                    activeStringEntry = activeStringEntry,
                    activeStringText = activeStringText,
                    activeStringCursor = activeStringCursor,
                    activeStringScrollPx = activeStringScrollPx,
                    openEnumEntry = openEnumEntry,
                    openItemListEntry = openItemListEntry,
                    sidebarModulesScroll = modulesScrollY,
                    sidebarConfigScroll = configScrollY,
                    includeEntry = includeEntry,
                ),
            ).instantiateMode("sidebar-mode")
        ).validate("sidebar Click GUI UI").bindGeneratedActions()

    fun enumDropdownDocument(
        entry: EnumEntry<*>,
        x: Int,
        y: Int,
        width: Int,
        includeEntry: (Module, ConfigEntry<*>) -> Boolean = { _, _ -> true },
    ): UiDocument =
        UiDocument(
            ClickGuiBlueprint(
                templates,
                ClickGuiLayoutContext(
                    categoryPositions = emptyMap(),
                    expandedModules = emptySet(),
                    collapsedCategories = emptySet(),
                    enumEntry = entry,
                    enumX = x,
                    enumY = y,
                    enumW = width,
                    includeEntry = includeEntry,
                ),
            ).instantiateMode("enum-dropdown")
        ).validate("enum dropdown UI").bindEnumActions(entry)

    fun categoryPanel(
        category: Module.Category,
        position: Pair<Int, Int>,
        expandedModules: Set<Module>,
        includeEntry: (Module, ConfigEntry<*>) -> Boolean,
    ): UiNode {
        val modules = ModuleManager.getByCategory(category).flatMap { module ->
            val moduleRow = moduleNode(module)
            if (module !in expandedModules) {
                listOf(moduleRow)
            } else {
                listOf(moduleRow) + entryNodes(module, includeEntry)
            }
        }

        return templates.instantiate(
            "category-panel",
            values = mapOf(
                "category.name" to category.name,
                "panel.x" to position.first.toString(),
                "panel.y" to position.second.toString(),
            ),
            slots = mapOf("modules" to modules),
        )
    }

    fun moduleCard(module: Module): UiNode =
        templates.instantiate(
            "module-card",
            values = moduleValues(module),
        )

    fun moduleNode(module: Module): UiNode =
        templates.instantiate(
            "module-row",
            values = moduleValues(module),
        )

    fun entryNodes(
        module: Module,
        includeEntry: (Module, ConfigEntry<*>) -> Boolean,
    ): List<UiNode> {
        val nodes = mutableListOf<UiNode>()
        var previousGroup: ConfigGroup? = null

        for (entry in module.entries.filter { includeEntry(module, it) }) {
            val group = entry.group
            if (group != null && group !== previousGroup) {
                nodes += templates.instantiate(
                    "group-header",
                    values = mapOf("group.name" to label(group.name)),
                )
            }
            nodes += entryNode(module, entry)
            previousGroup = group
        }

        return nodes
    }

    fun entryNode(module: Module, entry: ConfigEntry<*>): UiNode {
        val template = when (entry) {
            is BooleanEntry -> "boolean-entry"
            else -> "entry-row"
        }

        return templates.instantiate(
            template,
            values = mapOf(
                "entry.id" to entryId(module, entry),
                "entry.label" to label(entry.name),
                "entry.value" to entryValue(entry),
                "entry.fg" to if (entry is BooleanEntry && entry.value) "#FFD7D7E4" else "#FF76768C",
            ),
        )
    }

    private fun UiDocument.bindGeneratedActions(): UiDocument = apply {
        Module.Category.entries.forEach { category ->
            onClick(categoryId(category)) { event ->
                onCategoryClicked(category, event)
            }
        }

        onClick("config:header") { event -> onConfigHeaderClicked(event) }
        onClick("config:preset-input") { event ->
            onPresetEditRequested(event)
            true
        }
        onClick("config:save") {
            val name = presetNameProvider().ifBlank { "default" }
            ConfigManager.savePreset(name)
            NotificationManager.show("Config Saved", name)
            true
        }
        onClick("config:load") {
            val name = presetNameProvider().ifBlank { "default" }
            val exists = name in ConfigManager.listPresets()
            ConfigManager.loadPreset(name)
            if (exists) NotificationManager.show("Config Loaded", name)
            else NotificationManager.show("Not Found", name)
            true
        }
        onClick("config:folder") {
            ConfigManager.openPresetFolder()
            true
        }
        ConfigManager.listPresets().forEach { preset ->
            onClick(presetId(preset)) {
                onPresetSelected(preset)
                true
            }
        }

        ModuleManager.getAll().forEach { module ->
            onClick(moduleId(module)) { event ->
                val clickedExpandZone = event.x >= event.node.bounds.right - 18f && module.entries.isNotEmpty()
                when {
                    event.button == 0 && clickedExpandZone -> {
                        onModuleExpandRequested(module)
                        true
                    }
                    event.button == 1 -> {
                        onModuleExpandRequested(module)
                        true
                    }
                    event.button == 0 && !module.isProtected -> {
                        module.toggle()
                        true
                    }
                    event.button == 0 -> {
                        onModuleExpandRequested(module)
                        true
                    }
                    else -> false
                }
            }
            onClick(sidebarModuleCardId(module)) { event ->
                if (event.button != 0) return@onClick false
                onSidebarModuleSelected(module)
                true
            }
            onClick(sidebarModuleToggleId(module)) { event ->
                if (event.button != 0) return@onClick false
                onSidebarModuleToggleRequested(module)
                true
            }

            module.entries.forEach { entry ->
                when (entry) {
                    is BooleanEntry -> onClick(entryId(module, entry)) {
                        entry.value = !entry.value
                        true
                    }
                    is ButtonEntry -> onClick(entryId(module, entry)) {
                        entry.action()
                        true
                    }
                    is HudEditEntry -> onClick(entryId(module, entry)) {
                        if (module is HudModule) {
                            onHudEditRequested(module)
                            true
                        } else {
                            false
                        }
                    }
                    is EnumEntry<*> -> onClick(entryId(module, entry)) { event ->
                        onEnumDropdownRequested(entry, event)
                        true
                    }
                    is KeybindEntry -> onClick(entryId(module, entry)) {
                        onKeybindRequested(entry)
                        true
                    }
                    is StringEntry -> onClick(entryId(module, entry)) { event ->
                        onStringEditRequested(entry, event)
                        true
                    }
                    is ColorEntry -> onClick(entryId(module, entry)) { event ->
                        onColorEditRequested(entry, event)
                        true
                    }
                    is ItemListEntry -> onClick(entryId(module, entry)) { event ->
                        onItemListRequested(entry, event)
                        true
                    }
                    is IntEntry -> onClick(entryId(module, entry)) { event ->
                        if (entry.min == Int.MIN_VALUE || entry.max == Int.MAX_VALUE) return@onClick false
                        applyIntDrag(entry, event)
                        onNumericDragStarted(entry, event.node.bounds)
                        true
                    }
                    is FloatEntry -> onClick(entryId(module, entry)) { event ->
                        if (entry.min == -Float.MAX_VALUE || entry.max == Float.MAX_VALUE) return@onClick false
                        applyFloatDrag(entry, event)
                        onNumericDragStarted(entry, event.node.bounds)
                        true
                    }
                    is DoubleEntry -> onClick(entryId(module, entry)) { event ->
                        if (entry.min == -Double.MAX_VALUE || entry.max == Double.MAX_VALUE) return@onClick false
                        applyDoubleDrag(entry, event)
                        onNumericDragStarted(entry, event.node.bounds)
                        true
                    }
                    is IntRangeEntry -> onClick(entryId(module, entry)) { event ->
                        val high = rangeHighClosest(entryLowT(entry), entryHighT(entry), numericT(event))
                        applyIntRangeDrag(entry, event, high)
                        onRangeDragStarted(entry, event.node.bounds, high)
                        true
                    }
                    is FloatRangeEntry -> onClick(entryId(module, entry)) { event ->
                        val high = rangeHighClosest(entryLowT(entry), entryHighT(entry), numericT(event))
                        applyFloatRangeDrag(entry, event, high)
                        onRangeDragStarted(entry, event.node.bounds, high)
                        true
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun UiDocument.bindEnumActions(entry: EnumEntry<*>): UiDocument = apply {
        entry.constants.forEachIndexed { index, _ ->
            onClick(enumOptionId(entry, index)) {
                entry.setByIndex(index)
                true
            }
        }
    }

    private fun applyIntDrag(entry: IntEntry, event: UiPointerEvent) {
        val t = numericT(event)
        entry.value = (entry.min + t * (entry.max - entry.min)).roundToInt()
    }

    private fun applyFloatDrag(entry: FloatEntry, event: UiPointerEvent) {
        val t = numericT(event)
        entry.value = entry.min + t * (entry.max - entry.min)
    }

    private fun applyDoubleDrag(entry: DoubleEntry, event: UiPointerEvent) {
        val t = numericT(event).toDouble()
        entry.value = entry.min + t * (entry.max - entry.min)
    }

    private fun applyIntRangeDrag(entry: IntRangeEntry, event: UiPointerEvent, high: Boolean) {
        val t = numericT(event)
        val value = (entry.min + t * (entry.max - entry.min)).roundToInt()
        val (lo, hi) = entry.value
        entry.value = if (high) lo to value.coerceAtLeast(lo) else value.coerceAtMost(hi) to hi
    }

    private fun applyFloatRangeDrag(entry: FloatRangeEntry, event: UiPointerEvent, high: Boolean) {
        val t = numericT(event)
        val scale = Math.pow(10.0, entry.decimals.toDouble()).toFloat()
        val value = (Math.round((entry.min + t * (entry.max - entry.min)) * scale).toFloat()) / scale
        val (lo, hi) = entry.value
        entry.value = if (high) lo to value.coerceAtLeast(lo) else value.coerceAtMost(hi) to hi
    }

    private fun numericT(event: UiPointerEvent): Float =
        sliderT(event.node.bounds, event.x)

    internal companion object {
        const val DEFAULT_SLIDER_KNOB_SIZE = 8f
        const val DEFAULT_SLIDER_TRACK_INSET = DEFAULT_SLIDER_KNOB_SIZE / 2f

        fun sliderT(bounds: UiRect, x: Float): Float =
            ((x - (bounds.x + DEFAULT_SLIDER_TRACK_INSET)) /
                (bounds.width - DEFAULT_SLIDER_TRACK_INSET * 2f).coerceAtLeast(1f))
                .coerceIn(0f, 1f)
    }

    private fun entryLowT(entry: IntRangeEntry): Float =
        (entry.value.first - entry.min).toFloat() / (entry.max - entry.min).coerceAtLeast(1).toFloat()

    private fun entryHighT(entry: IntRangeEntry): Float =
        (entry.value.second - entry.min).toFloat() / (entry.max - entry.min).coerceAtLeast(1).toFloat()

    private fun entryLowT(entry: FloatRangeEntry): Float =
        (entry.value.first - entry.min) / (entry.max - entry.min).coerceAtLeast(0.0001f)

    private fun entryHighT(entry: FloatRangeEntry): Float =
        (entry.value.second - entry.min) / (entry.max - entry.min).coerceAtLeast(0.0001f)

    private fun rangeHighClosest(low: Float, high: Float, target: Float): Boolean =
        kotlin.math.abs(target - high) < kotlin.math.abs(target - low)

    private fun moduleValues(module: Module): Map<String, String> =
        mapOf(
            "module.id" to moduleId(module),
            "module.cardId" to sidebarModuleCardId(module),
            "module.toggleId" to sidebarModuleToggleId(module),
            "module.name" to module.name,
            "module.description" to module.description,
            "module.bg" to if (module.isEnabled()) themeValues()["theme.entrySelectedBg"].orEmpty() else themeValues()["theme.moduleBg"].orEmpty(),
            "module.fg" to if (module.isEnabled()) "#FFD7D7E4" else "#FF76768C",
        ) + themeValues()

    private fun colorValues(entry: ColorEntry, editingHex: Boolean = false, editingText: String = ""): Map<String, String> {
        val live = entry.value
        val custom = entry.customValue
        val isChroma = entry.pickerMode == ColorEntry.PickerMode.CHROMA
        val (baseHue, baseSat, baseValue) = rgbToHsv(custom)
        val hue = if (isChroma) ((ColorEntry.chromaTimeSeconds() * entry.chromaSpeed * 360f) % 360f + 360f) % 360f else baseHue
        val saturation = if (isChroma) entry.chromaSaturation else baseSat
        val value = if (isChroma) entry.chromaBrightness else baseValue
        return mapOf(
            "color.mode" to label(entry.pickerMode.name),
            "color.hue" to hue.toString(),
            "color.saturation" to saturation.toString(),
            "color.value" to value.toString(),
            "color.preview" to colorHex(live),
            "color.alpha" to (live.a / 255f).toString(),
            "color.hex" to if (editingHex) editingText else colorHex(live),
            "color.speed" to "%.2f".format(entry.chromaSpeed),
            "color.speedProgress" to ((entry.chromaSpeed - 0.05f) / (8f - 0.05f)).coerceIn(0f, 1f).toString(),
        )
    }

    private fun alphaSlot(entry: ColorEntry): List<UiNode> =
        if (entry.allowAlpha) listOf(templates.instantiate("color-alpha-bar", themeValues() + colorValues(entry))) else emptyList()

    private fun colorChannelFields(entry: ColorEntry, editingChannel: Int?, editingText: String): List<UiNode> {
        val channels = mutableListOf("R" to entry.customValue.r, "G" to entry.customValue.g, "B" to entry.customValue.b)
        if (entry.allowAlpha) channels += "A" to entry.customValue.a
        return channels.mapIndexed { index, (label, value) ->
            templates.instantiate(
                "color-channel-field",
                mapOf(
                    "field.id" to "color:channel:$index",
                    "field.label" to label,
                    "field.value" to if (editingChannel == index) editingText else value.toString(),
                ) + themeValues(),
            )
        }
    }

    private fun colorModeOptions(entry: ColorEntry): List<UiNode> {
        return colorModes(entry).map { mode ->
            templates.instantiate(
                "color-mode-option",
                mapOf(
                    "mode.id" to "color:mode:${mode.name}",
                    "mode.label" to label(mode.name),
                    "mode.fg" to if (entry.pickerMode == mode) "#FFD7D7E4" else "#FF76768C",
                    "mode.bg" to if (entry.pickerMode == mode) themeValues()["theme.entrySelectedBg"].orEmpty() else themeValues()["theme.entryBg"].orEmpty(),
                ) + themeValues(),
            )
        }
    }

    private fun colorModeCount(entry: ColorEntry): Int =
        colorModes(entry).size

    private fun colorModes(entry: ColorEntry): List<ColorEntry.PickerMode> =
        if (entry !== me.ghluka.medved.module.modules.other.Colour.accent) {
            listOf(ColorEntry.PickerMode.CUSTOM, ColorEntry.PickerMode.THEME, ColorEntry.PickerMode.CHROMA)
        } else {
            listOf(ColorEntry.PickerMode.CUSTOM, ColorEntry.PickerMode.CHROMA)
        }

    private fun colorHex(color: Color): String =
        "#%02X%02X%02X%02X".format(color.a, color.r, color.g, color.b)

    private fun rgbToHsv(color: Color): Triple<Float, Float, Float> {
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
}
