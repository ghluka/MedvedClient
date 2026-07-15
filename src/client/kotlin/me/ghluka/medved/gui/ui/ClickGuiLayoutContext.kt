package me.ghluka.medved.gui.ui

import me.ghluka.medved.config.ConfigGroup
import me.ghluka.medved.config.entry.ConfigEntry
import me.ghluka.medved.config.entry.EnumEntry
import me.ghluka.medved.config.entry.ItemListEntry
import me.ghluka.medved.config.entry.StringEntry
import me.ghluka.medved.module.Module

data class ClickGuiLayoutContext(
    val categoryPositions: Map<Module.Category, Pair<Int, Int>>,
    val categoryOrder: List<Module.Category> = Module.Category.entries,
    val expandedModules: Set<Module>,
    val collapsedCategories: Set<Module.Category>,
    val selectedCategory: Module.Category? = null,
    val detailModule: Module? = null,
    val sidebarTab: Int = 0,
    val sidebarX: Int = 40,
    val sidebarY: Int = 40,
    val configX: Int = 10,
    val configY: Int = 30,
    val configCollapsed: Boolean = true,
    val presetName: String = "",
    val presetActive: Boolean = false,
    val presetCursor: Int = 0,
    val presetScrollPx: Int = 0,
    val presets: List<String> = emptyList(),
    val dropdownScroll: Float = 0f,
    val sidebarModulesScroll: Float = 0f,
    val sidebarConfigScroll: Float = 0f,
    val activeStringEntry: StringEntry? = null,
    val activeStringText: String = "",
    val activeStringCursor: Int = 0,
    val activeStringScrollPx: Int = 0,
    val openEnumEntry: EnumEntry<*>? = null,
    val openItemListEntry: ItemListEntry? = null,
    val enumEntry: EnumEntry<*>? = null,
    val enumX: Int = 0,
    val enumY: Int = 0,
    val enumW: Int = 120,
    val includeEntry: (Module, ConfigEntry<*>) -> Boolean,
)

internal sealed interface ClickGuiSettingItem {
    data class Group(val group: ConfigGroup) : ClickGuiSettingItem
    data class Entry(val module: Module, val entry: ConfigEntry<*>) : ClickGuiSettingItem
}
