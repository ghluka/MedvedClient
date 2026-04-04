package me.ghluka.medved.config.entry

import com.google.gson.JsonElement
import com.google.gson.JsonNull

class HudEditEntry : ConfigEntry<Unit>("edit_position", Unit) {
    override fun toJson(): JsonElement = JsonNull.INSTANCE
    override fun fromJson(element: JsonElement) {}
}
