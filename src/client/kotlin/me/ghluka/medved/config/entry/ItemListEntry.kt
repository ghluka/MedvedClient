package me.ghluka.medved.config.entry

import com.google.gson.JsonElement
import com.google.gson.JsonArray
import com.google.gson.JsonObject

class ItemListEntry(name: String, default: List<String> = emptyList(), defaultMode: Mode = Mode.WHITELIST, defaultFilter: Filter = Filter.NONE) : ConfigEntry<List<String>>(name, default) {
    enum class Mode { WHITELIST, BLACKLIST }
    enum class Filter { NONE, BLOCKS_ONLY }

    var mode: Mode = defaultMode
    var filter: Filter = defaultFilter

    val items: MutableList<String>
        get() = value.toMutableList()

    fun contains(id: String): Boolean = value.any { it.equals(id, ignoreCase = true) }

    fun add(id: String) {
        val trimmed = id.trim()
        if (trimmed.isEmpty()) return
        value = listOf(trimmed) + value.filterNot { it.equals(trimmed, ignoreCase = true) }
    }

    fun remove(id: String) {
        value = value.filterNot { it.equals(id, ignoreCase = true) }
    }

    fun setAll(ids: List<String>) {
        value = ids.map { it.trim() }.filter { it.isNotEmpty() }
    }

    override fun toJson(): JsonElement {
        val obj = JsonObject()
        obj.addProperty("mode", mode.name)
        obj.addProperty("filter", filter.name)
        val arr = JsonArray()
        for (it in value) arr.add(it)
        obj.add("items", arr)
        return obj
    }

    override fun fromJson(element: JsonElement) {
        try {
            val obj = element.asJsonObject
            if (obj.has("mode")) {
                mode = try { Mode.valueOf(obj.getAsJsonPrimitive("mode").asString) } catch (e: Exception) { mode }
            }
            if (obj.has("filter")) {
                filter = try { Filter.valueOf(obj.getAsJsonPrimitive("filter").asString) } catch (e: Exception) { filter }
            }
            val arr = obj.getAsJsonArray("items")
            val list = mutableListOf<String>()
            for (v in arr) list.add(v.asString)
            value = list
        } catch (e: Exception) {
            try {
                val arr = element.asJsonArray
                val list = mutableListOf<String>()
                for (v in arr) list.add(v.asString)
                value = list
            } catch (_: Exception) {}
        }
    }
}
