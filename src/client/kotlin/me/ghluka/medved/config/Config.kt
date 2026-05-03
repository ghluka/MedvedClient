package me.ghluka.medved.config

import com.google.gson.JsonObject
import me.ghluka.medved.config.entry.*

/**
 * Base class for all module configs. Extend this and register entries in the
 * subclass's `init` block (or directly as property initializers).
 *
 * Example:
 * ```kotlin
 * object SprintConfig : Config("sprint") {
 *     val enabled = boolean("enabled", true)
 *     val toggleKey = keybind("toggle_key", GLFW.GLFW_KEY_V)
 *         .onPress { enabled.value = !enabled.value }
 * }
 * ```
 */
abstract class Config(val name: String) {

    private val _entries = mutableListOf<ConfigEntry<*>>()

    /** All entries registered to this config. */
    val entries: List<ConfigEntry<*>> get() = _entries

    protected fun <T : ConfigEntry<*>> register(entry: T): T {
        _entries += entry
        return entry
    }

    protected fun boolean(name: String, default: Boolean) =
        register(BooleanEntry(name, default))

    protected fun int(name: String, default: Int, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE) =
        register(IntEntry(name, default, min, max))

    protected fun float(name: String, default: Float, min: Float = -Float.MAX_VALUE, max: Float = Float.MAX_VALUE) =
        register(FloatEntry(name, default, min, max))

    protected fun double(name: String, default: Double, min: Double = -Double.MAX_VALUE, max: Double = Double.MAX_VALUE) =
        register(DoubleEntry(name, default, min, max))

    protected fun string(name: String, default: String) =
        register(StringEntry(name, default))

    protected fun color(name: String, default: Color, allowAlpha: Boolean = true) =
        register(ColorEntry(name, default, allowAlpha))

    protected fun keybind(name: String, default: Int) =
        register(KeybindEntry(name, default))

    protected inline fun <reified T : Enum<T>> enum(name: String, default: T) =
        register(EnumEntry(name, default, T::class.java))

    protected fun intRange(name: String, default: Pair<Int, Int>, min: Int, max: Int) =
        register(IntRangeEntry(name, default, min, max))

    protected fun floatRange(name: String, default: Pair<Float, Float>, min: Float, max: Float, decimals: Int = 1) =
        register(FloatRangeEntry(name, default, min, max, decimals))

    protected fun button(name: String, label: String, action: () -> Unit) =
        register(ButtonEntry(name, label, action))

    protected fun itemList(name: String, default: List<String> = emptyList(), defaultMode: me.ghluka.medved.config.entry.ItemListEntry.Mode = me.ghluka.medved.config.entry.ItemListEntry.Mode.WHITELIST, filter: me.ghluka.medved.config.entry.ItemListEntry.Filter = me.ghluka.medved.config.entry.ItemListEntry.Filter.NONE) =
        register(me.ghluka.medved.config.entry.ItemListEntry(name, default, defaultMode, filter))

    fun serialize(): JsonObject = JsonObject().also { obj ->
        _entries.forEach { obj.add(it.name, it.toJson()) }
    }

    fun deserialize(json: JsonObject) {
        for (entry in _entries) {
            if (json.has(entry.name)) {
                runCatching { entry.fromJson(json[entry.name]) }
            }
        }
    }

    fun refreshDynamicColors(themeColor: Color, timeSeconds: Float, supportsThemeMode: (ColorEntry) -> Boolean) {
        _entries.filterIsInstance<ColorEntry>().forEach { it.applyDynamicColor(themeColor, timeSeconds, supportsThemeMode(it)) }
    }

    /** Polls all [KeybindEntry] instances. Called each client tick by [ConfigManager]. */
    fun tickKeybinds() {
        _entries.filterIsInstance<KeybindEntry>().forEach { it.tick() }
    }
}
