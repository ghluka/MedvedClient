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
)

private val BLOCK_CATEGORIES = setOf("wool")
 
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

private fun _isBlock(item: Item): Boolean {
    return item is BlockItem
}
 
private sealed interface PickerRow {
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

private fun ClickGui.filteredRowsFor(query: String, filter: ItemListEntry.Filter = ItemListEntry.Filter.NONE): List<PickerRow> {
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
                items.mapTo(seen) { it.first }
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

internal fun ClickGui.drawItemListDropdown(
    g: GuiGraphicsExtractor,
    entry: ItemListEntry,
    x: Int, y: Int, w: Int,
    mx: Int, my: Int,
) {
    val h = 220
    g.roundedFill(x, y, w, h, 6, PNL_BG)
 
    val header = "${entry.value.size} items (${entry.mode.name.lowercase().replaceFirstChar { it.uppercase() }})"
    g.Text(guiFont, styled(header), x + 6, y + 4, TEXT)
 
    val searchY = y + 18
    val searchH = 14
    val sx = x + 6
    val sw = w - 12
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
 
    val topY = searchY + searchH + 6
    val iconSize = 16
    val topBarX = x + 6
    val topBarW = w - 12
 
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
 
    val listY = topY + iconSize + 8
    val rowH = 18
    val rows = filteredRowsFor(itemListSearch.text, entry.filter)
    val maxRows = ((y + h - listY - 6) / rowH).coerceAtLeast(1)
    val toShow = rows.drop(itemListScroll).take(maxRows + 1)
 
    val addedSet = entry.value.map { it.lowercase(Locale.getDefault()) }.toHashSet()
 
    var ry = listY
    for (row in toShow) {
        val preview: Pair<String, Item> = when (row) {
            is ItemRow -> row.item
            is CategoryRow -> {
                val catItems = row.items
                if (catItems.isEmpty()) continue
                catItems[animIndex(catItems.size)]
            }
        }
        g.item(preview.second.defaultInstance, x + 6, ry)
 
        val pretty = when (row) {
            is ItemRow -> row.item.first.replace('_', ' ').replaceFirstChar { it.uppercase() }
            is CategoryRow -> "${row.category.label} (${row.items.size})"
        }
        g.Text(guiFont, styled(pretty), x + 28, ry + 2, TEXT)
 
        val isAdded = when (row) {
            is ItemRow -> row.item.first in addedSet
            is CategoryRow -> (row.category.id + "_category") in addedSet
        }
        val px = x + w - 24
        g.fill(px, ry, px + 18, ry + 14, BTN_BG)
        g.TextCentered(guiFont, styled(if (isAdded) "-" else "+"), px + 9, ry + 2, TEXT)
        ry += rowH
    }
}
 
internal fun ClickGui.handleItemListDropdownClick(
    entry: ItemListEntry,
    x: Int, y: Int, w: Int,
    mx: Int, my: Int,
): Boolean {
    val h = 220
    if (mx !in x until x + w || my !in y until y + h) return false
 
    val searchY = y + 18
    val searchH = 14
    val sx = x + 6
    val sw = w - 12
    if (mx in sx until sx + sw && my in searchY until searchY + searchH) {
        itemListSearch.cursor = itemListSearch.posFromPixel(mx - (sx + 2))
        editingItemListSearch = true
        return true
    }
 
    val topY = searchY + searchH + 6
    val iconSize = 16
    val topBarX = x + 6
    var ox = topBarX - itemListAddedScroll
    for (id in entry.value) {
        val bx = ox + iconSize - 6
        if (mx in bx until bx + 6 && my in topY until topY + 6) {
            entry.remove(id)
            return true
        }
        ox += iconSize + 6
    }
 
    val listY = topY + iconSize + 8
    val rowH = 18
    val rows = filteredRowsFor(itemListSearch.text, entry.filter)
    val maxRows = ((y + h - listY - 6) / rowH).coerceAtLeast(1)
    val toShow = rows.drop(itemListScroll).take(maxRows)
    var ry = listY
    for (row in toShow) {
        val px = x + w - 24
        if (mx in px until px + 18 && my in ry until ry + 14) {
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