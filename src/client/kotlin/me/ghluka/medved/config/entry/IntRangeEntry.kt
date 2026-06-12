package me.ghluka.medved.config.entry

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

/**
 * A config entry storing a min–max integer range.
 * Rendered as two sliders in the GUI. Value is a [Pair] of (low, high).
 */
class IntRangeEntry(
    name: String,
    default: Pair<Int, Int>,
    val min: Int,
    val max: Int
) : ConfigEntry<Pair<Int, Int>>(name, default) {

    override fun toJson(): JsonElement = JsonArray().apply {
        add(JsonPrimitive(value.first))
        add(JsonPrimitive(value.second))
    }

    override fun fromJson(element: JsonElement) {
        val arr = element.arrayOrNull?.takeIf { it.size() >= 2 } ?: return
        val lo = arr[0].intOrNull?.coerceIn(min, max) ?: return
        val hi = arr[1].intOrNull?.coerceIn(min, max) ?: return
        value = lo.coerceAtMost(hi) to hi.coerceAtLeast(lo)
    }
}
