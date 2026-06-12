package me.ghluka.medved.config.entry

import com.google.gson.JsonElement
import me.ghluka.medved.config.ConfigGroup

abstract class ConfigEntry<T>(
    val name: String,
    var defaultValue: T
) : MutableConfigValue<T> {
    private val changeListeners = mutableListOf<(T) -> Unit>()

    /** When set, the entry is only shown in the GUI if this returns true. */
    var visibleWhen: (() -> Boolean)? = null

    private val _aliases = mutableListOf<String>()
    val aliases: List<String> get() = _aliases

    var group: ConfigGroup? = null
        internal set

    override var value: T = defaultValue
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

    fun aliases(vararg names: String): ConfigEntry<T> {
        _aliases += names.filter { it.isNotBlank() && it != name }
        return this
    }

    abstract fun toJson(): JsonElement
    abstract fun fromJson(element: JsonElement)
}
