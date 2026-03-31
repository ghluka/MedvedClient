package me.ghluka.medved.config.entry

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

class BooleanEntry(name: String, default: Boolean) : ConfigEntry<Boolean>(name, default) {
    override fun toJson(): JsonElement = JsonPrimitive(value)
    override fun fromJson(element: JsonElement) { value = element.asBoolean }
}

class IntEntry(
    name: String,
    default: Int,
    val min: Int = Int.MIN_VALUE,
    val max: Int = Int.MAX_VALUE
) : ConfigEntry<Int>(name, default) {
    override fun toJson(): JsonElement = JsonPrimitive(value)
    override fun fromJson(element: JsonElement) { value = element.asInt.coerceIn(min, max) }
}

class FloatEntry(
    name: String,
    default: Float,
    val min: Float = -Float.MAX_VALUE,
    val max: Float = Float.MAX_VALUE
) : ConfigEntry<Float>(name, default) {
    override fun toJson(): JsonElement = JsonPrimitive(value)
    override fun fromJson(element: JsonElement) { value = element.asFloat.coerceIn(min, max) }
}

class DoubleEntry(
    name: String,
    default: Double,
    val min: Double = -Double.MAX_VALUE,
    val max: Double = Double.MAX_VALUE
) : ConfigEntry<Double>(name, default) {
    override fun toJson(): JsonElement = JsonPrimitive(value)
    override fun fromJson(element: JsonElement) { value = element.asDouble.coerceIn(min, max) }
}

class StringEntry(name: String, default: String) : ConfigEntry<String>(name, default) {
    override fun toJson(): JsonElement = JsonPrimitive(value)
    override fun fromJson(element: JsonElement) { value = element.asString }
}
