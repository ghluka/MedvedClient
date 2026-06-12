package me.ghluka.medved.config.entry

import kotlin.reflect.KProperty

interface ConfigValue<T> {
    val value: T

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value
}

interface MutableConfigValue<T> : ConfigValue<T> {
    override var value: T

    operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: T) {
        value = newValue
    }
}
