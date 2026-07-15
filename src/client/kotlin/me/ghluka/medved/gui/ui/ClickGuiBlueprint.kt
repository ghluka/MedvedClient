package me.ghluka.medved.gui.ui

import me.ghluka.medved.config.ConfigGroup
import me.ghluka.medved.config.entry.BooleanEntry
import me.ghluka.medved.config.entry.ButtonEntry
import me.ghluka.medved.config.entry.ColorEntry
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
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.ModuleManager
import me.ghluka.medved.module.modules.other.Colour
import me.ghluka.medved.module.modules.other.Font
import org.lwjgl.glfw.GLFW

internal class ClickGuiBlueprint(
    private val templates: UiTemplateSet,
    private val layout: ClickGuiLayoutContext,
) {
    fun instantiateMode(name: String): UiNode =
        expandNode(templates.template(name), ClickGuiData())
            ?: error("Click GUI mode '$name' did not produce a root node")

    private fun expandNode(node: UiNode, data: ClickGuiData): UiNode? {
        return when (node.type) {
            "component" -> {
                val templateName = node.attributes["name"]?.replaceData(data)
                    ?: error("component is missing a name")
                expandNode(templates.template(templateName), data)
            }
            "for" -> {
                val items = loopItems(node.attributes["each"], data)
                UiNode(
                    type = "box",
                    axis = node.axis,
                    width = resolvedSize(node, data, width = true),
                    height = resolvedSize(node, data, width = false),
                    x = node.attributes["x"]?.replaceData(data)?.toFloatOrNull() ?: node.x,
                    y = node.attributes["y"]?.replaceData(data)?.toFloatOrNull() ?: node.y,
                    style = node.style,
                    attributes = node.attributes.mapValues { it.value.replaceData(data) },
                    children = items.flatMap { item ->
                        node.children.mapNotNull { child -> expandNode(child, item) }
                    },
                )
            }
            "if" -> {
                if (condition(node.attributes["condition"], data)) {
                    UiNode(
                        type = "box",
                        axis = node.axis,
                        width = resolvedSize(node, data, width = true),
                        height = resolvedSize(node, data, width = false),
                        style = node.style,
                        attributes = node.attributes.mapValues { it.value.replaceData(data) },
                        children = node.children.mapNotNull { expandNode(it, data) },
                    )
                } else {
                    null
                }
            }
            else -> {
                val attrs = node.attributes.mapValues { it.value.replaceData(data) }
                UiNode(
                    type = node.type,
                    id = node.id?.replaceData(data),
                    text = node.text?.replaceData(data),
                    axis = node.axis,
                    width = resolvedSize(node, data, width = true),
                    height = resolvedSize(node, data, width = false),
                    x = attrs["x"]?.toFloatOrNull() ?: node.x,
                    y = attrs["y"]?.toFloatOrNull() ?: node.y,
                    style = node.style,
                    attributes = attrs,
                    children = node.children.mapNotNull { expandNode(it, data) },
                )
            }
        }
    }

    private fun loopItems(each: String?, data: ClickGuiData): List<ClickGuiData> =
        when (each) {
            "categories" -> layout.categoryOrder
                .map { data.copy(category = it) }
            "modules" -> data.category
                ?.let(ModuleManager::getByCategory)
                ?.let { modules ->
                    modules.mapIndexed { index, module ->
                        data.copy(
                            module = module,
                            moduleLastVisible = index == modules.lastIndex && module !in layout.expandedModules,
                        )
                    }
                }
                ?: emptyList()
            "selected-modules" -> layout.selectedCategory
                ?.let(ModuleManager::getByCategory)
                ?.map { data.copy(category = layout.selectedCategory, module = it) }
                ?: emptyList()
            "module-description-lines" -> data.module
                ?.let(::moduleDescriptionLines)
                ?.map { data.copy(moduleDescriptionLine = it) }
                ?: emptyList()
            "detail-settings" -> layout.detailModule
                ?.let { module -> settingItems(module).map { data.copy(module = module, setting = it) } }
                ?: emptyList()
            "settings" -> data.module
                ?.let { module -> settingItems(module).map { data.copy(setting = it) } }
                ?: emptyList()
            "enum-options" -> layout.enumEntry
                ?.constants
                ?.mapIndexed { index, constant ->
                    data.copy(enumOptionIndex = index, enumOptionName = constant.name)
                }
                ?: emptyList()
            "presets" -> layout.presets.map { data.copy(presetName = it) }
            else -> emptyList()
        }

    private fun settingItems(module: Module): List<ClickGuiSettingItem> {
        val result = mutableListOf<ClickGuiSettingItem>()
        var previousGroup: ConfigGroup? = null
        for (entry in module.entries.filter { layout.includeEntry(module, it) }) {
            val group = entry.group
            if (group != null && group !== previousGroup) {
                result += ClickGuiSettingItem.Group(group)
            }
            result += ClickGuiSettingItem.Entry(module, entry)
            previousGroup = group
        }
        return result
    }

    private fun condition(condition: String?, data: ClickGuiData): Boolean =
        when (condition) {
            "category.expanded" -> data.category !in layout.collapsedCategories
            "module.expanded" -> data.module in layout.expandedModules
            "module.has-settings" -> data.module?.entries?.any { layout.includeEntry(data.module, it) } == true
            "setting.is-entry" -> data.setting is ClickGuiSettingItem.Entry
            "setting.is-group" -> data.setting is ClickGuiSettingItem.Group
            "config.expanded" -> !layout.configCollapsed
            "presets.empty" -> layout.presets.isEmpty()
            "presets.present" -> layout.presets.isNotEmpty()
            "sidebar.modules-tab" -> layout.sidebarTab == 0
            "sidebar.config-tab" -> layout.sidebarTab == 1
            "sidebar.module-list" -> layout.sidebarTab == 0 && layout.detailModule == null
            "sidebar.detail" -> layout.sidebarTab == 0 && layout.detailModule != null
            else -> false
        }

    private fun resolvedSize(node: UiNode, data: ClickGuiData, width: Boolean): UiSize {
        val primary = if (width) "w" else "h"
        val secondary = if (width) "width" else "height"
        return node.attributes[primary]?.replaceData(data)?.toUiSizeOrNull()
            ?: node.attributes[secondary]?.replaceData(data)?.toUiSizeOrNull()
            ?: if (width) node.width else node.height
    }

    private fun String.replaceData(data: ClickGuiData): String =
        Regex("""\{\{\s*([A-Za-z0-9_.-]+)\s*}}""").replace(this) { match ->
            valueFor(data, match.groupValues[1])
        }

    private fun String.toUiSizeOrNull(): UiSize? =
        when (lowercase()) {
            "", "wrap", "auto" -> UiSize.Wrap
            "fill", "match", "*" -> UiSize.Fill
            else -> toFloatOrNull()?.let(UiSize::Px) ?: removeSuffix("px").toFloatOrNull()?.let(UiSize::Px)
        }

    private data class ClickGuiData(
        val category: Module.Category? = null,
        val module: Module? = null,
        val setting: ClickGuiSettingItem? = null,
        val enumOptionIndex: Int = -1,
        val enumOptionName: String = "",
        val presetName: String = "",
        val moduleDescriptionLine: String = "",
        val moduleLastVisible: Boolean = false,
    )

    private fun valueFor(data: ClickGuiData, key: String): String {
        val entrySetting = data.setting as? ClickGuiSettingItem.Entry
        val groupSetting = data.setting as? ClickGuiSettingItem.Group
        val entry = entrySetting?.entry
        val entryModule = entrySetting?.module ?: data.module

        return when (key) {
            "category.name" -> data.category?.name.orEmpty()
            "category.id" -> data.category?.let(::categoryId).orEmpty()
            "category.x" -> data.category?.let { layout.categoryPositions[it]?.first?.toString() }.orEmpty()
            "category.y" -> data.category?.let { layout.categoryPositions[it]?.second?.toString() }.orEmpty()
            "theme.bg" -> hex(shade(9, 0.06f, 115))
            "theme.panelBg" -> hex(shade(20, 0.10f, 242))
            "theme.headerBg" -> hex(shade(24, 0.18f))
            "theme.sidebarHeaderBg" -> hex(shade(24, 0.16f))
            "theme.sidebarRailBg" -> hex(shade(16, 0.07f))
            "theme.sidebarDividerBg" -> hex(shade(255, 0.05f))
            "theme.moduleBg" -> hex(shade(24, 0.08f))
            "theme.moduleHoverBg" -> hex(shade(38, 0.13f))
            "theme.entryBg" -> hex(shade(21, 0.07f))
            "theme.entrySelectedBg" -> hex(shade(42, 0.18f))
            "theme.entryHoverBg" -> hex(shade(32, 0.12f))
            "theme.controlBg" -> hex(shade(42, 0.14f))
            "theme.controlActiveBg" -> hex(shade(45, 0.25f))
            "theme.sliderBg" -> hex(shade(38, 0.14f))
            "theme.sliderFg" -> sliderFg()
            "theme.accentSoft" -> accentSoftHex()
            "theme.text" -> "#FFD7D7E4"
            "theme.textDim" -> "#FF76768C"
            "theme.accent" -> accentHex()
            "theme.transparent" -> "#00000000"
            "category.bg" -> if (data.category == layout.selectedCategory) hex(shade(42, 0.18f)) else "#00000000"
            "category.fg" -> if (data.category == layout.selectedCategory) "#FFD7D7E4" else "#FF76768C"
            "category.sign" -> if (data.category in layout.collapsedCategories) "+" else "-"
            "sidebar.modulesFg" -> if (layout.sidebarTab == 0) "#FFD7D7E4" else "#FF76768C"
            "sidebar.configFg" -> if (layout.sidebarTab == 1) "#FFD7D7E4" else "#FF76768C"
            "sidebar.x" -> layout.sidebarX.toString()
            "sidebar.y" -> layout.sidebarY.toString()
            "config.x" -> layout.configX.toString()
            "config.y" -> layout.configY.toString()
            "config.title" -> "CONFIGS ${if (layout.configCollapsed) "+" else "-"}"
            "config.sign" -> if (layout.configCollapsed) "+" else "-"
            "preset.input" -> if (layout.presetName.isBlank() && !layout.presetActive) "preset name..." else layout.presetName
            "preset.active" -> layout.presetActive.toString()
            "preset.cursor" -> layout.presetCursor.toString()
            "preset.scroll" -> layout.presetScrollPx.toString()
            "preset.name" -> data.presetName
            "preset.id" -> presetId(data.presetName)
            "preset.fg" -> if (data.presetName == layout.presetName) "#FFD7D7E4" else "#FF76768C"
            "preset.bg" -> if (data.presetName == layout.presetName) hex(shade(42, 0.18f)) else hex(shade(21, 0.07f))
            "scroll.dropdown" -> layout.dropdownScroll.toString()
            "scroll.sidebar.modules" -> layout.sidebarModulesScroll.toString()
            "scroll.sidebar.config" -> layout.sidebarConfigScroll.toString()
            "module.id" -> data.module?.let(::moduleId).orEmpty()
            "module.cardId" -> data.module?.let(::sidebarModuleCardId).orEmpty()
            "module.toggleId" -> data.module?.let(::sidebarModuleToggleId).orEmpty()
            "module.name" -> data.module?.name.orEmpty()
            "module.description" -> data.module?.description.orEmpty()
            "module.descriptionLine" -> data.moduleDescriptionLine
            "module.cardH" -> data.module?.let { moduleCardHeight(it).toString() } ?: "54"
            "module.accentTemplate" -> if (data.moduleLastVisible) "module-accent-bottom" else "module-accent"
            "module.bg" -> if (data.module?.isEnabled() == true) hex(shade(42, 0.18f)) else hex(shade(24, 0.08f))
            "module.cardBg" -> hex(shade(30, 0.10f))
            "module.cardHoverBg" -> hex(shade(42, 0.15f))
            "module.cardOverlay" -> data.module
                ?.takeIf { it.isEnabled() }
                ?.let { accentOverlayHex() }
                ?: "#00000000"
            "module.fg" -> if (data.module?.isEnabled() == true) "#FFD7D7E4" else "#FF76768C"
            "module.accent" -> if (data.module?.isEnabled() == true) accentHex() else "#00000000"
            "module.on" -> (data.module?.isEnabled() == true).toString()
            "module.toggleKnob" -> if (data.module?.isEnabled() == true) accentHex() else hex(shade(140, 0.10f))
            "detail.name" -> layout.detailModule?.name.orEmpty()
            "detail.description" -> layout.detailModule?.description.orEmpty()
            "detail.toggleId" -> layout.detailModule?.let(::sidebarModuleToggleId).orEmpty()
            "detail.on" -> (layout.detailModule?.isEnabled() == true).toString()
            "detail.toggleKnob" -> if (layout.detailModule?.isEnabled() == true) accentHex() else hex(shade(140, 0.10f))
            "module.sign" -> data.module
                ?.takeIf { module -> module.entries.any { layout.includeEntry(module, it) } }
                ?.let { module -> if (module in layout.expandedModules) "-" else "+" }
                .orEmpty()
            "setting.template" -> settingTemplate(data.setting)
            "entry.id" -> if (entry != null && entryModule != null) entryId(entryModule, entry) else ""
            "entry.label" -> entry?.name?.let(::label).orEmpty()
            "entry.value" -> entry?.let { entryValue(it, layout) }.orEmpty()
            "entry.fg" -> if (entry is BooleanEntry && entry.value) "#FFD7D7E4" else "#FF76768C"
            "entry.on" -> if (entry is BooleanEntry && entry.value) "true" else "false"
            "entry.progress" -> entry?.let(::entryProgress) ?: "0"
            "entry.low" -> entry?.let(::entryLowProgress) ?: "0"
            "entry.high" -> entry?.let(::entryHighProgress) ?: "1"
            "entry.color" -> entry?.let(::entryColor) ?: "#FFFFFFFF"
            "entry.count" -> (entry as? ItemListEntry)?.value?.size?.toString().orEmpty()
            "entry.enumWidth" -> (entry as? EnumEntry<*>)?.let(::enumSelectedControlWidth)?.toString() ?: "64"
            "entry.open" -> when (entry) {
                is EnumEntry<*> -> (entry === layout.openEnumEntry).toString()
                is ItemListEntry -> (entry === layout.openItemListEntry).toString()
                else -> "false"
            }
            "entry.active" -> if (entry === layout.activeStringEntry) "true" else "false"
            "entry.cursor" -> if (entry === layout.activeStringEntry) layout.activeStringCursor.toString() else "0"
            "entry.scroll" -> if (entry === layout.activeStringEntry) layout.activeStringScrollPx.toString() else "0"
            "enum.x" -> layout.enumX.toString()
            "enum.y" -> layout.enumY.toString()
            "enum.w" -> layout.enumW.toString()
            "enum.h" -> (((layout.enumEntry?.constants?.size ?: 0) * 13) + 4).toString()
            "enum.option.id" -> layout.enumEntry?.let { enumOptionId(it, data.enumOptionIndex) }.orEmpty()
            "enum.option.name" -> label(data.enumOptionName)
            "enum.option.bg" -> if (layout.enumEntry?.value?.name == data.enumOptionName) hex(shade(42, 0.18f)) else hex(shade(21, 0.07f))
            "enum.option.fg" -> if (layout.enumEntry?.value?.name == data.enumOptionName) "#FFD7D7E4" else "#FF76768C"
            "group.name" -> groupSetting?.group?.name?.let(::label).orEmpty()
            else -> ""
        }
    }

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

    private fun accentSoftHex(): String {
        val color = Colour.accent.liveColor(Colour.accent.value)
        return "#%08X".format((105 shl 24) or (color.r shl 16) or (color.g shl 8) or color.b)
    }

    private fun accentOverlayHex(): String {
        val color = Colour.accent.liveColor(Colour.accent.value)
        return "#%08X".format((18 shl 24) or (color.r shl 16) or (color.g shl 8) or color.b)
    }

    private fun sliderFg(): String {
        val color = Colour.accent.liveColor(Colour.accent.value)
        return "#%08X".format((255 shl 24) or ((color.r * 0.8f).toInt() shl 16) or ((color.g * 0.8f).toInt() shl 8) or (color.b * 0.8f).toInt())
    }

    private fun moduleCardHeight(module: Module): Int {
        val lines = moduleDescriptionLines(module).size
        return 24 + (lines * SIDEBAR_CARD_DESC_LINE_H).coerceAtLeast(SIDEBAR_CARD_DESC_LINE_H) + 13
    }

    private fun moduleDescriptionLines(module: Module): List<String> =
        wrapText(module.description, SIDEBAR_CARD_DESCRIPTION_W).ifEmpty { listOf("") }

    private fun wrapText(text: String, maxWidth: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val font = Font.getFont()
        val lines = mutableListOf<String>()
        var current = ""
        for (word in text.split(Regex("\\s+")).filter { it.isNotBlank() }) {
            val candidate = if (current.isBlank()) word else "$current $word"
            if (font.width(Font.styledText(candidate)) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotBlank()) lines += current
                current = word
            }
        }
        if (current.isNotBlank()) lines += current
        return lines
    }

}

