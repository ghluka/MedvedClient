package me.ghluka.medved.config.entry

import com.google.gson.JsonElement

abstract class ConfigEntry<T>(
    val name: String,
    var defaultValue: T
) {
    private val changeListeners = mutableListOf<(T) -> Unit>()

    /** When set, the entry is only shown in the GUI if this returns true. */
    var visibleWhen: (() -> Boolean)? = null

    var value: T = defaultValue
        set(newValue) {
            val old = field
            field = newValue
            if (old != newValue) {
                changeListeners.forEach { it(newValue) }
            }
        }

    /** Register a listener that fires whenever this entry's value changes. */
    fun onChange(listener: (T) -> Unit): ConfigEntry<T> {
        changeListeners += listener
        return this
    }

    fun reset() {
        value = defaultValue
    }

    abstract fun toJson(): JsonElement
    abstract fun fromJson(element: JsonElement)
}
