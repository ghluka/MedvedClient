package me.ghluka.medved.config.entry

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

class EnumEntry<T : Enum<T>>(
    name: String,
    default: T,
    private val enumClass: Class<T>
) : ConfigEntry<T>(name, default) {

    /** All constants for this enum type. */
    val constants: Array<T> get() = enumClass.enumConstants

    /** Set the value to the constant at the given index. */
    fun setByIndex(index: Int) {
        val consts = enumClass.enumConstants
        if (index in consts.indices) value = consts[index]
    }

    /** Advance to the next enum constant, wrapping around. */
    fun cycle() {
        val consts = enumClass.enumConstants
        val idx = consts.indexOf(value)
        value = consts[(idx + 1) % consts.size]
    }

    override fun toJson(): JsonElement = JsonPrimitive(value.name)
    override fun fromJson(element: JsonElement) {
        value = enumClass.enumConstants.firstOrNull { it.name == element.asString } ?: defaultValue
    }
}