private const val SIDEBAR_CARD_DESCRIPTION_W = 288
private const val SIDEBAR_CARD_DESC_LINE_H = 12

internal fun enumSelectedControlWidth(entry: EnumEntry<*>): Int =
    (enumLabelWidth(label(entry.value.name)) + ENUM_CONTROL_CHROME_W).coerceIn(42, 116)

internal fun enumDropdownWidth(entry: EnumEntry<*>): Int {
    val selectedW = enumSelectedControlWidth(entry)
    val widestOptionW = entry.constants
        .maxOfOrNull { enumLabelWidth(label(it.name)) + ENUM_OPTION_PADDING_W }
        ?: selectedW
    return widestOptionW.coerceAtLeast(selectedW).coerceIn(64, 180)
}

private const val ENUM_CONTROL_CHROME_W = 18
private const val ENUM_OPTION_PADDING_W = 10

private fun enumLabelWidth(text: String): Int =
    Font.getFont().width(Font.styledText(text))

internal fun moduleId(module: Module): String =
    "module:${module.name}"

internal fun sidebarModuleCardId(module: Module): String =
    "sidebar-module:${module.name}"

internal fun sidebarModuleToggleId(module: Module): String =
    "sidebar-module-toggle:${module.name}"

internal fun categoryId(category: Module.Category): String =
    "category:${category.name}"

