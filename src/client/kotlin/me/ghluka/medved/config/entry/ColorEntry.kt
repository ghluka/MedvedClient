package me.ghluka.medved.config.entry

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

class ColorEntry(name: String, default: Color, val allowAlpha: Boolean = true) : ConfigEntry<Color>(name, default) {
    override fun toJson(): JsonElement = JsonPrimitive(value.toHex())
    override fun fromJson(element: JsonElement) { value = Color.fromHex(element.asString) }
}
