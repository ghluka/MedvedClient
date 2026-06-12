package me.ghluka.medved.gui.components
 
import me.ghluka.medved.gui.ClickGui
import net.minecraft.client.gui.GuiGraphicsExtractor
import me.ghluka.medved.config.entry.ItemListEntry
import me.ghluka.medved.util.Text
import me.ghluka.medved.util.roundedFill
import me.ghluka.medved.util.TextCentered
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.item.BlockItem
import java.lang.reflect.Modifier
import java.util.Locale
 
internal data class ItemCategory(
    val id: String,
    val label: String,
    val matches: (String) -> Boolean,
)

internal val itemCategories = listOf(
    ItemCategory("wool", "Wool") { name -> name.endsWith("wool") },
    ItemCategory("food", "Food") { name -> name in _edibleItemNames },
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
    ItemCategory("blocks", "Blocks") { name -> _isBlockByName(name) },
)

private val BLOCK_CATEGORIES = setOf("wool", "blocks")
 
internal var _cachedItems: List<Pair<String, Item>>? = null
 
internal fun getAllItems(): List<Pair<String, Item>> {
    _cachedItems?.let { return it }
    val list = mutableListOf<Pair<String, Item>>()
    try {
        for (f in Items::class.java.fields) {
            if (Modifier.isStatic(f.modifiers) && Item::class.java.isAssignableFrom(f.type)) {
                val v = f.get(null) as? Item ?: continue
                list.add(f.name.lowercase(Locale.getDefault()) to v)
            }
        }
    } catch (_: Exception) {}
    _cachedItems = list.distinctBy { it.first }
 
    _buildEdibleSet(_cachedItems!!)
 
    return _cachedItems!!
}
 
internal fun findItemByName(name: String): Pair<String, Item>? =
    getAllItems().firstOrNull { it.first.equals(name, ignoreCase = true) }
 
private var _edibleItemNames: Set<String> = emptySet()
 
private fun _buildEdibleSet(items: List<Pair<String, Item>>) {
    val edible = mutableSetOf<String>()
    for ((fname, item) in items) {
        if (_isEdible(item)) edible.add(fname)
    }
    _edibleItemNames = edible
}
 
private fun _isEdible(item: Item): Boolean {
    return item.components().has(DataComponents.FOOD)
}

private fun _isBlockByName(name: String): Boolean {
    return findItemByName(name)?.second?.let { _isBlock(it) } == true
}