internal fun entryId(module: Module, entry: ConfigEntry<*>): String =
    "entry:${module.name}:${entry.name}"

internal fun enumOptionId(entry: EnumEntry<*>, index: Int): String =
    "enum:${entry.name}:$index"

internal fun presetId(name: String): String =
    "config:preset:$name"

internal fun label(name: String): String =
    name.replace('_', ' ').replaceFirstChar { it.uppercase() }

internal fun settingTemplate(setting: ClickGuiSettingItem?): String =
    when (setting) {
        is ClickGuiSettingItem.Group -> "group-header"
        is ClickGuiSettingItem.Entry -> entryTemplate(setting.entry)
        null -> "entry-row"
    }

internal fun entryTemplate(entry: ConfigEntry<*>): String =
    when (entry) {
        is BooleanEntry -> "boolean-entry"
        is ButtonEntry -> "button-entry"
        is HudEditEntry -> "button-entry"
        is EnumEntry<*> -> "enum-entry"
        is KeybindEntry -> "keybind-entry"
        is StringEntry -> "string-entry"
        is ColorEntry -> "color-entry"
        is ItemListEntry -> "item-list-entry"
        is IntEntry -> if (entry.min != Int.MIN_VALUE && entry.max != Int.MAX_VALUE) "number-entry" else "entry-row"
        is FloatEntry -> if (entry.min != -Float.MAX_VALUE && entry.max != Float.MAX_VALUE) "number-entry" else "entry-row"
        is DoubleEntry -> if (entry.min != -Double.MAX_VALUE && entry.max != Double.MAX_VALUE) "number-entry" else "entry-row"
        is IntRangeEntry -> "range-entry"
        is FloatRangeEntry -> "range-entry"
        else -> "entry-row"
    }

