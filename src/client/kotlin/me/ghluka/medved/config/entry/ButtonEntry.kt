package me.ghluka.medved.config.entry

import com.google.gson.JsonElement
import com.google.gson.JsonNull

class ButtonEntry(name: String, val label: String, val action: () -> Unit) : ConfigEntry<Unit>(name, Unit) {
    override fun toJson(): JsonElement = JsonNull.INSTANCE
    override fun fromJson(element: JsonElement) {}
}