private fun _isBlock(item: Item): Boolean {
    return item is BlockItem
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
 
private fun itemNameOf(entry: Pair<String, Item>) = entry.first // already lowercase from getAllItems()

private var _rowCacheKey: Pair<String, ItemListEntry.Filter>? = null
private var _rowCache: List<PickerRow> = emptyList()

internal fun ClickGui.filteredItemListRowsFor(query: String, filter: ItemListEntry.Filter = ItemListEntry.Filter.NONE): List<PickerRow> {
    val cacheKey = query to filter
    if (cacheKey == _rowCacheKey) return _rowCache

    val q = query.lowercase(Locale.getDefault())
    var base = getAllItems()
    
    if (filter == ItemListEntry.Filter.BLOCKS_ONLY) {
        base = base.filter { (_, item) -> _isBlock(item) }
    }
    
    val filtered = if (q.isBlank()) base else base.filter { (fname, _) ->
        fname.contains(q) || fname.replace('_', ' ').contains(q)
    }

    val result: List<PickerRow> = if (q.isNotBlank()) {
        filtered.map { ItemRow(it) }
    } else {
        val seen = mutableSetOf<String>()
        val rows = mutableListOf<PickerRow>()
        for (category in itemCategories) {
            if (filter == ItemListEntry.Filter.BLOCKS_ONLY && category.id !in BLOCK_CATEGORIES) {
                continue
            }
            val items = filtered.filter { category.matches(itemNameOf(it)) }
            if (items.isNotEmpty()) {
                rows.add(CategoryRow(category, items))
                if (filter != ItemListEntry.Filter.BLOCKS_ONLY) {
                    items.mapTo(seen) { it.first }
                }
            }
        }
        for (item in filtered) {
            if (item.first !in seen) rows.add(ItemRow(item))
        }
        rows
    }
 
    _rowCacheKey = cacheKey
    _rowCache = result
    return result
}

private fun animIndex(size: Int): Int = (System.currentTimeMillis() / 600 % size).toInt()

internal const val ITEM_LIST_DROPDOWN_MAX_H = 220
private const val ITEM_LIST_ROW_H = 18

private data class ItemListLayout(
    val height: Int,
    val searchY: Int,
    val searchH: Int,
    val topY: Int,
    val iconSize: Int,
    val listY: Int,
    val maxRows: Int,
    val showAddedStrip: Boolean,
)

private fun ClickGui.itemListLayout(y: Int, entry: ItemListEntry): ItemListLayout {
    val searchY = y + 18
    val searchH = 14
    val iconSize = 16
    val showAddedStrip = entry.value.isNotEmpty()
    val topY = searchY + searchH + 4
    val listY = if (showAddedStrip) topY + iconSize + 5 else searchY + searchH + 5
    val rows = filteredItemListRowsFor(itemListSearch.text, entry.filter)
    val maxRows = ((ITEM_LIST_DROPDOWN_MAX_H - (listY - y) - 4) / ITEM_LIST_ROW_H)
        .coerceAtLeast(1)
        .coerceAtMost(rows.size.coerceAtLeast(1))
    val height = (listY - y) + maxRows * ITEM_LIST_ROW_H + 4
    return ItemListLayout(height, searchY, searchH, topY, iconSize, listY, maxRows, showAddedStrip)
}

internal fun ClickGui.itemListDropdownHeight(entry: ItemListEntry): Int =
    itemListLayout(0, entry).height

internal fun ClickGui.itemListMaxScroll(entry: ItemListEntry): Int {
    val layout = itemListLayout(0, entry)
    val rows = filteredItemListRowsFor(itemListSearch.text, entry.filter)
    return (rows.size - layout.maxRows).coerceAtLeast(0)
}

internal fun ClickGui.itemListAddedStripYRange(y: Int, entry: ItemListEntry): IntRange? {
    val layout = itemListLayout(y, entry)
    if (!layout.showAddedStrip) return null
    return layout.topY until layout.topY + layout.iconSize
}

internal fun ClickGui.drawItemListDropdown(
    g: GuiGraphicsExtractor,
    entry: ItemListEntry,
    x: Int, y: Int, w: Int,
    mx: Int, my: Int,
) {
    val layout = itemListLayout(y, entry)
    val h = layout.height
    g.roundedFill(x, y, w, h, 6, PNL_BG)
    val innerX = x + 6
    val innerW = w - 12
 
    val header = "${entry.value.size} items (${entry.mode.name.lowercase().replaceFirstChar { it.uppercase() }})"
    g.Text(guiFont, styled(header), innerX, y + 4, TEXT)
 
    val searchY = layout.searchY
    val searchH = layout.searchH
    val sx = innerX
    val sw = innerW
    g.fill(sx, searchY, sx + sw, searchY + searchH, BTN_BG)
    g.enableScissor(sx + 2, searchY + 1, sx + sw - 2, searchY + searchH - 1)
    val searchText = itemListSearch.text
    val searchTextY = searchY + (searchH - 8) / 2
    g.Text(
        guiFont,
        styled(searchText.ifBlank { "Search..." }),
        sx + 2, searchTextY,
        if (searchText.isBlank()) TEXT_DIM else TEXT,
    )
    if (editingItemListSearch && cursorVisible) {
        val before = itemListSearch.text.substring(0, itemListSearch.cursor.coerceAtMost(itemListSearch.text.length))
        val caretPx = guiFont.width(styled(before)) - itemListSearch.scrollPx
        val cx = (sx + 2 + caretPx).toInt()
        g.fill(cx, searchY + 3, cx + 1, searchY + searchH - 3, TEXT)
    }
    g.disableScissor()
 
    val topY = layout.topY
    val iconSize = layout.iconSize
    val topBarX = innerX
    val topBarW = innerW
 
    if (layout.showAddedStrip) {
        val totalAddedWidth = entry.value.size * (iconSize + 6)
        itemListAddedScroll = itemListAddedScroll.coerceIn(0, (totalAddedWidth - topBarW).coerceAtLeast(0))
 
        val allItems = getAllItems()
        g.enableScissor(topBarX, topY, topBarX + topBarW, topY + iconSize)
        var ox = topBarX - itemListAddedScroll
        for (id in entry.value) {
            val isCategoryId = id.endsWith("_category")
            val itemToDisplay: Item = if (isCategoryId) {
                val catId = id.removeSuffix("_category")
                val category = itemCategories.firstOrNull { it.id == catId }
                val catItems = if (category != null) allItems.filter { category.matches(itemNameOf(it)) } else emptyList()
                if (catItems.isEmpty()) continue
                catItems[animIndex(catItems.size)].second
            } else {
                findItemByName(id)?.second ?: continue
            }
            g.item(itemToDisplay.defaultInstance, ox, topY)
            val bx = ox + iconSize - 6
            g.fill(bx, topY, bx + 6, topY + 6, argb(255, 180, 50, 50))
            ox += iconSize + 6
        }
        g.disableScissor()
    } else {
        itemListAddedScroll = 0
    }
 
    val listY = layout.listY
    val rowH = ITEM_LIST_ROW_H
    val rows = filteredItemListRowsFor(itemListSearch.text, entry.filter)
    val toShow = rows.drop(itemListScroll).take(layout.maxRows)
 
    val addedSet = entry.value.map { it.lowercase(Locale.getDefault()) }.toHashSet()
 
    var ry = listY
    g.enableScissor(innerX, listY, innerX + innerW, y + h - 4)
    for (row in toShow) {
        val preview: Pair<String, Item> = when (row) {
            is ItemRow -> row.item
            is CategoryRow -> {
                val catItems = row.items
                if (catItems.isEmpty()) continue
                catItems[animIndex(catItems.size)]
            }
        }
        val hovered = mx in innerX until innerX + innerW && my in ry until ry + rowH
        drawRowSurface(g, innerX, ry, innerW, rowH, hovered = hovered, selected = false)
        g.item(preview.second.defaultInstance, innerX + 2, ry + 1)
 
        val pretty = when (row) {
            is ItemRow -> row.item.first.replace('_', ' ').replaceFirstChar { it.uppercase() }
            is CategoryRow -> "${row.category.label} (${row.items.size})"
        }
        val textX = innerX + 24
        val actionX = innerX + innerW - 22
        g.Text(guiFont, styled(pretty), textX, ry + (rowH - 8) / 2, TEXT)
 
        val isAdded = when (row) {
            is ItemRow -> row.item.first in addedSet
            is CategoryRow -> (row.category.id + "_category") in addedSet
        }
        drawControlSurface(g, actionX, ry + 2, 18, rowH - 4, active = isAdded, hovered = hovered)
        g.TextCentered(guiFont, styled(if (isAdded) "-" else "+"), actionX + 9, ry + (rowH - 8) / 2, TEXT)
        ry += rowH
    }
    g.disableScissor()
}
 
internal fun ClickGui.handleItemListDropdownClick(
    entry: ItemListEntry,
    x: Int, y: Int, w: Int,
    mx: Int, my: Int,
): Boolean {
    val layout = itemListLayout(y, entry)
    val h = layout.height
    if (mx !in x until x + w || my !in y until y + h) return false
    val innerX = x + 6
    val innerW = w - 12
 
    val searchY = layout.searchY
    val searchH = layout.searchH
    val sx = innerX
    val sw = innerW
    if (mx in sx until sx + sw && my in searchY until searchY + searchH) {
        itemListSearch.cursor = itemListSearch.posFromPixel(mx - (sx + 2))
        editingItemListSearch = true
        return true
    }
 
    val topY = layout.topY
    val iconSize = layout.iconSize
    if (layout.showAddedStrip) {
        val topBarX = innerX
        var ox = topBarX - itemListAddedScroll
        for (id in entry.value) {
            val bx = ox + iconSize - 6
            if (mx in bx until bx + 6 && my in topY until topY + 6) {
                entry.remove(id)
                return true
            }
            ox += iconSize + 6
        }
    }
 
    val listY = layout.listY
    val rowH = ITEM_LIST_ROW_H
    val rows = filteredItemListRowsFor(itemListSearch.text, entry.filter)
    val toShow = rows.drop(itemListScroll).take(layout.maxRows)
    var ry = listY
    for (row in toShow) {
        val px = innerX + innerW - 22
        if (mx in px until px + 18 && my in ry until ry + rowH) {
            when (row) {
                is ItemRow -> {
                    val id = row.item.first
                    if (entry.value.any { it.equals(id, ignoreCase = true) }) entry.remove(id) else entry.add(id)
                }
                is CategoryRow -> {
                    val categoryId = row.category.id + "_category"
                    if (entry.value.any { it.equals(categoryId, ignoreCase = true) }) entry.remove(categoryId) else entry.add(categoryId)
                }
            }
            return true
        }
        ry += rowH
    }
    return true
}