internal fun entryValue(entry: ConfigEntry<*>): String =
    entryValue(entry, null)

internal fun entryValue(entry: ConfigEntry<*>, layout: ClickGuiLayoutContext?): String =
    when (entry) {
        is BooleanEntry -> if (entry.value) "On" else "Off"
        is IntEntry -> entry.value.toString()
        is FloatEntry -> "%.2f".format(entry.value)
        is DoubleEntry -> "%.2f".format(entry.value)
        is StringEntry -> if (layout?.activeStringEntry === entry) layout.activeStringText else entry.value
        is ColorEntry -> "#%02X%02X%02X%02X".format(entry.value.a, entry.value.r, entry.value.g, entry.value.b)
        is KeybindEntry -> keyName(entry.value)
        is EnumEntry<*> -> label(entry.value.name)
        is IntRangeEntry -> "${entry.value.first} - ${entry.value.second}"
        is FloatRangeEntry -> "%.${entry.decimals}f - %.${entry.decimals}f".format(entry.value.first, entry.value.second)
        is ButtonEntry -> entry.label
        is HudEditEntry -> "Edit Position"
        is ItemListEntry -> "${entry.value.size}"
        else -> entry.value.toString()
    }

internal fun entryProgress(entry: ConfigEntry<*>): String {
    val progress = when (entry) {
        is IntEntry -> (entry.value - entry.min).toFloat() / (entry.max - entry.min).coerceAtLeast(1).toFloat()
        is FloatEntry -> (entry.value - entry.min) / (entry.max - entry.min).coerceAtLeast(0.0001f)
        is DoubleEntry -> ((entry.value - entry.min) / (entry.max - entry.min).coerceAtLeast(0.0001)).toFloat()
        else -> 0f
    }
    return progress.coerceIn(0f, 1f).toString()
}

internal fun entryLowProgress(entry: ConfigEntry<*>): String {
    val progress = when (entry) {
        is IntRangeEntry -> (entry.value.first - entry.min).toFloat() / (entry.max - entry.min).coerceAtLeast(1).toFloat()
        is FloatRangeEntry -> (entry.value.first - entry.min) / (entry.max - entry.min).coerceAtLeast(0.0001f)
        else -> 0f
    }
    return progress.coerceIn(0f, 1f).toString()
}

internal fun entryHighProgress(entry: ConfigEntry<*>): String {
    val progress = when (entry) {
        is IntRangeEntry -> (entry.value.second - entry.min).toFloat() / (entry.max - entry.min).coerceAtLeast(1).toFloat()
        is FloatRangeEntry -> (entry.value.second - entry.min) / (entry.max - entry.min).coerceAtLeast(0.0001f)
        else -> 1f
    }
    return progress.coerceIn(0f, 1f).toString()
}

internal fun entryColor(entry: ConfigEntry<*>): String =
    when (entry) {
        is ColorEntry -> {
            val color = entry.liveColor(entry.value)
            "#%02X%02X%02X%02X".format(color.a, color.r, color.g, color.b)
        }
        else -> "#FFFFFFFF"
    }

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
