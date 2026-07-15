package me.ghluka.medved.gui.helpers

import me.ghluka.medved.config.entry.ItemListEntry
import me.ghluka.medved.gui.ClickGui
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import java.util.Locale

internal data class ItemCategory(
    val id: String,
    val label: String,
    val matches: (String) -> Boolean,
)

internal val itemCategories = listOf(
    ItemCategory("wool", "Wool") { name -> name.endsWith("wool") },
    ItemCategory("food", "Food") { name -> name in edibleItemNames },
    ItemCategory("swords", "Swords") { name -> name.endsWith("sword") },
    ItemCategory("axes", "Axes") { name -> name.endsWith("axe") },
    ItemCategory("pickaxes", "Pickaxes") { name -> name.endsWith("pickaxe") },
    ItemCategory("shovels", "Shovels") { name -> name.endsWith("shovel") },
    ItemCategory("hoes", "Hoes") { name -> name.endsWith("hoe") },
    ItemCategory("armor", "Armor") { name ->
        name.endsWith("helmet") || name.endsWith("chestplate") ||
            name.endsWith("leggings") || name.endsWith("boots")
    },
    ItemCategory("potions", "Potions") { name -> name.contains("potion") },
    ItemCategory("blocks", "Blocks") { name -> isBlockByName(name) },
)

private val blockCategories = setOf("wool", "blocks")
private var cachedItems: List<Pair<String, Item>>? = null
private var edibleItemNames: Set<String> = emptySet()
private var rowCacheKey: Pair<String, ItemListEntry.Filter>? = null
private var rowCache: List<PickerRow> = emptyList()

internal fun getAllItems(): List<Pair<String, Item>> {
    cachedItems?.let { return it }
    val list = BuiltInRegistries.ITEM
        .asSequence()
        .filter { it !== Items.AIR }
        .map { item -> BuiltInRegistries.ITEM.getKey(item).path.lowercase(Locale.ROOT) to item }
        .distinctBy { it.first }
        .sortedBy { it.first.replace('_', ' ') }
        .toList()
    cachedItems = list
    edibleItemNames = list.filter { (_, item) -> item.components().has(DataComponents.FOOD) }
        .mapTo(mutableSetOf()) { it.first }
    return list
}

internal fun findItemByName(name: String): Pair<String, Item>? =
    normalizeItemName(name).let { normalized ->
        getAllItems().firstOrNull { it.first == normalized }
    }

internal fun normalizeItemName(name: String): String {
    var normalized = name.trim().lowercase(Locale.ROOT)
    normalized = normalized.substringAfter("item.minecraft.")
    normalized = normalized.substringAfter("block.minecraft.")
    normalized = normalized.substringAfter("minecraft:")
    return normalized
}

internal sealed interface PickerRow {
    val label: String
}

private data class CategoryRow(
    val category: ItemCategory,
    val items: List<Pair<String, Item>>,
) : PickerRow {
    override val label: String get() = category.label
}

private data class ItemRow(
    val item: Pair<String, Item>,
) : PickerRow {
    override val label: String get() = item.first
}

internal fun pickerRowId(row: PickerRow): String =
    when (row) {
        is ItemRow -> row.item.first
        is CategoryRow -> row.category.id + "_category"
    }

internal fun pickerRowIconName(row: PickerRow): String =
    when (row) {
        is ItemRow -> row.item.first
        is CategoryRow -> row.items.getOrNull(animIndex(row.items.size.coerceAtLeast(1)))?.first.orEmpty()
    }

internal fun pickerRowLabel(row: PickerRow): String =
    when (row) {
        is ItemRow -> row.item.first.replace('_', ' ').replaceFirstChar { it.uppercase() }
        is CategoryRow -> "${row.category.label} (${row.items.size})"
    }

internal const val ITEM_LIST_DROPDOWN_MAX_H = 220

internal fun ClickGui.filteredItemListRowsFor(
    query: String,
    filter: ItemListEntry.Filter,
): List<PickerRow> {
    val cacheKey = query to filter
    if (cacheKey == rowCacheKey) return rowCache

    val q = query.lowercase(Locale.getDefault())
    var base = getAllItems()
    if (filter == ItemListEntry.Filter.BLOCKS_ONLY) {
        base = base.filter { (_, item) -> item is BlockItem }
    }

    val filtered = if (q.isBlank()) {
        base
    } else {
        base.filter { (name, _) -> name.contains(q) || name.replace('_', ' ').contains(q) }
    }

    val result: List<PickerRow> = if (q.isNotBlank()) {
        filtered.map { ItemRow(it) }
    } else {
        val seen = mutableSetOf<String>()
        buildList {
            for (category in itemCategories) {
                if (filter == ItemListEntry.Filter.BLOCKS_ONLY && category.id !in blockCategories) continue
                val items = filtered.filter { category.matches(it.first) }
                if (items.isNotEmpty()) {
                    add(CategoryRow(category, items))
                    if (filter != ItemListEntry.Filter.BLOCKS_ONLY) items.mapTo(seen) { it.first }
                }
            }
            filtered.filter { it.first !in seen }.forEach { add(ItemRow(it)) }
        }
    }

    rowCacheKey = cacheKey
    rowCache = result
    return result
}

private fun isBlockByName(name: String): Boolean =
    findItemByName(name)?.second is BlockItem

private fun animIndex(size: Int): Int =
    (System.currentTimeMillis() / 600 % size).toInt()
