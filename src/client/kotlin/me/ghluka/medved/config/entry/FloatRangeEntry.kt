package me.ghluka.medved.config.entry

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

class FloatRangeEntry(
    name: String,
    default: Pair<Float, Float>,
    val min: Float,
    val max: Float,
    val decimals: Int = 1
) : ConfigEntry<Pair<Float, Float>>(name, default) {

    override fun toJson(): JsonElement = JsonArray().apply {
        add(JsonPrimitive(value.first))
        add(JsonPrimitive(value.second))
    }

    override fun fromJson(element: JsonElement) {
        val arr = element.asJsonArray
        val lo = arr[0].asFloat.coerceIn(min, max)
        val hi = arr[1].asFloat.coerceIn(min, max)
        value = lo.coerceAtMost(hi) to hi.coerceAtLeast(lo)
    }
}
